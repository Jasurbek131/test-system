<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NIcon, useMessage } from 'naive-ui'
import { Add, Checkmark, Remove } from '@vicons/ionicons5'
import { progressApi, subjectApi, testApi } from '@/api'
import { errorMessage } from '@/api/http'
import type { ReviewKind, Subject, SubjectProgress, TestMode, Topic } from '@/types'
import { masteryLabel } from '@/utils/format'
import AppBar from '@m/components/AppBar.vue'
import SubjectBadge from '@m/components/SubjectBadge.vue'
import { saveLastTest } from '@/utils/learning'

const MAX = 500
const route = useRoute()
const router = useRouter()
const message = useMessage()
const subjectId = Number(route.params.id)
const prefsKey = `ts_prefs_${subjectId}`

const subject = ref<Subject | null>(null)
const topics = ref<Topic[]>([])
const selected = ref<Set<number>>(new Set())
const count = ref(10)
const mode = ref<TestMode>('LEARNING')
const starting = ref(false)
// Blitsda savollar soni so'ralmaydi: 60 soniyaga yetadigan darajada ko'p savol olinadi
const BLITZ_COUNT = 100
const isBlitz = computed(() => mode.value === 'BLITZ')

// Mavzular bo'yicha o'zlashtirish va takrorlash
const progress = ref<SubjectProgress | null>(null)
const topicProgress = computed(() => new Map((progress.value?.topics ?? []).map((t) => [t.topicId, t])))
const masteredCount = computed(() => (progress.value?.topics ?? []).filter((t) => t.level === 'MASTERED' && t.total > 0).length)
const withQuestions = computed(() => topics.value.filter((t) => t.questionCount > 0).length)

async function review(kind: ReviewKind, reviewMode: TestMode) {
  if (starting.value) return
  starting.value = true
  try {
    const { id } = await testApi.createReview({ subjectId, kind, questionCount: 20, mode: reviewMode })
    router.replace(`/test/${id}`)
  } catch (e) {
    message.error(errorMessage(e))
  } finally {
    starting.value = false
  }
}

const available = computed(() =>
  Math.min(MAX, topics.value.filter((t) => selected.value.has(t.id)).reduce((s, t) => s + t.questionCount, 0)),
)
const allSelected = computed(() => topics.value.length > 0 && topics.value.every((t) => !t.questionCount || selected.value.has(t.id)))
const presets = computed(() => {
  const list = [10, 20, 30].filter((n) => n < available.value)
  return [...list.map((n) => ({ n, label: String(n) })), { n: available.value, label: 'Hammasi' }]
})

watch(available, (max) => {
  if (max > 0 && count.value > max) count.value = max
  if (max > 0 && count.value < 1) count.value = Math.min(10, max)
})

function toggle(t: Topic) {
  if (!t.questionCount) return
  const next = new Set(selected.value)
  next.has(t.id) ? next.delete(t.id) : next.add(t.id)
  selected.value = next
}
function toggleAll() {
  selected.value = allSelected.value ? new Set() : new Set(topics.value.filter((t) => t.questionCount).map((t) => t.id))
}
function step(d: number) {
  count.value = Math.min(Math.max(1, count.value + d), Math.max(available.value, 1))
}

onMounted(async () => {
  try {
    const [s, t, p] = await Promise.all([subjectApi.get(subjectId), subjectApi.topics(subjectId), progressApi.subject(subjectId)])
    progress.value = p
    subject.value = s
    topics.value = t
    let prefs: { count?: number; mode?: TestMode; topics?: number[] } = {}
    try {
      prefs = JSON.parse(localStorage.getItem(prefsKey) ?? '{}')
    } catch {
      /* sozlamalar yo'q */
    }
    // Natija sahifasidagi «Mashq qilish» zaif mavzularni ?topics=1,2 bilan yuboradi – o'rganish rejimida
    const practice = String(route.query.topics ?? '').split(',').map(Number).filter((n) => n > 0)
    const valid = (practice.length ? practice : (prefs.topics ?? [])).filter((id) => t.some((x) => x.id === id && x.questionCount))
    selected.value = new Set(valid.length ? valid : t.filter((x) => x.questionCount).map((x) => x.id))
    if (practice.length) mode.value = 'LEARNING'
    else if (prefs.mode) mode.value = prefs.mode
    if (prefs.count) count.value = prefs.count
  } catch (e) {
    message.error(errorMessage(e))
    router.replace('/')
  }
})

