<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NIcon, useMessage } from 'naive-ui'
import { CheckmarkCircle, CloseCircle, RemoveCircle } from '@vicons/ionicons5'
import { testApi } from '@/api'
import { errorMessage } from '@/api/http'
import type { QuestionView, ResultView, SessionSummary } from '@/types'
import { confetti, longestStreak } from '@/utils/learning'
import { formatDuration, formatScore, modeLabel, shortSubjectName } from '@/utils/format'
import AppBar from '@m/components/AppBar.vue'
import GradeMark from '@m/components/GradeMark.vue'
import { grade, gradeWord } from '@m/utils/grade'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const id = Number(route.params.id)
const r = ref<ResultView | null>(null)
const filter = ref<'wrong' | 'all' | 'correct'>('all')
const retrying = ref(false)

const g = computed(() => grade(r.value?.scorePercent ?? 0))
const isBlock = computed(() => r.value?.mode === 'BLOCK')
const toRetry = computed(() => (r.value ? r.value.wrongCount + r.value.unansweredCount : 0))
const state = (q: QuestionView) => (!q.answered ? 'skip' : q.correct ? 'ok' : 'bad')
const list = computed(() => {
  const all = (r.value?.questions ?? []).map((q, i) => ({ q, i }))
  if (filter.value === 'wrong') return all.filter(({ q }) => state(q) !== 'ok')
  if (filter.value === 'correct') return all.filter(({ q }) => state(q) === 'ok')
  return all
})
const optionText = (q: QuestionView, oid: number | null) => q.options.find((o) => o.id === oid)?.text ?? ''
const bestStreak = computed(() => longestStreak(r.value?.questions ?? []))
const isBlitz = computed(() => r.value?.mode === 'BLITZ')
const isFlashcard = computed(() => r.value?.mode === 'FLASHCARD')
const xpEarned = computed(() => r.value?.xp ?? 0)
// Blits rekordi: shu fandagi oldingi blitslarning eng yaxshisi
const blitzBest = ref<number | null>(null)
const newRecord = computed(() => isBlitz.value && !!r.value && r.value.correctCount > 0 && r.value.correctCount > (blitzBest.value ?? 0))

// 70% dan past mavzular – o'rganish rejimida alohida mashq qilish taklif qilinadi
const weakTopics = computed(() => (r.value?.byTopic ?? []).filter((t) => t.percent < 70))
const practice = () =>
  r.value?.subjectId &&
  router.replace({ path: `/subject/${r.value.subjectId}`, query: { topics: weakTopics.value.map((t) => t.topicId).join(',') } })

// Oldingi urinish: shu fan, shu rejim va aynan shu mavzular (blok testda – shu fanlar)
const previous = ref<SessionSummary | null>(null)
const delta = computed(() =>
  previous.value?.scorePercent == null || !r.value ? null : r.value.scorePercent - previous.value.scorePercent,
)
const sameSet = (a: string[], b: string[]) => a.length === b.length && [...a].sort().join('\n') === [...b].sort().join('\n')

onMounted(async () => {
  try {
    r.value = await testApi.result(id)
    if (r.value.wrongCount + r.value.unansweredCount > 0) filter.value = 'wrong'
  } catch (e) {
    message.error(errorMessage(e))
    router.replace('/')
    return
  }
  if (r.value.scorePercent >= 90 && r.value.questionCount >= 3) confetti()
  try {
    const { items } = await testApi.history(0, 100)
    const cur = items.find((s) => s.id === id)
    const res = r.value
    if (res.mode === 'BLITZ') {
      const prev = items.filter((s) => s.id < id && s.mode === 'BLITZ' && s.status === 'FINISHED' && s.subjectId === res.subjectId)
      blitzBest.value = prev.length ? Math.max(...prev.map((s) => s.correctCount ?? 0)) : null
      if (newRecord.value && res.scorePercent < 90) confetti()
    }
    previous.value =
      (cur &&
        items.find(
          (s) =>
            s.id < id &&
            s.status === 'FINISHED' &&
            s.mode === res.mode &&
            s.subjectId === res.subjectId &&
            sameSet(s.topicNames, cur.topicNames) &&
            sameSet(s.groupLabels, cur.groupLabels),
        )) ??
      null
  } catch {
    /* taqqoslash ixtiyoriy */
  }
})

