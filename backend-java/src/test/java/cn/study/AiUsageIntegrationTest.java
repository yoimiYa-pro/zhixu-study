package cn.study;

import cn.study.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import jakarta.validation.Validator;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

class AiUsageIntegrationTest extends ApiIntegrationBase {
    @Autowired AiUsageService usage;
    @Autowired ObjectMapper mapper;
    @Autowired Validator validator;
    @Autowired PlatformTransactionManager transactions;
    @BeforeEach void cleanUsage() { db.update("delete from ai_usage"); }
    @AfterEach void removeUsage() { db.update("delete from ai_usage"); }
    private Map<String,Object> event(String model,String kind,long calls,long reported,long incomplete,long input,long output,long total,long cached) {
        var row=new LinkedHashMap<String,Object>();
        row.put("id",UUID.randomUUID().toString());row.put("providerKey","a".repeat(64));row.put("providerName","models.example.com");
        row.put("model",model);row.put("kind",kind);row.put("calls",calls);row.put("reportedCalls",reported);row.put("incompleteCalls",incomplete);
        row.put("inputTokens",input);row.put("outputTokens",output);row.put("totalTokens",total);row.put("cachedInputTokens",cached);row.put("cacheReportedCalls",cached>0?reported:0);
        return row;
    }
    private String header(Map<String,Object>... events) { return db.json(Map.of("version",1,"events",List.of(events))); }
    private long value(Map row,String key) { return ((Number)row.get(key)).longValue(); }
    @Test void requiresAuthenticationValidatesRangeAndReturnsEmptyCalendar() {
        assertThat(http.getForEntity("/api/ai/usage",Map.class).getStatusCode().value()).isEqualTo(401);
        var response=request("/api/ai/usage?days=7",HttpMethod.GET,null,Map.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(value((Map)response.getBody().get("totals"),"calls")).isZero();
        assertThat((List)response.getBody().get("daily")).hasSize(7);
        assertThat((List)response.getBody().get("models")).isEmpty();
        assertThat(response.getBody().get("firstRecordedAt")).isNull();
        for(int days:List.of(0,91)) assertThat(request("/api/ai/usage?days="+days,HttpMethod.GET,null,Map.class).getStatusCode().value()).isEqualTo(400);
    }
    @Test void aggregatesModelsAndMissingUsageWithoutCountingHeaderTwice() {
        String header=header(event("chat-model","CHAT",1,1,0,120,30,150,80),event("vector-model","EMBEDDING",2,2,0,40,0,40,0),
            event("no-usage-model","CHAT",1,0,1,0,0,0,0),event("partial-model","CHAT",1,1,1,0,0,99,0));
        usage.record(header);usage.record(header);
        var result=usage.summary(7);var totals=(Map)result.get("totals");
        assertThat(db.count("select count(*) from ai_usage")).isEqualTo(4);
        assertThat(value(totals,"calls")).isEqualTo(5);assertThat(value(totals,"reportedCalls")).isEqualTo(4);
        assertThat(value(totals,"unreportedCalls")).isEqualTo(1);assertThat(value(totals,"incompleteCalls")).isEqualTo(2);
        assertThat(value(totals,"inputTokens")).isEqualTo(160);assertThat(value(totals,"outputTokens")).isEqualTo(30);
        assertThat(value(totals,"totalTokens")).isEqualTo(289);assertThat(value(totals,"cachedInputTokens")).isEqualTo(80);
        assertThat((List)result.get("models")).hasSize(4);
        assertThat(value((Map)((List)result.get("daily")).getLast(),"totalTokens")).isEqualTo(289);
        assertThat(result.get("firstRecordedAt")).isNotNull();
        assertThat(db.json(result)).doesNotContain("apiKey","prompt","questionContent");
    }
    @Test void recordsUsageFromInternalContainerHostnames() {
        var row=event("chat-model","CHAT",1,1,0,120,30,150,0);
        row.put("providerName","study_model_provider-1");
        usage.record(header(row));
        assertThat(value((Map)usage.summary(1).get("totals"),"totalTokens")).isEqualTo(150);
        assertThat(db.one("select provider_name from ai_usage")).containsEntry("providerName","study_model_provider-1");
    }
    @Test void persistsProviderHeadersBeforeErrorOrBodyDeserialization() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/ai/failure",exchange->{
            byte[] body="{\"code\":\"AI_INVALID_JSON\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.getResponseHeaders().set("X-Study-Usage",header(event("chat-model","CHAT",1,1,0,120,30,150,0)));
            exchange.sendResponseHeaders(502,body.length);exchange.getResponseBody().write(body);exchange.close();
        });
        server.createContext("/ai/invalid-body",exchange->{
            byte[] body="not-json".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.getResponseHeaders().set("X-Study-Usage",header(event("chat-model","CHAT",1,1,0,120,30,150,0)));
            exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();
        });server.start();
        try {
            var client=new AiClient("http://127.0.0.1:"+server.getAddress().getPort(),"test-only",5,validator,null,null,usage);
            assertThatThrownBy(()->client.post("/ai/failure",Map.of(),Map.class)).isInstanceOf(AiClient.Failure.class).hasMessage("AI_INVALID_JSON");
            assertThatThrownBy(()->client.post("/ai/invalid-body",Map.of(),Map.class)).isInstanceOf(AiClient.Failure.class).hasMessage("AI_UNAVAILABLE");
            var totals=(Map)usage.summary(1).get("totals");
            assertThat(value(totals,"calls")).isEqualTo(2);assertThat(value(totals,"totalTokens")).isEqualTo(300);
        } finally { server.stop(0); }
    }
    @Test void rejectsInvalidTelemetryAsAWholeWithoutBreakingBusinessCalls() {
        usage.record("not-json");usage.record("x".repeat(32769));usage.record("{\"version\":2,\"events\":[]}");
        var invalid=event("chat-model","CHAT",1,1,0,120,30,150,0);invalid.put("totalTokens",-1);
        usage.record(header(event("valid-model","CHAT",1,1,0,120,30,150,0),invalid));
        invalid=event("chat-model","CHAT",1,1,0,120,30,150,0);invalid.put("model","<script>secret</script>");usage.record(header(invalid));
        assertThat(db.count("select count(*) from ai_usage")).isZero();
    }
    @Test void usesServerTimezoneAtBothCalendarBoundaries() {
        Clock clock=Clock.fixed(Instant.parse("2026-10-06T03:00:00Z"),ZoneId.of("Asia/Shanghai"));
        var fixed=new AiUsageService(db,mapper,clock,transactions);
        for(String time:List.of("2026-10-05T15:59:59.999Z","2026-10-05T16:00:00Z","2026-10-06T15:59:59.999Z","2026-10-06T16:00:00Z")) {
            var row=event("chat-model","CHAT",1,1,0,120,30,150,0);fixed.record(header(row));
            db.update("update ai_usage set recorded_at=? where id=?",java.sql.Timestamp.from(Instant.parse(time)),UUID.fromString(row.get("id").toString()));
        }
        assertThat(value((Map)fixed.summary(1).get("totals"),"calls")).isEqualTo(2);
        var result=fixed.summary(3);var days=(List<Map>)result.get("daily");
        assertThat(days).extracting(day->day.get("date")).containsExactly("2026-10-04","2026-10-05","2026-10-06");
        assertThat(days).extracting(day->value(day,"calls")).containsExactly(0L,1L,2L);
        assertThat(value((Map)result.get("totals"),"totalTokens")).isEqualTo(450);
    }
    @Test void usageSurvivesBusinessTransactionRollback() {
        var transaction=new TransactionTemplate(transactions);
        assertThatThrownBy(()->transaction.executeWithoutResult(status->{
            db.update("insert into study_records(kind,quantity,wrong_count,time_spent) values('PRACTICE',1,0,1)");
            usage.record(header(event("chat-model","CHAT",1,1,0,120,30,150,0)));
            throw new IllegalStateException("Business validation failed after provider returned");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(db.count("select count(*) from study_records")).isZero();
        assertThat(db.count("select count(*) from ai_usage")).isEqualTo(1);
    }
}
