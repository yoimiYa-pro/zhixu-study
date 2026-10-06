"""Verify that the scaffold has all required service entry points without external services."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
required = [
    "frontend/package.json", "frontend/src/main.ts", "frontend/vite.config.ts",
    "backend-java/pom.xml", "backend-java/src/main/java/cn/study/StudyApplication.java",
    "ai-service/requirements.txt", "ai-service/app/main.py",
    "ai-service/constraints.txt", "docker-compose.yml",
    "frontend/Dockerfile", "backend-java/Dockerfile", "ai-service/Dockerfile",
    "frontend/nginx.conf", "scripts/test.sh",
    ".env.example", ".gitignore", "README.md", "docs/progress.md",
]
missing = [name for name in required if not (root / name).is_file()]
if missing:
    raise SystemExit(f"Missing files: {', '.join(missing)}")
print(f"Project structure: {len(required)} required files verified")
