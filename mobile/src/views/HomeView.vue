<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { NIcon } from 'naive-ui'
import { ChevronForward, Moon, PlayCircle, School, Sunny } from '@vicons/ionicons5'
import { subjectApi, testApi } from '@/api'
import type { SessionSummary, Subject } from '@/types'
import { useThemeStore } from '@/stores/theme'
import SubjectBadge from '@m/components/SubjectBadge.vue'
import { gradeLabel } from '@m/utils/grade'

const router = useRouter()
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

    <div class="section-label">Fanlar</div>
    <div v-if="!loaded" class="skeleton card" />
    <div class="list">
      <button v-for="s in subjects" :key="s.id" class="subject card" @click="router.push(`/subject/${s.id}`)">
        <SubjectBadge :id="s.id" :name="s.name" />
        <div class="subject-text">
          <div class="subject-name">
            {{ s.name.replace(/^\d+-sinf\s+/i, '') }}
            <span v-if="gradeLabel(s.name)" class="grade-chip">{{ gradeLabel(s.name) }}</span>
          </div>
          <div class="muted small tabular">{{ s.topicCount }} mavzu · {{ s.questionCount }} savol</div>
        </div>
        <NIcon :component="ChevronForward" size="20" class="muted" />
      </button>
    </div>
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
.grade-chip {
  font-size: 12px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--grid);
  color: var(--ink);
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
.skeleton {
  height: 80px;
  opacity: 0.6;
}
</style>
