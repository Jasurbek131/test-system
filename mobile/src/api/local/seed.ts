import type { Difficulty } from '@/types'
import { db, defaultGroupName } from './db'

interface SeedOption { text: string; correct: boolean }
interface SeedQuestion { text: string; explanation?: string | null; difficulty?: Difficulty | null; options: SeedOption[] }
interface SeedTopic { name: string; description?: string | null; questions?: SeedQuestion[] }
export interface SeedFile {
  subject: string
  subjectDescription?: string | null
  group?: string | null
  scoreWeight?: number | null
  topics: SeedTopic[]
}

// Fanlar backend bilan bir xil manbadan olinadi (backend/src/main/resources/seed/*.json) va ilovaga qo'shib yig'iladi.
const files = import.meta.glob<SeedFile>('../../../../backend/src/main/resources/seed/*.json', { import: 'default' })

function validQuestion(q: SeedQuestion | null | undefined): q is SeedQuestion {
  if (!q?.text?.trim() || !Array.isArray(q.options)) return false
  if (q.options.length < 2 || q.options.length > 6) return false
  if (q.options.some((o) => !o?.text?.trim())) return false
  if (q.options.filter((o) => o.correct).length !== 1) return false
  return new Set(q.options.map((o) => o.text.trim().toLowerCase())).size === q.options.length
}

/** Bitta fanni yuklaydi. Shu nomli fan bazada bo'lsa – hech narsa qilmaydi (backenddagi SeedDataLoader kabi). */
export async function importSubject(data: SeedFile): Promise<boolean> {
  const name = data.subject?.trim()
  if (!name || !Array.isArray(data.topics)) return false
  return db.transaction('rw', [db.subjects, db.topics, db.questions, db.options], async () => {
    if (await db.subjects.where('name').equals(name).count()) return false
    const subjectId = await db.subjects.add({
      name,
      description: data.subjectDescription?.trim() || null,
      groupName: data.group?.trim() || defaultGroupName(name),
      scoreWeight: data.scoreWeight && data.scoreWeight > 0 ? data.scoreWeight : 1,
      createdAt: new Date().toISOString(),
    } as never)
    let order = 1
    for (const t of data.topics) {
      if (!t?.name?.trim()) continue
      const topicId = await db.topics.add({
        subjectId,
        name: t.name.trim(),
        description: t.description?.trim() || null,
        orderIndex: order++,
      } as never)
      const questions = (t.questions ?? []).filter(validQuestion)
      if (!questions.length) continue
      const qIds = await db.questions.bulkAdd(
        questions.map((q) => ({
          subjectId,
          topicId,
          text: q.text.trim(),
          explanation: q.explanation?.trim() || null,
          difficulty: q.difficulty ?? 'MEDIUM',
        })) as never[],
        { allKeys: true },
      )
      await db.options.bulkAdd(
        questions.flatMap((q, i) =>
          q.options.map((o, oi) => ({ questionId: qIds[i], text: o.text.trim(), correct: !!o.correct, orderIndex: oi })),
        ) as never[],
      )
    }
    return true
  })
}

let seeding: Promise<void> | null = null

/** Ilova ochilganda chaqiriladi: hali yuklanmagan fanlarni bazaga qo'shadi. */
export function ensureSeeded(onProgress?: (text: string) => void): Promise<void> {
  seeding ??= (async () => {
    const existing = new Set((await db.subjects.toArray()).map((s) => s.name))
    for (const load of Object.values(files)) {
      const data = await load()
      if (existing.has(data.subject?.trim())) continue
      onProgress?.(`«${data.subject}» fani tayyorlanmoqda...`)
      await importSubject(data)
    }
  })()
  return seeding
}
