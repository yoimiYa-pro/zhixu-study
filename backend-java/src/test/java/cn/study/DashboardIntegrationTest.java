package cn.study;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import static org.assertj.core.api.Assertions.*;
class DashboardIntegrationTest extends ApiIntegrationBase {
    @Test void statisticsComeFromRecordsAndDailyPlanIsIdempotent() {
        var empty=request("/api/dashboard",HttpMethod.GET,null,Map.class).getBody();
        assertThat((List<?>)empty.get("currentAffairs")).isEmpty();
        assertThat(((Number)((Map<?,?>)empty.get("stats")).get("practiceCount")).intValue()).isZero();
        createQuestion();
        var record=Map.of("kind","PRACTICE","quantity",10,"wrongCount",3,"timeSpent",600,"note","测试记录");
        assertThat(request("/api/study-records",HttpMethod.POST,record,Map.class).getStatusCode().value()).isEqualTo(200);
        var stats=(Map<?,?>)request("/api/dashboard",HttpMethod.GET,null,Map.class).getBody().get("stats");
        assertThat(((Number)stats.get("practiceCount")).intValue()).isEqualTo(11);
        assertThat(((Number)stats.get("wrongCount")).intValue()).isEqualTo(4);
        assertThat(((Number)stats.get("weeklySeconds")).intValue()).isEqualTo(600);
        assertThat(db.count("select count(*) from daily_tasks")).isEqualTo(3);
        assertThat(request("/api/study-records",HttpMethod.POST,Map.of("kind","PRACTICE","quantity",1,"wrongCount",2),Map.class).getStatusCode().value()).isEqualTo(400);
    }
}
