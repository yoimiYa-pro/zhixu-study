package cn.study;

import cn.study.service.ReviewRule;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import static org.assertj.core.api.Assertions.*;

class ReviewIntegrationTest extends ApiIntegrationBase {
    @Test void correctReviewIsVerifiedAndSubmissionIsIdempotent() {
        UUID id=createQuestion();db.update("update review_plans set next_review_at=now()-interval '1 day' where question_id=?",id);
        var due=request("/api/reviews/today",HttpMethod.GET,null,List.class);assertThat(due.getBody()).hasSize(1);
        var body=Map.of("requestId",UUID.randomUUID(),"result","INCORRECT","answer","B","confidence",5,"timeSpent",30);
        var first=request("/api/reviews/"+id+"/complete",HttpMethod.POST,body,Map.class);
        assertThat(first.getStatusCode().value()).isEqualTo(200);assertThat(first.getBody().get("outcome")).isEqualTo("CORRECT");
        long days=Duration.between(Instant.now(),Instant.parse(first.getBody().get("nextReviewAt").toString())).toHours();assertThat(days).isBetween(71L,72L);
        assertThat(request("/api/reviews/"+id+"/complete",HttpMethod.POST,body,Map.class).getBody().get("id")).isEqualTo(first.getBody().get("id"));
        assertThat(db.count("select count(*) from reviews")).isEqualTo(1);
        assertThat(request("/api/reviews/today",HttpMethod.GET,null,List.class).getBody()).isEmpty();
    }
    @Test void errorsShortenIntervalsAndSkipsDoNotCountAsCorrect() {
        assertThat(ReviewRule.next(3,false,5,"中等",90,4).days()).isEqualTo(1);
        assertThat(ReviewRule.next(0,true,5,"中等",90,0).days()).isEqualTo(3);
        assertThat(ReviewRule.next(1,true,5,"中等",90,1).days()).isEqualTo(7);
        assertThat(ReviewRule.next(2,true,5,"中等",90,2).days()).isEqualTo(14);
        assertThat(ReviewRule.next(3,true,5,"中等",90,3).days()).isEqualTo(30);
        UUID id=createQuestion();db.update("update review_plans set next_review_at=now()-interval '1 day' where question_id=?",id);
        var result=request("/api/reviews/"+id+"/complete",HttpMethod.POST,Map.of("requestId",UUID.randomUUID(),"result","SKIP","confidence",1,"timeSpent",5),Map.class);
        assertThat(result.getBody().get("outcome")).isEqualTo("SKIP");assertThat(db.count("select correct_count from review_plans where question_id=?",id)).isZero();
    }
}