async function retry() {
  retrying.value = true
  try {
    const { id: next } = await testApi.retryWrong(id)
    router.replace(`/test/${next}`)
  } catch (e) {
    message.error(errorMessage(e))
  } finally {
    retrying.value = false
  }
}
</script>

<template>
  <AppBar title="Natija" to="/" />
  <main v-if="r" class="screen">
    <section class="sheet grid-paper card">
      <GradeMark :grade="g" :size="112" />
      <div class="sheet-text">
        <div class="word">{{ gradeWord[g] }}</div>
        <div v-if="isBlock" class="score tabular"><b>{{ formatScore(r.score) }}</b> / {{ formatScore(r.maxScore) }} ball</div>
        <div v-else class="score tabular"><b>{{ r.correctCount }}</b> / {{ r.questionCount }} to'g'ri</div>
        <div v-if="!isBlock" class="muted small">{{ r.subjectName }}</div>
        <div class="muted small">{{ modeLabel[r.mode] }} · {{ formatDuration(r.durationSeconds) }}<template v-if="r.timeLimitSeconds"> / {{ formatDuration(r.timeLimitSeconds) }}</template> · {{ r.scorePercent }}%</div>
        <div v-if="xpEarned" class="xp small tabular">+{{ xpEarned }} XP</div>
        <div v-if="newRecord" class="record">🏆 Yangi rekord!<span v-if="blitzBest !== null" class="muted small"> (avvalgi: {{ blitzBest }})</span></div>
        <div v-else-if="isBlitz && blitzBest !== null" class="muted small">Rekordingiz: {{ blitzBest }}</div>
        <div v-if="previous && delta !== null" class="compare small tabular">
          <span class="delta" :class="delta > 0 ? 'up' : delta < 0 ? 'down' : ''">{{ delta > 0 ? '▲ +' : delta < 0 ? '▼ ' : '' }}{{ delta }}%</span>
          <span class="muted">oldingi urinishga nisbatan ({{ previous.scorePercent }}%)</span>
        </div>
      </div>
    </section>

    <div class="tiles">
      <div class="tile ok"><b class="tabular">{{ r.correctCount }}</b><span>{{ isFlashcard ? 'bilardim' : "to'g'ri" }}</span></div>
      <div class="tile bad"><b class="tabular">{{ r.wrongCount }}</b><span>{{ isFlashcard ? 'bilmasdim' : 'xato' }}</span></div>
      <div v-if="!isBlitz" class="tile skip"><b class="tabular">{{ r.unansweredCount }}</b><span>javobsiz</span></div>
      <div class="tile streak"><b class="tabular">{{ bestStreak >= 3 ? '🔥' : '' }}{{ bestStreak }}</b><span>seriya</span></div>
    </div>

    <div class="actions">
      <button v-if="toRetry" class="btn btn-primary btn-block" :disabled="retrying" @click="retry">Xatolarni qayta yechish · {{ toRetry }}</button>
      <button class="btn btn-secondary btn-block" @click="router.replace(isBlock ? '/block' : `/subject/${r.subjectId}`)">Yangi test</button>
    </div>

    <template v-if="isBlock">
      <div class="section-label">Fanlar bo'yicha</div>
      <div class="card topics">
        <div v-for="sr in r.byGroup" :key="sr.groupId" class="topic-row">
          <div class="topic-head">
            <span class="topic-name">
              <b>{{ sr.groupName }}</b>
              <span class="muted"> · {{ sr.subjectNames.map((n) => shortSubjectName(n, sr.groupName)).join(', ') }}</span>
            </span>
            <span class="tabular"><b>{{ formatScore(sr.score) }}</b> <span class="muted">/ {{ formatScore(sr.maxScore) }}</span></span>
          </div>
          <div class="bar"><div class="bar-fill" :class="sr.percent >= 70 ? 'ok' : sr.percent >= 50 ? 'mid' : 'bad'" :style="{ width: `${Math.max(sr.percent, 3)}%` }" /></div>
          <div class="muted small tabular subject-detail">{{ sr.correct }}/{{ sr.total }} to'g'ri × {{ formatScore(sr.scoreWeight) }} ball</div>
        </div>
      </div>
    </template>

    <template v-if="!isBlock && r.byTopic.length > 1">
      <div class="section-label">Mavzular bo'yicha</div>
      <div v-if="weakTopics.length" class="card practice">
        <div class="practice-text">
          <b>📚 Mashq kerak:</b>
          {{ weakTopics.length === 1 ? weakTopics[0].topicName : `${weakTopics.length} ta mavzuda natija 70% dan past` }}
        </div>
        <button class="btn btn-primary practice-btn" @click="practice">Mashq qilish</button>
      </div>
      <div class="card topics">
        <div v-for="t in r.byTopic" :key="t.topicId" class="topic-row">
          <div class="topic-head">
            <span class="topic-name">{{ t.topicName }}</span>
            <span class="tabular muted">{{ t.correct }}/{{ t.total }}</span>
          </div>
          <div class="bar"><div class="bar-fill" :class="t.percent >= 70 ? 'ok' : t.percent >= 50 ? 'mid' : 'bad'" :style="{ width: `${Math.max(t.percent, 3)}%` }" /></div>
        </div>
      </div>
    </template>

    <div class="section-label">Savollar tahlili</div>
    <div class="filters">
      <button class="chip" :class="{ on: filter === 'wrong' }" @click="filter = 'wrong'">Xatolar · {{ toRetry }}</button>
      <button class="chip" :class="{ on: filter === 'all' }" @click="filter = 'all'">Hammasi · {{ r.questionCount }}</button>
      <button class="chip" :class="{ on: filter === 'correct' }" @click="filter = 'correct'">To'g'ri · {{ r.correctCount }}</button>
    </div>
    <p v-if="!list.length" class="muted empty">{{ filter === 'wrong' ? "Xato yo'q — barakalla!" : "Bu bo'limda savol yo'q" }}</p>
    <article v-for="{ q, i } in list" :key="q.id" class="card review" :class="state(q)">
      <div class="review-head">
        <span class="num tabular">{{ i + 1 }}</span>
        <NIcon :component="state(q) === 'ok' ? CheckmarkCircle : state(q) === 'bad' ? CloseCircle : RemoveCircle" size="20" class="state-icon" />
        <span class="muted small topic-small">{{ isBlock ? `${q.groupName} · ` : '' }}{{ q.topicName.replace(/^\d+\.\s*/, '') }}</span>
      </div>
      <div class="review-q">{{ q.text }}</div>
      <div v-if="state(q) !== 'ok' && !isFlashcard" class="answer bad-answer">
        <span class="label">Sizning javobingiz</span>{{ q.answered ? optionText(q, q.selectedOptionId) : 'Javob berilmagan' }}
      </div>
      <div class="answer ok-answer"><span class="label">To'g'ri javob</span>{{ optionText(q, q.correctOptionId) }}</div>
      <div v-if="q.explanation" class="explain">{{ q.explanation }}</div>
    </article>
  </main>
