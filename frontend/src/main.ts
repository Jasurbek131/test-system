import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { setUnauthorizedHandler } from './api/http'
import { useAuthStore } from './stores/auth'
import './styles.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)

setUnauthorizedHandler(() => {
  useAuthStore().logout()
  const current = router.currentRoute.value
  if (!current.meta.public) {
    router.replace({ path: '/login', query: { redirect: current.fullPath } })
  }
})

app.mount('#app')
