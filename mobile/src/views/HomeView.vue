<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NIcon } from 'naive-ui'
import { ChevronBack, ChevronForward, Moon, PlayCircle, Refresh, School, Sunny } from '@vicons/ionicons5'
import { progressApi, subjectApi, testApi } from '@/api'
import type { ProgressOverview, SessionSummary, Subject } from '@/types'
import { useThemeStore } from '@/stores/theme'
import SubjectBadge from '@m/components/SubjectBadge.vue'
import { gradeLabel } from '@m/utils/grade'
import { modeLabel } from '@/utils/format'
import { loadLastTest } from '@/utils/learning'
import { dailyGoal, dayStreak, levelInfo, todayActivity } from '@/utils/gamification'

const router = useRouter()
const route = useRoute()
const theme = useThemeStore()
const subjects = ref<Subject[]>([])
const unfinished = ref<SessionSummary | null>(null)
const loaded = ref(false)
const lastTest = loadLastTest()

// Bugungi kun: kunlik maqsad, ketma-ket kunlar, daraja, takrorlash
const overview = ref<ProgressOverview | null>(null)
const today = computed(() => todayActivity(overview.value?.days ?? []))
const streak = computed(() => dayStreak(overview.value?.days ?? []))
const lvl = computed(() => levelInfo(overview.value?.xp ?? 0))
const goalPercent = computed(() => Math.min(100, Math.round((today.value.answered / dailyGoal.value) * 100)))
const dueSubjects = computed(() => (overview.value?.subjects ?? []).filter((s) => s.due > 0))

async function startReview(subjectId: number) {
  if (repeating.value) return
  repeating.value = true
  try {
    const { id } = await testApi.createReview({ subjectId, kind: 'DUE', questionCount: 20, mode: 'LEARNING' })
    router.push(`/test/${id}`)
  } catch {
    router.push(`/subject/${subjectId}`)
  } finally {
    repeating.value = false
  }
}
const repeating = ref(false)
const lastSubjectExists = computed(() => !!lastTest && subjects.value.some((s) => s.id === lastTest.subjectId))

// Oxirgi sozlamalar bilan bir bosishda yangi test; mavzular o'zgargan bo'lsa – sozlash sahifasi
async function repeatLast() {
  if (!lastTest || repeating.value) return
  repeating.value = true
  try {
    const { id } = await testApi.create({
      subjectId: lastTest.subjectId,
      topicIds: lastTest.topicIds,
      questionCount: lastTest.questionCount,
      mode: lastTest.mode,
    })
    router.push(`/test/${id}`)
  } catch {
    router.push(`/subject/${lastTest.subjectId}`)
  } finally {
    repeating.value = false
  }
}

interface Group {
  id: number
  name: string
  subjects: Subject[]
  questionCount: number
}

const gradeOf = (s: Subject) => Number(s.name.match(/^(\d+)-sinf/i)?.[1] ?? 0)

// Fanlar guruhlarga (masalan, «Matematika» – 1-sinf, 2-sinf) birlashtiriladi, ichida sinf bo'yicha tartiblanadi
const groups = computed<Group[]>(() => {
  const map = new Map<number, Group>()
  for (const s of subjects.value) {
    let g = map.get(s.groupId)
    if (!g) map.set(s.groupId, (g = { id: s.groupId, name: s.groupName, subjects: [], questionCount: 0 }))
    g.subjects.push(s)
    g.questionCount += s.questionCount
  }
  for (const g of map.values()) g.subjects.sort((a, b) => gradeOf(a) - gradeOf(b) || a.name.localeCompare(b.name))
  return [...map.values()]
})

const selectedGroup = computed(() => groups.value.find((g) => String(g.id) === route.query.group) ?? null)

// Guruhda bitta fan bo'lsa, sinf tanlash bosqichi o'tkazib yuboriladi
function openGroup(g: Group) {
  if (g.subjects.length === 1) router.push(`/subject/${g.subjects[0].id}`)
  else router.push({ query: { group: g.id } })
}

