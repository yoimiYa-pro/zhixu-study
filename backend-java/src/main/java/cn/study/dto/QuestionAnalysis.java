package cn.study.dto;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record QuestionAnalysis(
    @NotBlank @Pattern(regexp="常识判断|言语理解|数量关系|判断推理|资料分析|申论") String questionType,
    @NotEmpty @Size(max=20) List<@NotBlank @Size(max=200) String> knowledgePoints,
    @NotBlank @Pattern(regexp="简单|中等|困难") String difficulty,
    @NotBlank @Pattern(regexp="知识盲区|理解错误|审题错误|计算错误|方法错误|粗心|时间不足|记忆错误") String mistakeReason,
    @NotBlank @Size(max=12000) String analysis,
    @NotBlank @Size(max=8000) String correctAnswerExplanation,
    @NotNull @Size(max=10) List<@NotBlank @Size(max=1000) String> pitfalls,
    @NotEmpty @Size(max=12) List<@NotBlank @Size(max=1000) String> solutionSteps,
    @NotBlank @Size(max=1000) String quickMethod,
    @NotNull @Size(max=15) List<@NotBlank @Size(max=1000) String> relatedKnowledge,
    @NotEmpty @Size(max=5) List<@Valid ReviewSuggestion> reviewSuggestions
) {
    public record ReviewSuggestion(@Min(1) @Max(60) int afterDays,@NotBlank @Size(max=1000) String reason) {}
}
