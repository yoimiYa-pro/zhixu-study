from typing import Literal
from uuid import UUID
from pydantic import BaseModel, ConfigDict, Field

EntityKind = Literal["question", "knowledge", "essay", "current_affair", "study"]


class Context(BaseModel):
    entityType: EntityKind
    entityId: UUID
    title: str = Field(max_length=500)
    content: str = Field(max_length=6000)


class Citation(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    entityType: EntityKind
    entityId: UUID = Field(strict=False)


class History(BaseModel):
    role: Literal["user", "assistant"]
    content: str = Field(max_length=12000)


class ChatRequest(BaseModel):
    query: str = Field(min_length=1, max_length=3000)
    contexts: list[Context] = Field(max_length=25)
    history: list[History] = Field(default_factory=list, max_length=20)
    learningStatus: dict = Field(default_factory=dict)
    retrievalStatus: Literal["VECTOR_AND_DATABASE", "DATABASE_ONLY", "VECTOR_UNAVAILABLE"]


class ChatAnswer(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    answer: str = Field(min_length=1, max_length=12000)
    citations: list[Citation] = Field(max_length=15)


class EssayRequest(BaseModel):
    topic: str = Field(min_length=1, max_length=300)
    contexts: list[Context] = Field(max_length=25)
    retrievalStatus: Literal["VECTOR_AND_DATABASE", "DATABASE_ONLY", "VECTOR_UNAVAILABLE"]


class EssayOutline(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    background: str = Field(max_length=3000)
    problems: list[str] = Field(max_length=10)
    causes: list[str] = Field(max_length=10)
    solutions: list[str] = Field(max_length=10)
    cases: list[str] = Field(max_length=6)
    quotes: list[str] = Field(max_length=10)
    citations: list[Citation] = Field(max_length=15)