onMounted(async () => {
  progressApi.overview().then((o) => (overview.value = o), () => {})
  const [list, hist] = await Promise.all([subjectApi.list(), testApi.history(0, 20)])
  subjects.value = list
  unfinished.value = hist.items.find((s) => s.status === 'IN_PROGRESS') ?? null
  loaded.value = true
})
</script>

<template>
  <main class="screen">
    <div class="top">
      <h1 class="home-title">Nimani o'rganamiz?</h1>
      <button class="theme" :aria-label="theme.dark ? 'Yorug\' rejim' : 'Qorong\'i rejim'" @click="theme.toggle">
        <NIcon :component="theme.dark ? Sunny : Moon" size="22" />
      </button>
    </div>

    <button v-if="overview" class="strip" @click="router.push('/achievements')">
      <span class="strip-item tabular" :class="{ done: goalPercent >= 100 }">🎯 {{ today.answered }}/{{ dailyGoal }}</span>
      <span class="strip-item tabular" :class="{ dim: !streak.todayDone }">🔥 {{ streak.current }}</span>
      <span class="strip-item tabular">⭐ {{ lvl.level }}-daraja</span>
      <span class="strip-bar"><span class="strip-fill" :style="{ width: `${goalPercent}%` }" /></span>
    </button>

    <button v-if="unfinished" class="resume card" @click="router.push(`/test/${unfinished.id}`)">
      <NIcon :component="PlayCircle" size="40" class="resume-icon" />
      <div class="resume-text">
        <div class="resume-title">Testni davom ettirish</div>
        <div class="muted small">
          {{ unfinished.mode === 'BLOCK' ? 'Blok test' : unfinished.subjectName }} · {{ unfinished.answeredCount }} / {{ unfinished.questionCount }} javob
        </div>
      </div>
      <NIcon :component="ChevronForward" size="20" class="muted" />
    </button>


    <template v-if="selectedGroup">
      <button class="back-link" @click="router.back()">
        <NIcon :component="ChevronBack" size="18" /> Barcha fanlar
      </button>
      <div class="section-label">{{ selectedGroup.name }} · sinfni tanlang</div>
      <div class="list">
        <button v-for="s in selectedGroup.subjects" :key="s.id" class="subject card" @click="router.push(`/subject/${s.id}`)">
          <SubjectBadge :id="s.id" :name="s.name" />
          <div class="subject-text">
            <div class="subject-name">{{ gradeLabel(s.name) || s.name }}</div>
            <div class="muted small tabular">{{ s.topicCount }} mavzu · {{ s.questionCount }} savol</div>
          </div>
          <NIcon :component="ChevronForward" size="20" class="muted" />
        </button>
      </div>
    </template>

    <template v-else>
      <div class="section-label">Fanlar</div>
      <div v-if="!loaded" class="skeleton card" />
      <div class="list">
        <button v-for="g in groups" :key="g.id" class="subject card" @click="openGroup(g)">
          <SubjectBadge :id="g.subjects[0].id" :name="g.name" />
          <div class="subject-text">
            <div class="subject-name">{{ g.name }}</div>
            <div class="muted small tabular">
              {{ g.subjects.length > 1 ? `${g.subjects.length} sinf · ` : '' }}{{ g.questionCount }} savol
            </div>
          </div>
          <NIcon :component="ChevronForward" size="20" class="muted" />
        </button>
      </div>

      <div v-if="subjects.length > 1" class="section-label">Blok test</div>
      <button v-if="subjects.length > 1" class="block-entry card" @click="router.push('/block')">
        <span class="block-icon"><NIcon :component="School" size="26" /></span>
        <div class="subject-text">
          <div class="subject-name">Blok test</div>
          <div class="muted small">DTM uslubida: bir nechta fan, natija ballda</div>
        </div>
        <NIcon :component="ChevronForward" size="20" class="muted" />
      </button>

      <template v-if="dueSubjects.length || overview?.mistakesCount || (lastTest && lastSubjectExists)">
        <div class="section-label">Takrorlash</div>
        <div v-if="dueSubjects.length || overview?.mistakesCount" class="due-chips">
          <button v-for="d in dueSubjects" :key="d.subjectId" class="due-chip" :disabled="repeating" @click="startReview(d.subjectId)">
            🔁 {{ d.subjectName }} · {{ d.due }}
          </button>
          <button v-if="overview?.mistakesCount" class="due-chip ghost" @click="router.push('/mistakes')">
            Xatolar · {{ overview.mistakesCount }}
          </button>
        </div>
    <button v-if="lastTest && lastSubjectExists" class="repeat card" :disabled="repeating" @click="repeatLast">
      <NIcon :component="Refresh" size="32" class="resume-icon" />
      <div class="resume-text">
        <div class="resume-title">Yana bir test</div>
        <div class="muted small resume-sub">
          {{ lastTest.subjectName }} · {{ modeLabel[lastTest.mode] }} · {{ lastTest.questionCount }} savol
        </div>
      </div>
      <NIcon :component="ChevronForward" size="20" class="muted" />
    </button>

      </template>
    </template>
  </main>
