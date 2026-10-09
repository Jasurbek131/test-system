<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NIcon, useDialog, useMessage } from 'naive-ui'
import { CheckmarkCircle, ChevronBack, ChevronDown, Close, CloseCircle } from '@vicons/ionicons5'
import { testApi } from '@/api'
import { errorMessage } from '@/api/http'
import type { QuestionView, SessionView } from '@/types'
import { difficultyLabel, formatDuration, optionLetter } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const dialog = useDialog()
const id = Number(route.params.id)

const session = ref<SessionView | null>(null)
const index = ref(0)
const busy = ref(false)
const sheet = ref(false)
const direction = ref<'next' | 'prev'>('next')
const now = ref(Date.now())
const feedbackEl = ref<HTMLElement | null>(null)
let timer: ReturnType<typeof setInterval> | undefined

const learning = computed(() => session.value?.mode === 'LEARNING')
const qs = computed(() => session.value?.questions ?? [])
const q = computed<QuestionView | undefined>(() => qs.value[index.value])
const answered = computed(() => qs.value.filter((x) => x.answered).length)
const allAnswered = computed(() => qs.value.length > 0 && answered.value === qs.value.length)
const locked = computed(() => !!q.value && learning.value && q.value.answered)
const revealed = computed(() => !!q.value && learning.value && q.value.answered && q.value.correct !== null)
const elapsed = computed(() =>
  session.value ? Math.max(0, Math.floor((now.value - new Date(session.value.startedAt).getTime()) / 1000)) : 0,
)
const isLast = computed(() => index.value === qs.value.length - 1)

onMounted(async () => {
  try {
    const s = await testApi.get(id)
    if (s.status === 'FINISHED') return router.replace(`/test/${id}/result`)
    session.value = s
    const open = s.questions.findIndex((x) => !x.answered)
    index.value = open === -1 ? 0 : open
  } catch (e) {
    message.error(errorMessage(e))
    router.replace('/')
  }
  timer = setInterval(() => (now.value = Date.now()), 1000)
})
onBeforeUnmount(() => clearInterval(timer))

function go(i: number) {
  if (i < 0 || i >= qs.value.length || i === index.value) return
  direction.value = i > index.value ? 'next' : 'prev'
  index.value = i
  window.scrollTo({ top: 0 })
}

function nextOpen() {
  const n = qs.value.length
  for (let step = 1; step <= n; step++) {
    const i = (index.value + step) % n
    if (!qs.value[i].answered) return go(i)
  }
  go(index.value + 1)
}

async function choose(optionId: number) {
  const cur = q.value
  if (!cur || locked.value || busy.value) return
  if (cur.answered && cur.selectedOptionId === optionId) return
  const prev = cur.selectedOptionId
  cur.selectedOptionId = optionId
  busy.value = true
  try {
    const res = await testApi.answer(id, cur.id, optionId)
    Object.assign(cur, { answered: true, correct: res.correct, correctOptionId: res.correctOptionId, explanation: res.explanation })
    if (learning.value) {
      await nextTick()
      feedbackEl.value?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
    }
  } catch (e) {
    cur.selectedOptionId = prev
    message.error(errorMessage(e))
  } finally {
    busy.value = false
  }
}

function optionState(oid: number): string {
  const cur = q.value!
  if (cur.correctOptionId !== null) {
    if (oid === cur.correctOptionId) return 'ok'
    if (oid === cur.selectedOptionId) return 'bad'
    return 'dim'
  }
  return oid === cur.selectedOptionId ? 'picked' : ''
}

function cellState(x: QuestionView): string {
  if (!x.answered) return ''
  if (x.correct === true) return 'ok'
  if (x.correct === false) return 'bad'
  return 'done'
}

function primary() {
  if (allAnswered.value) return askFinish()
  if (learning.value && !q.value?.answered) return nextOpen()
  if (isLast.value) return askFinish()
  return learning.value ? nextOpen() : go(index.value + 1)
}
const primaryLabel = computed(() => {
  if (allAnswered.value) return 'Yakunlash'
  if (learning.value) return q.value?.answered ? 'Keyingi savol' : "O'tkazib yuborish"
  return isLast.value ? 'Yakunlash' : 'Keyingi'
})
const primaryClass = computed(() => (learning.value && !q.value?.answered && !allAnswered.value ? 'btn-secondary' : 'btn-primary'))

