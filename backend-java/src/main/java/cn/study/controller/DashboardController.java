package cn.study.controller;

import cn.study.repository.Db;
import cn.study.service.*;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
public class DashboardController {
    private final DashboardService dashboard;private final Db db;private final AiClient ai;
    public DashboardController(DashboardService dashboard,Db db,AiClient ai) { this.dashboard=dashboard;this.db=db;this.ai=ai; }
    @GetMapping("/api/dashboard") public Map<String,Object> dashboard() { return dashboard.get(); }
    @GetMapping("/api/ai/status") public Map<String,Object> ai() { return ai.status(); }
    public record TaskUpdate(@NotNull Boolean completed) {}
    @PatchMapping("/api/daily-tasks/{id}") public Map<String,Object> task(@PathVariable UUID id,@Valid @RequestBody TaskUpdate input) { return dashboard.task(id,input.completed()); }
    public record Study(@NotBlank @Pattern(regexp="PRACTICE|READING") String kind,@Min(0) @Max(10000) int quantity,@Min(0) @Max(10000) int wrongCount,@Min(0) @Max(86400) int timeSpent,@Size(max=1000) String note,UUID knowledgePointId) {}
    @PostMapping("/api/study-records") public Map<String,Object> study(@Valid @RequestBody Study input) {
        if(input.wrongCount()>input.quantity()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"错题数不能超过练习数量");
        if(input.knowledgePointId()!=null) db.one("select id from knowledge_points where id=?",input.knowledgePointId());
        return db.one("insert into study_records(kind,quantity,wrong_count,time_spent,note,knowledge_point_id) values (?,?,?,?,?,?) returning *",input.kind(),input.quantity(),input.wrongCount(),input.timeSpent(),Objects.requireNonNullElse(input.note(),""),input.knowledgePointId());
    }
    @GetMapping("/api/study-records") public List<Map<String,Object>> records() { return db.rows("select * from study_records order by studied_at desc limit 100"); }
}
