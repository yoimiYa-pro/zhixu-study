import asyncio
import ipaddress
import hashlib
import hmac
import os
import socket
import time
import unittest
from unittest.mock import AsyncMock, patch

from relay import Config, Relay, open_public, resolve_public


class RelayTests(unittest.IsolatedAsyncioTestCase):
    def config(self, subnet="127.0.0.1/32"):
        return Config("127.0.0.1", ipaddress.ip_network(subnet), frozenset({"trusted.example"}))

    async def request(self, relay, request):
        async with await asyncio.start_server(relay.handle, "127.0.0.1", 0, limit=8192) as server:
            reader, writer = await asyncio.open_connection("127.0.0.1", server.sockets[0].getsockname()[1])
            writer.write(request)
            await writer.drain()
            result = await asyncio.wait_for(reader.read(), timeout=3)
            writer.close()
            await writer.wait_closed()
            return result

    async def test_rejects_sources_outside_project_subnet(self):
        result = await self.request(Relay(self.config("172.19.0.0/16")),
                                    b"CONNECT trusted.example:443 HTTP/1.1\r\n\r\n")
        self.assertIn(b"403 Forbidden", result)

    async def test_rejects_unlisted_targets_ports_credentials_and_http(self):
        connector = AsyncMock()
        relay = Relay(self.config(), connector)
        for target in ("evil.example:443", "trusted.example:80", "user@trusted.example:443"):
            result = await self.request(relay, f"CONNECT {target} HTTP/1.1\r\n\r\n".encode())
            self.assertIn(b"403 Forbidden", result)
        result = await self.request(relay, b"GET https://trusted.example/ HTTP/1.1\r\n\r\n")
        self.assertIn(b"405 Method Not Allowed", result)
        connector.assert_not_awaited()

    async def test_tunnel_transfers_opaque_bytes(self):
        opaque = b"\x16\x03\x01opaque-payload"

        async def echo(reader, writer):
            data = await reader.readexactly(len(opaque))
            writer.write(data)
            await writer.drain()
            writer.close()
            await writer.wait_closed()

        async with await asyncio.start_server(echo, "127.0.0.1", 0) as origin:
            async def connect(host):
                self.assertEqual(host, "trusted.example")
                return await asyncio.open_connection("127.0.0.1", origin.sockets[0].getsockname()[1])
            result = await self.request(Relay(self.config(), connect),
                                        b"CONNECT trusted.example:443 HTTP/1.1\r\n\r\n" + opaque)
        self.assertTrue(result.startswith(b"HTTP/1.1 200 Connection Established\r\n\r\n"))
        self.assertTrue(result.endswith(opaque))

    async def test_rejects_private_or_mixed_dns_answers(self):
        loop = asyncio.get_running_loop()
        public = (socket.AF_INET, socket.SOCK_STREAM, 6, "", ("8.8.8.8", 443))
        private = (socket.AF_INET, socket.SOCK_STREAM, 6, "", ("127.0.0.1", 443))
        for answers in ([private], [public, private], []):
            with patch.object(loop, "getaddrinfo", AsyncMock(return_value=answers)):
                with self.assertRaises(ValueError):
                    await resolve_public("trusted.example")
        with patch.object(loop, "getaddrinfo", AsyncMock(return_value=[public])):
            self.assertEqual(await resolve_public("trusted.example"), ["8.8.8.8"])

    async def test_connection_uses_verified_ip_without_second_dns_lookup(self):
        connection = AsyncMock(return_value=("reader", "writer"))
        with patch("relay.resolve_public", AsyncMock(return_value=["8.8.8.8"])), \
                patch("relay.asyncio.open_connection", connection):
            self.assertEqual(await open_public("trusted.example"), ("reader", "writer"))
        connection.assert_awaited_once_with("8.8.8.8", 443)

    async def test_health_and_connection_cap(self):
        relay = Relay(self.config())
        self.assertIn(b"200 OK", await self.request(relay, b"GET /health HTTP/1.1\r\n\r\n"))
        relay.active = 32
        self.assertIn(b"503 Service Unavailable", await self.request(
            relay, b"CONNECT trusted.example:443 HTTP/1.1\r\n\r\n"))

    def test_signed_custom_targets_are_bound_to_host_port_and_time(self):
        config = Config("127.0.0.1", ipaddress.ip_network("127.0.0.1/32"), frozenset({"trusted.example"}), "test-signing-key")
        stamp = str(int(time.time()))
        signature = hmac.new(config.auth_key.encode(), f"custom.example:8443:{stamp}".encode(), hashlib.sha256).hexdigest()
        headers = {"x-study-proxy-authorization": f"Study {stamp}:{signature}"}
        self.assertEqual(config.target("custom.example:8443", headers), "custom.example")
        for target in ("other.example:8443", "custom.example:443", "127.0.0.1:8443", "169.254.169.254:8443"):
            with self.assertRaises(ValueError):
                config.target(target, headers)
        with self.assertRaises(ValueError):
            config.target("custom.example:8443", {})
        with patch("relay.time.time", return_value=int(stamp)+61), self.assertRaises(ValueError):
            config.target("custom.example:8443", headers)

    async def test_signed_custom_target_keeps_opaque_tls_payload(self):
        config = Config("127.0.0.1", ipaddress.ip_network("127.0.0.1/32"), frozenset(), "test-signing-key")
        stamp = str(int(time.time()))
        signature = hmac.new(config.auth_key.encode(), f"custom.example:443:{stamp}".encode(), hashlib.sha256).hexdigest()
        opaque = b"opaque-tls"
        async def echo(reader, writer):
            writer.write(await reader.readexactly(len(opaque)))
            await writer.drain()
            writer.close()
            await writer.wait_closed()
        async with await asyncio.start_server(echo, "127.0.0.1", 0) as origin:
            async def connect(host):
                self.assertEqual(host, "custom.example")
                return await asyncio.open_connection("127.0.0.1", origin.sockets[0].getsockname()[1])
            request = f"CONNECT custom.example:443 HTTP/1.1\r\nX-Study-Proxy-Authorization: Study {stamp}:{signature}\r\n\r\n".encode() + opaque
            result = await self.request(Relay(config, connect), request)
        self.assertTrue(result.startswith(b"HTTP/1.1 200 Connection Established"))
        self.assertTrue(result.endswith(opaque))

    def test_configuration_rejects_public_bind_and_public_source_ranges(self):
        for bind, subnet in (("8.8.8.8", "8.8.8.0/24"), ("172.19.0.1", "0.0.0.0/0")):
            with patch.dict(os.environ, {"EGRESS_BIND_HOST": bind, "EGRESS_ALLOWED_CIDR": subnet}, clear=True):
                with self.assertRaises(ValueError):
                    Config.from_env()


if __name__ == "__main__":
    unittest.main()
