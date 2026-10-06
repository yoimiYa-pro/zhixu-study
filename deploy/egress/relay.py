"""Private HTTPS CONNECT relay. TLS and credentials remain between client and origin."""
import asyncio
from dataclasses import dataclass, field
import hashlib
import hmac
import ipaddress
import os
import re
import socket
import time
from urllib.parse import urlsplit

PORT = 17890
HEADER_LIMIT = 8192
DOMAIN = re.compile(r"[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?\Z")


def domain(value):
    host = value.strip().lower()
    if not DOMAIN.fullmatch(host) or "." not in host:
        raise ValueError("Invalid allowed domain")
    try:
        ipaddress.ip_address(host)
    except ValueError:
        return host
    raise ValueError("Literal IP targets are not allowed")


@dataclass(frozen=True)
class Config:
    bind: str
    subnet: object
    hosts: frozenset
    auth_key: str = field(default="", repr=False)

    @classmethod
    def from_env(cls):
        bind = ipaddress.ip_address(os.environ.get("EGRESS_BIND_HOST", "127.0.0.1"))
        subnet = ipaddress.ip_network(os.environ.get("EGRESS_ALLOWED_CIDR", "127.0.0.1/32"))
        if (not bind.is_private or bind.is_unspecified or bind.is_multicast or bind not in subnet
                or not subnet.is_private):
            raise ValueError("Relay must bind within the configured private source subnet")
        hosts = set()
        for name in ("AI_BASE_URL", "EMBEDDING_BASE_URL"):
            parts = urlsplit(os.environ.get(name, "https://api.openai.com/v1"))
            if (parts.scheme != "https" or not parts.hostname or parts.port not in (None, 443)
                    or parts.username or parts.password or parts.query or parts.fragment):
                raise ValueError("Relay provider URLs must use HTTPS port 443")
            hosts.add(domain(parts.hostname))
        hosts.update(domain(value) for value in os.environ.get("NEWS_ALLOWED_HOSTS", "").split(",") if value.strip())
        key = os.environ.get("AI_SERVICE_TOKEN", "")
        if key and len(key) < 32:
            raise ValueError("Relay signing key is too short")
        return cls(str(bind), subnet, frozenset(hosts), key)

    def allows_peer(self, peer):
        return bool(peer and ipaddress.ip_address(peer[0]) in self.subnet)

    def target(self, authority, headers=None):
        parts = urlsplit("//" + authority)
        host = (parts.hostname or "").lower()
        if (parts.username or parts.password or parts.path or parts.query or parts.fragment
                or not parts.port or not host):
            raise ValueError("Target rejected")
        if parts.port == 443 and host in self.hosts:
            return host
        try:
            ip = ipaddress.ip_address(host)
        except ValueError:
            domain(host)
        else:
            if not ip.is_global:
                raise ValueError("Non-public target")
        signed = (headers or {}).get("x-study-proxy-authorization", "")
        match = re.fullmatch(r"Study ([0-9]{10}):([a-f0-9]{64})", signed)
        if not self.auth_key or not match or abs(time.time() - int(match[1])) > 60:
            raise ValueError("Target authorization required")
        expected = hmac.new(self.auth_key.encode(), f"{host}:{parts.port}:{match[1]}".encode(), hashlib.sha256).hexdigest()
        if not hmac.compare_digest(expected, match[2]):
            raise ValueError("Invalid target signature")
        return host


async def resolve_public(host, port=443):
    addresses = await asyncio.get_running_loop().getaddrinfo(host, port, type=socket.SOCK_STREAM)
    ips = list(dict.fromkeys(address[4][0] for address in addresses))
    if not ips or any(not ipaddress.ip_address(ip).is_global for ip in ips):
        raise ValueError("Non-public DNS target rejected")
    return ips


async def open_public(host, port=443):
    # Pin the verified addresses, so a second DNS lookup cannot switch to a private IP.
    ips = await resolve_public(host) if port == 443 else await resolve_public(host, port)
    for ip in ips:
        try:
            return await asyncio.open_connection(ip, port)
        except OSError:
            continue
    raise OSError("Origin unavailable")


