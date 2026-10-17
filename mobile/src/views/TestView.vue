<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NIcon, useDialog, useMessage } from 'naive-ui'
import { CheckmarkCircle, ChevronBack, ChevronDown, Close, CloseCircle, VolumeHigh, VolumeMute } from '@vicons/ionicons5'
import { testApi } from '@/api'
import { errorMessage } from '@/api/http'
import type { QuestionView, SessionView } from '@/types'
import { difficultyLabel, formatClock, isKidsSubject, optionLetter, shortSubjectName } from '@/utils/format'
import { isStreakMilestone, playFeedback, praise, soundOn } from '@/utils/learning'

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

// Javob darhol ochiladigan rejimlar: o'rganish va blits
const learning = computed(() => session.value?.mode === 'LEARNING' || session.value?.mode === 'BLITZ')
const blitz = computed(() => session.value?.mode === 'BLITZ')
const flashcard = computed(() => session.value?.mode === 'FLASHCARD')
// 1–4-sinf fanlari uchun bolalarga mos ko'rinish
const kids = computed(() => !!session.value && session.value.mode !== 'BLOCK' && isKidsSubject(session.value.subjectName))
const correctSoFar = computed(() => qs.value.filter((x) => x.correct === true).length)
const qs = computed(() => session.value?.questions ?? [])
const q = computed<QuestionView | undefined>(() => qs.value[index.value])
const answered = computed(() => qs.value.filter((x) => x.answered).length)
const allAnswered = computed(() => qs.value.length > 0 && answered.value === qs.value.length)
const locked = computed(() => !!q.value && (learning.value || flashcard.value) && q.value.answered)

// Kartochka: avval savol, aylantirilgach – javob va izoh, keyin o'zini baholash
const flipped = ref(new Set<number>())
const isFlipped = computed(() => !!q.value && (flipped.value.has(q.value.id) || q.value.answered))
const correctText = computed(() => q.value?.options.find((o) => o.id === q.value?.correctOptionId)?.text ?? '')
function flip() {
  if (q.value) flipped.value.add(q.value.id)
}
async function rate(known: boolean) {
  const cur = q.value
  if (!cur || cur.answered || busy.value) return
  busy.value = true
  try {
    const res = await testApi.rate(id, cur.id, known)
    Object.assign(cur, { selectedOptionId: res.selectedOptionId, answered: true, correct: res.correct })
    if (!known) navigator.vibrate?.(60)
    if (!allAnswered.value) nextOpen()
  } catch (e) {
    message.error(errorMessage(e))
  } finally {
    busy.value = false
  }
}
let blitzTimer: ReturnType<typeof setTimeout> | undefined
const revealed = computed(() => !!q.value && learning.value && q.value.answered && q.value.correct !== null)
const elapsed = computed(() =>
  session.value ? Math.max(0, Math.floor((now.value - new Date(session.value.startedAt).getTime()) / 1000)) : 0,
)
const isLast = computed(() => index.value === qs.value.length - 1)

// O'rganish rejimi: ketma-ket to'g'ri javoblar (streak) va rag'batlantiruvchi sarlavha
const streak = ref(0)
const feedbackTitle = ref(new Map<number, string>())
const justAnswered = ref<number | null>(null)
// Streak bosqichiga yetgan savol: tabrik izoh kartochkasining ichida ko'rsatiladi (savol matnini yopmaydi)
const milestone = ref(new Map<number, number>())

// Qayta ochilganda streak savollar tartibidagi oxirgi javoblardan tiklanadi
function restoreStreak(list: QuestionView[]) {
  let run = 0
  for (const x of list) if (x.answered) run = x.correct ? run + 1 : 0
  streak.value = run
}

function onLearningAnswer(cur: QuestionView) {
  const ok = cur.correct === true
  playFeedback(ok)
  if (!ok) navigator.vibrate?.(120)
  feedbackTitle.value.set(cur.id, praise(ok))
  justAnswered.value = cur.id
  streak.value = ok ? streak.value + 1 : 0
  if (ok && isStreakMilestone(streak.value)) milestone.value.set(cur.id, streak.value)
}

