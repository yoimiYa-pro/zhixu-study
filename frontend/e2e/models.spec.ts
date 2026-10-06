import { test, expect } from '@playwright/test'
import { readFileSync } from 'node:fs'

test('模型快捷切换：成功保存、失败保留、刷新恢复和手机布局', async ({ page }) => {
  let state = { activeModel: 'chat-first', selectedModel: null as string | null, defaultModel: 'chat-first', models: ['chat-first', 'chat-second', 'chat-unavailable'], revision: 0 }
  await page.route('**/api/ai-models', async route => {
    if (route.request().method() === 'GET') return route.fulfill({ json: state })
    const input = route.request().postDataJSON()
    if (input.model === 'chat-unavailable') return route.fulfill({ status: 503, json: { message: '模型未通过连接测试，请选择其他模型' } })
    if (input.expectedRevision !== state.revision) return route.fulfill({ status: 409, json: { message: '模型设置已更新' } })
    state = { ...state, selectedModel: input.model, activeModel: input.model || state.defaultModel, revision: state.revision + 1 }
    return route.fulfill({ json: state })
  })
  await page.goto('/')
  await page.getByLabel('用户名').fill(process.env.E2E_USERNAME || 'admin')
  await page.getByLabel('密码', { exact: true }).fill(process.env.E2E_PASSWORD || readFileSync('../.local/initial-password.txt', 'utf8').trim())
  await page.getByRole('button', { name: '登录' }).click()
  const selector = page.getByLabel('AI 模型', { exact: true })
  const modelControl = page.locator('.model-switcher')
  await expect(selector).toBeEnabled()
  await selector.selectOption('chat-second')
  await expect(modelControl.getByRole('status')).toHaveText('连接测试通过，已切换')
  await expect(selector).toHaveValue('chat-second')
  await page.reload()
  await expect(selector).toHaveValue('chat-second')
  await selector.selectOption('chat-unavailable')
  await expect(modelControl.getByRole('alert')).toHaveText('模型未通过连接测试，请选择其他模型')
  await expect(selector).toHaveValue('chat-second')
  await selector.selectOption('')
  await expect(selector).toHaveValue('')
  await expect(modelControl.getByRole('status')).toHaveText('连接测试通过，已切换')
  await page.setViewportSize({ width: 390, height: 844 })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await expect(selector).toBeVisible()
})
