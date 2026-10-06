package cn.study.tasks;
import cn.study.dto.WeeklySummary;
import cn.study.repository.Db;
import cn.study.service.*;
import java.util.*;
import org.springframework.stereotype.Component;
@Component
public class WeeklyReportHandler implements TaskHandler {
    private final AiClient ai;private final WeeklyReportService reports;
    public WeeklyReportHandler(AiClient ai,WeeklyReportService reports) { this.ai=ai;this.reports=reports; }
    public String kind() { return "WEEKLY_REPORT"; }
    public Object handle(Map<String,Object> task) {
        UUID id=Db.uuid(task.get("referenceId"));int revision=((Number)((Map<?,?>)task.get("payload")).get("revision")).intValue();var row=reports.get(id);
        if(((Number)row.get("revision")).intValue()!=revision) return Map.of("skipped","superseded");
        var summary=ai.post("/ai/weekly-report",Map.of("stats",row.get("stats")),WeeklySummary.class);
        return Map.of("applied",reports.apply(id,revision,summary));
    }
}
