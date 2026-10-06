package cn.study.dto;

import java.util.*;
import jakarta.validation.constraints.*;

public record QuestionInput(
    @NotBlank @Size(max=20000) String content,
    @Size(max=8) Map<String,@NotBlank @Size(max=5000) String> options,
    @NotBlank @Size(max=5000) String correctAnswer,
    @Size(max=5000) String userAnswer,
    @Size(max=20000) String explanation,
    @NotBlank @Pattern(regexp="常识判断|言语理解|数量关系|判断推理|资料分析|申论") String questionType,
    @Pattern(regexp="简单|中等|困难") String difficulty,
    @Size(max=20) List<@NotBlank @Size(max=200) String> knowledgePoints,
    @Pattern(regexp="知识盲区|理解错误|审题错误|计算错误|方法错误|粗心|时间不足|记忆错误|未确认") String mistakeReason,
    @Size(max=300) String source,
    @Min(1980) @Max(2100) Integer year,
    @Size(max=100) String region,
    Boolean mistake,
    @Min(0) @Max(86400) Integer timeSpent,
    UUID clientRequestId
) {
    public QuestionInput {
        options=options==null?Map.of():options; userAnswer=userAnswer==null?"":userAnswer;
        explanation=explanation==null?"":explanation; difficulty=difficulty==null?"中等":difficulty;
        knowledgePoints=knowledgePoints==null?List.of():knowledgePoints;
        mistakeReason=mistakeReason==null?"未确认":mistakeReason;
        source=source==null?"":source;region=region==null?"":region;
        mistake=mistake==null?true:mistake;timeSpent=timeSpent==null?0:timeSpent;
    }
}
