package cn.study.service;

import cn.study.repository.Db;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SearchService {
    private final AiClient ai;private final QuestionService questions;
    public SearchService(AiClient ai,QuestionService questions) { this.ai=ai;this.questions=questions; }
    public List<Map<String,Object>> similar(UUID id) {
        var question=questions.get(id);
        List hits=ai.post("/ai/search/similar",Map.of("query",question.get("content"),"entityType","question","excludeIds",List.of(id),"limit",8),List.class);
        var results=new ArrayList<Map<String,Object>>();
        for(Object object:hits) {
            var hit=(Map<?,?>)object;
            try {
                var item=questions.get(Db.uuid(hit.get("entityId")));item.put("score",hit.get("score"));results.add(item);
            } catch (ResponseStatusException e) { if(e.getStatusCode().value()!=404) throw e; }
        }
        return results;
    }
}
