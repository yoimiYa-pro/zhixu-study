package cn.study.tasks;
import cn.study.dto.NewsModels.Analysis;
import cn.study.repository.Db;
import cn.study.service.*;
import java.util.*;
import org.springframework.stereotype.Component;
@Component
public class NewsAnalysisHandler implements TaskHandler {
    private final AiClient ai;private final NewsService news;
    public NewsAnalysisHandler(AiClient ai,NewsService news) { this.ai=ai;this.news=news; }
    public String kind() { return "ANALYZE_NEWS"; }
    public Object handle(Map<String,Object> task) {
        UUID id=Db.uuid(task.get("referenceId"));var row=news.get(id);int version=((Number)((Map<?,?>)task.get("payload")).get("revision")).intValue();
        if(((Number)row.get("revision")).intValue()!=version) return Map.of("skipped","superseded");
        var article=new LinkedHashMap<String,Object>();for(String field:List.of("title","content","source","sourceUrl","publishTime","fetchTime","sourceUnverified")) article.put(field,row.get(field));
        var analysis=ai.post("/ai/current-affairs/analyze",Map.of("article",article),Analysis.class);
        return Map.of("applied",news.apply(id,version,analysis));
    }
}
