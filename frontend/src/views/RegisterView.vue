<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NForm, NFormItem, NInput, useMessage, type FormInst, type FormRules } from 'naive-ui'
import AuthCard from '@/components/AuthCard.vue'
import { useAuthStore } from '@/stores/auth'
import { errorMessage } from '@/api/http'

const auth = useAuthStore()
const router = useRouter()
const message = useMessage()

const formRef = ref<FormInst | null>(null)
const loading = ref(false)
const form = reactive({ fullName: '', username: '', password: '', confirm: '' })
const rules: FormRules = {
  fullName: { required: true, message: 'Ismingizni kiriting', trigger: 'blur' },
  username: [
    { required: true, message: 'Loginni kiriting', trigger: 'blur' },
    {
      pattern: /^[a-zA-Z0-9_.]{3,50}$/,
      message: "3–50 belgi: lotin harflari, raqamlar, _ va .",
      trigger: 'blur',
    },
  ],
  password: [
    { required: true, message: 'Parolni kiriting', trigger: 'blur' },
    { min: 6, message: "Kamida 6 belgi bo'lsin", trigger: 'blur' },
  ],
  confirm: {
    required: true,
    trigger: 'blur',
    validator: (_r, v: string) => (v === form.password ? true : new Error('Parollar mos emas')),
  },
}

async function submit() {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  loading.value = true
  try {
    await auth.register({ fullName: form.fullName, username: form.username, password: form.password })
    message.success("Xush kelibsiz!")
    router.replace('/')
  } catch (e) {
    message.error(errorMessage(e))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <AuthCard title="Ro'yxatdan o'tish">
    <NForm ref="formRef" :model="form" :rules="rules" @submit.prevent="submit">
      <NFormItem label="Ism familiya" path="fullName">
        <NInput v-model:value="form.fullName" placeholder="Ali Valiyev" />
      </NFormItem>
      <NFormItem label="Login" path="username">
        <NInput v-model:value="form.username" placeholder="ali_valiyev" :input-props="{ autocomplete: 'username' }" />
      </NFormItem>
      <NFormItem label="Parol" path="password">
        <NInput
          v-model:value="form.password"
          type="password"
          show-password-on="click"
          :input-props="{ autocomplete: 'new-password' }"
        />
      </NFormItem>
      <NFormItem label="Parolni takrorlang" path="confirm">
        <NInput
          v-model:value="form.confirm"
          type="password"
          show-password-on="click"
          :input-props="{ autocomplete: 'new-password' }"
        />
      </NFormItem>
      <NButton type="primary" block attr-type="submit" :loading="loading">Ro'yxatdan o'tish</NButton>
    </NForm>
    <p class="muted" style="margin-bottom: 0; text-align: center">
      Akkauntingiz bormi? <RouterLink to="/login">Kirish</RouterLink>
    </p>
  </AuthCard>
</template>
