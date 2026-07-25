import { createApp } from 'vue'
import { createPinia } from 'pinia'
import PrimeVue from 'primevue/config'
import App from './App.vue'
import router from './router'
import preset from './theme/preset'
import { installAuthInterceptors, useAuthStore } from './stores/auth'
import './style.css'
import 'primeicons/primeicons.css'

const app = createApp(App)

app.use(createPinia())
app.use(PrimeVue, {
  theme: {
    preset,
    options: {
      // PrimeVue injects its own `@layer` order statement before our
      // style.css even loads, so declaring the order here (not just in
      // style.css) is what actually wins the race — primevue sits between
      // Tailwind's base/reset and its utilities, so utility classes can
      // still override PrimeVue without needing `!` overrides.
      cssLayer: { name: 'primevue', order: 'tailwind-base, primevue, tailwind-components, tailwind-utilities' },
    },
  },
})

installAuthInterceptors()
app.use(router)

// Mount is delayed until bootstrap resolves so the router's first
// navigation guard already sees accurate auth state.
useAuthStore()
  .bootstrap()
  .finally(() => app.mount('#app'))
