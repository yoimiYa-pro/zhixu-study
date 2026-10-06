package cn.study.service;

import cn.study.dto.QuestionAnalysis;
import cn.study.repository.*;
import java.util.*;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalysisResultService {
    private final Db db;private final KnowledgeService knowledge;private final TaskRepository tasks;private final Validator validator;
    public AnalysisResultService(Db db,KnowledgeService knowledge,TaskRepository tasks,Validator validator) { this.db=db;this.knowledge=knowledge;this.tasks=tasks;this.validator=validator; }
    @Transactional public boolean apply(UUID id,int revision,QuestionAnalysis analysis) {
        if(!validator.validate(analysis).isEmpty()) throw new AiClient.Failure("AI_INVALID_OUTPUT");
        if(db.update("update questions set analysis_json=?::jsonb where id=? and revision=? and deleted_at is null",db.json(analysis),id,revision)==0) return false;
        var question=db.one("select question_type from questions where id=?",id);
        var names=new LinkedHashSet<String>();
        for(var row:db.rows("select name from knowledge_points k join question_knowledge_points x on x.knowledge_point_id=k.id where x.question_id=?",id)) names.add(row.get("name").toString());
        names.addAll(analysis.knowledgePoints());knowledge.attach(id,names.stream().limit(20).toList(),question.get("questionType").toString());
        tasks.enqueue("INDEX_DOCUMENT",id,Map.of("entityType","question","revision",revision),"index:question:"+id+":"+revision+":analysis");
        return true;
    }
}
