from app.core.errors import ServiceError
from app.models.assistant import ChatAnswer, ChatRequest, EssayOutline, EssayRequest
from app.services.structured import structured


def check_citations(output, contexts):
    allowed = {(item.entityType, item.entityId) for item in contexts}
    if any((ref.entityType, ref.entityId) not in allowed for ref in output.citations):
        raise ServiceError("AI_INVALID_OUTPUT", "回答引用了未检索到的资料，已拒绝保存", 502)


async def chat(provider, input: ChatRequest):
    answer = await structured(provider, "chat.txt", input.model_dump(mode="json"), ChatAnswer)
    check_citations(answer, input.contexts)
    return answer


async def essay(provider, input: EssayRequest):
    outline = await structured(provider, "essay_material.txt", input.model_dump(mode="json"), EssayOutline)
    check_citations(outline, input.contexts)
    if any(not quote.strip() or not any(quote in item.content for item in input.contexts) for quote in outline.quotes):
        raise ServiceError("AI_INVALID_OUTPUT", "金句无法在原始资料中核对，已拒绝保存", 502)
    return outline
