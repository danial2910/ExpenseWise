import { createApp } from 'vue'
import { createPinia } from 'pinia'
import PrimeVue from 'primevue/config'
import App from './App.vue'
import router from './router'
import './style.css'
import 'primeicons/primeicons.css'

createApp(App).use(createPinia()).use(router).use(PrimeVue).mount('#app')
