package cn.study.service;

import cn.study.repository.Db;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReviewService {
    public record Completion(@NotNull UUID requestId,@NotBlank @Pattern(regexp="CORRECT|INCORRECT|SKIP") String result,@Size(max=5000) String answer,@Min(0) @Max(86400) int timeSpent,@Min(1) @Max(5) int confidence) {}
    private final Db db;private final QuestionService questions;private final Clock clock;
    public ReviewService(Db db,QuestionService questions,Clock clock) { this.db=db;this.questions=questions;this.clock=clock; }
    public Timestamp endOfToday() { return Timestamp.from(LocalDate.now(clock).plusDays(1).atStartOfDay(clock.getZone()).toInstant()); }
    public List<Map<String,Object>> today() {
        var result=new ArrayList<Map<String,Object>>();
        for(var row:db.rows("select p.question_id from review_plans p join questions q on q.id=p.question_id where q.deleted_at is null and p.next_review_at<? order by p.next_review_at",endOfToday())) result.add(questions.get(Db.uuid(row.get("questionId"))));
        return result;
    }
    @Transactional public Map<String,Object> complete(UUID id,Completion input) {
        var existing=db.rows("select * from reviews where request_id=?",input.requestId());
        if(!existing.isEmpty()) {
            if(!existing.getFirst().get("questionId").equals(id.toString())) throw new ResponseStatusException(HttpStatus.CONFLICT,"请求编号已用于其他题目");
            return existing.getFirst();
        }
        var plan=db.one("select * from review_plans where question_id=? for update",id);
        existing=db.rows("select * from reviews where request_id=?",input.requestId());
        if(!existing.isEmpty()) return existing.getFirst();
        if(Instant.parse(plan.get("nextReviewAt").toString()).compareTo(endOfToday().toInstant())>=0) throw new ResponseStatusException(HttpStatus.CONFLICT,"该题复习日期已更新，请刷新列表");
        var question=questions.get(id);boolean skip=input.result().equals("SKIP");String answer=Objects.requireNonNullElse(input.answer(),"");
        if(!skip && !question.get("questionType").equals("申论") && !((Map<?,?>)question.get("options")).containsKey(answer)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请提交有效选项或跳过");
        boolean correct=question.get("questionType").equals("申论")?input.result().equals("CORRECT"):question.get("correctAnswer").equals(answer);
        double mastery=((Number)db.one("select coalesce(100.0*sum(quantity-wrong_count)/nullif(sum(quantity),0),0) as value from study_records where question_id=? or question_id in (select distinct x.question_id from question_knowledge_points x join question_knowledge_points mine on mine.knowledge_point_id=x.knowledge_point_id where mine.question_id=?) or knowledge_point_id in (select knowledge_point_id from question_knowledge_points where question_id=?)",id,id,id).get("value")).doubleValue();
        int stage=((Number)plan.get("stage")).intValue(),attempts=((Number)plan.get("attempts")).intValue();
        var schedule=skip?new ReviewRule.Schedule(stage,1):ReviewRule.next(stage,correct,input.confidence(),question.get("difficulty").toString(),mastery,attempts);
        Timestamp next=Timestamp.from(clock.instant().atZone(clock.getZone()).plusDays(schedule.days()).toInstant());
        db.update("update review_plans set stage=?,attempts=attempts+?,correct_count=correct_count+?,next_review_at=? where question_id=?",schedule.stage(),skip?0:1,!skip&&correct?1:0,next,id);
        db.update("update mistakes set last_review_at=? where question_id=?",Timestamp.from(clock.instant()),id);
        db.update("insert into study_records(question_id,kind,quantity,wrong_count,time_spent) values (?,?,?,?,?)",id,skip?"REVIEW_SKIP":"REVIEW",skip?0:1,!skip&&!correct?1:0,input.timeSpent());
        return db.one("insert into reviews(id,request_id,question_id,outcome,answer,time_spent,confidence,next_review_at,due_at) values (?,?,?,?,?,?,?,?,?) returning *",UUID.randomUUID(),input.requestId(),id,skip?"SKIP":correct?"CORRECT":"INCORRECT",answer,input.timeSpent(),input.confidence(),next,Timestamp.from(Instant.parse(plan.get("nextReviewAt").toString())));
    }
}
