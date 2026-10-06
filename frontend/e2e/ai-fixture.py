"""Local model boundary for isolated E2E runs. Never used by application images."""
import json
import hashlib
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

calls = []
lock = threading.Lock()


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def respond(self, body, status=200):
        data = json.dumps(body, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        try:
            self.wfile.write(data)
        except (BrokenPipeError, ConnectionResetError):
            pass

    def do_GET(self):
        if self.path == "/ai/models":
            self.respond({"providerId": "a" * 64, "defaultModel": "chat-test", "models": ["chat-test"]})
        elif self.path == "/ai/status":
            self.respond({"llmConfigured": True, "embeddingConfigured": True})
        elif self.path == "/_test/chat-calls":
            with lock:
                self.respond(list(calls))
        else:
            self.respond({"code": "TEST_ENDPOINT_UNKNOWN"}, 404)

    def do_POST(self):
        if "chunked" in self.headers.get("Transfer-Encoding", "").lower():
            chunks = []
            while True:
                length = int(self.rfile.readline().split(b";", 1)[0], 16)
                if not length:
                    while self.rfile.readline() not in (b"\r\n", b"\n", b""):
                        pass
                    break
                chunks.append(self.rfile.read(length))
                self.rfile.read(2)
            raw = b"".join(chunks)
        else:
            raw = self.rfile.read(int(self.headers.get("Content-Length", "0")))
        data = json.loads(raw)
        if self.path == "/ai/search/similar":
            self.respond([])
        elif self.path == "/ai/chat":
            # No authorization headers or server configuration are stored.
            with lock:
                calls.append({"query": data["query"], "history": data.get("history", []),
                              "model": self.headers.get("X-Study-Model"), "baseUrl": self.headers.get("X-Study-Base-Url"),
                              "customCredentialPresent": bool(self.headers.get("X-Study-Api-Key"))})
            if data["query"].startswith("慢速验收"):
                time.sleep(4)
            if data["query"].startswith("查询当前聊天上下文"):
                history = [item["content"] for item in data.get("history", []) if item["role"] == "user"]
                answer = "## 当前聊天上下文\n\n" + ("\n\n".join(history) if history else "新聊天没有历史上下文。")
            else:
                answer = "## 学习回答\n\n**已记录这次提问**：" + data["query"]
            self.respond({"answer": answer, "citations": []})
        elif self.path == "/ai/models/test":
            self.respond({"available": True, "providerId": "a" * 64, "model": data["model"]})
        elif self.path == "/ai/models/connections/test":
            if data["apiKey"] == "invalid-e2e-key":
                self.respond({"code": "PROVIDER_UNAVAILABLE"}, 502)
            else:
                self.respond({"available": True, "providerId": hashlib.sha256(data["baseUrl"].encode()).hexdigest(), "model": data["model"]})
        else:
            self.respond({"code": "TEST_ENDPOINT_UNKNOWN"}, 404)


server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
port_file = Path(sys.argv[1])
port_file.write_text(str(server.server_address[1]))
port_file.chmod(0o600)
server.serve_forever()
