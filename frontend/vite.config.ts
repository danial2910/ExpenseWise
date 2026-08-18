import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'

const pkg = JSON.parse(readFileSync(fileURLToPath(new URL('./package.json', import.meta.url)), 'utf-8'))

export default defineConfig({
  plugins: [vue(), tailwindcss()],
  server: {
    port: 5173,
  },
  // Single source of truth for the version shown on the About screen —
  // no second hardcoded copy in AboutView.vue.
  define: {
    __APP_VERSION__: JSON.stringify(pkg.version),
  },
})
