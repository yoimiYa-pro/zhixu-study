import hmac
from fastapi import Depends, Header
from app.core.settings import Settings, get_settings
from app.core.errors import ServiceError


def require_token(authorization: str | None = Header(default=None), settings: Settings = Depends(get_settings)):
    token = settings.ai_service_token.get_secret_value()
    if not token:
        raise ServiceError("INTERNAL_TOKEN_NOT_CONFIGURED", "AI 内部访问令牌未配置")
    if not authorization or not hmac.compare_digest(authorization, "Bearer " + token):
        raise ServiceError("INTERNAL_AUTH_REQUIRED", "内部服务认证失败", 401)
