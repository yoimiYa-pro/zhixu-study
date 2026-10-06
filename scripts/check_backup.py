"""Restore the real backup into a temporary database, then remove only that database."""
from pathlib import Path
import re
import subprocess
import tempfile
import uuid

ROOT = Path(__file__).resolve().parents[1]
target = "backup_check_" + uuid.uuid4().hex[:16]
command = ["docker"]
if subprocess.run(command + ["info"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode:
    command = ["sudo", "-n", "docker"]
compose = command + ["compose", "exec", "-T", "postgres"]


def psql(sql):
    return subprocess.check_output(compose + ["sh", "-c", 'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -At -c "$1"', "sh", sql], cwd=ROOT, text=True)


def main():
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("backup_dir")
    args = parser.parse_args()
    dump = Path(args.backup_dir).resolve() / "postgres.dump"
    assert dump.is_file(), "Backup dump does not exist"
    with dump.open("rb") as file:
        listing = subprocess.check_output(compose + ["pg_restore", "--list"], cwd=ROOT, stdin=file, text=True)
    expected_tables = set(re.findall(r'^\d+;\s+\d+\s+\d+\s+TABLE\s+public\s+(\w+)\s+', listing, re.MULTILINE))
    required_tables = {'users', 'questions', 'question_options', 'mistakes', 'knowledge_points',
                       'question_knowledge_points', 'review_plans', 'reviews', 'study_records',
                       'current_affairs', 'idioms', 'essay_materials', 'weekly_reports', 'ai_tasks',
                       'daily_tasks', 'chat_messages', 'flyway_schema_history'}
    assert required_tables <= expected_tables, "Archive is missing required business tables"
    psql(f"CREATE DATABASE {target}")
    try:
        def target_sql(sql):
            return subprocess.check_output(compose + ["sh", "-c", 'exec psql -X -U "$POSTGRES_USER" -d "$1" -v ON_ERROR_STOP=1 -At -c "$2"', "sh", target, sql], cwd=ROOT, text=True).strip()

        restore_command = compose + ["sh", "-c", 'exec psql -X -U "$POSTGRES_USER" -d "$1" --single-transaction -v ON_ERROR_STOP=1 -f -', "sh", target]
        target_sql("CREATE TABLE public.restore_probe (value text); INSERT INTO public.restore_probe VALUES ('original')")
        failed = subprocess.run(restore_command, cwd=ROOT, text=True, input="DROP SCHEMA public CASCADE;\nSELECT restore_failure_probe();\n", capture_output=True)
        assert failed.returncode != 0 and target_sql("SELECT value FROM public.restore_probe") == 'original', "Failed schema replacement did not roll back"
        with tempfile.TemporaryFile() as sql, dump.open("rb") as file:
            sql.write(b'DROP SCHEMA public CASCADE;\n')
            sql.flush()
            subprocess.run(compose + ["pg_restore", "--no-owner", "--no-privileges", "--file=-"], cwd=ROOT, stdin=file, stdout=sql, check=True)
            sql.seek(0)
            subprocess.run(restore_command, cwd=ROOT, stdin=sql, check=True, stdout=subprocess.DEVNULL)
        result = subprocess.check_output(compose + ["sh", "-c", 'exec psql -U "$POSTGRES_USER" -d "$1" -v ON_ERROR_STOP=1 -At -c "$2"', "sh", target,
            "select (select count(*) from public.users),(select count(*) from public.flyway_schema_history),(select count(*) from information_schema.tables where table_schema='public')"], cwd=ROOT, text=True).strip().split("|")
        # Older backups are valid: the app applies subsequent Flyway migrations on startup.
        assert int(result[0]) == 1 and int(result[1]) >= 1 and int(result[2]) == len(expected_tables), "Restored schema or user is incomplete"
        restored_tables = set(target_sql("SELECT table_name FROM information_schema.tables WHERE table_schema='public'").splitlines())
        assert restored_tables == expected_tables, "Restored tables differ from the archive"
        assert target_sql("SELECT to_regclass('public.restore_probe') IS NULL") == 't', "Schema replacement left an obsolete table"
        print("Real backup restored into an isolated database; user, Flyway history and business tables verified. Failed replacement rolled back, and successful replacement removed obsolete tables.")
    finally:
        # target is generated above and never accepts a user-supplied database name.
        psql(f"DROP DATABASE {target} WITH (FORCE)")


if __name__ == "__main__":
    main()
