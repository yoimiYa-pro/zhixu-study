package cn.study.controller;

import cn.study.service.KnowledgeService;
import java.util.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/knowledge-points")
public class KnowledgeController {
    private final KnowledgeService service;
    public KnowledgeController(KnowledgeService service) { this.service=service; }
    @GetMapping public List<Map<String,Object>> list() { return service.list(); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public Map<String,Object> create(@Valid @RequestBody KnowledgeService.Input input) { return service.create(input); }
    @PutMapping("/{id}") public Map<String,Object> update(@PathVariable UUID id,@Valid @RequestBody KnowledgeService.Input input) { return service.update(id,input); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID id) { service.delete(id); }
}
