<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { NIcon, useMessage } from 'naive-ui'
import { Add, Checkmark, Remove } from '@vicons/ionicons5'
import { subjectApi, testApi } from '@/api'
import { errorMessage } from '@/api/http'
import type { Subject } from '@/types'
import { formatDuration, formatScore, shortSubjectName } from '@/utils/format'
import AppBar from '@m/components/AppBar.vue'

// DTM'dagidek har bir fandan odatda 30 ta savol
const DEFAULT_COUNT = 30
const MAX = 500
// Har bir fanga 1 soat (lokal servisdagi BLOCK_SECONDS_PER_SUBJECT bilan bir xil)
const SECONDS_PER_SUBJECT = 3600
const prefsKey = 'ts_block_prefs'
const router = useRouter()
const message = useMessage()

/** Blok testdagi fan: bir yoki bir nechta sinf (masalan Matematika: 1-sinf, 2-sinf) */
interface Group {
  id: number
  name: string
  scoreWeight: number
  subjects: Subject[]
}

const groups = ref<Group[]>([])
// Tanlangan sinflar (fan id lari); guruh tanlangan = undan kamida bitta sinf tanlangan
const selected = ref<Set<number>>(new Set())
const counts = reactive<Record<number, number>>({})
const starting = ref(false)

const chosenSubjects = (g: Group) => g.subjects.filter((s) => selected.value.has(s.id))
const isChosen = (g: Group) => chosenSubjects(g).length > 0
const available = (g: Group) => chosenSubjects(g).reduce((sum, s) => sum + s.questionCount, 0)
const groupTotal = (g: Group) => g.subjects.reduce((sum, s) => sum + s.questionCount, 0)

// Sinflar qatori: guruhda bir nechta fan bo'lsa yoki fan nomida sinf bo'lsa ("1-sinf Matematika")
const hasGrades = (g: Group) => g.subjects.length > 1 || g.subjects.some((s) => shortSubjectName(s.name, g.name) !== s.name)

const chosen = computed(() => groups.value.filter(isChosen))
const total = computed(() => chosen.value.reduce((sum, g) => sum + counts[g.id], 0))
const maxScore = computed(() => chosen.value.reduce((sum, g) => sum + counts[g.id] * g.scoreWeight, 0))
const canStart = computed(() => chosen.value.length > 0 && total.value <= MAX)

function setSelected(ids: number[], on: boolean) {
  const next = new Set(selected.value)
  for (const id of ids) on ? next.add(id) : next.delete(id)
  selected.value = next
}
// Fan belgilanganda barcha sinflari tanlanadi, keyin keraksizlarini olib tashlash mumkin
function toggleGroup(g: Group) {
  const ids = g.subjects.filter((s) => s.questionCount).map((s) => s.id)
  if (!ids.length) return
  setSelected(ids, !isChosen(g))
  fit(g)
}
function toggleSubject(g: Group, s: Subject) {
  if (!s.questionCount) return
  setSelected([s.id], !selected.value.has(s.id))
  fit(g)
}
// Savollar soni tanlangan sinflardagi savollardan oshmasin
function fit(g: Group) {
  const max = available(g)
  if (max) counts[g.id] = Math.min(Math.max(1, counts[g.id]), max)
}
function step(g: Group, d: number) {
  counts[g.id] = Math.min(Math.max(1, counts[g.id] + d), available(g))
}

onMounted(async () => {
  try {
    const byGroup = new Map<number, Group>()
    for (const s of await subjectApi.list()) {
      const g = byGroup.get(s.groupId) ?? { id: s.groupId, name: s.groupName, scoreWeight: s.scoreWeight, subjects: [] }
      g.subjects.push(s)
      byGroup.set(s.groupId, g)
    }
    groups.value = [...byGroup.values()].sort((a, b) => a.name.localeCompare(b.name))
    for (const g of groups.value) g.subjects.sort((a, b) => a.name.localeCompare(b.name, undefined, { numeric: true }))

    let prefs: { counts?: Record<number, number>; subjects?: number[] } = {}
    try {
      prefs = JSON.parse(localStorage.getItem(prefsKey) ?? '{}')
    } catch {
      /* sozlamalar yo'q */
    }
    const valid = groups.value.flatMap((g) => g.subjects).filter((s) => s.questionCount).map((s) => s.id)
    selected.value = new Set((prefs.subjects ?? []).filter((id) => valid.includes(id)))
    for (const g of groups.value) {
      counts[g.id] = Math.min(prefs.counts?.[g.id] ?? DEFAULT_COUNT, available(g) || groupTotal(g)) || 1
    }
  } catch (e) {
    message.error(errorMessage(e))
    router.replace('/')
  }
})

async function start() {
  if (!canStart.value) return
  starting.value = true
  try {
    try {
      localStorage.setItem(prefsKey, JSON.stringify({ counts, subjects: [...selected.value] }))
    } catch {
      /* saqlab bo'lmasa ham davom etamiz */
    }
    const { id } = await testApi.createBlock(
      chosen.value.map((g) => ({ groupId: g.id, subjectIds: chosenSubjects(g).map((s) => s.id), questionCount: counts[g.id] })),
    )
    router.replace(`/test/${id}`)
  } catch (e) {
    message.error(errorMessage(e))
  } finally {
    starting.value = false
  }
}
</script>

