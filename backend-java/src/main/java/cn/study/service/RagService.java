package cn.study.service;
import cn.study.dto.AssistantModels.*;
import cn.study.repository.Db;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RagService {
    public record Context(String entityType,UUID entityId,String title,String content) {}
    public record Retrieval(List<Context> contexts,String status) {}
    private final Db db;private final AiClient ai;private final QuestionService questions;private final KnowledgeService knowledge;
    public RagService(Db db,AiClient ai,QuestionService questions,KnowledgeService knowledge) { this.db=db;this.ai=ai;this.questions=questions;this.knowledge=knowledge; }
    private Context context(String kind,Map<String,Object> row) {
        String title=Objects.toString(row.getOrDefault("title",row.getOrDefault("name",row.getOrDefault("content","学习记录"))));
        String content=switch(kind) {
            case "question"->"题目（节选）："+clip(row.get("content"),3000)+"\n选项："+db.json(row.get("options"))+"\n正确答案："+row.get("correctAnswer")+"\n我的答案："+row.get("userAnswer")+"\n参考解析："+clip(row.get("explanation"),1000)+"\n分析："+clip(db.json(row.get("analysis")),1000)+"\n知识点："+row.get("knowledgePoints");
            case "essay","current_affair"->clip(row.get("content"),4500)+"\n来源："+row.get("source")+"\n来源链接："+row.get("sourceUrl")+"\n发布时间："+row.get("publishTime")+"\n来源待核实："+row.get("sourceUnverified");
            default->db.json(row);
        };
        return new Context(kind,Db.uuid(row.get("id")),title.substring(0,Math.min(title.length(),500)),content.substring(0,Math.min(content.length(),6000)));
    }
    private static String clip(Object value,int limit) { String text=Objects.toString(value,"");return text.substring(0,Math.min(text.length(),limit)); }
    private void add(Map<String,Context> map,String type,Map<String,Object> row) {
        var context=context(type,row);map.putIfAbsent(type+":"+context.entityId(),context);
    }
    public Retrieval retrieve(String query,UUID questionId,boolean essay) {
        var contexts=new LinkedHashMap<String,Context>();String term="%"+query+"%";
        // Database reads always run first; vector availability never controls ownership or record access.
        if(!essay) {
            if(questionId!=null) add(contexts,"question",questions.get(questionId));
            for(var row:db.rows("select id from questions where deleted_at is null order by case when content ilike ? then 0 else 1 end,created_at desc limit 5",term)) add(contexts,"question",questions.get(Db.uuid(row.get("id"))));
            for(var row:db.rows("select * from study_records order by studied_at desc limit 4")) add(contexts,"study",row);
        }
        var points=new ArrayList<>(knowledge.list());points.sort(Comparator.comparingInt(p->p.get("name").toString().contains(query)?0:1));
        for(var row:points.stream().limit(4).toList()) add(contexts,"knowledge",row);
        for(var row:db.rows("select * from essay_materials order by case when title ilike ? or content ilike ? or category=? or tags_json::text ilike ? then 0 else 1 end,created_at desc limit ?",term,term,query,term,essay?5:2)) add(contexts,"essay",row);
        for(var row:db.rows("select * from current_affairs order by case when title ilike ? or content ilike ? then 0 else 1 end,fetch_time desc limit ?",term,term,essay?5:2)) add(contexts,"current_affair",row);
        String status="VECTOR_AND_DATABASE";
        try {
            Hit[] hits=ai.post("/ai/search/similar",Map.of("query",query,"limit",10),Hit[].class);
            for(var hit:hits) {
                String type=hit.entityType().equals("question_analysis")?"question":hit.entityType();
                if(essay && (type.equals("question") || type.equals("study"))) continue;
                try { add(contexts,type,hydrate(type,hit.entityId())); }
                catch(ResponseStatusException missing) { if(missing.getStatusCode().value()!=404) throw missing; }
            }
        } catch(AiClient.Failure failure) { status=failure.code.equals("EMBEDDING_NOT_CONFIGURED")?"DATABASE_ONLY":"VECTOR_UNAVAILABLE"; }
        return new Retrieval(contexts.values().stream().limit(25).toList(),status);
    }
    public Map<String,Object> hydrate(String type,UUID id) {
        return switch(type) {
            case "question"->questions.get(id);
            case "knowledge"->db.one("select * from knowledge_points where id=?",id);
            case "essay"->db.one("select * from essay_materials where id=?",id);
            case "current_affair"->db.one("select * from current_affairs where id=?",id);
            case "study"->db.one("select * from study_records where id=?",id);
            default->throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"资料类型无效");
        };
    }
    public List<Map<String,Object>> citations(List<Citation> refs,Retrieval retrieval) {
        var allowed=new HashSet<String>();for(var item:retrieval.contexts()) allowed.add(item.entityType()+":"+item.entityId());
        var result=new ArrayList<Map<String,Object>>();
        for(var ref:refs) {
            if(!allowed.contains(ref.entityType()+":"+ref.entityId())) throw new AiClient.Failure("AI_INVALID_OUTPUT");
            var row=hydrate(ref.entityType(),ref.entityId());var value=new LinkedHashMap<String,Object>();
            value.put("entityType",ref.entityType());value.put("entityId",ref.entityId());value.put("title",clip(row.getOrDefault("title",row.getOrDefault("name",row.getOrDefault("content","学习记录"))),500));
            value.put("source",row.getOrDefault("source","个人学习记录"));value.put("sourceUrl",row.get("sourceUrl"));value.put("sourceUnverified",row.getOrDefault("sourceUnverified",false));
            result.add(value);
        }
        return result;
    }
}
