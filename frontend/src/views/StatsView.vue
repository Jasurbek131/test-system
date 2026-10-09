<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NCard, NEmpty, NGrid, NGridItem, NProgress, NSkeleton, NStatistic, useMessage } from 'naive-ui'
import { statsApi } from '@/api'
import { errorMessage } from '@/api/http'
import type { MyStats } from '@/types'
import { scoreStatus } from '@/utils/format'

const router = useRouter()
const message = useMessage()
const stats = ref<MyStats | null>(null)

const topicsBySubject = computed(() => {
  const groups = new Map<number, { name: string; topics: MyStats['byTopic'] }>()
  for (const t of stats.value?.byTopic ?? []) {
    if (!groups.has(t.subjectId)) groups.set(t.subjectId, { name: t.subjectName, topics: [] })
    groups.get(t.subjectId)!.topics.push(t)
  }
  return [...groups.entries()].map(([id, g]) => ({ id, ...g }))
})

const weakTopics = computed(() =>
  [...(stats.value?.byTopic ?? [])].filter((t) => t.answered >= 3 && t.percent < 70).sort((a, b) => a.percent - b.percent).slice(0, 5),
)

onMounted(async () => {
  try {
    stats.value = await statsApi.me()
  } catch (e) {
    message.error(errorMessage(e))
  }
})
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1>Statistika</h1>
    </div>

    <NCard v-if="!stats"><NSkeleton text :repeat="6" /></NCard>

    <NEmpty v-else-if="!stats.totalAnswered" description="Statistika hali yo'q. Test yechib ko'ring!" style="margin-top: 60px">
      <template #extra>
        <NButton type="primary" @click="router.push('/')">Test boshlash</NButton>
      </template>
    </NEmpty>

    <template v-else>
      <NGrid cols="2 m:4" responsive="screen" :x-gap="16" :y-gap="16" style="margin-bottom: 16px">
        <NGridItem><NCard size="small"><NStatistic label="Testlar" :value="stats.totalTests" /></NCard></NGridItem>
        <NGridItem><NCard size="small"><NStatistic label="Yakunlangan" :value="stats.finishedTests" /></NCard></NGridItem>
        <NGridItem>
          <NCard size="small"><NStatistic label="Javoblar" :value="`${stats.totalCorrect} / ${stats.totalAnswered}`" /></NCard>
        </NGridItem>
        <NGridItem>
          <NCard size="small">
            <NStatistic label="To'g'ri javoblar" :value="stats.overallPercent">
              <template #suffix>%</template>
            </NStatistic>
          </NCard>
        </NGridItem>
      </NGrid>

      <NCard v-if="weakTopics.length" title="Takrorlash kerak bo'lgan mavzular" size="small" style="margin-bottom: 16px">
        <div v-for="t in weakTopics" :key="t.topicId" class="row">
          <div class="row-head">
            <span>{{ t.topicName }} <span class="muted">· {{ t.subjectName }}</span></span>
            <NButton size="tiny" @click="router.push({ path: '/test/new', query: { subjectId: t.subjectId } })">
              Mashq qilish
            </NButton>
          </div>
          <NProgress type="line" :percentage="t.percent" :status="scoreStatus(t.percent)" :show-indicator="false" :height="8" />
        </div>
      </NCard>

      <NCard v-for="g in topicsBySubject" :key="g.id" :title="g.name" size="small" style="margin-bottom: 16px">
        <template #header-extra>
          <span class="muted tabular">
            <template v-for="s in stats.bySubject.filter((x) => x.subjectId === g.id)" :key="s.subjectId">
              {{ s.tests }} ta test · {{ s.correct }}/{{ s.answered }} · {{ s.percent }}%
            </template>
          </span>
        </template>
        <div v-for="t in g.topics" :key="t.topicId" class="row">
          <div class="row-head">
            <span>{{ t.topicName }}</span>
            <span class="muted tabular">{{ t.correct }} / {{ t.answered }}</span>
          </div>
          <NProgress type="line" :percentage="t.percent" :status="scoreStatus(t.percent)" :show-indicator="false" :height="8" />
        </div>
      </NCard>
    </template>
  </div>
</template>

<style scoped>
.row + .row {
  margin-top: 12px;
}
.row-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  font-size: 14px;
  margin-bottom: 4px;
}
</style>
