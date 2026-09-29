import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as Icons from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import './styles/base.css'

const app = createApp(App)
// 全局注册 Element Plus 图标组件,页面里可直接用 <el-icon><Search/></el-icon>
for (const [name, comp] of Object.entries(Icons)) {
  app.component(name, comp)
}
app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })
app.mount('#app')