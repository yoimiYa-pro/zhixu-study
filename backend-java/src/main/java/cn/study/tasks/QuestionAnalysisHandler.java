package cn.study.tasks;

import cn.study.dto.QuestionAnalysis;
import cn.study.repository.Db;
import cn.study.service.*;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class QuestionAnalysisHandler implements TaskHandler {
    private final QuestionService questions;private final AiClient ai;private final AnalysisResultService results;
    public QuestionAnalysisHandler(QuestionService questions,AiClient ai,AnalysisResultService results) { this.questions=questions;this.ai=ai;this.results=results; }
    public String kind() { return "ANALYZE_QUESTION"; }
    public Object handle(Map<String,Object> task) {
        UUID id=Db.uuid(task.get("referenceId"));var payload=(Map<?,?>)task.get("payload");int revision=((Number)payload.get("revision")).intValue();
        Map<String,Object> question;
        try { question=questions.get(id); }
        catch (ResponseStatusException e) { if(e.getStatusCode().value()==404) return Map.of("skipped","deleted");throw e; }
        if(((Number)question.get("revision")).intValue()!=revision) return Map.of("skipped","superseded");
        var analysis=ai.post("/ai/question/analyze",Map.of("question",question),QuestionAnalysis.class);
        return Map.of("applied",results.apply(id,revision,analysis));
    }
}
