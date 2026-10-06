# 时政来源、原文与标签

每日时政从服务器上的实际来源抓取，保留来源名称、原始 HTTPS 链接、发布时间和采集时间。“查看原文”在新标签页打开来源网站；“查看收录内容”阅读已经保存的文字，RSS 可能只提供摘要。

## 来源配置

在服务器 `.env` 中配置，RSS 与网站合计最多 12 个来源：

```dotenv
NEWS_FEEDS='https://www.chinanews.com.cn/rss/china.xml,https://www.chinanews.com.cn/rss/world.xml,https://www.chinanews.com.cn/rss/finance.xml'
NEWS_SITES='https://www.news.cn/politics/,https://news.cnr.cn/native/gd/,https://www.gov.cn/yaowen/liebiao/'
NEWS_ALLOWED_HOSTS='www.chinanews.com.cn,www.news.cn,news.cn,news.cnr.cn,www.cnr.cn,www.gov.cn,gov.cn'
NEWS_LOOKBACK_DAYS='7'
NEWS_MAX_ARTICLES='10'
```

- 中国新闻网的时政、国际和财经来自其[官方 RSS 目录](https://www.chinanews.com.cn/rss/)。
- [新华网时政](https://www.news.cn/politics/)和[央广网国内滚动](https://news.cnr.cn/native/gd/)解析实际文章链接及正文。
- [中国政府网要闻](https://www.gov.cn/yaowen/liebiao/)使用该页的官方 JSON 列表，再下载文章正文。

网页适配只支持上述网站结构，其他网站需新增对应解析器；RSS / Atom 可以使用其他实际来源，但列表和文章的域名都必须配置。

系统并发限制为 4，各来源轮流取文；单次返回最多 NEWS_MAX_ARTICLES 篇。请求按 AI_TIMEOUT_SECONDS 预留抓取预算，慢来源不会占用全部时间。只收录近期文章；不能确定发布时间时保留待核实标记。URL 去重由 PostgreSQL 唯一约束保证，重复抓取不会重复写入或创建素材。

自动跳转关闭；服务手动处理最多 3 次跳转，每一跳先检查允许域名、HTTPS 和公网 DNS。HTTP 降级、私有地址、越界域名、过大响应和跳转循环均拒绝。正文提取忽略脚本、导航与页脚；网页结构改变时显示来源失败，不用生成文字替代原文。

修改 `.env` 后重新创建 AI 容器；使用私有出网转发时也重新创建 egress-relay，使新增允许域名生效：

```bash
docker compose --profile app --profile egress up -d --no-build --force-recreate ai-service egress-relay
```

## 分类与筛选

时政支持 12 个固定主题：国内时政、国际、经济、科技、教育、民生、法治、生态文明、文化、乡村振兴、基层治理、数字政府。每篇最多 6 个，未明确分类时显示待分类。

抓取后按原文关键词初分，AI 整理根据原文主题完善标签。点击“编辑标签”可修正；手动保存后，AI 保留用户分类。手动收录时也可选择标签，全部留空则自动分类。

日报支持日期、主题、来源及关键词筛选；筛选查询最多返回 200 篇，统计图使用该日全量记录。一篇新闻可能属于多个主题，因此标签数量之和可能大于文章总数。摘要、事实、考点和申论提取均来自已经收录的原文。

## 进度和可视化

“最近抓取”显示实际新增、去重数量及各来源结果；来源读取或模型响应时没有准确百分比，显示处理中。保存原文阶段根据实际总量更新进度，随后独立执行 AI 整理与索引。

日报显示主题分布、来源分布和 AI 整理进度。全系统使用相同图标、指标卡、目标环和进度条；学习趋势、掌握度、正确率、复习完成率和任务状态来自实际数据库记录。无练习记录的知识点显示待练习。
