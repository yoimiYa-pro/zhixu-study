package cn.study;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import static org.assertj.core.api.Assertions.*;

class QuestionIntegrationTest extends ApiIntegrationBase {
    @Test void savesWithoutAiAndDeduplicatesRetriesThenEditsAndArchives() {
        var body=questionBody();
        var first=request("/api/questions",HttpMethod.POST,body,Map.class);
        assertThat(first.getStatusCode().value()).isEqualTo(201);
        String id=first.getBody().get("id").toString();
        assertThat(first.getBody().get("aiStatus")).isEqualTo("PENDING");
        assertThat(first.getBody().get("nextReviewAt")).isNotNull();
        assertThat(request("/api/questions",HttpMethod.POST,body,Map.class).getBody().get("id")).isEqualTo(id);
        assertThat(db.count("select count(*) from study_records")).isEqualTo(1);
        body.put("explanation","增长率 = 增长量 / 基期量");body.put("mistakeReason","计算错误");
        var edited=request("/api/questions/"+id,HttpMethod.PUT,body,Map.class);
        assertThat(edited.getStatusCode().value()).isEqualTo(200);
        assertThat(edited.getBody().get("explanation")).isEqualTo(body.get("explanation"));
        var reason=request("/api/questions/"+id+"/mistake",HttpMethod.PATCH,Map.of("reason","审题错误"),Map.class);
        assertThat(reason.getBody().get("mistakeConfirmed")).isEqualTo(true);
        assertThat(reason.getBody().get("mistakeReason")).isEqualTo("审题错误");
        assertThat(request("/api/questions/"+id,HttpMethod.DELETE,null,String.class).getStatusCode().value()).isEqualTo(204);
        assertThat(request("/api/questions/"+id,HttpMethod.GET,null,Map.class).getStatusCode().value()).isEqualTo(404);
        assertThat(db.count("select count(*) from review_plans")).isEqualTo(0);
        assertThat(db.count("select count(*) from study_records")).isEqualTo(1);
    }
    @Test void invalidAnswersRollbackAllRelatedWrites() {
        var body=questionBody();body.put("correctAnswer","H");
        assertThat(request("/api/questions",HttpMethod.POST,body,Map.class).getStatusCode().value()).isEqualTo(400);
        assertThat(db.count("select count(*) from questions")).isZero();
        assertThat(db.count("select count(*) from ai_tasks")).isZero();
        assertThat(request("/api/questions?pageSize=1000",HttpMethod.GET,null,Map.class).getStatusCode().value()).isEqualTo(400);
    }
    @Test void preventsKnowledgeCyclesAndDeletingReferencedPoints() {
        var root=request("/api/knowledge-points",HttpMethod.POST,Map.of("name","数量关系"),Map.class).getBody();
        var child=request("/api/knowledge-points",HttpMethod.POST,Map.of("name","行程问题","parentId",root.get("id")),Map.class).getBody();
        assertThat(request("/api/knowledge-points/"+root.get("id"),HttpMethod.PUT,Map.of("name","数量关系","parentId",child.get("id")),Map.class).getStatusCode().value()).isEqualTo(400);
        assertThat(request("/api/knowledge-points/"+root.get("id"),HttpMethod.DELETE,null,Map.class).getStatusCode().value()).isEqualTo(409);
    }
}
