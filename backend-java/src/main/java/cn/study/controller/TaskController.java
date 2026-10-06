package cn.study.controller;

import cn.study.repository.TaskRepository;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/ai-tasks")
public class TaskController {
    private final TaskRepository tasks;
    public TaskController(TaskRepository tasks) { this.tasks=tasks; }
    @GetMapping public List<Map<String,Object>> list() { return tasks.recent(); }
    @GetMapping("/{id}") public Map<String,Object> get(@PathVariable UUID id) { return tasks.get(id); }
    @PostMapping("/{id}/retry") public Map<String,Object> retry(@PathVariable UUID id) { return tasks.retry(id); }
}
