package cn.study.service;
import cn.study.repository.Db;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
@Service
public class StatisticsService {
    private final Db db;private final DashboardService dates;private final KnowledgeService knowledge;private final Clock clock;
    public StatisticsService(Db db,DashboardService dates,KnowledgeService knowledge,Clock clock) { this.db=db;this.dates=dates;this.knowledge=knowledge;this.clock=clock; }
    public Map<String,Object> get(int days) {
        if(days!=7 && days!=30) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"统计范围支持 7 天或 30 天");
        LocalDate end=LocalDate.now(clock).plusDays(1);return range(end.minusDays(days),end);
    }
    public Map<String,Object> range(LocalDate from,LocalDate until) {
        Timestamp start=dates.start(from),end=dates.start(until);
        var summary=db.one("""
            select coalesce(sum(quantity) filter(where kind='PRACTICE'),0) as practice_count,
              coalesce(sum(wrong_count) filter(where kind='PRACTICE'),0) as wrong_count,
              coalesce(sum(time_spent),0) as study_seconds
            from study_records where studied_at>=? and studied_at<?
            """,start,end);
        long count=((Number)summary.get("practiceCount")).longValue(),wrong=((Number)summary.get("wrongCount")).longValue();
        summary.put("accuracy",percentage(count-wrong,count));
        var reviews=db.one("select count(*) filter(where outcome<>'SKIP') as completed,count(*) filter(where outcome='CORRECT') as correct,count(*) filter(where outcome='SKIP') as skipped from reviews where reviewed_at>=? and reviewed_at<?",start,end);
        long completed=((Number)reviews.get("completed")).longValue(),skipped=((Number)reviews.get("skipped")).longValue();
        long pending=db.count("select count(*) from review_plans p join questions q on q.id=p.question_id where q.deleted_at is null and p.next_review_at<?",end);
        summary.put("reviewsCompleted",completed);summary.put("reviewsSkipped",skipped);summary.put("duePending",pending);
        summary.put("reviewCompletionRate",percentage(completed,completed+skipped+pending));
        summary.put("reviewAccuracy",percentage(((Number)reviews.get("correct")).longValue(),completed));
        summary.put("newMistakes",db.count("select count(*) from mistakes where created_at>=? and created_at<?",start,end));
        var groups=db.rows("""
            select date(timezone(?,studied_at)) as day,
              coalesce(sum(quantity) filter(where kind='PRACTICE'),0) as practice_count,
              coalesce(sum(wrong_count) filter(where kind='PRACTICE'),0) as wrong_count,
              coalesce(sum(quantity) filter(where kind='REVIEW'),0) as reviews_completed,
              coalesce(sum(time_spent),0) as study_seconds
            from study_records where studied_at>=? and studied_at<? group by 1
            """,clock.getZone().getId(),start,end);
        var byDay=new HashMap<String,Map<String,Object>>();for(var row:groups) byDay.put(row.get("day").toString(),row);
        var trend=new ArrayList<Map<String,Object>>();
        for(LocalDate day=from;day.isBefore(until);day=day.plusDays(1)) {
            var row=byDay.getOrDefault(day.toString(),new LinkedHashMap<>(Map.of("day",day.toString(),"practiceCount",0,"wrongCount",0,"reviewsCompleted",0,"studySeconds",0)));
            long n=((Number)row.get("practiceCount")).longValue(),w=((Number)row.get("wrongCount")).longValue();row.put("accuracy",percentage(n-w,n));trend.add(row);
        }
        var points=knowledge.list();
        return Map.of("start",from.toString(),"end",until.minusDays(1).toString(),"timezone",clock.getZone().getId(),"summary",summary,"trend",trend,
            "typeDistribution",db.rows("select q.question_type as name,count(*) as value from mistakes m join questions q on q.id=m.question_id where q.deleted_at is null group by q.question_type order by value desc"),
            "reasonDistribution",db.rows("select m.reason as name,count(*) as value from mistakes m join questions q on q.id=m.question_id where q.deleted_at is null group by m.reason order by value desc"),
            "knowledgeDistribution",db.rows("select k.name,count(*) as value from knowledge_points k join question_knowledge_points x on x.knowledge_point_id=k.id join questions q on q.id=x.question_id where q.deleted_at is null group by k.name order by value desc limit 20"),
            "weakPoints",points.stream().filter(p->((Number)p.get("practiceCount")).longValue()>0).sorted(Comparator.comparingDouble(p->((Number)p.get("mastery")).doubleValue())).limit(10).toList());
    }
    static double percentage(long number,long total) { return total==0?0:Math.round(1000.0*number/total)/10.0; }
}
