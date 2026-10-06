# API

业务接口使用 Authorization: Bearer Access-JWT。登录、刷新、退出接受 JSON Content-Type；Refresh 通过 HttpOnly Cookie 发送。直接 FastAPI 调用使用 AI_SERVICE_TOKEN，该内部令牌不进入浏览器。

## Java

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | /api/auth/login | username / password |
| POST | /api/auth/refresh | 空 JSON，轮换 Refresh Cookie |
| POST | /api/auth/logout | 空 JSON，撤销 Refresh |
| GET | /api/auth/me | 当前用户、avatar PNG Data URL（无头像为空）与服务器时区；登录/刷新也返回头像 |
| GET | /api/ai/usage | days=1..90（默认 7）；服务器时区的每日趋势、模型汇总、输入/输出/总 Token、缓存命中及缺失用量调用数 |
| PATCH | /api/account/profile | username / currentPassword，修改用户名 |
| POST | /api/account/password | currentPassword / newPassword（至少 8 字符，至多 72 UTF-8 字节）；旧访问及刷新令牌立即失效 |
| POST | /api/account/avatar | multipart file，PNG/JPG 最大 512 KB，尺寸不超过 4096 × 4096；网页先缩小至 256px，支持选择 PNG/JPG/WebP 最大 2 MB |
| DELETE | /api/account/avatar | 移除头像，恢复用户名首字默认头像 |
| GET | /api/dashboard | 实际统计、任务、复习与今日积累 |
| GET / POST | /api/questions | q / type / mistakesOnly / page / pageSize；创建 |
| GET / PUT / DELETE | /api/questions/{id} | 查询、完整修改、软删除 |
| POST | /api/questions/{id}/analyze | 开始或重新讲解，返回 202 与题目；当前版本已有等待或处理中的分析时复用任务 |
| PATCH | /api/questions/{id}/mistake | reason 确认错因 |
| POST | /api/questions/{id}/similar | 真正的向量相似题 |
| GET / POST | /api/knowledge-points | 节点和掌握度；添加 |
| PUT / DELETE | /api/knowledge-points/{id} | 拒绝循环或删除被引用节点 |
| GET | /api/reviews/today | 到期及过期未复习题目 |
| POST | /api/reviews/{id}/complete | requestId / result / answer / timeSpent / confidence |
| GET / POST | /api/study-records | 最近 100 条；记录学习 |
| PATCH | /api/daily-tasks/{id} | completed，只更新当天任务 |
| GET | /api/current-affairs/today | 今日实际收录时政 |
| GET / POST | /api/current-affairs | date / tag / source / q 查询，单次最多 200 条；手动原文和来源 |
| GET | /api/current-affairs/overview | date；当日总量、整理量、原文链接量及标签/来源分布 |
| GET | /api/current-affairs/sources | 实际配置的来源目录（RSS / 网站） |
| PATCH | /api/current-affairs/{id}/tags | tags；手动分类，后续 AI 保留 |
| GET | /api/current-affairs/{id} | 原文与结构化整理 |
| POST | /api/current-affairs/fetch | 入队，202 和 taskId |
| POST | /api/current-affairs/{id}/analyze | 入队，202 和 taskId |
| GET / POST | /api/idioms | q / favorite / mastered / page；创建 |
| GET | /api/idioms/today | 今日未掌握词语推荐 |
| GET / PUT / PATCH / DELETE | /api/idioms/{id} | 查询 / 编辑 / 状态 / 删除 |
| GET / POST | /api/essay-materials | q / category / kind / page；创建 |
| GET / PUT / DELETE | /api/essay-materials/{id} | 素材查询 / 编辑 / 删除 |
| GET | /api/statistics | days=7 或 30 |
| GET | /api/weekly-reports | 最近 52 条周报及任务状态 |
| GET | /api/weekly-reports/{id} | 已保存统计和总结 |
| POST | /api/weekly-reports/generate | 默认本周，可用 date 指定过去 |
| GET | /api/chat | 可选 conversationId，返回该聊天最近 100 条；未传时为最近聊天 |
| POST | /api/chat | conversationId / turnId / query / 可选 questionId，回复含 conversationId |
| GET / POST | /api/chat/conversations | 会话列表；新建，可选 questionId |
| GET / PATCH / DELETE | /api/chat/conversations/{id} | 查询 / 修改 title / 删除会话及消息 |
| GET | /api/chat/conversations/{id}/messages | before 消息 UUID / pageSize 默认 50、最大 100；返回 messages / hasMore |
| POST | /api/essay-assistant | topic，返回学习提纲与引用 |
| GET | /api/ai-tasks | 最近 100 个持久化任务 |
| GET | /api/ai-tasks/{id} | 单任务 |
| POST | /api/ai-tasks/{id}/retry | 只允许失败任务重试 |
| GET | /api/ai/status | 配置状态；失败时 serviceAvailable=false |
| GET | /api/ai-models | 当前服务的模型列表、默认和有效模型、已保存选择及 revision |
| POST | /api/ai-models | model（模型 ID 或 null 使用默认）/ expectedRevision；连接校验后保存 |
| POST | /api/maintenance/reindex | 安排全库索引，返回 queued |
| GET | /api/scheduler-runs | 最近 50 次执行 |

## 输入约定

题目 content / correctAnswer / questionType 必填。选择题 options 为 A–H 对象，至少两项；答案必须对应实际选项。申论可无选项。题型：常识判断、言语理解、数量关系、判断推理、资料分析、申论。难度：简单、中等、困难。

可选 userAnswer / explanation / knowledgePoints / source / year / region / mistake / mistakeReason / timeSpent。创建时 clientRequestId 是幂等 UUID。

