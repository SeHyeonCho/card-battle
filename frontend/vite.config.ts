import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { defineConfig } from 'vite'

// 개발 중에는 Vite(5173)가 /api, /ws 요청을 Spring 서버(8080)로 넘겨준다.
// 그래서 프론트 코드는 항상 같은 주소(상대 경로)로만 요청하면 된다.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
      '/ws': { target: 'ws://localhost:8080', ws: true },
    },
  },
})
