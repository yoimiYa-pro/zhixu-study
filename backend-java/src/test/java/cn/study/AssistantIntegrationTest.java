package cn.study;
import cn.study.service.*;
import cn.study.repository.Db;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import jakarta.validation.Validator;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.*;
class AssistantIntegrationTest extends ApiIntegrationBase {
    @Autowired QuestionService questions;@Autowired KnowledgeService knowledge;@Autowired DashboardService dashboard;
    @Autowired Validator validator;@Autowired ObjectMapper mapper;
    @Autowired ChatConversationService conversations;
    @Test void databaseRetrievalPrecedesModelAndInvalidCitationCannotBeSaved() throws Exception {
        UUID question=createQuestion();var calls=new AtomicInteger();var invalid=new boolean[]{false};
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/ai/search/similar",exchange->{byte[] data="{\"code\":\"QDRANT_UNAVAILABLE\"}".getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(503,data.length);exchange.getResponseBody().write(data);exchange.close();});
        server.createContext("/ai/chat",exchange->{
            var input=mapper.readTree(exchange.getRequestBody());
            assertThat(input.get("contexts").toString()).contains(question.toString()).contains("增长率");
            assertThat(input.get("learningStatus").get("stats").get("practiceCount").asInt()).isEqualTo(1);
            calls.incrementAndGet();String response=db.json(Map.of("answer","仅测试响应：增长率按基期量计算","citations",List.of(Map.of("entityType","question","entityId",invalid[0]?UUID.randomUUID():question))));
            byte[] data=response.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,data.length);exchange.getResponseBody().write(data);exchange.close();
        });server.start();
        try {
            var client=new AiClient("http://127.0.0.1:"+server.getAddress().getPort(),"test-only",5,validator);
            var rag=new RagService(db,client,questions,knowledge);var assistant=new AssistantService(db,rag,client,dashboard,conversations);
            UUID turn=UUID.randomUUID();var result=assistant.chat(turn,"解释这道题",question);
            assertThat(result.get("retrievalStatus")).isEqualTo("VECTOR_UNAVAILABLE");
            assertThat(assistant.chat(turn,"解释这道题",question).get("id")).isEqualTo(result.get("id"));assertThat(calls.get()).isEqualTo(1);
            invalid[0]=true;
            assertThatThrownBy(()->assistant.chat(UUID.randomUUID(),"再次解释",question)).isInstanceOf(AiClient.Failure.class).hasMessage("AI_INVALID_OUTPUT");
            assertThat(db.count("select count(*) from chat_messages where role='assistant'")).isEqualTo(1);
            assertThat(questions.get(question)).isNotEmpty();
        } finally { server.stop(0); }
    }
}
