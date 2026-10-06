import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import './style.css'
import './dashboard.css'
import './workspace.css'
import './study-pages.css'

createApp(App).use(createPinia()).use(router).mount('#app')
