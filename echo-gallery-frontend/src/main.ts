import { createApp } from 'vue'
import './style.css'
import App from './App.vue'

import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import { createPinia } from 'pinia'
import router from './router/index.ts'

import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import { VueQueryPlugin } from '@tanstack/vue-query'

const app = createApp(App)

app.use(router)
app.use(createPinia())
app.use(ElementPlus)
app.use(VueQueryPlugin)

// 註冊 Icon
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.mount('#app'); // 最後
