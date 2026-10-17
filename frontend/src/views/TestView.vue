<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
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
import {
  difficultyLabel,
  difficultyType,
  formatClock,
  formatDuration,
  isKidsSubject,
  modeLabel,
  shortSubjectName,
  topicSummary,
} from '@/utils/format'
import { isStreakMilestone, playFeedback, praise, soundOn } from '@/utils/learning'

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

// Javob darhol ochiladigan rejimlar: o'rganish va blits
const isLearning = computed(() => session.value?.mode === 'LEARNING' || session.value?.mode === 'BLITZ')
const isBlock = computed(() => session.value?.mode === 'BLOCK')
const isBlitz = computed(() => session.value?.mode === 'BLITZ')
const isFlashcard = computed(() => session.value?.mode === 'FLASHCARD')
// 1–4-sinf fanlari uchun bolalarga mos ko'rinish
const kids = computed(() => !!session.value && !isBlock.value && isKidsSubject(session.value.subjectName))
const subtitle = computed(() => {
  const s = session.value
  if (!s) return ''
  if (s.mode === 'BLOCK') return s.groups.map((g) => `${g.groupName}: ${g.questionCount} ta`).join(' · ')
  return topicSummary(s.topics.map((t) => t.name))
})
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
// Blok test: savollar fanlar bo'yicha bo'limlarga ajratiladi (Matematika, Biologiya); oddiy testda bitta bo'lim
interface Section {
  id: number
  name: string
  items: { q: QuestionView; i: number }[]
}
const sections = computed<Section[]>(() => {
  if (!isBlock.value) return [{ id: 0, name: '', items: questions.value.map((q, i) => ({ q, i })) }]
  const byGroup = new Map<number, Section>()
  questions.value.forEach((q, i) => {
    const id = q.groupId ?? 0
    const section = byGroup.get(id) ?? { id, name: q.groupName ?? '', items: [] }
    section.items.push({ q, i })
    byGroup.set(id, section)
  })
  const order = session.value?.groups.map((g) => g.groupId) ?? []
  return [...byGroup.values()].sort((a, b) => order.indexOf(a.id) - order.indexOf(b.id))
})
const currentSection = computed(() => sections.value.find((s) => s.items.some(({ i }) => i === index.value)))
const posInSection = computed(() => (currentSection.value?.items.findIndex(({ i }) => i === index.value) ?? 0) + 1)
const sectionAnswered = (s: Section) => s.items.filter(({ q }) => q.answered).length

// Bo'limga o'tish: birinchi javobsiz savoliga (hammasi javob berilgan bo'lsa – boshiga)
function goSection(s: Section) {
  go((s.items.find(({ q }) => !q.answered) ?? s.items[0]).i)
}

// Vaqt chegarasi (blok test): muddat server bergan qolgan vaqtdan hisoblanadi, kompyuter soati noto'g'ri bo'lsa ham
const deadline = ref<number | null>(null)
const remaining = computed(() =>
  deadline.value === null ? null : Math.max(0, Math.ceil((deadline.value - now.value) / 1000)),
)
let timeUp = false
watch(remaining, (r) => {
  if (r === 0 && !timeUp) {
    timeUp = true
    onTimeUp()
  }
})

async function onTimeUp() {
  finishing.value = true
  // Server muddati o'tganini o'zi ko'rishi uchun biroz kutamiz: yakunlash vaqti aynan muddat bo'ladi
  await new Promise((r) => setTimeout(r, 1500))
  try {
    await testApi.finish(id)
  } catch {
    // server o'zi ham vaqti tugagan testni yakunlaydi
  }
  message.warning(isBlitz.value ? `⚡ Blits tugadi! ${correctSoFar.value} ta to'g'ri javob` : 'Vaqt tugadi. Test yakunlandi')
  router.replace(`/test/${id}/result`)
}

const locked = computed(() => !!current.value && (isLearning.value || isFlashcard.value) && current.value.answered)

