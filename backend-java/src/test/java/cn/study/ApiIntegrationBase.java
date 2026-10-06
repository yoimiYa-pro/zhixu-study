package cn.study;

import cn.study.repository.Db;
import cn.study.security.JwtTokens;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.flyway.schemas=integration_test","spring.flyway.default-schema=integration_test",
    "spring.datasource.hikari.schema=integration_test","auth.redis-prefix=study:test",
    "scheduler.enabled=false","ai.worker-enabled=false"
})
abstract class ApiIntegrationBase {
    @Autowired protected Db db;
    @Autowired protected TestRestTemplate http;
    @Autowired protected JwtTokens tokens;
    @BeforeEach void isolatedDatabase() {
        assertThat(db.one("select current_schema() as schema").get("schema")).isEqualTo("integration_test");
        db.update("truncate questions,knowledge_points,essay_materials,current_affairs,idioms,weekly_reports,daily_tasks,chat_messages,chat_conversations,ai_tasks,study_records,scheduler_runs restart identity cascade");
        db.update("update ai_model_settings set provider_id=null,model_name=null,connection_id=null,revision=0 where singleton=true");
        db.update("delete from ai_model_connections");
    }
    protected <T> ResponseEntity<T> request(String path,HttpMethod method,Object body,Class<T> type) {
        var headers=new HttpHeaders();headers.setBearerAuth(tokens.issue());headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(path,method,new HttpEntity<>(body,headers),type);
    }
    protected Map<String,Object> questionBody() {
        var body=new LinkedHashMap<String,Object>();
        body.put("content","某项数据由 100 增长到 120，增长率是多少？");body.put("options",Map.of("A","10%","B","20%"));
        body.put("correctAnswer","B");body.put("userAnswer","A");body.put("questionType","资料分析");
        body.put("difficulty","中等");body.put("knowledgePoints",List.of("增长率"));body.put("source","集成测试，仅存在于测试 schema");
        body.put("clientRequestId",UUID.randomUUID().toString());return body;
    }
    protected UUID createQuestion() {
        var result=request("/api/questions",HttpMethod.POST,questionBody(),Map.class);
        assertThat(result.getStatusCode().value()).isEqualTo(201);return Db.uuid(result.getBody().get("id"));
    }
}