function askFinish() {
  const left = qs.value.length - answered.value
  dialog.warning({
    title: 'Testni yakunlaysizmi?',
    content: left ? `${left} ta savolga javob berilmagan. Ular xato hisoblanadi.` : 'Hamma savollarga javob berdingiz.',
    positiveText: 'Yakunlash',
    negativeText: 'Qaytish',
    onPositiveClick: finish,
  })
}
async function finish() {
  try {
    await testApi.finish(id)
    router.replace(`/test/${id}/result`)
  } catch (e) {
    message.error(errorMessage(e))
  }
}
function askExit() {
  dialog.info({
    title: 'Testdan chiqasizmi?',
    content: "Javoblaringiz saqlanadi. Testni bosh sahifadan davom ettirishingiz mumkin.",
    positiveText: 'Chiqish',
    negativeText: 'Davom etish',
    onPositiveClick: () => {
      router.replace('/')
    },
  })
}

// Chapga/o'ngga surib savol almashtirish
let sx = 0
let sy = 0
function touchStart(e: TouchEvent) {
  sx = e.touches[0].clientX
  sy = e.touches[0].clientY
}
function touchEnd(e: TouchEvent) {
  const dx = e.changedTouches[0].clientX - sx
  const dy = e.changedTouches[0].clientY - sy
  if (Math.abs(dx) > 70 && Math.abs(dx) > Math.abs(dy) * 1.5) go(index.value + (dx < 0 ? 1 : -1))
}
</script>

