import json
import httpx
import pytest
from app.core.errors import ServiceError
from app.core.settings import Settings
from app.providers.openai_compatible import OpenAICompatibleProvider


def settings(**kwargs):
    return Settings(_env_file=None, ai_api_key="test-only-provider-key", ai_model="test-only-model", **kwargs)


async def test_provider_rejects_non_json_and_unavailable_api():
    for response in (httpx.Response(503), httpx.Response(200, json={"choices": [{"message": {"content": "unvalidated prose"}}]})):
        provider = OpenAICompatibleProvider(settings(), httpx.MockTransport(lambda request: response))
        with pytest.raises(ServiceError):
            await provider.complete("prompt", {}, {})


async def test_provider_accepts_json_object_without_leaking_key_to_body():
    def handler(request):
        assert request.headers["Authorization"] == "Bearer test-only-provider-key"
        body = json.loads(request.content)
        assert "test-only-provider-key" not in request.content.decode()
        assert body["response_format"] == {"type": "json_object"}
        return httpx.Response(200, json={"choices": [{"message": {"content": '{"validated":true}'}}]})
    provider = OpenAICompatibleProvider(settings(), httpx.MockTransport(handler))
    assert await provider.complete("prompt", {}, {}) == {"validated": True}


async def test_embedding_rejects_bad_dimensions_and_indices():
    for data in ([{"index": 0, "embedding": [1]}], [{"index": 9, "embedding": [1, 0]}]):
        provider = OpenAICompatibleProvider(settings(embedding_model="test-only-embedding", embedding_dimension=2),
                                            httpx.MockTransport(lambda request: httpx.Response(200, json={"data": data})))
        with pytest.raises(ServiceError):
            await provider.embed(["test text"])
