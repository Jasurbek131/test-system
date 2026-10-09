<script setup lang="ts">
import { computed } from 'vue'
import { NConfigProvider, NDialogProvider, NMessageProvider, darkTheme, type GlobalThemeOverrides } from 'naive-ui'
import { useThemeStore } from '@/stores/theme'

const theme = useThemeStore()
const font = "'Onest Variable', system-ui, sans-serif"
const overrides = computed<GlobalThemeOverrides>(() => {
  const ink = theme.dark ? '#8fa6ff' : '#23336b'
  return {
    common: {
      primaryColor: ink,
      primaryColorHover: ink,
      primaryColorPressed: ink,
      primaryColorSuppl: ink,
      fontFamily: font,
      borderRadius: '12px',
      fontSize: '16px',
      fontSizeMedium: '16px',
      bodyColor: theme.dark ? '#0f1430' : '#f6f8fc',
      cardColor: theme.dark ? '#18203f' : '#ffffff',
      modalColor: theme.dark ? '#18203f' : '#ffffff',
    },
  }
})
</script>

<template>
  <NConfigProvider :theme="theme.dark ? darkTheme : null" :theme-overrides="overrides">
    <NDialogProvider>
      <NMessageProvider placement="bottom">
        <RouterView />
      </NMessageProvider>
    </NDialogProvider>
  </NConfigProvider>
</template>
