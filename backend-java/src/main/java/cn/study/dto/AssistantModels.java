package cn.study.dto;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
public final class AssistantModels {
    private AssistantModels() {}
    public record Citation(@NotBlank @Pattern(regexp="question|knowledge|essay|current_affair|study") String entityType,@NotNull UUID entityId) {}
    public record ChatAnswer(@NotBlank @Size(max=12000) String answer,@NotNull @Size(max=15) List<@Valid Citation> citations) {}
    public record Outline(@NotNull @Size(max=3000) String background,@NotNull @Size(max=10) List<@Size(max=2000) String> problems,
        @NotNull @Size(max=10) List<@Size(max=2000) String> causes,@NotNull @Size(max=10) List<@Size(max=2000) String> solutions,
        @NotNull @Size(max=6) List<@Size(max=3000) String> cases,@NotNull @Size(max=10) List<@Size(max=1000) String> quotes,
        @NotNull @Size(max=15) List<@Valid Citation> citations) {}
    public record Hit(@NotNull UUID entityId,@NotBlank @Pattern(regexp="question|question_analysis|knowledge|essay|current_affair") String entityType,
        @NotNull @DecimalMin("-1.001") @DecimalMax("1.001") Double score) {}
}
