package cn.study.controller;

import cn.study.service.ReviewService;
import java.util.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/reviews")
public class ReviewController {
    private final ReviewService service;
    public ReviewController(ReviewService service) { this.service=service; }
    @GetMapping("/today") public List<Map<String,Object>> today() { return service.today(); }
    @PostMapping("/{id}/complete") public Map<String,Object> complete(@PathVariable UUID id,@Valid @RequestBody ReviewService.Completion input) { return service.complete(id,input); }
}
