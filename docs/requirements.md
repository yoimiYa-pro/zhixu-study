你是一名资深 Java / Python 全栈工程师、AI Agent 工程师和 DevOps 工程师。

请从零开发一个可部署到 Ubuntu 云服务器的：

# 公考 AI 学习服务器

目标是为公务员考试备考提供一个长期使用的私人学习系统，而不是制作静态 Demo。

系统核心能力包括：

- 每日时政整理
- 公考热点知识库
- 错题管理
- AI 错题分析
- 知识点分类
- 相似题检索
- 复习计划
- 高频成语 / 词语积累
- 申论素材积累
- 学习统计
- AI 学习助手

---

# 一、总体原则

开发前：

1. 检查当前目录和已有文件。
2. 如果目录已有项目，不要直接覆盖。
3. 先理解已有结构，再修改。
4. 不使用 Mock 数据替代核心真实功能。
5. 所有 API Key、密码、Token 使用 `.env`。
6. 提供 `.env.example`。
7. 禁止把密钥提交到 Git。
8. 每完成一个阶段都运行测试。
9. 优先保证稳定、清晰、可维护。
10. 不要一次性堆砌大量复杂功能。

目标服务器：

- Ubuntu Server
- Docker
- Nginx
- Java 21+
- Python 3.12+
- Node.js 22+
- PostgreSQL
- Redis
- Qdrant

---

# 二、系统架构

采用：

Frontend
+
Spring Boot
+
FastAPI AI Service
+
PostgreSQL
+
Redis
+
Qdrant

架构：

Browser

↓

Nginx

↓

Frontend

↓

Spring Boot API

↓

PostgreSQL
Redis

↓

FastAPI AI Service

↓

LLM API
Embedding API
Qdrant

职责划分：

Spring Boot：

- 用户认证
- 题目管理
- 错题管理
- 时政管理
- 学习记录
- 复习计划
- 数据统计
- 调用 AI 服务

FastAPI：

- LLM 调用
- Embedding
- 错题分析
- 知识点提取
- 相似题搜索
- 时政 AI 整理
- 申论素材提取
- 学习总结

---

# 三、前端

使用：

- Vue 3
- TypeScript
- Vite
- Pinia
- Vue Router
- Axios
- Tailwind CSS
- ECharts

UI：

简洁、现代、深浅色模式。

不要做复杂炫技动画。

优先：

- 信息密度
- 阅读体验
- 学习效率
- 手机适配

---

# 四、首页 Dashboard

首页显示：

今日学习

包括：

- 今日时政
- 今日待复习错题
- 今日成语
- 今日申论素材
- 今日学习任务
- 当前连续学习天数

统计卡片：

今日刷题数

错题数

复习完成数

本周学习时间

知识点掌握情况

---

# 五、每日时政模块

系统支持每天自动生成：

《公考时政日报》

格式：

## 今日十大时政

每条包括：

标题

事件摘要

关键时间

关键地点

关键人物 / 机构

关键数字

政策名称

重要表述

---

然后自动提取：

### 行测 / 常识判断考点

例如：

- 政治
- 经济
- 科技
- 法律
- 文化
- 地理
- 国际

---

### 申论素材

提取：

- 背景
- 成效
- 问题
- 原因
- 对策
- 金句
- 可使用主题

---

所有内容必须记录：

source

source_url

publish_time

fetch_time

禁止 AI 编造新闻来源。

如果缺少可靠来源：

标记：

source_unverified = true

而不是生成虚假来源。

---

# 六、错题系统

支持题型：

- 常识判断
- 言语理解
- 数量关系
- 判断推理
- 资料分析
- 申论

每道题保存：

id

题目

选项

正确答案

用户答案

解析

题型

知识点

难度

错因

来源

年份

考试地区

创建时间

最近复习时间

下次复习时间

---

# 七、AI 错题分析

用户提交错题后：

Spring Boot 调用 FastAPI。

AI 返回：

题型

核心知识点

正确答案解释

错误原因

易错点

解题思路

快速判断方法

相关知识

建议复习时间

必须输出结构化 JSON。

例如：

{
  "questionType": "",
  "knowledgePoints": [],
  "difficulty": "",
  "mistakeReason": "",
  "analysis": "",
  "quickMethod": "",
  "reviewSuggestions": []
}

使用 Pydantic 校验。

禁止直接把无法验证的 LLM 输出写入数据库。

---

# 八、错因分类

系统支持：

知识盲区

理解错误

审题错误

计算错误

方法错误

粗心

时间不足

记忆错误

AI 可以推荐错因。

用户可以手动修改。

