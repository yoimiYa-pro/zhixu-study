package cn.study;

import cn.study.dto.ModelSelection.*;
import cn.study.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import jakarta.validation.Validator;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;

class CustomModelIntegrationTest extends ApiIntegrationBase {
    @Autowired ModelCredentials credentials;
    @Autowired Validator validator;
    @Autowired PlatformTransactionManager transactions;
    private final String base="https://models.example/v1";
    private final String secret="test-only-custom-key";
    private HttpServer server(boolean succeeds,List<String> calls,Runnable onProbe) throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/ai/models",exchange->{
            String path=exchange.getRequestURI().getPath();
            boolean custom=path.equals("/ai/models/connections/test");
            boolean test=path.endsWith("/test");
            Object result;
            if(custom) {
                var body=new ObjectMapper().readValue(exchange.getRequestBody().readAllBytes(),Map.class);
                calls.add((String)body.get("apiKey"));
                if(onProbe!=null) onProbe.run();
                result=Map.of("available",true,"providerId",ModelSelectionService.fingerprint((String)body.get("baseUrl")),"model",body.get("model"));
            } else if(test) result=Map.of("available",true,"providerId","a".repeat(64),"model","chat-default");
            else result=Map.of("providerId","a".repeat(64),"defaultModel","chat-default","models",List.of("chat-default"));
            if(custom&&!succeeds) result=Map.of("code","PROVIDER_UNAVAILABLE");
            byte[] bytes=db.json(result).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(custom&&!succeeds?502:200,bytes.length);
            exchange.getResponseBody().write(bytes);exchange.close();
        });
        for(String path:List.of("/ai/chat","/ai/question/embed")) server.createContext(path,exchange->{
            for(String name:List.of("X-Study-Model","X-Study-Base-Url","X-Study-Api-Key","X-Study-Json-Mode"))
                calls.add(Objects.requireNonNullElse(exchange.getRequestHeaders().getFirst(name),"none"));
            byte[] bytes="{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);
            exchange.getResponseBody().write(bytes);exchange.close();
        });
        server.start();return server;
    }
    private AiClient client(HttpServer server) { return new AiClient("http://127.0.0.1:"+server.getAddress().getPort(),"test-only",5,validator,db,credentials); }
    private ConnectionInput input(String key,String model,long revision) { return new ConnectionInput("我的模型",base,key,model,false,revision); }
    @Test void saveEncryptsKeyAndUsesConnectionOnlyForChatCalls() throws Exception {
        var calls=new ArrayList<String>();var server=server(true,calls,null);
        try {
            var service=new ModelSelectionService(db,client(server),credentials);
            var saved=service.save(null,input(secret,"custom-chat",0));
            assertThat(saved.activeConnectionId()).isNotNull();
            assertThat(saved.activeModel()).isEqualTo("custom-chat");
            assertThat(db.json(saved)).doesNotContain(secret,"apiKeyCiphertext");
            var encrypted=(String)db.one("select * from ai_model_connections").get("apiKeyCiphertext");
            assertThat(encrypted).startsWith("v1:").doesNotContain(secret);
            assertThat(credentials.decrypt(encrypted,saved.activeConnectionId())).isEqualTo(secret);
            assertThatThrownBy(()->credentials.decrypt(encrypted,UUID.randomUUID())).isInstanceOf(AiClient.Failure.class);
            assertThat(new ModelSelectionService(db,client(server),credentials).catalog().activeConnectionId()).isEqualTo(saved.activeConnectionId());
            calls.clear();client(server).post("/ai/chat",Map.of(),Map.class);client(server).post("/ai/question/embed",Map.of(),Map.class);
            assertThat(calls).containsExactly("custom-chat",base,secret,"false","none","none","none","none");
        } finally { server.stop(0); }
    }
    @Test void editRetainsOrReplacesKeyAndActiveConnectionCannotBeRemoved() throws Exception {
        var calls=new ArrayList<String>();var server=server(true,calls,null);
        try {
            var service=new ModelSelectionService(db,client(server),credentials);
            var saved=service.save(null,input(secret,"custom-chat",0));
            UUID id=saved.activeConnectionId();
            var edited=service.save(id,input(null,"custom-edited",1));
            assertThat(edited.activeModel()).isEqualTo("custom-edited");
            assertThat(calls).containsExactly(secret,secret);
            service.save(id,input("replacement-test-key","custom-edited",2));
            assertThat(calls).last().isEqualTo("replacement-test-key");
            assertThatThrownBy(()->service.remove(id,new Revision(3L))).isInstanceOf(ResponseStatusException.class);
            assertThat(service.catalog().revision()).isEqualTo(3);
            service.select(new Choice(null,3L));
            assertThat(service.remove(id,new Revision(4L)).connections()).isEmpty();
            assertThat(db.count("select count(*) from ai_model_connections")).isZero();
        } finally { server.stop(0); }
    }
    @Test void failedProbeStaleRevisionAndInvalidBaseDoNotSave() throws Exception {
        var server=server(false,new ArrayList<>(),null);
        try {
            var service=new ModelSelectionService(db,client(server),credentials);
            assertThatThrownBy(()->service.save(null,input(secret,"custom-chat",0))).isInstanceOf(AiClient.Failure.class);
            assertThatThrownBy(()->service.save(null,input(secret,"custom-chat",99))).isInstanceOf(ResponseStatusException.class);
            assertThatThrownBy(()->service.save(null,new ConnectionInput("测试","https://user:pass@models.example/v1",secret,"custom-chat",true,0L))).isInstanceOf(ResponseStatusException.class);
            assertThat(db.count("select count(*) from ai_model_connections")).isZero();
            assertThat(service.catalog().revision()).isZero();
        } finally { server.stop(0); }
    }
    @Test void concurrentChangeRollsBackTheNewConnection() throws Exception {
        var once=new AtomicBoolean();
        var server=server(true,new ArrayList<>(),()->{if(once.compareAndSet(false,true)) db.update("update ai_model_settings set revision=revision+1 where singleton=true");});
        try {
            var service=new ModelSelectionService(db,client(server),credentials);
            var transaction=new TransactionTemplate(transactions);
            assertThatThrownBy(()->transaction.execute(status->service.save(null,input(secret,"custom-chat",0)))).isInstanceOf(ResponseStatusException.class);
            assertThat(db.count("select count(*) from ai_model_connections")).isZero();
            assertThat(service.catalog().revision()).isEqualTo(1);
        } finally { server.stop(0); }
    }
    @Test void connectionApiRequiresAuthAndValidationNeverEchoesKey() {
        assertThat(http.getForEntity("/api/ai-models/connections",Map.class).getStatusCode().value()).isEqualTo(401);
        var response=request("/api/ai-models/connections",HttpMethod.POST,Map.of("provider","测试","baseUrl",base,"apiKey",secret+"\r\n","model","custom-chat","expectedRevision",0),Map.class);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(db.json(response.getBody())).doesNotContain(secret);
    }
}
