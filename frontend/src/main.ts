import { createApp } from 'vue'
import { createPinia } from 'pinia'
import PrimeVue from 'primevue/config'
import App from './App.vue'
import router from './router'
import preset from './theme/preset'
import { installAuthInterceptors } from './stores/auth'
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

// The router's own beforeEach guard awaits authStore.bootstrap() on the
// first navigation (see router/index.ts) — mounting here doesn't need to
// wait for it too, and doing so would risk two concurrent bootstrap()
// calls both trying to consume/rotate the same refresh cookie.
app.mount('#app')