<template>
  <AppBar title="Blok test" />
  <main class="screen">
    <p class="muted intro">DTM uslubida: fanlarni tanlang, har bir fan alohida bo'limda, natija ballda hisoblanadi.</p>

    <div class="section-label">Fanlar</div>
    <div class="list">
      <div v-for="g in groups" :key="g.id" class="card subject" :class="{ on: isChosen(g), off: !groupTotal(g) }">
        <button class="subject-head" role="checkbox" :aria-checked="isChosen(g)" @click="toggleGroup(g)">
          <span class="tick"><NIcon v-if="isChosen(g)" :component="Checkmark" size="16" /></span>
          <span class="subject-text">
            <span class="subject-name">{{ g.name }}</span>
            <span class="muted small tabular">
              1 savol = {{ formatScore(g.scoreWeight) }} ball · {{ isChosen(g) ? available(g) : groupTotal(g) }} savol
            </span>
          </span>
        </button>
        <div v-if="isChosen(g) && hasGrades(g)" class="grades">
          <button
            v-for="s in g.subjects"
            :key="s.id"
            class="grade"
            :class="{ on: selected.has(s.id) }"
            :disabled="!s.questionCount"
            role="checkbox"
            :aria-checked="selected.has(s.id)"
            @click="toggleSubject(g, s)"
          >
            <NIcon v-if="selected.has(s.id)" :component="Checkmark" size="14" />
            {{ shortSubjectName(s.name, g.name) }}
          </button>
        </div>
        <div v-if="isChosen(g)" class="counter">
          <button class="round" aria-label="Kamaytirish" :disabled="counts[g.id] <= 1" @click="step(g, -1)">
            <NIcon :component="Remove" size="20" />
          </button>
          <div class="count tabular">{{ counts[g.id] }} <span class="muted small">ta</span></div>
          <button class="round" aria-label="Ko'paytirish" :disabled="counts[g.id] >= available(g)" @click="step(g, 1)">
            <NIcon :component="Add" size="20" />
          </button>
          <div class="sub-score muted small tabular">{{ formatScore(counts[g.id] * g.scoreWeight) }} ball</div>
        </div>
      </div>
    </div>

    <div v-if="chosen.length" class="card summary tabular">
      <div><b>{{ total }}</b><span class="muted small">savol</span></div>
      <div><b>{{ formatScore(maxScore) }}</b><span class="muted small">maksimal ball</span></div>
      <div><b>{{ formatDuration(chosen.length * SECONDS_PER_SUBJECT) }}</b><span class="muted small">vaqt</span></div>
    </div>
    <p v-if="total > MAX" class="warn small">Bitta testda ko'pi bilan {{ MAX }} ta savol bo'lishi mumkin.</p>
    <p class="muted small note">Har bir fanga 1 soat, vaqt tugaganda test avtomatik yakunlanadi. Bir nechta sinf tanlansa, savollar ular orasida teng taqsimlanadi. Javoblar va ball faqat oxirida ko'rsatiladi. Yakunlaguncha javobni o'zgartirish mumkin.</p>
  </main>

  <div class="bottom-bar">
    <div class="bottom-bar-inner">
      <button class="btn btn-primary btn-block" :disabled="!canStart || starting" @click="start">
        {{ chosen.length ? `Boshlash · ${total} ta savol` : 'Fan tanlang' }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.intro {
  margin: 4px 4px 0;
  font-size: 15px;
  line-height: 1.4;
}
.list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.subject {
  overflow: hidden;
  box-shadow: var(--shadow), inset 0 0 0 1.5px transparent;
}
.subject.on {
  box-shadow: inset 0 0 0 2px var(--ink);
}
.subject.off {
  opacity: 0.45;
}
.subject-head {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border: 0;
  background: transparent;
  text-align: left;
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
.subject.on .tick {
  background: var(--ink);
  border-color: var(--ink);
}
:root[data-theme='dark'] .subject.on .tick {
  color: #0f1430;
}
.subject-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.subject-name {
  font-weight: 650;
  color: var(--ink);
}
.small {
  font-size: 13px;
}
.grades {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 0 16px 12px 52px;
}
.grade {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 34px;
  padding: 0 14px;
  border-radius: 17px;
  border: 1.5px solid var(--line);
  background: transparent;
  font-weight: 600;
  font-size: 14px;
  color: var(--ink);
}
.grade.on {
  background: var(--ink);
  border-color: var(--ink);
  color: #fff;
}
:root[data-theme='dark'] .grade.on {
  color: #0f1430;
}
.grade:disabled {
  opacity: 0.4;
}
.counter {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 0 16px 14px 52px;
}
.round {
  width: 40px;
  height: 40px;
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
  font-size: 24px;
  font-weight: 650;
  color: var(--ink);
  min-width: 64px;
  text-align: center;
}
.sub-score {
  margin-left: auto;
}
.summary {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 8px;
  margin-top: 16px;
  padding: 14px 16px;
  background: var(--marker-soft);
}
.summary div {
  display: flex;
  flex-direction: column;
}
.summary b {
  font-family: var(--display);
  font-size: 24px;
  color: var(--ink);
}
.warn {
  color: #d03050;
  margin: 10px 4px 0;
}
.note {
  margin: 12px 4px 0;
  line-height: 1.4;
}
</style>
