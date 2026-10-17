<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  NAlert,
  NButton,
  NCard,
  NCheckbox,
  NEmpty,
  NFormItem,
  NInputNumber,
  NProgress,
  NRadioButton,
  NRadioGroup,
  NSkeleton,
  NSpace,
  NTag,
  useMessage,
} from 'naive-ui'
import { progressApi, subjectApi, testApi } from '@/api'
import { errorMessage } from '@/api/http'
import type { ReviewKind, Subject, SubjectProgress, TestMode, Topic, TopicProgress } from '@/types'
import { saveLastTest } from '@/utils/learning'
import { masteryLabel } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const message = useMessage()

const subjectId = Number(route.query.subjectId)
const subject = ref<Subject | null>(null)
const topics = ref<Topic[]>([])
const selected = ref<number[]>([])
// Natija sahifasidan «Mashq qilish» yoki «Oxirgi sozlamalar» orqali kelinganda: ?topicIds=1,2&mode=LEARNING&count=20
const queryTopicIds = String(route.query.topicIds ?? '')
  .split(',')
  .map(Number)
  .filter((n) => n > 0)
const queryCount = Number(route.query.count) || null
const count = ref<number | null>(queryCount ?? 10)
const MODES: TestMode[] = ['LEARNING', 'EXAM', 'FLASHCARD', 'BLITZ']
const mode = ref<TestMode>(MODES.find((m) => m === route.query.mode) ?? 'LEARNING')
// Blitsda savollar soni so'ralmaydi: 60 soniyaga yetadigan darajada ko'p savol olinadi
const BLITZ_COUNT = 100
const isBlitz = computed(() => mode.value === 'BLITZ')

// Mavzular bo'yicha o'zlashtirish va takrorlash
const progress = ref<SubjectProgress | null>(null)
const topicProgress = computed(() => new Map((progress.value?.topics ?? []).map((t) => [t.topicId, t])))
const masteredCount = computed(() => (progress.value?.topics ?? []).filter((t) => t.level === 'MASTERED' && t.total > 0).length)
const withQuestions = computed(() => topics.value.filter((t) => t.questionCount > 0).length)
const levelType = { NEW: 'default', LEARNING: 'warning', MASTERED: 'success' } as const
const levelText = (p: TopicProgress) =>
  p.level === 'LEARNING' ? `${masteryLabel.LEARNING} · ${p.recentCorrect}/${p.recentAnswered}` : masteryLabel[p.level]
const reviewing = ref<string | null>(null)

async function startReview(kind: ReviewKind, reviewMode: TestMode) {
  reviewing.value = kind + reviewMode
  try {
    const { id } = await testApi.createReview({ subjectId, kind, questionCount: 20, mode: reviewMode })
    router.push(`/test/${id}`)
  } catch (e) {
    message.error(errorMessage(e))
  } finally {
    reviewing.value = null
  }
}
const loading = ref(true)
const starting = ref(false)

// Backend bitta testda 500 tadan ortiq savolga ruxsat bermaydi
const MAX_QUESTIONS = 500
const totalSelected = computed(() =>
  topics.value.filter((t) => selected.value.includes(t.id)).reduce((sum, t) => sum + t.questionCount, 0),
)
const available = computed(() => Math.min(totalSelected.value, MAX_QUESTIONS))
const selectableTopics = computed(() => topics.value.filter((t) => t.questionCount > 0))
const allSelected = computed(
  () => selectableTopics.value.length > 0 && selected.value.length === selectableTopics.value.length,
)
const someSelected = computed(() => selected.value.length > 0 && !allSelected.value)

function toggleAll(v: boolean) {
  selected.value = v ? selectableTopics.value.map((t) => t.id) : []
}

function toggle(t: Topic) {
  if (!t.questionCount) return
  selected.value = selected.value.includes(t.id)
    ? selected.value.filter((id) => id !== t.id)
    : [...selected.value, t.id]
}

// Savollar soni mavjud savollardan oshmasligi kerak
watch(available, (max) => {
  if (count.value && max && count.value > max) count.value = max
  if (!count.value && max) count.value = Math.min(10, max)
})

const canStart = computed(
  () => selected.value.length > 0 && (isBlitz.value ? available.value > 0 : !!count.value && count.value <= available.value),
)

