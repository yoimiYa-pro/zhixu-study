package cn.study.service;

import cn.study.dto.QuestionInput;
import cn.study.repository.*;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class QuestionService {
    private final Db db;private final TaskRepository tasks;private final KnowledgeService knowledge;private final Clock clock;
    private static final String DETAIL="""
        select q.*,m.reason as mistake_reason,m.confirmed as mistake_confirmed,m.last_review_at,p.next_review_at,
        (m.id is not null) as mistake,
        (select status from ai_tasks t where t.reference_id=q.id and t.kind='ANALYZE_QUESTION' order by t.created_at desc limit 1) as ai_status
        from questions q left join mistakes m on m.question_id=q.id left join review_plans p on p.question_id=q.id
        """;
    public QuestionService(Db db,TaskRepository tasks,KnowledgeService knowledge,Clock clock) { this.db=db;this.tasks=tasks;this.knowledge=knowledge;this.clock=clock; }
    public Map<String,Object> list(String query,String type,boolean mistakesOnly,int page,int size) {
        String search="%"+Objects.requireNonNullElse(query,"")+"%";
        String filter=" where q.deleted_at is null and q.content ilike ? and (?='' or q.question_type=?) and (?=false or m.id is not null)";
        var items=db.rows(DETAIL+filter+" order by q.created_at desc limit ? offset ?",search,Objects.requireNonNullElse(type,""),Objects.requireNonNullElse(type,""),mistakesOnly,size,(page-1)*size);
        for(var item:items) enrich(item);
        long total=db.count("select count(*) from questions q left join mistakes m on m.question_id=q.id"+filter,search,Objects.requireNonNullElse(type,""),Objects.requireNonNullElse(type,""),mistakesOnly);
        return Map.of("items",items,"total",total,"page",page,"pageSize",size);
    }
    public Map<String,Object> get(UUID id) { var row=db.one(DETAIL+" where q.id=? and q.deleted_at is null",id);enrich(row);return row; }
    private void enrich(Map<String,Object> row) {
        UUID id=Db.uuid(row.get("id"));
        var options=new LinkedHashMap<String,String>();for(var option:db.rows("select option_key,content from question_options where question_id=? order by option_key",id)) options.put(option.get("optionKey").toString(),option.get("content").toString());
        row.put("options",options);row.put("knowledgePoints",db.rows("select k.name from knowledge_points k join question_knowledge_points x on x.knowledge_point_id=k.id where x.question_id=? order by k.name",id).stream().map(k->k.get("name")).toList());
    }
    private void validateOptions(QuestionInput input) {
        if(!input.options().keySet().stream().allMatch(key->key.matches("[A-H]"))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"选项编号必须为 A 到 H");
        if(!input.questionType().equals("申论") && (input.options().size()<2 || !input.options().containsKey(input.correctAnswer()) || (!input.userAnswer().isBlank() && !input.options().containsKey(input.userAnswer()))))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"选择题至少有两个选项，答案必须是有效选项编号");
    }
    @Transactional public Map<String,Object> create(QuestionInput input) {
        validateOptions(input);UUID id=UUID.randomUUID();
        var inserted=db.rows("insert into questions(id,content,correct_answer,user_answer,explanation,question_type,difficulty,source,year,region,creation_request) values (?,?,?,?,?,?,?,?,?,?,?) on conflict(creation_request) do nothing returning id",id,input.content(),input.correctAnswer(),input.userAnswer(),input.explanation(),input.questionType(),input.difficulty(),input.source(),input.year(),input.region(),input.clientRequestId());
        if(inserted.isEmpty()) return get(Db.uuid(db.one("select id from questions where creation_request=?",input.clientRequestId()).get("id")));
        saveRelated(id,input);
        db.update("insert into study_records(question_id,kind,quantity,wrong_count,time_spent) values (?,'PRACTICE',1,?,?)",id,input.mistake() || !input.correctAnswer().equals(input.userAnswer())?1:0,input.timeSpent());
        queue(id,1,input.mistake());return get(id);
    }
    @Transactional public Map<String,Object> update(UUID id,QuestionInput input) {
        validateOptions(input);get(id);
        var row=db.one("update questions set content=?,correct_answer=?,user_answer=?,explanation=?,question_type=?,difficulty=?,source=?,year=?,region=?,analysis_json=null,revision=revision+1,updated_at=now() where id=? returning revision",input.content(),input.correctAnswer(),input.userAnswer(),input.explanation(),input.questionType(),input.difficulty(),input.source(),input.year(),input.region(),id);
        saveRelated(id,input);queue(id,((Number)row.get("revision")).intValue(),input.mistake());return get(id);
    }
    private void saveRelated(UUID id,QuestionInput input) {
        db.update("delete from question_options where question_id=?",id);
        for(var option:input.options().entrySet()) db.update("insert into question_options(question_id,option_key,content) values (?,?,?)",id,option.getKey(),option.getValue());
        knowledge.attach(id,input.knowledgePoints(),input.questionType());
        if(input.mistake()) {
            db.update("insert into mistakes(id,question_id,reason,confirmed) values (?,?,?,?) on conflict(question_id) do update set reason=excluded.reason,confirmed=excluded.confirmed",UUID.randomUUID(),id,input.mistakeReason(),!input.mistakeReason().equals("未确认"));
            db.update("insert into review_plans(question_id,next_review_at) values (?,?) on conflict(question_id) do nothing",id,Timestamp.from(clock.instant().atZone(clock.getZone()).plusDays(1).toInstant()));
        } else {
            db.update("delete from review_plans where question_id=?",id);db.update("delete from mistakes where question_id=?",id);
        }
    }
    private void queue(UUID id,int revision,boolean mistake) {
        var payload=Map.<String,Object>of("revision",revision,"entityType","question");
        if(mistake) tasks.enqueue("ANALYZE_QUESTION",id,payload,"analysis:question:"+id+":"+revision);
        tasks.enqueue("INDEX_DOCUMENT",id,payload,"index:question:"+id+":"+revision);
    }
    @Transactional public Map<String,Object> analyze(UUID id) {
        var row=db.one("select revision from questions where id=? and deleted_at is null for update",id);
        int revision=((Number)row.get("revision")).intValue();
        // Serialize repeated clicks and keep the previous explanation while the
        // replacement runs. A new revision also rejects older in-flight results.
        if(db.count("select count(*) from ai_tasks where reference_id=? and kind='ANALYZE_QUESTION' and status in ('PENDING','PROCESSING') and payload_json->>'revision'=?",id,Integer.toString(revision))>0) return get(id);
        int nextRevision=revision+1;
        db.update("update questions set revision=?,updated_at=now() where id=?",nextRevision,id);
        tasks.enqueue("ANALYZE_QUESTION",id,Map.of("revision",nextRevision,"entityType","question"),"analysis:question:"+id+":"+nextRevision);
        return get(id);
    }
    @Transactional public void delete(UUID id) {
        db.one("select id from questions where id=?",id);
        db.update("update questions set deleted_at=now(),updated_at=now() where id=? and deleted_at is null",id);
        db.update("delete from review_plans where question_id=?",id);
        tasks.enqueue("DELETE_DOCUMENT",id,Map.of("entityType","question"),"delete:question:"+id);
    }
    @Transactional public Map<String,Object> confirmReason(UUID id,String reason) {
        get(id);
        if(db.update("update mistakes set reason=?,confirmed=true where question_id=?",reason,id)==0) throw new ResponseStatusException(HttpStatus.CONFLICT,"该题没有错题记录");
        return get(id);
    }
}
