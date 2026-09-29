import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 后端地址(短横线数据库 college_token)
const BACKEND = 'http://localhost:8081'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      // 业务接口 + 静态图片全部转发到后端,规避跨域
      '/user': BACKEND,
      '/shop': BACKEND,
      '/shop-type': BACKEND,
      '/blog': BACKEND,
      '/follow': BACKEND,
      '/voucher': BACKEND,
      '/voucher-order': BACKEND,
      '/upload': BACKEND,
      '/imgs': BACKEND
    }
  }
})