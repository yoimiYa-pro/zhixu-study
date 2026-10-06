# 模型接入

服务支持服务器默认模型和用户添加的自定义模型接入。服务器默认配置来自 `.env`，自定义接入与当前选择保存在数据库。服务使用实际模型分析和 Embedding。

登录后，页面顶部的 **AI 模型** 下拉框可快速选择服务器模型或已保存的自定义接入。连接和 JSON 输出测试通过后保存当前选择，刷新页面与服务重启会恢复。带“默认”的选项恢复 `.env` 的 `AI_MODEL`。后续对话、题目/时政解析、申论助手和周报使用所选模型；每次请求保留开始时的选择。

点击下拉框旁的 **管理模型** 按钮，再选择 **添加接入**，填写提供商名称、Base URL、API Key 和模型名称，点击 **验证并使用**。提供商名称用于识别接入；接口使用 OpenAI 兼容的 Chat Completions 协议，Base URL 填写 HTTPS 公网接口基址，不填写 `/chat/completions`、查询参数或 URL 凭据。自定义测试直接调用指定模型，不要求提供商支持 `/models` 列表接口。

管理弹窗支持编辑、切换和移除，最多保存 20 个接入。编辑时 API Key 留空沿用原密钥，填写新值则替换；密钥默认隐藏，可临时显示。接口不支持 `response_format` 时可关闭 **JSON 模式**，结构化结果仍会校验。连接或输出验证失败保留原选择，当前接入须先切换后才能移除。服务器默认模型没有配置、列表暂不可用时，仍可添加自定义接入。

API Key 由后端使用 AES-GCM 加密后保存，绑定接入 ID，API 只返回 `hasApiKey` 状态。前端不回显已存密钥，也不将接入凭据写入浏览器持久存储。`MODEL_CREDENTIALS_KEY` 是服务器私有加密密钥，新安装初始化时生成；旧安装未配置时从现有 `JWT_SECRET` 派生。保存接入后须保留其加密密钥；已有接入使用 JWT 派生密钥时，轮换 JWT 前应迁移密钥或重新录入接入。配置与数据库备份都应保留。

自定义模型只切换聊天与解析，Embedding 的服务、模型、维度和凭据沿用服务器配置。API Key 在内部鉴权链路中传递，日志不输出请求头或模型完整错误响应。自定义请求验证公网 DNS，直接连接及私有代理都固定已验证的公网地址并保留原服务的 TLS 证书校验。

`AI_BASE_URL` 指向 OpenAI Compatible `/v1` 基址；服务调用 `/chat/completions`。`AI_API_KEY` 和 `AI_MODEL` 必填。默认启用 JSON mode，返回后仍由 Pydantic 校验；JSON mode 本身不保证符合 Schema，见 [OpenAI 官方结构输出说明](https://developers.openai.com/api/docs/guides/structured-outputs)。

`AI_TEMPERATURE` 默认空，不发送该参数；需要时填写模型支持的数值。`AI_JSON_MODE=false` 可兼容不接受 `response_format` 的服务，结构校验仍保留。模型特有参数需要扩展 Provider，当前版本使用标准 Chat Completions 契约，见 [官方 API 参数](https://developers.openai.com/api/reference/python/resources/chat/subresources/completions/methods/create)。

Embedding 支持独立的 `EMBEDDING_BASE_URL` / `EMBEDDING_API_KEY` / `EMBEDDING_MODEL`。独立 Key 为空时使用 AI Key。`EMBEDDING_DIMENSION` 必须等于实际返回维度。需要服务端缩短向量且模型支持时设置 `EMBEDDING_SEND_DIMENSIONS=true`；这个参数并非所有模型支持，见 [官方 Embedding 文档](https://developers.openai.com/api/docs/guides/embeddings)。不会截断、补零或伪造向量。

长文按 `EMBEDDING_CHUNK_CHARS` 切片，默认 1800 字符、有 100 字符重叠；更小上下文的模型可减小该值。模型或维度改变会使用新的索引集合，原始数据始终保存在 PostgreSQL，需要重新执行索引任务。

`AI_PROXY_URL` 可指定模型请求的出站代理。使用项目私有代理时，自定义提供商目标附带短时签名，不必修改代理域名配置。

`NEWS_PROXY_URL` 单独配置 RSS 和文章抓取的出站代理，仍会检查来源域名、HTTPS 和公网 DNS 地址。Docker 网桥无法直接访问外网时，可启用[私有出网转发](private-egress.md)。

测试中的 HTTP 响应和二维向量只用于测试契约、结构拒绝和真实 Qdrant 操作，运行服务使用外部模型 API。没有真实模型凭据时，相关任务明确失败，业务 CRUD 正常工作。
