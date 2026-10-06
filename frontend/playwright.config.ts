import { defineConfig } from '@playwright/test'
export default defineConfig({ testDir: './e2e', workers: 1, reporter: 'list', timeout: 45000,
  use: { baseURL: 'http://127.0.0.1:5190', trace: 'off', screenshot: 'off' } })
