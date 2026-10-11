import Dexie, { type EntityTable } from 'dexie'
import type { Difficulty, TestMode, TestStatus } from '@/types'

export interface DbSubject {
  id: number
  name: string
  description: string | null
  /** Blok test guruhi: "1-sinf Matematika" -> "Matematika" (seed fayldagi group yoki nomidan) */
  groupName?: string
  /** Guruhning 1 savol balli (seed fayldagi scoreWeight); guruhda eng kattasi olinadi */
  scoreWeight?: number
  createdAt: string
}

export interface DbTopic {
  id: number
  subjectId: number
  name: string
  description: string | null
  orderIndex: number
}

export interface DbQuestion {
  id: number
  subjectId: number
  topicId: number
  text: string
  explanation: string | null
  difficulty: Difficulty
}

export interface DbOption {
  id: number
  questionId: number
  text: string
  correct: boolean
  orderIndex: number
}

/** Blok testdagi fan guruhi: tanlangan sinflar, umumiy savollar soni va test boshlangan paytdagi ball */
export interface DbSessionGroup {
  groupId: number
  groupName: string
  subjectIds: number[]
  questionCount: number
  scoreWeight: number
}

export interface DbSession {
  id: number
  /** Blok testda null, fanlar – groups da */
  subjectId: number | null
  mode: TestMode
  status: TestStatus
  questionCount: number
  topicIds: number[]
  startedAt: string
  finishedAt: string | null
  correctCount: number | null
  scorePercent: number | null
  groups?: DbSessionGroup[]
  score?: number | null
  maxScore?: number | null
  /** Vaqt chegarasi (blok testda har bir fanga 1 soat) */
  timeLimitSeconds?: number | null
}

export interface DbSessionQuestion {
  id: number
  sessionId: number
  questionId: number
  orderIndex: number
  /** Aralashtirilgan variantlar tartibi – sahifa qayta ochilganda o'zgarmaydi */
  optionIds: number[]
  selectedOptionId: number | null
  correct: boolean | null
  answeredAt: string | null
}

export class AppDb extends Dexie {
  subjects!: EntityTable<DbSubject, 'id'>
  topics!: EntityTable<DbTopic, 'id'>
  questions!: EntityTable<DbQuestion, 'id'>
  options!: EntityTable<DbOption, 'id'>
  sessions!: EntityTable<DbSession, 'id'>
  sessionQuestions!: EntityTable<DbSessionQuestion, 'id'>

  constructor(name = 'test-tizimi') {
    super(name)
    this.version(1).stores({
      subjects: '++id, &name',
      topics: '++id, subjectId',
      questions: '++id, subjectId, topicId',
      options: '++id, questionId',
      sessions: '++id, subjectId',
      sessionQuestions: '++id, sessionId, questionId',
    })
    // Blok test: o'rnatilgan ilovadagi fanlarga guruh va standart ball (backenddagi V2, V4 migratsiyalar kabi)
    this.version(2).upgrade((tx) =>
      tx
        .table('subjects')
        .toCollection()
        .modify((s: DbSubject) => {
          s.groupName ??= defaultGroupName(s.name)
          s.scoreWeight ??= /biologiya/i.test(s.name) ? 3.1 : /matematika/i.test(s.name) ? 1.1 : 1
        }),
    )
  }
}

/** Fan nomidan standart guruh nomi: "1-sinf Matematika" -> "Matematika" (backenddagi qoida bilan bir xil). */
export function defaultGroupName(subjectName: string): string {
  return subjectName.trim().replace(/^\s*\d+\s*-\s*sinf\s+/i, '').trim() || subjectName.trim()
}

export let db = new AppDb()

/** Testlar uchun: alohida (bo'sh) baza bilan ishlash. */
export function useDb(instance: AppDb) {
  db = instance
}
