"""Record provider-reported usage per request; never inspect prompts or infer token counts."""
from contextvars import ContextVar
from hashlib import sha256
import json
from urllib.parse import urlsplit
from uuid import uuid4
from starlette.datastructures import MutableHeaders
from app.models.model_selection import valid_model_id

_usage: ContextVar[dict | None] = ContextVar("model_usage", default=None)


def token_count(value):
    return value if isinstance(value, int) and not isinstance(value, bool) and 0 <= value <= 10**12 else None


def record_usage(base: str, model: str, kind: str, response: dict | None):
    collector = _usage.get()
    if collector is None:
        return
    returned_model = response.get("model") if response else None
    if isinstance(returned_model, str) and valid_model_id(returned_model):
        model = returned_model
    if not valid_model_id(model):
        model = "unknown-model"
    provider = sha256(base.encode()).hexdigest()
    identity = (provider, model, kind)
    if identity not in collector:
        collector[identity] = {
            "id": str(uuid4()), "providerKey": provider, "providerName": urlsplit(base).hostname or "unknown",
            "model": model, "kind": kind, "calls": 0, "reportedCalls": 0, "incompleteCalls": 0,
            "inputTokens": 0, "outputTokens": 0, "totalTokens": 0, "cachedInputTokens": 0,
            "cacheReportedCalls": 0,
        }
    event = collector[identity]
    usage = response.get("usage") if response else None
    usage = usage if isinstance(usage, dict) else {}
    input_tokens = token_count(usage.get("prompt_tokens", usage.get("input_tokens")))
    output_tokens = 0 if kind == "EMBEDDING" else token_count(usage.get("completion_tokens", usage.get("output_tokens")))
    total_tokens = token_count(usage.get("total_tokens"))
    if total_tokens is None and input_tokens is not None and output_tokens is not None:
        total_tokens = input_tokens + output_tokens
    details = usage.get("prompt_tokens_details", usage.get("input_tokens_details"))
    details = details if isinstance(details, dict) else {}
    cached = token_count(details.get("cached_tokens", usage.get("prompt_cache_hit_tokens")))
    if input_tokens is None or (cached is not None and cached > input_tokens):
        cached = None
    event["calls"] += 1
    event["reportedCalls"] += total_tokens is not None
    event["incompleteCalls"] += any(value is None for value in (input_tokens, output_tokens, total_tokens))
    event["cacheReportedCalls"] += cached is not None
    for key, value in (("inputTokens", input_tokens), ("outputTokens", output_tokens),
                       ("totalTokens", total_tokens), ("cachedInputTokens", cached)):
        if value is not None:
            event[key] += value


class UsageMiddleware:
    def __init__(self, app):
        self.app = app

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http" or not scope.get("path", "").startswith("/ai/"):
            return await self.app(scope, receive, send)
        collector = {}
        token = _usage.set(collector)

        async def send_usage(message):
            if message["type"] == "http.response.start" and collector:
                # A document can invoke embeddings in many batches. Aggregate each model
                # within this HTTP request to keep the internal header bounded.
                MutableHeaders(scope=message)["X-Study-Usage"] = json.dumps(
                    {"version": 1, "events": list(collector.values())}, separators=(",", ":"), ensure_ascii=True)
            await send(message)

        try:
            await self.app(scope, receive, send_usage)
        finally:
            _usage.reset(token)
