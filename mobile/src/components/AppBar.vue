<script setup lang="ts">
import { useRouter } from 'vue-router'
import { NIcon } from 'naive-ui'
import { ArrowBack, Close } from '@vicons/ionicons5'

const props = defineProps<{ title?: string; close?: boolean; to?: string }>()
const emit = defineEmits<{ back: [] }>()
const router = useRouter()

function onBack() {
  if (props.close) return emit('back')
  if (props.to) return router.replace(props.to)
  if (window.history.state?.back) router.back()
  else router.replace('/')
}
</script>

<template>
  <header class="appbar">
    <button class="icon-btn" :aria-label="close ? 'Yopish' : 'Orqaga'" @click="onBack">
      <NIcon :component="close ? Close : ArrowBack" size="24" />
    </button>
    <div class="title">
      <slot name="title">{{ title }}</slot>
    </div>
    <div class="right"><slot name="right" /></div>
  </header>
</template>

<style scoped>
.appbar {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  gap: 6px;
  height: 58px;
  padding: 0 8px;
  background: color-mix(in srgb, var(--paper) 92%, transparent);
  backdrop-filter: blur(10px);
}
.icon-btn {
  width: 44px;
  height: 44px;
  border: 0;
  border-radius: 12px;
  background: transparent;
  color: var(--ink);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
.icon-btn:active {
  background: var(--grid);
}
.title {
  flex: 1;
  min-width: 0;
  font-weight: 650;
  font-size: 17px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.right {
  display: flex;
  align-items: center;
  min-width: 44px;
  justify-content: flex-end;
}
</style>