<template>
  <div class="test grid-paper">
    <header class="top">
      <button class="icon" aria-label="Testdan chiqish" @click="askExit"><NIcon :component="Close" size="24" /></button>
      <button class="counter tabular" :disabled="!session" @click="sheet = true">
        {{ index + 1 }} / {{ qs.length || '–' }}
        <NIcon :component="ChevronDown" size="16" />
      </button>
      <div class="time tabular muted">{{ formatDuration(elapsed) }}</div>
    </header>
    <div class="progress" role="progressbar" :aria-valuenow="answered" :aria-valuemax="qs.length">
      <div class="progress-fill" :style="{ width: qs.length ? `${(answered / qs.length) * 100}%` : '0' }" />
    </div>

    <main v-if="q" class="body" @touchstart.passive="touchStart" @touchend.passive="touchEnd">
      <Transition :name="direction" mode="out-in">
        <section :key="q.id" class="question">
          <div class="meta">
            <span class="topic">{{ q.topicName.replace(/^\d+\.\s*/, '') }}</span>
            <span class="level muted">{{ difficultyLabel[q.difficulty] }}</span>
          </div>
          <h2 class="text">{{ q.text }}</h2>

          <div class="options" role="radiogroup">
            <button
              v-for="(o, i) in q.options"
              :key="o.id"
              class="option"
              :class="optionState(o.id)"
              role="radio"
              :aria-checked="o.id === q.selectedOptionId"
              :disabled="locked || busy"
              @click="choose(o.id)"
            >
              <span class="letter">{{ optionLetter(i) }}</span>
              <span class="option-text">{{ o.text }}</span>
              <NIcon v-if="optionState(o.id) === 'ok'" :component="CheckmarkCircle" size="24" class="mark-ok" />
              <NIcon v-else-if="optionState(o.id) === 'bad'" :component="CloseCircle" size="24" class="mark-bad" />
            </button>
          </div>

          <div v-if="revealed" ref="feedbackEl" class="feedback" :class="q.correct ? 'ok' : 'bad'">
            <div class="feedback-title">{{ q.correct ? "To'g'ri!" : "Noto'g'ri" }}</div>
            <div v-if="q.explanation" class="feedback-text">{{ q.explanation }}</div>
          </div>
          <p v-else-if="!learning && q.answered" class="muted hint">Javob saqlandi. Yakunlaguncha o'zgartirishingiz mumkin.</p>
        </section>
      </Transition>
    </main>

    <div class="bottom-bar">
      <div class="bottom-bar-inner">
        <button class="btn btn-secondary nav" aria-label="Oldingi savol" :disabled="index === 0" @click="go(index - 1)">
          <NIcon :component="ChevronBack" size="22" />
        </button>
        <button class="btn btn-block" :class="primaryClass" :disabled="!q" @click="primary">{{ primaryLabel }}</button>
      </div>
    </div>

    <Transition name="sheet">
      <div v-if="sheet" class="sheet-backdrop" @click.self="sheet = false">
        <div class="sheet-panel" role="dialog" aria-label="Savollar">
          <div class="sheet-grip" />
          <div class="sheet-head">
            <span class="sheet-title">Savollar</span>
            <span class="muted tabular">{{ answered }}/{{ qs.length }} javob</span>
          </div>
          <div class="cells">
            <button
              v-for="(x, i) in qs"
              :key="x.id"
              class="cell tabular"
              :class="[cellState(x), { cur: i === index }]"
              @click="go(i); sheet = false"
            >
              {{ i + 1 }}
            </button>
          </div>
          <div class="legend muted">
            <template v-if="learning">
              <span><i class="dot ok" />to'g'ri</span>
              <span><i class="dot bad" />xato</span>
            </template>
            <span v-else><i class="dot done" />javob berilgan</span>
            <span><i class="dot" />javobsiz</span>
          </div>
          <button class="btn btn-primary btn-block" style="margin-top: 18px" @click="sheet = false; askFinish()">Testni yakunlash</button>
        </div>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.test {
  min-height: 100vh;
}
.top {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
  padding: 0 8px;
  background: var(--paper);
}
.icon {
  width: 44px;
  height: 44px;
  border: 0;
  border-radius: 12px;
  background: transparent;
  color: var(--ink);
  display: flex;
  align-items: center;
  justify-content: center;
}
.counter {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 36px;
  padding: 0 14px;
  border-radius: 18px;
  border: 0;
  background: var(--card);
  box-shadow: var(--shadow);
  font-family: var(--display);
  font-weight: 600;
  font-size: 15px;
  color: var(--ink);
}
.time {
  width: 72px;
  text-align: right;
  padding-right: 8px;
  font-size: 14px;
}
.progress {
  height: 4px;
  background: var(--line);
  position: sticky;
  top: 56px;
  z-index: 10;
}
.progress-fill {
  height: 100%;
  background: var(--marker);
  transition: width 0.3s ease;
}
.body {
  max-width: 640px;
  margin: 0 auto;
  padding: 20px 16px calc(110px + var(--safe-bottom));
  overflow-x: hidden;
}
.meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  font-size: 13px;
}
.topic {
  font-weight: 600;
  color: var(--ink);
  /* marker bilan belgilangandek */
  background: linear-gradient(transparent 45%, var(--marker-soft) 45%);
  padding: 0 2px;
  max-width: 75%;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.text {
  font-size: 21px;
  line-height: 1.4;
  font-weight: 600;
  margin: 0 0 22px;
  white-space: pre-wrap;
  word-break: break-word;
}
.options {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.option {
  display: flex;
  align-items: center;
  gap: 14px;
  min-height: 60px;
  padding: 12px 14px;
  border: 0;
  border-radius: 16px;
  background: var(--card);
  box-shadow: var(--shadow), inset 0 0 0 1.5px var(--line);
  text-align: left;
  font-size: 17px;
  cursor: pointer;
  transition: box-shadow 0.15s, background 0.15s, transform 0.08s;
}
.option:not(:disabled):active {
  transform: scale(0.99);
}
.option:disabled {
  cursor: default;
}
.letter {
  flex: none;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--display);
  font-weight: 600;
  font-size: 14px;
  background: var(--grid);
  color: var(--ink);
}
.option-text {
  flex: 1;
  line-height: 1.35;
  word-break: break-word;
}
.option.picked {
  background: var(--marker-soft);
  box-shadow: inset 0 0 0 2px var(--ink);
}
.option.picked .letter {
  background: var(--ink);
  color: #fff;
}
.option.ok {
  background: var(--ok-soft);
  box-shadow: inset 0 0 0 2px var(--ok);
}
.option.ok .letter {
  background: var(--ok);
  color: #fff;
}
.option.bad {
  background: var(--bad-soft);
  box-shadow: inset 0 0 0 2px var(--bad);
}
.option.bad .letter {
  background: var(--bad);
  color: #fff;
}
.option.dim {
  opacity: 0.55;
}
.mark-ok {
  color: var(--ok);
}
.mark-bad {
  color: var(--bad);
}
.feedback {
  margin-top: 16px;
  padding: 14px 16px;
  border-radius: 16px;
  border-left: 5px solid;
  background: var(--card);
  box-shadow: var(--shadow);
  animation: rise 0.25s ease-out;
}
.feedback.ok {
  border-color: var(--ok);
}
.feedback.bad {
  border-color: var(--bad);
}
.feedback-title {
  font-family: var(--display);
  font-weight: 650;
  font-size: 17px;
  margin-bottom: 4px;
}
.feedback.ok .feedback-title {
  color: var(--ok);
}
.feedback.bad .feedback-title {
  color: var(--bad);
}
.feedback-text {
  font-size: 15px;
  line-height: 1.5;
}
.hint {
  margin-top: 14px;
  font-size: 14px;
  text-align: center;
}
.nav {
  flex: none;
  width: 56px;
  padding: 0;
}
.sheet-backdrop {
  position: fixed;
  inset: 0;
  z-index: 50;
  background: rgba(10, 14, 35, 0.45);
  display: flex;
  align-items: flex-end;
}
.sheet-panel {
  width: 100%;
  max-height: 80vh;
  overflow-y: auto;
  background: var(--card);
  border-radius: 22px 22px 0 0;
  padding: 10px 18px calc(18px + var(--safe-bottom));
}
.sheet-grip {
  width: 40px;
  height: 5px;
  border-radius: 3px;
  background: var(--line);
  margin: 0 auto 12px;
}
.sheet-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 14px;
}
.sheet-title {
  font-family: var(--display);
  font-weight: 650;
  font-size: 18px;
  color: var(--ink);
}
.sheet-enter-active,
.sheet-leave-active {
  transition: background 0.2s ease;
}
.sheet-enter-active .sheet-panel,
.sheet-leave-active .sheet-panel {
  transition: transform 0.22s ease;
}
.sheet-enter-from,
.sheet-leave-to {
  background: transparent;
}
.sheet-enter-from .sheet-panel,
.sheet-leave-to .sheet-panel {
  transform: translateY(100%);
}
.cells {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(46px, 1fr));
  gap: 8px;
}
.cell {
  height: 46px;
  border-radius: 12px;
  border: 1.5px solid var(--line);
  background: transparent;
  font-weight: 600;
}
.cell.done {
  background: var(--marker-soft);
  border-color: var(--marker);
}
.cell.ok {
  background: var(--ok-soft);
  border-color: var(--ok);
  color: var(--ok);
}
.cell.bad {
  background: var(--bad-soft);
  border-color: var(--bad);
  color: var(--bad);
}
.cell.cur {
  outline: 3px solid var(--ink);
  outline-offset: 1px;
}
.legend {
  display: flex;
  gap: 14px;
  flex-wrap: wrap;
  margin-top: 14px;
  font-size: 13px;
}
.dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 3px;
  margin-right: 5px;
  border: 1.5px solid var(--line);
  vertical-align: -1px;
}
.dot.ok {
  background: var(--ok);
  border-color: var(--ok);
}
.dot.bad {
  background: var(--bad);
  border-color: var(--bad);
}
.dot.done {
  background: var(--marker);
  border-color: var(--marker);
}
.next-enter-active,
.next-leave-active,
.prev-enter-active,
.prev-leave-active {
  transition: transform 0.18s ease, opacity 0.18s ease;
}
.next-enter-from,
.prev-leave-to {
  transform: translateX(24px);
  opacity: 0;
}
.next-leave-to,
.prev-enter-from {
  transform: translateX(-24px);
  opacity: 0;
}
@keyframes rise {
  from {
    transform: translateY(8px);
    opacity: 0;
  }
}
</style>
