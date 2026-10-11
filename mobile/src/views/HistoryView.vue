<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { NIcon, useDialog, useMessage } from 'naive-ui'
import { ChevronForward, PlayCircle, TrashOutline } from '@vicons/ionicons5'
import { testApi } from '@/api'
import { errorMessage } from '@/api/http'
import type { SessionSummary } from '@/types'
import { formatDate, formatScore, modeLabel, topicSummary } from '@/utils/format'
import GradeMark from '@m/components/GradeMark.vue'
import { grade } from '@m/utils/grade'

const PAGE = 20
const router = useRouter()
const dialog = useDialog()
const message = useMessage()
const items = ref<SessionSummary[]>([])
const total = ref(0)
const page = ref(0)
const loaded = ref(false)

async function load(next: number) {
  const res = await testApi.history(next, PAGE)
  items.value = next === 0 ? res.items : [...items.value, ...res.items]
  total.value = res.total
  page.value = next
  loaded.value = true
}
onMounted(() => load(0))

function clearHistory() {
  dialog.warning({
    title: 'Tarixni tozalaysizmi?',
    content: "Barcha yechgan testlaringiz, natijalar va statistika o'chiriladi. Fanlar va savollar o'zgarmaydi.",
    positiveText: 'Tozalash',
    negativeText: 'Bekor qilish',
    onPositiveClick: async () => {
      try {
        await testApi.clearHistory()
        message.success('Tarix tozalandi')
        await load(0)
      } catch (e) {
        message.error(errorMessage(e))
      }
    },
  })
}

const open = (s: SessionSummary) => router.push(s.status === 'FINISHED' ? `/test/${s.id}/result` : `/test/${s.id}`)
</script>

<template>
  <main class="screen">
    <div class="head">
      <h1 class="screen-title" style="padding-top: 12px">Tarix</h1>
      <button v-if="total > 0" class="clear" aria-label="Tarixni tozalash" @click="clearHistory">
        <NIcon :component="TrashOutline" size="18" /> Tozalash
      </button>
    </div>

    <div v-if="loaded && !items.length" class="empty">
      <p>Hali test yechilmagan.</p>
      <button class="btn btn-primary" @click="router.replace('/')">Fan tanlash</button>
    </div>

    <div class="list">
      <button v-for="s in items" :key="s.id" class="item card" @click="open(s)">
        <GradeMark v-if="s.status === 'FINISHED'" :grade="grade(s.scorePercent ?? 0)" :size="48" />
        <div v-else class="progress-icon"><NIcon :component="PlayCircle" size="30" /></div>
        <div class="text">
          <div class="title">{{ s.mode === 'BLOCK' ? 'Blok test' : s.subjectName }}</div>
          <div class="muted small">
            {{ s.mode === 'BLOCK' ? s.groupLabels.join(', ') : topicSummary(s.topicNames.map((n) => n.replace(/^\d+\.\s*/, ''))) }}
          </div>
          <div class="muted small tabular">
            <template v-if="s.status === 'FINISHED' && s.mode === 'BLOCK'">{{ formatScore(s.score) }}/{{ formatScore(s.maxScore) }} ball · </template>
            <template v-else-if="s.status === 'FINISHED'">{{ s.correctCount }}/{{ s.questionCount }} to'g'ri · </template>
            <template v-else>Davom etmoqda · {{ s.answeredCount }}/{{ s.questionCount }} · </template>
            {{ modeLabel[s.mode] }} · {{ formatDate(s.startedAt).slice(0, 5) }}
          </div>
        </div>
        <NIcon :component="ChevronForward" size="18" class="muted" />
      </button>
    </div>
    <button v-if="items.length < total" class="btn btn-secondary btn-block" style="margin-top: 12px" @click="load(page + 1)">Yana ko'rsatish</button>
  </main>
</template>

<style scoped>
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.clear {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 12px;
  padding: 8px 12px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: #d03050;
  font: inherit;
  font-size: 14px;
  font-weight: 600;
}
.clear:active {
  background: rgba(208, 48, 80, 0.1);
}
.list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 16px;
}
.item {
  width: 100%;
  border: 0;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 14px;
  text-align: left;
}
.progress-icon {
  width: 48px;
  height: 48px;
  flex: none;
  border-radius: 50%;
  background: var(--marker-soft);
  color: var(--ink);
  display: flex;
  align-items: center;
  justify-content: center;
}
.text {
  flex: 1;
  min-width: 0;
}
.title {
  font-weight: 650;
}
.small {
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.empty {
  text-align: center;
  margin-top: 60px;
  color: var(--muted);
}
</style>
