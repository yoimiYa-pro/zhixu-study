"""Real persistence checks; temporary probe records are removed after verification."""
import json
import os
from pathlib import Path
import shlex
import socket
import subprocess
import time
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]
config = {}
for line in (ROOT / ".env").read_text().splitlines():
    if "=" in line and not line.startswith("#"):
        key, value = line.split("=", 1)
        parsed = shlex.split(value)
        config[key] = parsed[0] if parsed else ""
docker = ["docker"] if os.geteuid() == 0 else ["sudo", "-n", "docker"]
compose = docker + ["compose"]
probe = uuid.uuid4().hex[:12]


def sql(statement):
    command = compose + ["exec", "-T", "postgres", "psql", "-U", config["POSTGRES_USER"],
                         "-d", config["POSTGRES_DB"], "-v", "ON_ERROR_STOP=1", "-qtA", "-c", statement]
    return subprocess.check_output(command, cwd=ROOT, stderr=subprocess.DEVNULL, text=True).strip()


def redis(*parts):
    def encode(items):
        result = f"*{len(items)}\r\n".encode()
        for item in items:
            item = str(item).encode()
            result += f"${len(item)}\r\n".encode() + item + b"\r\n"
        return result
    with socket.create_connection(("127.0.0.1", 6379), timeout=3) as connection:
        stream = connection.makefile("rb")
        connection.sendall(encode(["AUTH", config["REDIS_PASSWORD"]]))
        assert stream.readline().startswith(b"+OK")
        connection.sendall(encode(parts))
        line = stream.readline().strip()
        if line.startswith(b"$"):
            length = int(line[1:])
            return stream.read(length + 2)[:-2].decode() if length >= 0 else None
        return line.decode()


def qdrant(method, path, data=None):
    body = json.dumps(data).encode() if data is not None else None
    req = urllib.request.Request("http://127.0.0.1:6333" + path, method=method, data=body,
                                 headers={"api-key": config["QDRANT_API_KEY"], "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=5) as response:
        return json.load(response)


def wait_ready():
    for _ in range(90):
        try:
            assert sql("select 1") == "1"
            assert redis("PING") == "+PONG"
            qdrant("GET", "/collections")
            return
        except Exception:
            time.sleep(1)
    raise SystemExit("Infrastructure did not become ready")


wait_ready()
table = "infra_probe_" + probe
collection = "infra_probe_" + probe
key = "infra:probe:" + probe
sql(f"create table {table}(value text); insert into {table} values ('persisted');")
assert redis("SET", key, "persisted") == "+OK"
qdrant("PUT", f"/collections/{collection}", {"vectors": {"size": 2, "distance": "Cosine"}})
qdrant("PUT", f"/collections/{collection}/points?wait=true",
       {"points": [{"id": 1, "vector": [1.0, 0.0], "payload": {"probe": "persisted"}}]})
subprocess.run(compose + ["restart", "postgres", "redis", "qdrant"], cwd=ROOT, check=True)
wait_ready()
assert sql(f"select value from {table}") == "persisted"
assert redis("GET", key) == "persisted"
assert qdrant("GET", f"/collections/{collection}/points/1")["result"]["payload"]["probe"] == "persisted"
sql(f"drop table {table}")
redis("DEL", key)
qdrant("DELETE", f"/collections/{collection}")
print("PostgreSQL / Redis / Qdrant: authenticated writes survived restart; probes removed")
