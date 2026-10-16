<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NIcon } from 'naive-ui'
import { ChevronBack, ChevronForward, Moon, PlayCircle, School, Sunny } from '@vicons/ionicons5'
import { subjectApi, testApi } from '@/api'
import type { SessionSummary, Subject } from '@/types'
import { useThemeStore } from '@/stores/theme'
import SubjectBadge from '@m/components/SubjectBadge.vue'
import { gradeLabel } from '@m/utils/grade'

const router = useRouter()
const route = useRoute()
const theme = useThemeStore()
const subjects = ref<Subject[]>([])
const unfinished = ref<SessionSummary | null>(null)
const loaded = ref(false)

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 11) return 'Xayrli tong!'
  if (h < 18) return 'Xayrli kun!'
  return 'Xayrli kech!'
})

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
  const [list, hist] = await Promise.all([subjectApi.list(), testApi.history(0, 20)])
  subjects.value = list
  unfinished.value = hist.items.find((s) => s.status === 'IN_PROGRESS') ?? null
  loaded.value = true
})
</script>

<template>
  <main class="screen">
    <div class="top">
      <div>
        <div class="hello muted">{{ greeting }}</div>
        <h1 class="screen-title">Nimani o'rganamiz?</h1>
      </div>
      <button class="theme" :aria-label="theme.dark ? 'Yorug\' rejim' : 'Qorong\'i rejim'" @click="theme.toggle">
        <NIcon :component="theme.dark ? Sunny : Moon" size="22" />
      </button>
    </div>

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

    <button v-if="subjects.length > 1" class="block-entry card" @click="router.push('/block')">
      <span class="block-icon"><NIcon :component="School" size="26" /></span>
      <div class="subject-text">
        <div class="subject-name">Blok test</div>
        <div class="muted small">DTM uslubida: bir nechta fan, natija ballda</div>
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
    </template>
  </main>
</template>

<style scoped>
.top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding-top: 12px;
}
.hello {
  font-size: 15px;
  margin-top: 12px;
}
.theme {
  margin-top: 8px;
  width: 44px;
  height: 44px;
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
  margin-top: 14px;
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
