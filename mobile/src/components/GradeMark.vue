<script setup lang="ts">
// O'qituvchining qizil ruchkadagi bahosi: qo'lda chizilgandek doira ichida
defineProps<{ grade: number; size?: number }>()
</script>

<template>
  <div class="mark" :style="{ width: `${size ?? 120}px`, height: `${size ?? 120}px` }" :aria-label="`Baho: ${grade}`" role="img">
    <svg viewBox="0 0 120 120" aria-hidden="true">
      <path
        class="ring"
        d="M62 10c27 1 49 21 48 49-1 28-22 50-51 50C31 109 10 88 11 60 12 34 32 14 58 12c9-1 19 2 27 7"
      />
    </svg>
    <span class="digit" :style="{ fontSize: `${(size ?? 120) * 0.5}px` }">{{ grade }}</span>
  </div>
</template>

<style scoped>
.mark {
  position: relative;
  flex: none;
  transform: rotate(-6deg);
}
svg {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}
.ring {
  fill: none;
  stroke: var(--teacher);
  stroke-width: 5;
  stroke-linecap: round;
  stroke-dasharray: 400;
  stroke-dashoffset: 400;
  animation: draw 0.7s 0.15s ease-out forwards;
}
.digit {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--display);
  font-weight: 700;
  color: var(--teacher);
}
@keyframes draw {
  to {
    stroke-dashoffset: 0;
  }
}
</style>