最终以用户确认结果为准。

---

# 九、知识点系统

建立 Knowledge Point：

例如：

资料分析

↓

增长

↓

增长率

↓

基期量

↓

间隔增长率

支持：

父子关系

题目与知识点关系

错题与知识点关系

学习记录

掌握度

---

# 十、向量知识库

使用：

Qdrant

存储：

题目 Embedding

题目解析 Embedding

知识点 Embedding

申论素材 Embedding

时政内容 Embedding

支持：

相似题检索

相关知识点检索

相关时政检索

申论素材检索

---

# 十一、相似题功能

打开一道错题后：

点击：

寻找相似题

FastAPI：

生成 embedding

↓

Qdrant 检索

↓

返回 Top K

展示：

相似度

题目

知识点

来源

答案

不要只通过关键词匹配。

---

# 十二、复习系统

根据：

错题时间

复习次数

正确率

题目难度

知识点掌握度

生成复习计划。

第一版使用简单间隔复习规则。

例如：

第一次：

1 天

第二次：

3 天

第三次：

7 天

第四次：

14 天

第五次：

30 天

如果复习再次做错：

降低间隔。

不要第一版直接实现复杂机器学习模型。

---

# 十三、每日复习

Dashboard：

今日待复习：

12 道

点击：

开始复习

逐题显示。

完成后记录：

correct

incorrect

skip

time_spent

confidence

更新：

next_review_at

---

# 十四、成语词语模块

系统保存：

成语

词语

释义

近义词

反义词

使用场景

易错点

示例

支持：

收藏

掌握

未掌握

每日随机推荐。

---

# 十五、申论素材库

分类：

经济

科技

教育

基层治理

乡村振兴

生态文明

文化

社会治理

民生

数字政府

支持：

案例

政策

金句

观点

数据

人物案例

每条素材支持标签。

---

# 十六、AI 申论助手

输入主题：

例如：

基层治理

AI 从：

时政库

申论素材库

知识库

检索相关内容。

生成：

背景

问题

原因

对策

案例

金句

禁止直接生成完整考试作弊答案。

主要用于：

学习

素材整理

写作训练

---

# 十七、AI 学习助手

提供 Chat 页面。

可以提问：

为什么这题选 B？

间隔增长率怎么快速算？

帮我总结今天错题。

今天应该复习什么？

最近资料分析哪里最薄弱？

AI 必须优先查询：

用户错题

知识点

学习记录

再调用 LLM。

采用 RAG。

---

# 十八、学习统计

Statistics 页面。

显示：

每日刷题数量

错题数量

正确率

复习完成率

知识点分布

错题类型分布

错因分布

7 天趋势

30 天趋势

最薄弱知识点

---

# 十九、周报

每周自动生成：

公考学习周报

包括：

本周刷题量

正确率

新增错题

复习完成情况

高频错因

薄弱知识点

本周时政重点

建议重点复习内容

AI 总结

默认：

周日 20:00

执行。

使用服务器时区：

Asia/Shanghai

允许环境变量修改。

---

# 二十、定时任务

实现 Scheduler。

任务：

每日：

05:00

生成当天学习计划。

每日：

06:00

抓取时政。

每日：

06:30

AI 整理时政。

每日：

20:00

检查待复习题目。

每周日：

20:00

生成学习周报。

时间必须配置化。

不要在代码中写死。

---

# 二十一、认证

这是私人学习系统。

第一版只支持：

单用户。

不实现：

注册。

使用：

username

password

登录。

密码保存：

BCrypt Hash。

登录：

JWT

Access Token：

30 分钟。

Refresh Token：

7 天。

Refresh Token 使用 Redis。

---

# 二十二、数据库

PostgreSQL。

主要表：

users

questions

question_options

mistakes

knowledge_points

question_knowledge_points

reviews

review_plans

study_records

current_affairs

idioms

essay_materials

weekly_reports

ai_tasks

---

# 二十三、异步 AI 任务

Embedding / AI 分析不要阻塞主要请求。

实现异步任务机制。

第一版可以使用：

FastAPI BackgroundTasks

或 Redis Queue。

例如：

上传错题

↓

立即保存 PostgreSQL

↓

返回成功

↓

异步：

AI 分析

Embedding

Qdrant 写入

↓

更新状态

状态：

PENDING

PROCESSING

COMPLETED

FAILED

---

# 二十四、AI Provider

不要绑定单一模型。

设计：

LLMProvider

接口。

支持：

OpenAI Compatible API。

环境变量：

AI_BASE_URL

AI_API_KEY

AI_MODEL

