package cn.study.service;

import cn.study.repository.Db;
import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {
    private final Db db;private final Clock clock;private final KnowledgeService knowledge;private final int goal;
    public DashboardService(Db db,Clock clock,KnowledgeService knowledge,@Value("${DAILY_QUESTION_GOAL:20}") int goal) { this.db=db;this.clock=clock;this.knowledge=knowledge;this.goal=goal; }
    public Timestamp start(LocalDate date) { return Timestamp.from(date.atStartOfDay(clock.getZone()).toInstant()); }
    public LocalDate today() { return LocalDate.now(clock); }
    @Transactional public void plan() {
        LocalDate today=today();long due=db.count("select count(*) from review_plans p join questions q on q.id=p.question_id where q.deleted_at is null and p.next_review_at<?",start(today.plusDays(1)));
        db.update("insert into daily_tasks(task_date,kind,title,target) values (?,'PRACTICE','完成今日练习',?) on conflict do nothing",today,goal);
        db.update("insert into daily_tasks(task_date,kind,title,target,completed) values (?,'REVIEW','复习到期错题',?,?) on conflict do nothing",today,(int)due,due==0);
        db.update("insert into daily_tasks(task_date,kind,title,target) values (?,'NEWS','阅读今日时政',1) on conflict do nothing",today);
    }
    public Map<String,Object> get() {
        plan();LocalDate date=today();Timestamp start=start(date),end=start(date.plusDays(1));
        var stats=db.one("select coalesce(sum(quantity),0) as practice_count,coalesce(sum(wrong_count),0) as wrong_count from study_records where kind='PRACTICE' and studied_at>=? and studied_at<?",start,end);
        stats.put("reviewsCompleted",db.count("select count(*) from reviews where outcome<>'SKIP' and reviewed_at>=? and reviewed_at<?",start,end));
        stats.put("dueReviews",db.count("select count(*) from review_plans p join questions q on q.id=p.question_id where q.deleted_at is null and p.next_review_at<?",end));
        stats.put("weeklySeconds",db.count("select coalesce(sum(time_spent),0) from study_records where studied_at>=? and studied_at<?",start(date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))),end));
        var days=new HashSet<LocalDate>();for(var row:db.rows("select distinct date(timezone(?,studied_at)) as day from study_records where quantity>0 or time_spent>0",clock.getZone().getId())) days.add(LocalDate.parse(row.get("day").toString()));
        LocalDate cursor=days.contains(date)?date:date.minusDays(1);int streak=0;while(days.contains(cursor)) { streak++;cursor=cursor.minusDays(1); }
        return Map.of("date",date.toString(),"timezone",clock.getZone().getId(),"stats",stats,"streak",streak,
            "tasks",db.rows("select * from daily_tasks where task_date=? order by kind",date),
            "currentAffairs",db.rows("select * from current_affairs where fetch_time>=? and fetch_time<? order by publish_time desc nulls last limit 3",start,end),
            "idioms",db.rows("select * from idioms where mastered=false order by md5(id::text || ?) limit 1",date.toString()),
            "materials",db.rows("select * from essay_materials order by created_at desc limit 1"),
            "weakPoints",knowledge.list().stream().filter(p->((Number)p.get("questionCount")).longValue()>0).sorted(Comparator.comparingDouble(p->((Number)p.get("mastery")).doubleValue())).limit(6).toList());
    }
    public Map<String,Object> task(UUID id,boolean completed) { return db.one("update daily_tasks set completed=? where id=? and task_date=? returning *",completed,id,today()); }
}
