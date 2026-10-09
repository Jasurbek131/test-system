<script setup lang="ts">
import { coverColor, initials } from '@m/utils/grade'

defineProps<{ id: number; name: string; size?: number }>()
</script>

<template>
  <div class="badge" :style="{ background: coverColor(id), width: `${size ?? 52}px`, height: `${size ?? 52}px`, fontSize: `${(size ?? 52) * 0.42}px` }" aria-hidden="true">
    {{ initials(name) }}
  </div>
</template>

<style scoped>
.badge {
  flex: none;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-family: var(--display);
  font-weight: 700;
  /* daftar muqovasining tikuv chizig'i */
  box-shadow: inset 5px 0 0 rgba(0, 0, 0, 0.14);
}
</style>
