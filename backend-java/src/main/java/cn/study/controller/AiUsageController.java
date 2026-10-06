package cn.study.controller;

import cn.study.service.AiUsageService;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/usage")
public class AiUsageController {
    private final AiUsageService usage;
    public AiUsageController(AiUsageService usage) { this.usage=usage; }
    @GetMapping public Map<String,Object> get(@RequestParam(defaultValue="7") int days) { return usage.summary(days); }
}