</template>

<style scoped>
.subject-detail {
  margin-top: 4px;
}
.sheet {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 20px 18px;
  margin-top: 4px;
  overflow: hidden;
}
.sheet-text {
  min-width: 0;
}
.word {
  font-family: var(--display);
  font-weight: 650;
  font-size: 20px;
  color: var(--ink);
  margin-bottom: 2px;
}
.score {
  font-size: 17px;
  margin-bottom: 4px;
}
.score b {
  font-family: var(--display);
  font-size: 22px;
  color: var(--ink);
}
.small {
  font-size: 13px;
}
.xp {
  display: inline-block;
  margin-top: 6px;
  padding: 2px 10px;
  border-radius: 12px;
  background: var(--marker-soft);
  color: var(--ink);
  font-weight: 650;
}
.record {
  margin-top: 6px;
  font-weight: 650;
  color: #d97706;
}
.compare {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: 4px;
}
.delta {
  font-weight: 650;
}
.delta.up {
  color: var(--ok);
}
.delta.down {
  color: var(--bad);
}
.practice {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  margin-bottom: 10px;
  background: var(--marker-soft);
  box-shadow: inset 0 0 0 1.5px var(--marker);
}
.practice-text {
  flex: 1;
  min-width: 0;
  font-size: 14px;
  line-height: 1.4;
}
.practice-btn {
  flex: none;
  min-height: 42px;
  width: auto;
  padding: 0 16px;
}
.tile.streak {
  border-color: #ea580c;
}
.tiles {
  display: flex;
  gap: 10px;
  margin-top: 12px;
}
.tile {
  flex: 1;
  min-width: 0;
  background: var(--card);
  border-radius: 14px;
  box-shadow: var(--shadow);
  padding: 12px;
  display: flex;
  flex-direction: column;
  align-items: center;
  border-top: 4px solid;
}
.tile b {
  font-family: var(--display);
  font-size: 22px;
}
.tile span {
  font-size: 13px;
  color: var(--muted);
}
.tile.ok {
  border-color: var(--ok);
}
.tile.bad {
  border-color: var(--bad);
}
.tile.skip {
  border-color: var(--line);
}
.actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 16px;
}
.topics {
  padding: 6px 16px;
}
.topic-row {
  padding: 10px 0;
  border-bottom: 1px solid var(--line);
}
.topic-row:last-child {
  border-bottom: 0;
}
.topic-head {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  font-size: 14px;
  margin-bottom: 6px;
}
.topic-name {
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
.filters {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  margin-bottom: 12px;
  padding-bottom: 2px;
}
.chip {
  flex: none;
  height: 38px;
  padding: 0 14px;
  border-radius: 19px;
  border: 1.5px solid var(--line);
  background: var(--card);
  font-weight: 600;
  font-size: 14px;
}
.chip.on {
  background: var(--ink);
  border-color: var(--ink);
  color: #fff;
}
:root[data-theme='dark'] .chip.on {
  color: #0f1430;
}
.empty {
  text-align: center;
  padding: 24px 0;
}
.review {
  padding: 14px 16px;
  margin-bottom: 10px;
  border-left: 4px solid var(--line);
}
.review.ok {
  border-left-color: var(--ok);
}
.review.bad {
  border-left-color: var(--bad);
}
.review-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.num {
  font-family: var(--display);
  font-weight: 600;
  color: var(--ink);
}
.review.ok .state-icon {
  color: var(--ok);
}
.review.bad .state-icon {
  color: var(--bad);
}
.review.skip .state-icon {
  color: var(--muted);
}
.topic-small {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.review-q {
  font-weight: 600;
  line-height: 1.4;
  margin-bottom: 10px;
}
.answer {
  font-size: 15px;
  padding: 8px 10px;
  border-radius: 10px;
  margin-bottom: 6px;
}
.answer .label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  opacity: 0.8;
}
.bad-answer {
  background: var(--bad-soft);
}
.bad-answer .label {
  color: var(--bad);
}
.ok-answer {
  background: var(--ok-soft);
}
.ok-answer .label {
  color: var(--ok);
}
.explain {
  font-size: 14px;
  line-height: 1.5;
  color: var(--muted);
  margin-top: 8px;
}
</style>
