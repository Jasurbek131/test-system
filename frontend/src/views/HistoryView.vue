<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NCard, NDataTable, NTag, useDialog, useMessage, type DataTableColumns } from 'naive-ui'
import { testApi } from '@/api'
import { errorMessage } from '@/api/http'
import type { SessionSummary } from '@/types'
import { formatDate, modeLabel, scoreStatus, topicSummary } from '@/utils/format'

const router = useRouter()
const message = useMessage()
const dialog = useDialog()
const rows = ref<SessionSummary[]>([])
const loading = ref(false)
const pagination = reactive({
  page: 1,
  pageSize: 20,
  itemCount: 0,
  prefix: ({ itemCount }: { itemCount?: number }) => `Jami: ${itemCount ?? 0}`,
})

async function load(page = pagination.page) {
  loading.value = true
  try {
    const res = await testApi.history(page - 1, pagination.pageSize)
    rows.value = res.items
    pagination.page = page
    pagination.itemCount = res.total
  } catch (e) {
    message.error(errorMessage(e))
  } finally {
    loading.value = false
  }
}

function clearHistory() {
  dialog.warning({
    title: 'Tarixni tozalash',
    content:
      "Barcha yechgan testlaringiz, natijalar va statistika o'chiriladi. Fanlar va savollar o'zgarmaydi. Bu amalni ortga qaytarib bo'lmaydi.",
    positiveText: 'Tozalash',
    negativeText: 'Bekor qilish',
    onPositiveClick: async () => {
      try {
        await testApi.clearHistory()
        message.success('Tarix tozalandi')
        await load(1)
      } catch (e) {
        message.error(errorMessage(e))
      }
    },
  })
}

const columns: DataTableColumns<SessionSummary> = [
  { title: '#', key: 'id', width: 70 },
  {
    title: 'Fan',
    key: 'subjectName',
    minWidth: 180,
    render: (r) =>
      h('div', [h('div', { style: 'font-weight:500' }, r.subjectName), h('div', { class: 'muted', style: 'font-size:12px' }, topicSummary(r.topicNames))]),
  },
  {
    title: 'Rejim',
    key: 'mode',
    width: 110,
    render: (r) => h(NTag, { size: 'small', bordered: false }, { default: () => modeLabel[r.mode] }),
  },
  {
    title: 'Natija',
    key: 'scorePercent',
    width: 150,
    render: (r) =>
      r.status === 'FINISHED'
        ? h(NTag, { type: scoreStatus(r.scorePercent ?? 0), size: 'small' }, () =>
            `${r.correctCount}/${r.questionCount} · ${r.scorePercent}%`,
          )
        : h(NTag, { type: 'info', size: 'small' }, () => `Jarayonda: ${r.answeredCount}/${r.questionCount}`),
  },
  { title: 'Boshlangan', key: 'startedAt', width: 150, render: (r) => formatDate(r.startedAt) },
  {
    title: '',
    key: 'actions',
    width: 130,
    render: (r) =>
      h(
        NButton,
        {
          size: 'small',
          type: r.status === 'FINISHED' ? 'default' : 'primary',
          onClick: () => router.push(r.status === 'FINISHED' ? `/test/${r.id}/result` : `/test/${r.id}`),
        },
        () => (r.status === 'FINISHED' ? "Ko'rish" : 'Davom etish'),
      ),
  },
]

onMounted(() => load(1))
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1>Testlar tarixi</h1>
      <NButton v-if="pagination.itemCount > 0" type="error" secondary @click="clearHistory">Tarixni tozalash</NButton>
    </div>
    <NCard content-style="padding: 0">
      <NDataTable
        remote
        :columns="columns"
        :data="rows"
        :loading="loading"
        :pagination="pagination"
        :row-key="(r: SessionSummary) => r.id"
        :scroll-x="780"
        @update:page="load"
      >
        <template #empty>
          <div style="padding: 24px; text-align: center">
            <div class="muted" style="margin-bottom: 12px">Hali test yechmagansiz</div>
            <NButton type="primary" @click="router.push('/')">Birinchi testni boshlash</NButton>
          </div>
        </template>
      </NDataTable>
    </NCard>
  </div>
</template>
