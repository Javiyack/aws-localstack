import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import path from 'path'

export default defineConfig({
  plugins: [react()],
  test: {
    globals:    true,
    environment:'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    coverage: {
      reporter:  ['text', 'html'],
      threshold: { lines: 70 }
    }
  },
  resolve: {
    alias: { '@': path.resolve(__dirname, './src') }
  }
})