async def copy_bytes(reader, writer):
    while chunk := await asyncio.wait_for(reader.read(65536), timeout=120):
        writer.write(chunk)
        await writer.drain()


async def response(writer, status):
    writer.write(f"HTTP/1.1 {status}\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".encode("ascii"))
    await writer.drain()


async def reject(reader, writer, status):
    # Consume a bounded request header before closing, so ordinary clients receive the status.
    try:
        await asyncio.wait_for(reader.readuntil(b"\r\n\r\n"), timeout=1)
    except (asyncio.LimitOverrunError, asyncio.IncompleteReadError, OSError, asyncio.TimeoutError):
        pass
    await response(writer, status)


class Relay:
    def __init__(self, config, connector=open_public):
        self.config, self.connector = config, connector
        self.active = 0

    async def handle(self, reader, writer):
        upstream = None
        connected = False
        admitted = False
        try:
            if not self.config.allows_peer(writer.get_extra_info("peername")):
                await reject(reader, writer, "403 Forbidden")
                return
            if self.active >= 32:
                await reject(reader, writer, "503 Service Unavailable")
                return
            self.active += 1
            admitted = True
            header = await asyncio.wait_for(reader.readuntil(b"\r\n\r\n"), timeout=10)
            if len(header) > HEADER_LIMIT:
                raise ValueError("Header too large")
            method, authority, version = header.split(b"\r\n", 1)[0].decode("ascii").split(" ")
            if version not in ("HTTP/1.0", "HTTP/1.1"):
                raise ValueError("Unsupported request version")
            if method == "GET" and authority == "/health":
                await response(writer, "200 OK")
                return
            if method != "CONNECT":
                await response(writer, "405 Method Not Allowed")
                return
            try:
                headers = {}
                for line in header.split(b"\r\n")[1:-2]:
                    name, value = line.decode("ascii").split(":", 1)
                    name = name.lower().strip()
                    if name in headers:
                        raise ValueError("Duplicate header")
                    headers[name] = value.strip()
                host = self.config.target(authority, headers)
                port = urlsplit("//" + authority).port
                connection = self.connector(host) if port == 443 else self.connector(host, port)
                origin_reader, upstream = await asyncio.wait_for(connection, timeout=8)
            except ValueError:
                await response(writer, "403 Forbidden")
                return
            except (OSError, asyncio.TimeoutError):
                await response(writer, "502 Bad Gateway")
                return
            writer.write(b"HTTP/1.1 200 Connection Established\r\n\r\n")
            await writer.drain()
            connected = True
            tasks = [asyncio.create_task(copy_bytes(reader, upstream)),
                     asyncio.create_task(copy_bytes(origin_reader, writer))]
            try:
                await asyncio.wait(tasks, return_when=asyncio.FIRST_COMPLETED)
            finally:
                for task in tasks:
                    task.cancel()
                await asyncio.gather(*tasks, return_exceptions=True)
        except (ValueError, UnicodeError, asyncio.LimitOverrunError, asyncio.IncompleteReadError,
                OSError, asyncio.TimeoutError):
            if not connected:
                try:
                    await response(writer, "400 Bad Request")
                except (OSError, asyncio.TimeoutError):
                    pass
        finally:
            if admitted:
                self.active -= 1
            for connection in (upstream, writer):
                if connection is not None:
                    connection.close()
                    try:
                        await asyncio.wait_for(connection.wait_closed(), timeout=2)
                    except (OSError, asyncio.TimeoutError):
                        pass


async def main():
    config = Config.from_env()
    relay = Relay(config)
    server = await asyncio.start_server(relay.handle, config.bind, PORT, limit=HEADER_LIMIT)
    print("Private HTTPS relay ready", flush=True)
    async with server:
        await server.serve_forever()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except (ValueError, OSError):
        raise SystemExit("Private relay configuration or bind failed") from None
