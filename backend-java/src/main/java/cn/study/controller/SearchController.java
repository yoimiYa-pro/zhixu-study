package cn.study.controller;

import cn.study.service.SearchService;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class SearchController {
    private final SearchService search;
    public SearchController(SearchService search) { this.search=search; }
    @PostMapping("/api/questions/{id}/similar") public List<Map<String,Object>> similar(@PathVariable UUID id) { return search.similar(id); }
}
