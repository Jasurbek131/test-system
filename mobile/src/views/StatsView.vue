<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { statsApi } from '@/api'
import type { MyStats } from '@/types'

const router = useRouter()
const stats = ref<MyStats | null>(null)

const groups = computed(() => {
  const map = new Map<number, { id: number; name: string; topics: MyStats['byTopic'] }>()
  for (const t of stats.value?.byTopic ?? []) {
    if (!map.has(t.subjectId)) map.set(t.subjectId, { id: t.subjectId, name: t.subjectName, topics: [] })
    map.get(t.subjectId)!.topics.push(t)
  }
  return [...map.values()]
})
const weak = computed(() =>
  [...(stats.value?.byTopic ?? [])].filter((t) => t.answered >= 3 && t.percent < 70).sort((a, b) => a.percent - b.percent).slice(0, 4),
)
const subjectLine = (id: number) => stats.value?.bySubject.find((s) => s.subjectId === id)
const tone = (p: number) => (p >= 70 ? 'ok' : p >= 50 ? 'mid' : 'bad')
const clean = (s: string) => s.replace(/^\d+\.\s*/, '')

onMounted(async () => {
  stats.value = await statsApi.me()
})
</script>

<template>
  <main class="screen">
    <h1 class="screen-title" style="padding-top: 12px">Natijalarim</h1>

    <div v-if="stats && !stats.totalAnswered" class="empty">
      <p>Test yechganingizdan keyin bu yerda qaysi mavzularni yaxshi bilishingiz ko'rinadi.</p>
      <button class="btn btn-primary" @click="router.replace('/')">Fan tanlash</button>
    </div>

    <template v-else-if="stats">
      <div class="summary card">
        <div class="big tabular">{{ stats.overallPercent }}<small>%</small></div>
        <div class="summary-text">
          <div>to'g'ri javoblar</div>
          <div class="muted small tabular">{{ stats.totalCorrect }} / {{ stats.totalAnswered }} javob · {{ stats.totalTests }} test</div>
        </div>
      </div>

      <template v-if="weak.length">
        <div class="section-label">Takrorlash kerak</div>
        <button v-for="t in weak" :key="t.topicId" class="weak card" @click="router.push(`/subject/${t.subjectId}`)">
          <div class="weak-text">
            <div class="weak-name">{{ clean(t.topicName) }}</div>
            <div class="muted small">{{ t.subjectName }} · {{ t.correct }}/{{ t.answered }}</div>
          </div>
          <span class="pct bad tabular">{{ t.percent }}%</span>
        </button>
      </template>

      <template v-for="g in groups" :key="g.id">
        <div class="section-label">{{ g.name }} · {{ subjectLine(g.id)?.percent ?? 0 }}%</div>
        <div class="card rows">
          <div v-for="t in g.topics" :key="t.topicId" class="row">
            <div class="row-head">
              <span class="row-name">{{ clean(t.topicName) }}</span>
              <span class="tabular muted">{{ t.correct }}/{{ t.answered }}</span>
            </div>
            <div class="bar"><div class="bar-fill" :class="tone(t.percent)" :style="{ width: `${Math.max(t.percent, 3)}%` }" /></div>
          </div>
        </div>
      </template>
    </template>
  </main>
</template>

<style scoped>
.summary {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 18px;
  margin-top: 16px;
}
.big {
  font-family: var(--display);
  font-size: 44px;
  font-weight: 650;
  color: var(--ink);
  line-height: 1;
}
.big small {
  font-size: 22px;
}
.small {
  font-size: 13px;
}
.weak {
  width: 100%;
  border: 0;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  margin-bottom: 8px;
  text-align: left;
  border-left: 4px solid var(--bad);
}
.weak-text {
  flex: 1;
  min-width: 0;
}
.weak-name {
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pct {
  font-family: var(--display);
  font-weight: 600;
}
.pct.bad {
  color: var(--bad);
}
.rows {
  padding: 4px 16px;
}
.row {
  padding: 10px 0;
  border-bottom: 1px solid var(--line);
}
.row:last-child {
  border-bottom: 0;
}
.row-head {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  font-size: 14px;
  margin-bottom: 6px;
}
.row-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.bar {
  height: 8px;
  border-radius: 4px;
  background: var(--grid);
  overflow: hidden;
}
.bar-fill {
  height: 100%;
  border-radius: 4px;
}
.bar-fill.ok {
  background: var(--ok);
}
.bar-fill.mid {
  background: var(--marker);
}
.bar-fill.bad {
  background: var(--bad);
}
.empty {
  text-align: center;
  margin-top: 48px;
  color: var(--muted);
  padding: 0 12px;
}
</style>
