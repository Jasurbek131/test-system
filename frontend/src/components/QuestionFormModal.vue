<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import {
  NButton,
  NForm,
  NFormItem,
  NIcon,
  NInput,
  NModal,
  NRadio,
  NRadioButton,
  NRadioGroup,
  NSelect,
  NSpace,
  NSwitch,
  useMessage,
  type FormInst,
} from 'naive-ui'
import { TrashOutline } from '@vicons/ionicons5'
import SubjectSelect from '@/components/SubjectSelect.vue'
import { adminApi } from '@/api'
import { errorMessage } from '@/api/http'
import type { Difficulty, QuestionAdmin, Topic } from '@/types'
import { optionLetter } from '@/utils/format'

const props = defineProps<{ question: QuestionAdmin | null; defaultSubjectId: number | null; defaultTopicId: number | null }>()
const show = defineModel<boolean>('show', { required: true })
const emit = defineEmits<{ saved: [] }>()
const message = useMessage()

interface FormOption {
  key: number
  id: number | null
  text: string
}

let keySeq = 0
const formRef = ref<FormInst | null>(null)
const saving = ref(false)
const topics = ref<Topic[]>([])
const form = reactive({
  subjectId: null as number | null,
  topicId: null as number | null,
  text: '',
  explanation: '',
  difficulty: 'MEDIUM' as Difficulty,
  active: true,
  options: [] as FormOption[],
  correctKey: null as number | null,
})

const topicOptions = computed(() =>
  topics.value.map((t) => ({ label: t.active ? t.name : `${t.name} (nofaol)`, value: t.id })),
)

const newOption = (id: number | null = null, text = ''): FormOption => ({ key: ++keySeq, id, text })

watch(show, (v) => {
  if (!v) return
  const q = props.question
  if (q) {
    const opts = q.options.map((o) => newOption(o.id ?? null, o.text))
    Object.assign(form, {
      subjectId: q.subjectId,
      topicId: q.topicId,
      text: q.text,
      explanation: q.explanation ?? '',
      difficulty: q.difficulty,
      active: q.active,
      options: opts,
      correctKey: opts[q.options.findIndex((o) => o.correct)]?.key ?? null,
    })
  } else {
    Object.assign(form, {
      subjectId: props.defaultSubjectId,
      topicId: props.defaultTopicId,
      text: '',
      explanation: '',
      difficulty: 'MEDIUM',
      active: true,
      options: [newOption(), newOption(), newOption(), newOption()],
      correctKey: null,
    })
  }
})

watch(
  () => form.subjectId,
  async (id, old) => {
    topics.value = id ? await adminApi.topics(id).catch(() => []) : []
    if (old !== undefined && old !== null && id !== old) form.topicId = null
  },
)

function addOption() {
  if (form.options.length < 6) form.options.push(newOption())
}

function removeOption(key: number) {
  if (form.options.length <= 2) return
  form.options = form.options.filter((o) => o.key !== key)
  if (form.correctKey === key) form.correctKey = null
}

async function save() {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  if (form.options.some((o) => !o.text.trim())) return message.warning("Barcha variantlar matnini kiriting")
  if (form.correctKey === null) return message.warning("To'g'ri javobni belgilang")

  saving.value = true
  try {
    const body = {
      topicId: form.topicId!,
      text: form.text,
      explanation: form.explanation || null,
      difficulty: form.difficulty,
      active: form.active,
      options: form.options.map((o) => ({ id: o.id, text: o.text, correct: o.key === form.correctKey })),
    }
    if (props.question) await adminApi.updateQuestion(props.question.id, body)
    else await adminApi.createQuestion(body)
    message.success('Saqlandi')
    show.value = false
    emit('saved')
  } catch (e) {
    message.error(errorMessage(e))
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <NModal
    v-model:show="show"
    preset="card"
    :title="question ? 'Savolni tahrirlash' : 'Yangi savol'"
    style="max-width: 720px; width: calc(100% - 32px)"
    :mask-closable="false"
  >
    <NForm ref="formRef" :model="form">
      <div class="two-cols">
        <NFormItem label="Fan" path="subjectId" :rule="{ required: true, type: 'number', message: 'Fanni tanlang', trigger: 'change' }">
          <SubjectSelect v-model="form.subjectId" :disabled="!!question" />
        </NFormItem>
        <NFormItem label="Mavzu" path="topicId" :rule="{ required: true, type: 'number', message: 'Mavzuni tanlang', trigger: 'change' }">
          <NSelect v-model:value="form.topicId" :options="topicOptions" filterable placeholder="Mavzuni tanlang" :disabled="!form.subjectId" />
        </NFormItem>
      </div>
      <NFormItem label="Savol matni" path="text" :rule="{ required: true, message: 'Savol matnini kiriting', trigger: 'blur' }">
        <NInput v-model:value="form.text" type="textarea" :autosize="{ minRows: 2, maxRows: 8 }" />
      </NFormItem>

      <NFormItem label="Variantlar (to'g'risini belgilang)" :show-feedback="false" style="margin-bottom: 18px">
        <NRadioGroup v-model:value="form.correctKey" style="width: 100%">
          <div class="options">
            <div v-for="(o, i) in form.options" :key="o.key" class="option-row">
              <NRadio :value="o.key" :aria-label="`${optionLetter(i)} to'g'ri`" />
              <span class="letter">{{ optionLetter(i) }}</span>
              <NInput v-model:value="o.text" type="textarea" :autosize="{ minRows: 1, maxRows: 4 }" placeholder="Variant matni" />
              <NButton quaternary circle :disabled="form.options.length <= 2" aria-label="O'chirish" @click="removeOption(o.key)">
                <template #icon><NIcon :component="TrashOutline" /></template>
              </NButton>
            </div>
            <NButton dashed :disabled="form.options.length >= 6" @click="addOption">+ Variant qo'shish</NButton>
          </div>
        </NRadioGroup>
      </NFormItem>

      <NFormItem label="Izoh (nega bu javob to'g'ri)" path="explanation">
        <NInput v-model:value="form.explanation" type="textarea" :autosize="{ minRows: 2, maxRows: 6 }" />
      </NFormItem>
      <div class="two-cols">
        <NFormItem label="Qiyinlik" path="difficulty">
          <NRadioGroup v-model:value="form.difficulty">
            <NRadioButton value="EASY">Oson</NRadioButton>
            <NRadioButton value="MEDIUM">O'rta</NRadioButton>
            <NRadioButton value="HARD">Qiyin</NRadioButton>
          </NRadioGroup>
        </NFormItem>
        <NFormItem label="Faol" path="active">
          <NSwitch v-model:value="form.active" />
        </NFormItem>
      </div>
    </NForm>
    <template #action>
      <NSpace justify="end">
        <NButton @click="show = false">Bekor qilish</NButton>
        <NButton type="primary" :loading="saving" @click="save">Saqlash</NButton>
      </NSpace>
    </template>
  </NModal>
</template>

<style scoped>
.two-cols {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}
@media (max-width: 600px) {
  .two-cols {
    grid-template-columns: 1fr;
  }
}
.options {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}
.option-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.letter {
  width: 18px;
  font-weight: 600;
  flex: none;
}
</style>
