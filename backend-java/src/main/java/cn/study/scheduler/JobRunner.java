package cn.study.scheduler;
import cn.study.repository.Db;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;
import org.slf4j.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
@Service
public class JobRunner {
    private static final Logger log=LoggerFactory.getLogger(JobRunner.class);
    private final Db db;private final TransactionTemplate transaction;
    public JobRunner(Db db,PlatformTransactionManager manager) { this.db=db;this.transaction=new TransactionTemplate(manager); }
    public boolean run(String kind,LocalDate date,Supplier<Map<String,Object>> action) {
        try {
            boolean completed=Boolean.TRUE.equals(transaction.execute(status->{
                boolean locked=(Boolean)db.one("select pg_try_advisory_xact_lock(hashtext(?)) as locked","schedule:"+kind+":"+date).get("locked");
                if(!locked || db.count("select count(*) from scheduler_runs where kind=? and run_date=? and status='COMPLETED'",kind,date)>0) return false;
                var result=action.get();
                db.update("insert into scheduler_runs(kind,run_date,status,result_json) values (?,?,'COMPLETED',?::jsonb) on conflict(kind,run_date) do update set status='COMPLETED',result_json=excluded.result_json,error_code=null,ran_at=now()",kind,date,db.json(result));
                return true;
            }));
            log.info("scheduled_job kind={} date={} executed={}",kind,date,completed);return completed;
        } catch(RuntimeException error) {
            log.error("scheduled_job_failed kind={} date={} type={}",kind,date,error.getClass().getSimpleName());
            try { transaction.executeWithoutResult(status->db.update("insert into scheduler_runs(kind,run_date,status,error_code) values (?,?,'FAILED','SCHEDULE_FAILED') on conflict(kind,run_date) do update set status='FAILED',error_code='SCHEDULE_FAILED',ran_at=now()",kind,date)); }
            catch(RuntimeException unavailable) { log.error("scheduled_journal_unavailable kind={}",kind); }
            return false;
        }
    }
}
