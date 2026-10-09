import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

const KEY = 'ts_theme'

export const useThemeStore = defineStore('theme', () => {
  const stored = localStorage.getItem(KEY)
  const dark = ref(
    stored ? stored === 'dark' : window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false,
  )
  watch(
    dark,
    (v) => {
      localStorage.setItem(KEY, v ? 'dark' : 'light')
      document.documentElement.dataset.theme = v ? 'dark' : 'light'
    },
    { immediate: true },
  )
  const toggle = () => (dark.value = !dark.value)
  return { dark, toggle }
})
