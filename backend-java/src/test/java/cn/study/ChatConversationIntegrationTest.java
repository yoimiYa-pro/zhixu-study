package cn.study;

import cn.study.repository.Db;
import cn.study.service.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import jakarta.validation.Validator;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;

class ChatConversationIntegrationTest extends ApiIntegrationBase {
    @Autowired ChatConversationService conversations;
    @Autowired QuestionService questions;
    @Autowired KnowledgeService knowledge;
    @Autowired DashboardService dashboard;
    @Autowired Validator validator;
    @Autowired ObjectMapper mapper;
    @Autowired DataSource dataSource;

    @Test void conversationCrudIsAuthenticatedAndMessagesArePagedAndIsolated() {
        assertThat(http.getForEntity("/api/chat/conversations",Map.class).getStatusCode().value()).isEqualTo(401);
        UUID first=Db.uuid(request("/api/chat/conversations",HttpMethod.POST,Map.of(),Map.class).getBody().get("id"));
        UUID second=Db.uuid(conversations.create(null).get("id"));
        for(int index=0;index<70;index++) db.update("insert into chat_messages(conversation_id,turn_id,role,content,created_at) values (?,?,'user',?,now()+make_interval(secs=>?))",first,UUID.randomUUID(),"消息 "+index,index);
        db.update("insert into chat_messages(conversation_id,turn_id,role,content) values (?,?,'user','第二个聊天')",second,UUID.randomUUID());
        var result=request("/api/chat/conversations/"+first+"/messages",HttpMethod.GET,null,Map.class);
        assertThat(result.getStatusCode().value()).isEqualTo(200);
        var recent=(List<Map<String,Object>>)result.getBody().get("messages");
        assertThat(recent).hasSize(50);
        assertThat(recent.getFirst().get("content")).isEqualTo("消息 20");
        assertThat(result.getBody().get("hasMore")).isEqualTo(true);
        var older=conversations.messages(first,Db.uuid(recent.getFirst().get("id")),50);
        assertThat((List<?>)older.get("messages")).hasSize(20);
        assertThat(older.get("hasMore")).isEqualTo(false);
        UUID foreign=Db.uuid(conversations.messages(second,null,50).get("messages") instanceof List<?> list?((Map<?,?>)list.getFirst()).get("id"):"");
        assertThat(request("/api/chat/conversations/"+first+"/messages?before="+foreign,HttpMethod.GET,null,Map.class).getStatusCode().value()).isEqualTo(404);
        assertThat(request("/api/chat/conversations/"+first+"/messages?pageSize=1000",HttpMethod.GET,null,Map.class).getStatusCode().value()).isEqualTo(400);
        assertThat(request("/api/chat/conversations/"+first,HttpMethod.PATCH,Map.of("title"," "),Map.class).getStatusCode().value()).isEqualTo(400);
        assertThat(request("/api/chat/conversations/"+first,HttpMethod.PATCH,Map.of("title","资料分析练习"),Map.class).getBody().get("title")).isEqualTo("资料分析练习");
        assertThat(request("/api/chat/conversations/"+first,HttpMethod.DELETE,null,Void.class).getStatusCode().value()).isEqualTo(204);
        assertThat(db.count("select count(*) from chat_messages where conversation_id=?",first)).isZero();
        assertThat(conversations.messages(second,null,50).get("messages")).asList().hasSize(1);
        assertThat(request("/api/chat/conversations/"+first,HttpMethod.GET,null,Map.class).getStatusCode().value()).isEqualTo(404);
    }

