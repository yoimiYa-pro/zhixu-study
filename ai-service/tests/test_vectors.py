from uuid import uuid4
import pytest
from app.core.settings import Settings
from app.models.vector import Document, SearchRequest
from app.rag.vectors import VectorStore


class TestEmbeddings:
    """Only this test supplies predetermined vectors; runtime uses the external Embedding API."""
    __test__ = False

    async def embed(self, texts):
        return [[0.0, 1.0] if text == "orthogonal document" else [1.0, 0.0] for text in texts]


@pytest.mark.integration
async def test_real_qdrant_similarity_exclusion_replacement_and_delete():
    config = Settings(embedding_model="integration-" + str(uuid4()), embedding_dimension=2,
                      embedding_api_key="test-only-key")
    store = VectorStore(config, TestEmbeddings())
    first, second = uuid4(), uuid4()
    try:
        await store.index(Document(entityId=first, entityType="question", text="unrelated wording", title="first"))
        await store.index(Document(entityId=second, entityType="question", text="orthogonal document", title="second"))
        hits = await store.search(SearchRequest(query="query with no shared keywords", entityType="question", limit=2))
        assert hits[0].entityId == first and hits[0].score > 0.99
        hits = await store.search(SearchRequest(query="query", entityType="question", excludeIds=[first]))
        assert all(hit.entityId != first for hit in hits)
        await store.index(Document(entityId=first, entityType="question", text="updated", title="updated"))
        hits = await store.search(SearchRequest(query="query", entityType="question"))
        assert hits[0].title == "updated"
        await store.delete("question", str(first))
        assert all(hit.entityId != first for hit in await store.search(SearchRequest(query="query")))
    finally:
        if await store.client.collection_exists(store.collection):
            await store.client.delete_collection(store.collection)
        await store.close()
