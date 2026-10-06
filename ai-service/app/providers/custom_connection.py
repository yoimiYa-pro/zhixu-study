"""Per-request model credentials and public, DNS-pinned HTTPS connections."""
import asyncio
import hashlib
import hmac
import ipaddress
import re
import socket
import ssl
import time
from urllib.parse import urlsplit
import httpcore
import httpx
from httpcore._backends.anyio import AnyIOBackend
from pydantic import SecretStr
from app.core.errors import ServiceError
from app.models.model_selection import valid_model_id


def public_base_url(value: str) -> str:
    base = value.strip().rstrip("/")
    try:
        parts = urlsplit(base)
        host = parts.hostname or ""
        if (parts.scheme != "https" or not host or parts.username is not None or parts.password is not None
                or parts.query or parts.fragment or "?" in base or "#" in base
                or parts.port == 0 or base.endswith("/chat/completions")
                or any(ord(char) < 33 or ord(char) > 126 for char in base)):
            raise ValueError()
        try:
            ip = ipaddress.ip_address(host)
        except ValueError:
            if not re.fullmatch(r"[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?", host) or "." not in host:
                raise ValueError()
        else:
            if not ip.is_global:
                raise ValueError()
    except ValueError:
        raise ValueError("Use a public HTTPS API base URL without credentials or query parameters") from None
    return base


def connection_settings(settings, base, key, model, json_mode=True):
    try:
        base = public_base_url(base)
    except ValueError:
        raise ServiceError("AI_URL_REJECTED", "请使用 HTTPS 公网模型接口基址", 400) from None
    if not valid_model_id(model) or not re.fullmatch(r"[\x21-\x7E]{1,4096}", key):
        raise ServiceError("AI_MODEL_INVALID", "模型名称或密钥格式无效", 400)
    return settings.model_copy(update={
        "ai_base_url": base, "ai_api_key": SecretStr(key), "ai_model": model,
        "ai_json_mode": json_mode, "ai_custom_connection": True,
        # A chat-provider change must not send its key to the existing vector provider.
        "embedding_api_key": SecretStr(settings.embedding_api_key.get_secret_value() or settings.ai_api_key.get_secret_value()),
    })


async def public_addresses(host, port):
    try:
        addresses = await asyncio.wait_for(asyncio.get_running_loop().getaddrinfo(host, port, type=socket.SOCK_STREAM), timeout=5)
    except (OSError, asyncio.TimeoutError):
        raise ServiceError("PROVIDER_UNAVAILABLE", "无法解析模型服务地址", 502) from None
    ips = list(dict.fromkeys(address[4][0] for address in addresses))
    if not ips or any(not ipaddress.ip_address(ip).is_global for ip in ips):
        raise ServiceError("AI_URL_REJECTED", "模型地址必须解析到公网", 400)
    return ips


class PublicNetworkBackend(AnyIOBackend):
    async def connect_tcp(self, host, port, timeout=None, local_address=None, socket_options=None):
        ips = await public_addresses(host, port)
        for ip in ips:
            try:
                return await super().connect_tcp(ip, port, timeout, local_address, socket_options)
            except (httpcore.ConnectError, httpcore.ConnectTimeout):
                continue
        raise httpcore.ConnectError("Model endpoint unavailable")


class PublicHTTPTransport(httpx.AsyncHTTPTransport):
    def __init__(self):
        # httpx 0.28's inherited request adapter keeps the original TLS hostname;
        # only the TCP destination is replaced by a verified public address.
        self._pool = httpcore.AsyncConnectionPool(ssl_context=ssl.create_default_context(), network_backend=PublicNetworkBackend())


def relay_headers(base, token):
    if not token:
        return {}
    parts = urlsplit(base)
    stamp = str(int(time.time()))
    authority = f"{parts.hostname}:{parts.port or 443}"
    signature = hmac.new(token.encode(), f"{authority}:{stamp}".encode(), hashlib.sha256).hexdigest()
    return {"X-Study-Proxy-Authorization": f"Study {stamp}:{signature}"}
