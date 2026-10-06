from typing import Literal
from uuid import UUID
from pydantic import BaseModel, ConfigDict, Field

EntityType = Literal["question", "question_analysis", "knowledge", "essay", "current_affair"]


class Document(BaseModel):
    model_config = ConfigDict(extra="forbid")
    entityId: UUID
    entityType: EntityType
    text: str = Field(min_length=1, max_length=200000)
    title: str = Field(default="", max_length=500)
    source: str = Field(default="", max_length=1000)
    knowledgePoints: list[str] = Field(default_factory=list, max_length=20)


class SearchRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    query: str = Field(min_length=1, max_length=50000)
    entityType: EntityType | None = None
    excludeIds: list[UUID] = Field(default_factory=list, max_length=30)
    limit: int = Field(default=8, ge=1, le=20)


class SearchHit(BaseModel):
    entityId: UUID
    entityType: EntityType
    score: float
    title: str
    text: str
    source: str
    knowledgePoints: list[str]


class DeleteDocument(BaseModel):
    model_config = ConfigDict(extra="forbid")
    entityId: UUID
    entityType: EntityType
