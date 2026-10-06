package cn.study.controller;

import cn.study.dto.QuestionInput;
import cn.study.service.*;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/questions") @Validated
public class QuestionController {
    private final QuestionService questions;
    public QuestionController(QuestionService questions) { this.questions=questions; }
    @GetMapping public Map<String,Object> list(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String type,@RequestParam(defaultValue="false") boolean mistakesOnly,@RequestParam(defaultValue="1") @Min(1) @Max(10000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int pageSize) { return questions.list(q,type,mistakesOnly,page,pageSize); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public Map<String,Object> create(@Valid @RequestBody QuestionInput input) { return questions.create(input); }
    @GetMapping("/{id}") public Map<String,Object> get(@PathVariable UUID id) { return questions.get(id); }
    @PutMapping("/{id}") public Map<String,Object> update(@PathVariable UUID id,@Valid @RequestBody QuestionInput input) { return questions.update(id,input); }
    @PostMapping("/{id}/analyze") @ResponseStatus(HttpStatus.ACCEPTED) public Map<String,Object> analyze(@PathVariable UUID id) { return questions.analyze(id); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID id) { questions.delete(id); }
    public record Reason(@NotBlank @Pattern(regexp="知识盲区|理解错误|审题错误|计算错误|方法错误|粗心|时间不足|记忆错误") String reason) {}
    @PatchMapping("/{id}/mistake") public Map<String,Object> reason(@PathVariable UUID id,@Valid @RequestBody Reason input) { return questions.confirmReason(id,input.reason()); }
}
