import hashlib
import logging
from uuid import NAMESPACE_URL, uuid5
from qdrant_client import AsyncQdrantClient, models
from app.core.errors import ServiceError
from app.models.vector import Document, SearchRequest, SearchHit

OWNER_ID = "00000000-0000-0000-0000-000000000001"
log = logging.getLogger(__name__)


def chunks(text: str, size: int = 1800) -> list[str]:
    return [text[start:start + size] for start in range(0, len(text), size - 100)]


class VectorStore:
    def __init__(self, settings, provider, client=None):
        self.settings, self.provider = settings, provider
        self.client = client or AsyncQdrantClient(url=settings.qdrant_url,
                    api_key=settings.qdrant_api_key.get_secret_value() or None, timeout=15)
        fingerprint = hashlib.sha256((settings.embedding_base_url + "|" + settings.embedding_model).encode()).hexdigest()[:12]
        self.collection = f"civil_study_{settings.embedding_dimension}_{fingerprint}"

    async def close(self):
        await self.client.close()

    def require_embedding(self):
        if not self.settings.embedding_model or not (self.settings.embedding_api_key.get_secret_value() or self.settings.ai_api_key.get_secret_value()):
            raise ServiceError("EMBEDDING_NOT_CONFIGURED", "请配置 Embedding 模型和 API Key")

    async def ensure_collection(self):
        if not await self.client.collection_exists(self.collection):
            try:
                await self.client.create_collection(self.collection, models.VectorParams(
                    size=self.settings.embedding_dimension, distance=models.Distance.COSINE))
            except Exception:
                if not await self.client.collection_exists(self.collection):
                    raise
        info = await self.client.get_collection(self.collection)
        if info.config.params.vectors.size != self.settings.embedding_dimension:
            raise ServiceError("EMBEDDING_INVALID_OUTPUT", "索引维度与配置不一致")

    def doc_filter(self, kind: str, entity_id: str):
        kinds = ["question", "question_analysis"] if kind == "question" else [kind]
        return models.Filter(must=[
            models.FieldCondition(key="ownerId", match=models.MatchValue(value=OWNER_ID)),
            models.FieldCondition(key="entityType", match=models.MatchAny(any=kinds)),
            models.FieldCondition(key="entityId", match=models.MatchValue(value=entity_id)),
        ])

    async def index(self, document: Document):
        self.require_embedding()
        pieces = chunks(document.text, self.settings.embedding_chunk_chars)
        vectors = []
        batch = max(1, min(16, 200000 // self.settings.embedding_dimension))
        for start in range(0, len(pieces), batch):
            vectors.extend(await self.provider.embed(pieces[start:start + batch]))
        try:
            await self.ensure_collection()
            # Replace only this document type; deleting a question also removes its analysis below.
            filter_ = models.Filter(must=[models.FieldCondition(key="ownerId", match=models.MatchValue(value=OWNER_ID)),
                      models.FieldCondition(key="entityType", match=models.MatchValue(value=document.entityType)),
                      models.FieldCondition(key="entityId", match=models.MatchValue(value=str(document.entityId)))])
            await self.client.delete(self.collection, models.FilterSelector(filter=filter_), wait=True)
            points = []
            for i, (text, vector) in enumerate(zip(pieces, vectors, strict=True)):
                points.append(models.PointStruct(id=str(uuid5(NAMESPACE_URL, f"{OWNER_ID}/{document.entityType}/{document.entityId}/{i}")),
                    vector=vector, payload={"ownerId": OWNER_ID, "entityId": str(document.entityId),
                    "entityType": document.entityType, "title": document.title, "text": text,
                    "source": document.source, "knowledgePoints": document.knowledgePoints}))
            await self.client.upsert(self.collection, points, wait=True)
            return {"indexed": True, "chunks": len(points)}
        except ServiceError:
            raise
        except Exception as error:
            log.error("vector_write_failed type=%s", type(error).__name__)
            raise ServiceError("QDRANT_UNAVAILABLE", "向量索引暂时不可用") from None

    async def search(self, input: SearchRequest) -> list[SearchHit]:
        self.require_embedding()
        try:
            if not await self.client.collection_exists(self.collection):
                return []
            vectors = await self.provider.embed(chunks(input.query, self.settings.embedding_chunk_chars))
            query = [sum(column) / len(vectors) for column in zip(*vectors, strict=True)]
            if not any(query):
                query = vectors[0]
            conditions = [models.FieldCondition(key="ownerId", match=models.MatchValue(value=OWNER_ID))]
            if input.entityType:
                conditions.append(models.FieldCondition(key="entityType", match=models.MatchValue(value=input.entityType)))
            excluded = [models.FieldCondition(key="entityId", match=models.MatchAny(any=[str(id) for id in input.excludeIds]))] if input.excludeIds else []
            response = await self.client.query_points(self.collection, query=query,
                query_filter=models.Filter(must=conditions, must_not=excluded), limit=min(100, input.limit * 5), with_payload=True)
            hits, seen = [], set()
            for point in response.points:
                data = point.payload
                identity = (data["entityType"], data["entityId"])
                if identity in seen:
                    continue
                seen.add(identity)
                hits.append(SearchHit(entityId=data["entityId"], entityType=data["entityType"], score=point.score,
                    title=data["title"], text=data["text"], source=data["source"], knowledgePoints=data["knowledgePoints"]))
                if len(hits) == input.limit:
                    break
            return hits
        except ServiceError:
            raise
        except Exception as error:
            log.error("vector_search_failed type=%s", type(error).__name__)
            raise ServiceError("QDRANT_UNAVAILABLE", "相似内容检索暂时不可用") from None

    async def delete(self, kind: str, entity_id: str):
        try:
            if await self.client.collection_exists(self.collection):
                await self.client.delete(self.collection, models.FilterSelector(filter=self.doc_filter(kind, entity_id)), wait=True)
            return {"deleted": True}
        except Exception:
            raise ServiceError("QDRANT_UNAVAILABLE", "向量索引暂时不可用") from None
