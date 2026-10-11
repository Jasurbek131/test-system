<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NCard, NEmpty, NGrid, NGridItem, NSkeleton, NTag, useMessage } from 'naive-ui'
import { subjectApi } from '@/api'
import { errorMessage } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import type { Subject } from '@/types'

const router = useRouter()
const message = useMessage()
const auth = useAuthStore()
const subjects = ref<Subject[]>([])
const loading = ref(true)

onMounted(async () => {
  try {
    subjects.value = await subjectApi.list()
  } catch (e) {
    message.error(errorMessage(e))
  } finally {
    loading.value = false
  }
})

const start = (s: Subject) => router.push({ path: '/test/new', query: { subjectId: s.id } })
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1>Fanlar</h1>
        <div class="muted">Fanni tanlang, mavzularni belgilang va test yechib o'rganing</div>
      </div>
    </div>

    <NCard v-if="!loading && subjects.length > 1" class="block-banner" @click="router.push('/block/new')">
      <div class="block-banner-body">
        <div>
          <div class="subject-name">Blok test (DTM uslubida)</div>
          <div class="muted">Bir nechta fanni tanlang, har bir fan alohida bo'limda, natija ballda hisoblanadi</div>
        </div>
        <NButton type="primary" @click.stop="router.push('/block/new')">Blok testni boshlash</NButton>
      </div>
    </NCard>

    <NGrid v-if="loading" cols="1 s:2 l:3" responsive="screen" :x-gap="16" :y-gap="16">
      <NGridItem v-for="i in 3" :key="i">
        <NCard><NSkeleton text :repeat="3" /></NCard>
      </NGridItem>
    </NGrid>

    <NEmpty v-else-if="!subjects.length" description="Hozircha fanlar yo'q" style="margin-top: 60px">
      <template v-if="auth.isAdmin" #extra>
        <NButton type="primary" @click="router.push('/admin/subjects')">Fan qo'shish</NButton>
      </template>
    </NEmpty>

    <NGrid v-else cols="1 s:2 l:3" responsive="screen" :x-gap="16" :y-gap="16">
      <NGridItem v-for="s in subjects" :key="s.id">
        <NCard hoverable class="subject-card" @click="s.questionCount && start(s)">
          <div class="subject-name">{{ s.name }}</div>
          <p class="muted description">{{ s.description || 'Tavsif kiritilmagan' }}</p>
          <div class="meta">
            <NTag size="small" :bordered="false">{{ s.topicCount }} ta mavzu</NTag>
            <NTag size="small" :bordered="false" type="info">{{ s.questionCount }} ta savol</NTag>
          </div>
          <template #action>
            <NButton type="primary" block :disabled="!s.questionCount" @click.stop="start(s)">
              {{ s.questionCount ? 'Test boshlash' : "Savollar yo'q" }}
            </NButton>
          </template>
        </NCard>
      </NGridItem>
    </NGrid>
  </div>
</template>

<style scoped>
.block-banner {
  cursor: pointer;
  margin-bottom: 16px;
}
.block-banner-body {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.subject-card {
  cursor: pointer;
  height: 100%;
}
.subject-name {
  font-size: 18px;
  font-weight: 600;
}
.description {
  min-height: 42px;
  margin: 8px 0 14px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.meta {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
