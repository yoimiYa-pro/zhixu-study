import hashlib
import re
from pydantic import BaseModel, ConfigDict, Field, SecretStr, field_validator
from app.core.settings import Settings

MODEL_ID = r"^[A-Za-z0-9][A-Za-z0-9._:/-]{0,199}$"


def provider_id(settings: Settings) -> str:
    return hashlib.sha256(settings.ai_base_url.encode("utf-8")).hexdigest()


def valid_model_id(value: str) -> bool:
    return bool(re.fullmatch(MODEL_ID, value))


class ModelCatalog(BaseModel):
    providerId: str
    defaultModel: str
    models: list[str]
    message: str | None = None


class ConnectionProbe(BaseModel):
    model_config = ConfigDict(extra="forbid")
    baseUrl: str = Field(max_length=2048)
    apiKey: SecretStr = Field(min_length=1, max_length=4096)
    model: str = Field(pattern=MODEL_ID)
    jsonMode: bool = True

    @field_validator("baseUrl")
    @classmethod
    def valid_base(cls, value):
        from app.providers.custom_connection import public_base_url
        return public_base_url(value)

    @field_validator("apiKey")
    @classmethod
    def valid_key(cls, value):
        if not re.fullmatch(r"[\x21-\x7E]{1,4096}", value.get_secret_value()):
            raise ValueError("Invalid API Key format")
        return value


class ModelProbe(BaseModel):
    model_config = ConfigDict(extra="forbid")
    model: str = Field(pattern=MODEL_ID)


class ProbeResult(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    ok: bool