错因：知识盲区、理解错误、审题错误、计算错误、方法错误、粗心、时间不足、记忆错误；尚未确认保留“未确认”。AI 建议不会覆盖用户确认。软删除保留学习和复习历史。

重新讲解保留题干、选项、答案、手动参考解析、已确认错因与复习计划；旧 AI 分析在新分析通过校验前继续可见。重新讲解生成新题目版本，过期任务不能覆盖结果。AI 分析文字字段支持 Markdown 与 LaTeX 行间公式，仍通过 JSON Schema 校验；页面按思路、步骤、答案核对、快速方法与易错点展示，纯文本旧分析中的常见算式自动分行。

复习 result 为 CORRECT / INCORRECT / SKIP，confidence 1–5，timeSpent 0–86400 秒。选择题由服务端根据实际答案判断，申论自评。相同 requestId 不重复计数。

时政与素材：source / sourceUrl（HTTPS）/ publishTime（带时区 ISO 或 null）/ fetchTime / sourceUnverified。采集时间由服务器生成，AI 不生成或覆盖来源；来源不足强制待核实。

时政 tags 最多 6 个，支持国内时政、国际、经济、科技、教育、民生、法治、生态文明、文化、乡村振兴、基层治理、数字政府。手动收录可传 `classification: {tags: [...]}`；不传则先按关键词初分，再由 AI 完善。PATCH 保存后 tagsManual=true，空数组表示手动设为待分类。GET 的 tag=待分类查询空标签。overview 为当日全量统计，一篇新闻可以出现在多个标签中。

词语：word / kind（成语、词语）/ definition；可选 synonyms / antonyms 字符串列表，scenario / pitfalls / example。PATCH 更新 favorite / mastered。

素材分类：经济、科技、教育、基层治理、乡村振兴、生态文明、文化、社会治理、民生、数字政府。类型：案例、政策、金句、观点、数据、人物案例。tags 最多 20 个。

聊天会话和消息存于 PostgreSQL。名称自动采用首条问题的前 60 个字符，手动名称为 1–80 个字符。`turnId` 保持消息幂等，同一编号不得用于其他会话或问题；同一会话已有回复生成中时返回 409，不同会话可独立请求。失败后可用原 `turnId` 重试。删除返回 204，已删除会话返回 404；生成中的迟到回复不能恢复已删除的会话。

消息分页按 `(createdAt, id)` 排序，`before` 必须属于当前会话，响应按时间正序。完整历史保留在库中，模型请求使用当前会话最近 20 条、单条至多 12,000 字符、合计至多 48,000 字符，不包含本轮重复问题。学习资料检索与真实学习统计仍按原有规则提供。旧客户端可不传 `conversationId`，使用最近会话；新客户端应明确指定。关联错题会在后续追问中继续提供上下文。

## FastAPI

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /health | 公开进程存活 |
| GET | /ai/status | 配置状态 |
| GET | /ai/models | 内部模型列表与服务标识，不返回凭据 |
| POST | /ai/models/test | model；真实调用验证连接及 JSON 输出 |
| POST | /ai/question/analyze | question，结构化分析 |
| POST | /ai/question/embed | entityId / entityType / text / title / source / knowledgePoints |
| POST | /ai/search/similar | query / limit / entityType / excludeIds |
| POST | /ai/documents/delete | entityId / entityType |
| POST | /ai/news/fetch | 使用服务器配置来源 |
| GET | /ai/news/sources | 内部来源目录，需内部令牌 |
| POST | /ai/current-affairs/analyze | article，不生成来源 |
| POST | /ai/weekly-report | stats，真实快照 |
| POST | /ai/chat | query / contexts / history / learningStatus / retrievalStatus |
| POST | /ai/essay-assistant | topic / contexts / retrievalStatus |

LLM 输出和向量维度必须通过校验。Java 再次确认引用对应本次检索的真实数据库记录；金句必须在本次原始资料中出现。

快捷选模保存在 PostgreSQL `ai_model_settings`。切换需要 JWT，模型列表来自已配置服务的 `/models`。新模型通过实际调用验证后再原子保存；验证失败保留原选择，revision 冲突返回 409。Java 在后续对话、题目/时政解析、申论助手与周报请求中发送内部选模头，AI 为每次请求复制配置；已开始的请求保留原模型。内部选模头同时校验服务标识，切换模型服务地址后使用该服务的默认模型。Embedding 按独立服务器配置运行。

## 错误、队列与健康

Java 错误返回 code / message / requestId，可关联安全日志：400 输入无效，401 认证无效，409 冲突，429 尝试过多，503 依赖不可用。FastAPI 无效模型 JSON 或结构返回 502，由 Java 转为明确的 AI 错误。

队列状态 PENDING → PROCESSING → COMPLETED / FAILED。原子领取、租约恢复、有限重试和失败后手动重试。题目、新闻和周报按版本检查，旧任务不覆盖新版本。队列使用 PostgreSQL，Redis 管理认证会话；AI 或 Redis 失败时核心 CRUD 继续工作。

任务列表及详情返回 progressDone / progressTotal / progressLabel。progressTotal=null 表示尚无可计数总量，处理中显示不定进度；原文保存阶段按实际文章数更新，完成后进度达到总量。重试清除旧进度和结果。NEWS_FETCH 的 result 包括 received、stored（实际新增）、duplicates、failedFeeds 和各来源 sources（status / articles / errorCode）；部分来源失败时仍保存成功取得的内容。

Java /api/health 为公开存活。/actuator/health 需 JWT，反映数据库与 Redis 状态。Nginx /ai/health 映射 FastAPI 公开存活。