</template>

<style scoped>
.top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding-top: 14px;
}
.home-title {
  flex: 1;
  min-width: 0;
  margin: 0;
  font-family: var(--display);
  font-size: 18px;
  font-weight: 650;
  letter-spacing: -0.01em;
  color: var(--ink);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.theme {
  flex: none;
  width: 40px;
  height: 40px;
  border: 0;
  border-radius: 12px;
  background: var(--card);
  color: var(--ink);
  box-shadow: var(--shadow);
  display: flex;
  align-items: center;
  justify-content: center;
}
.small {
  font-size: 14px;
}
.strip {
  position: relative;
  width: 100%;
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 9px 14px 12px;
  border: 0;
  border-radius: 14px;
  background: var(--card);
  box-shadow: var(--shadow);
  overflow: hidden;
  text-align: left;
}
.strip-item {
  font-weight: 650;
  font-size: 14px;
  color: var(--ink);
}
.strip-item.done {
  color: var(--ok);
}
.strip-item.dim {
  opacity: 0.5;
}
.strip-bar {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 3px;
  background: var(--grid);
}
.strip-fill {
  display: block;
  height: 100%;
  background: var(--ok);
}
.due-chips {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.due-chip {
  height: 36px;
  padding: 0 14px;
  border-radius: 18px;
  border: 0;
  background: var(--ink);
  color: #fff;
  font-weight: 600;
  font-size: 14px;
}
:root[data-theme='dark'] .due-chip {
  color: #0f1430;
}
.due-chip.ghost {
  background: transparent;
  color: var(--ink);
  box-shadow: inset 0 0 0 1.5px var(--line);
}
.resume {
  margin-top: 18px;
  width: 100%;
  border: 0;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  text-align: left;
  background: var(--marker-soft);
  box-shadow: inset 0 0 0 1.5px var(--marker);
}
.repeat {
  margin-top: 10px;
  width: 100%;
  border: 0;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  text-align: left;
}
.repeat:active {
  transform: scale(0.99);
}
.resume-sub {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.resume-icon {
  color: var(--ink);
}
.resume-text {
  flex: 1;
  min-width: 0;
}
.resume-title {
  font-weight: 650;
  color: var(--ink);
}
.list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.subject {
  width: 100%;
  border: 0;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 14px 14px 14px;
  text-align: left;
  cursor: pointer;
}
.subject:active,
.resume:active {
  transform: scale(0.99);
}
.subject-text {
  flex: 1;
  min-width: 0;
}
.subject-name {
  font-weight: 650;
  font-size: 17px;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.block-entry {
  width: 100%;
  border: 0;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px;
  text-align: left;
}
.block-entry:active {
  transform: scale(0.99);
}
.block-icon {
  width: 48px;
  height: 48px;
  flex: none;
  border-radius: 14px;
  background: var(--ink);
  color: var(--card);
  display: flex;
  align-items: center;
  justify-content: center;
}
.back-link {
  margin-top: 16px;
  border: 0;
  background: none;
  color: var(--ink);
  font-size: 15px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 0;
  cursor: pointer;
}
.skeleton {
  height: 80px;
  opacity: 0.6;
}
</style>
