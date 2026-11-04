import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

const apiProxyTarget = process.env.VITE_API_PROXY_TARGET || 'http://localhost:8080'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  // @yuanda-lowcode-ai/scaffold-ui 发布的是 .vue/.ts 源码：交给 plugin-vue 编译，别让 esbuild 预打包（预打包只认 JS，遇到 .vue 直接失败）
  optimizeDeps: {
    exclude: ['@yuanda-lowcode-ai/scaffold-ui'],
  },
  server: {
    port: 5173,
    open: true,
    proxy: {
      '/api': {
        target: apiProxyTarget,
        changeOrigin: true,
      },
    },
  },
})
