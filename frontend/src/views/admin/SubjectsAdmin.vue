<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  NButton,
  NCard,
  NDataTable,
  NForm,
  NFormItem,
  NAutoComplete,
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
import { adminApi } from '@/api'
import { errorMessage } from '@/api/http'
import type { Subject } from '@/types'

const message = useMessage()
const dialog = useDialog()
const router = useRouter()

const rows = ref<Subject[]>([])
const loading = ref(false)
const show = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInst | null>(null)
const form = reactive({ name: '', description: '', active: true, groupName: '', scoreWeight: 1 as number | null })
const groupOptions = computed(() => {
  const names = [...new Set(rows.value.map((s) => s.groupName))]
  const q = form.groupName.trim().toLowerCase()
  return names.filter((n) => !q || n.toLowerCase().includes(q))
})
// Mavjud guruh tanlansa, ball shu guruhniki
function onGroupSelect(name: string) {
  const g = rows.value.find((s) => s.groupName === name)
  if (g) form.scoreWeight = g.scoreWeight
}

async function load() {
  loading.value = true
  try {
    rows.value = await adminApi.subjects()
  } catch (e) {
    message.error(errorMessage(e))
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { name: '', description: '', active: true, groupName: '', scoreWeight: 1 })
  show.value = true
}

function openEdit(s: Subject) {
  editingId.value = s.id
  Object.assign(form, {
    name: s.name,
    description: s.description ?? '',
    active: s.active,
    groupName: s.groupName,
    scoreWeight: s.scoreWeight,
  })
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
      name: form.name,
      description: form.description || null,
      active: form.active,
      groupName: form.groupName.trim() || null,
      scoreWeight: form.scoreWeight ?? 1,
    }
    if (editingId.value) await adminApi.updateSubject(editingId.value, body)
    else await adminApi.createSubject(body)
    message.success('Saqlandi')
    show.value = false
    load()
  } catch (e) {
    message.error(errorMessage(e))
  } finally {
    saving.value = false
  }
}

function remove(s: Subject) {
  dialog.warning({
    title: "Fanni o'chirish",
    content: `"${s.name}" nofaol qilinadi va foydalanuvchilarga ko'rinmaydi. Eski natijalar saqlanib qoladi.`,
    positiveText: "O'chirish",
    negativeText: 'Bekor qilish',
    onPositiveClick: async () => {
      try {
        await adminApi.deleteSubject(s.id)
        message.success('Fan nofaol qilindi')
        load()
      } catch (e) {
        message.error(errorMessage(e))
      }
    },
  })
}

const columns: DataTableColumns<Subject> = [
  { title: 'ID', key: 'id', width: 70 },
  {
    title: 'Nomi',
    key: 'name',
    minWidth: 200,
    render: (s) =>
      h('div', [
        h('div', { style: 'font-weight:500' }, s.name),
        s.description ? h('div', { class: 'muted', style: 'font-size:12px' }, s.description) : null,
      ]),
  },
  { title: 'Mavzular', key: 'topicCount', width: 100 },
  { title: 'Savollar', key: 'questionCount', width: 100 },
  {
    title: 'Blok test guruhi',
    key: 'groupName',
    width: 170,
    render: (s) => h('span', [s.groupName, h('span', { class: 'muted' }, ` · ${s.scoreWeight} ball`)]),
  },
  {
    title: 'Holat',
    key: 'active',
    width: 100,
    render: (s) =>
      h(NTag, { size: 'small', type: s.active ? 'success' : 'default' }, () => (s.active ? 'Faol' : 'Nofaol')),
  },
  {
    title: '',
    key: 'actions',
    width: 260,
    render: (s) =>
      h(NSpace, { size: 6, justify: 'end' }, () => [
        h(NButton, { size: 'small', onClick: () => router.push({ path: '/admin/topics', query: { subjectId: s.id } }) }, () => 'Mavzular'),
        h(NButton, { size: 'small', onClick: () => openEdit(s) }, () => 'Tahrirlash'),
        s.active ? h(NButton, { size: 'small', type: 'error', ghost: true, onClick: () => remove(s) }, () => "O'chirish") : null,
      ]),
  },
]

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1>Fanlar</h1>
      <NButton type="primary" @click="openCreate">+ Fan qo'shish</NButton>
    </div>
    <NCard content-style="padding: 0">
      <NDataTable :columns="columns" :data="rows" :loading="loading" :row-key="(r: Subject) => r.id" :scroll-x="820" />
    </NCard>

    <NModal v-model:show="show" preset="card" :title="editingId ? 'Fanni tahrirlash' : 'Yangi fan'" style="max-width: 520px; width: calc(100% - 32px)">
      <NForm ref="formRef" :model="form" @submit.prevent="save">
        <NFormItem label="Nomi" path="name" :rule="{ required: true, message: 'Nomini kiriting', trigger: 'blur' }">
          <NInput v-model:value="form.name" maxlength="200" />
        </NFormItem>
        <NFormItem label="Tavsif" path="description">
          <NInput v-model:value="form.description" type="textarea" :autosize="{ minRows: 2, maxRows: 5 }" />
        </NFormItem>
        <NFormItem label="Blok test guruhi" path="groupName">
          <NAutoComplete
            v-model:value="form.groupName"
            :options="groupOptions"
            placeholder="Bo'sh qolsa nomidan: «2-sinf Matematika» → «Matematika»"
            @select="(v: string | number) => onGroupSelect(String(v))"
          />
        </NFormItem>
        <NFormItem label="Guruhning 1 savol balli" path="scoreWeight">
          <NInputNumber v-model:value="form.scoreWeight" :min="0.1" :max="100" :step="0.1" :precision="1" style="width: 140px" />
          <span class="muted" style="margin-left: 12px; font-size: 13px">Guruhdagi barcha fanlarga qo'llanadi</span>
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
