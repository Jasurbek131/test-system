// Veb-versiyadagi '@/api' bilan bir xil nomlar va imzolar – lekin hammasi telefondagi lokal bazada bajariladi.
import type { TestMode } from '@/types'
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
  finish: (id: number) => svc.finish(id),
  result: (id: number) => svc.result(id),
  retryWrong: (id: number) => svc.retryWrong(id),
  history: (page: number, size: number) => svc.history(page, size),
  clearHistory: () => svc.clearHistory(),
}

export const statsApi = {
  me: () => svc.myStats(),
}
