package cn.study.tasks;
import cn.study.dto.NewsModels.Batch;
import cn.study.service.*;
import cn.study.repository.Db;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;
@Component
public class NewsFetchHandler implements TaskHandler {
    private final AiClient ai;private final NewsService news;private final Db db;private final Clock clock;
    public NewsFetchHandler(AiClient ai,NewsService news,Db db,Clock clock) { this.ai=ai;this.news=news;this.db=db;this.clock=clock; }
    public String kind() { return "NEWS_FETCH"; }
    public Object handle(Map<String,Object> task) {
        UUID id=Db.uuid(task.get("id"));
        db.update("update ai_tasks set progress_label='正在读取新闻来源' where id=?",id);
        var batch=ai.post("/ai/news/fetch",Map.of(),Batch.class);
        int stored=0,processed=0;
        if(!batch.articles().isEmpty()) db.update("update ai_tasks set progress_done=0,progress_total=?,progress_label='正在保存原文' where id=?",batch.articles().size(),id);
        for(var article:batch.articles()) {
            if(Boolean.TRUE.equals(news.store(article,false).get("newlyStored"))) stored++;
            db.update("update ai_tasks set progress_done=? where id=?",++processed,id);
        }
        if(Boolean.TRUE.equals(((Map<?,?>)task.get("payload")).get("analyze")) || db.count("select count(*) from ai_tasks where kind='ANALYZE_NEWS_BATCH' and payload_json->>'date'=? and status='COMPLETED'",LocalDate.now(clock).toString())>0) news.analyzeToday();
        return Map.of("received",batch.articles().size(),"stored",stored,"duplicates",batch.articles().size()-stored,"failedFeeds",batch.failedFeeds(),"sources",batch.sources()==null?List.of():batch.sources());
    }
}
