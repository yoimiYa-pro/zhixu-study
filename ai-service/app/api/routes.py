from fastapi import APIRouter, Depends, Header
from typing import Annotated
from app.core.settings import Settings, get_settings
from app.core.security import require_token
from app.models.question import AnalyzeRequest, QuestionAnalysis
from app.providers.openai_compatible import OpenAICompatibleProvider
from app.services.structured import structured
from app.models.vector import Document, DeleteDocument, SearchRequest, SearchHit
from app.rag.vectors import VectorStore
from app.models.news import FeedBatch, NewsAnalyzeRequest, NewsAnalysis, NewsSource
from app.services.news import NewsFetcher
from app.models.assistant import ChatRequest, ChatAnswer, EssayRequest, EssayOutline
from app.services.assistant import chat as answer_chat, essay as essay_outline
from app.models.weekly import WeeklyRequest, WeeklySummary
from app.core.errors import ServiceError
from app.models.model_selection import ConnectionProbe, ModelCatalog, ModelProbe, ProbeResult, provider_id, valid_model_id
from app.providers.custom_connection import connection_settings

router = APIRouter(prefix="/ai", dependencies=[Depends(require_token)])


def get_provider(settings: Settings = Depends(get_settings),
                 x_study_model: Annotated[str | None, Header()] = None,
                 x_study_provider: Annotated[str | None, Header()] = None,
                 x_study_base_url: Annotated[str | None, Header()] = None,
                 x_study_api_key: Annotated[str | None, Header()] = None,
                 x_study_json_mode: Annotated[str | None, Header()] = None):
    if x_study_base_url is not None or x_study_api_key is not None:
        if not x_study_base_url or not x_study_api_key or not x_study_model or x_study_json_mode not in (None, "true", "false"):
            raise ServiceError("AI_MODEL_INVALID", "自定义模型配置不完整", 400)
        settings = connection_settings(settings, x_study_base_url, x_study_api_key, x_study_model, x_study_json_mode != "false")
        if x_study_provider != provider_id(settings):
            raise ServiceError("AI_MODEL_INVALID", "自定义模型配置不一致", 400)
    if x_study_model and x_study_provider == provider_id(settings):
        if not valid_model_id(x_study_model):
            raise ServiceError("AI_MODEL_INVALID", "模型名称无效", 400)
        settings = settings.model_copy(update={"ai_model": x_study_model})
    return OpenAICompatibleProvider(settings)


@router.get("/status")
def status(provider=Depends(get_provider)):
    settings = provider.settings
    return {"llmConfigured": bool(settings.ai_api_key.get_secret_value() and settings.ai_model),
            "embeddingConfigured": bool((settings.embedding_api_key.get_secret_value() or settings.ai_api_key.get_secret_value()) and settings.embedding_model),
            "activeModel": settings.ai_model}


@router.get("/models", response_model=ModelCatalog)
async def models(settings: Settings = Depends(get_settings)):
    try:
        ids = await OpenAICompatibleProvider(settings.model_copy(update={"ai_timeout_seconds": min(10, settings.ai_timeout_seconds)})).available_models()
        return ModelCatalog(providerId=provider_id(settings), defaultModel=settings.ai_model, models=ids)
    except ServiceError:
        return ModelCatalog(providerId=provider_id(settings), defaultModel=settings.ai_model, models=[],
                            message="服务器模型列表暂不可用，可管理自定义模型")


async def verify_connection(provider):
    result = await provider.complete('连接测试。请仅返回 JSON 对象 {"ok":true}。', {}, ProbeResult.model_json_schema())
    try:
        valid = ProbeResult.model_validate(result).ok
    except ValueError:
        valid = False
    if not valid:
        raise ServiceError("AI_MODEL_UNAVAILABLE", "模型未通过连接和 JSON 输出测试", 502)


@router.post("/models/connections/test")
async def test_connection(input: ConnectionProbe, settings: Settings = Depends(get_settings)):
    selected = connection_settings(settings, input.baseUrl, input.apiKey.get_secret_value(), input.model, input.jsonMode)
    await verify_connection(OpenAICompatibleProvider(selected))
    return {"available": True, "providerId": provider_id(selected), "model": input.model}


@router.post("/models/test")
async def test_model(input: ModelProbe, settings: Settings = Depends(get_settings)):
    provider = OpenAICompatibleProvider(settings)
    if input.model not in await provider.available_models() and input.model != settings.ai_model:
        raise ServiceError("AI_MODEL_INVALID", "模型不在当前服务的可用列表中", 400)
    provider = OpenAICompatibleProvider(settings.model_copy(update={"ai_model": input.model}))
    await verify_connection(provider)
    return {"available": True, "providerId": provider_id(settings), "model": input.model}


@router.post("/question/analyze", response_model=QuestionAnalysis)
async def analyze(input: AnalyzeRequest, provider=Depends(get_provider)):
    return await structured(provider, "question_analysis.txt", input.question.model_dump(), QuestionAnalysis)


async def get_vectors(settings: Settings = Depends(get_settings), provider=Depends(get_provider)):
    store = VectorStore(settings, provider)
    try:
        yield store
    finally:
        await store.close()


@router.post("/question/embed")
async def embed(input: Document, store=Depends(get_vectors)):
    return await store.index(input)


@router.post("/search/similar", response_model=list[SearchHit])
async def search(input: SearchRequest, store=Depends(get_vectors)):
    return await store.search(input)


@router.post("/documents/delete")
async def delete_document(input: DeleteDocument, store=Depends(get_vectors)):
    return await store.delete(input.entityType, str(input.entityId))


@router.post("/news/fetch", response_model=FeedBatch)
async def fetch_news(settings: Settings = Depends(get_settings)):
    return await NewsFetcher(settings).fetch()


@router.get("/news/sources", response_model=list[NewsSource])
def news_sources(settings: Settings = Depends(get_settings)):
    return NewsFetcher(settings).sources()


@router.post("/current-affairs/analyze", response_model=NewsAnalysis)
async def analyze_news(input: NewsAnalyzeRequest, provider=Depends(get_provider)):
    return await structured(provider, "current_affairs.txt", input.article.model_dump(mode="json"), NewsAnalysis)


@router.post("/chat", response_model=ChatAnswer)
async def chat(input: ChatRequest, provider=Depends(get_provider)):
    return await answer_chat(provider, input)


@router.post("/essay-assistant", response_model=EssayOutline)
async def essay(input: EssayRequest, provider=Depends(get_provider)):
    return await essay_outline(provider, input)


@router.post("/weekly-report", response_model=WeeklySummary)
async def weekly_report(input: WeeklyRequest, provider=Depends(get_provider)):
    result = await structured(provider, "weekly_report.txt", input.stats, WeeklySummary)
    allowed = {str(item.get("id")) for item in input.stats.get("currentAffairs", [])}
    if any(str(item.newsId) not in allowed for item in result.currentAffairs):
        raise ServiceError("AI_INVALID_OUTPUT", "周报引用了本周资料之外的时政", 502)
    return result