// Blok test: savollar fanlar bo'yicha bo'limlarga ajratiladi (Matematika, Biologiya)
interface Section {
  id: number
  name: string
  items: { x: QuestionView; i: number }[]
}
const block = computed(() => session.value?.mode === 'BLOCK')
const sections = computed<Section[]>(() => {
  if (!block.value) return [{ id: 0, name: '', items: qs.value.map((x, i) => ({ x, i })) }]
  const byGroup = new Map<number, Section>()
  qs.value.forEach((x, i) => {
    const id = x.groupId ?? 0
    const section = byGroup.get(id) ?? { id, name: x.groupName ?? '', items: [] }
    section.items.push({ x, i })
    byGroup.set(id, section)
  })
  const order = session.value?.groups.map((g) => g.groupId) ?? []
  return [...byGroup.values()].sort((a, b) => order.indexOf(a.id) - order.indexOf(b.id))
})
const currentSection = computed(() => sections.value.find((s) => s.items.some(({ i }) => i === index.value)))
const sectionAnswered = (s: Section) => s.items.filter(({ x }) => x.answered).length
function goSection(s: Section) {
  go((s.items.find(({ x }) => !x.answered) ?? s.items[0]).i)
}
// Fan ichidagi sinf: "1-sinf Matematika" -> "1-sinf" (nomida sinf bo'lmasa – bo'sh)
const grade = (x: QuestionView) =>
  x.groupName && shortSubjectName(x.subjectName, x.groupName) !== x.subjectName ? shortSubjectName(x.subjectName, x.groupName) : ''

// Vaqt chegarasi (blok test): qolgan vaqt tugaganda test avtomatik yakunlanadi
const deadline = ref<number | null>(null)
const remaining = computed(() =>
  deadline.value === null ? null : Math.max(0, Math.ceil((deadline.value - now.value) / 1000)),
)
let timeUp = false
watch(remaining, async (r) => {
  if (r !== 0 || timeUp) return
  timeUp = true
  busy.value = true
  // Muddat aniq o'tib bo'lgach yakunlaymiz: yakunlash vaqti aynan muddat bo'ladi
  await new Promise((r) => setTimeout(r, 1500))
  try {
    await testApi.finish(id)
  } catch {
    /* lokal servis ham vaqti tugagan testni yakunlaydi */
  }
  message.warning(blitz.value ? `⚡ Blits tugadi! ${correctSoFar.value} ta to'g'ri` : 'Vaqt tugadi. Test yakunlandi')
  router.replace(`/test/${id}/result`)
})

