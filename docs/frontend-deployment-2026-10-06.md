# 前端发布记录 · 2026-10-06

错题手动录入的参考解析已部署至 [知序公网入口](https://175.24.234.152:9443)。错题详情和每日复习使用统一 Markdown 组件，支持标题、列表、表格、重点、代码块和 LaTeX 数学公式。录入页增加格式提示，编辑时保留原始 Markdown。

Vite 构建将字体输出为独立同源文件，避免小型 KaTeX 字体内嵌后被生产 CSP 拦截。

| 项目 | 记录 |
| --- | --- |
| 发布批次（UTC） | `20261006T031137Z` |
| 新前端镜像 | `sha256:977b518940d065d6e07d1a3a15e0a9f7b18d482231583bd7d34be0b279d59333` |
| 原前端镜像 | `sha256:8b06b72b7aa2cd89ef3f6fcbe787edc1df2432d6679fa198843736df23d4a578` |
| 回滚标签 | `civil-service-ai-frontend:before-question-markdown-20261006T031137Z` |
| 备份 | `.local/backups/20261006T031137Z-question-markdown` |
| 私有验收记录 | `.local/deployments/20261006T031137Z-question-markdown` |

发布前创建数据库 dump 和私有配置备份，SHA256 与 PostgreSQL 归档目录检查通过。使用 `BUILD_NETWORK=host` 构建前端镜像，类型检查、生产构建和 Nginx 配置检查通过，再以 `up -d --no-deps --no-build --wait` 更新前端容器。

已有 5 项前端单元测试与本地浏览器录入、保存、编辑、刷新、复习展示验证通过。公网验收确认全部 97 个发布文件与镜像 SHA256 一致，HTTPS、登录、业务接口、续期、退出及安全响应头通过。

实际已有错题的解析显示 15 处数学公式，编辑页保留原文。桌面 1440×1000、手机 390×844 深色模式与刷新验证通过，无页面横向溢出、公式字体策略错误、脚本异常或资源加载失败。未登录时预期的续期 401 单独记录。验收仅查看已有学习资料，没有保存题目或提交复习。

最终 7 个生产服务运行，6 个已配置健康检查均通过。其他服务容器 ID 与发布前一致。

需要回滚时，在项目目录执行；Docker 权限不足时加 sudo：

```bash
docker image tag civil-service-ai-frontend:before-question-markdown-20261006T031137Z civil-service-ai-frontend:latest
docker compose --profile app up -d --no-deps --no-build --wait --wait-timeout 60 frontend
```
