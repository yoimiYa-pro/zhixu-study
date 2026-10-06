# 开发环境检查

检查日期：2026-10-02，用户时区 Asia/Shanghai。

| 项目 | 结果 |
|---|---|
| 工作目录 | `/home/ubuntu/projects/study` |
| 已有业务代码 | 无 |
| 目录/祖先目录 AGENTS.md | 无 |
| 系统 | Ubuntu 26.04.1 LTS / x86_64 |
| Node.js | 22.22.1 |
| npm | 9.2.0 |
| 系统 Python | 3.14.4，无 pip |
| Java / Maven | 未预装 |
| Docker | 29.8.1 |
| Docker Compose | 5.5.1 |
| Nginx | 1.28.3 |
| PostgreSQL / Redis 客户端 | 未预装 |
| 可用磁盘空间 | 约 46 GB |
| Docker 权限 | 当前账户需 sudo；服务可用 |

项目代码在独立的 `civil-service-ai/` 子目录创建。Java 和 Python 的本阶段验证使用官方 Maven / Java 21 和 Python 3.12 容器。容器运行不要求在宿主机安装 Java 或替换 Python。

目标部署是普通 Ubuntu Server + Docker Compose；本文的工具版本是开发环境检查记录，不是服务器最低版本要求。

