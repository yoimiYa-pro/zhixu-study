package cn.study.scheduler;
import cn.study.repository.*;
import cn.study.service.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="scheduler.enabled",havingValue="true",matchIfMissing=true)
public class StudyScheduler {
    private final JobRunner runner;private final DashboardService dashboard;private final TaskRepository tasks;private final WeeklyReportService weekly;
    private final Db db;private final Clock clock;private final List<CronExpression> crons;
    public StudyScheduler(JobRunner runner,DashboardService dashboard,TaskRepository tasks,WeeklyReportService weekly,Db db,Clock clock,
        @Value("${scheduler.daily-plan-cron}") String daily,@Value("${scheduler.news-fetch-cron}") String fetch,
        @Value("${scheduler.news-analyze-cron}") String analyze,@Value("${scheduler.review-check-cron}") String review,@Value("${scheduler.weekly-report-cron}") String report) {
        this.runner=runner;this.dashboard=dashboard;this.tasks=tasks;this.weekly=weekly;this.db=db;this.clock=clock;
        crons=List.of(daily,fetch,analyze,review,report).stream().map(CronExpression::parse).toList();
    }
    @Scheduled(cron="${scheduler.daily-plan-cron}",zone="${app.timezone}") public void plan() { execute(0,LocalDate.now(clock)); }
    @Scheduled(cron="${scheduler.news-fetch-cron}",zone="${app.timezone}") public void fetch() { execute(1,LocalDate.now(clock)); }
    @Scheduled(cron="${scheduler.news-analyze-cron}",zone="${app.timezone}") public void analyze() { execute(2,LocalDate.now(clock)); }
    @Scheduled(cron="${scheduler.review-check-cron}",zone="${app.timezone}") public void review() { execute(3,LocalDate.now(clock)); }
    @Scheduled(cron="${scheduler.weekly-report-cron}",zone="${app.timezone}") public void report() { execute(4,LocalDate.now(clock)); }
    public void execute(int job,LocalDate date) {
        String kind=List.of("DAILY_PLAN","NEWS_FETCH","NEWS_ANALYZE","REVIEW_CHECK","WEEKLY_REPORT").get(job);
        runner.run(kind,date,()->switch(job) {
            case 0->{ dashboard.plan();yield Map.of("planned",true); }
            case 1->Map.of("taskId",tasks.enqueue("NEWS_FETCH",null,Map.of(),"scheduled:news-fetch:"+date));
            case 2->Map.of("taskId",tasks.enqueue("ANALYZE_NEWS_BATCH",null,Map.of("date",date.toString()),"scheduled:news-analysis:"+date));
            case 3->{
                dashboard.plan();long due=db.count("select count(*) from review_plans p join questions q on q.id=p.question_id where q.deleted_at is null and p.next_review_at<?",dashboard.start(date.plusDays(1)));
                db.update("update daily_tasks set target=?,completed=? where task_date=? and kind='REVIEW'",(int)due,due==0,date);
                yield Map.of("dueReviews",due);
            }
            case 4->Map.of("reportId",weekly.generate(date).get("id"));
            default->throw new IllegalArgumentException("Unknown scheduled job");
        });
    }
    @EventListener(ApplicationReadyEvent.class) public void catchUp() {
        ZonedDateTime now=clock.instant().atZone(clock.getZone());
        for(int job=0;job<4;job++) {
            var expected=crons.get(job).next(now.toLocalDate().atStartOfDay(clock.getZone()).minusNanos(1));
            if(expected!=null && !expected.isAfter(now) && expected.toLocalDate().equals(now.toLocalDate())) execute(job,now.toLocalDate());
        }
        var candidate=crons.get(4).next(now.minusWeeks(1).minusSeconds(1));ZonedDateTime latest=null;
        for(int n=0;candidate!=null && !candidate.isAfter(now) && n<64;n++) { latest=candidate;candidate=crons.get(4).next(candidate); }
        if(latest!=null) execute(4,latest.toLocalDate());
    }
}
