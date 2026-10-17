<script setup lang="ts">
import { NIcon } from 'naive-ui'
import { CheckmarkCircle, CloseCircle } from '@vicons/ionicons5'
import type { OptionView } from '@/types'
import { optionLetter } from '@/utils/format'

/**
 * Variantlar ro'yxati. correctOptionId berilsa (javob ochilgan) – to'g'ri variant yashil,
 * tanlangan noto'g'ri variant qizil bilan belgilanadi.
 */
const props = defineProps<{
  options: OptionView[]
  selectedId: number | null
  correctId: number | null
  disabled?: boolean
  /** Javob hozirgina ochildi – to'g'ri variant "sakraydi", xato variant "silkinadi" */
  animate?: boolean
}>()
const emit = defineEmits<{ select: [id: number] }>()

function state(id: number): 'correct' | 'wrong' | 'selected' | '' {
  if (props.correctId != null) {
    if (id === props.correctId) return 'correct'
    if (id === props.selectedId) return 'wrong'
    return ''
  }
  return id === props.selectedId ? 'selected' : ''
}
</script>

<template>
  <div class="options" role="radiogroup">
    <button
      v-for="(o, i) in options"
      :key="o.id"
      type="button"
      role="radio"
      :aria-checked="o.id === selectedId"
      class="option"
      :class="[state(o.id), { disabled, animate }]"
      :disabled="disabled"
      @click="emit('select', o.id)"
    >
      <span class="letter">{{ optionLetter(i) }}</span>
      <span class="text pre-wrap">{{ o.text }}</span>
      <NIcon v-if="state(o.id) === 'correct'" :component="CheckmarkCircle" size="22" color="var(--correct-border)" />
      <NIcon v-else-if="state(o.id) === 'wrong'" :component="CloseCircle" size="22" color="var(--wrong-border)" />
    </button>
  </div>
</template>

<style scoped>
.options {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.option {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  text-align: left;
  padding: 12px 14px;
  font: inherit;
  font-size: 15px;
  color: inherit;
  background: transparent;
  border: 1.5px solid rgba(127, 127, 127, 0.25);
  border-radius: 10px;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}
.option:hover:not(.disabled) {
  border-color: var(--n-primary, #0d9488);
  background: rgba(13, 148, 136, 0.06);
}
.option:focus-visible {
  outline: 2px solid #0d9488;
  outline-offset: 2px;
}
.option.disabled {
  cursor: default;
}
.option.selected {
  border-color: #0d9488;
  background: rgba(13, 148, 136, 0.1);
}
.option.correct {
  border-color: var(--correct-border);
  background: var(--correct-bg);
}
.option.wrong {
  border-color: var(--wrong-border);
  background: var(--wrong-bg);
}
.animate.correct {
  animation: option-pop 0.4s ease-out;
}
.animate.wrong {
  animation: option-shake 0.4s ease-in-out;
}
@keyframes option-pop {
  40% {
    transform: scale(1.025);
  }
}
@keyframes option-shake {
  20%,
  60% {
    transform: translateX(-6px);
  }
  40%,
  80% {
    transform: translateX(6px);
  }
}
@media (prefers-reduced-motion: reduce) {
  .animate.correct,
  .animate.wrong {
    animation: none;
  }
}
.letter {
  flex: none;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 13px;
  background: rgba(127, 127, 127, 0.12);
}
.selected .letter {
  background: #0d9488;
  color: #fff;
}
.correct .letter {
  background: var(--correct-border);
  color: #fff;
}
.wrong .letter {
  background: var(--wrong-border);
  color: #fff;
}
.text {
  flex: 1;
}
</style>
