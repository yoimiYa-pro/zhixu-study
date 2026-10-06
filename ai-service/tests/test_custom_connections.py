import json
from unittest.mock import AsyncMock, patch
import httpcore
import httpx
import pytest
from app.api.routes import get_provider
import app.api.routes as routes
from app.core.errors import ServiceError
from app.core.settings import Settings, get_settings
from app.main import app
from app.models.model_selection import provider_id
from app.providers.custom_connection import connection_settings, public_addresses, public_base_url
from app.providers.openai_compatible import OpenAICompatibleProvider


def config():
    return Settings(_env_file=None, ai_service_token="test-internal", ai_api_key="server-key", ai_model="server-model",
                    embedding_base_url="https://vectors.example/v1", embedding_model="vector-fixed", embedding_dimension=2)


@pytest.mark.parametrize("base", ["http://models.example/v1", "https://127.0.0.1/v1", "https://169.254.169.254/v1",
                                   "https://[::1]/v1", "https://user:key@models.example/v1", "https://models.example/v1?key=private",
                                   "https://models.example/v1#private", "https://models.example:0/v1", "https://models.example/v1/chat/completions"])
def test_custom_base_rejects_private_and_invalid_urls(base):
    with pytest.raises(ValueError):
        public_base_url(base)


async def test_public_dns_is_checked_and_mixed_results_rejected():
    import asyncio
    import socket
    loop = asyncio.get_running_loop()
    public = (socket.AF_INET, socket.SOCK_STREAM, 6, "", ("8.8.8.8", 443))
    private = (socket.AF_INET, socket.SOCK_STREAM, 6, "", ("10.0.0.1", 443))
    with patch.object(loop, "getaddrinfo", AsyncMock(return_value=[public])):
        assert await public_addresses("models.example", 443) == ["8.8.8.8"]
    for addresses in ([public, private], [private], []):
        with patch.object(loop, "getaddrinfo", AsyncMock(return_value=addresses)):
            with pytest.raises(ServiceError, match="AI_URL_REJECTED"):
                await public_addresses("models.example", 443)


async def test_custom_request_uses_its_key_model_and_base_without_changing_vectors():
    original = config()
    custom = connection_settings(original, "https://models.example/v1/", "custom-key", "custom-model", False)
    provider = get_provider(original, "custom-model", provider_id(custom), custom.ai_base_url, "custom-key", "false")
    calls = []
    def upstream(request):
        calls.append((str(request.url), request.headers["Authorization"], json.loads(request.content)))
        if request.url.path.endswith("/embeddings"):
            return httpx.Response(200, json={"data": [{"index": 0, "embedding": [1.0, 0.0]}]})
        return httpx.Response(200, json={"choices": [{"message": {"content": '{"ok":true}'}}]})
    provider.transport = httpx.MockTransport(upstream)
    assert await provider.complete("test", {}, {}) == {"ok": True}
    await provider.embed(["text"])
    assert calls[0][0:2] == ("https://models.example/v1/chat/completions", "Bearer custom-key")
    assert calls[0][2]["model"] == "custom-model" and "response_format" not in calls[0][2]
    assert calls[1][0:2] == ("https://vectors.example/v1/embeddings", "Bearer server-key")
    assert calls[1][2]["model"] == "vector-fixed"
    assert original.ai_model == "server-model" and original.ai_api_key.get_secret_value() == "server-key"


async def test_probe_works_without_models_endpoint_and_does_not_expose_keys(monkeypatch):
    calls = []
    def upstream(request):
        calls.append(request.method)
        assert request.method == "POST", "Custom probes must not require GET /models"
        return httpx.Response(200, json={"choices": [{"message": {"content": '{"ok":true}'}}]})
    monkeypatch.setattr(routes, "OpenAICompatibleProvider", lambda settings: OpenAICompatibleProvider(settings, httpx.MockTransport(upstream)))
    app.dependency_overrides[get_settings] = config
    try:
        async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://test") as client:
            body = {"baseUrl": "https://models.example/v1", "apiKey": "test-secret-key", "model": "custom-model"}
            assert (await client.post("/ai/models/connections/test", json=body)).status_code == 401
            headers = {"Authorization": "Bearer test-internal"}
            response = await client.post("/ai/models/connections/test", headers=headers, json=body)
            assert response.status_code == 200 and response.json()["model"] == "custom-model"
            assert "test-secret-key" not in response.text
            body["apiKey"] = "test-secret-key\r\n"
            response = await client.post("/ai/models/connections/test", headers=headers, json=body)
            assert response.status_code == 400 and "test-secret-key" not in response.text
        assert calls == ["POST"]
    finally:
        app.dependency_overrides.clear()


async def test_unconfigured_default_catalog_still_allows_custom_management():
    app.dependency_overrides[get_settings] = lambda: Settings(_env_file=None, ai_service_token="test-internal", ai_api_key="", ai_model="")
    try:
        async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://test") as client:
            response = await client.get("/ai/models", headers={"Authorization": "Bearer test-internal"})
            assert response.status_code == 200 and response.json()["models"] == []
    finally:
        app.dependency_overrides.clear()


async def test_direct_connection_pins_public_ip_and_preserves_tls_hostname():
    from app.providers.custom_connection import AnyIOBackend
    tls_names = []
    content = json.dumps({"choices": [{"message": {"content": '{"ok":true}'}}]}).encode()
    class Stream(httpcore.AsyncNetworkStream):
        def __init__(self):
            self.response = b"HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: " + str(len(content)).encode() + b"\r\n\r\n" + content
        async def read(self, max_bytes, timeout=None):
            result, self.response = self.response[:max_bytes], self.response[max_bytes:]
            return result
        async def write(self, buffer, timeout=None):
            pass
        async def aclose(self):
            pass
        async def start_tls(self, ssl_context, server_hostname=None, timeout=None):
            tls_names.append(server_hostname)
            return self
        def get_extra_info(self, info):
            return None
    connect = AsyncMock(return_value=Stream())
    custom = connection_settings(config(), "https://models.example/v1", "custom-key", "custom-model")
    with patch("app.providers.custom_connection.public_addresses", AsyncMock(return_value=["8.8.8.8"])), patch.object(AnyIOBackend, "connect_tcp", connect):
        assert await OpenAICompatibleProvider(custom).complete("test", {}, {}) == {"ok": True}
    assert connect.call_args.args[:2] == ("8.8.8.8", 443)
    assert tls_names == ["models.example"]
