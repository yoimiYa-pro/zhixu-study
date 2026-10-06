package cn.study.controller;
import cn.study.repository.Db;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
@RestController
public class MaintenanceController {
    private final Db db;
    public MaintenanceController(Db db) { this.db=db; }
    @PostMapping("/api/maintenance/reindex") @Transactional public Map<String,Object> reindex() {
        UUID operation=UUID.randomUUID();int count=0;
        for(String type:List.of("question","knowledge","essay","current_affair")) {
            String table=switch(type) { case "question"->"questions";case "knowledge"->"knowledge_points";case "essay"->"essay_materials";default->"current_affairs"; };
            String version=type.equals("knowledge")?"1":"revision";String filter=type.equals("question")?" where deleted_at is null":"";
            count+=db.update("insert into ai_tasks(kind,reference_id,payload_json,dedupe_key) select 'INDEX_DOCUMENT',id,jsonb_build_object('entityType',?::text,'revision',"+version+"),?::text || id::text from "+table+filter,type,"rebuild:"+operation+":");
        }
        return Map.of("operationId",operation,"queued",count);
    }
    @GetMapping("/api/scheduler-runs") public List<Map<String,Object>> schedules() { return db.rows("select * from scheduler_runs order by ran_at desc limit 50"); }
}
