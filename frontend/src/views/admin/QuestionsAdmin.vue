<script setup lang="ts">
import { computed, h, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  NButton,
  NCard,
  NDataTable,
  NInput,
  NSelect,
  NSpace,
  NTag,
  useDialog,
  useMessage,
  type DataTableColumns,
} from 'naive-ui'
import SubjectSelect from '@/components/SubjectSelect.vue'
import QuestionFormModal from '@/components/QuestionFormModal.vue'
import { adminApi } from '@/api'
import { errorMessage } from '@/api/http'
import type { Difficulty, QuestionAdmin, Topic } from '@/types'
import { difficultyLabel, difficultyType } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const dialog = useDialog()

const num = (v: unknown) => (v ? Number(v) : null)
const filter = reactive({
  subjectId: num(route.query.subjectId),
  topicId: num(route.query.topicId),
  difficulty: null as Difficulty | null,
  active: true as boolean | null,
  q: '',
})
const topics = ref<Topic[]>([])
const rows = ref<QuestionAdmin[]>([])
const loading = ref(false)
const pagination = reactive({
  page: 1,
  pageSize: 20,
  itemCount: 0,
  showSizePicker: true,
  pageSizes: [20, 50, 100],
  prefix: ({ itemCount }: { itemCount?: number }) => `Jami: ${itemCount ?? 0}`,
})

const showForm = ref(false)
const editing = ref<QuestionAdmin | null>(null)

const topicOptions = computed(() => topics.value.map((t) => ({ label: t.name, value: t.id })))
const difficultyOptions = (['EASY', 'MEDIUM', 'HARD'] as Difficulty[]).map((d) => ({ label: difficultyLabel[d], value: d }))
const activeOptions = [
  { label: 'Faol', value: 'true' },
  { label: 'Nofaol', value: 'false' },
]
const activeValue = computed({
  get: () => (filter.active === null ? null : String(filter.active)),
  set: (v: string | null) => (filter.active = v === null ? null : v === 'true'),
})

async function load(page = pagination.page) {
  loading.value = true
  try {
    const res = await adminApi.questions({ ...filter, page: page - 1, size: pagination.pageSize })
    rows.value = res.items
    pagination.page = page
    pagination.itemCount = res.total
  } catch (e) {
    message.error(errorMessage(e))
  } finally {
    loading.value = false
  }
}

watch(
  () => filter.subjectId,
  async (id, old) => {
    topics.value = id ? await adminApi.topics(id).catch(() => []) : []
    if (old !== undefined && id !== old) filter.topicId = null
  },
  { immediate: true },
)

let searchTimer: ReturnType<typeof setTimeout> | undefined
watch(
  () => [filter.subjectId, filter.topicId, filter.difficulty, filter.active],
  () => {
    router.replace({ query: { subjectId: filter.subjectId ?? undefined, topicId: filter.topicId ?? undefined } })
    load(1)
  },
)
watch(
  () => filter.q,
  () => {
    clearTimeout(searchTimer)
    searchTimer = setTimeout(() => load(1), 350)
  },
)

function onPageSize(size: number) {
  pagination.pageSize = size
  load(1)
}

function openCreate() {
  editing.value = null
  showForm.value = true
}

function openEdit(q: QuestionAdmin) {
  editing.value = q
  showForm.value = true
}

function remove(q: QuestionAdmin) {
  dialog.warning({
    title: "Savolni o'chirish",
    content: "Savol nofaol qilinadi va yangi testlarda chiqmaydi. Eski natijalar saqlanib qoladi.",
    positiveText: "O'chirish",
    negativeText: 'Bekor qilish',
    onPositiveClick: async () => {
      try {
        await adminApi.deleteQuestion(q.id)
        message.success('Savol nofaol qilindi')
        load()
      } catch (e) {
        message.error(errorMessage(e))
      }
    },
  })
}

const columns: DataTableColumns<QuestionAdmin> = [
  { title: 'ID', key: 'id', width: 70 },
  {
    title: 'Savol',
    key: 'text',
    minWidth: 320,
    render: (q) =>
      h('div', [
        h('div', { class: 'clamp' }, q.text),
        h('div', { class: 'muted', style: 'font-size:12px;margin-top:2px' }, `${q.subjectName} · ${q.topicName} · ${q.options.length} variant`),
      ]),
  },
  {
    title: 'Qiyinlik',
    key: 'difficulty',
    width: 100,
    render: (q) => h(NTag, { size: 'small', bordered: false, type: difficultyType[q.difficulty] }, () => difficultyLabel[q.difficulty]),
  },
  {
    title: 'Holat',
    key: 'active',
    width: 90,
    render: (q) => h(NTag, { size: 'small', type: q.active ? 'success' : 'default' }, () => (q.active ? 'Faol' : 'Nofaol')),
  },
  {
    title: '',
    key: 'actions',
    width: 190,
    render: (q) =>
      h(NSpace, { size: 6, justify: 'end' }, () => [
        h(NButton, { size: 'small', onClick: () => openEdit(q) }, () => 'Tahrirlash'),
        q.active ? h(NButton, { size: 'small', type: 'error', ghost: true, onClick: () => remove(q) }, () => "O'chirish") : null,
      ]),
  },
]

onMounted(() => load(1))
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1>Savollar</h1>
      <NSpace>
        <NButton @click="router.push('/admin/import')">Import</NButton>
        <NButton type="primary" @click="openCreate">+ Savol qo'shish</NButton>
      </NSpace>
    </div>

    <NCard size="small" style="margin-bottom: 12px">
      <div class="filters">
        <SubjectSelect v-model="filter.subjectId" clearable placeholder="Barcha fanlar" />
        <NSelect v-model:value="filter.topicId" :options="topicOptions" clearable filterable placeholder="Barcha mavzular" :disabled="!filter.subjectId" />
        <NSelect v-model:value="filter.difficulty" :options="difficultyOptions" clearable placeholder="Qiyinlik" />
        <NSelect v-model:value="activeValue" :options="activeOptions" clearable placeholder="Holat" />
        <NInput v-model:value="filter.q" clearable placeholder="Savol matni bo'yicha qidirish" class="search" />
      </div>
    </NCard>

    <NCard content-style="padding: 0">
      <NDataTable
        remote
        :columns="columns"
        :data="rows"
        :loading="loading"
        :pagination="pagination"
        :row-key="(r: QuestionAdmin) => r.id"
        :scroll-x="820"
        @update:page="load"
        @update:page-size="onPageSize"
      />
    </NCard>

    <QuestionFormModal
      v-model:show="showForm"
      :question="editing"
      :default-subject-id="filter.subjectId"
      :default-topic-id="filter.topicId"
      @saved="load()"
    />
  </div>
</template>

<style scoped>
.filters {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}
.search {
  grid-column: 1 / -1;
}
@media (max-width: 760px) {
  .filters {
    grid-template-columns: 1fr 1fr;
  }
}
:deep(.clamp) {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
