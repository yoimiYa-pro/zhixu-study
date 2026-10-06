package cn.study.dto;
import java.time.OffsetDateTime;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public final class NewsModels {
    private NewsModels() {}
    public record Article(@NotBlank @Size(max=500) String title,@NotBlank @Size(max=30000) String content,
        @NotBlank @Size(max=200) String source,@Size(max=2000) String sourceUrl,OffsetDateTime publishTime,
        @NotNull OffsetDateTime fetchTime,boolean sourceUnverified) {}
    public record Source(@NotBlank @Size(max=200) String name,@NotBlank @Size(max=2000) String url,@Pattern(regexp="rss|site") String kind) {}
    public record SourceResult(@NotBlank @Size(max=200) String name,@NotBlank @Size(max=2000) String url,@Pattern(regexp="rss|site") String kind,
        @Pattern(regexp="ok|failed") String status,@Min(0) int articles,@Size(max=100) String errorCode) {}
    public record Batch(@NotNull @Size(max=30) List<@Valid Article> articles,@Min(0) int failedFeeds,@Size(max=12) List<@Valid SourceResult> sources) {
        public Batch(List<Article> articles,int failedFeeds) { this(articles,failedFeeds,List.of()); }
    }
    public record Material(@NotBlank @Size(max=300) String title,@NotBlank @Size(max=20000) String content,
        @NotBlank @Pattern(regexp="经济|科技|教育|基层治理|乡村振兴|生态文明|文化|社会治理|民生|数字政府") String category,
        @NotBlank @Pattern(regexp="案例|政策|金句|观点|数据|人物案例") String kind,
        @NotNull @Size(max=20) List<@Size(max=100) String> tags) {}
    public record Essay(@NotNull @Size(max=3000) String background,@NotNull @Size(max=10) List<@Size(max=3000) String> achievements,
        @NotNull @Size(max=10) List<@Size(max=3000) String> problems,@NotNull @Size(max=10) List<@Size(max=3000) String> causes,
        @NotNull @Size(max=10) List<@Size(max=3000) String> solutions,@NotNull @Size(max=10) List<@Size(max=3000) String> quotes,
        @NotNull @Size(max=10) List<@Size(max=3000) String> themes) {}
    public record Analysis(@NotBlank @Size(max=3000) String summary,@NotNull @Size(max=10) List<@Size(max=1000) String> keyTime,
        @NotNull @Size(max=10) List<@Size(max=1000) String> keyLocation,@NotNull @Size(max=15) List<@Size(max=1000) String> keyPeople,
        @NotNull @Size(max=15) List<@Size(max=1000) String> keyNumbers,@NotNull @Size(max=15) List<@Size(max=1000) String> policies,
        @NotNull @Size(max=15) List<@Size(max=2000) String> statements,
        @NotNull Map<@Pattern(regexp="政治|经济|科技|法律|文化|地理|国际") String,@Size(max=20) List<@Size(max=2000) String>> examPoints,
        @NotNull @Valid Essay essay,@NotNull @Size(max=5) List<@Valid Material> materials,
        @Size(max=6) List<@NotBlank @Pattern(regexp="国内时政|国际|经济|科技|教育|民生|法治|生态文明|文化|乡村振兴|基层治理|数字政府") String> tags) {
        public Analysis(String summary,List<String> keyTime,List<String> keyLocation,List<String> keyPeople,List<String> keyNumbers,List<String> policies,List<String> statements,Map<String,List<String>> examPoints,Essay essay,List<Material> materials) {
            this(summary,keyTime,keyLocation,keyPeople,keyNumbers,policies,statements,examPoints,essay,materials,List.of());
        }
    }
}
