import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

// Veb-versiyaning sahifa va komponentlari (frontend/src) shu yerdan olinadi, o'zgartirilmaydi.
// Faqat server bilan ishlaydigan modullar mobil (lokal) versiyalar bilan almashtiriladi.
const web = fileURLToPath(new URL('../frontend/src', import.meta.url))
const here = fileURLToPath(new URL('./src', import.meta.url))

export default defineConfig({
  plugins: [vue()],
  base: './',
  resolve: {
    alias: [
      { find: /^@\/api$/, replacement: `${here}/api/index.ts` },
      { find: /^@\/api\/http$/, replacement: `${here}/api/errors.ts` },
      { find: /^@\/stores\/auth$/, replacement: `${here}/stores/auth.ts` },
      { find: /^@m\//, replacement: `${here}/` },
      { find: /^@\//, replacement: `${web}/` },
    ],
    // Umumiy fayllar frontend/ ichida bo'lsa ham kutubxonalarning bitta nusxasi ishlatilsin
    dedupe: ['vue', 'vue-router', 'pinia', 'naive-ui', '@vicons/ionicons5'],
  },
  server: { fs: { allow: ['..'] } },
  build: { outDir: 'dist', chunkSizeWarningLimit: 2000 },
  test: { environment: 'node' },
})
