<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { NSelect } from 'naive-ui'
import { adminApi } from '@/api'
import type { Subject } from '@/types'

defineProps<{ clearable?: boolean; placeholder?: string }>()
const model = defineModel<number | null>({ default: null })
const subjects = ref<Subject[]>([])
const loading = ref(true)

const options = computed(() =>
  subjects.value.map((s) => ({ label: s.active ? s.name : `${s.name} (nofaol)`, value: s.id })),
)

onMounted(async () => {
  try {
    subjects.value = await adminApi.subjects()
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <NSelect
    v-model:value="model"
    :options="options"
    :loading="loading"
    :clearable="clearable"
    filterable
    :placeholder="placeholder ?? 'Fanni tanlang'"
  />
</template>
