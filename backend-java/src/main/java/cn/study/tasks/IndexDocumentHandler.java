package cn.study.tasks;

import cn.study.repository.Db;
import cn.study.service.*;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class IndexDocumentHandler implements TaskHandler {
    private final Db db;private final AiClient ai;private final QuestionService questions;
    public IndexDocumentHandler(Db db,AiClient ai,QuestionService questions) { this.db=db;this.ai=ai;this.questions=questions; }
    public String kind() { return "INDEX_DOCUMENT"; }
    public Object handle(Map<String,Object> task) {
        UUID id=Db.uuid(task.get("referenceId"));var payload=(Map<?,?>)task.get("payload");String type=payload.get("entityType").toString();
        Map<String,Object> row;
        try {
            if(type.equals("question")) row=questions.get(id);
            else row=db.one(switch(type) { case "knowledge"->"select * from knowledge_points where id=?";case "essay"->"select * from essay_materials where id=?";case "current_affair"->"select * from current_affairs where id=?";default->throw new AiClient.Failure("UNSUPPORTED_TASK"); },id);
        } catch (ResponseStatusException e) { if(e.getStatusCode().value()==404) return Map.of("skipped","deleted");throw e; }
        if(payload.get("revision") instanceof Number version && row.get("revision") instanceof Number current && version.intValue()!=current.intValue()) return Map.of("skipped","superseded");
        String title=Objects.toString(row.getOrDefault("title",row.getOrDefault("name",row.getOrDefault("content",""))),"");
        String source=Objects.toString(row.get("source"),"");
        String text=type.equals("question")?row.get("content")+"\n"+db.json(row.get("options"))+"\n"+row.get("explanation"):type.equals("knowledge")?title+"\n"+row.get("description"):title+"\n"+row.get("content");
        var doc=new LinkedHashMap<String,Object>();doc.put("entityId",id);doc.put("entityType",type);doc.put("title",title.substring(0,Math.min(500,title.length())));doc.put("source",source);doc.put("text",text);doc.put("knowledgePoints",row.getOrDefault("knowledgePoints",List.of()));
        var result=ai.post("/ai/question/embed",doc,Map.class);
        if(type.equals("question") && row.get("analysis")!=null) { doc.put("entityType","question_analysis");doc.put("text",db.json(row.get("analysis")));ai.post("/ai/question/embed",doc,Map.class); }
        return result;
    }
}
