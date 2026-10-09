<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NForm, NFormItem, NInput, useMessage, type FormInst, type FormRules } from 'naive-ui'
import AuthCard from '@/components/AuthCard.vue'
import { useAuthStore } from '@/stores/auth'
import { errorMessage } from '@/api/http'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const message = useMessage()

const formRef = ref<FormInst | null>(null)
const loading = ref(false)
const form = reactive({ username: '', password: '' })
const rules: FormRules = {
  username: { required: true, message: 'Loginni kiriting', trigger: 'blur' },
  password: { required: true, message: 'Parolni kiriting', trigger: 'blur' },
}

async function submit() {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  loading.value = true
  try {
    await auth.login(form.username, form.password)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    router.replace(redirect)
  } catch (e) {
    message.error(errorMessage(e))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <AuthCard title="Kirish">
    <NForm ref="formRef" :model="form" :rules="rules" @submit.prevent="submit">
      <NFormItem label="Login" path="username">
        <NInput v-model:value="form.username" placeholder="login" :input-props="{ autocomplete: 'username' }" />
      </NFormItem>
      <NFormItem label="Parol" path="password">
        <NInput
          v-model:value="form.password"
          type="password"
          show-password-on="click"
          placeholder="••••••"
          :input-props="{ autocomplete: 'current-password' }"
        />
      </NFormItem>
      <NButton type="primary" block attr-type="submit" :loading="loading">Kirish</NButton>
    </NForm>
    <p class="muted" style="margin-bottom: 0; text-align: center">
      Akkauntingiz yo'qmi? <RouterLink to="/register">Ro'yxatdan o'ting</RouterLink>
    </p>
  </AuthCard>
</template>
