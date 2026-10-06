"""Generate private local configuration. Passwords are never printed or sent to a model."""
import argparse
import getpass
import os
from pathlib import Path
import secrets

import bcrypt

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--generate-password", action="store_true",
                        help="Generate a local password and write it to .local/initial-password.txt")
    args = parser.parse_args()
    path = ROOT / ".env"
    if path.exists():
        raise SystemExit(".env already exists; refusing to overwrite credentials.")
    password = secrets.token_urlsafe(20) if args.generate_password else getpass.getpass("Admin password: ")
    if not args.generate_password and password != getpass.getpass("Confirm password: "):
        raise SystemExit("Passwords do not match.")
    if len(password) < 12 or len(password.encode()) > 72:
        raise SystemExit("Password must be at least 12 characters and at most 72 UTF-8 bytes.")
    values = {
        "POSTGRES_PASSWORD": secrets.token_hex(24), "REDIS_PASSWORD": secrets.token_hex(24),
        "QDRANT_API_KEY": secrets.token_hex(32), "JWT_SECRET": secrets.token_hex(32),
        "AI_SERVICE_TOKEN": secrets.token_hex(32), "MODEL_CREDENTIALS_KEY": secrets.token_hex(32),
        "ADMIN_PASSWORD_HASH": bcrypt.hashpw(password.encode(), bcrypt.gensalt(rounds=12)).decode(),
    }
    lines = []
    for line in (ROOT / ".env.example").read_text().splitlines():
        if "=" not in line or line.startswith("#"):
            lines.append(line)
            continue
        key, value = line.split("=", 1)
        value = values.get(key, value).strip("'\"")
        # Single quotes preserve the dollar characters in BCrypt hashes in Compose.
        lines.append(f"{key}='{value}'")
    if args.generate_password:
        local = ROOT / ".local"
        local.mkdir(exist_ok=True)
        with os.fdopen(os.open(local / "initial-password.txt", os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), "w") as file:
            file.write(password + "\n")
    with os.fdopen(os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), "w") as file:
        file.write("\n".join(lines) + "\n")
    print("Created private .env" + ("; local password: .local/initial-password.txt" if args.generate_password else ""))


if __name__ == "__main__":
    main()
