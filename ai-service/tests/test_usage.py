import asyncio
import json
import httpx
import pytest
from fastapi import FastAPI
from app.api.routes import get_provider
from app.core.settings import Settings, get_settings
from app.core.usage import UsageMiddleware
from app.main import app
from app.providers.openai_compatible import OpenAICompatibleProvider


@pytest.fixture
def config():
    settings = Settings(_env_file=None, ai_service_token="test-only-internal-token", ai_api_key="test-only-secret",
                        ai_model="test-model", embedding_base_url="https://vectors.example.com/v1",
                        embedding_model="test-embedding", embedding_dimension=2)
    app.dependency_overrides[get_settings] = lambda: settings
    yield settings
    app.dependency_overrides.clear()


def completion(usage=None, content=None, model="resolved-model"):
    value = {"choices": [{"message": {"content": content or '{"answer":"学习回答","citations":[]}'}}], "model": model}
    if usage is not None:
        value["usage"] = usage
    return value


async def chat(response, config):
    provider = OpenAICompatibleProvider(config, httpx.MockTransport(lambda request: response))
    app.dependency_overrides[get_provider] = lambda: provider
    async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://test") as client:
        return await client.post("/ai/chat", headers={"Authorization": "Bearer test-only-internal-token"},
                                 json={"query": "private learning question", "contexts": [], "retrievalStatus": "DATABASE_ONLY"})


async def test_records_real_usage_and_cache_without_prompt_or_credentials(config):
    response = await chat(httpx.Response(200, json=completion({"prompt_tokens": 120, "completion_tokens": 30,
        "total_tokens": 150, "prompt_cache_hit_tokens": 80})), config)
    assert response.status_code == 200
    envelope = json.loads(response.headers["X-Study-Usage"])
    event = envelope["events"][0]
    assert envelope["version"] == 1
    assert (event["inputTokens"], event["outputTokens"], event["totalTokens"], event["cachedInputTokens"]) == (120, 30, 150, 80)
    assert event["model"] == "resolved-model"
    assert event["calls"] == event["reportedCalls"] == event["cacheReportedCalls"] == 1
    assert event["incompleteCalls"] == 0
    assert "private learning question" not in response.headers["X-Study-Usage"]
    assert "test-only-secret" not in response.headers["X-Study-Usage"]


@pytest.mark.parametrize("status,content", [(200, "invalid JSON"), (502, None)])
async def test_keeps_reported_usage_when_provider_or_json_validation_fails(config, status, content):
    response = await chat(httpx.Response(status, json=completion({"prompt_tokens": 10, "completion_tokens": 5,
                                                                "total_tokens": 15}, content)), config)
    assert response.status_code == 502
    event = json.loads(response.headers["X-Study-Usage"])["events"][0]
    assert event["totalTokens"] == 15 and event["reportedCalls"] == 1


@pytest.mark.parametrize("usage,reported,incomplete,total", [
    (None, 0, 1, 0),
    ({"prompt_tokens": True, "completion_tokens": -1, "total_tokens": "20"}, 0, 1, 0),
    ({"total_tokens": 99}, 1, 1, 99),
    ({"input_tokens": 10, "output_tokens": 5, "input_tokens_details": {"cached_tokens": 3}}, 1, 0, 15),
    ({"prompt_tokens": 0, "completion_tokens": 0, "total_tokens": 0}, 1, 0, 0),
])
async def test_distinguishes_missing_invalid_partial_and_true_zero_usage(config, usage, reported, incomplete, total):
    response = await chat(httpx.Response(200, json=completion(usage)), config)
    assert response.status_code == 200
    event = json.loads(response.headers["X-Study-Usage"])["events"][0]
    assert event["reportedCalls"] == reported
    assert event["incompleteCalls"] == incomplete
    assert event["totalTokens"] == total


async def test_aggregates_embedding_batches_and_separates_chat_model(config):
    local = FastAPI()
    local.add_middleware(UsageMiddleware)

    def provider_response(request):
        if request.url.path.endswith("/embeddings"):
            return httpx.Response(200, json={"model": "test-embedding", "data": [{"index": 0, "embedding": [1, 0]}],
                                            "usage": {"prompt_tokens": 20, "total_tokens": 20}})
        return httpx.Response(200, json=completion({"prompt_tokens": 10, "completion_tokens": 5, "total_tokens": 15}))

    provider = OpenAICompatibleProvider(config, httpx.MockTransport(provider_response))

    @local.post("/ai/batch")
    async def batch():
        await provider.embed(["first"])
        await provider.embed(["second"])
        await provider.complete("prompt", {}, {})
        return {"ok": True}

    async with httpx.AsyncClient(transport=httpx.ASGITransport(app=local), base_url="http://test") as client:
        response = await client.post("/ai/batch")
    events = json.loads(response.headers["X-Study-Usage"])["events"]
    assert len(events) == 2
    vector, chat_event = events
    assert vector["kind"] == "EMBEDDING"
    assert vector["calls"] == vector["reportedCalls"] == 2
    assert vector["inputTokens"] == vector["totalTokens"] == 40
    assert vector["outputTokens"] == 0 and vector["incompleteCalls"] == 0
    assert vector["providerName"] == "vectors.example.com"
    assert vector["providerKey"] != chat_event["providerKey"]


async def test_isolates_concurrent_requests_and_does_not_record_catalog_reads(config, monkeypatch):
    async def provider_response(request):
        if request.url.path.endswith("/models"):
            return httpx.Response(200, json={"data": [{"id": "test-model"}]})
        payload = json.loads(json.loads(request.content)["messages"][1]["content"])
        count = int(payload["query"])
        await asyncio.sleep(0.01)
        return httpx.Response(200, json=completion({"prompt_tokens": count, "completion_tokens": 5, "total_tokens": count + 5}))

    provider = OpenAICompatibleProvider(config, httpx.MockTransport(provider_response))
    app.dependency_overrides[get_provider] = lambda: provider
    monkeypatch.setattr("app.api.routes.OpenAICompatibleProvider", lambda settings: provider)
    async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://test") as client:
        responses = await asyncio.gather(*(client.post("/ai/chat", headers={"Authorization": "Bearer test-only-internal-token"},
            json={"query": str(count), "contexts": [], "retrievalStatus": "DATABASE_ONLY"}) for count in (10, 200)))
        events = [json.loads(response.headers["X-Study-Usage"])["events"][0] for response in responses]
        assert [event["totalTokens"] for event in events] == [15, 205]
        assert events[0]["id"] != events[1]["id"]
        health = await client.get("/health")
        assert "X-Study-Usage" not in health.headers
        catalog = await client.get("/ai/models", headers={"Authorization": "Bearer test-only-internal-token"})
        assert catalog.status_code == 200 and "X-Study-Usage" not in catalog.headers
