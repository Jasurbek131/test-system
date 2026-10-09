<script setup lang="ts">
import { ref } from 'vue'
import {
  NAlert,
  NButton,
  NCard,
  NCollapse,
  NCollapseItem,
  NInput,
  NList,
  NListItem,
  NSpace,
  NUpload,
  type UploadFileInfo,
  useMessage,
} from 'naive-ui'
import { adminApi } from '@/api'
import { errorMessage } from '@/api/http'
import type { ImportReport } from '@/types'

const message = useMessage()
const text = ref('')
const loading = ref(false)
const report = ref<ImportReport | null>(null)

const sample = `{
  "subject": "Fizika",
  "subjectDescription": "Mexanika asoslari",
  "topics": [
    {
      "name": "Kinematika",
      "description": "Harakat turlari",
      "questions": [
        {
          "text": "Tezlikning SI birligi qaysi?",
          "explanation": "Tezlik = yo'l / vaqt, ya'ni metr / sekund.",
          "difficulty": "EASY",
          "options": [
            { "text": "m/s", "correct": true },
            { "text": "km", "correct": false },
            { "text": "m/s²", "correct": false },
            { "text": "N", "correct": false }
          ]
        }
      ]
    }
  ]
}`

function onFile({ file }: { file: UploadFileInfo }) {
  const raw = file.file
  if (!raw) return false
  const reader = new FileReader()
  reader.onload = () => (text.value = String(reader.result ?? ''))
  reader.readAsText(raw, 'utf-8')
  return false
}

async function submit() {
  let payload: unknown
  try {
    payload = JSON.parse(text.value)
  } catch (e) {
    message.error('JSON formati noto\'g\'ri: ' + (e as Error).message)
    return
  }
  loading.value = true
  report.value = null
  try {
    report.value = await adminApi.importQuestions(payload)
    message.success(`${report.value.questionsImported} ta savol import qilindi`)
  } catch (e) {
    message.error(errorMessage(e))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page page-narrow">
    <div class="page-header">
      <div>
        <h1>Savollarni import qilish</h1>
        <div class="muted">JSON fayl yuklang yoki matnni joylang. Fan va mavzular nomi bo'yicha topiladi, bo'lmasa yaratiladi.</div>
      </div>
    </div>

    <NCard size="small" style="margin-bottom: 16px">
      <NSpace vertical>
        <NUpload accept=".json,application/json" :max="1" :show-file-list="false" :custom-request="() => {}" @before-upload="onFile">
          <NButton>JSON fayl tanlash</NButton>
        </NUpload>
        <NInput
          v-model:value="text"
          type="textarea"
          :autosize="{ minRows: 10, maxRows: 24 }"
          placeholder="JSON ni shu yerga joylang"
          style="font-family: ui-monospace, monospace; font-size: 13px"
        />
        <NSpace>
          <NButton type="primary" :disabled="!text.trim()" :loading="loading" @click="submit">Import qilish</NButton>
          <NButton quaternary @click="text = sample">Namunani qo'yish</NButton>
        </NSpace>
      </NSpace>
    </NCard>

    <NCard v-if="report" title="Hisobot" size="small" style="margin-bottom: 16px">
      <NAlert :type="report.errors.length ? 'warning' : 'success'" :show-icon="false" style="margin-bottom: 12px">
        Fan {{ report.subjectCreated ? 'yaratildi' : 'mavjud edi' }} · yangi mavzular: {{ report.topicsCreated }} ·
        import qilindi: <b>{{ report.questionsImported }}</b> · o'tkazib yuborildi: {{ report.questionsSkipped }}
      </NAlert>
      <NList v-if="report.errors.length" bordered size="small">
        <NListItem v-for="(e, i) in report.errors" :key="i">
          <code>{{ e.path }}</code> — {{ e.message }}
        </NListItem>
      </NList>
    </NCard>

    <NCollapse>
      <NCollapseItem title="Fayl formati" name="format">
        <ul class="muted" style="margin-top: 0; padding-left: 18px">
          <li>Har bir savolda 2–6 variant va aynan bitta <code>"correct": true</code> bo'lishi kerak.</li>
          <li><code>difficulty</code>: EASY, MEDIUM yoki HARD (ixtiyoriy, standart MEDIUM).</li>
          <li>Xato savollar o'tkazib yuboriladi, qolganlari import qilinadi.</li>
        </ul>
        <pre class="sample">{{ sample }}</pre>
      </NCollapseItem>
    </NCollapse>
  </div>
</template>

<style scoped>
.sample {
  margin: 0;
  padding: 12px;
  border-radius: 8px;
  background: rgba(127, 127, 127, 0.08);
  font-size: 12.5px;
  overflow-x: auto;
}
</style>
