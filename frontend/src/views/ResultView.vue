<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  NAlert,
  NButton,
  NCard,
  NEmpty,
  NGrid,
  NGridItem,
  NProgress,
  NRadioButton,
  NRadioGroup,
  NSkeleton,
  NSpace,
  NStatistic,
  NTag,
  useMessage,
} from 'naive-ui'
import OptionList from '@/components/OptionList.vue'
import { testApi } from '@/api'
import { errorMessage } from '@/api/http'
import type { QuestionView, ResultView } from '@/types'
import { formatDate, formatDuration, formatScore, modeLabel, scoreStatus, shortSubjectName } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const message = useMessage()

const id = Number(route.params.id)
const result = ref<ResultView | null>(null)
const filter = ref<'all' | 'wrong' | 'correct' | 'unanswered'>('all')
const retrying = ref(false)

const status = computed(() => scoreStatus(result.value?.scorePercent ?? 0))
const verdict = computed(() => {
  const p = result.value?.scorePercent ?? 0
  if (p >= 90) return "A'lo natija! Mavzularni yaxshi o'zlashtirgansiz."
  if (p >= 70) return 'Yaxshi natija. Xatolar ustida ishlab, yanada yaxshilash mumkin.'
  if (p >= 50) return "O'rtacha natija. Kuchsiz mavzularni qayta takrorlang."
  return "Mavzularni qayta o'rganish tavsiya etiladi. Xatolarni qayta yechib ko'ring."
})

function qState(q: QuestionView) {
  if (!q.answered) return 'unanswered'
  return q.correct ? 'correct' : 'wrong'
}

const filtered = computed(() => {
  const qs = result.value?.questions ?? []
  if (filter.value === 'all') return qs.map((q, i) => ({ q, i }))
  return qs.map((q, i) => ({ q, i })).filter(({ q }) => qState(q) === filter.value)
})

const isBlock = computed(() => result.value?.mode === 'BLOCK')
const newTestLink = computed(() =>
  isBlock.value || !result.value ? '/block/new' : { path: '/test/new', query: { subjectId: result.value.subjectId } },
)

const toRetry = computed(() => (result.value ? result.value.wrongCount + result.value.unansweredCount : 0))

onMounted(async () => {
  try {
    result.value = await testApi.result(id)
  } catch (e) {
    message.error(errorMessage(e))
    router.replace(`/test/${id}`)
  }
})

async function retryWrong() {
  retrying.value = true
  try {
    const { id: newId } = await testApi.retryWrong(id)
    router.push(`/test/${newId}`)
  } catch (e) {
    message.error(errorMessage(e))
  } finally {
    retrying.value = false
  }
}
</script>

