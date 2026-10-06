"""Stop only this project's AI/Redis, verify real API behavior, and always restore services."""
from http.cookiejar import CookieJar
import json
from pathlib import Path
import subprocess
import sys
import time
import urllib.error
import urllib.request

from check_deployment import config

ROOT = Path(__file__).resolve().parents[1]
url = "http://127.0.0.1:8088"
docker = ["docker"]
if subprocess.run(docker + ["info"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode:
    docker = ["sudo", "-n", "docker"]
compose = docker + ["compose", "--profile", "app"]
cookies = CookieJar()
opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cookies))


def request(path, body=None, token="", expected=200):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(url + path, None if body is None else json.dumps(body).encode(), headers)
    try:
        with opener.open(req, timeout=15) as response:
            assert response.status == expected
            return json.loads(response.read())
    except urllib.error.HTTPError as error:
        assert error.code == expected, f"{path}: expected {expected}, got {error.code}"
        return json.loads(error.read())


def main():
    values = config()
    password = (ROOT / ".local/initial-password.txt").read_text().strip()
    token = request("/api/auth/login", {"username": values["ADMIN_USERNAME"], "password": password})["accessToken"]
    baseline = {path: request(path, token=token) for path in ["/api/questions", "/api/idioms", "/api/essay-materials"]}
    try:
        subprocess.run(compose + ["stop", "ai-service", "redis"], cwd=ROOT, check=True, stdout=subprocess.DEVNULL)
        for path, data in baseline.items():
            assert request(path, token=token) == data, "Core database reads changed during dependency outage"
        assert request("/api/dashboard", token=token)["stats"] is not None
        assert request("/api/ai/status", token=token)["serviceAvailable"] is False
        error = request("/api/auth/refresh", {}, expected=503)
        assert error["code"] == "HTTP_503"
        print("Actual AI + Redis outage: existing JWT still serves core data; refresh fails explicitly with 503.")
    finally:
        subprocess.run(compose + ["up", "-d", "redis", "ai-service"], cwd=ROOT, check=True, stdout=subprocess.DEVNULL)
    for attempt in range(30):
        try:
            request("/api/auth/refresh", {})
            break
        except (urllib.error.URLError, AssertionError):
            time.sleep(1)
    else:
        raise RuntimeError("Redis did not recover within the verification window")
    for path, data in baseline.items():
        assert request(path, token=token) == data
    print("Services recovered; PostgreSQL-backed records are unchanged.")


if __name__ == "__main__":
    try:
        main()
    except Exception:
        print("Resilience verification failed; check local service logs.", file=sys.stderr)
        raise
