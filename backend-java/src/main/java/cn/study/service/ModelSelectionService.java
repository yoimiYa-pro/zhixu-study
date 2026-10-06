package cn.study.service;

import cn.study.dto.ModelSelection.*;
import cn.study.repository.Db;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ModelSelectionService {
    private final Db db;
    private final AiClient ai;
    private final ModelCredentials credentials;
    public ModelSelectionService(Db db, AiClient ai, ModelCredentials credentials) { this.db=db;this.ai=ai;this.credentials=credentials; }
    private Catalog defaults() {
        try { return ai.get("/ai/models",Catalog.class); }
        catch (AiClient.Failure e) { return new Catalog("0".repeat(64), "", List.of(), "服务器模型列表暂不可用，可管理自定义模型"); }
    }
    public View catalog() {
        var catalog=defaults();
        return view(catalog,db.one("select * from ai_model_settings where singleton=true"));
    }
    private View view(Catalog catalog,Map<String,Object> row) {
        var connections=connections();
        UUID activeId=row.get("connectionId")==null?null:Db.uuid(row.get("connectionId"));
        String selected=activeId==null && Objects.equals(catalog.providerId(),row.get("providerId"))?(String)row.get("modelName"):null;
        String active=activeId==null?(selected==null?catalog.defaultModel():selected):connections.stream().filter(c->c.id().equals(activeId)).findFirst().orElseThrow().model();
        return new View(active,selected,catalog.defaultModel(),catalog.models(),((Number)row.get("revision")).longValue(),activeId,connections,catalog.message());
    }
    public List<Connection> connections() {
        return db.rows("select id,provider,base_url,model_name,json_mode from ai_model_connections order by created_at,id")
            .stream().map(row->new Connection(Db.uuid(row.get("id")),(String)row.get("provider"),(String)row.get("baseUrl"),(String)row.get("modelName"),(boolean)row.get("jsonMode"),true)).toList();
    }
    private Map<String,Object> settings(long expected) {
        var row=db.one("select * from ai_model_settings where singleton=true");
        if(((Number)row.get("revision")).longValue()!=expected) throw stale();
        return row;
    }
    private void activate(UUID id,String provider,String model,long expected) {
        if(db.update("update ai_model_settings set connection_id=?,provider_id=?,model_name=?,revision=revision+1,updated_at=now() where singleton=true and revision=?",id,provider,model,expected)!=1) throw stale();
    }
    private ResponseStatusException stale() { return new ResponseStatusException(HttpStatus.CONFLICT,"模型设置已更新，请刷新后重试"); }
    private void probe(String base,String key,String model,boolean jsonMode) {
        var result=ai.post("/ai/models/connections/test",Map.of("baseUrl",base,"apiKey",key,"model",model,"jsonMode",jsonMode),Probe.class);
        if(!fingerprint(base).equals(result.providerId()) || !model.equals(result.model())) throw stale();
    }
    @Transactional
    public View select(Choice choice) {
        settings(choice.expectedRevision());
        if(choice.connectionId()!=null) {
            if(choice.model()!=null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请选择一种模型来源");
            var row=db.one("select * from ai_model_connections where id=?",choice.connectionId());
            probe((String)row.get("baseUrl"),credentials.decrypt((String)row.get("apiKeyCiphertext"),choice.connectionId()),(String)row.get("modelName"),(boolean)row.get("jsonMode"));
            activate(choice.connectionId(),null,null,choice.expectedRevision());
            return catalog();
        }
        var catalog=defaults();
        String candidate=choice.model()==null?catalog.defaultModel():choice.model();
        if(candidate.isBlank() || (!candidate.equals(catalog.defaultModel()) && !catalog.models().contains(candidate)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请选择当前服务的可用聊天模型");
        // Validate remotely before the atomic update; existing requests keep using their original model.
        var probe=ai.post("/ai/models/test",Map.of("model",candidate),Probe.class);
        if(!catalog.providerId().equals(probe.providerId()) || !candidate.equals(probe.model()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"模型服务配置已更新，请刷新后重试");
        activate(null,catalog.providerId(),choice.model(),choice.expectedRevision());
        return view(catalog,db.one("select * from ai_model_settings where singleton=true"));
    }
    @Transactional
    public View save(UUID existingId,ConnectionInput input) {
        settings(input.expectedRevision());
        var existing=existingId==null?null:db.one("select * from ai_model_connections where id=?",existingId);
        if(existingId==null && db.count("select count(*) from ai_model_connections")>=20)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"最多保存 20 个模型接入，请先移除不再使用的接入");
        UUID id=existingId==null?UUID.randomUUID():existingId;
        String key=input.apiKey();
        if(key==null || key.isBlank()) {
            if(existing==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请输入 API Key");
            key=credentials.decrypt((String)existing.get("apiKeyCiphertext"),id);
        }
        String base=baseUrl(input.baseUrl());
        boolean jsonMode=input.jsonMode()==null || input.jsonMode();
        probe(base,key,input.model(),jsonMode);
        String encrypted=credentials.encrypt(key,id);
        if(existing==null) db.update("insert into ai_model_connections(id,provider,base_url,model_name,api_key_ciphertext,json_mode) values (?,?,?,?,?,?)",id,input.provider().strip(),base,input.model(),encrypted,jsonMode);
        else db.update("update ai_model_connections set provider=?,base_url=?,model_name=?,api_key_ciphertext=?,json_mode=?,updated_at=now() where id=?",input.provider().strip(),base,input.model(),encrypted,jsonMode,id);
        activate(id,null,null,input.expectedRevision());
        return catalog();
    }
    @Transactional
    public View remove(UUID id,Revision input) {
        var row=settings(input.expectedRevision());
        db.one("select id from ai_model_connections where id=?",id);
        if(id.toString().equals(row.get("connectionId"))) throw new ResponseStatusException(HttpStatus.CONFLICT,"请先切换到其他模型，再移除此接入");
        if(db.update("update ai_model_settings set revision=revision+1,updated_at=now() where singleton=true and revision=?",input.expectedRevision())!=1) throw stale();
        db.update("delete from ai_model_connections where id=?",id);
        return catalog();
    }
    public static String baseUrl(String value) {
        try {
            String base=value.strip().replaceAll("/+$","");
            var uri=URI.create(base);
            String host=uri.getHost();
            if(!"https".equals(uri.getScheme()) || host==null || host.isBlank() || uri.getUserInfo()!=null || uri.getRawQuery()!=null || uri.getRawFragment()!=null
                || uri.getPort()==0 || uri.getPort()>65535 || base.endsWith("/chat/completions") || !base.chars().allMatch(c->c>=33 && c<=126)) throw new IllegalArgumentException();
            return base;
        } catch(IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Base URL 请填写 HTTPS 接口基址，不含密钥、查询参数或 /chat/completions"); }
    }
    public static String fingerprint(String base) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(base.getBytes(StandardCharsets.UTF_8))); }
        catch(GeneralSecurityException e) { throw new IllegalStateException(); }
    }
}