    @Test void followingTurnsUseOnlyTheirConversationAndRetryDoesNotRepeatTheModel() throws Exception {
        var payloads=new CopyOnWriteArrayList<JsonNode>();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/ai/search/similar",exchange->{byte[] bytes="[]".getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});
        server.createContext("/ai/chat",exchange->{
            payloads.add(mapper.readTree(exchange.getRequestBody()));
            byte[] bytes="{\"answer\":\"当前会话的测试回答\",\"citations\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });server.start();
        try {
            var client=new AiClient("http://127.0.0.1:"+server.getAddress().getPort(),"test-only",5,validator);
            var service=new AssistantService(db,new RagService(db,client,questions,knowledge),client,dashboard,conversations);
            UUID first=Db.uuid(conversations.create(null).get("id")),second=Db.uuid(conversations.create(null).get("id"));
            UUID turn=UUID.randomUUID();
            var response=service.chat(first,turn,"第一会话专属学习暗号",null);
            assertThat(payloads.getFirst().get("history")).isEmpty();
            assertThat(service.chat(first,turn,"第一会话专属学习暗号",null).get("id")).isEqualTo(response.get("id"));
            assertThat(payloads).hasSize(1);
            assertThatThrownBy(()->service.chat(second,turn,"第一会话专属学习暗号",null)).isInstanceOf(ResponseStatusException.class);
            service.chat(second,UUID.randomUUID(),"第二会话的问题",null);
            assertThat(payloads.get(1).get("history")).isEmpty();
            service.chat(first,UUID.randomUUID(),"接着解释刚才的内容",null);
            assertThat(payloads.get(2).get("history")).hasSize(2);
            assertThat(payloads.get(2).get("history").toString()).contains("第一会话专属学习暗号").doesNotContain("第二会话的问题");
            assertThat(conversations.get(first).get("title")).isEqualTo("第一会话专属学习暗号");
            assertThat(conversations.get(first).get("isGenerating")).isEqualTo(false);
            assertThat(conversations.history(first)).hasSize(4);
            conversations.delete(first);
            assertThat(conversations.history(second)).hasSize(2);
        } finally { server.stop(0); }
    }

    @Test void deletingDuringGenerationCannotResurrectTheConversation() throws Exception {
        UUID id=Db.uuid(conversations.create(null).get("id"));
        var arrived=new CountDownLatch(1);var release=new CountDownLatch(1);
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/ai/search/similar",exchange->{byte[] bytes="[]".getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});
        server.createContext("/ai/chat",exchange->{
            exchange.getRequestBody().readAllBytes();arrived.countDown();
            try { release.await(10,TimeUnit.SECONDS); } catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            byte[] bytes="{\"answer\":\"已删除会话的迟到回复\",\"citations\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });server.start();
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var client=new AiClient("http://127.0.0.1:"+server.getAddress().getPort(),"test-only",5,validator);
            var service=new AssistantService(db,new RagService(db,client,questions,knowledge),client,dashboard,conversations);
            var pending=executor.submit(()->service.chat(id,UUID.randomUUID(),"等待回答",null));
            assertThat(arrived.await(5,TimeUnit.SECONDS)).isTrue();
            assertThat(conversations.get(id).get("isGenerating")).isEqualTo(true);
            assertThatThrownBy(()->service.chat(id,UUID.randomUUID(),"重复并发消息",null)).isInstanceOf(ResponseStatusException.class);
            assertThat(db.count("select count(*) from chat_messages where conversation_id=?",id)).isEqualTo(1);
            conversations.delete(id);release.countDown();
            assertThatThrownBy(()->pending.get(5,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class).hasCauseInstanceOf(ResponseStatusException.class);
            assertThat(db.count("select count(*) from chat_messages where conversation_id=?",id)).isZero();
            assertThat(db.count("select count(*) from chat_conversations where id=?",id)).isZero();
        } finally { release.countDown();server.stop(0); }
    }

    @Test void migrationKeepsLegacyMessagesAndCitations() throws Exception {
        String schema="migration_chat_"+UUID.randomUUID().toString().replace("-","");
        UUID user=UUID.randomUUID(),answer=UUID.randomUUID(),turn=UUID.randomUUID();
        try {
            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("9").load().migrate();
            db.update("insert into "+schema+".chat_messages(id,turn_id,role,content) values (?,?,'user','原有学习问题')",user,turn);
            String citations="[{\"entityType\":\"knowledge\",\"entityId\":\""+UUID.randomUUID()+"\",\"title\":\"原有引用\"}]";
            db.update("insert into "+schema+".chat_messages(id,turn_id,role,content,citations_json,retrieval_status) values (?,?,'assistant','## 原有回答',?::jsonb,'VECTOR_UNAVAILABLE')",answer,turn,citations);
            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).load().migrate();
            var migrated=db.rows("select * from "+schema+".chat_messages order by role");
            assertThat(migrated).hasSize(2);
            assertThat(migrated).extracting(row->row.get("id")).containsExactlyInAnyOrder(user.toString(),answer.toString());
            assertThat(migrated.getFirst().get("conversationId")).isEqualTo(migrated.getLast().get("conversationId"));
            var saved=migrated.stream().filter(row->row.get("role").equals("assistant")).findFirst().orElseThrow();
            assertThat(saved.get("content")).isEqualTo("## 原有回答");
            assertThat(mapper.<com.fasterxml.jackson.databind.JsonNode>valueToTree(saved.get("citations"))).isEqualTo(mapper.readTree(citations));
            assertThat(saved.get("retrievalStatus")).isEqualTo("VECTOR_UNAVAILABLE");
            assertThat(db.count("select count(*) from "+schema+".chat_conversations")).isEqualTo(1);
        } finally { db.update("drop schema if exists "+schema+" cascade"); }
    }
}
