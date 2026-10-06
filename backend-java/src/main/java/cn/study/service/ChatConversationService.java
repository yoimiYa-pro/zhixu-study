package cn.study.service;

import cn.study.repository.Db;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChatConversationService {
    public record Started(UUID conversationId, UUID requestId, UUID questionId, Map<String,Object> completed) {}
    private final Db db;
    private final TransactionTemplate transaction;
    private final int leaseSeconds;
    private static final String SUMMARY = """
        select c.id,c.title,c.title_manual,c.question_id,c.created_at,c.updated_at,
               coalesce(c.active_until>now(),false) is_generating,
               (select count(*) from chat_messages m where m.conversation_id=c.id) message_count
        from chat_conversations c
        """;

    public ChatConversationService(Db db, PlatformTransactionManager manager, @Value("${AI_TIMEOUT_SECONDS:60}") int timeout) {
        this.db=db;
        this.transaction=new TransactionTemplate(manager);
        this.leaseSeconds=Math.max(240,timeout+60);
    }
    public List<Map<String,Object>> list() {
        return db.rows(SUMMARY+" order by c.updated_at desc,c.id desc");
    }
    public Map<String,Object> get(UUID id) { return db.one(SUMMARY+" where c.id=?",id); }
    public Map<String,Object> create(UUID questionId) {
        if(questionId!=null) requireQuestion(questionId);
        UUID id=UUID.randomUUID();
        db.update("insert into chat_conversations(id,question_id) values (?,?)",id,questionId);
        return get(id);
    }
    public Map<String,Object> rename(UUID id,String title) {
        String cleaned=title.strip();
        if(cleaned.isEmpty() || cleaned.codePointCount(0,cleaned.length())>80)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"聊天名称需为 1 至 80 个字符");
        if(db.update("update chat_conversations set title=?,title_manual=true where id=?",cleaned,id)==0) missing();
        return get(id);
    }
    public void delete(UUID id) {
        if(db.update("delete from chat_conversations where id=?",id)==0) missing();
    }
    public UUID latest() {
        var rows=db.rows("select id from chat_conversations order by updated_at desc,id desc limit 1");
        return rows.isEmpty()?null:Db.uuid(rows.getFirst().get("id"));
    }
    public List<Map<String,Object>> history(UUID id) {
        UUID target=id==null?latest():id;
        if(target==null) return List.of();
        get(target);
        return db.rows("select * from (select * from chat_messages where conversation_id=? order by created_at desc,id desc limit 100) recent order by created_at,id",target);
    }
    public Map<String,Object> messages(UUID id,UUID before,int pageSize) {
        get(id);
        if(pageSize<1 || pageSize>100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"每页消息数量需为 1 至 100");
        if(before!=null) db.one("select id from chat_messages where id=? and conversation_id=?",before,id);
        var rows=before==null
            ?db.rows("select * from chat_messages where conversation_id=? order by created_at desc,id desc limit ?",id,pageSize+1)
            :db.rows("select * from chat_messages where conversation_id=? and (created_at,id)<(select created_at,id from chat_messages where id=? and conversation_id=?) order by created_at desc,id desc limit ?",id,before,id,pageSize+1);
        boolean more=rows.size()>pageSize;
        var page=new ArrayList<>(rows.subList(0,Math.min(rows.size(),pageSize)));
        Collections.reverse(page);
        return Map.of("messages",page,"hasMore",more);
    }

    public Started begin(UUID requested,UUID turn,String query,UUID question) {
        return Objects.requireNonNull(transaction.execute(status->{
            var previous=db.rows("select conversation_id,content from chat_messages where turn_id=? and role='user'",turn);
            UUID id=requested;
            if(id==null) id=previous.isEmpty()?latest():Db.uuid(previous.getFirst().get("conversationId"));
            if(id==null) id=Db.uuid(create(question).get("id"));
            var conversation=db.one("select * from chat_conversations where id=? for update",id);
            if(!previous.isEmpty() && (!previous.getFirst().get("conversationId").equals(id.toString()) || !previous.getFirst().get("content").equals(query)))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"消息编号已经用于其他聊天或问题");
            var completed=db.rows("select * from chat_messages where conversation_id=? and turn_id=? and role='assistant'",id,turn);
            if(!completed.isEmpty()) return new Started(id,null,null,completed.getFirst());
            if(db.count("select count(*) from chat_conversations where id=? and active_until>now()",id)>0)
                throw new ResponseStatusException(HttpStatus.CONFLICT,"这个聊天正在整理上一条回答，请稍后重试");
            UUID attached=conversation.get("questionId")==null?null:Db.uuid(conversation.get("questionId"));
            if(question!=null) {
                requireQuestion(question);
                if(attached!=null && !attached.equals(question))
                    throw new ResponseStatusException(HttpStatus.CONFLICT,"请为另一道错题新建聊天");
                attached=question;
            }
            if(attached!=null && db.count("select count(*) from questions where id=? and deleted_at is null",attached)==0) attached=null;
            boolean first=db.count("select count(*) from chat_messages where conversation_id=?",id)==0;
            UUID lease=UUID.randomUUID();
            db.update("update chat_conversations set active_turn_id=?,active_request_id=?,active_until=now()+make_interval(secs=>?),question_id=?,updated_at=now() where id=?",turn,lease,leaseSeconds,attached,id);
            if(first && Boolean.FALSE.equals(conversation.get("titleManual"))) {
                String title=query.replaceAll("\\s+"," ").strip();
                int end=title.offsetByCodePoints(0,Math.min(60,title.codePointCount(0,title.length())));
                db.update("update chat_conversations set title=? where id=?",title.substring(0,end),id);
            }
            db.update("insert into chat_messages(conversation_id,turn_id,role,content) values (?,?,'user',?) on conflict(turn_id,role) do nothing",id,turn,query);
            var stored=db.one("select conversation_id,content from chat_messages where turn_id=? and role='user'",turn);
            if(!stored.get("conversationId").equals(id.toString()) || !stored.get("content").equals(query))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"消息编号已经用于其他聊天或问题");
            return new Started(id,lease,attached,null);
        }));
    }
    public List<Map<String,Object>> context(UUID id,UUID currentTurn) {
        var rows=db.rows("select role,content from (select role,content,created_at,id from chat_messages where conversation_id=? and turn_id<>? order by created_at desc,id desc limit 20) recent order by created_at,id",id,currentTurn);
        int budget=48000;
        var history=new LinkedList<Map<String,Object>>();
        for(int index=rows.size()-1;index>=0 && budget>0;index--) {
            var row=rows.get(index);
            String text=row.get("content").toString();
            int length=Math.min(text.length(),Math.min(12000,budget));
            if(length>0 && length<text.length() && Character.isHighSurrogate(text.charAt(length-1))) length--;
            history.addFirst(Map.of("role",row.get("role"),"content",text.substring(0,length)));
            budget-=length;
        }
        return history;
    }
    public Map<String,Object> save(Started started,UUID turn,String answer,List<Map<String,Object>> citations,String retrievalStatus) {
        return Objects.requireNonNull(transaction.execute(status->{
            var conversation=db.one("select active_request_id from chat_conversations where id=? for update",started.conversationId());
            if(!started.requestId().toString().equals(conversation.get("activeRequestId")))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"这条回复已失效，请重新打开聊天");
            db.update("insert into chat_messages(conversation_id,turn_id,role,content,citations_json,retrieval_status) values (?,?,'assistant',?,?::jsonb,?) on conflict(turn_id,role) do nothing",started.conversationId(),turn,answer,db.json(citations),retrievalStatus);
            db.update("update chat_conversations set updated_at=now(),active_turn_id=null,active_request_id=null,active_until=null where id=?",started.conversationId());
            return db.one("select * from chat_messages where conversation_id=? and turn_id=? and role='assistant'",started.conversationId(),turn);
        }));
    }
    public void finish(Started started) {
        db.update("update chat_conversations set active_turn_id=null,active_request_id=null,active_until=null where id=? and active_request_id=?",started.conversationId(),started.requestId());
    }
    private void requireQuestion(UUID id) { db.one("select id from questions where id=? and deleted_at is null",id); }
    private static void missing() { throw new ResponseStatusException(HttpStatus.NOT_FOUND,"聊天不存在或已删除"); }
}
