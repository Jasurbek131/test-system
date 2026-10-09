<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  NAlert,
  NButton,
  NCard,
  NProgress,
  NSkeleton,
  NTag,
  useDialog,
  useMessage,
  useThemeVars,
} from 'naive-ui'
import OptionList from '@/components/OptionList.vue'
import { testApi } from '@/api'
import { errorMessage } from '@/api/http'
import type { QuestionView, SessionView } from '@/types'
import { difficultyLabel, difficultyType, formatDuration, modeLabel, topicSummary } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const dialog = useDialog()
const themeVars = useThemeVars()

const id = Number(route.params.id)
const session = ref<SessionView | null>(null)
const index = ref(0)
const submitting = ref(false)
const finishing = ref(false)
const now = ref(Date.now())
let timer: ReturnType<typeof setInterval> | undefined

const isLearning = computed(() => session.value?.mode === 'LEARNING')
const questions = computed(() => session.value?.questions ?? [])
const current = computed<QuestionView | undefined>(() => questions.value[index.value])
const answeredCount = computed(() => questions.value.filter((q) => q.answered).length)
const correctSoFar = computed(() => questions.value.filter((q) => q.correct === true).length)
const progress = computed(() =>
  questions.value.length ? Math.round((answeredCount.value / questions.value.length) * 100) : 0,
)
const elapsed = computed(() =>
  session.value ? Math.max(0, Math.floor((now.value - new Date(session.value.startedAt).getTime()) / 1000)) : 0,
)
const locked = computed(() => !!current.value && isLearning.value && current.value.answered)

onMounted(async () => {
  try {
    const s = await testApi.get(id)
    if (s.status === 'FINISHED') {
      router.replace(`/test/${id}/result`)
      return
    }
    session.value = s
    // birinchi javob berilmagan savoldan davom ettiramiz
    const firstOpen = s.questions.findIndex((q) => !q.answered)
    index.value = firstOpen === -1 ? 0 : firstOpen
  } catch (e) {
    message.error(errorMessage(e))
    router.replace('/history')
  }
  timer = setInterval(() => (now.value = Date.now()), 1000)
  window.addEventListener('keydown', onKey)
})

onBeforeUnmount(() => {
  clearInterval(timer)
  window.removeEventListener('keydown', onKey)
})

async function select(optionId: number) {
  const q = current.value
  if (!q || locked.value || submitting.value) return
  if (q.selectedOptionId === optionId && q.answered) return
  const previous = q.selectedOptionId
  q.selectedOptionId = optionId // darhol ko'rsatamiz
  submitting.value = true
  try {
    const res = await testApi.answer(id, q.id, optionId)
    Object.assign(q, {
      selectedOptionId: res.selectedOptionId,
      answered: true,
      correct: res.correct,
      correctOptionId: res.correctOptionId,
      explanation: res.explanation,
    })
  } catch (e) {
    q.selectedOptionId = previous
    message.error(errorMessage(e))
  } finally {
    submitting.value = false
  }
}

function go(i: number) {
  if (i >= 0 && i < questions.value.length) index.value = i
}

function nextUnanswered() {
  const n = questions.value.length
  for (let step = 1; step <= n; step++) {
    const i = (index.value + step) % n
    if (!questions.value[i].answered) return go(i)
  }
  go(index.value + 1)
}

function onKey(e: KeyboardEvent) {
  if (!current.value || e.ctrlKey || e.metaKey || e.altKey) return
  const target = e.target as HTMLElement | null
  if (target && ['INPUT', 'TEXTAREA'].includes(target.tagName)) return
  if (e.key === 'ArrowRight') go(index.value + 1)
  else if (e.key === 'ArrowLeft') go(index.value - 1)
  else {
    const i = '1234567890'.indexOf(e.key) !== -1 ? '1234567890'.indexOf(e.key) : 'abcdef'.indexOf(e.key.toLowerCase())
    const opt = current.value.options[i]
    if (opt) select(opt.id)
  }
}

function navState(q: QuestionView): string {
  if (!q.answered) return ''
  if (q.correct === true) return 'correct'
  if (q.correct === false) return 'wrong'
  return 'answered'
}

function confirmFinish() {
  const unanswered = questions.value.length - answeredCount.value
  dialog.warning({
    title: 'Testni yakunlaysizmi?',
    content: unanswered
      ? `${unanswered} ta savolga javob berilmagan. Ular xato hisoblanadi.`
      : 'Barcha savollarga javob berdingiz.',
    positiveText: 'Yakunlash',
    negativeText: 'Davom etish',
    onPositiveClick: finish,
  })
}

async function finish() {
  finishing.value = true
  try {
    await testApi.finish(id)
    router.replace(`/test/${id}/result`)
  } catch (e) {
    message.error(errorMessage(e))
  } finally {
    finishing.value = false
  }
}
</script>

