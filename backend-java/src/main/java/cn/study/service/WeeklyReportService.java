package cn.study.service;
import cn.study.dto.WeeklySummary;
import cn.study.repository.*;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
@Service
public class WeeklyReportService {
    private final Db db;private final StatisticsService statistics;private final TaskRepository tasks;private final DashboardService dates;private final Clock clock;
    public WeeklyReportService(Db db,StatisticsService statistics,TaskRepository tasks,DashboardService dates,Clock clock) { this.db=db;this.statistics=statistics;this.tasks=tasks;this.dates=dates;this.clock=clock; }
    public List<Map<String,Object>> list() { return db.rows("select w.*,(select status from ai_tasks t where t.kind='WEEKLY_REPORT' and t.reference_id=w.id order by t.created_at desc limit 1) as ai_status from weekly_reports w order by week_start desc limit 52"); }
    public Map<String,Object> get(UUID id) { return db.one("select * from weekly_reports where id=?",id); }
    @Transactional public Map<String,Object> generate(LocalDate date) {
        if(date.isAfter(LocalDate.now(clock))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"不能生成未来的学习周报");
        LocalDate from=date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),until=from.plusDays(7);
        var stats=new LinkedHashMap<>(statistics.range(from,until));
        stats.put("reasonDistribution",db.rows("select coalesce(m.reason,'未确认') as name,sum(s.wrong_count) as value from study_records s left join mistakes m on m.question_id=s.question_id where s.question_id is not null and s.wrong_count>0 and s.studied_at>=? and s.studied_at<? group by coalesce(m.reason,'未确认') order by value desc",dates.start(from),dates.start(until)));
        stats.put("currentAffairs",db.rows("select id,title,source,source_url,publish_time,fetch_time,source_unverified,left(content,2000) as content,analysis_json from current_affairs where fetch_time>=? and fetch_time<? order by publish_time desc nulls last limit 10",dates.start(from),dates.start(until)));
        var row=db.one("""
            insert into weekly_reports(week_start,week_end,stats_json) values (?,?,?::jsonb)
            on conflict(week_start) do update set stats_json=excluded.stats_json,summary_json=null,revision=weekly_reports.revision+1,updated_at=now() returning *
            """,from,until.minusDays(1),db.json(stats));
        UUID id=Db.uuid(row.get("id"));tasks.enqueue("WEEKLY_REPORT",id,Map.of("revision",row.get("revision")),"weekly:"+id+":"+row.get("revision"));return row;
    }
    @Transactional public boolean apply(UUID id,int revision,WeeklySummary summary) {
        var row=get(id);var stats=(Map<?,?>)row.get("stats");var news=(List<Map<String,Object>>)stats.get("currentAffairs");
        var ids=new HashSet<String>();for(var item:news) ids.add(item.get("id").toString());
        if(summary.currentAffairs().stream().anyMatch(item->!ids.contains(item.newsId().toString()))) throw new AiClient.Failure("AI_INVALID_OUTPUT");
        return db.update("update weekly_reports set summary_json=?::jsonb,updated_at=now() where id=? and revision=?",db.json(summary),id,revision)>0;
    }
}
