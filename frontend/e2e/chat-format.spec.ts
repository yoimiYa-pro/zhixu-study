import { test, expect } from '@playwright/test'
import { readFileSync } from 'node:fs'
import { execFileSync, spawnSync } from 'node:child_process'
import { randomUUID } from 'node:crypto'

const answer = String.raw`## 同比增速怎么计算

先记住：**基期是上年同期**。\(r = 20\%\) 是一个增长率，\$100 和 $200 是金额。

### 一、分清比较对象

| 概念 | 比较时间 | 示例 |
| --- | --- | --- |
| 同比 | 上年同一时期 | 2026 年 3 月与 2025 年 3 月 |
| 环比 | 相邻上一时期 | 2026 年 3 月与 2026 年 2 月 |

### 二、按步骤计算

1. 找出本期和基期。
2. 用差额除以基期。
   - 单位先统一。
   - **分母不能写成本期**。

> 记忆提示：同比找去年同期，环比找相邻时期。

\[
\text{同比增速} = \frac{\text{本期} - \text{基期}}{\text{基期}} \times 100\%
\]

$$
\frac{120 - 100}{100} = 20\%
$$

---

行内代码保留原样：\`$r$\`。

~~~python
rate = (current - baseline) / baseline
literal = "$r$"
long_line = "ABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZ"
~~~

[资料链接](https://example.com/study)

<script>window.__assistantXss = 1</script>
<img src="https://tracker.invalid/pixel" onerror="window.__assistantXss = 2">
[不安全链接](javascript:window.__assistantXss=3)
[编码不安全链接](jav&#x61;script:alert%281%29)
[数据链接](data:text/html;base64,PHNjcmlwdD4=)
![追踪图片](https://tracker.invalid/pixel)
\(\href{javascript:alert(1)}{x}\)
\(\htmlStyle{position:fixed;inset:0}{x}\)
`
// Keep literal backticks in the fixture without escaping the math dollar signs.
const savedAnswer = answer.replaceAll('\\`', '`')

