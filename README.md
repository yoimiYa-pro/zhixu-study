# 知序 · 公考 AI 学习服务器

长期使用的单用户学习系统：Vue 3 / TypeScript、Java 21 / Spring Boot、Python 3.12 / FastAPI、PostgreSQL、Redis、Qdrant 与 Nginx。数据以 PostgreSQL 为准；AI 分析、Embedding 和索引采用持久化后台队列。

## 功能

- 单用户 BCrypt 登录、30 分钟 Access JWT、Redis 7 天 Refresh 会话与令牌轮换，没有注册入口。
- Dashboard、每日任务、连续学习天数、练习数量及学习时间记录。
- 六种题型的题目 / 错题 CRUD、用户确认错因、知识点树与掌握度。
- 经过 Pydantic 和 Java 验证的错题分析，版本检查避免旧分析覆盖编辑结果。
- 真正的 Embedding / Qdrant 相似题检索，索引题目、解析、知识点、时政和素材。
- 1 / 3 / 7 / 14 / 30 天间隔复习，按对错、难度、信心和掌握度调整，提交幂等。
- 实际 RSS / Atom 与新闻网站采集、原文直达、主题标签筛选与手动分类、结构化考点与申论素材提取。
- 成语 / 词语库、收藏、掌握标记、每日推荐与带分类和标签的申论素材库。
- 7 / 30 天统计和 ECharts 图表，数据来自实际学习记录。
- Chat RAG 和申论学习提纲，先读取私人数据库，再补充向量检索，校验实际资料引用。
- 可配置定时任务、启动补执行、去重，以及先保存真实统计再生成 AI 总结的周报。
- 手机布局、深浅色模式、完整 Docker 服务、Nginx / HTTPS 示例与备份恢复。

不附带模拟题库、新闻、答案或向量。初始库为空，日报与推荐只显示实际收录内容。**在线 AI 需要自己的模型与 Embedding 凭据，自动抓取需要有效 RSS / Atom 或支持的网站来源。** 配置缺失明确报错。本地验证不等同于已部署到你的云服务器。

## 快速开始：Docker

从项目根目录操作，先创建私有配置：

~~~bash
python3 -m venv .local/init-env
.local/init-env/bin/pip install bcrypt==4.3.0
.local/init-env/bin/python scripts/init_env.py
docker compose config --quiet
docker compose --profile app up -d --build
~~~

初始化交互询问管理员密码，至少 12 字符、不超过 72 个 UTF-8 字节，生成 BCrypt Hash 与随机服务密钥。私有 .env 权限为 600，脚本拒绝覆盖已有配置。没有本地 Python 依赖时，使用 [部署文档](docs/deployment.md) 中的容器初始化方式。

当前开发环境已生成 .env，初始用户名为 admin，自动生成的密码在 .local/initial-password.txt。该文件不进 Git，密码不写入日志。新部署请自行初始化，无须复用开发凭据。

登录后点击右上角头像进入「个人设置」，可上传头像、修改用户名和密码。账号修改保存在数据库中，重启服务不会重置；`.env` 中的管理员信息仅用于首次初始化。密码修改后所有设备需重新登录，私有初始密码文件不会同步改写。

「处理进度」展示两列任务及模型 Token 用量，支持今天、最近 7 天和 30 天的每日趋势、模型排行与明细。用量依据模型实际返回的统计，从功能上线后开始记录；缺失上报的调用单独显示，历史调用不会估算补填。

