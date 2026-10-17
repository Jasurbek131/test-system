<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NCard, NEmpty, NGrid, NGridItem, NProgress, NSkeleton, NTag, useMessage } from 'naive-ui'
import { progressApi, subjectApi, testApi } from '@/api'
import { errorMessage } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import type { ProgressOverview, SessionSummary, Subject } from '@/types'
import { modeLabel } from '@/utils/format'
import { loadLastTest } from '@/utils/learning'
import { dailyGoal, dayStreak, levelInfo, todayActivity } from '@/utils/gamification'

const router = useRouter()
const route = useRoute()
const message = useMessage()
const auth = useAuthStore()
const subjects = ref<Subject[]>([])
const loading = ref(true)

// Tezkor boshlash: tugallanmagan test va oxirgi sozlamalar bilan yangi test
const inProgress = ref<SessionSummary | null>(null)
const lastTest = loadLastTest()
const quickStarting = ref(false)
const lastSubjectExists = computed(() => !!lastTest && subjects.value.some((s) => s.id === lastTest.subjectId))

// Bugungi kun: kunlik maqsad, ketma-ket kunlar, daraja va takrorlash
const overview = ref<ProgressOverview | null>(null)
const today = computed(() => todayActivity(overview.value?.days ?? []))
const streak = computed(() => dayStreak(overview.value?.days ?? []))
const lvl = computed(() => levelInfo(overview.value?.xp ?? 0))
const goalPercent = computed(() => Math.min(100, Math.round((today.value.answered / dailyGoal.value) * 100)))
const dueSubjects = computed(() => (overview.value?.subjects ?? []).filter((s) => s.due > 0))
const reviewing = ref<number | null>(null)

async function startReview(subjectId: number) {
  reviewing.value = subjectId
  try {
    const { id } = await testApi.createReview({ subjectId, kind: 'DUE', questionCount: 20, mode: 'LEARNING' })
    router.push(`/test/${id}`)
  } catch (e) {
    message.error(errorMessage(e))
  } finally {
    reviewing.value = null
  }
}

onMounted(async () => {
  progressApi.overview().then((o) => (overview.value = o), () => {})
  const history = testApi.history(0, 10).catch(() => null)
  try {
    subjects.value = await subjectApi.list()
  } catch (e) {
    message.error(errorMessage(e))
  } finally {
    loading.value = false
  }
  inProgress.value = (await history)?.items.find((s) => s.status === 'IN_PROGRESS') ?? null
})

async function repeatLast() {
  if (!lastTest) return
  quickStarting.value = true
  try {
    const { id } = await testApi.create({
      subjectId: lastTest.subjectId,
      topicIds: lastTest.topicIds,
      questionCount: lastTest.questionCount,
      mode: lastTest.mode,
    })
    router.push(`/test/${id}`)
  } catch {
    // Mavzu o'chirilgan yoki savollar kamaygan bo'lishi mumkin – sozlash sahifasida tuzatish imkoni
    router.push({
      path: '/test/new',
      query: {
        subjectId: lastTest.subjectId,
        topicIds: lastTest.topicIds.join(','),
        mode: lastTest.mode,
        count: lastTest.questionCount,
      },
    })
  } finally {
    quickStarting.value = false
  }
}