test('助手历史和新回复排版、公式、安全过滤及手机深色阅读', async ({ page }) => {
  const schema = process.env.E2E_SCHEMA || ''
  expect(schema).toMatch(/^e2e_[0-9]+_[0-9]+$/)
  const turn = randomUUID(), userId = randomUUID(), assistantId = randomUUID(), knowledgeId = randomUUID(), conversationId = randomUUID()
  const citations = [{ entityType: 'knowledge', entityId: knowledgeId, title: '同比增速验收知识点', source: '隔离测试资料' }]
  const literal = (value: string) => "convert_from(decode('" + Buffer.from(value).toString('base64') + "','base64'),'UTF8')"
  const sql = `INSERT INTO ${schema}.knowledge_points(id,name) VALUES ('${knowledgeId}','同比增速验收知识点');
    INSERT INTO ${schema}.chat_conversations(id,title) VALUES ('${conversationId}','同比增速排版验收');
    INSERT INTO ${schema}.chat_messages(id,conversation_id,turn_id,role,content,citations_json,created_at) VALUES
    ('${userId}','${conversationId}','${turn}','user',${literal('如何正确计算同比增速？**我的问题保持原文**')},'[]',now()-interval '2 seconds'),
    ('${assistantId}','${conversationId}','${turn}','assistant',${literal(savedAnswer)},${literal(JSON.stringify(citations))}::jsonb,now());`
  const dockerArgs = ['compose', 'exec', '-T', 'postgres', 'sh', '-c', 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -f -']
  if (spawnSync('docker', ['info'], { stdio: 'ignore' }).status === 0) execFileSync('docker', dockerArgs, { cwd: '..', input: sql, stdio: ['pipe', 'ignore', 'pipe'] })
  else execFileSync('sudo', ['-n', 'docker', ...dockerArgs], { cwd: '..', input: sql, stdio: ['pipe', 'ignore', 'pipe'] })

  const failures: string[] = [], trackers: string[] = []
  page.on('pageerror', error => failures.push(error.name))
  page.on('request', request => { if (request.url().includes('tracker.invalid')) trackers.push(request.url()) })
  await page.goto('/chat')
  await page.getByLabel('用户名').fill(process.env.E2E_USERNAME || 'admin')
  await page.getByLabel('密码', { exact: true }).fill(process.env.E2E_PASSWORD || readFileSync('../.local/initial-password.txt', 'utf8').trim())
  await page.getByRole('button', { name: '登录' }).click()
  await page.getByRole('link', { name: 'AI 学习助手', exact: true }).click()
  await page.getByRole('button', { name: '打开聊天：同比增速排版验收', exact: true }).click()
  const body = page.locator('.chat-message.assistant .markdown-content').first()
  await expect(body.getByRole('heading', { name: '同比增速怎么计算', level: 2 })).toBeVisible()
  await expect(body.locator('strong').first()).toHaveText('基期是上年同期')
  await expect(body.getByRole('table')).toBeVisible()
  await expect(body.locator('ol > li')).toHaveCount(2)
  await expect(body.locator('ul > li')).toHaveCount(2)
  await expect(body.locator('blockquote')).toContainText('同比找去年同期')
  await expect(body.locator('.math-block .katex')).toHaveCount(2)
  await expect(body.locator('math')).toHaveCount(5)
  await expect(body.locator('pre code')).toContainText('literal = "$r$"')
  await expect(body.locator('pre .katex')).toHaveCount(0)
  await expect(body.locator('p code').first()).toHaveText('$r$')
  await expect(body).toContainText('$100 和 $200 是金额')
  await expect(page.locator('.chat-message.user strong')).toHaveCount(0)
  await expect(page.locator('.chat-message.user')).toContainText('**我的问题保持原文**')
  const source = body.getByRole('link', { name: '资料链接', exact: true })
  await expect(source).toHaveAttribute('href', 'https://example.com/study')
  await expect(source).toHaveAttribute('target', '_blank')
  await expect(source).toHaveAttribute('rel', 'noopener noreferrer')
  await expect(body.locator('script,img,iframe,svg,style,[onerror],[onclick]')).toHaveCount(0)
  await expect(body.locator('a[href^="javascript:"],a[href^="data:"]')).toHaveCount(0)
  expect(await page.evaluate(() => '__assistantXss' in window)).toBe(false)
  expect(trackers).toEqual([])
  await expect(page.getByRole('link', { name: /同比增速验收知识点/ })).toHaveAttribute('href', '/knowledge')
  await page.reload()
  await expect(body.getByRole('heading', { name: '同比增速怎么计算' })).toBeVisible()
  await expect(body.locator('math')).toHaveCount(5)

  // Only the new AI response is simulated; history above comes from the isolated database.
  let releaseReply: () => void = () => {}
  const ready = new Promise<void>(resolve => { releaseReply = resolve })
  await page.route('**/api/chat', async route => {
    if (route.request().method() !== 'POST') return route.continue()
    await ready
    const input = route.request().postDataJSON()
    await route.fulfill({ json: { id: randomUUID(), conversationId: input.conversationId, createdAt: new Date().toISOString(), turnId: input.turnId, role: 'assistant', content: '## 新回答\n\n**基期是分母**。\\(r=20\\%\\)', citations: [], retrievalStatus: 'DATABASE_ONLY' } })
  })
  await page.getByLabel('学习问题').fill('再说明一下分母')
  await page.getByRole('button', { name: '发送问题', exact: true }).click()
  await expect(page.locator('.chat-thinking')).toContainText('正在查询个人资料并整理回答')
  releaseReply()
  await expect(page.getByRole('heading', { name: '新回答', level: 2 })).toBeVisible()
  await expect(page.locator('.chat-message.assistant').last().locator('strong')).toHaveText('基期是分母')
  await expect(page.locator('.chat-message.assistant').last().locator('.katex')).toHaveCount(1)

  await page.setViewportSize({ width: 390, height: 844 })
  await page.getByRole('button', { name: '切换深色', exact: true }).click()
  await expect(page.locator('html')).toHaveClass('dark')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  const table = body.locator('.markdown-table-scroll')
  expect(await table.evaluate(element => element.scrollWidth > element.clientWidth)).toBe(true)
  await table.focus()
  await page.keyboard.press('ArrowRight')
  await expect.poll(() => table.evaluate(element => element.scrollLeft)).toBeGreaterThan(0)
  expect(await body.locator('pre').evaluate(element => element.scrollWidth > element.clientWidth)).toBe(true)

  await page.route('**/api/essay-assistant', route => route.fulfill({ json: { topic: '基层治理', outline: { background: '**群众需求**是出发点。', problems: ['加强**基层协同**。'], causes: [], solutions: [], cases: [], quotes: [] }, citations: [], retrievalStatus: 'DATABASE_ONLY' } }))
  await page.getByRole('link', { name: 'AI 申论助手', exact: true }).click()
  await page.getByLabel('训练主题').fill('基层治理')
  await page.getByRole('button', { name: '整理学习提纲', exact: true }).click()
  await expect(page.locator('.markdown-content strong').first()).toHaveText('群众需求')
  await expect(page.locator('li .markdown-content strong')).toHaveText('基层协同')
  expect(failures).toEqual([])
  expect(trackers).toEqual([])
})
