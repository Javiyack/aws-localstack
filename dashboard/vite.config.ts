import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'path'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { '@': path.resolve(__dirname, './src') }
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
