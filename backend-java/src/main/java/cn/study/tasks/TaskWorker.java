package cn.study.tasks;

import cn.study.repository.Db;
import cn.study.service.AiClient;
import java.util.*;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TaskWorker {
    private static final Logger log=LoggerFactory.getLogger(TaskWorker.class);
    private final Db db;private final Map<String,TaskHandler> handlers=new HashMap<>();
    private final int maxAttempts,leaseSeconds;private final boolean enabled;
    public TaskWorker(Db db,List<TaskHandler> handlers,@Value("${ai.max-attempts:3}") int maxAttempts,@Value("${ai.worker-enabled:true}") boolean enabled,@Value("${ai.lease-seconds:300}") int leaseSeconds) {
        this.db=db;for(var handler:handlers) this.handlers.put(handler.kind(),handler);this.maxAttempts=maxAttempts;this.enabled=enabled;this.leaseSeconds=leaseSeconds;
    }
    @Scheduled(fixedDelayString="${ai.poll-ms:5000}") public void tick() {
        if(!enabled) return;
        try {
            db.update("update ai_tasks set status='PENDING',started_at=null,available_at=now() where status='PROCESSING' and started_at < now()-make_interval(secs=>?)",leaseSeconds);
            processOne();
        } catch (RuntimeException error) { log.error("worker_poll_failed type={}",error.getClass().getSimpleName()); }
    }
    public boolean processOne() {
        var rows=db.rows("""
            update ai_tasks set status='PROCESSING',attempts=attempts+1,started_at=now(),error_code=null,progress_done=0,progress_total=null,progress_label='处理中'
            where id=(select id from ai_tasks where status='PENDING' and available_at<=now() order by created_at,id limit 1 for update skip locked) returning *
            """);
        if(rows.isEmpty()) return false;
        var task=rows.getFirst();UUID id=Db.uuid(task.get("id"));String kind=task.get("kind").toString();
        try {
            var handler=handlers.get(kind);
            if(handler==null) throw new AiClient.Failure("UNSUPPORTED_TASK");
            Object result=handler.handle(task);
            db.update("update ai_tasks set status='COMPLETED',result_json=?::jsonb,completed_at=now(),error_code=null,progress_done=coalesce(progress_total,1),progress_total=coalesce(progress_total,1),progress_label='已完成' where id=?",db.json(result),id);
            log.info("ai_task_completed id={} kind={}",id,kind);
        } catch (RuntimeException error) {
            String code=error instanceof AiClient.Failure failure?failure.code:"TASK_PROCESSING_FAILED";
            int attempt=((Number)task.get("attempts")).intValue();
            boolean retryable=!Set.of("LLM_NOT_CONFIGURED","EMBEDDING_NOT_CONFIGURED","AI_INVALID_OUTPUT","UNSUPPORTED_TASK","NEWS_FEEDS_EMPTY").contains(code);
            boolean retry=retryable && attempt<maxAttempts;
            db.update("update ai_tasks set status=?,error_code=?,available_at=now()+make_interval(secs=>?),completed_at=case when ? then null else now() end,progress_label=? where id=?",retry?"PENDING":"FAILED",code,30*attempt,retry,retry?"等待重试":"处理失败",id);
            log.warn("ai_task_failed id={} kind={} code={} attempt={}",id,kind,code,attempt);
        }
        return true;
    }
}
