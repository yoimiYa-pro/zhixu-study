package cn.study.dto;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.*;
public final class LibraryInput {
    private LibraryInput() {}
    public record Idiom(@NotBlank @Size(max=100) String word,@NotBlank @Pattern(regexp="成语|词语") String kind,
        @NotBlank @Size(max=10000) String definition,@Size(max=30) List<@NotBlank @Size(max=100) String> synonyms,
        @Size(max=30) List<@NotBlank @Size(max=100) String> antonyms,@Size(max=5000) String scenario,
        @Size(max=5000) String pitfalls,@Size(max=5000) String example) {}
    public record State(Boolean favorite,Boolean mastered) {}
    public record Material(@NotBlank @Size(max=300) String title,@NotBlank @Size(max=20000) String content,
        @NotBlank @Pattern(regexp="经济|科技|教育|基层治理|乡村振兴|生态文明|文化|社会治理|民生|数字政府") String category,
        @NotBlank @Pattern(regexp="案例|政策|金句|观点|数据|人物案例") String kind,
        @Size(max=20) List<@NotBlank @Size(max=100) String> tags,@Size(max=200) String source,
        @Size(max=2000) String sourceUrl,OffsetDateTime publishTime,Boolean sourceUnverified) {}
}