// Kartochka: avval savol, aylantirilgach – javob va izoh, keyin o'zini baholash
const flipped = ref(new Set<number>())
const isFlipped = computed(() => !!current.value && (flipped.value.has(current.value.id) || current.value.answered))
function flip() {
  if (current.value) flipped.value.add(current.value.id)
}
const correctText = computed(
  () => current.value?.options.find((o) => o.id === current.value?.correctOptionId)?.text ?? '',
)

async function rate(known: boolean) {
  const q = current.value
  if (!q || q.answered || submitting.value) return
  submitting.value = true
  try {
    const res = await testApi.rate(id, q.id, known)
    Object.assign(q, { selectedOptionId: res.selectedOptionId, answered: true, correct: res.correct })
    if (answeredCount.value < questions.value.length) nextUnanswered()
  } catch (e) {
    message.error(errorMessage(e))
  } finally {
    submitting.value = false
  }
}

let blitzTimer: ReturnType<typeof setTimeout> | undefined

// O'rganish rejimi: ketma-ket to'g'ri javoblar (streak) va javobdan keyingi rag'batlantiruvchi sarlavha
const streak = ref(0)
const bestStreak = ref(0)
const feedbackTitle = ref(new Map<number, string>())
const feedbackEl = ref<HTMLElement | null>(null)
const justAnswered = ref<number | null>(null)
// Streak bosqichiga yetgan savol: tabrik izoh ichida ko'rsatiladi (savolni yopmaydi)
const milestone = ref(new Map<number, number>())

// Qayta yuklanganda streak savollar tartibidagi oxirgi javoblardan tiklanadi
function restoreStreak(qs: QuestionView[]) {
  let run = 0
  for (const q of qs) {
    if (!q.answered) continue
    run = q.correct ? run + 1 : 0
    bestStreak.value = Math.max(bestStreak.value, run)
  }
  streak.value = run
}

function onLearningAnswer(q: QuestionView) {
  const ok = q.correct === true
  playFeedback(ok)
  feedbackTitle.value.set(q.id, praise(ok))
  justAnswered.value = q.id
  if (ok) {
    streak.value++
    bestStreak.value = Math.max(bestStreak.value, streak.value)
    if (isStreakMilestone(streak.value)) milestone.value.set(q.id, streak.value)
  } else {
    streak.value = 0
  }
  // Telefonda izoh ekrandan pastda qolib ketmasligi uchun
  nextTick(() => feedbackEl.value?.scrollIntoView({ block: 'nearest', behavior: 'smooth' }))
}

