package cn.study.service;

import java.util.*;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import cn.study.repository.Db;

@Component
public class AiClient {
    public static class Failure extends RuntimeException {
        public final String code;
        public Failure(String code) { super(code); this.code=code; }
    }
    private final RestClient client;private final Validator validator;private final Db db;private final ModelCredentials credentials;
    private final AiUsageService usage;
    private static final Set<String> LLM_PATHS=Set.of("/ai/question/analyze","/ai/chat","/ai/essay-assistant","/ai/weekly-report","/ai/current-affairs/analyze");
    private static final Set<String> CODES=Set.of("LLM_NOT_CONFIGURED","EMBEDDING_NOT_CONFIGURED","PROVIDER_UNAVAILABLE","AI_INVALID_JSON","AI_INVALID_OUTPUT","AI_MODELS_UNAVAILABLE","AI_MODEL_INVALID","AI_MODEL_UNAVAILABLE","AI_URL_REJECTED","EMBEDDING_INVALID_OUTPUT","QDRANT_UNAVAILABLE","NEWS_FEEDS_EMPTY","NEWS_FETCH_FAILED","NEWS_URL_REJECTED","NEWS_SOURCE_LIMIT","INTERNAL_AUTH_REQUIRED","INTERNAL_TOKEN_NOT_CONFIGURED");
    public AiClient(@Value("${ai.service-url:http://localhost:8000}") String url,@Value("${AI_SERVICE_TOKEN:}") String token,@Value("${AI_TIMEOUT_SECONDS:60}") int timeout,Validator validator) {
        this(url,token,timeout,validator,null);
    }
    public AiClient(@Value("${ai.service-url:http://localhost:8000}") String url,@Value("${AI_SERVICE_TOKEN:}") String token,@Value("${AI_TIMEOUT_SECONDS:60}") int timeout,Validator validator,Db db) {
        this(url,token,timeout,validator,db,null);
    }
    public AiClient(@Value("${ai.service-url:http://localhost:8000}") String url,@Value("${AI_SERVICE_TOKEN:}") String token,@Value("${AI_TIMEOUT_SECONDS:60}") int timeout,Validator validator,Db db,ModelCredentials credentials) {
        this(url,token,timeout,validator,db,credentials,null);
    }
    @Autowired
    public AiClient(@Value("${ai.service-url:http://localhost:8000}") String url,@Value("${AI_SERVICE_TOKEN:}") String token,@Value("${AI_TIMEOUT_SECONDS:60}") int timeout,Validator validator,Db db,ModelCredentials credentials,AiUsageService usage) {
        var factory=new SimpleClientHttpRequestFactory();factory.setConnectTimeout(3000);factory.setReadTimeout((timeout+10)*1000);
        this.usage=usage;
        client=RestClient.builder().baseUrl(url).defaultHeader(HttpHeaders.AUTHORIZATION,"Bearer "+token).requestFactory(factory)
            .requestInterceptor((request,body,execution)-> { var response=execution.execute(request,body);recordUsage(response.getHeaders());return response; }).build();
        this.validator=validator;this.db=db;this.credentials=credentials;
    }
    private void modelHeaders(RestClient.RequestHeadersSpec<?> request) {
        if(db==null) return;
        var rows=db.rows("select s.provider_id,s.model_name,s.connection_id,c.base_url,c.model_name as custom_model,c.api_key_ciphertext,c.json_mode from ai_model_settings s left join ai_model_connections c on c.id=s.connection_id where s.singleton=true");
        if(!rows.isEmpty() && rows.getFirst().get("connectionId")!=null) {
            var row=rows.getFirst();
            if(credentials==null) throw new Failure("AI_CREDENTIALS_UNAVAILABLE");
            String base=(String)row.get("baseUrl");
            request.header("X-Study-Model",(String)row.get("customModel"))
                .header("X-Study-Provider",ModelSelectionService.fingerprint(base))
                .header("X-Study-Base-Url",base)
                .header("X-Study-Api-Key",credentials.decrypt((String)row.get("apiKeyCiphertext"),Db.uuid(row.get("connectionId"))))
                .header("X-Study-Json-Mode",row.get("jsonMode").toString());
            return;
        }
        if(!rows.isEmpty() && rows.getFirst().get("modelName")!=null && rows.getFirst().get("providerId")!=null)
            request.header("X-Study-Model",rows.getFirst().get("modelName").toString()).header("X-Study-Provider",rows.getFirst().get("providerId").toString());
    }
    public <T> T post(String path,Object payload,Class<T> type) {
        try {
            var request=client.post().uri(path);
            if(LLM_PATHS.contains(path)) modelHeaders(request);
            T result=request.body(payload).retrieve().body(type);
            validate(result);
            return result;
        } catch (RestClientResponseException e) {
            try {
                var value=e.getResponseBodyAs(Map.class);Object code=value==null?null:value.get("code");
                if(code!=null && CODES.contains(code.toString())) throw new Failure(code.toString());
            } catch (Failure failure) { throw failure; }
            catch (RuntimeException ignored) {}
            throw new Failure("AI_UNAVAILABLE");
        } catch (RestClientException e) { throw new Failure("AI_UNAVAILABLE"); }
    }
    private void recordUsage(HttpHeaders headers) { if(usage!=null && headers!=null) usage.record(headers.getFirst("X-Study-Usage")); }
    public <T> T get(String path,Class<T> type) {
        try {
            T result=client.get().uri(path).retrieve().body(type);validate(result);return result;
        } catch(RestClientResponseException e) {
            try {
                var value=e.getResponseBodyAs(Map.class);Object code=value==null?null:value.get("code");
                if(code!=null && CODES.contains(code.toString())) throw new Failure(code.toString());
            } catch(Failure failure) { throw failure; }
            catch(RuntimeException ignored) {}
            throw new Failure("AI_UNAVAILABLE");
        } catch(RestClientException e) { throw new Failure("AI_UNAVAILABLE"); }
    }
    private void validate(Object result) {
        if(result==null) throw new Failure("AI_INVALID_OUTPUT");
        if(result instanceof Object[] array) { for(var item:array) validate(item); }
        else if(result instanceof Collection<?> collection) { for(var item:collection) validate(item); }
        else if(!validator.validate(result).isEmpty()) throw new Failure("AI_INVALID_OUTPUT");
    }
    public Map<String,Object> status() {
        try { var request=client.get().uri("/ai/status");modelHeaders(request);return request.retrieve().body(Map.class); }
        catch (RestClientException e) { return Map.of("serviceAvailable",false,"llmConfigured",false,"embeddingConfigured",false); }
    }
}