<template>
  <div class="page page-narrow">
    <NCard v-if="!result"><NSkeleton text :repeat="8" /></NCard>

    <template v-else>
      <div class="page-header">
        <div>
          <h1>Natija: {{ isBlock ? 'Blok test' : result.subjectName }}</h1>
          <div class="muted">
            {{ modeLabel[result.mode] }} rejimi · {{ formatDate(result.finishedAt) }} ·
            {{ formatDuration(result.durationSeconds) }}<template v-if="result.timeLimitSeconds">
              / {{ formatDuration(result.timeLimitSeconds) }}</template>
          </div>
        </div>
      </div>

      <NCard style="margin-bottom: 16px">
        <div class="score">
          <NProgress
            type="circle"
            :percentage="result.scorePercent"
            :status="status"
            :stroke-width="9"
            style="width: 130px"
          >
            <div v-if="isBlock" class="tabular score-center">
              <div class="score-value">{{ formatScore(result.score) }}</div>
              <div class="muted score-max">/ {{ formatScore(result.maxScore) }} ball</div>
            </div>
            <div v-else class="score-value tabular">{{ result.scorePercent }}%</div>
          </NProgress>
          <div class="score-body">
            <div v-if="isBlock" class="score-title tabular">
              {{ formatScore(result.score) }} ball <span class="muted">({{ result.scorePercent }}%)</span>
            </div>
            <NGrid cols="3" :x-gap="12">
              <NGridItem><NStatistic label="To'g'ri" :value="result.correctCount" /></NGridItem>
              <NGridItem><NStatistic label="Xato" :value="result.wrongCount" /></NGridItem>
              <NGridItem><NStatistic label="Javobsiz" :value="result.unansweredCount" /></NGridItem>
            </NGrid>
            <p class="verdict">{{ verdict }}</p>
          </div>
        </div>
        <template #action>
          <NSpace>
            <NButton v-if="toRetry > 0" type="primary" :loading="retrying" @click="retryWrong">
              Xatolarni qayta yechish ({{ toRetry }})
            </NButton>
            <NButton @click="router.push(newTestLink)">Yangi test</NButton>
            <NButton quaternary @click="router.push('/history')">Tarix</NButton>
          </NSpace>
        </template>
      </NCard>

      <NCard v-if="isBlock" title="Fanlar bo'yicha" size="small" style="margin-bottom: 16px">
        <div v-for="sr in result.byGroup" :key="sr.groupId" class="topic-row">
          <div class="topic-head">
            <span>
              <b>{{ sr.groupName }}</b>
              <span class="muted"> · {{ sr.subjectNames.map((n) => shortSubjectName(n, sr.groupName)).join(', ') }}</span>
            </span>
            <span class="tabular"><b>{{ formatScore(sr.score) }}</b> <span class="muted">/ {{ formatScore(sr.maxScore) }} ball</span></span>
          </div>
          <NProgress
            type="line"
            :percentage="sr.percent"
            :status="scoreStatus(sr.percent)"
            :show-indicator="false"
            :height="8"
          />
          <div class="muted subject-detail tabular">
            {{ sr.correct }} to'g'ri × {{ formatScore(sr.scoreWeight) }} ball · {{ sr.wrong }} xato · {{ sr.unanswered }} javobsiz
          </div>
        </div>
      </NCard>

      <!-- Blok testda mavzular juda ko'p va har birida 1-2 savol bo'ladi, shuning uchun faqat fanlar kesimi -->
      <NCard v-else title="Mavzular bo'yicha" size="small" style="margin-bottom: 16px">
        <div v-for="t in result.byTopic" :key="t.topicId" class="topic-row">
          <div class="topic-head">
            <span>{{ t.topicName }}</span>
            <span class="muted tabular">{{ t.correct }} / {{ t.total }} · {{ t.percent }}%</span>
          </div>
          <NProgress
            type="line"
            :percentage="t.percent"
            :status="scoreStatus(t.percent)"
            :show-indicator="false"
            :height="8"
          />
        </div>
        <NAlert v-if="result.byTopic.length > 1 && result.byTopic[0].percent < 70" type="info" :show-icon="false" style="margin-top: 12px">
          Eng kuchsiz mavzu: <b>{{ result.byTopic[0].topicName }}</b>. Shu mavzuni alohida takrorlab ko'ring.
        </NAlert>
      </NCard>

      <div class="review-head">
        <h2>Savollar tahlili</h2>
        <NRadioGroup v-model:value="filter" size="small">
          <NRadioButton value="all">Barchasi</NRadioButton>
          <NRadioButton value="wrong">Xato</NRadioButton>
          <NRadioButton value="unanswered">Javobsiz</NRadioButton>
          <NRadioButton value="correct">To'g'ri</NRadioButton>
        </NRadioGroup>
      </div>

      <NEmpty v-if="!filtered.length" description="Bu bo'limda savollar yo'q" style="margin: 32px 0" />

      <NCard v-for="{ q, i } in filtered" :key="q.id" size="small" class="review-card">
        <div class="q-meta">
          <span class="q-number tabular">{{ i + 1 }}.</span>
          <NTag v-if="isBlock" size="small" :bordered="false" type="info">{{ q.groupName }}</NTag>
          <NTag size="small" :bordered="false">{{ q.topicName }}</NTag>
          <NTag v-if="qState(q) === 'correct'" size="small" type="success">To'g'ri</NTag>
          <NTag v-else-if="qState(q) === 'wrong'" size="small" type="error">Xato</NTag>
          <NTag v-else size="small" type="warning">Javob berilmagan</NTag>
        </div>
        <div class="q-text pre-wrap">{{ q.text }}</div>
        <OptionList :options="q.options" :selected-id="q.selectedOptionId" :correct-id="q.correctOptionId" disabled />
        <div v-if="q.explanation" class="explanation pre-wrap">
          <b>Izoh:</b> {{ q.explanation }}
        </div>
      </NCard>
    </template>
  </div>
</template>

<style scoped>
.score {
  display: flex;
  align-items: center;
  gap: 28px;
  flex-wrap: wrap;
}
.score-value {
  font-size: 26px;
  font-weight: 650;
}
.score-center {
  text-align: center;
  line-height: 1.1;
}
.score-max {
  font-size: 12px;
}
.score-title {
  font-size: 20px;
  font-weight: 650;
  margin-bottom: 12px;
}
.subject-detail {
  font-size: 13px;
  margin-top: 4px;
}
.score-body {
  flex: 1;
  min-width: 240px;
}
.verdict {
  margin: 14px 0 0;
}
.topic-row + .topic-row {
  margin-top: 12px;
}
.topic-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 4px;
  font-size: 14px;
}
.review-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin: 24px 0 12px;
}
.review-head h2 {
  font-size: 18px;
  margin: 0;
}
.review-card {
  margin-bottom: 12px;
}
.q-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}
.q-number {
  font-weight: 600;
}
.q-text {
  font-weight: 500;
  margin-bottom: 12px;
  line-height: 1.5;
}
.explanation {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  background: rgba(127, 127, 127, 0.08);
  font-size: 14px;
  line-height: 1.5;
}
</style>