onMounted(async () => {
  try {
    const s = await testApi.get(id)
    if (s.status === 'FINISHED') {
      router.replace(`/test/${id}/result`)
      return
    }
    session.value = s
    if (s.remainingSeconds !== null) deadline.value = Date.now() + s.remainingSeconds * 1000
    if (s.mode === 'LEARNING' || s.mode === 'BLITZ') restoreStreak(s.questions)
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
  clearTimeout(blitzTimer)
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
    if (isLearning.value && res.correct !== null) onLearningAnswer(q)
    // Blitsda keyingi savolga o'zi o'tadi – javobni ko'rishga qisqa vaqt
    if (isBlitz.value && answeredCount.value < questions.value.length) {
      const answeredId = q.id
      blitzTimer = setTimeout(() => current.value?.id === answeredId && nextUnanswered(), res.correct ? 450 : 1100)
    }
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
  if (isFlashcard.value) {
    if (e.key === ' ' || e.key === 'Enter') {
      if (target?.tagName === 'BUTTON') return
      e.preventDefault()
      if (!isFlipped.value) flip()
      else if (current.value.answered) nextUnanswered()
      return
    }
    if (isFlipped.value && !current.value.answered && (e.key === '1' || e.key === '2')) return rate(e.key === '2')
  }
  if (e.key === 'ArrowRight') go(index.value + 1)
  else if (e.key === 'ArrowLeft') go(index.value - 1)
  else if (e.key === 'Enter') {
    // Boshqa tugma fokusda bo'lsa, Enter uning o'zini bosadi (variant tugmasidan tashqari)
    if (target?.tagName === 'BUTTON' && !target.classList.contains('option')) return
    e.preventDefault()
    if (answeredCount.value === questions.value.length) confirmFinish()
    else if (isLearning.value) {
      if (current.value.answered) nextUnanswered()
    } else go(index.value + 1)
  }
  else if (!isFlashcard.value) {
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
  <div class="page" :class="{ kids }">
    <NCard v-if="!session"><NSkeleton text :repeat="8" /></NCard>

    <template v-else>
      <div class="page-header">
        <div>
          <h1>{{ isBlock ? 'Blok test' : isBlitz ? `⚡ Blits · ${session.subjectName}` : session.subjectName }}</h1>
          <div class="muted">
            <template v-if="!isBlock">{{ modeLabel[session.mode] }} rejimi · </template>{{ subtitle }}
          </div>
        </div>
        <div class="header-stats tabular">
          <span v-if="isBlitz" class="blitz-score" title="To'g'ri javoblar">✓ {{ correctSoFar }}</span>
          <span
            v-if="remaining !== null"
            class="countdown"
            :class="{ low: remaining < (isBlitz ? 10 : 300), blitz: isBlitz }"
            title="Qolgan vaqt"
          >
            ⏳ {{ formatClock(remaining) }}
          </span>
          <span v-else class="muted">⏱ {{ formatDuration(elapsed) }}</span>
          <NButton
            v-if="isLearning"
            quaternary
            circle
            :title="soundOn ? 'Ovozni o\'chirish' : 'Ovozni yoqish'"
            :aria-label="soundOn ? 'Ovozni o\'chirish' : 'Ovozni yoqish'"
            @click="soundOn = !soundOn"
          >
            {{ soundOn ? '🔊' : '🔇' }}
          </NButton>
          <NButton type="primary" :loading="finishing" @click="confirmFinish">Testni yakunlash</NButton>
        </div>
      </div>

      <div class="layout" :class="{ single: isBlitz }">
        <div class="main">
          <NCard size="small" style="margin-bottom: 12px">
            <div v-if="isBlock" class="sections">
              <button
                v-for="sec in sections"
                :key="sec.id"
                type="button"
                class="section-tab"
                :class="{ active: sec.id === currentSection?.id }"
                @click="goSection(sec)"
              >
                <span class="section-name">{{ sec.name }}</span>
                <span class="tabular muted">{{ sectionAnswered(sec) }} / {{ sec.items.length }}</span>
              </button>
            </div>
            <div class="progress-row tabular">
              <span>Javob berildi: {{ answeredCount }} / {{ questions.length }}</span>
              <span v-if="isLearning" class="learning-stats">
                <span v-if="streak >= 2" :key="streak" class="streak" :title="`Eng uzun: ${bestStreak}`">🔥 {{ streak }}</span>
                <span class="muted">To'g'ri: {{ correctSoFar }}</span>
              </span>
            </div>
            <NProgress type="line" :percentage="progress" :color="themeVars.primaryColor" :show-indicator="false" :height="8" />
          </NCard>

          <NCard v-if="current">
            <div v-if="isBlock" class="section-title">
              {{ current.groupName }} <span class="muted tabular">· {{ posInSection }} / {{ currentSection?.items.length }}</span>
            </div>
            <div class="q-meta">
              <span class="q-number tabular">{{ index + 1 }} / {{ questions.length }}</span>
              <NTag
                v-if="isBlock && current.groupName && shortSubjectName(current.subjectName, current.groupName) !== current.subjectName"
                size="small"
                :bordered="false"
                type="info"
              >
                {{ shortSubjectName(current.subjectName, current.groupName) }}
              </NTag>
              <NTag size="small" :bordered="false">{{ current.topicName }}</NTag>
              <NTag size="small" :bordered="false" :type="difficultyType[current.difficulty]">
                {{ difficultyLabel[current.difficulty] }}
              </NTag>
            </div>
            <div class="q-text pre-wrap">{{ current.text }}</div>

            <template v-if="isFlashcard">
              <div class="flashcard" :class="{ flipped: isFlipped }">
                <button v-if="!isFlipped" type="button" class="flip-btn" @click="flip">
                  <span class="flip-icon">🃏</span>
                  Javobni ko'rish
                  <span class="muted flip-hint">Probel yoki Enter</span>
                </button>
                <div v-else class="flash-answer">
                  <div class="flash-label">To'g'ri javob</div>
                  <div class="flash-text pre-wrap">{{ correctText }}</div>
                  <div v-if="current.explanation" class="flash-expl pre-wrap">{{ current.explanation }}</div>
                </div>
              </div>
              <div v-if="isFlipped && !current.answered" class="rate-row">
                <NButton size="large" type="error" secondary :disabled="submitting" @click="rate(false)">
                  😕 Bilmasdim <span class="key">1</span>
                </NButton>
                <NButton size="large" type="success" :disabled="submitting" @click="rate(true)">
                  😊 Bilardim <span class="key">2</span>
                </NButton>
              </div>
              <div v-else-if="current.answered" class="rated" :class="current.correct ? 'ok' : 'bad'">
                {{ current.correct ? '😊 Bilardim' : "😕 Bilmasdim – takrorlashga qo'shildi" }}
              </div>
            </template>

            <OptionList
              v-else
              :options="current.options"
              :selected-id="current.selectedOptionId"
              :correct-id="current.correctOptionId"
              :disabled="locked || submitting"
              :animate="justAnswered === current.id"
              @select="select"
            />

            <div v-if="isLearning && current.answered && current.correct !== null" ref="feedbackEl" class="feedback">
              <NAlert
                :type="current.correct ? 'success' : 'error'"
                :title="feedbackTitle.get(current.id) ?? (current.correct ? 'To\'g\'ri!' : 'Noto\'g\'ri')"
              >
                <template v-if="kids" #icon>
                  <span class="mascot">{{ current.correct ? '🦉' : '🤔' }}</span>
                </template>
                <div v-if="milestone.get(current.id)" class="milestone">
                  🔥 {{ milestone.get(current.id) }} ta ketma-ket to'g'ri javob!
                </div>
                <template v-if="!isBlitz">
                  <div v-if="current.explanation" class="pre-wrap">{{ current.explanation }}</div>
                  <div v-else class="muted">Izoh kiritilmagan</div>
                </template>
              </NAlert>
            </div>

            <div class="q-actions">
              <NButton :disabled="index === 0" @click="go(index - 1)">← Oldingi</NButton>
              <NButton v-if="answeredCount === questions.length" type="primary" @click="confirmFinish">
                Yakunlash
              </NButton>
              <NButton
                v-else-if="(isLearning || isFlashcard) && current.answered"
                type="primary"
                @click="nextUnanswered"
              >
                Keyingi savol →
              </NButton>
              <NButton v-else :disabled="index === questions.length - 1" @click="go(index + 1)">
                Keyingi →
              </NButton>
            </div>
            <div class="hint muted">
              <template v-if="isFlashcard">Klaviatura: Probel – aylantirish, 1 – bilmasdim, 2 – bilardim, ← → o'tish</template>
              <template v-else>Klaviatura: 1–6 yoki A–F variant tanlash, Enter – keyingi savol, ← → savollar orasida o'tish</template>
            </div>
          </NCard>
        </div>

        <aside v-if="!isBlitz" class="side">
          <NCard size="small" title="Savollar">
            <template v-for="sec in sections" :key="sec.id">
              <div v-if="sec.name" class="nav-section tabular">
                <span>{{ sec.name }}</span>
                <span class="muted">{{ sectionAnswered(sec) }}/{{ sec.items.length }}</span>
              </div>
              <div class="nav-grid">
                <button
                  v-for="{ q, i } in sec.items"
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
            </template>
            <div class="legend muted">
              <template v-if="isFlashcard">
                <span><i class="dot correct" /> bilardim</span>
                <span><i class="dot wrong" /> bilmasdim</span>
              </template>
              <template v-else-if="isLearning">
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
.countdown {
  font-size: 18px;
  font-weight: 650;
}
.countdown.low {
  color: #d03050;
}
.countdown.blitz {
  font-size: 26px;
  min-width: 70px;
  text-align: right;
}
.countdown.blitz.low {
  animation: pulse 1s ease-in-out infinite;
}
@keyframes pulse {
  50% {
    opacity: 0.4;
  }
}
.blitz-score {
  font-size: 20px;
  font-weight: 700;
  color: var(--correct-border);
}
.layout.single {
  grid-template-columns: minmax(0, 720px);
  justify-content: center;
}
.flashcard {
  border-radius: 14px;
  border: 1.5px dashed rgba(127, 127, 127, 0.35);
  min-height: 150px;
  display: flex;
  align-items: stretch;
  perspective: 800px;
}
.flashcard.flipped {
  border-style: solid;
  border-color: var(--correct-border);
  background: var(--correct-bg);
  animation: card-flip 0.35s ease-out;
}
@keyframes card-flip {
  from {
    transform: rotateY(90deg);
  }
}
.flip-btn {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  font-size: 17px;
  font-weight: 600;
  cursor: pointer;
  border-radius: 14px;
}
.flip-btn:hover {
  background: rgba(13, 148, 136, 0.06);
}
.flip-icon {
  font-size: 34px;
}
.flip-hint {
  font-size: 12px;
  font-weight: 400;
}
.flash-answer {
  padding: 16px 18px;
}
.flash-label {
  font-size: 12px;
  font-weight: 650;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--correct-border);
  margin-bottom: 4px;
}
.flash-text {
  font-size: 18px;
  font-weight: 600;
}
.flash-expl {
  margin-top: 10px;
  font-size: 14px;
  line-height: 1.5;
}
.rate-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-top: 16px;
}
.key {
  margin-left: 8px;
  font-size: 11px;
  opacity: 0.6;
  border: 1px solid currentColor;
  border-radius: 4px;
  padding: 0 4px;
}
.rated {
  margin-top: 16px;
  font-weight: 600;
  text-align: center;
}
.rated.ok {
  color: var(--correct-border);
}
.rated.bad {
  color: var(--wrong-border);
}
.milestone {
  font-weight: 650;
  color: #ea580c;
  margin-bottom: 4px;
}
.mascot {
  font-size: 30px;
  line-height: 1;
}
/* 1–4-sinf: yirik matn va rang-barang variantlar */
.kids .q-text {
  font-size: 24px;
  line-height: 1.45;
}
.kids :deep(.option) {
  font-size: 19px;
  padding: 16px 16px;
  border-radius: 16px;
}
.kids :deep(.option .letter) {
  width: 36px;
  height: 36px;
  font-size: 16px;
  color: #fff;
}
.kids :deep(.option:nth-child(4n + 1) .letter) {
  background: #3b82f6;
}
.kids :deep(.option:nth-child(4n + 2) .letter) {
  background: #f59e0b;
}
.kids :deep(.option:nth-child(4n + 3) .letter) {
  background: #8b5cf6;
}
.kids :deep(.option:nth-child(4n + 4) .letter) {
  background: #ec4899;
}
.kids :deep(.option.correct .letter) {
  background: var(--correct-border);
}
.kids :deep(.option.wrong .letter) {
  background: var(--wrong-border);
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
.sections {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.section-tab {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 14px;
  border-radius: 8px;
  border: 1.5px solid rgba(127, 127, 127, 0.25);
  background: transparent;
  color: inherit;
  font: inherit;
  font-size: 14px;
  cursor: pointer;
}
.section-tab.active {
  border-color: #0d9488;
  background: rgba(13, 148, 136, 0.1);
}
.section-name {
  font-weight: 600;
}
.section-title {
  font-size: 13px;
  font-weight: 650;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: #0d9488;
  margin-bottom: 8px;
}
.nav-section {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  font-weight: 600;
  margin: 4px 0 8px;
}
.nav-grid + .nav-section {
  margin-top: 14px;
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
  animation: feedback-in 0.25s ease-out;
}
@keyframes feedback-in {
  from {
    opacity: 0;
    transform: translateY(6px);
  }
}
.learning-stats {
  display: inline-flex;
  align-items: center;
  gap: 12px;
}
.streak {
  font-weight: 650;
  color: #ea580c;
  animation: streak-pop 0.35s ease-out;
}
@keyframes streak-pop {
  40% {
    transform: scale(1.35);
  }
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
