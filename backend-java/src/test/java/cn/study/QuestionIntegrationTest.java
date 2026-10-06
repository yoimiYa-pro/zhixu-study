package cn.study;

import cn.study.repository.Db;
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
    @Test void reanalysisPreservesSavedWorkAndDeduplicatesActiveRequests() {
        var body=questionBody();body.put("explanation","我的手动解析");body.put("mistakeReason","计算错误");
        UUID id=Db.uuid(request("/api/questions",HttpMethod.POST,body,Map.class).getBody().get("id"));
        var nextReview=db.one("select next_review_at from review_plans where question_id=?",id).get("nextReviewAt");
        assertThat(request("/api/questions/"+id+"/analyze",HttpMethod.POST,Map.of(),Map.class).getStatusCode().value()).isEqualTo(202);
        assertThat(db.count("select count(*) from ai_tasks where kind='ANALYZE_QUESTION' and reference_id=?",id)).isEqualTo(1);
        db.update("update ai_tasks set status='COMPLETED' where reference_id=? and kind='ANALYZE_QUESTION'",id);
        db.update("update questions set analysis_json=?::jsonb where id=?",db.json(Map.of("analysis","上次讲解")),id);
        var concurrent=java.util.concurrent.CompletableFuture.supplyAsync(()->request("/api/questions/"+id+"/analyze",HttpMethod.POST,Map.of(),Map.class));
        var response=request("/api/questions/"+id+"/analyze",HttpMethod.POST,Map.of(),Map.class);
        assertThat(concurrent.join().getStatusCode().value()).isEqualTo(202);
        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(response.getBody().get("aiStatus")).isEqualTo("PENDING");
        assertThat(response.getBody().get("revision")).isEqualTo(2);
        assertThat(response.getBody().get("analysis")).isEqualTo(Map.of("analysis","上次讲解"));
        assertThat(response.getBody().get("explanation")).isEqualTo("我的手动解析");
        assertThat(response.getBody().get("mistakeReason")).isEqualTo("计算错误");
        assertThat(response.getBody().get("mistakeConfirmed")).isEqualTo(true);
        assertThat(db.one("select next_review_at from review_plans where question_id=?",id).get("nextReviewAt")).isEqualTo(nextReview);
        assertThat(request("/api/questions/"+id+"/analyze",HttpMethod.POST,Map.of(),Map.class).getBody().get("revision")).isEqualTo(2);
        assertThat(db.count("select count(*) from ai_tasks where kind='ANALYZE_QUESTION' and reference_id=?",id)).isEqualTo(2);
        db.update("update ai_tasks set status='FAILED' where reference_id=? and kind='ANALYZE_QUESTION'",id);
        assertThat(request("/api/questions/"+id+"/analyze",HttpMethod.POST,Map.of(),Map.class).getBody().get("revision")).isEqualTo(3);
        request("/api/questions/"+id,HttpMethod.DELETE,null,String.class);
        assertThat(request("/api/questions/"+id+"/analyze",HttpMethod.POST,Map.of(),Map.class).getStatusCode().value()).isEqualTo(404);
        assertThat(request("/api/questions/"+UUID.randomUUID()+"/analyze",HttpMethod.POST,Map.of(),Map.class).getStatusCode().value()).isEqualTo(404);
    }
    @Test void preventsKnowledgeCyclesAndDeletingReferencedPoints() {
        var root=request("/api/knowledge-points",HttpMethod.POST,Map.of("name","数量关系"),Map.class).getBody();
        var child=request("/api/knowledge-points",HttpMethod.POST,Map.of("name","行程问题","parentId",root.get("id")),Map.class).getBody();
        assertThat(request("/api/knowledge-points/"+root.get("id"),HttpMethod.PUT,Map.of("name","数量关系","parentId",child.get("id")),Map.class).getStatusCode().value()).isEqualTo(400);
        assertThat(request("/api/knowledge-points/"+root.get("id"),HttpMethod.DELETE,null,Map.class).getStatusCode().value()).isEqualTo(409);
    }
}
