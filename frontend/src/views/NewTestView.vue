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
  NRadioButton,
  NRadioGroup,
  NSkeleton,
  NSpace,
  NTag,
  useMessage,
} from 'naive-ui'
import { subjectApi, testApi } from '@/api'
import { errorMessage } from '@/api/http'
import type { Subject, TestMode, Topic } from '@/types'

const route = useRoute()
const router = useRouter()
const message = useMessage()

const subjectId = Number(route.query.subjectId)
const subject = ref<Subject | null>(null)
const topics = ref<Topic[]>([])
const selected = ref<number[]>([])
const count = ref<number | null>(10)
const mode = ref<TestMode>('LEARNING')
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

const canStart = computed(() => selected.value.length > 0 && !!count.value && count.value <= available.value)

onMounted(async () => {
  if (!subjectId) {
    router.replace('/')
    return
  }
  try {
    const [s, t] = await Promise.all([subjectApi.get(subjectId), subjectApi.topics(subjectId)])
    subject.value = s
    topics.value = t
    toggleAll(true)
  } catch (e) {
    message.error(errorMessage(e))
    router.replace('/')
  } finally {
    loading.value = false
  }
})

async function start() {
  if (!canStart.value || !count.value) return
  starting.value = true
  try {
    const { id } = await testApi.create({
      subjectId,
      topicIds: selected.value,
      questionCount: count.value,
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
      <NCard title="1. Mavzular" size="small" style="margin-bottom: 16px">
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
            </div>
            <NTag size="small" :bordered="false" :type="t.questionCount ? 'default' : 'warning'" class="tabular">
              {{ t.questionCount }} ta savol
            </NTag>
          </div>
        </div>
      </NCard>

      <NCard title="2. Sozlamalar" size="small" style="margin-bottom: 16px">
        <NFormItem label="Savollar soni" :show-feedback="false" style="margin-bottom: 18px">
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
          </NRadioGroup>
        </NFormItem>
        <NAlert :show-icon="false" style="margin-top: 14px">
          <template v-if="mode === 'LEARNING'">
            Har bir javobdan keyin to'g'ri javob va izoh darhol ko'rsatiladi. Javobni o'zgartirib bo'lmaydi.
          </template>
          <template v-else>
            Natija va izohlar faqat test yakunlangach ko'rsatiladi. Yakunlaguncha javoblarni o'zgartirish mumkin.
          </template>
        </NAlert>
      </NCard>

      <NButton type="primary" size="large" block :disabled="!canStart" :loading="starting" @click="start">
        Testni boshlash{{ count && canStart ? ` (${count} ta savol)` : '' }}
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
</style>
