import type { Difficulty, MasteryLevel, TestMode } from '@/types'

export const difficultyLabel: Record<Difficulty, string> = {
  EASY: 'Oson',
  MEDIUM: "O'rta",
  HARD: 'Qiyin',
}

export const difficultyType: Record<Difficulty, 'success' | 'warning' | 'error'> = {
  EASY: 'success',
  MEDIUM: 'warning',
  HARD: 'error',
}

export const modeLabel: Record<TestMode, string> = {
  LEARNING: "O'rganish",
  EXAM: 'Imtihon',
  BLOCK: 'Blok test',
  FLASHCARD: 'Kartochka',
  BLITZ: 'Blits',
}


export const masteryLabel: Record<MasteryLevel, string> = {
  NEW: 'Yangi',
  LEARNING: "O'rganilmoqda",
  MASTERED: "O'zlashtirildi",
}

/** "1-sinf Matematika" -> 1; sinf ko'rsatilmagan bo'lsa 0 */
export const gradeOf = (subjectName: string) => Number(subjectName.match(/^(\d+)-sinf/i)?.[1] ?? 0)

/** Kichik sinflar (1–4) uchun bolalarga mos, yirikroq va rang-barang ko'rinish */
export const isKidsSubject = (subjectName: string) => {
  const g = gradeOf(subjectName)
  return g >= 1 && g <= 4
}

/** Ballni chiroyli ko'rsatish: 93 -> "93", 52.7 -> "52.7" */
export const formatScore = (n: number | null | undefined) => (n == null ? '—' : String(Math.round(n * 10) / 10))

export function formatDate(iso: string | null | undefined): string {
  if (!iso) return '—'
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getDate())}.${pad(d.getMonth() + 1)}.${d.getFullYear()} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

export function formatDuration(seconds: number): string {
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = seconds % 60
  if (h > 0) return m > 0 ? `${h} soat ${m} daq` : `${h} soat`
  if (m > 0) return `${m} daq ${s} son`
  return `${s} son`
}

/** Guruh ichida fanning qisqa nomi: ("1-sinf Matematika", "Matematika") -> "1-sinf" */
export function shortSubjectName(subjectName: string, groupName: string): string {
  const rest = subjectName.replace(new RegExp(groupName.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'i'), '').trim()
  return rest || subjectName
}

/** Teskari sanoq uchun: 7265 -> "2:01:05", 125 -> "2:05" */
export function formatClock(seconds: number): string {
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = seconds % 60
  const pad = (n: number) => String(n).padStart(2, '0')
  return h > 0 ? `${h}:${pad(m)}:${pad(s)}` : `${m}:${pad(s)}`
}

/** Foizga qarab rang: progress va teglar uchun. */
export function scoreStatus(percent: number): 'success' | 'warning' | 'error' {
  if (percent >= 80) return 'success'
  if (percent >= 50) return 'warning'
  return 'error'
}

export const optionLetter = (i: number) => String.fromCharCode(65 + i)

/** Ko'p mavzuli testlar uchun qisqa ro'yxat: "A, B va yana 16 ta mavzu". */
export function topicSummary(names: string[], max = 2): string {
  if (names.length <= max + 1) return names.join(', ')
  return `${names.slice(0, max).join(', ')} va yana ${names.length - max} ta mavzu`
}
