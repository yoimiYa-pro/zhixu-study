package cn.study.controller;
import cn.study.service.WeeklyReportService;
import java.time.*;
import java.util.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
@RestController
public class WeeklyController {
    private final WeeklyReportService reports;private final Clock clock;
    public WeeklyController(WeeklyReportService reports,Clock clock) { this.reports=reports;this.clock=clock; }
    @GetMapping("/api/weekly-reports") public List<Map<String,Object>> list() { return reports.list(); }
    @GetMapping("/api/weekly-reports/{id}") public Map<String,Object> get(@PathVariable UUID id) { return reports.get(id); }
    @PostMapping("/api/weekly-reports/generate") public Map<String,Object> generate(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date) { return reports.generate(date==null?LocalDate.now(clock):date); }
}
