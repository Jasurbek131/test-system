import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { App as CapApp } from '@capacitor/app'
import App from './App.vue'
import router from './router'
import { ensureSeeded } from './api/local/seed'
import './theme.css'

async function start() {
  const bootText = document.getElementById('boot-text')
  try {
    await ensureSeeded((text) => {
      if (bootText) bootText.textContent = text
    })
  } catch (e) {
    console.error(e)
    if (bootText) bootText.textContent = "Ma'lumotlarni yuklab bo'lmadi. Ilovani qayta oching."
    return
  }
  const app = createApp(App)
  app.use(createPinia())
  app.use(router)
  app.mount('#app')
}

start()

// Android «orqaga» tugmasi: bosh sahifada ilovadan chiqadi, boshqa joyda orqaga qaytadi.
// Test va natija sahifasidan orqaga bosilsa, bosh sahifaga qaytiladi (sozlash sahifasiga qaytib,
// bexosdan yangi test boshlab yubormaslik uchun; boshlangan testni «Tarix»dan davom ettirish mumkin).
CapApp.addListener('backButton', () => {
  const route = router.currentRoute.value
  if (route.path === '/') {
    CapApp.exitApp()
  } else if (/^\/test\/\d+(\/result)?$/.test(route.path) || window.history.length <= 1) {
    router.replace('/')
  } else {
    router.back()
  }
}).catch(() => {
  // brauzerda (sinov uchun) Capacitor plagini yo'q – e'tiborsiz qoldiramiz
})
