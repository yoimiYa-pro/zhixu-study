package cn.study.service;
import cn.study.dto.NewsModels.*;
import cn.study.repository.*;
import java.net.URI;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NewsService {
    private final Db db;private final TaskRepository tasks;private final DashboardService dashboard;private final Clock clock;
    public NewsService(Db db,TaskRepository tasks,DashboardService dashboard,Clock clock) { this.db=db;this.tasks=tasks;this.dashboard=dashboard;this.clock=clock; }
    public List<Map<String,Object>> list(LocalDate date) { return list(date,null,null,null); }
    public List<Map<String,Object>> list(LocalDate date,String tag,String source,String query) {
        var sql=new StringBuilder("select * from current_affairs where fetch_time>=? and fetch_time<?");
        var args=new ArrayList<Object>(List.of(dashboard.start(date),dashboard.start(date.plusDays(1))));
        if(tag!=null && !tag.isBlank()) {
            if(tag.equals("待分类")) sql.append(" and tags_json='[]'::jsonb");
            else { if(!NewsTags.ALL.contains(tag)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"时政标签无效");sql.append(" and jsonb_exists(tags_json,?)");args.add(tag); }
        }
        if(source!=null && !source.isBlank()) { sql.append(" and source=?");args.add(source); }
        if(query!=null && !query.isBlank()) { sql.append(" and position(lower(?) in lower(title || ' ' || content || ' ' || source))>0");args.add(query.strip()); }
        sql.append(" order by publish_time desc nulls last,fetch_time desc,id desc limit 200");
        return db.rows(sql.toString(),args.toArray());
    }
    public Map<String,Object> overview(LocalDate date) {
        var start=dashboard.start(date);var end=dashboard.start(date.plusDays(1));
        var result=db.one("select count(*) as total,count(*) filter(where analysis_json is not null) as analyzed,count(*) filter(where source_url is not null) as originals,count(*) filter(where not source_unverified) as verified from current_affairs where fetch_time>=? and fetch_time<?",start,end);
        result.put("tags",db.rows("select tag as name,count(*) as value from current_affairs cross join lateral jsonb_array_elements_text(case when tags_json='[]'::jsonb then '[\"待分类\"]'::jsonb else tags_json end) as labels(tag) where fetch_time>=? and fetch_time<? group by tag order by count(*) desc,tag",start,end));
        result.put("sources",db.rows("select source as name,count(*) as value from current_affairs where fetch_time>=? and fetch_time<? group by source order by count(*) desc,source",start,end));
        result.put("date",date.toString());result.put("availableTags",NewsTags.ALL);
        return result;
    }
    public Map<String,Object> tags(UUID id,List<String> tags) {
        return db.one("update current_affairs set tags_json=?::jsonb,tags_manual=true where id=? returning *",db.json(tags.stream().distinct().toList()),id);
    }
    public Map<String,Object> get(UUID id) { return db.one("select * from current_affairs where id=?",id); }
    public UUID fetch() { return tasks.enqueue("NEWS_FETCH",null,Map.of("analyze",true),"news-fetch:"+LocalDate.now(clock)+":"+UUID.randomUUID()); }
    public UUID analyze(UUID id) { var row=get(id);return tasks.enqueue("ANALYZE_NEWS",id,Map.of("revision",row.get("revision")),"analyze:news:"+id+":"+row.get("revision")); }
    public void analyzeToday() { for(var row:list(dashboard.today())) if(row.get("analysis")==null) analyze(Db.uuid(row.get("id"))); }
    public static void validUrl(String value) {
        if(value==null || value.isBlank()) return;
        try { var url=URI.create(value);if(!"https".equalsIgnoreCase(url.getScheme()) || url.getHost()==null || url.getUserInfo()!=null) throw new IllegalArgumentException(); }
        catch(IllegalArgumentException error) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"来源链接必须是完整 HTTPS 地址"); }
    }
    @Transactional public Map<String,Object> store(Article article,boolean queueAnalysis) {
        validUrl(article.sourceUrl());
        if(article.publishTime()!=null && article.publishTime().toInstant().isAfter(clock.instant())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"发布时间不能晚于现在");
        boolean unverified=article.sourceUnverified() || article.sourceUrl()==null || article.sourceUrl().isBlank() || article.publishTime()==null;
        var inserted=db.rows("""
            insert into current_affairs(title,content,source,source_url,publish_time,fetch_time,source_unverified,tags_json)
            values (?,?,?,?,?,?,?,?::jsonb) on conflict(source_url) do nothing returning *
            """,article.title(),article.content(),article.source(),article.sourceUrl()==null || article.sourceUrl().isBlank()?null:article.sourceUrl(),
            article.publishTime()==null?null:Timestamp.from(article.publishTime().toInstant()),Timestamp.from(article.fetchTime().toInstant()),unverified,
            db.json(NewsTags.infer(article.title()+" "+article.content().substring(0,Math.min(8000,article.content().length())))));
        var row=inserted.isEmpty()?db.one("select * from current_affairs where source_url=?",article.sourceUrl()):inserted.getFirst();
        UUID id=Db.uuid(row.get("id"));
        if(!inserted.isEmpty()) tasks.enqueue("INDEX_DOCUMENT",id,Map.of("entityType","current_affair","revision",row.get("revision")),"index:news:"+id+":"+row.get("revision"));
        if(queueAnalysis && row.get("analysis")==null) analyze(id);
        row.put("newlyStored",!inserted.isEmpty());
        return row;
    }
    @Transactional public boolean apply(UUID id,int revision,Analysis analysis) {
        var current=get(id);
        var tags=NewsTags.analysis(analysis,(List<?>)current.get("tags"));
        if(db.update("update current_affairs set analysis_json=?::jsonb,tags_json=case when tags_manual then tags_json else ?::jsonb end where id=? and revision=?",db.json(analysis),db.json(tags),id,revision)==0) return false;
        var source=get(id);
        for(Material material:analysis.materials()) {
            var rows=db.rows("""
                insert into essay_materials(title,content,category,kind,tags_json,source,source_url,publish_time,fetch_time,source_unverified,current_affair_id)
                values (?,?,?,?,?::jsonb,?,?,?,?,?,?) on conflict(current_affair_id,title) do nothing returning id
                """,material.title(),material.content(),material.category(),material.kind(),db.json(material.tags()),source.get("source"),source.get("sourceUrl"),
                source.get("publishTime")==null?null:Timestamp.from(Instant.parse(source.get("publishTime").toString())),
                Timestamp.from(Instant.parse(source.get("fetchTime").toString())),source.get("sourceUnverified"),id);
            if(!rows.isEmpty()) { UUID materialId=Db.uuid(rows.getFirst().get("id"));tasks.enqueue("INDEX_DOCUMENT",materialId,Map.of("entityType","essay","revision",1),"index:essay:"+materialId+":1"); }
        }
        return true;
    }
}
