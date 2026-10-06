package cn.study.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
public record WeeklySummary(@NotBlank @Size(max=2000) String overview,@NotNull @Size(max=8) List<@Size(max=1500) String> strengths,
    @NotNull @Size(max=8) List<@Size(max=1500) String> weaknesses,@NotNull @Size(max=10) List<@Size(max=1000) String> priorities,
    @NotNull @Size(max=7) List<@Valid Plan> nextWeekPlan,@NotNull @Size(max=10) List<@Valid Highlight> currentAffairs) {
    public record Plan(@NotBlank @Size(max=200) String focus,@NotNull @Size(min=1,max=5) List<@Size(max=1000) String> actions) {}
    public record Highlight(@NotNull UUID newsId,@NotBlank @Size(max=1000) String insight) {}
}
