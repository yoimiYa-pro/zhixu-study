import json
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


async def test_markdown_steps_and_display_math_survive_question_analysis_protocol():
    config = Settings(_env_file=None, ai_service_token="test-only-internal-token", ai_api_key="test-key", ai_model="test-model")
    explanation = "增长量为20，基期量为100。\n\n$$\n\\frac{20}{100}\\times100\\%=20\\%\n$$\n\n所以选择 B。"
    output = {
        "questionType": "资料分析", "knowledgePoints": ["增长率"], "difficulty": "简单",
        "mistakeReason": "计算错误", "analysis": "先看增加了多少，再和原来的数量比。\n\n分母用原来的100。",
        "correctAnswerExplanation": explanation, "pitfalls": ["**分母不要用120**，要用原来的100。"],
        "solutionSteps": ["**先求增长量**\n\n$$\n120-100=20\n$$", "**再求增长率**\n\n" + explanation],
        "quickMethod": "增长量除以基期量。", "relatedKnowledge": ["基期量"],
        "reviewSuggestions": [{"afterDays": 1, "reason": "再练习一次分母的选择。"}],
    }

    def provider_response(request):
        body = json.loads(request.content)
        assert body["messages"][1]["role"] == "user"
        assert json.loads(body["messages"][1]["content"])["explanation"] == explanation
        assert body["response_format"] == {"type": "json_object"}
        return httpx.Response(200, json={"choices": [{"message": {"content": json.dumps(output, ensure_ascii=False)}}]})

    provider = OpenAICompatibleProvider(config, httpx.MockTransport(provider_response))
    app.dependency_overrides[get_settings] = lambda: config
    app.dependency_overrides[get_provider] = lambda: provider
    try:
        async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://test") as client:
            response = await client.post("/ai/question/analyze", headers={"Authorization": "Bearer test-only-internal-token"}, json={"question": {"content": "数据从100增长到120，增长率是多少？", "correctAnswer": "B", "questionType": "资料分析", "explanation": explanation}})
            assert response.status_code == 200
            assert response.json() == output
    finally:
        app.dependency_overrides.clear()
