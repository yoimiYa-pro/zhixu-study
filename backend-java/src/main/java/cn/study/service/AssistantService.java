package cn.study.service;
import cn.study.dto.AssistantModels.*;
import cn.study.repository.Db;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
@Service
public class AssistantService {
    private final Db db;private final RagService rag;private final AiClient ai;private final DashboardService dashboard;private final ChatConversationService conversations;
    public AssistantService(Db db,RagService rag,AiClient ai,DashboardService dashboard,ChatConversationService conversations) { this.db=db;this.rag=rag;this.ai=ai;this.dashboard=dashboard;this.conversations=conversations; }
    public List<Map<String,Object>> history() { return conversations.history(null); }
    public Map<String,Object> chat(UUID turn,String query,UUID question) {
        return chat(null,turn,query,question);
    }
    public Map<String,Object> chat(UUID conversation,UUID turn,String query,UUID question) {
        var started=conversations.begin(conversation,turn,query,question);
        if(started.completed()!=null) return started.completed();
        try {
            var retrieval=rag.retrieve(query,started.questionId(),false);
            var history=conversations.context(started.conversationId(),turn);
            var dash=dashboard.get();var learningStatus=Map.of("date",dash.get("date"),"stats",dash.get("stats"),"streak",dash.get("streak"),"weakPoints",dash.get("weakPoints"),"tasks",dash.get("tasks"));
            var payload=Map.of("query",query,"contexts",retrieval.contexts(),"history",history,"learningStatus",learningStatus,"retrievalStatus",retrieval.status());
            var answer=ai.post("/ai/chat",payload,ChatAnswer.class);
            return conversations.save(started,turn,answer.answer(),rag.citations(answer.citations(),retrieval),retrieval.status());
        } finally { conversations.finish(started); }
    }
    public Map<String,Object> outline(String topic) {
        var retrieval=rag.retrieve(topic,null,true);
        var result=ai.post("/ai/essay-assistant",Map.of("topic",topic,"contexts",retrieval.contexts(),"retrievalStatus",retrieval.status()),Outline.class);
        if(result.quotes().stream().anyMatch(quote->quote.isBlank() || retrieval.contexts().stream().noneMatch(context->context.content().contains(quote)))) throw new AiClient.Failure("AI_INVALID_OUTPUT");
        var response=new LinkedHashMap<String,Object>();response.put("topic",topic);response.put("outline",result);response.put("citations",rag.citations(result.citations(),retrieval));response.put("retrievalStatus",retrieval.status());return response;
    }
}
