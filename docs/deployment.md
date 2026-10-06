# Ubuntu 部署与维护

## 1. 准备服务器

使用 Ubuntu Server，建议至少 2 核 / 4 GB 内存并预留数据库备份空间。安装 Docker Engine 与 Compose 插件：[Docker 官方 Ubuntu 安装说明](https://docs.docker.com/engine/install/ubuntu/)。上传整个项目源码，排除 `.local`、`node_modules`、`target`、虚拟环境和已有密钥。

在项目目录创建 `.env`。可以用 Python 环境中的 `bcrypt` 运行 `scripts/init_env.py`；无本地 Python 依赖时使用下面的容器初始化。请先填好环境配置再启动服务。

```bash
docker run --rm -it -u "$(id -u):$(id -g)" \
  -v "$PWD:/work" -w /work python:3.12-slim \
  sh -c 'python -m venv /tmp/init-env && /tmp/init-env/bin/pip install bcrypt==4.3.0 && /tmp/init-env/bin/python scripts/init_env.py'
docker compose config --quiet
docker compose --profile app up -d --build
docker compose --profile app ps
```

前端只绑定 `127.0.0.1:8088`。PostgreSQL / Redis / Qdrant 也只绑定本地回环地址；云安全组只需要允许 SSH、80、443，不开放数据端口。模型参数见 `ai-provider.md`。修改 `.env` 后用 `docker compose --profile app up -d --force-recreate backend-java ai-service`，容器 `restart` 不会重新读取新的环境变量。

容器无法访问外网、宿主机可以直连时，按[私有出网转发](private-egress.md)为项目网桥设置受限的 HTTPS 转发。

管理员用户名和密码配置仅用于首次初始化，已有账号以数据库为准。日常修改请使用右上角「个人设置」；重启、重建容器及改写 `.env` 不会覆盖已保存的用户名、密码或头像。修改密码后需重新登录，`.local/initial-password.txt` 不会自动更新。账号修改后的部署检查可指定 `--username` 和 `--prompt-password`，交互输入当前密码。

构建镜像使用已锁定的依赖；Java 镜像的打包步骤不连接生产数据库，数据库测试通过 `scripts/test.sh --docker` 单独执行。Docker 构建网络由 `BUILD_NETWORK` 配置；一般用 `default`，桥接网络不能访问依赖仓库时可使用 `host`。[Compose 构建网络说明](https://docs.docker.com/reference/compose-file/build/#network)

## 2. 域名、Nginx 与 HTTPS

将域名 A / AAAA 记录指向服务器。把示例中的 `study.example.com` 改成自己的域名，仅在该站点的服务器上执行：

```bash
sudo apt update
sudo apt install nginx snapd
sudo cp deploy/nginx/study-http.conf /etc/nginx/sites-available/study
sudo ln -s /etc/nginx/sites-available/study /etc/nginx/sites-enabled/study
sudo nginx -t
sudo systemctl reload nginx
sudo snap install --classic certbot
sudo /snap/bin/certbot --nginx -d study.example.com
sudo /snap/bin/certbot renew --dry-run
```

Certbot 会在已启用的域名配置上添加证书和 HTTPS。也提供 `deploy/nginx/study-https.conf` 作为已取得证书后的完整配置参考；不要同时启用两个同域名示例。具体流程依据 [Certbot 官方 Nginx 指南](https://certbot.eff.org/instructions?os=snap&tab=standard&ws=nginx) 与 [Ubuntu HTTPS 文档](https://ubuntu.com/server/docs/how-to/security/obtain-tls-certificates/)。

HTTPS 启用后将 `.env` 中 `COOKIE_SECURE=true`，重新创建 Java 容器，再登录。若使用 HTTP 的本地预览，保留 `false`。Access JWT 在浏览器内存中，Refresh Cookie 为 HttpOnly / SameSite=Strict；刷新令牌只以摘要存入 Redis，轮换后旧令牌失效。

外部 Nginx 将请求转给容器入口；容器 Nginx 服务 Vue，并将 `/api/` 转给 Java、`/ai/` 转给 FastAPI。直接 `/ai/` 调用仍需 `.env` 中 `AI_SERVICE_TOKEN`，浏览器业务页面使用 Java JWT。该内部令牌不进入前端构建。Nginx 日志不包含请求体、Cookie、Authorization 或查询参数。

### 当前服务器：使用 IP 加端口

本次服务器已启用 [https://175.24.234.152:9443](https://175.24.234.152:9443)。使用原学习系统账号密码登录，无需域名。

- 已安装 `/etc/nginx/sites-available/civil-study-ip`，启用同名站点，配置来源为 `deploy/nginx/study-ip.conf`。
- Nginx 的 9443 HTTPS 入口转发到本机 `127.0.0.1:8088`，复用已有的有效 IP 证书及现有续期任务。其他服务器需要自己的 IP 证书并修改配置中的 IP 和证书路径。
- 外层 Nginx 为 `study_refresh` 添加 Secure / HttpOnly / SameSite=Strict；当前本地 HTTP 预览继续使用原设置。
- Ubuntu 防火墙已放行 IPv4 的 TCP 9443。云安全组也需要允许该端口；若其他设备连接超时，请检查云安全组入站规则。

维护此入口时，在项目目录执行：

```bash
sudo install -o root -g root -m 644 deploy/nginx/study-ip.conf /etc/nginx/sites-available/civil-study-ip
sudo nginx -t
sudo systemctl reload nginx
python3 scripts/check_deployment.py --url https://175.24.234.152:9443
```

## 3. 持久化与备份

PostgreSQL、Redis AOF、Qdrant 使用命名卷；服务重启和镜像更新保留数据。不要在正常更新时执行 `docker compose down -v`。

```bash
bash scripts/backup.sh
```

备份保存 `public` schema 的 PostgreSQL 自定义格式 dump、SHA256 校验及私有 `.env` 副本。`.local/backups` 不进 Git，文件权限由 `umask 077` 限制。定期把备份复制到自己控制的独立存储；这些文件包含私人记录和密钥，应按私人数据保存。

恢复需要明确的 `--confirm-replace`，会先验证校验和、完整读取 dump 并自动保存当前备份，然后暂时停止前端和 Java。删除旧 `public` schema 与导入在同一个 PostgreSQL 事务内执行；导入失败会回滚并重新启动应用。选择要恢复的目录：

```bash
bash scripts/restore.sh /absolute/path/to/backup --confirm-replace
```

旧版本备份恢复后，Java 启动时会继续执行尚未应用的 Flyway 迁移。跨服务器恢复时，先手动恢复对应的私有配置并调整域名、URL、模型配置，再启动服务。Qdrant 是可重建的派生索引：登录后在处理进度页使用“重建知识索引”，或调用 `POST /api/maintenance/reindex`。Redis 的 Refresh 会话在丢失后重新登录即可；学习记录保存在 PostgreSQL。

## 4. 更新与故障

```bash
bash scripts/backup.sh
bash scripts/test.sh --docker
docker compose --profile app up -d --build
docker compose --profile app logs --tail 100 backend-java ai-service
```

只发布前端时，在备份和前端验证完成后，先构建镜像，再更新前端服务：

```bash
docker compose --profile app build frontend
docker compose --profile app up -d --no-deps --no-build --wait --wait-timeout 60 frontend
python3 scripts/check_deployment.py --url https://175.24.234.152:9443
```

默认 Docker 网络无法下载依赖时，构建命令前加 `BUILD_NETWORK=host`；需要 sudo 时使用 `sudo env BUILD_NETWORK=host docker compose --profile app build frontend`。前端 Dockerfile 同时复制 `src` 与 `public`，自托管字体、许可证及插画均进入镜像。实际发布与回滚信息见 [2026-10-05 前端发布记录](frontend-deployment-2026-10-05.md) 和 [2026-10-06 Markdown 解析发布记录](frontend-deployment-2026-10-06.md)。 处理进度、模型 Token 统计及三个应用的最新发布/回滚信息见 [2026-10-06 Token 用量发布记录](token-usage-deployment-2026-10-06.md)。

- AI / Embedding / Qdrant 失败：核心错题、素材、学习记录与复习继续工作；任务保存失败代码并允许重试。Chat 会使用实际数据库记录进行检索回退，生成回答仍需要可用 LLM。
- Redis 失败：已有未过期的 Access JWT 可以继续使用 CRUD；登录、刷新、退出会话会返回服务不可用。Redis 恢复后重新尝试。
- PostgreSQL 失败：核心接口返回 503 并提示重试；不返回伪造数据。Java `/actuator/health` 反映数据依赖状态，`/api/health` 和 FastAPI `/health` 是进程存活检查。
- 定时任务按 `TZ` 执行，默认 `Asia/Shanghai`。启动会补执行当天已到时间的任务及最近一次周报，PostgreSQL 日志和去重键避免重启重复创建任务。
- 修改 Embedding 模型、URL 或维度会使用新的 Qdrant collection。配置后重建索引；原始学习记录和历史解析保留。

本项目没有自动访问云账号或申请真实域名证书。示例配置与本地镜像/路由测试不等同于用户云服务器已经上线。