打开 [本地入口](http://127.0.0.1:8088)。默认 Compose 只启动基础服务；加 --profile app 启动 Vue/Nginx、Java 和 FastAPI。Docker 权限不足时，在 Docker 命令前加 sudo。端口默认只绑定本地回环。

当前服务器另有 [IP HTTPS 入口](https://175.24.234.152:9443)，使用原账号密码登录。配置与维护见[部署文档](docs/deployment.md#当前服务器使用-ip-加端口)。

## 模型与时政配置

在 .env 中填写自己的配置，并保留字符串单引号：

~~~dotenv
AI_BASE_URL='https://your-provider.example/v1'
AI_API_KEY=''
AI_MODEL=''
EMBEDDING_BASE_URL='https://your-embedding-provider.example/v1'
EMBEDDING_API_KEY=''
EMBEDDING_MODEL=''
EMBEDDING_DIMENSION='1536'
NEWS_FEEDS=''
NEWS_SITES=''
NEWS_LOOKBACK_DAYS='7'
NEWS_ALLOWED_HOSTS='www.gov.cn,gov.cn,www.news.cn,news.cn,www.people.com.cn,people.com.cn'
~~~

上面是格式示例，域名是占位符。模型名与向量维度必须对应实际提供商。Embedding Key 为空时可复用 AI Key，但服务地址和 Key 必须兼容。默认不发送 temperature 或 dimensions，按提供商能力设置 AI_TEMPERATURE、EMBEDDING_SEND_DIMENSIONS。详见 [AI Provider](docs/ai-provider.md)。

NEWS_FEEDS 用逗号分隔实际 HTTPS RSS / Atom 地址；NEWS_SITES 支持新华网、央广网和中国政府网的新闻列表，两类合计最多 12 个来源。将列表与文章域名加入 NEWS_ALLOWED_HOSTS。多个来源轮流取文，默认只收录最近 7 天的新闻；单次上限由 NEWS_MAX_ARTICLES 决定。每一次请求及最多 3 次跳转均检查 HTTPS、允许域名与公网 DNS，拒绝降级和内网地址。没有来源或抓取失败时不编造新闻。具体配置见[时政来源与分类](docs/news.md)。

日报提供明确的“查看原文”按钮、标签/来源/关键词筛选、标签和来源分布图及抓取进度。初步标签由关键词生成，AI 整理后完善；可手动修改，AI 保留手动分类。首页、每日复习、知识体系、学习统计和后台任务使用实际记录显示目标环、进度条与图表，导航使用统一图标；未知进度显示处理中。

修改配置后重新创建容器环境：

~~~bash
docker compose --profile app up -d --force-recreate backend-java ai-service
~~~

题目立即入库，后台另行分析和索引。在“处理进度”查看状态，修正配置后重试；更换向量模型或恢复数据库后使用“重建知识索引”。相似度是余弦分数，不是答题正确概率。

## 日常使用

1. 登录并录入错题，填写参考答案、自己的答案和来源。
2. 确认错因、建立知识点；配置 AI 后后台补充分析和知识点建议。
3. 在“每日复习”逐题作答，选择信心程度，提交后自动安排下次复习。
4. 在“今日学习”记录额外练习数量、错题数、用时和备注。
5. 阅读时政，积累词语和申论素材，查看统计和周报。
6. 使用学习助手进行教学和复盘，使用申论助手整理素材与训练提纲。

助手回答支持 Markdown 标题、重点加粗、列表、引用、表格、代码块和 LaTeX 公式，已有聊天记录也会自动排版。公式可使用 `\(...\)`、独立行的 `\[...\]`、`$...$`、`$$...$$` 或 math 代码块。手机上的宽表格与长公式可横向滚动，支持深色模式，引用仍可打开已有资料。

学习助手支持新建、切换、搜索、重命名和删除聊天。消息自动保存到数据库，刷新后恢复当前聊天；追问使用当前聊天的历史，其他聊天的消息不会混入。原有记录迁入“此前的学习对话”，较长记录可加载更早的消息。手机点“聊天历史”打开会话列表。使用和上下文范围见[聊天会话](docs/chat.md)。

默认周日 20:00 生成周报。LLM 不可用时统计快照照常保存，AI 总结显示未完成。统计页题型与错因分布为当前错题库；趋势和正确率按选定范围计算，周报高频错因按本周题目学习记录计算。手工批量练习不会被伪造分配到题型或错因。

## 独立开发

需要 Node.js 22.12+、Java 21+ / Maven 3.9+、Python 3.12+。先创建私有配置并启动基础服务：

~~~bash
docker compose up -d postgres redis qdrant
~~~

三个终端分别从根目录开始。Java：

~~~bash
cd backend-java
python3 ../scripts/run_env.py mvn spring-boot:run
~~~

AI：

~~~bash
cd ai-service
python3.12 -m venv .venv
.venv/bin/pip install -r requirements-dev.txt -c constraints.txt
.venv/bin/uvicorn app.main:app --host 127.0.0.1 --port 8000 --no-access-log
~~~

Vue：

~~~bash
cd frontend
npm ci
npm run dev -- --host 127.0.0.1
~~~

打开 [开发入口](http://127.0.0.1:5173)。当前宿主 8000 已被其他服务占用；独立 AI 可用 8011，同时设置 AI_SERVICE_URL=http://127.0.0.1:8011、VITE_AI_PROXY_TARGET=http://127.0.0.1:8011。VITE_JAVA_PROXY_TARGET 可调整 Java 端口。真正的密钥均不使用 VITE_ 前缀。

## 测试

~~~bash
bash scripts/test.sh --docker
~~~

执行前端测试 / 类型检查 / 构建、真实 PostgreSQL / Redis 的 Java 测试、Python API 与真实 Qdrant 集成、Ruff 和 Compose 配置检查。Java 测试使用 integration_test schema，截断前验证名称，不截断生产 public。

浏览器验收：

~~~bash
cd frontend
npx playwright install --with-deps chromium
cd ..
bash scripts/test-e2e.sh
~~~

脚本启动随机隔离 schema、8081 测试 Java、5190 生产预览及随机本机端口的模型测试服务。使用私有初始密码文件，或通过 E2E_USERNAME / E2E_PASSWORD 提供自定义登录。结束后清除隔离 schema 和测试服务。请预留 8081 / 5190 端口。固定模型和抓取响应仅用于测试，生产路径使用真实服务。

生产入口只读检查：python3 scripts/check_deployment.py。
基础服务持久化检查：python3 scripts/check_infra.py。
备份恢复验证：python3 scripts/check_backup.py BACKUP_DIRECTORY。
check_resilience.py 会短暂关闭本项目 AI 与 Redis 并恢复，仅在验收环境运行。

实际结果及未验证项见 [阶段验证](docs/progress.md)。在线模型实测需要凭据；域名 HTTPS 实测需要云服务器与 DNS。

## 部署、备份、维护

页面顶部新增 **AI 模型** 快捷选择：读取当前服务的实际模型列表，通过连接测试后保存选择，刷新页面和重启服务后保留。默认选项使用 `.env` 的 `AI_MODEL`，详见[模型接入](docs/ai-provider.md)。

完整说明见 [Ubuntu 部署](docs/deployment.md)：域名、Nginx、Certbot、Secure Cookie、更新、持久化、备份、恢复和故障处理。云安全组不开放数据端口。

~~~bash
bash scripts/backup.sh
# 明确替换当前 public schema；脚本另外保存当前备份
bash scripts/restore.sh /absolute/path/to/backup --confirm-replace
~~~

.env、.local、虚拟环境、日志、构建及浏览器产物已加入 .gitignore。正常更新不要执行 docker compose down -v，否则会删除持久化卷。

## 目录

~~~text
civil-service-ai/
├── frontend/src/{views,components,router,stores,lib}
├── frontend/{e2e,Dockerfile,nginx.conf}
├── backend-java/src/main/java/cn/study/
│   ├── controller, service, repository, dto
│   └── config, security, scheduler, tasks, exception
├── backend-java/src/main/resources/db/migration/
├── backend-java/src/test/java/cn/study/
├── ai-service/app/{api,services,providers,models,prompts,rag,core}
├── ai-service/{tests,requirements.txt,constraints.txt,Dockerfile}
├── deploy/nginx/
├── docs/
├── scripts/
├── docker-compose.yml
├── .env.example
└── README.md
~~~

[架构](docs/architecture.md) · [API](docs/api.md) · [原始需求](docs/requirements.md)
