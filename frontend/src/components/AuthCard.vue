<script setup lang="ts">
import { NButton, NCard, NIcon } from 'naive-ui'
import { MoonOutline, SunnyOutline } from '@vicons/ionicons5'
import { useThemeStore } from '@/stores/theme'

defineProps<{ title: string; subtitle?: string }>()
const theme = useThemeStore()
</script>

<template>
  <div class="auth-page">
    <NButton quaternary circle class="theme-btn" aria-label="Mavzu" @click="theme.toggle">
      <template #icon><NIcon :component="theme.dark ? SunnyOutline : MoonOutline" /></template>
    </NButton>
    <div class="auth-box">
      <div class="brand">
        <img src="/favicon.svg" alt="" width="40" height="40" />
        <div>
          <div class="brand-title">Test tizimi</div>
          <div class="muted">Test yechib o'rganing</div>
        </div>
      </div>
      <NCard :title="title" size="large">
        <p v-if="subtitle" class="muted" style="margin-top: 0">{{ subtitle }}</p>
        <slot />
      </NCard>
    </div>
  </div>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px 16px;
  background: var(--page-bg);
  position: relative;
}
.theme-btn {
  position: absolute;
  top: 16px;
  right: 16px;
}
.auth-box {
  width: 100%;
  max-width: 400px;
}
.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}
.brand-title {
  font-size: 20px;
  font-weight: 650;
}
</style>
