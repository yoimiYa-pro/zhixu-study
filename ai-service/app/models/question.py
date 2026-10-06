from typing import Annotated, Literal
from pydantic import BaseModel, ConfigDict, Field, StringConstraints

QuestionType = Literal["常识判断", "言语理解", "数量关系", "判断推理", "资料分析", "申论"]
MistakeReason = Literal["知识盲区", "理解错误", "审题错误", "计算错误", "方法错误", "粗心", "时间不足", "记忆错误"]
ShortText = Annotated[str, StringConstraints(min_length=1, max_length=1000, strip_whitespace=True)]


class StrictOutput(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)


class ReviewSuggestion(StrictOutput):
    afterDays: int = Field(ge=1, le=60)
    reason: ShortText


class QuestionAnalysis(StrictOutput):
    questionType: QuestionType
    knowledgePoints: list[Annotated[str, StringConstraints(min_length=1, max_length=200)]] = Field(min_length=1, max_length=20)
    difficulty: Literal["简单", "中等", "困难"]
    mistakeReason: MistakeReason
    analysis: str = Field(min_length=1, max_length=12000)
    correctAnswerExplanation: str = Field(min_length=1, max_length=8000)
    pitfalls: list[ShortText] = Field(max_length=10)
    solutionSteps: list[ShortText] = Field(min_length=1, max_length=12)
    quickMethod: ShortText
    relatedKnowledge: list[ShortText] = Field(max_length=15)
    reviewSuggestions: list[ReviewSuggestion] = Field(min_length=1, max_length=5)


class QuestionPayload(BaseModel):
    model_config = ConfigDict(extra="ignore")
    content: str = Field(min_length=1, max_length=20000)
    options: dict[str, str] = Field(default_factory=dict, max_length=8)
    correctAnswer: str = Field(min_length=1, max_length=5000)
    userAnswer: str = Field(default="", max_length=5000)
    explanation: str = Field(default="", max_length=20000)
    questionType: QuestionType
    knowledgePoints: list[str] = Field(default_factory=list, max_length=20)


class AnalyzeRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    question: QuestionPayload
