package cn.study;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import static org.assertj.core.api.Assertions.*;
class StatisticsIntegrationTest extends ApiIntegrationBase {
    @Test void trendsAreZeroFilledAndParentMasteryIncludesDescendantsWithoutDoubleCounting() {
        createQuestion();
        String child=db.one("select id from knowledge_points where name='增长率'").get("id").toString();
        String parent=db.one("select id from knowledge_points where name='资料分析'").get("id").toString();
        db.update("insert into question_knowledge_points(question_id,knowledge_point_id) select question_id,?::uuid from question_knowledge_points where knowledge_point_id=?::uuid on conflict do nothing",parent,child);
        request("/api/study-records",HttpMethod.POST,Map.of("kind","PRACTICE","quantity",9,"wrongCount",1,"timeSpent",600,"knowledgePointId",child),Map.class);
        var statistics=request("/api/statistics?days=7",HttpMethod.GET,null,Map.class).getBody();
        var summary=(Map<?,?>)statistics.get("summary");assertThat(summary.get("practiceCount")).isEqualTo(10);
        assertThat(summary.get("wrongCount")).isEqualTo(2);assertThat(((Number)summary.get("accuracy")).doubleValue()).isEqualTo(80);
        var trend=(List<Map<String,Object>>)statistics.get("trend");assertThat(trend).hasSize(7);
        assertThat(((Number)trend.getFirst().get("practiceCount")).intValue()).isZero();
        List<Map<String,Object>> points=request("/api/knowledge-points",HttpMethod.GET,null,List.class).getBody();
        var root=(Map<?,?>)points.stream().map(x->(Map<?,?>)x).filter(x->x.get("name").equals("资料分析")).findFirst().orElseThrow();
        assertThat(((Number)root.get("mastery")).doubleValue()).isEqualTo(80);
        assertThat(((Number)root.get("questionCount")).intValue()).isEqualTo(1);
        assertThat(request("/api/statistics?days=30",HttpMethod.GET,null,Map.class).getBody().get("trend")).asList().hasSize(30);
        assertThat(request("/api/statistics?days=400",HttpMethod.GET,null,Map.class).getStatusCode().value()).isEqualTo(400);
    }
}
