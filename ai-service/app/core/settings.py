from functools import lru_cache
from urllib.parse import urlsplit
from pydantic import Field, SecretStr, ValidationError, field_validator
from app.core.errors import ServiceError
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file="../.env", extra="ignore", env_ignore_empty=True)
    ai_service_token: SecretStr = SecretStr("")
    ai_base_url: str = "https://api.openai.com/v1"
    ai_api_key: SecretStr = SecretStr("")
    ai_model: str = ""
    ai_temperature: float | None = Field(default=None, ge=0, le=2)
    ai_json_mode: bool = True
    ai_custom_connection: bool = False
    embedding_base_url: str = "https://api.openai.com/v1"
    embedding_api_key: SecretStr = SecretStr("")
    embedding_model: str = ""
    embedding_dimension: int = Field(default=1536, ge=1, le=65536)
    embedding_send_dimensions: bool = False
    embedding_chunk_chars: int = Field(default=1800, ge=128, le=2000)
    ai_timeout_seconds: int = Field(default=60, ge=1, le=180)
    ai_proxy_url: SecretStr = SecretStr("")
    qdrant_url: str = "http://127.0.0.1:6333"
    qdrant_api_key: SecretStr = SecretStr("")
    news_feeds: str = ""
    news_sites: str = ""
    news_lookback_days: int = Field(default=7, ge=1, le=30)
    news_proxy_url: SecretStr = SecretStr("")
    news_allowed_hosts: str = "www.gov.cn,gov.cn,www.news.cn,news.cn,www.people.com.cn,people.com.cn"
    news_max_articles: int = Field(default=10, ge=1, le=30)

    @field_validator("ai_base_url", "embedding_base_url", "qdrant_url")
    @classmethod
    def valid_service_url(cls, value: str) -> str:
        parts = urlsplit(value)
        if parts.scheme not in ("http", "https") or not parts.hostname or parts.username or parts.password or parts.query or parts.fragment:
            raise ValueError("Service URL must be HTTP(S), with credentials supplied separately")
        return value.rstrip("/")


@lru_cache
def get_settings() -> Settings:
    try:
        return Settings()
    except ValidationError:
        raise ServiceError("AI_CONFIG_INVALID", "AI 环境配置无效，请检查变量名称、URL 和数值范围") from None
