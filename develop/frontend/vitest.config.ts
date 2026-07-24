import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

// Standalone config for the test runner. Unit/component tests mock the API
// layer directly and don't need the dev server proxy from vite.config.ts.
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: false,
    setupFiles: ['./src/test/setup.ts'],
    css: true,
  },
})
