<script setup lang="ts">
import { h, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  NButton,
  NCard,
  NDataTable,
  NEmpty,
  NForm,
  NFormItem,
  NInput,
  NInputNumber,
  NModal,
  NSpace,
  NSwitch,
  NTag,
  useDialog,
  useMessage,
  type DataTableColumns,
  type FormInst,
} from 'naive-ui'
import SubjectSelect from '@/components/SubjectSelect.vue'
import { adminApi } from '@/api'
import { errorMessage } from '@/api/http'
import type { Topic } from '@/types'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const dialog = useDialog()

const subjectId = ref<number | null>(route.query.subjectId ? Number(route.query.subjectId) : null)
const rows = ref<Topic[]>([])
const loading = ref(false)
const show = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInst | null>(null)
const form = reactive({ name: '', description: '', orderIndex: null as number | null, active: true })

async function load() {
  if (!subjectId.value) {
    rows.value = []
    return
  }
  loading.value = true
  try {
    rows.value = await adminApi.topics(subjectId.value)
  } catch (e) {
    message.error(errorMessage(e))
  } finally {
    loading.value = false
  }
}

watch(subjectId, (v) => {
  router.replace({ query: v ? { subjectId: v } : {} })
  load()
})

function openCreate() {
  editingId.value = null
  Object.assign(form, { name: '', description: '', orderIndex: null, active: true })
  show.value = true
}

function openEdit(t: Topic) {
  editingId.value = t.id
  Object.assign(form, { name: t.name, description: t.description ?? '', orderIndex: t.orderIndex, active: t.active })
  show.value = true
}

async function save() {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    const body = {
      subjectId: subjectId.value!,
      name: form.name,
      description: form.description || null,
      orderIndex: form.orderIndex,
      active: form.active,
    }
    if (editingId.value) await adminApi.updateTopic(editingId.value, body)
    else await adminApi.createTopic(body)
    message.success('Saqlandi')
    show.value = false
    load()
  } catch (e) {
    message.error(errorMessage(e))
  } finally {
    saving.value = false
  }
}

function remove(t: Topic) {
  dialog.warning({
    title: "Mavzuni o'chirish",
    content: `"${t.name}" nofaol qilinadi va yangi testlarda chiqmaydi.`,
    positiveText: "O'chirish",
    negativeText: 'Bekor qilish',
    onPositiveClick: async () => {
      try {
        await adminApi.deleteTopic(t.id)
        message.success('Mavzu nofaol qilindi')
        load()
      } catch (e) {
        message.error(errorMessage(e))
      }
    },
  })
}

const columns: DataTableColumns<Topic> = [
  { title: 'Tartib', key: 'orderIndex', width: 80 },
  {
    title: 'Nomi',
    key: 'name',
    minWidth: 200,
    render: (t) =>
      h('div', [
        h('div', { style: 'font-weight:500' }, t.name),
        t.description ? h('div', { class: 'muted', style: 'font-size:12px' }, t.description) : null,
      ]),
  },
  { title: 'Savollar', key: 'questionCount', width: 100 },
  {
    title: 'Holat',
    key: 'active',
    width: 100,
    render: (t) =>
      h(NTag, { size: 'small', type: t.active ? 'success' : 'default' }, () => (t.active ? 'Faol' : 'Nofaol')),
  },
  {
    title: '',
    key: 'actions',
    width: 260,
    render: (t) =>
      h(NSpace, { size: 6, justify: 'end' }, () => [
        h(
          NButton,
          { size: 'small', onClick: () => router.push({ path: '/admin/questions', query: { subjectId: subjectId.value, topicId: t.id } }) },
          () => 'Savollar',
        ),
        h(NButton, { size: 'small', onClick: () => openEdit(t) }, () => 'Tahrirlash'),
        t.active ? h(NButton, { size: 'small', type: 'error', ghost: true, onClick: () => remove(t) }, () => "O'chirish") : null,
      ]),
  },
]

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1>Mavzular</h1>
      <NSpace>
        <SubjectSelect v-model="subjectId" style="width: 260px" />
        <NButton type="primary" :disabled="!subjectId" @click="openCreate">+ Mavzu qo'shish</NButton>
      </NSpace>
    </div>

    <NEmpty v-if="!subjectId" description="Mavzularni ko'rish uchun fanni tanlang" style="margin-top: 60px" />
    <NCard v-else content-style="padding: 0">
      <NDataTable :columns="columns" :data="rows" :loading="loading" :row-key="(r: Topic) => r.id" :scroll-x="760" />
    </NCard>

    <NModal v-model:show="show" preset="card" :title="editingId ? 'Mavzuni tahrirlash' : 'Yangi mavzu'" style="max-width: 520px; width: calc(100% - 32px)">
      <NForm ref="formRef" :model="form" @submit.prevent="save">
        <NFormItem label="Nomi" path="name" :rule="{ required: true, message: 'Nomini kiriting', trigger: 'blur' }">
          <NInput v-model:value="form.name" maxlength="200" />
        </NFormItem>
        <NFormItem label="Tavsif" path="description">
          <NInput v-model:value="form.description" type="textarea" :autosize="{ minRows: 2, maxRows: 5 }" />
        </NFormItem>
        <NFormItem label="Tartib raqami" path="orderIndex">
          <NInputNumber v-model:value="form.orderIndex" :min="0" placeholder="avtomatik" clearable />
        </NFormItem>
        <NFormItem label="Faol" path="active">
          <NSwitch v-model:value="form.active" />
        </NFormItem>
      </NForm>
      <template #action>
        <NSpace justify="end">
          <NButton @click="show = false">Bekor qilish</NButton>
          <NButton type="primary" :loading="saving" @click="save">Saqlash</NButton>
        </NSpace>
      </template>
    </NModal>
  </div>
</template>
