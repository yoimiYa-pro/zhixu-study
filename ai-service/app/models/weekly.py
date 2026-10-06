from uuid import UUID
from pydantic import BaseModel, ConfigDict, Field


class WeeklyRequest(BaseModel):
    stats: dict


class WeekPlan(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    focus: str = Field(min_length=1, max_length=200)
    actions: list[str] = Field(min_length=1, max_length=5)


class NewsHighlight(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    newsId: UUID = Field(strict=False)
    insight: str = Field(min_length=1, max_length=1000)


class WeeklySummary(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    overview: str = Field(min_length=1, max_length=2000)
    strengths: list[str] = Field(max_length=8)
    weaknesses: list[str] = Field(max_length=8)
    priorities: list[str] = Field(max_length=10)
    nextWeekPlan: list[WeekPlan] = Field(max_length=7)
    currentAffairs: list[NewsHighlight] = Field(max_length=10)