const start = (s: Subject) => router.push({ path: '/test/new', query: { subjectId: s.id } })

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
const openGroup = (g: Group) => {
  if (g.subjects.length === 1) start(g.subjects[0])
  else router.push({ query: { group: g.id } })
}
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1>Fanlar</h1>
        <div class="muted">Fanni va sinfni tanlang, mavzularni belgilang va test yechib o'rganing</div>
      </div>
    </div>

    <NCard v-if="overview && !selectedGroup" class="today" size="small">
      <div class="today-grid">
        <div class="today-item">
          <NProgress type="circle" :percentage="goalPercent" :stroke-width="10" status="success" style="width: 64px">
            <span class="tabular goal-num">{{ today.answered }}</span>
          </NProgress>
          <div>
            <div class="today-title">{{ goalPercent >= 100 ? '🎉 Maqsad bajarildi!' : 'Kunlik maqsad' }}</div>
            <div class="muted small tabular">{{ today.answered }} / {{ dailyGoal }} ta savol</div>
          </div>
        </div>
        <div class="today-item">
          <span class="big-emoji" :class="{ dim: !streak.todayDone }">🔥</span>
          <div>
            <div class="today-title tabular">{{ streak.current }} kun ketma-ket</div>
            <div class="muted small">
              {{ streak.todayDone ? 'Bugun ham o\'qidingiz' : streak.current ? 'Bugun o\'qib, davom ettiring!' : 'Bugundan boshlang' }}
            </div>
          </div>
        </div>
        <div class="today-item level" @click="router.push('/achievements')">
          <span class="level-badge tabular">{{ lvl.level }}</span>
          <div class="level-body">
            <div class="today-title">{{ lvl.title }}</div>
            <NProgress type="line" :percentage="lvl.percent" :show-indicator="false" :height="6" />
            <div class="muted small tabular">{{ lvl.current }} / {{ lvl.needed }} XP</div>
          </div>
        </div>
      </div>
      <div v-if="dueSubjects.length" class="due-row">
        <span class="due-label">🔁 Takrorlash vaqti keldi:</span>
        <NButton
          v-for="s in dueSubjects"
          :key="s.subjectId"
          size="small"
          secondary
          type="primary"
          :loading="reviewing === s.subjectId"
          @click="startReview(s.subjectId)"
        >
          {{ s.subjectName }} · {{ s.due }}
        </NButton>
        <NButton v-if="overview.mistakesCount" size="small" quaternary @click="router.push('/mistakes')">
          Xatolar daftari ({{ overview.mistakesCount }})
        </NButton>
      </div>
    </NCard>

    <div v-if="!selectedGroup && (inProgress || (lastTest && lastSubjectExists))" class="quick">
      <NCard v-if="inProgress" size="small" class="quick-card continue" @click="router.push(`/test/${inProgress.id}`)">
        <div class="quick-body">
          <div class="quick-text">
            <div class="quick-title">Davom ettirish</div>
            <div class="muted quick-sub">
              {{ inProgress.mode === 'BLOCK' ? 'Blok test' : inProgress.subjectName }} · {{ modeLabel[inProgress.mode] }} ·
              <span class="tabular">{{ inProgress.answeredCount }} / {{ inProgress.questionCount }}</span>
            </div>
          </div>
          <NButton type="primary" @click.stop="router.push(`/test/${inProgress.id}`)">Davom etish →</NButton>
        </div>
      </NCard>
      <NCard v-if="lastTest && lastSubjectExists" size="small" class="quick-card" @click="repeatLast">
        <div class="quick-body">
          <div class="quick-text">
            <div class="quick-title">Yana bir test</div>
            <div class="muted quick-sub">
              {{ lastTest.subjectName }} · {{ modeLabel[lastTest.mode] }} ·
              <span class="tabular">{{ lastTest.questionCount }} ta savol, {{ lastTest.topicIds.length }} ta mavzu</span>
            </div>
          </div>
          <NButton secondary type="primary" :loading="quickStarting" @click.stop="repeatLast">Boshlash</NButton>
        </div>
      </NCard>
    </div>

    <NCard v-if="!loading && subjects.length > 1" class="block-banner" @click="router.push('/block/new')">
      <div class="block-banner-body">
        <div>
          <div class="subject-name">Blok test (DTM uslubida)</div>
          <div class="muted">Bir nechta fanni tanlang, har bir fan alohida bo'limda, natija ballda hisoblanadi</div>
        </div>
        <NButton type="primary" @click.stop="router.push('/block/new')">Blok testni boshlash</NButton>
      </div>
    </NCard>

    <NGrid v-if="loading" cols="1 s:2 l:3" responsive="screen" :x-gap="16" :y-gap="16">
      <NGridItem v-for="i in 3" :key="i">
        <NCard><NSkeleton text :repeat="3" /></NCard>
      </NGridItem>
    </NGrid>

    <NEmpty v-else-if="!subjects.length" description="Hozircha fanlar yo'q" style="margin-top: 60px">
      <template v-if="auth.isAdmin" #extra>
        <NButton type="primary" @click="router.push('/admin/subjects')">Fan qo'shish</NButton>
      </template>
    </NEmpty>

    <template v-else-if="selectedGroup">
      <div class="group-header">
        <NButton quaternary @click="router.push({ query: {} })">← Barcha fanlar</NButton>
        <h2>{{ selectedGroup.name }}</h2>
      </div>
      <NGrid cols="1 s:2 l:3" responsive="screen" :x-gap="16" :y-gap="16">
        <NGridItem v-for="s in selectedGroup.subjects" :key="s.id">
          <NCard hoverable class="subject-card" @click="s.questionCount && start(s)">
            <div class="subject-name">{{ s.name }}</div>
            <p class="muted description">{{ s.description || 'Tavsif kiritilmagan' }}</p>
            <div class="meta">
              <NTag size="small" :bordered="false">{{ s.topicCount }} ta mavzu</NTag>
              <NTag size="small" :bordered="false" type="info">{{ s.questionCount }} ta savol</NTag>
            </div>
            <template #action>
              <NButton type="primary" block :disabled="!s.questionCount" @click.stop="start(s)">
                {{ s.questionCount ? 'Test boshlash' : "Savollar yo'q" }}
              </NButton>
            </template>
          </NCard>
        </NGridItem>
      </NGrid>
    </template>

    <NGrid v-else cols="1 s:2 l:3" responsive="screen" :x-gap="16" :y-gap="16">
      <NGridItem v-for="g in groups" :key="g.id">
        <NCard hoverable class="subject-card" @click="g.questionCount && openGroup(g)">
          <div class="subject-name">{{ g.name }}</div>
          <p class="muted description">{{ g.subjects.map((s) => s.name).join(', ') }}</p>
          <div class="meta">
            <NTag size="small" :bordered="false">{{ g.subjects.length }} ta fan</NTag>
            <NTag size="small" :bordered="false" type="info">{{ g.questionCount }} ta savol</NTag>
          </div>
          <template #action>
            <NButton type="primary" block :disabled="!g.questionCount" @click.stop="openGroup(g)">
              {{ !g.questionCount ? "Savollar yo'q" : g.subjects.length > 1 ? 'Sinfni tanlash' : 'Test boshlash' }}
            </NButton>
          </template>
        </NCard>
      </NGridItem>
    </NGrid>
  </div>
