import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  plugins: [vue()],
  test: {
    environment: 'jsdom',
    clearMocks: true,
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html'],
      include: ['src/**/*.{ts,vue}'],
      exclude: [
        'src/**/*.d.ts',
        'src/**/*.test.ts',
        'src/mock-data/**',
        // 目前未被應用程式引用，且內容為舊版／整段註解的範例頁面
        'src/components/HelloWorld.vue',
        'src/views/CardCreate.vue',
        'src/views/Home.vue',
      ],
    },
  },
})
