# 前端发布记录 · 2026-10-05

用户确认全站前端优化后授权发布。最新源码在现有服务器项目目录构建，并已更新运行中的前端容器。公网入口：[https://175.24.234.152:9443](https://175.24.234.152:9443)。

## 发布内容

知序全站的新导航、排版、列表、阅读、表单、图表、聊天与登录界面上线；内容区不再重复侧栏栏目标题。浅色、深色及手机布局一并发布。前端 Dockerfile 补充 `COPY public ./public`，确保字体、字体许可证和纸页山峦插画进入生产镜像。

默认 Docker 网络连接 npm 仓库失败，主机网络连接验证通过，本次按既有部署选项使用 `BUILD_NETWORK=host` 完成构建。Vue / TypeScript 类型检查与 Vite 生产构建通过；保留既有 ECharts 分包体积提示。

| 项目 | 记录 |
| --- | --- |
| 发布批次 | `20261005T091354Z` |
| 新镜像 | `sha256:3284ccf8b2bbe39f42ac51d70429da582488375529c38423514a0461a6ea1503` |
| 旧镜像 | `sha256:54f4595f969997f52c300137289cd522d364e063b33954f58c836ea7f6ab4450` |
| 回滚标签 | `civil-service-ai-frontend:before-20261005T091354Z` |
| JS 入口 | `/assets/index-BsAGRmBd.js` |
| CSS 入口 | `/assets/index-Dyc1K3Oc.css` |
| 备份 | `.local/backups/20261005T091354Z-frontend-deploy` |
| 私有验收记录 | `.local/deployments/20261005T091354Z-frontend` |

发布前运行项目备份脚本，数据库 dump 的 SHA256 及 PostgreSQL 归档目录校验通过。旧前端镜像保留回滚标签。新镜像的 Nginx 配置检查通过后，使用 `up -d --no-deps --no-build --wait --wait-timeout 60 frontend` 完成切换，健康检查通过。后端、AI、数据库、缓存、向量库及出口代理沿用原容器，容器 ID 均与发布前一致。

## 线上验收

- 使用正常证书验证访问公网 HTTPS，既有部署检查脚本通过：业务 API、鉴权边界、登录、续期、退出、安全响应头及静态资源私有配置泄漏检查。
- 公网下载的全部 96 个发布文件 SHA256 与生产镜像清单一致，包含全部 5 个 `public` 资产。
- Playwright 通过公网入口检查 14 个页面，分别使用 1448px 桌面与 390px 手机，共 28 个组合：登录后首页、错题列表、录入、复习、知识体系、时政、词语、素材、统计、记录、周报、两个助手和处理进度。
- 字体及插画正常加载，路由、当前导航与页面标题正确，无横向溢出、页面错误提示、脚本异常或资源错误。人工查看生产截图，首页的字体、构图与已确认的设计一致。
- Refresh Cookie 的 Secure、HttpOnly 与 SameSite=Strict 验证通过；深浅主题切换、刷新后的主题与会话恢复、退出登录通过。
- 7 个生产服务处于运行状态，已配置健康检查的服务均为 healthy。页面验收只读取业务资料，没有新增题目、文章、学习记录或 AI 对话。

首次并发文件校验遇到单次 HTTPS 读取超时；改用连接复用与有限重试后全部通过。实际浏览器访问、页面与资源验收均通过。

浏览器验收 JSON、文件校验清单及 5 张生产截图保存在上述私有验收目录，详细部署 JSON 同时记录实际容器启动时间与验收完成时间（UTC）。备份目录包含私有配置，按项目既有权限保存。

## 回滚前端

需要恢复此次发布前的界面时，在项目目录执行；Docker 权限不足时加 sudo：

```bash
docker image tag civil-service-ai-frontend:before-20261005T091354Z civil-service-ai-frontend:latest
docker compose --profile app up -d --no-deps --no-build --wait --wait-timeout 60 frontend
```

这两条命令使用已保留的旧前端镜像。数据库恢复仍按 [部署文档](deployment.md) 的专门流程执行。