onMounted(async () => {
  try {
    const s = await testApi.get(id)
    if (s.status === 'FINISHED') return router.replace(`/test/${id}/result`)
    session.value = s
    if (s.remainingSeconds !== null) deadline.value = Date.now() + s.remainingSeconds * 1000
    if (s.mode === 'LEARNING' || s.mode === 'BLITZ') restoreStreak(s.questions)
    const open = s.questions.findIndex((x) => !x.answered)
    index.value = open === -1 ? 0 : open
  } catch (e) {
    message.error(errorMessage(e))
    router.replace('/')
  }
  timer = setInterval(() => (now.value = Date.now()), 1000)
})
onBeforeUnmount(() => {
  clearInterval(timer)
  clearTimeout(blitzTimer)
})

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
      if (res.correct !== null) onLearningAnswer(cur)
      // Blitsda keyingi savolga o'zi o'tadi – javobni ko'rishga qisqa vaqt
      if (blitz.value && !allAnswered.value) {
        const answeredId = cur.id
        blitzTimer = setTimeout(() => q.value?.id === answeredId && nextOpen(), res.correct ? 450 : 1100)
      }
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
  if (flashcard.value) return !q.value?.answered && !isFlipped.value ? flip() : nextOpen()
  if (learning.value && !q.value?.answered) return nextOpen()
  if (isLast.value) return askFinish()
  return learning.value ? nextOpen() : go(index.value + 1)
}
const primaryLabel = computed(() => {
  if (allAnswered.value) return 'Yakunlash'
  if (flashcard.value) return isFlipped.value ? 'Keyingi' : "Javobni ko'rish"
  if (learning.value) return q.value?.answered ? 'Keyingi savol' : "O'tkazib yuborish"
  return isLast.value ? 'Yakunlash' : 'Keyingi'
})
const primaryClass = computed(() =>
  learning.value && !q.value?.answered && !allAnswered.value ? 'btn-secondary' : 'btn-primary',
)
// Kartochka aylantirilgan, lekin hali baholanmagan – pastda «Bilmasdim / Bilardim»
const rating = computed(() => flashcard.value && !!q.value && isFlipped.value && !q.value.answered)

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
  <div class="test grid-paper" :class="{ kids }">
    <header class="top">
      <button class="icon" aria-label="Testdan chiqish" @click="askExit"><NIcon :component="Close" size="24" /></button>
      <button class="counter tabular" :disabled="!session" @click="sheet = true">
        {{ index + 1 }} / {{ qs.length || '–' }}
        <NIcon :component="ChevronDown" size="16" />
      </button>
      <div class="top-right">
        <span v-if="blitz" class="blitz-score tabular">✓ {{ correctSoFar }}</span>
        <span v-if="learning && !blitz && streak >= 2" :key="streak" class="streak tabular">🔥 {{ streak }}</span>
        <button
          v-if="learning"
          class="icon sound"
          :aria-label="soundOn ? 'Ovozni o\'chirish' : 'Ovozni yoqish'"
          @click="soundOn = !soundOn"
        >
          <NIcon :component="soundOn ? VolumeHigh : VolumeMute" size="20" />
        </button>
        <div v-if="remaining !== null" class="time tabular countdown" :class="{ low: remaining < (blitz ? 10 : 300), blitz }">
          {{ formatClock(remaining) }}
        </div>
        <div v-else class="time tabular muted">{{ formatClock(elapsed) }}</div>
      </div>
    </header>
    <div class="progress" role="progressbar" :aria-valuenow="answered" :aria-valuemax="qs.length">
      <div class="progress-fill" :style="{ width: qs.length ? `${(answered / qs.length) * 100}%` : '0' }" />
    </div>
    <nav v-if="block" class="sections" aria-label="Fanlar">
      <button
        v-for="sec in sections"
        :key="sec.id"
        class="section-chip tabular"
        :class="{ on: sec.id === currentSection?.id }"
        @click="goSection(sec)"
      >
        {{ sec.name }} <span class="section-count">{{ sectionAnswered(sec) }}/{{ sec.items.length }}</span>
      </button>
    </nav>

    <main v-if="q" class="body" @touchstart.passive="touchStart" @touchend.passive="touchEnd">
      <Transition :name="direction" mode="out-in">
        <section :key="q.id" class="question">
          <div class="meta">
            <span class="topic">{{ block ? q.groupName : q.topicName.replace(/^\d+\.\s*/, '') }}</span>
            <span class="level muted">{{ block && grade(q) ? `${grade(q)} · ` : '' }}{{ difficultyLabel[q.difficulty] }}</span>
          </div>
          <h2 class="text">{{ q.text }}</h2>

          <template v-if="flashcard">
            <button v-if="!isFlipped" class="flashcard card" @click="flip">
              <span class="flip-icon">🃏</span>
              <span>Javobni ko'rish uchun bosing</span>
            </button>
            <div v-else class="flashcard card flipped">
              <div class="flash-label">To'g'ri javob</div>
              <div class="flash-text">{{ correctText }}</div>
              <div v-if="q.explanation" class="flash-expl">{{ q.explanation }}</div>
            </div>
            <div v-if="q.answered" class="rated" :class="q.correct ? 'ok' : 'bad'">
              {{ q.correct ? '😊 Bilardim' : "😕 Bilmasdim – takrorlashga qo'shildi" }}
            </div>
          </template>

          <div v-else class="options" role="radiogroup">
            <button
              v-for="(o, i) in q.options"
              :key="o.id"
              class="option"
              :class="[optionState(o.id), { animate: justAnswered === q.id }]"
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
            <span v-if="kids" class="mascot">{{ q.correct ? '🦉' : '🤔' }}</span>
            <div>
              <div class="feedback-title">{{ feedbackTitle.get(q.id) ?? (q.correct ? "To'g'ri!" : "Noto'g'ri") }}</div>
              <div v-if="milestone.get(q.id)" class="milestone">🔥 {{ milestone.get(q.id) }} ta ketma-ket to'g'ri!</div>
              <div v-if="q.explanation && !blitz" class="feedback-text">{{ q.explanation }}</div>
            </div>
          </div>
          <p v-else-if="!learning && !flashcard && q.answered" class="muted hint">Javob saqlandi. Yakunlaguncha o'zgartirishingiz mumkin.</p>
        </section>
      </Transition>
    </main>

    <div class="bottom-bar">
      <div class="bottom-bar-inner">
        <button class="btn btn-secondary nav" aria-label="Oldingi savol" :disabled="index === 0" @click="go(index - 1)">
          <NIcon :component="ChevronBack" size="22" />
        </button>
        <template v-if="rating">
          <button class="btn btn-block rate bad" :disabled="busy" @click="rate(false)">😕 Bilmasdim</button>
          <button class="btn btn-block rate ok" :disabled="busy" @click="rate(true)">😊 Bilardim</button>
        </template>
        <button v-else class="btn btn-block" :class="primaryClass" :disabled="!q" @click="primary">{{ primaryLabel }}</button>
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
          <template v-for="sec in sections" :key="sec.id">
            <div v-if="sec.name" class="cells-title tabular">
              <span>{{ sec.name }}</span>
              <span class="muted">{{ sectionAnswered(sec) }}/{{ sec.items.length }}</span>
            </div>
            <div class="cells">
              <button
                v-for="{ x, i } in sec.items"
                :key="x.id"
                class="cell tabular"
                :class="[cellState(x), { cur: i === index }]"
                @click="go(i); sheet = false"
              >
                {{ i + 1 }}
              </button>
            </div>
          </template>
          <div class="legend muted">
            <template v-if="flashcard">
              <span><i class="dot ok" />bilardim</span>
              <span><i class="dot bad" />bilmasdim</span>
            </template>
            <template v-else-if="learning">
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
  /* chap va o'ng ustunlar teng – savol hisoblagichi doim o'rtada, yonidagilar uni surmaydi */
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  align-items: center;
  height: 56px;
  padding: 0 4px;
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
  white-space: nowrap;
}
.top-right {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 0;
  min-width: 0;
  white-space: nowrap;
}
.sound {
  flex: none;
  width: 34px;
  height: 34px;
  color: var(--muted);
}
.streak {
  font-family: var(--display);
  font-weight: 650;
  font-size: 14px;
  color: #ea580c;
  padding: 0 2px;
  animation: streak-pop 0.35s ease-out;
}
@keyframes streak-pop {
  40% {
    transform: scale(1.35);
  }
}
.option.animate.ok {
  animation: option-pop 0.4s ease-out;
}
.option.animate.bad {
  animation: option-shake 0.4s ease-in-out;
}
@keyframes option-pop {
  40% {
    transform: scale(1.03);
  }
}
@keyframes option-shake {
  20%,
  60% {
    transform: translateX(-7px);
  }
  40%,
  80% {
    transform: translateX(7px);
  }
}
.time {
  flex: none;
  text-align: right;
  padding: 0 6px 0 2px;
  font-size: 14px;
}
.countdown {
  font-weight: 650;
  font-size: 15px;
  color: var(--ink);
}
.countdown.low {
  color: #d03050;
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
.feedback {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}
.milestone {
  font-weight: 650;
  font-size: 14px;
  color: #ea580c;
  margin-bottom: 4px;
}
.mascot {
  font-size: 36px;
  line-height: 1;
}
.countdown.blitz {
  font-size: 22px;
  font-family: var(--display);
}
.countdown.blitz.low {
  animation: pulse 1s ease-in-out infinite;
}
@keyframes pulse {
  50% {
    opacity: 0.35;
  }
}
.blitz-score {
  font-family: var(--display);
  font-weight: 650;
  font-size: 16px;
  color: var(--ok);
  padding: 0 6px;
}
.flashcard {
  width: 100%;
  min-height: 170px;
  border: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 18px;
  font-size: 16px;
  font-weight: 600;
  color: var(--ink);
  background: var(--card);
  border: 2px dashed var(--line);
}
.flashcard.flipped {
  align-items: stretch;
  justify-content: flex-start;
  border: 0;
  background: var(--ok-soft);
  box-shadow: inset 0 0 0 2px var(--ok);
  animation: card-flip 0.35s ease-out;
}
@keyframes card-flip {
  from {
    transform: rotateY(90deg);
  }
}
.flip-icon {
  font-size: 40px;
}
.flash-label {
  font-size: 12px;
  font-weight: 650;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--ok);
}
.flash-text {
  font-size: 19px;
  line-height: 1.4;
  color: var(--text);
  white-space: pre-wrap;
}
.flash-expl {
  font-size: 15px;
  font-weight: 400;
  line-height: 1.5;
  color: var(--text);
  margin-top: 6px;
  white-space: pre-wrap;
}
.rated {
  margin-top: 14px;
  text-align: center;
  font-weight: 650;
}
.rated.ok {
  color: var(--ok);
}
.rated.bad {
  color: var(--bad);
}
.rate {
  color: #fff;
}
.rate.ok {
  background: var(--ok);
}
.rate.bad {
  background: var(--bad);
}
/* 1–4-sinf: yirik matn va rang-barang variantlar */
.kids .text {
  font-size: 25px;
}
.kids .option {
  min-height: 68px;
  font-size: 20px;
  border-radius: 20px;
}
.kids .option .letter {
  width: 40px;
  height: 40px;
  font-size: 17px;
  color: #fff;
}
.kids .option:nth-child(4n + 1) .letter {
  background: #3b82f6;
}
.kids .option:nth-child(4n + 2) .letter {
  background: #f59e0b;
}
.kids .option:nth-child(4n + 3) .letter {
  background: #8b5cf6;
}
.kids .option:nth-child(4n + 4) .letter {
  background: #ec4899;
}
.kids .option.ok .letter {
  background: var(--ok);
}
.kids .option.bad .letter {
  background: var(--bad);
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
.sections {
  display: flex;
  gap: 8px;
  padding: 10px 16px 0;
  overflow-x: auto;
  scrollbar-width: none;
}
.section-chip {
  flex: none;
  height: 34px;
  padding: 0 14px;
  border-radius: 17px;
  border: 1.5px solid var(--line);
  background: var(--card);
  color: var(--ink);
  font-weight: 650;
  font-size: 14px;
}
.section-chip.on {
  background: var(--ink);
  border-color: var(--ink);
  color: #fff;
}
:root[data-theme='dark'] .section-chip.on {
  color: #0f1430;
}
.section-count {
  font-weight: 500;
  opacity: 0.75;
  margin-left: 4px;
}
.cells-title {
  display: flex;
  justify-content: space-between;
  font-weight: 650;
  margin: 4px 0 10px;
}
.cells + .cells-title {
  margin-top: 18px;
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
