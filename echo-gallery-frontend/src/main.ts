import { createApp } from 'vue'
import App from './App.vue'

import ElementPlus from 'element-plus'
import zhTw from 'element-plus/es/locale/lang/zh-tw'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import './style.css'
import './assets/styles/treasure-rank.css'
import { createPinia } from 'pinia'
import router from './router/index.ts'

import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import { VueQueryPlugin } from '@tanstack/vue-query'
import { queryClient } from './utils/queryClient'
import { initializeTheme } from './utils/theme'

initializeTheme()

const app = createApp(App)

app.use(router)
app.use(createPinia())
app.use(ElementPlus, { locale: zhTw })
app.use(VueQueryPlugin, { queryClient })

// 註冊 Icon
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.mount('#app'); // 最後
