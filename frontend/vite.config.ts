import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '..', 'VITE_')
  return {
  plugins: [vue(), tailwindcss()],
  // Serve formula fonts as same-origin files under the production CSP.
  build: { assetsInlineLimit: 0 },
  optimizeDeps: { include: ['echarts/core', 'echarts/charts', 'echarts/components', 'echarts/renderers'] },
  server: { proxy: {
    '/api': { target: env.VITE_JAVA_PROXY_TARGET || 'http://127.0.0.1:8080' },
    '/ai/health': { target: env.VITE_AI_PROXY_TARGET || 'http://127.0.0.1:8000', rewrite: (path: string) => path.replace(/^\/ai/, '') },
  } },
  }
})
