from uuid import uuid4
import pytest
from app.core.errors import ServiceError
from app.models.assistant import Citation, ChatAnswer, ChatRequest, Context, EssayRequest
from app.services.assistant import chat, check_citations, essay


def test_fabricated_citation_is_rejected():
    context = Context(entityType="question", entityId=uuid4(), title="stored", content="actual text")
    output = ChatAnswer(answer="explanation", citations=[Citation(entityType="question", entityId=context.entityId)])
    check_citations(output, [context])
    output.citations[0].entityId = uuid4()
    with pytest.raises(ServiceError, match="AI_INVALID_OUTPUT"):
        check_citations(output, [context])


async def test_essay_quote_must_exist_in_original_material():
    context = Context(entityType="essay", entityId=uuid4(), title="actual", content="服务群众，落在实处。")
    class TestProvider:
        async def complete(self, *args):
            return {"background": "背景", "problems": [], "causes": [], "solutions": [], "cases": [],
                    "quotes": ["从未出现在资料里的伪造金句"], "citations": []}
    request = EssayRequest(topic="基层治理", contexts=[context], retrievalStatus="DATABASE_ONLY")
    with pytest.raises(ServiceError, match="AI_INVALID_OUTPUT"):
        await essay(TestProvider(), request)


async def test_chat_forwards_long_recent_history_without_losing_markdown_context():
    long_answer = '## 方法解释\n\n' + '增长率的基期口径需要保持一致。' * 400
    history = [{"role": "user", "content": "同比增速怎么计算？"}, {"role": "assistant", "content": long_answer}]
    class TestProvider:
        async def complete(self, prompt, payload, schema):
            assert payload["history"] == history
            assert len(payload["history"][1]["content"]) > 3000
            assert "当前聊天" in prompt
            return {"answer": "继续解释同一聊天的基期口径。", "citations": []}
    request = ChatRequest(query="继续解释基期", contexts=[], history=history, retrievalStatus="DATABASE_ONLY")
    answer = await chat(TestProvider(), request)
    assert answer.answer == "继续解释同一聊天的基期口径。"
