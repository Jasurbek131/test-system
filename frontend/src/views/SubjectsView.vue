<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NCard, NEmpty, NGrid, NGridItem, NSkeleton, NTag, useMessage } from 'naive-ui'
import { subjectApi } from '@/api'
import { errorMessage } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import type { Subject } from '@/types'

const router = useRouter()
const route = useRoute()
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

interface Group {
  id: number
  name: string
  subjects: Subject[]
  questionCount: number
}

const gradeOf = (s: Subject) => Number(s.name.match(/^(\d+)-sinf/i)?.[1] ?? 0)

// Fanlar guruhlarga (masalan, «Matematika» – 1-sinf, 2-sinf) birlashtiriladi, ichida sinf bo'yicha tartiblanadi
const groups = computed<Group[]>(() => {
  const map = new Map<number, Group>()
  for (const s of subjects.value) {
    let g = map.get(s.groupId)
    if (!g) map.set(s.groupId, (g = { id: s.groupId, name: s.groupName, subjects: [], questionCount: 0 }))
    g.subjects.push(s)
    g.questionCount += s.questionCount
  }
  for (const g of map.values()) g.subjects.sort((a, b) => gradeOf(a) - gradeOf(b) || a.name.localeCompare(b.name))
  return [...map.values()]
})

const selectedGroup = computed(() => groups.value.find((g) => String(g.id) === route.query.group) ?? null)

// Guruhda bitta fan bo'lsa, sinf tanlash bosqichi o'tkazib yuboriladi
const openGroup = (g: Group) => {
  if (g.subjects.length === 1) start(g.subjects[0])
  else router.push({ query: { group: g.id } })
}
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1>Fanlar</h1>
        <div class="muted">Fanni va sinfni tanlang, mavzularni belgilang va test yechib o'rganing</div>
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

    <template v-else-if="selectedGroup">
      <div class="group-header">
        <NButton quaternary @click="router.push({ query: {} })">← Barcha fanlar</NButton>
        <h2>{{ selectedGroup.name }}</h2>
      </div>
      <NGrid cols="1 s:2 l:3" responsive="screen" :x-gap="16" :y-gap="16">
        <NGridItem v-for="s in selectedGroup.subjects" :key="s.id">
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
    </template>

    <NGrid v-else cols="1 s:2 l:3" responsive="screen" :x-gap="16" :y-gap="16">
      <NGridItem v-for="g in groups" :key="g.id">
        <NCard hoverable class="subject-card" @click="g.questionCount && openGroup(g)">
          <div class="subject-name">{{ g.name }}</div>
          <p class="muted description">{{ g.subjects.map((s) => s.name).join(', ') }}</p>
          <div class="meta">
            <NTag size="small" :bordered="false">{{ g.subjects.length }} ta fan</NTag>
            <NTag size="small" :bordered="false" type="info">{{ g.questionCount }} ta savol</NTag>
          </div>
          <template #action>
            <NButton type="primary" block :disabled="!g.questionCount" @click.stop="openGroup(g)">
              {{ !g.questionCount ? "Savollar yo'q" : g.subjects.length > 1 ? 'Sinfni tanlash' : 'Test boshlash' }}
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
.group-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.group-header h2 {
  margin: 0;
  font-size: 20px;
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
