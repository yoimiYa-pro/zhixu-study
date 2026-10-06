import httpx
from app.main import app
from app.core.settings import Settings, get_settings
from app.api.routes import get_provider
from app.providers.openai_compatible import OpenAICompatibleProvider


async def test_requires_internal_token_and_missing_model_is_explicit():
    config = Settings(_env_file=None, ai_service_token="test-only-internal-token", ai_api_key="", ai_model="")
    app.dependency_overrides[get_settings] = lambda: config
    try:
        async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://test") as client:
            assert (await client.post("/ai/question/analyze", json={})).status_code == 401
            response = await client.post("/ai/question/analyze", headers={"Authorization": "Bearer test-only-internal-token"}, json={"question": {"content": "测试题", "correctAnswer": "A", "questionType": "常识判断"}})
            assert response.status_code == 503
            assert response.json()["code"] == "LLM_NOT_CONFIGURED"
    finally:
        app.dependency_overrides.clear()


async def test_unvalidated_model_output_is_rejected_at_api_boundary():
    config = Settings(_env_file=None, ai_service_token="test-only-internal-token", ai_api_key="test-key", ai_model="test-model")
    provider = OpenAICompatibleProvider(config, httpx.MockTransport(lambda request: httpx.Response(200, json={"choices": [{"message": {"content": '{"difficulty":"invalid"}'}}]})))
    app.dependency_overrides[get_settings] = lambda: config
    app.dependency_overrides[get_provider] = lambda: provider
    try:
        async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://test") as client:
            response = await client.post("/ai/question/analyze", headers={"Authorization": "Bearer test-only-internal-token"}, json={"question": {"content": "测试题", "correctAnswer": "A", "questionType": "常识判断"}})
            assert response.status_code == 502
            assert response.json()["code"] == "AI_INVALID_OUTPUT"
    finally:
        app.dependency_overrides.clear()
