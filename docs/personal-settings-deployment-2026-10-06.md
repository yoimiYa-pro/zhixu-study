# 个人设置与侧栏发布记录（2026-10-06）

已部署至 [公网学习系统](https://175.24.234.152:9443)，版本 `20261006T034822Z`。前后端镜像均已更新，数据库迁移至 V12。

## 用户可见变化

- 侧栏导航隐藏滚动条，保留鼠标滚轮、触控及键盘导航滚动；手机继续横向滚动。
- 「知序」品牌旁使用现有手绘组件的书本与铅笔图标，浅色、深色主题均可显示。
- 点击右上角头像或用户名打开「个人设置」，支持修改用户名、修改密码、上传及移除头像。未上传时显示用户名首字。
- 用户名修改需验证当前密码；新密码至少 8 字符，至多 72 UTF-8 字节。密码修改使所有旧访问令牌和刷新会话失效，页面提示重新登录。
- 图片选择支持 PNG/JPG/WebP，最大 2 MB。网页缩小至 256px 后上传，服务端验证真实图片内容并重新编码，头像随账号保存、登录和刷新恢复。
- 首次启动后账号信息以数据库为准，服务重启保留用户修改；初始化环境变量及私有初始密码文件不自动改写。

## 验证

| 检查 | 结果 |
| --- | --- |
| 前端类型检查、5 项 Markdown 单元测试及生产构建 | 通过 |
| Java 全量测试 | 39 项通过；上传错误响应调整后，8 项账号、认证及 JWT 测试再次通过 |
| 隔离浏览器流程 | 错误密码提示、修改用户名、上传/移除头像、修改密码、重新登录和服务重启后持久保存通过 |
| 生产 CSP 下图片处理 | 通过 |
| 原有学习流程浏览器回归 | 登录、录题、编辑、复习、积累、统计、AI 页面和手机布局通过 |
| 最终 Alpine Java 镜像 | 使用随机隔离 schema 验证启动、V12 迁移、头像缩小重编码、个人资料及刷新返回头像 |
| 公网浏览器 | Chromium 1480×668、390×844、320×844：页面身份、非空页面、无框架错误覆盖、控制台、截图和交互通过 |
| 个人设置交互 | 资料/改密界面、标签和按钮、Escape 关闭及焦点恢复、刷新恢复账号、手机深色主题通过 |
| 之前的页面修复 | 已有参考解析公式渲染和错题详情满宽显示通过 |
| 公网静态文件 | 全部 96 个文件 SHA256 与新镜像一致，TLS 验证通过 |
| 部署检查 | 业务 API、登录/刷新/退出、AI 内部令牌边界、安全响应头和静态资源无凭据通过 |

用户名、密码和头像的写入测试在隔离数据库中完成；公网使用真实账号只读复核。发布前后账号指纹一致。其他浏览器尚未自动化验证。

本地截图与流程结果：`/tmp/study-account-qa/`。公网截图与结果：`/tmp/study-account-live-qa/`。

## 发布与备份

前端镜像：`sha256:0feb6a396045c2da356e90e7d231ca0c6c8405538c8e2f3d00e0b1c3d8d367c1`。

Java 镜像：`sha256:74e06a3ba4f779eb9482336042bc5e7a87e73d1417a16354012aa66917413fee`。

发布元数据、构建日志和资源清单：`/home/ubuntu/projects/study/civil-service-ai/.local/deployments/20261006T034822Z-personal-settings`。

数据库备份：`/home/ubuntu/projects/study/civil-service-ai/.local/backups/20261006T034822Z-personal-settings`，SHA256 与 PostgreSQL 归档目录检查通过。V12 仅新增头像与凭据版本字段。

最终 7 个服务运行，6 个已配置健康检查通过。AI 服务、出网转发、PostgreSQL、Redis 和 Qdrant 容器保持原实例。

## 回滚资源

前端旧镜像：`civil-service-ai-frontend:before-personal-settings-20261006T034822Z`。

Java 旧镜像：`civil-service-ai-backend-java:before-personal-settings-20261006T034822Z`。

前端可使用以下命令回退（Docker 权限不足时加 sudo）：

```bash
docker image tag civil-service-ai-frontend:before-personal-settings-20261006T034822Z civil-service-ai-frontend:latest
docker compose --profile app up -d --no-deps --no-build --wait --wait-timeout 60 frontend
```

旧版 Java 启动会使用初始化配置覆盖账号；修改账号之后回退旧 Java 前，应备份数据库并使初始化账号配置与当前凭据一致。保留 V12 新增字段和头像数据。
