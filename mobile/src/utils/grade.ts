/** Foizdan maktab bahosi (5 balli tizim) – natijani o'quvchiga tanish ko'rinishda berish uchun. */
export function grade(percent: number): 5 | 4 | 3 | 2 {
  if (percent >= 86) return 5
  if (percent >= 71) return 4
  if (percent >= 56) return 3
  return 2
}

export const gradeWord: Record<number, string> = {
  5: "A'lo",
  4: 'Yaxshi',
  3: 'Qoniqarli',
  2: 'Yana mashq qiling',
}

/** Har bir fanga daftar muqovasidek o'z rangi (id bo'yicha barqaror). */
const COVERS = ['#2f6fdb', '#1f9d6b', '#e0802b', '#8a4fd8', '#d1446b', '#168aa3']
export function coverColor(id: number): string {
  return COVERS[(id - 1) % COVERS.length]
}

export function initials(name: string): string {
  const words = name.replace(/^\d+-sinf\s+/i, '').trim()
  return words.slice(0, 1).toUpperCase()
}

export function gradeLabel(subject: string): string {
  const m = subject.match(/^(\d+)-sinf/i)
  return m ? `${m[1]}-sinf` : ''
}