</template>

<style scoped>
.today {
  margin-bottom: 16px;
}
.today-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 16px;
}
.today-item {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}
.today-item.level {
  cursor: pointer;
}
.today-title {
  font-weight: 600;
}
.small {
  font-size: 13px;
}
.goal-num {
  font-weight: 650;
  font-size: 16px;
}
.big-emoji {
  font-size: 40px;
  line-height: 1;
  width: 64px;
  text-align: center;
}
.big-emoji.dim {
  filter: grayscale(1);
  opacity: 0.45;
}
.level-badge {
  flex: none;
  width: 56px;
  height: 56px;
  margin: 4px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, #0d9488, #6366f1);
}
.level-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.due-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid rgba(127, 127, 127, 0.15);
}
.due-label {
  font-weight: 600;
  font-size: 14px;
}
.quick {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}
.quick-card {
  cursor: pointer;
}
.quick-card.continue {
  border-color: rgba(13, 148, 136, 0.5);
  background: rgba(13, 148, 136, 0.06);
}
.quick-body {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.quick-text {
  flex: 1;
  min-width: 0;
}
.quick-title {
  font-weight: 600;
  font-size: 16px;
}
.quick-sub {
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.block-banner {
  cursor: pointer;
  margin-bottom: 16px;
}
.block-banner-body {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.group-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.group-header h2 {
  margin: 0;
  font-size: 20px;
}
.subject-card {
  cursor: pointer;
  height: 100%;
}
.subject-name {
  font-size: 18px;
  font-weight: 600;
}
.description {
  min-height: 42px;
  margin: 8px 0 14px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.meta {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
