<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'
import { NIcon } from 'naive-ui'
import { BarChart, BarChartOutline, Book, BookOutline, Time, TimeOutline } from '@vicons/ionicons5'

const route = useRoute()
const tabs = [
  { to: '/', label: 'Fanlar', icon: BookOutline, active: Book },
  { to: '/history', label: 'Tarix', icon: TimeOutline, active: Time },
  { to: '/stats', label: 'Natijalarim', icon: BarChartOutline, active: BarChart },
]
const current = computed(() => (route.path === '/' ? '/' : '/' + route.path.split('/')[1]))
</script>

<template>
  <div class="tabs-layout">
    <RouterView :key="route.fullPath" />
    <nav class="tabs" aria-label="Bo'limlar">
      <RouterLink v-for="t in tabs" :key="t.to" :to="t.to" replace class="tab" :class="{ on: current === t.to }">
        <span class="pill"><NIcon :component="current === t.to ? t.active : t.icon" size="22" /></span>
        <span class="label">{{ t.label }}</span>
      </RouterLink>
    </nav>
  </div>
</template>

<style scoped>
.tabs-layout {
  min-height: 100vh;
}
.tabs {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 30;
  display: flex;
  padding: 6px 8px calc(6px + var(--safe-bottom));
  background: var(--card);
  border-top: 1px solid var(--line);
}
.tab {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 4px 0;
  color: var(--muted);
  text-decoration: none;
  font-size: 12px;
  font-weight: 500;
}
.pill {
  width: 56px;
  height: 30px;
  border-radius: 15px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s;
}
.tab.on {
  color: var(--ink);
  font-weight: 650;
}
.tab.on .pill {
  background: var(--marker-soft);
}
</style>
