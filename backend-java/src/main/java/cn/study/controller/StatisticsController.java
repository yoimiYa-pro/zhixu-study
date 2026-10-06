package cn.study.controller;
import cn.study.service.StatisticsService;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController
public class StatisticsController {
    private final StatisticsService stats;
    public StatisticsController(StatisticsService stats) { this.stats=stats; }
    @GetMapping("/api/statistics") public Map<String,Object> get(@RequestParam(defaultValue="7") int days) { return stats.get(days); }
}
