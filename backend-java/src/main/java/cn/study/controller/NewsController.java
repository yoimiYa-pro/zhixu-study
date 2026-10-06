package cn.study.controller;
import cn.study.dto.NewsModels.Article;
import cn.study.dto.NewsModels.Source;
import cn.study.service.NewsService;
import cn.study.service.AiClient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping("/api/current-affairs")
@Validated
public class NewsController {
    private final NewsService news;private final Clock clock;private final AiClient ai;
    public NewsController(NewsService news,Clock clock,AiClient ai) { this.news=news;this.clock=clock;this.ai=ai; }
    @GetMapping("/today") public List<Map<String,Object>> today() { return news.list(LocalDate.now(clock)); }
    @GetMapping public List<Map<String,Object>> list(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date,
        @RequestParam(required=false) @Size(max=30) String tag,@RequestParam(required=false) @Size(max=200) String source,@RequestParam(required=false) @Size(max=100) String q) { return news.list(date==null?LocalDate.now(clock):date,tag,source,q); }
    @GetMapping("/overview") public Map<String,Object> overview(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date) { return news.overview(date==null?LocalDate.now(clock):date); }
    @GetMapping("/sources") public Source[] sources() { return ai.get("/ai/news/sources",Source[].class); }
    public record Tags(@NotNull @Size(max=6) List<@NotBlank @Pattern(regexp="国内时政|国际|经济|科技|教育|民生|法治|生态文明|文化|乡村振兴|基层治理|数字政府") String> tags) {}
    @PatchMapping("/{id}/tags") public Map<String,Object> tags(@PathVariable UUID id,@Valid @RequestBody Tags input) { return news.tags(id,input.tags()); }
    @GetMapping("/{id}") public Map<String,Object> get(@PathVariable UUID id) { return news.get(id); }
    @PostMapping("/fetch") public ResponseEntity<?> fetch() { return ResponseEntity.accepted().body(Map.of("taskId",news.fetch())); }
    @PostMapping("/{id}/analyze") public ResponseEntity<?> analyze(@PathVariable UUID id) { return ResponseEntity.accepted().body(Map.of("taskId",news.analyze(id))); }
    public record Manual(@NotBlank @Size(max=500) String title,@NotBlank @Size(max=30000) String content,
        @NotBlank @Size(max=200) String source,@Size(max=2000) String sourceUrl,OffsetDateTime publishTime,Boolean sourceUnverified,
        @Valid Tags classification) {}
    @PostMapping public ResponseEntity<?> create(@Valid @RequestBody Manual input) {
        var article=new Article(input.title(),input.content(),input.source(),input.sourceUrl(),input.publishTime(),OffsetDateTime.now(clock),!Boolean.FALSE.equals(input.sourceUnverified()));
        var row=news.store(article,true);
        if(input.classification()!=null) row=news.tags(UUID.fromString(row.get("id").toString()),input.classification().tags());
        return ResponseEntity.status(201).body(row);
    }
}
