package cn.study.service;
import cn.study.dto.LibraryInput.*;
import cn.study.repository.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
@Service
public class LibraryService {
    private final Db db;private final TaskRepository tasks;private final Clock clock;
    public LibraryService(Db db,TaskRepository tasks,Clock clock) { this.db=db;this.tasks=tasks;this.clock=clock; }
    static String text(String value) { return Objects.requireNonNullElse(value,""); }
    static <T> List<T> list(List<T> value) { return value==null?List.of():value; }
    public List<Map<String,Object>> today() { return db.rows("select * from idioms where mastered=false order by md5(id::text || ?) limit 1",LocalDate.now(clock).toString()); }
    public Map<String,Object> idioms(String q,Boolean favorite,Boolean mastered,int page) {
        if(page<1 || page>10000 || q.length()>200) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"查询范围无效");
        String where=" where (word ilike ? or definition ilike ?) and (?::boolean is null or favorite=?::boolean) and (?::boolean is null or mastered=?::boolean)";
        var args=new Object[]{"%"+q+"%","%"+q+"%",favorite,favorite,mastered,mastered};
        var pageArgs=new ArrayList<Object>(Arrays.asList(args));pageArgs.add((page-1)*20);
        return Map.of("total",db.count("select count(*) from idioms"+where,args),"items",db.rows("select * from idioms"+where+" order by favorite desc,created_at desc limit 20 offset ?",pageArgs.toArray()));
    }
    public Map<String,Object> idiom(UUID id) { return db.one("select * from idioms where id=?",id); }
    public Map<String,Object> createIdiom(Idiom input) {
        return db.one("insert into idioms(word,kind,definition,synonyms_json,antonyms_json,scenario,pitfalls,example) values (?,?,?,?::jsonb,?::jsonb,?,?,?) returning *",input.word().strip(),input.kind(),input.definition(),db.json(list(input.synonyms())),db.json(list(input.antonyms())),text(input.scenario()),text(input.pitfalls()),text(input.example()));
    }
    public Map<String,Object> updateIdiom(UUID id,Idiom input) { return db.one("update idioms set word=?,kind=?,definition=?,synonyms_json=?::jsonb,antonyms_json=?::jsonb,scenario=?,pitfalls=?,example=? where id=? returning *",input.word().strip(),input.kind(),input.definition(),db.json(list(input.synonyms())),db.json(list(input.antonyms())),text(input.scenario()),text(input.pitfalls()),text(input.example()),id); }
    public Map<String,Object> state(UUID id,State input) { return db.one("update idioms set favorite=coalesce(?::boolean,favorite),mastered=coalesce(?::boolean,mastered) where id=? returning *",input.favorite(),input.mastered(),id); }
    public void deleteIdiom(UUID id) { idiom(id);db.update("delete from idioms where id=?",id); }
    public Map<String,Object> materials(String q,String category,String kind,int page) {
        if(page<1 || page>10000 || q.length()>200 || category.length()>30 || kind.length()>30) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"查询范围无效");
        String where=" where (title ilike ? or content ilike ? or tags_json::text ilike ?) and (?='' or category=?) and (?='' or kind=?)";
        var args=new Object[]{"%"+q+"%","%"+q+"%","%"+q+"%",category,category,kind,kind};
        var pageArgs=new ArrayList<Object>(Arrays.asList(args));pageArgs.add((page-1)*20);
        return Map.of("total",db.count("select count(*) from essay_materials"+where,args),"items",db.rows("select * from essay_materials"+where+" order by created_at desc limit 20 offset ?",pageArgs.toArray()));
    }
    public Map<String,Object> material(UUID id) { return db.one("select * from essay_materials where id=?",id); }
    @Transactional public Map<String,Object> saveMaterial(UUID id,Material input) {
        NewsService.validUrl(input.sourceUrl());
        if(input.publishTime()!=null && input.publishTime().toInstant().isAfter(clock.instant())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"发布时间不能晚于现在");
        Object publish=input.publishTime()==null?null:Timestamp.from(input.publishTime().toInstant());
        String sourceUrl=input.sourceUrl()==null || input.sourceUrl().isBlank()?null:input.sourceUrl();
        boolean unverified=!Boolean.FALSE.equals(input.sourceUnverified()) || sourceUrl==null || publish==null || text(input.source()).isBlank();
        Map<String,Object> row;
        if(id==null) row=db.one("insert into essay_materials(title,content,category,kind,tags_json,source,source_url,publish_time,source_unverified) values (?,?,?,?,?::jsonb,?,?,?,?) returning *",input.title(),input.content(),input.category(),input.kind(),db.json(list(input.tags())),text(input.source()),sourceUrl,publish,unverified);
        else row=db.one("update essay_materials set title=?,content=?,category=?,kind=?,tags_json=?::jsonb,source=?,source_url=?,publish_time=?,source_unverified=?,revision=revision+1 where id=? returning *",input.title(),input.content(),input.category(),input.kind(),db.json(list(input.tags())),text(input.source()),sourceUrl,publish,unverified,id);
        UUID ref=Db.uuid(row.get("id"));tasks.enqueue("INDEX_DOCUMENT",ref,Map.of("entityType","essay","revision",row.get("revision")),"index:essay:"+ref+":"+row.get("revision"));return row;
    }
    @Transactional public void deleteMaterial(UUID id) { material(id);db.update("delete from essay_materials where id=?",id);tasks.enqueue("DELETE_DOCUMENT",id,Map.of("entityType","essay"),"delete:essay:"+id); }
}
