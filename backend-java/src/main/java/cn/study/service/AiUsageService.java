package cn.study.service;

import cn.study.repository.Db;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.slf4j.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AiUsageService {
    private static final Logger log=LoggerFactory.getLogger(AiUsageService.class);
    public record Event(String id,String providerKey,String providerName,String model,String kind,
        long calls,long reportedCalls,long incompleteCalls,long inputTokens,long outputTokens,long totalTokens,
        long cachedInputTokens,long cacheReportedCalls) {}
    public record Envelope(int version,List<Event> events) {}
    private final Db db;private final ObjectMapper mapper;private final Clock clock;
    private final TransactionTemplate writes;
    public AiUsageService(Db db,ObjectMapper mapper,Clock clock,PlatformTransactionManager transactions) {
        this.db=db;this.mapper=mapper;this.clock=clock;writes=new TransactionTemplate(transactions);
        writes.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    // Telemetry failure must not retry an already consumed, billable provider call.
    public void record(String header) {
        if(header==null || header.isBlank()) return;
        try {
            if(header.length()>32768) throw new IllegalArgumentException();
            var envelope=mapper.readValue(header,Envelope.class);
            if(envelope.version()!=1 || envelope.events()==null || envelope.events().size()>64) throw new IllegalArgumentException();
            for(var event:envelope.events()) validate(event);
            writes.executeWithoutResult(status -> { for(var event:envelope.events()) db.update("""
                insert into ai_usage(id,provider_key,provider_name,model,kind,calls,reported_calls,incomplete_calls,
                    input_tokens,output_tokens,total_tokens,cached_input_tokens,cache_reported_calls,recorded_at)
                values(?,?,?,?,?,?,?,?,?,?,?,?,?,?) on conflict(id) do nothing
                """,UUID.fromString(event.id()),event.providerKey(),event.providerName(),event.model(),event.kind(),
                event.calls(),event.reportedCalls(),event.incompleteCalls(),event.inputTokens(),event.outputTokens(),
                event.totalTokens(),event.cachedInputTokens(),event.cacheReportedCalls(),java.sql.Timestamp.from(clock.instant())); });
        } catch(Exception error) { log.warn("ai_usage_record_failed type={}",error.getClass().getSimpleName()); }
    }
    private static void validate(Event event) {
        if(event!=null) UUID.fromString(event.id());
        if(event==null || event.id()==null || !event.id().matches("[a-fA-F0-9-]{36}") || event.providerKey()==null || !event.providerKey().matches("[a-f0-9]{64}")
            || event.providerName()==null || !event.providerName().matches("[A-Za-z0-9._:-]{1,253}")
            || event.model()==null || !event.model().matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,199}")
            || !Set.of("CHAT","EMBEDDING").contains(event.kind()) || event.calls()<1 || event.calls()>1000000
            || event.reportedCalls()<0 || event.reportedCalls()>event.calls() || event.incompleteCalls()<0 || event.incompleteCalls()>event.calls()
            || event.cacheReportedCalls()<0 || event.cacheReportedCalls()>event.calls()
            || event.inputTokens()<0 || event.outputTokens()<0 || event.totalTokens()<0 || event.cachedInputTokens()<0
            || event.cachedInputTokens()>event.inputTokens() || event.inputTokens()>1000000000000000L
            || event.outputTokens()>1000000000000000L || event.totalTokens()>1000000000000000L) throw new IllegalArgumentException();
    }
    private static final String SUMS="""
        coalesce(sum(calls),0) as calls,coalesce(sum(reported_calls),0) as reported_calls,
        coalesce(sum(calls-reported_calls),0) as unreported_calls,coalesce(sum(incomplete_calls),0) as incomplete_calls,
        coalesce(sum(input_tokens),0) as input_tokens,coalesce(sum(output_tokens),0) as output_tokens,
        coalesce(sum(total_tokens),0) as total_tokens,coalesce(sum(cached_input_tokens),0) as cached_input_tokens,
        coalesce(sum(cache_reported_calls),0) as cache_reported_calls
        """;
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Map<String,Object> summary(int days) {
        if(days<1 || days>90) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"统计范围需在 1 至 90 天内");
        LocalDate today=LocalDate.now(clock),start=today.minusDays(days-1);
        var from=java.sql.Timestamp.from(start.atStartOfDay(clock.getZone()).toInstant());
        var until=java.sql.Timestamp.from(today.plusDays(1).atStartOfDay(clock.getZone()).toInstant());
        String range=" from ai_usage where recorded_at>=? and recorded_at<?";
        var totals=db.one("select "+SUMS+range,from,until);
        var grouped=db.rows("select to_char(recorded_at at time zone ?,'YYYY-MM-DD') as date,"+SUMS+range+" group by 1 order by 1",clock.getZone().getId(),from,until);
        var byDate=new HashMap<String,Map<String,Object>>();for(var row:grouped) byDate.put(row.get("date").toString(),row);
        var daily=new ArrayList<Map<String,Object>>();
        for(LocalDate day=start;!day.isAfter(today);day=day.plusDays(1)) {
            var row=byDate.get(day.toString());
            if(row==null) { row=new LinkedHashMap<>();for(var key:totals.keySet()) row.put(key,0L);row.put("date",day.toString()); }
            daily.add(row);
        }
        var models=db.rows("select provider_key,max(provider_name) as provider_name,model,kind,"+SUMS+range+" group by provider_key,model,kind order by total_tokens desc,calls desc,model,provider_key limit 100",from,until);
        long modelCount=db.count("select count(distinct (provider_key,model,kind))"+range,from,until);
        var first=db.one("select min(recorded_at) as first_recorded_at from ai_usage").get("firstRecordedAt");
        var result=new LinkedHashMap<String,Object>();
        result.put("days",days);result.put("timezone",clock.getZone().getId());result.put("startDate",start.toString());result.put("endDate",today.toString());
        result.put("totals",totals);result.put("daily",daily);result.put("models",models);result.put("modelCount",modelCount);result.put("firstRecordedAt",first);
        return result;
    }
}