EMBEDDING_MODEL

以后可以接入：

OpenAI

DeepSeek

MiMo

Gemini

其他 OpenAI Compatible 服务。

---

# 二十五、Prompt 管理

不要把大型 Prompt 写死在业务代码中。

建立：

prompts/

例如：

question_analysis.txt

current_affairs.txt

weekly_report.txt

essay_material.txt

允许独立修改。

---

# 二十六、API

Spring Boot API 示例：

POST /api/auth/login

POST /api/auth/refresh

GET /api/dashboard

GET /api/questions

POST /api/questions

GET /api/questions/{id}

PUT /api/questions/{id}

DELETE /api/questions/{id}

GET /api/reviews/today

POST /api/reviews/{id}/complete

GET /api/current-affairs/today

GET /api/idioms/today

GET /api/statistics

GET /api/weekly-reports

---

FastAPI：

POST /ai/question/analyze

POST /ai/question/embed

POST /ai/search/similar

POST /ai/current-affairs/analyze

POST /ai/weekly-report

POST /ai/chat

---

# 二十七、异常处理

必须正确处理：

AI API 不可用

Embedding API 不可用

Qdrant 不可用

Redis 不可用

PostgreSQL 连接失败

新闻抓取失败

JSON 解析失败

LLM 输出格式错误

不能因为 AI 服务失败导致核心错题系统不可使用。

核心原则：

AI 是增强功能。

数据库 CRUD 必须可以独立工作。

---

# 二十八、日志

Spring Boot 和 FastAPI 都实现日志。

包含：

INFO

WARNING

ERROR

记录：

请求失败

AI 调用错误

任务失败

定时任务状态

禁止记录：

密码

JWT

API KEY

完整 Cookie

---

# 二十九、Docker

提供：

docker-compose.yml

运行：

PostgreSQL

Redis

Qdrant

可选：

Frontend

Spring Boot

FastAPI

但开发环境必须支持服务单独运行。

---

# 三十、Nginx

部署结构：

study.example.com

↓

Nginx

↓

Frontend

/api

↓

Spring Boot

/ai

↓

FastAPI

提供完整配置示例。

HTTPS：

Let's Encrypt / Certbot。

---

# 三十一、项目结构

建议：

civil-service-ai/

frontend/

backend-java/

ai-service/

deploy/

docs/

docker-compose.yml

.env.example

README.md

---

backend-java：

src/main/java/

controller

service

repository

entity

dto

config

security

scheduler

exception

---

ai-service：

app/

api/

services/

providers/

models/

prompts/

rag/

tasks/

core/

main.py

---

# 三十二、开发阶段

不要一次生成所有代码。

按阶段完成。

Phase 1

初始化项目结构。

Phase 2

PostgreSQL + Redis + Qdrant。

Phase 3

Spring Boot 用户认证。

Phase 4

题目 / 错题 CRUD。

Phase 5

FastAPI AI Service。

Phase 6

错题 AI 分析。

Phase 7

Embedding + Qdrant。

Phase 8

复习系统。

Phase 9

Vue Dashboard。

Phase 10

时政模块。

Phase 11

成语与申论素材。

Phase 12

统计系统。

Phase 13

AI Chat + RAG。

Phase 14

定时任务。

Phase 15

Docker / Nginx 部署。

Phase 16

测试和 README。

每一个阶段：

运行项目

运行测试

检查错误

确认通过后继续。

---

# 三十三、第一版不要做

不要实现：

复杂微服务

Kubernetes

Kafka

RabbitMQ

Neo4j

复杂推荐算法

多用户 SaaS

支付

社交系统

复杂权限系统

手机 App

第一版目标：

做出一个真正每天可以使用的个人公考 AI 学习系统。

---

# 三十四、验收标准

最终必须做到：

用户可以登录。

可以录入一道错题。

可以编辑题目。

可以记录自己的答案。

AI 可以分析错题。

系统能够提取知识点。

题目可以生成 Embedding。

可以搜索相似题。

可以每天看到待复习错题。

完成复习后自动安排下一次复习。

可以查看每日时政。

可以积累成语。

可以保存申论素材。

可以查看学习统计。

可以生成周报。

AI Chat 可以查询自己的错题和知识库。

服务重启以后数据不会丢失。

AI API 出现故障时：

核心学习功能仍然正常。

---

现在开始：

首先检查当前目录、系统环境和已有文件。

然后给出：

1. 当前环境检查结果
2. 简短系统架构
3. 最终目录结构
4. Phase 1 需要创建的文件

随后直接开始 Phase 1。

不要只停留在方案设计阶段。