<template>
  <div class="page">
    <NCard v-if="!session"><NSkeleton text :repeat="8" /></NCard>

    <template v-else>
      <div class="page-header">
        <div>
          <h1>{{ session.subjectName }}</h1>
          <div class="muted">
            {{ modeLabel[session.mode] }} rejimi · {{ topicSummary(session.topics.map((t) => t.name)) }}
          </div>
        </div>
        <div class="header-stats tabular">
          <span class="muted">⏱ {{ formatDuration(elapsed) }}</span>
          <NButton type="primary" :loading="finishing" @click="confirmFinish">Testni yakunlash</NButton>
        </div>
      </div>

      <div class="layout">
        <div class="main">
          <NCard size="small" style="margin-bottom: 12px">
            <div class="progress-row tabular">
              <span>Javob berildi: {{ answeredCount }} / {{ questions.length }}</span>
              <span v-if="isLearning" class="muted">To'g'ri: {{ correctSoFar }}</span>
            </div>
            <NProgress type="line" :percentage="progress" :color="themeVars.primaryColor" :show-indicator="false" :height="8" />
          </NCard>

          <NCard v-if="current">
            <div class="q-meta">
              <span class="q-number tabular">{{ index + 1 }} / {{ questions.length }}</span>
              <NTag size="small" :bordered="false">{{ current.topicName }}</NTag>
              <NTag size="small" :bordered="false" :type="difficultyType[current.difficulty]">
                {{ difficultyLabel[current.difficulty] }}
              </NTag>
            </div>
            <div class="q-text pre-wrap">{{ current.text }}</div>

            <OptionList
              :options="current.options"
              :selected-id="current.selectedOptionId"
              :correct-id="current.correctOptionId"
              :disabled="locked || submitting"
              @select="select"
            />

            <NAlert
              v-if="isLearning && current.answered && current.correct !== null"
              :type="current.correct ? 'success' : 'error'"
              :title="current.correct ? 'To\'g\'ri!' : 'Noto\'g\'ri'"
              class="feedback"
            >
              <div v-if="current.explanation" class="pre-wrap">{{ current.explanation }}</div>
              <div v-else class="muted">Izoh kiritilmagan</div>
            </NAlert>

            <div class="q-actions">
              <NButton :disabled="index === 0" @click="go(index - 1)">← Oldingi</NButton>
              <NButton v-if="answeredCount === questions.length" type="primary" @click="confirmFinish">
                Yakunlash
              </NButton>
              <NButton
                v-else-if="isLearning && current.answered"
                type="primary"
                @click="nextUnanswered"
              >
                Keyingi savol →
              </NButton>
              <NButton v-else :disabled="index === questions.length - 1" @click="go(index + 1)">
                Keyingi →
              </NButton>
            </div>
            <div class="hint muted">Klaviatura: 1–6 variant tanlash, ← → savollar orasida o'tish</div>
          </NCard>
        </div>

        <aside class="side">
          <NCard size="small" title="Savollar">
            <div class="nav-grid">
              <button
                v-for="(q, i) in questions"
                :key="q.id"
                type="button"
                class="nav-btn tabular"
                :class="[navState(q), { current: i === index }]"
                :aria-label="`${i + 1}-savol`"
                @click="go(i)"
              >
                {{ i + 1 }}
              </button>
            </div>
            <div class="legend muted">
              <template v-if="isLearning">
                <span><i class="dot correct" /> to'g'ri</span>
                <span><i class="dot wrong" /> xato</span>
              </template>
              <span v-else><i class="dot answered" /> javob berilgan</span>
              <span><i class="dot" /> javobsiz</span>
            </div>
          </NCard>
        </aside>
      </div>
    </template>
  </div>
</template>

<style scoped>
.header-stats {
  display: flex;
  align-items: center;
  gap: 16px;
}
.layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 260px;
  gap: 16px;
  align-items: start;
}
.side {
  position: sticky;
  top: 76px;
}
@media (max-width: 860px) {
  .layout {
    grid-template-columns: minmax(0, 1fr);
  }
  .side {
    position: static;
    order: -1;
  }
}
.progress-row {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 14px;
}
.q-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.q-number {
  font-weight: 600;
  margin-right: 4px;
}
.q-text {
  font-size: 18px;
  line-height: 1.5;
  font-weight: 500;
  margin-bottom: 18px;
}
.feedback {
  margin-top: 16px;
}
.q-actions {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-top: 20px;
}
.hint {
  font-size: 12px;
  margin-top: 12px;
  text-align: center;
}
@media (max-width: 600px) {
  .hint {
    display: none;
  }
}
.nav-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(38px, 1fr));
  gap: 6px;
}
.nav-btn {
  height: 36px;
  border-radius: 8px;
  font: inherit;
  font-size: 13px;
  font-weight: 500;
  color: inherit;
  border: 1.5px solid rgba(127, 127, 127, 0.25);
  background: transparent;
  cursor: pointer;
}
.nav-btn.answered {
  background: rgba(13, 148, 136, 0.18);
  border-color: transparent;
}
.nav-btn.correct {
  background: var(--correct-bg);
  border-color: var(--correct-border);
}
.nav-btn.wrong {
  background: var(--wrong-bg);
  border-color: var(--wrong-border);
}
.nav-btn.current {
  outline: 2px solid #0d9488;
  outline-offset: 1px;
}
.legend {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  font-size: 12px;
  margin-top: 12px;
}
.dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 3px;
  border: 1.5px solid rgba(127, 127, 127, 0.35);
  vertical-align: -1px;
}
.dot.answered {
  background: rgba(13, 148, 136, 0.35);
  border-color: transparent;
}
.dot.correct {
  background: var(--correct-border);
  border-color: transparent;
}
.dot.wrong {
  background: var(--wrong-border);
  border-color: transparent;
}
</style>
