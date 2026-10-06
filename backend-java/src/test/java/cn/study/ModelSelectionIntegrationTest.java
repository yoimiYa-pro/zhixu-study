package cn.study;

import cn.study.dto.ModelSelection.*;
import cn.study.service.*;
import com.sun.net.httpserver.HttpServer;
import jakarta.validation.Validator;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;

class ModelSelectionIntegrationTest extends ApiIntegrationBase {
    @Autowired Validator validator;
    @Autowired ModelCredentials credentials;
    private final String provider="a".repeat(64);
    private HttpServer server(boolean succeeds,List<String> headers) throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/ai/models",exchange->{
            boolean test=exchange.getRequestURI().getPath().endsWith("/test");
            String body=test?new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8):"";
            Object result=test?(succeeds?Map.of("available",true,"providerId",provider,"model",body.contains("chat-second")?"chat-second":"chat-first"):Map.of("code","AI_MODEL_UNAVAILABLE")):
                Map.of("providerId",provider,"defaultModel","chat-first","models",List.of("chat-first","chat-second"));
            byte[] bytes=db.json(result).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(test&&!succeeds?502:200,bytes.length);
            exchange.getResponseBody().write(bytes);exchange.close();
        });
        server.createContext("/ai/chat",exchange->{
            headers.add(Objects.requireNonNullElse(exchange.getRequestHeaders().getFirst("X-Study-Model"),"none"));
            headers.add(Objects.requireNonNullElse(exchange.getRequestHeaders().getFirst("X-Study-Provider"),"none"));
            byte[] bytes="{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);
            exchange.getResponseBody().write(bytes);exchange.close();
        });
        server.createContext("/ai/question/embed",exchange->{
            headers.add(Objects.requireNonNullElse(exchange.getRequestHeaders().getFirst("X-Study-Model"),"none"));
            byte[] bytes="{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);
            exchange.getResponseBody().write(bytes);exchange.close();
        });server.start();return server;
    }
    private AiClient client(HttpServer server) {
        return new AiClient("http://127.0.0.1:"+server.getAddress().getPort(),"test-only",5,validator,db);
    }
    @Test void successfulSwitchPersistsAndSubsequentClientUsesIt() throws Exception {
        var headers=new ArrayList<String>();var server=server(true,headers);
        try {
            var service=new ModelSelectionService(db,client(server),credentials);
            assertThat(service.catalog().activeModel()).isEqualTo("chat-first");
            var switched=service.select(new Choice("chat-second",0L));
            assertThat(switched.activeModel()).isEqualTo("chat-second");
            assertThat(new ModelSelectionService(db,client(server),credentials).catalog().activeModel()).isEqualTo("chat-second");
            client(server).post("/ai/chat",Map.of(),Map.class);
            client(server).post("/ai/question/embed",Map.of(),Map.class);
            assertThat(headers).containsExactly("chat-second",provider,"none");
            assertThat(service.select(new Choice(null,1L)).activeModel()).isEqualTo("chat-first");
            assertThat(service.catalog().selectedModel()).isNull();
        } finally { server.stop(0); }
    }
    @Test void failedProbeUnknownModelAndStaleRevisionDoNotSave() throws Exception {
        var server=server(false,new ArrayList<>());
        try {
            var service=new ModelSelectionService(db,client(server),credentials);
            assertThatThrownBy(()->service.select(new Choice("chat-second",0L))).isInstanceOf(AiClient.Failure.class);
            assertThat(service.catalog().revision()).isZero();
            assertThat(service.catalog().selectedModel()).isNull();
            assertThatThrownBy(()->service.select(new Choice("unknown",0L))).isInstanceOf(ResponseStatusException.class);
            assertThatThrownBy(()->service.select(new Choice("chat-second",9L))).isInstanceOf(ResponseStatusException.class);
            assertThat(service.catalog().revision()).isZero();
        } finally { server.stop(0); }
    }
    @Test void modelSettingsAreAuthenticatedAndInputIsValidated() {
        assertThat(http.getForEntity("/api/ai-models",Map.class).getStatusCode().value()).isEqualTo(401);
        assertThat(request("/api/ai-models",HttpMethod.POST,Map.of("model","bad header","expectedRevision",0),Map.class).getStatusCode().value()).isEqualTo(400);
    }
}
