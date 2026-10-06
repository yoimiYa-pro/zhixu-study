package cn.study.service;

import cn.study.repository.*;
import java.util.*;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class KnowledgeService {
    public record Input(@NotBlank @Size(max=200) String name,UUID parentId,@Size(max=20000) String description) {}
    private final Db db;private final TaskRepository tasks;
    public KnowledgeService(Db db,TaskRepository tasks) { this.db=db;this.tasks=tasks; }
    public List<Map<String,Object>> list() {
        return db.rows("""
            with recursive descendants(root,id) as (
              select id,id from knowledge_points
              union all select d.root,k.id from descendants d join knowledge_points k on k.parent_id=d.id
            ), linked as (select distinct d.root,x.question_id from descendants d join question_knowledge_points x on x.knowledge_point_id=d.id),
            records as (select distinct d.root,s.id,s.quantity,s.wrong_count from study_records s
              join descendants d on s.knowledge_point_id=d.id
              union select distinct l.root,s.id,s.quantity,s.wrong_count from linked l join study_records s on s.question_id=l.question_id)
            select k.*,(select count(*) from linked l join questions q on q.id=l.question_id where l.root=k.id and q.deleted_at is null) as question_count,
              coalesce((select round(100.0*sum(r.quantity-r.wrong_count)/nullif(sum(r.quantity),0),1) from records r where r.root=k.id),0) as mastery,
              coalesce((select sum(r.quantity) from records r where r.root=k.id),0) as practice_count
            from knowledge_points k order by k.name
            """);
    }
    @Transactional public Map<String,Object> create(Input input) {
        if(input.parentId()!=null) db.one("select id from knowledge_points where id=?",input.parentId());
        var row=db.one("insert into knowledge_points(name,parent_id,description) values (?,?,?) returning *",input.name().strip(),input.parentId(),Objects.requireNonNullElse(input.description(),""));
        UUID id=Db.uuid(row.get("id"));tasks.enqueue("INDEX_DOCUMENT",id,Map.of("entityType","knowledge"),"index:knowledge:"+id+":"+UUID.randomUUID());return row;
    }
    @Transactional public Map<String,Object> update(UUID id,Input input) {
        db.one("select id from knowledge_points where id=?",id);
        if(input.parentId()!=null) {
            db.one("select id from knowledge_points where id=?",input.parentId());
            if(db.count("with recursive ancestors as (select id,parent_id from knowledge_points where id=? union all select k.id,k.parent_id from knowledge_points k join ancestors a on k.id=a.parent_id) select count(*) from ancestors where id=?",input.parentId(),id)>0)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"父子关系不能形成循环");
        }
        var row=db.one("update knowledge_points set name=?,parent_id=?,description=? where id=? returning *",input.name().strip(),input.parentId(),Objects.requireNonNullElse(input.description(),""),id);
        tasks.enqueue("INDEX_DOCUMENT",id,Map.of("entityType","knowledge"),"index:knowledge:"+id+":"+UUID.randomUUID());return row;
    }
    @Transactional public void delete(UUID id) {
        db.one("select id from knowledge_points where id=?",id);
        db.update("delete from knowledge_points where id=?",id);
        tasks.enqueue("DELETE_DOCUMENT",id,Map.of("entityType","knowledge"),"delete:knowledge:"+id);
    }
    public void attach(UUID question,List<String> names,String rootName) {
        db.update("delete from question_knowledge_points where question_id=?",question);
        UUID root=null;
        if(!names.isEmpty()) root=ensure(rootName,null);
        for(String name:new LinkedHashSet<>(names)) {
            UUID id=ensure(name.strip(),name.strip().equals(rootName)?null:root);
            db.update("insert into question_knowledge_points(question_id,knowledge_point_id) values (?,?) on conflict do nothing",question,id);
        }
    }
    private UUID ensure(String name,UUID parent) {
        var inserted=db.rows("insert into knowledge_points(name,parent_id) values (?,?) on conflict(name) do nothing returning id",name,parent);
        UUID id=inserted.isEmpty()?Db.uuid(db.one("select id from knowledge_points where name=?",name).get("id")):Db.uuid(inserted.getFirst().get("id"));
        if(!inserted.isEmpty()) tasks.enqueue("INDEX_DOCUMENT",id,Map.of("entityType","knowledge"),"index:knowledge:"+id);
        return id;
    }
}
