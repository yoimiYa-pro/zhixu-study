package cn.study.repository;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

@Repository
public class TaskRepository {
    private final Db db;
    public TaskRepository(Db db) { this.db=db; }
    public UUID enqueue(String kind,UUID reference,Map<String,?> payload,String dedupe) {
        return Db.uuid(db.one("insert into ai_tasks(kind,reference_id,payload_json,dedupe_key) values (?,?,?::jsonb,?) on conflict(dedupe_key) do update set dedupe_key=excluded.dedupe_key returning id",kind,reference,db.json(payload),dedupe).get("id"));
    }
    public List<Map<String,Object>> recent() { return db.rows("select id,kind,reference_id,status,attempts,error_code,created_at,started_at,completed_at,progress_done,progress_total,progress_label,result_json from ai_tasks order by created_at desc limit 100"); }
    public Map<String,Object> get(UUID id) { return db.one("select * from ai_tasks where id=?",id); }
    public Map<String,Object> retry(UUID id) {
        var rows=db.rows("update ai_tasks set status='PENDING',attempts=0,error_code=null,available_at=now(),started_at=null,completed_at=null,progress_done=0,progress_total=null,progress_label='等待处理',result_json=null where id=? and status='FAILED' returning *",id);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT,"只有失败任务可以重试");
        return rows.getFirst();
    }
}