onMounted(async () => {
  if (!subjectId) {
    router.replace('/')
    return
  }
  try {
    progressApi.subject(subjectId).then((p) => (progress.value = p), () => {})
    const [s, t] = await Promise.all([subjectApi.get(subjectId), subjectApi.topics(subjectId)])
    subject.value = s
    topics.value = t
    const preset = selectableTopics.value.filter((x) => queryTopicIds.includes(x.id)).map((x) => x.id)
    if (preset.length) selected.value = preset
    else toggleAll(true)
  } catch (e) {
    message.error(errorMessage(e))
    router.replace('/')
  } finally {
    loading.value = false
  }
})

async function start() {
  if (!canStart.value) return
  const n = isBlitz.value ? Math.min(BLITZ_COUNT, available.value) : count.value!
  starting.value = true
  try {
    const { id } = await testApi.create({
      subjectId,
      topicIds: selected.value,
      questionCount: n,
      mode: mode.value,
    })
    saveLastTest({
      subjectId,
      subjectName: subject.value?.name ?? '',
      topicIds: selected.value,
      questionCount: n,
      mode: mode.value,
    })
    router.push(`/test/${id}`)
  } catch (e) {
    message.error(errorMessage(e))
  } finally {
    starting.value = false
  }
}
</script>

<template>
  <div class="page page-narrow">
    <div class="page-header">
      <div>
        <h1>{{ subject?.name ?? 'Yangi test' }}</h1>
        <div class="muted">Mavzularni tanlang va testni sozlang</div>
      </div>
      <NButton quaternary @click="router.push('/')">← Fanlar</NButton>
    </div>

    <NCard v-if="loading"><NSkeleton text :repeat="6" /></NCard>

    <template v-else>
      <NCard v-if="progress && (progress.due || progress.mistakes)" size="small" class="review-card">
        <div class="review-body">
          <div>
            <div class="review-title">🔁 Takrorlash</div>
            <div class="muted review-sub">
              <template v-if="progress.due">{{ progress.due }} ta savolni takrorlash vaqti keldi. </template>
              <template v-if="progress.mistakes">Xatolar daftarida {{ progress.mistakes }} ta savol.</template>
            </div>
          </div>
          <NSpace>
            <NButton
              v-if="progress.due"
              type="primary"
              :loading="reviewing === 'DUELEARNING'"
              @click="startReview('DUE', 'LEARNING')"
            >
              Takrorlash
            </NButton>
            <NButton v-if="progress.due" secondary :loading="reviewing === 'DUEFLASHCARD'" @click="startReview('DUE', 'FLASHCARD')">
              Kartochkalar bilan
            </NButton>
            <NButton
              v-if="progress.mistakes"
              secondary
              type="error"
              :loading="reviewing === 'MISTAKESLEARNING'"
              @click="startReview('MISTAKES', 'LEARNING')"
            >
              Xatolarni yechish
            </NButton>
          </NSpace>
        </div>
      </NCard>

      <NCard title="1. Mavzular" size="small" style="margin-bottom: 16px">
        <div v-if="progress && withQuestions" class="mastery-summary">
          <span class="tabular">O'zlashtirilgan: <b>{{ masteredCount }}</b> / {{ withQuestions }} mavzu</span>
          <NProgress
            type="line"
            :percentage="Math.round((masteredCount / withQuestions) * 100)"
            status="success"
            :show-indicator="false"
            :height="6"
            style="flex: 1; max-width: 260px"
          />
        </div>
        <template #header-extra>
          <NCheckbox :checked="allSelected" :indeterminate="someSelected" @update:checked="toggleAll">
            Hammasini tanlash
          </NCheckbox>
        </template>
        <NEmpty v-if="!topics.length" description="Bu fanda mavzular yo'q" />
        <div v-else class="topic-list">
          <div
            v-for="t in topics"
            :key="t.id"
            class="topic-row"
            :class="{ disabled: !t.questionCount, active: selected.includes(t.id) }"
            @click="toggle(t)"
          >
            <NCheckbox
              :checked="selected.includes(t.id)"
              :disabled="!t.questionCount"
              @click.stop
              @update:checked="toggle(t)"
            />
            <div class="topic-text">
              <div class="topic-name">{{ t.name }}</div>
              <div v-if="t.description" class="muted topic-desc">{{ t.description }}</div>
              <div v-if="topicProgress.get(t.id)?.seen" class="muted topic-desc tabular">
                {{ topicProgress.get(t.id)!.seen }} / {{ t.questionCount }} savol ko'rilgan
              </div>
            </div>
            <NTag
              v-if="topicProgress.get(t.id) && t.questionCount"
              size="small"
              :type="levelType[topicProgress.get(t.id)!.level]"
              :bordered="topicProgress.get(t.id)!.level === 'NEW'"
              class="level-tag"
            >
              {{ topicProgress.get(t.id)!.level === 'MASTERED' ? '✓ ' : '' }}{{ levelText(topicProgress.get(t.id)!) }}
            </NTag>
            <NTag size="small" :bordered="false" :type="t.questionCount ? 'default' : 'warning'" class="tabular">
              {{ t.questionCount }} ta savol
            </NTag>
          </div>
        </div>
      </NCard>

      <NCard title="2. Sozlamalar" size="small" style="margin-bottom: 16px">
        <NFormItem v-if="!isBlitz" label="Savollar soni" :show-feedback="false" style="margin-bottom: 18px">
          <NSpace align="center">
            <NInputNumber
              v-model:value="count"
              :min="1"
              :max="Math.max(available, 1)"
              :disabled="!available"
              style="width: 140px"
            />
            <span class="muted">
              maksimum: {{ available }}<template v-if="totalSelected > MAX_QUESTIONS"> (jami {{ totalSelected }} ta savoldan)</template>
            </span>
          </NSpace>
        </NFormItem>
        <NFormItem label="Rejim" :show-feedback="false">
          <NRadioGroup v-model:value="mode">
            <NRadioButton value="LEARNING">O'rganish</NRadioButton>
            <NRadioButton value="EXAM">Imtihon</NRadioButton>
            <NRadioButton value="FLASHCARD">Kartochka</NRadioButton>
            <NRadioButton value="BLITZ">⚡ Blits</NRadioButton>
          </NRadioGroup>
        </NFormItem>
        <NAlert :show-icon="false" style="margin-top: 14px">
          <template v-if="mode === 'LEARNING'">
            Har bir javobdan keyin to'g'ri javob va izoh darhol ko'rsatiladi. Javobni o'zgartirib bo'lmaydi.
          </template>
          <template v-else-if="mode === 'EXAM'">
            Natija va izohlar faqat test yakunlangach ko'rsatiladi. Yakunlaguncha javoblarni o'zgartirish mumkin.
          </template>
          <template v-else-if="mode === 'FLASHCARD'">
            Savolni o'qing, javobini eslang, so'ng kartochkani aylantiring va «Bilardim» yoki «Bilmasdim» ni bosing.
            Bilmagan savollaringiz takrorlashga qo'shiladi.
          </template>
          <template v-else>
            60 soniya ichida iloji boricha ko'p savolga javob bering. Javob darhol ko'rsatiladi va keyingi savolga o'tiladi.
          </template>
        </NAlert>
      </NCard>

      <NButton type="primary" size="large" block :disabled="!canStart" :loading="starting" @click="start">
        <template v-if="isBlitz">⚡ Blitsni boshlash (60 soniya)</template>
        <template v-else-if="mode === 'FLASHCARD'">Kartochkalarni boshlash{{ count && canStart ? ` (${count} ta)` : '' }}</template>
        <template v-else>Testni boshlash{{ count && canStart ? ` (${count} ta savol)` : '' }}</template>
      </NButton>
    </template>
  </div>
</template>

<style scoped>
.topic-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.topic-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  border: 1px solid transparent;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
}
.topic-row:hover {
  background: rgba(127, 127, 127, 0.08);
}
.topic-row.active {
  border-color: rgba(127, 127, 127, 0.2);
}
.topic-row.disabled {
  cursor: not-allowed;
  opacity: 0.55;
}
.topic-text {
  flex: 1;
  min-width: 0;
}
.topic-name {
  font-weight: 500;
}
.topic-desc {
  font-size: 13px;
}
.level-tag {
  flex: none;
}
.mastery-summary {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  font-size: 14px;
  margin-bottom: 12px;
}
.review-card {
  margin-bottom: 16px;
  border-color: rgba(13, 148, 136, 0.5);
  background: rgba(13, 148, 136, 0.05);
}
.review-body {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.review-title {
  font-weight: 600;
  font-size: 16px;
}
.review-sub {
  font-size: 13px;
}
</style>
