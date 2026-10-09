import Dexie, { type EntityTable } from 'dexie'
import type { Difficulty, TestMode, TestStatus } from '@/types'

export interface DbSubject {
  id: number
  name: string
  description: string | null
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

export interface DbSession {
  id: number
  subjectId: number
  mode: TestMode
  status: TestStatus
  questionCount: number
  topicIds: number[]
  startedAt: string
  finishedAt: string | null
  correctCount: number | null
  scorePercent: number | null
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
  }
}

export let db = new AppDb()

/** Testlar uchun: alohida (bo'sh) baza bilan ishlash. */
export function useDb(instance: AppDb) {
  db = instance
}
