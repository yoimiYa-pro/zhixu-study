"""Load private .env without shell expansion, then launch a native development command."""
import os
from pathlib import Path
import re
import shlex
import sys

ROOT = Path(__file__).resolve().parents[1]
if len(sys.argv) < 2:
    raise SystemExit("Usage: python3 scripts/run_env.py COMMAND [ARGUMENTS...]")
environment = dict(os.environ)
for line in (ROOT / ".env").read_text().splitlines():
    if line and not line.lstrip().startswith("#") and "=" in line:
        key, value = line.split("=", 1)
        if not re.fullmatch(r"[A-Z][A-Z0-9_]*", key):
            raise SystemExit("Invalid .env variable name")
        parsed = shlex.split(value, comments=True)
        environment.setdefault(key, " ".join(parsed))
os.execvpe(sys.argv[1], sys.argv[1:], environment)