async function start() {
  if (!selected.value.size || available.value < 1) return
  starting.value = true
  try {
    const n = Math.min(isBlitz.value ? BLITZ_COUNT : count.value, available.value)
    try {
      localStorage.setItem(prefsKey, JSON.stringify({ count: n, mode: mode.value, topics: [...selected.value] }))
    } catch {
      /* saqlab bo'lmasa ham davom etamiz */
    }
    const { id } = await testApi.create({ subjectId, topicIds: [...selected.value], questionCount: n, mode: mode.value })
    saveLastTest({ subjectId, subjectName: subject.value?.name ?? '', topicIds: [...selected.value], questionCount: n, mode: mode.value })
    router.replace(`/test/${id}`)
  } catch (e) {
    message.error(errorMessage(e))
  } finally {
    starting.value = false
  }
}
</script>

<template>
  <AppBar title="Test sozlash" />
  <main v-if="subject" class="screen">
    <div class="hero">
      <SubjectBadge :id="subject.id" :name="subject.name" :size="56" />
      <div>
        <h1 class="name">{{ subject.name }}</h1>
        <div class="muted tabular">{{ subject.topicCount }} mavzu · {{ subject.questionCount }} savol</div>
      </div>
    </div>

    <div v-if="progress && (progress.due || progress.mistakes)" class="card review">
      <div class="review-title">🔁 Takrorlash</div>
      <div class="muted small">
        <template v-if="progress.due">{{ progress.due }} ta savolni takrorlash vaqti keldi. </template>
        <template v-if="progress.mistakes">Xatolar: {{ progress.mistakes }} ta.</template>
      </div>
      <div class="review-actions">
        <button v-if="progress.due" class="btn btn-primary" :disabled="starting" @click="review('DUE', 'LEARNING')">Takrorlash</button>
        <button v-if="progress.due" class="btn btn-secondary" :disabled="starting" @click="review('DUE', 'FLASHCARD')">Kartochkalar</button>
        <button v-if="progress.mistakes" class="btn btn-secondary" :disabled="starting" @click="review('MISTAKES', 'LEARNING')">
          Xatolarni yechish
        </button>
      </div>
    </div>

    <template v-if="!isBlitz">
    <div class="section-label">Savollar soni</div>
    <div class="card block">
      <div class="counter">
        <button class="round" aria-label="Kamaytirish" :disabled="count <= 1" @click="step(-1)">
          <NIcon :component="Remove" size="22" />
        </button>
        <div class="count tabular">{{ Math.min(count, available) }}</div>
        <button class="round" aria-label="Ko'paytirish" :disabled="count >= available" @click="step(1)">
          <NIcon :component="Add" size="22" />
        </button>
      </div>
      <div class="chips">
        <button v-for="p in presets" :key="p.label" class="chip" :class="{ on: Math.min(count, available) === p.n }" @click="count = p.n">
          {{ p.label }}
        </button>
      </div>
    </div>
    </template>

    <div class="section-label">Rejim</div>
    <div class="modes">
      <button class="mode card" :class="{ on: mode === 'LEARNING' }" @click="mode = 'LEARNING'">
        <span class="mode-title">O'rganish</span>
        <span class="mode-desc">Har javobdan keyin to'g'ri javob va izoh</span>
      </button>
      <button class="mode card" :class="{ on: mode === 'EXAM' }" @click="mode = 'EXAM'">
        <span class="mode-title">Imtihon</span>
        <span class="mode-desc">Javoblar va baho faqat oxirida</span>
      </button>
      <button class="mode card" :class="{ on: mode === 'FLASHCARD' }" @click="mode = 'FLASHCARD'">
        <span class="mode-title">🃏 Kartochka</span>
        <span class="mode-desc">Javobni eslang, aylantiring, o'zingizni baholang</span>
      </button>
      <button class="mode card" :class="{ on: mode === 'BLITZ' }" @click="mode = 'BLITZ'">
        <span class="mode-title">⚡ Blits</span>
        <span class="mode-desc">60 soniyada iloji boricha ko'p savol</span>
      </button>
    </div>

    <div class="topics-head">
      <div class="section-label" style="margin: 0">
        Mavzular · {{ selected.size }}/{{ topics.length }}
        <span v-if="progress && masteredCount" class="mastered-note">· ✓ {{ masteredCount }}/{{ withQuestions }}</span>
      </div>
      <button class="link" @click="toggleAll">{{ allSelected ? 'Hammasini olib tashlash' : 'Hammasini tanlash' }}</button>
    </div>
    <div class="card topics">
      <button
        v-for="t in topics"
        :key="t.id"
        class="topic"
        :class="{ on: selected.has(t.id), off: !t.questionCount }"
        role="checkbox"
        :aria-checked="selected.has(t.id)"
        @click="toggle(t)"
      >
        <span class="tick"><NIcon v-if="selected.has(t.id)" :component="Checkmark" size="16" /></span>
        <span class="topic-text">
          <span class="topic-name">{{ t.name }}</span>
          <span v-if="topicProgress.get(t.id)?.seen" class="topic-level" :class="topicProgress.get(t.id)!.level">
            {{ topicProgress.get(t.id)!.level === 'MASTERED' ? '✓ ' : '' }}{{ masteryLabel[topicProgress.get(t.id)!.level] }}
            · {{ topicProgress.get(t.id)!.seen }}/{{ t.questionCount }}
          </span>
        </span>
        <span class="topic-count tabular">{{ t.questionCount }}</span>
      </button>
    </div>
  </main>

  <div class="bottom-bar">
    <div class="bottom-bar-inner">
      <button class="btn btn-primary btn-block" :disabled="!selected.size || available < 1 || starting" @click="start">
        <template v-if="!selected.size">Mavzu tanlang</template>
        <template v-else-if="isBlitz">⚡ Boshlash · 60 soniya</template>
        <template v-else>Boshlash · {{ Math.min(count, available) }} ta {{ mode === 'FLASHCARD' ? 'kartochka' : 'savol' }}</template>
      </button>
    </div>
  </div>
