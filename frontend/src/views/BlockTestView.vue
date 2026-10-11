<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { NAlert, NButton, NCard, NCheckbox, NEmpty, NInputNumber, NSkeleton, NTag, useMessage } from 'naive-ui'
import { subjectApi, testApi } from '@/api'
import { errorMessage } from '@/api/http'
import type { Subject } from '@/types'
import { formatDuration, formatScore, shortSubjectName } from '@/utils/format'

const router = useRouter()
const message = useMessage()

// DTM'dagidek har bir fandan odatda 30 ta savol
const DEFAULT_COUNT = 30
// Har bir fanga 1 soat (backenddagi BLOCK_SECONDS_PER_SUBJECT bilan bir xil)
const SECONDS_PER_SUBJECT = 3600
// Backend bitta testda 500 tadan ortiq savolga ruxsat bermaydi
const MAX_QUESTIONS = 500

/** Blok testdagi fan: bir yoki bir nechta sinf (masalan Matematika: 1-sinf, 2-sinf) */
interface Group {
  id: number
  name: string
  scoreWeight: number
  subjects: Subject[]
}

const groups = ref<Group[]>([])
// Tanlangan sinflar (fan id lari); guruh tanlangan = undan kamida bitta sinf tanlangan
const selected = ref<Set<number>>(new Set())
const counts = reactive<Record<number, number | null>>({})
const loading = ref(true)
const starting = ref(false)

const chosenSubjects = (g: Group) => g.subjects.filter((s) => selected.value.has(s.id))
const isChosen = (g: Group) => chosenSubjects(g).length > 0
const available = (g: Group) => chosenSubjects(g).reduce((sum, s) => sum + s.questionCount, 0)
const groupTotal = (g: Group) => g.subjects.reduce((sum, s) => sum + s.questionCount, 0)

// Sinflar qatori: guruhda bir nechta fan bo'lsa yoki fan nomida sinf bo'lsa ("1-sinf Matematika")
const hasGrades = (g: Group) => g.subjects.length > 1 || g.subjects.some((s) => shortSubjectName(s.name, g.name) !== s.name)

const chosen = computed(() => groups.value.filter(isChosen))
const totalQuestions = computed(() => chosen.value.reduce((sum, g) => sum + (counts[g.id] ?? 0), 0))
const maxScore = computed(() => chosen.value.reduce((sum, g) => sum + (counts[g.id] ?? 0) * g.scoreWeight, 0))
const canStart = computed(
  () =>
    chosen.value.length > 0 &&
    totalQuestions.value <= MAX_QUESTIONS &&
    chosen.value.every((g) => !!counts[g.id] && counts[g.id]! <= available(g)),
)

function setSelected(ids: number[], on: boolean) {
  const next = new Set(selected.value)
  for (const id of ids) on ? next.add(id) : next.delete(id)
  selected.value = next
}

// Fan belgilanganda barcha sinflari tanlanadi, keyin keraksizlarini olib tashlash mumkin
function toggleGroup(g: Group) {
  const ids = g.subjects.filter((s) => s.questionCount).map((s) => s.id)
  if (!ids.length) return
  setSelected(ids, !isChosen(g))
  fitCount(g)
}

function toggleSubject(g: Group, s: Subject) {
  if (!s.questionCount) return
  setSelected([s.id], !selected.value.has(s.id))
  fitCount(g)
}

// Savollar soni tanlangan sinflardagi savollardan oshmasin
function fitCount(g: Group) {
  const max = available(g)
  if (max && (!counts[g.id] || counts[g.id]! > max)) counts[g.id] = Math.min(DEFAULT_COUNT, max)
}

onMounted(async () => {
  try {
    const byGroup = new Map<number, Group>()
    for (const s of await subjectApi.list()) {
      const g = byGroup.get(s.groupId) ?? { id: s.groupId, name: s.groupName, scoreWeight: s.scoreWeight, subjects: [] }
      g.subjects.push(s)
      byGroup.set(s.groupId, g)
    }
    groups.value = [...byGroup.values()].sort((a, b) => a.name.localeCompare(b.name))
    for (const g of groups.value) {
      g.subjects.sort((a, b) => a.name.localeCompare(b.name, undefined, { numeric: true }))
      counts[g.id] = Math.min(DEFAULT_COUNT, groupTotal(g)) || null
    }
  } catch (e) {
    message.error(errorMessage(e))
  } finally {
    loading.value = false
  }
})

async function start() {
  if (!canStart.value) return
  starting.value = true
  try {
    const { id } = await testApi.createBlock(
      chosen.value.map((g) => ({
        groupId: g.id,
        subjectIds: chosenSubjects(g).map((s) => s.id),
        questionCount: counts[g.id]!,
      })),
    )
    router.push(`/test/${id}`)
  } catch (e) {
    message.error(errorMessage(e))
  } finally {
    starting.value = false
  }
}
</script>

