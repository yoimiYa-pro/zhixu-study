import json
import logging
import math
import httpx
from app.core.errors import ServiceError
from app.core.settings import Settings
from app.core.usage import record_usage
from app.models.model_selection import valid_model_id
from app.providers.custom_connection import PublicHTTPTransport, public_addresses, relay_headers
from urllib.parse import urlsplit

log = logging.getLogger(__name__)


class OpenAICompatibleProvider:
    def __init__(self, settings: Settings, transport: httpx.AsyncBaseTransport | None = None):
        self.settings, self.transport = settings, transport

    async def _request(self, base: str, key: str, path: str, body: dict | None, method: str = "POST") -> dict:
        result = None
        try:
            proxy = self.settings.ai_proxy_url.get_secret_value() or None
            transport = self.transport
            if self.settings.ai_custom_connection and path != "/embeddings" and transport is None:
                if proxy:
                    parts = urlsplit(base)
                    await public_addresses(parts.hostname, parts.port or 443)
                    proxy = httpx.Proxy(proxy, headers=relay_headers(base, self.settings.ai_service_token.get_secret_value()))
                else:
                    transport = PublicHTTPTransport()
            async with httpx.AsyncClient(timeout=httpx.Timeout(self.settings.ai_timeout_seconds, connect=5),
                                         trust_env=False, transport=transport, proxy=proxy) as client:
                response = await client.request(method, base + path, headers={"Authorization": "Bearer " + key}, json=body)
            if response.status_code >= 400:
                if len(response.content) <= 4_000_000:
                    try:
                        value = response.json()
                        result = value if isinstance(value, dict) else None
                    except ValueError:
                        pass
                log.warning("provider_http_failure status=%s", response.status_code)
                raise ServiceError("PROVIDER_UNAVAILABLE", "模型服务调用失败", 502)
            if len(response.content) > 4_000_000:
                raise ServiceError("PROVIDER_INVALID_OUTPUT", "模型返回超过大小限制", 502)
            value = response.json()
            if not isinstance(value, dict):
                raise ValueError()
            result = value
            return value
        except (httpx.HTTPError, ValueError) as error:
            log.warning("provider_failure type=%s", type(error).__name__)
            raise ServiceError("PROVIDER_UNAVAILABLE", "模型服务连接或响应失败", 502) from None
        finally:
            if body and path in ("/chat/completions", "/embeddings"):
                record_usage(base, body["model"], "EMBEDDING" if path == "/embeddings" else "CHAT", result)

    async def available_models(self) -> list[str]:
        key = self.settings.ai_api_key.get_secret_value()
        if not key:
            raise ServiceError("LLM_NOT_CONFIGURED", "请先在服务器配置模型 API Key")
        result = await self._request(self.settings.ai_base_url, key, "/models", None, "GET")
        data = result.get("data")
        if not isinstance(data, list) or len(data) > 1000:
            raise ServiceError("AI_MODELS_UNAVAILABLE", "无法读取当前服务的模型列表", 502)
        ids = sorted({item["id"] for item in data if isinstance(item, dict)
                      and isinstance(item.get("id"), str) and valid_model_id(item["id"])})
        if not ids:
            raise ServiceError("AI_MODELS_UNAVAILABLE", "当前服务未返回可用模型", 502)
        return ids

    async def complete(self, prompt: str, payload: dict, schema: dict) -> dict:
        key = self.settings.ai_api_key.get_secret_value()
        if not key or not self.settings.ai_model:
            raise ServiceError("LLM_NOT_CONFIGURED", "请在 .env 配置 AI_API_KEY 和 AI_MODEL")
        body = {
            "model": self.settings.ai_model,
            "messages": [
                {"role": "system", "content": prompt + "\n严格遵守此 JSON Schema：\n" + json.dumps(schema, ensure_ascii=False)},
                {"role": "user", "content": json.dumps(payload, ensure_ascii=False)},
            ],
        }
        if self.settings.ai_json_mode:
            body["response_format"] = {"type": "json_object"}
        if self.settings.ai_temperature is not None:
            body["temperature"] = self.settings.ai_temperature
        result = await self._request(self.settings.ai_base_url, key, "/chat/completions", body)
        try:
            content = result["choices"][0]["message"]["content"]
            if not isinstance(content, str) or len(content) > 80000:
                raise ValueError()
            value = json.loads(content)
            if not isinstance(value, dict):
                raise ValueError()
            return value
        except (KeyError, IndexError, TypeError, ValueError):
            raise ServiceError("AI_INVALID_JSON", "模型未返回有效 JSON 对象", 502) from None

    async def embed(self, texts: list[str]) -> list[list[float]]:
        key = self.settings.embedding_api_key.get_secret_value() or self.settings.ai_api_key.get_secret_value()
        if not key or not self.settings.embedding_model:
            raise ServiceError("EMBEDDING_NOT_CONFIGURED", "请在 .env 配置 Embedding 模型与 API Key")
        body = {"model": self.settings.embedding_model, "input": texts, "encoding_format": "float"}
        if self.settings.embedding_send_dimensions:
            body["dimensions"] = self.settings.embedding_dimension
        result = await self._request(self.settings.embedding_base_url, key, "/embeddings", body)
        try:
            data = sorted(result["data"], key=lambda item: item["index"])
            if [item["index"] for item in data] != list(range(len(texts))):
                raise ValueError()
            vectors = [item["embedding"] for item in data]
            for vector in vectors:
                if len(vector) != self.settings.embedding_dimension or not any(vector):
                    raise ValueError()
                if not all(isinstance(value, (int, float)) and not isinstance(value, bool) and math.isfinite(value) for value in vector):
                    raise ValueError()
            return vectors
        except (KeyError, TypeError, ValueError):
            raise ServiceError("EMBEDDING_INVALID_OUTPUT", "Embedding 维度、序号或数值不符合配置", 502) from None
