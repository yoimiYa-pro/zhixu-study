# 错题详情满宽布局发布记录 · 2026-10-06

用户反馈错题详情右侧留白过大，区块与其他页面宽度不一致。该页的区块原先被限制为 1000px，正文与 Markdown 解析另有行宽上限。现在移除区块限宽，并仅在错题详情取消正文行宽限制；题目、选项、参考解析、AI 分析、错因确认及相似题区块与页标题栏使用同一内容宽度。无选项的申论题也使用相同布局。

最新前端已部署至 [知序公网入口](https://175.24.234.152:9443)。

| 项目 | 记录 |
| --- | --- |
| 发布批次（UTC） | `20261006T032731Z` |
| 新前端镜像 | `sha256:7397a5b2970e5054b6b80ac601126f8e180f67c1b43790b416669625613f0c13` |
| 原前端镜像 | `sha256:977b518940d065d6e07d1a3a15e0a9f7b18d482231583bd7d34be0b279d59333` |
| 回滚标签 | `civil-service-ai-frontend:before-question-detail-width-20261006T032731Z` |
| 备份 | `.local/backups/20261006T032731Z-question-detail-width` |
| 私有发布记录 | `.local/deployments/20261006T032731Z-question-detail-width` |

类型检查、生产构建和 Nginx 配置检查通过。1920px 桌面上，四个区块宽度由 1000px 增至 1328px，均与页标题栏左右对齐。参考解析由 624px 增至同样的 1328px。本地浏览器还验证了申论题及相似题结果；公网浏览器验证了列表进入详情、刷新、1440px 桌面、390px 深色手机和 320px 手机。没有页面横向溢出或相关控制台、资源错误，已有 15 处公式正常渲染。浏览器证据位于 `/tmp/study-question-width-qa`，只读线上资料，无题目保存或复习提交。

全部 97 个公网静态文件 SHA256 与最新镜像一致；公网登录、业务 API、续期、退出与安全响应头检查通过。备份 SHA256 与 PostgreSQL 归档目录检查通过。最终 7 个生产服务运行，6 个已有健康检查通过；其他服务容器 ID 与发布前一致。

回滚时在项目目录执行；Docker 权限不足时加 sudo：

```bash
docker image tag civil-service-ai-frontend:before-question-detail-width-20261006T032731Z civil-service-ai-frontend:latest
docker compose --profile app up -d --no-deps --no-build --wait --wait-timeout 60 frontend
```
