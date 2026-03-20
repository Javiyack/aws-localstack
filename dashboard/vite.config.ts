import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { '@': new URL('./src', import.meta.url).pathname }
  },
  server: {
    port: 5173,
    proxy: {
      '/localstack': {
        target:       'http://localhost:4566',
        rewrite:      (p) => p.replace(/^\/localstack/, ''),
        changeOrigin: true
      }
    }
  }
})
