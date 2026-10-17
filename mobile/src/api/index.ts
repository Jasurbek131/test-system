// Veb-versiyadagi '@/api' bilan bir xil nomlar va imzolar – lekin hammasi telefondagi lokal bazada bajariladi.
import type { Leaderboard, ReviewKind, TestMode } from '@/types'
import * as svc from './local/service'

export const subjectApi = {
  list: () => svc.listSubjects(),
  get: (id: number) => svc.getSubject(id),
  topics: (id: number) => svc.listTopics(id),
}

export const testApi = {
  create: (body: { subjectId: number; topicIds: number[]; questionCount: number; mode: TestMode }) => svc.createTest(body),
  get: (id: number) => svc.getSession(id),
  answer: (id: number, sessionQuestionId: number, optionId: number) => svc.answer(id, sessionQuestionId, optionId),
  rate: (id: number, sessionQuestionId: number, known: boolean) => svc.answer(id, sessionQuestionId, null, known),
  createReview: (body: { subjectId: number; kind: ReviewKind; questionCount: number; mode: TestMode }) => svc.createReview(body),
  finish: (id: number) => svc.finish(id),
  result: (id: number) => svc.result(id),
  createBlock: (groups: { groupId: number; subjectIds: number[]; questionCount: number }[]) => svc.createBlockTest(groups),
  retryWrong: (id: number) => svc.retryWrong(id),
  history: (page: number, size: number) => svc.history(page, size),
  clearHistory: () => svc.clearHistory(),
}

export const statsApi = {
  me: () => svc.myStats(),
}

export const progressApi = {
  overview: () => svc.progressOverview(),
  subject: (subjectId: number) => svc.subjectProgress(subjectId),
  mistakes: (subjectId?: number | null) => svc.mistakes(subjectId),
  /** Reyting – faqat veb-versiyada (oflayn ilovada boshqa foydalanuvchilar yo'q) */
  leaderboard: async (): Promise<Leaderboard> => ({ items: [], me: null }),
}
