from typing import Literal
from urllib.parse import urlsplit
from pydantic import BaseModel, ConfigDict, Field, AwareDatetime, field_validator

Category = Literal["经济", "科技", "教育", "基层治理", "乡村振兴", "生态文明", "文化", "社会治理", "民生", "数字政府"]
MaterialKind = Literal["案例", "政策", "金句", "观点", "数据", "人物案例"]
NewsTag = Literal["国内时政", "国际", "经济", "科技", "教育", "民生", "法治", "生态文明", "文化", "乡村振兴", "基层治理", "数字政府"]


class Article(BaseModel):
    model_config = ConfigDict(extra="forbid")
    title: str = Field(min_length=1, max_length=500)
    content: str = Field(min_length=1, max_length=30000)
    source: str = Field(min_length=1, max_length=200)
    sourceUrl: str | None = Field(default=None, max_length=2000)
    publishTime: AwareDatetime | None = None
    fetchTime: AwareDatetime
    sourceUnverified: bool = True

    @field_validator("sourceUrl")
    @classmethod
    def source_url(cls, value):
        if value is None:
            return value
        parts = urlsplit(value)
        if parts.scheme != "https" or not parts.hostname or parts.username or parts.password:
            raise ValueError("Source URL must be HTTPS")
        return value


class NewsSource(BaseModel):
    name: str = Field(min_length=1, max_length=200)
    url: str = Field(max_length=2000)
    kind: Literal["rss", "site"]


class SourceResult(NewsSource):
    status: Literal["ok", "failed"]
    articles: int = Field(ge=0)
    errorCode: str | None = None


class FeedBatch(BaseModel):
    articles: list[Article] = Field(max_length=30)
    failedFeeds: int = Field(ge=0)
    sources: list[SourceResult] = Field(default_factory=list, max_length=12)


class EssayExtract(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    title: str = Field(min_length=1, max_length=300)
    content: str = Field(min_length=1, max_length=20000)
    category: Category
    kind: MaterialKind
    tags: list[str] = Field(max_length=20)


class EssayAngles(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    background: str = Field(max_length=3000)
    achievements: list[str] = Field(max_length=10)
    problems: list[str] = Field(max_length=10)
    causes: list[str] = Field(max_length=10)
    solutions: list[str] = Field(max_length=10)
    quotes: list[str] = Field(max_length=10)
    themes: list[str] = Field(max_length=10)


class NewsAnalysis(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    summary: str = Field(min_length=1, max_length=3000)
    keyTime: list[str] = Field(max_length=10)
    keyLocation: list[str] = Field(max_length=10)
    keyPeople: list[str] = Field(max_length=15)
    keyNumbers: list[str] = Field(max_length=15)
    policies: list[str] = Field(max_length=15)
    statements: list[str] = Field(max_length=15)
    examPoints: dict[Literal["政治", "经济", "科技", "法律", "文化", "地理", "国际"], list[str]]
    essay: EssayAngles
    materials: list[EssayExtract] = Field(max_length=5)
    tags: list[NewsTag] = Field(default_factory=list, max_length=6)


class NewsAnalyzeRequest(BaseModel):
    article: Article
