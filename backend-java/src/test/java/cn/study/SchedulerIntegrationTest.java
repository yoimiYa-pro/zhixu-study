package cn.study;
import cn.study.dto.WeeklySummary;
import cn.study.scheduler.JobRunner;
import cn.study.service.WeeklyReportService;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import static org.assertj.core.api.Assertions.*;
class SchedulerIntegrationTest extends ApiIntegrationBase {
    @Autowired JobRunner runner;@Autowired WeeklyReportService reports;@Autowired Clock clock;
    @Test void scheduleIsDurableAndWeeklyStatisticsDoNotRequireAi() {
        db.update("delete from scheduler_runs");
        createQuestion();LocalDate date=LocalDate.now(clock);
        assertThat(runner.run("WEEKLY_TEST",date,()->Map.of("reportId",reports.generate(date).get("id")))).isTrue();
        assertThat(runner.run("WEEKLY_TEST",date,()->{throw new IllegalStateException("duplicate must not execute");})).isFalse();
        var row=reports.list().getFirst();assertThat(row.get("summary")).isNull();assertThat(row.get("revision")).isEqualTo(1);
        var stats=(Map<?,?>)row.get("stats");assertThat(((Map<?,?>)stats.get("summary")).get("practiceCount")).isEqualTo(1);
        var valid=new WeeklySummary("仅测试摘要",List.of(),List.of(),List.of("增长率"),List.of(),List.of());
        UUID id=UUID.fromString(row.get("id").toString());assertThat(reports.apply(id,1,valid)).isTrue();
        reports.generate(date);assertThat(reports.apply(id,1,valid)).isFalse();
        var bad=new WeeklySummary("伪造引用",List.of(),List.of(),List.of(),List.of(),List.of(new WeeklySummary.Highlight(UUID.randomUUID(),"未知时政")));
        assertThatThrownBy(()->reports.apply(id,2,bad)).hasMessage("AI_INVALID_OUTPUT");
        assertThat(request("/api/weekly-reports",HttpMethod.GET,null,List.class).getBody()).hasSize(1);
    }
}
