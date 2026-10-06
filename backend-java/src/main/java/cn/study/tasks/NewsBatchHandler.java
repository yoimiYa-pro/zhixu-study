package cn.study.tasks;
import cn.study.repository.Db;
import cn.study.service.NewsService;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Component;
@Component
public class NewsBatchHandler implements TaskHandler {
    private final NewsService news;
    public NewsBatchHandler(NewsService news) { this.news=news; }
    public String kind() { return "ANALYZE_NEWS_BATCH"; }
    public Object handle(Map<String,Object> task) {
        LocalDate date=LocalDate.parse(((Map<?,?>)task.get("payload")).get("date").toString());int queued=0;
        for(var row:news.list(date)) if(row.get("analysis")==null) { news.analyze(Db.uuid(row.get("id")));queued++; }
        return Map.of("queued",queued);
    }
}
