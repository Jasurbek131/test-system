<script setup lang="ts">
import { computed } from 'vue'
import {
  NConfigProvider,
  NDialogProvider,
  NGlobalStyle,
  NLoadingBarProvider,
  NMessageProvider,
  darkTheme,
  type GlobalThemeOverrides,
} from 'naive-ui'
import { useThemeStore } from '@/stores/theme'

const themeStore = useThemeStore()
const theme = computed(() => (themeStore.dark ? darkTheme : null))

const lightOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: '#0f766e',
    primaryColorHover: '#0d9488',
    primaryColorPressed: '#115e59',
    primaryColorSuppl: '#0d9488',
    borderRadius: '8px',
  },
}
const darkOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: '#2dd4bf',
    primaryColorHover: '#5eead4',
    primaryColorPressed: '#14b8a6',
    primaryColorSuppl: '#5eead4',
    borderRadius: '8px',
  },
}
const overrides = computed(() => (themeStore.dark ? darkOverrides : lightOverrides))
</script>

<template>
  <NConfigProvider :theme="theme" :theme-overrides="overrides">
    <NGlobalStyle />
    <NLoadingBarProvider>
      <NDialogProvider>
        <NMessageProvider>
          <RouterView />
        </NMessageProvider>
      </NDialogProvider>
    </NLoadingBarProvider>
  </NConfigProvider>
</template>
