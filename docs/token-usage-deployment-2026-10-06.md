# 处理进度与 Token 用量发布记录（2026-10-06）

已部署至 [处理进度](https://175.24.234.152:9443/tasks)，版本 `20261006T045841Z`。前端、Java 后端、AI 服务均已更新，数据库迁移至 V13。

## 页面变化

- 任务列表使用全部内容宽度，桌面每行两项，760px 以下自动单列；每项进度条占满自身列宽，状态筛选及失败重试保留。
- 新增「模型 Token 用量」，支持今天、最近 7 天、最近 30 天。显示已记录总 Token、输入、输出、模型调用次数及缓存命中。
- 增加每日输入/输出柱状与总量折线图、模型用量排行及可展开的模型明细。涵盖学习与向量模型，按提供方和模型分别汇总。
- 原任务状态分布及完成比例收纳于可展开区域；用量每 30 秒、任务每 5 秒自动更新，页面不可见时暂停轮询，支持手动刷新。
- 手机、深色主题、表格滚动和统计范围标签均已验证。

## 数据口径

用量来自模型 API 实际返回的 `usage`，不按文本估算。失败响应中已返回的用量也保存；业务输出校验失败及数据库事务回滚不会丢失已采集用量。仅保存调用元数据和用量，不保存提示词、回答或密钥。学习/向量模型及模型连接测试均计入。

从本版本上线后开始记录，历史调用无法补回。未返回完整用量时明确标注，上报零与未上报分别处理。缓存命中为输入 Token 的子集，不重复加到总量。日期按服务器时区统计，当前为北京时间。数据写入 PostgreSQL，刷新及后端重启保留。

## 验证

| 检查 | 结果 |
| --- | --- |
| Java 测试 | 46 项通过，包含受保护统计接口、时间边界、响应去重及独立事务保存 |
| Python 测试 | 47 项通过，包含真实提供方字段、缓存、缺失/零、失败响应、向量批次和并发隔离 |
| 前端 | 类型检查、5 项 Markdown 测试及生产构建通过 |
| Ruff | 通过 |
| 隔离浏览器 | 1480px 桌面、390/320px 手机；两列/单列、筛选、重试、图表、模型明细、深色与重启持久化通过 |
| 最终生产镜像 | 随机隔离数据库验证 V13 迁移及模拟提供方 → AI 响应头 → Java → 数据库/API 的完整链路；含失败输出与缺失用量 |
| 公网浏览器 | 真实任务与统计 API、时间切换、刷新、满宽两列、手机单列、账号/头像、深色与资源检查通过 |
| 公网网关 | TLS、安全响应头、受保护接口及 AI 内部令牌边界通过 |
| 公网静态文件 | 全部 97 个发布文件 SHA256 与镜像一致 |
| 持久化 | 发布前后用户名、密码、凭据版本和头像指纹一致 |

浏览器插件不可用，使用已安装的 Playwright Chromium。写入与模型调用测试在隔离数据库和模拟提供方中执行。公网检查使用服务端签发的 10 分钟只读验收令牌；仅浏览器启动刷新响应由本地提供，业务 API 和资源均为真实公网响应。未修改真实账号、刷新会话或模型选择，也未新增付费模型调用。其他浏览器尚未自动化验证。

本地流程截图与结果：`/tmp/study-token-qa/`。公网截图与结果：`/tmp/study-token-live-qa/`。

## 发布、备份及回滚

| 服务 | 发布镜像 | 回滚标签 |
| --- | --- | --- |
| 前端 | `sha256:ee8916601595c49a021792a291957598e5ed2bf822f4ee6c0a50a13aee0a9ee7` | `civil-service-ai-frontend:before-token-usage-20261006T045841Z` |
| Java | `sha256:d6c6dd83cca3a92ad2e9b5217d804d2c0a5eda96c9e167ba6ef5ac8f5ec8db3c` | `civil-service-ai-backend-java:before-token-usage-20261006T045841Z` |
| AI 服务 | `sha256:d9d799c7a5d560ffdade8a63aa0f6fe32ca1d326999f3e8eeb8b984742687a3d` | `civil-service-ai-ai-service:before-token-usage-20261006T045841Z` |

发布元数据、构建日志与资源清单：`/home/ubuntu/projects/study/civil-service-ai/.local/deployments/20261006T045841Z-token-usage`。

数据库备份：`/home/ubuntu/projects/study/civil-service-ai/.local/backups/20261006T045841Z-token-usage`，SHA256 及 PostgreSQL 归档目录检查通过。V13 仅新增 `ai_usage` 表及日期索引，保留既有业务数据。

最终 7 个服务运行，6 个已配置健康检查通过。PostgreSQL、Redis、Qdrant 和出网转发保持原容器。

回滚三个应用时保留 V13 表和已记录数据，可执行（Docker 权限不足时加 sudo）：

```bash
docker image tag civil-service-ai-frontend:before-token-usage-20261006T045841Z civil-service-ai-frontend:latest
docker image tag civil-service-ai-backend-java:before-token-usage-20261006T045841Z civil-service-ai-backend-java:latest
docker image tag civil-service-ai-ai-service:before-token-usage-20261006T045841Z civil-service-ai-ai-service:latest
docker compose --profile app up -d --no-deps --no-build --wait --wait-timeout 90 ai-service backend-java frontend
```

本次回滚镜像来自已支持个人设置的版本，重启不会覆盖当前账号；旧版本不再采集用量，已有 `ai_usage` 数据保留。