<template>
  <div class="page page-narrow">
    <div class="page-header">
      <div>
        <h1>Blok test</h1>
        <div class="muted">Davlat imtihonlari (DTM) uslubida: bir nechta fan, har biri alohida bo'limda, natija ballda</div>
      </div>
    </div>

    <NCard v-if="loading"><NSkeleton text :repeat="6" /></NCard>

    <template v-else>
      <NCard title="Fanlar va savollar soni" size="small" style="margin-bottom: 16px">
        <NEmpty v-if="!groups.length" description="Hozircha fanlar yo'q" />
        <div v-else class="group-list">
          <div
            v-for="g in groups"
            :key="g.id"
            class="group"
            :class="{ disabled: !groupTotal(g), active: isChosen(g) }"
          >
            <div class="group-head" @click="toggleGroup(g)">
              <NCheckbox
                :checked="isChosen(g)"
                :indeterminate="isChosen(g) && chosenSubjects(g).length < g.subjects.filter((s) => s.questionCount).length"
                :disabled="!groupTotal(g)"
                @click.stop
                @update:checked="toggleGroup(g)"
              />
              <div class="group-text">
                <div class="group-name">{{ g.name }}</div>
                <div class="muted small tabular">
                  1 savol = {{ formatScore(g.scoreWeight) }} ball ·
                  {{ isChosen(g) ? `tanlanganlarda ${available(g)}` : `bazada ${groupTotal(g)}` }} ta savol
                </div>
              </div>
              <div class="count" @click.stop>
                <NInputNumber
                  v-model:value="counts[g.id]"
                  :min="1"
                  :max="Math.max(available(g), 1)"
                  :disabled="!isChosen(g)"
                  size="small"
                  style="width: 96px"
                />
                <span class="muted small">ta</span>
              </div>
            </div>
            <div v-if="hasGrades(g)" class="grades">
              <span class="muted small">Sinflar:</span>
              <NCheckbox
                v-for="s in g.subjects"
                :key="s.id"
                :checked="selected.has(s.id)"
                :disabled="!s.questionCount"
                @update:checked="toggleSubject(g, s)"
              >
                {{ shortSubjectName(s.name, g.name) }} <span class="muted tabular">({{ s.questionCount }})</span>
              </NCheckbox>
            </div>
          </div>
        </div>
      </NCard>

      <NCard size="small" style="margin-bottom: 16px">
        <div class="summary tabular">
          <div>
            <div class="muted small">Savollar</div>
            <div class="summary-value">{{ totalQuestions }}</div>
          </div>
          <div>
            <div class="muted small">Maksimal ball</div>
            <div class="summary-value">{{ formatScore(maxScore) }}</div>
          </div>
          <div>
            <div class="muted small">Vaqt</div>
            <div class="summary-value">{{ chosen.length ? formatDuration(chosen.length * SECONDS_PER_SUBJECT) : '—' }}</div>
          </div>
          <div class="summary-tags">
            <NTag v-for="g in chosen" :key="g.id" size="small" :bordered="false">
              {{ g.name }}: {{ counts[g.id] ?? 0 }} × {{ formatScore(g.scoreWeight) }}
            </NTag>
          </div>
        </div>
        <NAlert :show-icon="false" style="margin-top: 14px">
          Har bir fanga 1 soat vaqt beriladi, vaqt tugaganda test avtomatik yakunlanadi. Bir nechta sinf tanlansa,
          savollar ular orasida teng taqsimlanadi. Har bir fan savollari alohida bo'limda turadi, natija va izohlar
          faqat yakunlangach ko'rsatiladi. Ball = to'g'ri javoblar × fanning 1 savol balli.
        </NAlert>
        <NAlert v-if="totalQuestions > MAX_QUESTIONS" type="warning" :show-icon="false" style="margin-top: 10px">
          Bitta testda ko'pi bilan {{ MAX_QUESTIONS }} ta savol bo'lishi mumkin.
        </NAlert>
      </NCard>

      <NButton type="primary" size="large" block :disabled="!canStart" :loading="starting" @click="start">
        Blok testni boshlash{{ canStart ? ` (${totalQuestions} ta savol)` : '' }}
      </NButton>
    </template>
  </div>
</template>

<style scoped>
.group-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.group {
  border-radius: 8px;
  border: 1px solid transparent;
  transition: background 0.15s, border-color 0.15s;
}
.group.active {
  border-color: rgba(127, 127, 127, 0.2);
}
.group.disabled {
  opacity: 0.55;
}
.group-head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
}
.group-head:hover {
  background: rgba(127, 127, 127, 0.08);
}
.group.disabled .group-head {
  cursor: not-allowed;
}
.group-text {
  flex: 1;
  min-width: 0;
}
.group-name {
  font-weight: 500;
}
.small {
  font-size: 13px;
}
.count {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: default;
}
.grades {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px 16px;
  padding: 0 12px 12px 42px;
}
.summary {
  display: flex;
  align-items: center;
  gap: 28px;
  flex-wrap: wrap;
}
.summary-value {
  font-size: 24px;
  font-weight: 650;
}
.summary-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  flex: 1;
  justify-content: flex-end;
}
</style>
