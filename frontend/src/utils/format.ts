import type { Difficulty, TestMode } from '@/types'

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
}

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
  if (h > 0) return `${h} soat ${m} daq`
  if (m > 0) return `${m} daq ${s} son`
  return `${s} son`
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