</template>

<style scoped>
.hero {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 4px;
}
.name {
  font-family: var(--display);
  font-size: 20px;
  font-weight: 650;
  letter-spacing: -0.01em;
  color: var(--ink);
  margin: 0 0 2px;
}
.block {
  padding: 16px;
}
.counter {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 28px;
}
.round {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  border: 0;
  background: var(--grid);
  color: var(--ink);
  display: flex;
  align-items: center;
  justify-content: center;
}
.round:disabled {
  opacity: 0.35;
}
.count {
  font-family: var(--display);
  font-size: 40px;
  font-weight: 650;
  color: var(--ink);
  min-width: 90px;
  text-align: center;
}
.chips {
  display: flex;
  gap: 8px;
  justify-content: center;
  margin-top: 14px;
  flex-wrap: wrap;
}
.chip {
  min-width: 56px;
  height: 38px;
  padding: 0 14px;
  border-radius: 19px;
  border: 1.5px solid var(--line);
  background: transparent;
  font-weight: 600;
}
.chip.on {
  background: var(--ink);
  border-color: var(--ink);
  color: #fff;
}
:root[data-theme='dark'] .chip.on {
  color: #0f1430;
}
.modes {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
.mode {
  border: 0;
  padding: 14px;
  text-align: left;
  display: flex;
  flex-direction: column;
  gap: 4px;
  box-shadow: var(--shadow), inset 0 0 0 1.5px transparent;
}
.mode.on {
  box-shadow: inset 0 0 0 2px var(--ink);
  background: var(--marker-soft);
}
.mode-title {
  font-weight: 650;
  color: var(--ink);
}
.mode-desc {
  font-size: 13px;
  color: var(--muted);
  line-height: 1.35;
}
.topics-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 24px 4px 10px;
}
.link {
  border: 0;
  background: none;
  color: var(--ink);
  font-weight: 600;
  font-size: 14px;
  padding: 6px 0;
}
.topics {
  overflow: hidden;
}
.topic {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border: 0;
  border-bottom: 1px solid var(--line);
  background: transparent;
  text-align: left;
}
.topic:last-child {
  border-bottom: 0;
}
.topic.off {
  opacity: 0.45;
}
.tick {
  flex: none;
  width: 24px;
  height: 24px;
  border-radius: 7px;
  border: 2px solid var(--line);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}
.topic.on .tick {
  background: var(--ink);
  border-color: var(--ink);
}
:root[data-theme='dark'] .topic.on .tick {
  color: #0f1430;
}
.review {
  margin-top: 16px;
  padding: 14px 16px;
  background: var(--marker-soft);
  box-shadow: inset 0 0 0 1.5px var(--marker);
}
.review-title {
  font-weight: 650;
  color: var(--ink);
  margin-bottom: 2px;
}
.small {
  font-size: 13px;
}
.review-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 10px;
}
.review-actions .btn {
  min-height: 42px;
  padding: 0 14px;
  font-size: 14px;
}
.mastered-note {
  color: var(--ok);
}
.topic-text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.topic-level {
  font-size: 12px;
  font-weight: 600;
  color: var(--muted);
}
.topic-level.LEARNING {
  color: #c27c0e;
}
.topic-level.MASTERED {
  color: var(--ok);
}
.topic-name {
  min-width: 0;
  font-size: 15px;
  line-height: 1.35;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.topic-count {
  flex: none;
  font-size: 13px;
  font-weight: 600;
  color: var(--muted);
}
</style>
