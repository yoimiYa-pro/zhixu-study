import json
import httpx
import pytest
from app.api.routes import get_provider
import app.api.routes as routes
from app.core.errors import ServiceError
from app.core.settings import Settings, get_settings
from app.main import app
from app.models.model_selection import provider_id
from app.providers.openai_compatible import OpenAICompatibleProvider


def configuration():
    return Settings(_env_file=None, ai_service_token="test-internal", ai_api_key="test-private-key",
                    ai_model="chat-first", embedding_model="embedding-fixed", embedding_dimension=2)


async def test_models_are_read_from_provider_without_exposing_credentials():
    def upstream(request):
        assert request.method == "GET" and request.url.path == "/v1/models"
        assert request.headers["Authorization"] == "Bearer test-private-key"
        return httpx.Response(200, json={"data": [{"id": "chat-second"}, {"id": "chat-first"},
                                                 {"id": "chat-second"}, {"id": "invalid\r\nheader"}]})
    provider = OpenAICompatibleProvider(configuration(), httpx.MockTransport(upstream))
    assert await provider.available_models() == ["chat-first", "chat-second"]


async def test_request_override_preserves_environment_and_embedding_configuration():
    config = configuration()
    provider = get_provider(config, "chat-second", provider_id(config))
    bodies = []
    def upstream(request):
        body = json.loads(request.content)
        bodies.append(body)
        if request.url.path.endswith("/embeddings"):
            return httpx.Response(200, json={"data": [{"index": 0, "embedding": [1.0, 0.0]}]})
        return httpx.Response(200, json={"choices": [{"message": {"content": '{"ok":true}'}}]})
    provider.transport = httpx.MockTransport(upstream)
    assert await provider.complete("JSON", {}, {}) == {"ok": True}
    await provider.embed(["text"])
    assert bodies[0]["model"] == "chat-second"
    assert bodies[1]["model"] == "embedding-fixed"
    assert config.ai_model == "chat-first"
    assert get_provider(config, "chat-second", "other-provider").settings.ai_model == "chat-first"
    with pytest.raises(ServiceError, match="AI_MODEL_INVALID"):
        get_provider(config, "bad\r\nheader", provider_id(config))


async def test_internal_status_reports_selected_model_and_requires_authentication():
    config = configuration()
    app.dependency_overrides[get_settings] = lambda: config
    try:
        async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://test") as client:
            assert (await client.get("/ai/models")).status_code == 401
            assert (await client.post("/ai/models/test", json={"model": "chat-first"})).status_code == 401
            response = await client.get("/ai/status", headers={"Authorization": "Bearer test-internal",
                "X-Study-Model": "chat-second", "X-Study-Provider": provider_id(config)})
            assert response.status_code == 200
            assert response.json()["activeModel"] == "chat-second"
            assert "test-private-key" not in response.text
    finally:
        app.dependency_overrides.clear()


async def test_probe_checks_actual_candidate_and_rejects_invalid_output(monkeypatch):
    config = configuration()
    response_content = '{"ok":true}'
    calls = []

    def upstream(request):
        if request.method == "GET":
            return httpx.Response(200, json={"data": [{"id": "chat-first"}, {"id": "chat-second"}]})
        body = json.loads(request.content)
        calls.append(body["model"])
        return httpx.Response(200, json={"choices": [{"message": {"content": response_content}}]})

    monkeypatch.setattr(routes, "OpenAICompatibleProvider", lambda settings: OpenAICompatibleProvider(settings, httpx.MockTransport(upstream)))
    app.dependency_overrides[get_settings] = lambda: config
    try:
        async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://test",
                                    headers={"Authorization": "Bearer test-internal"}) as client:
            response = await client.post("/ai/models/test", json={"model": "chat-second"})
            assert response.status_code == 200
            assert response.json() == {"available": True, "providerId": provider_id(config), "model": "chat-second"}
            assert calls == ["chat-second"]
            assert config.ai_model == "chat-first"
            assert (await client.post("/ai/models/test", json={"model": "unknown"})).status_code == 400
            response_content = '{"ok":false}'
            response = await client.post("/ai/models/test", json={"model": "chat-second"})
            assert response.status_code == 502 and response.json()["code"] == "AI_MODEL_UNAVAILABLE"
    finally:
        app.dependency_overrides.clear()
