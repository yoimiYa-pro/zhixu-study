"""Read-only smoke check of the real Docker gateway. Credentials never reach stdout."""
import argparse
import getpass
import json
from pathlib import Path
import re
import shlex
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]


def config():
    values = {}
    for line in (ROOT / '.env').read_text().splitlines():
        if line and not line.startswith('#') and '=' in line:
            key, value = line.split('=', 1)
            parsed = shlex.split(value)
            values[key] = parsed[0] if parsed else ''
    return values


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--url', default='http://127.0.0.1:8088')
    parser.add_argument('--username', help='使用个人设置修改后的用户名；默认使用初始化配置')
    parser.add_argument('--prompt-password', action='store_true', help='交互输入当前密码，不读取初始密码文件')
    args = parser.parse_args()
    values = config()
    password_file = ROOT / '.local/initial-password.txt'
    password = password_file.read_text().strip() if password_file.exists() and not args.prompt_password else getpass.getpass('Admin password: ')
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor())

    def call(path, body=None, token='', status=200):
        headers = {'Content-Type': 'application/json'}
        if token:
            headers['Authorization'] = 'Bearer ' + token
        request = urllib.request.Request(args.url + path, data=None if body is None else json.dumps(body).encode(), headers=headers)
        try:
            with opener.open(request, timeout=20) as response:
                assert response.status == status, path
                return response.read(), response.headers
        except urllib.error.HTTPError as error:
            assert error.code == status, f'{path}: expected {status}, got {error.code}'
            return error.read(), error.headers

    html, headers = call('/')
    assert 'frame-ancestors' in headers.get('Content-Security-Policy', '')
    assert headers.get('X-Content-Type-Options') == 'nosniff'
    assert headers.get('Cache-Control') == 'no-store'
    call('/api/questions', status=401)
    call('/ai/status', status=401)
    assert json.loads(call('/ai/health')[0])['status'] == 'UP'
    raw, login_headers = call('/api/auth/login', {'username': args.username or values['ADMIN_USERNAME'], 'password': password})
    token = json.loads(raw)['accessToken']
    assert 'HttpOnly' in login_headers.get('Set-Cookie', '')
    for path in ['/api/auth/me', '/api/dashboard', '/api/questions', '/api/reviews/today', '/api/current-affairs/today', '/api/idioms/today', '/api/essay-materials', '/api/statistics?days=30', '/api/weekly-reports', '/api/chat', '/api/chat/conversations', '/api/scheduler-runs']:
        json.loads(call(path, token=token)[0])
    ai_status = json.loads(call('/ai/status', token=values['AI_SERVICE_TOKEN'])[0])
    assert isinstance(ai_status['llmConfigured'], bool)
    assets = re.findall(r'(?:src|href)="(/assets/[^\"]+)"', html.decode())
    content = html + b''.join(call(path)[0] for path in assets)
    for key in ['ADMIN_PASSWORD_HASH', 'JWT_SECRET', 'AI_API_KEY', 'EMBEDDING_API_KEY', 'AI_SERVICE_TOKEN', 'POSTGRES_PASSWORD', 'REDIS_PASSWORD', 'QDRANT_API_KEY']:
        secret = values.get(key, '')
        assert not secret or secret.encode() not in content, 'Private configuration leaked into static assets'
    call('/api/auth/refresh', {})
    call('/api/auth/logout', {})
    print('Real gateway verified: frontend, protected APIs, refresh/logout, AI token boundary, security headers and no credentials in assets.')


if __name__ == '__main__':
    main()
