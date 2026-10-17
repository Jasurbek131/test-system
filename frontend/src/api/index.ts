import { http } from './http'
import type {
  AnswerResponse,
  AuthResponse,
  Difficulty,
  ImportReport,
  Leaderboard,
  MistakeItem,
  MyStats,
  Page,
  ProgressOverview,
  QuestionAdmin,
  QuestionRequest,
  ResultView,
  ReviewKind,
  SessionSummary,
  SessionView,
  Subject,
  SubjectProgress,
  TestMode,
  Topic,
  User,
} from '@/types'

const data = <T>(p: Promise<{ data: T }>) => p.then((r) => r.data)

export const authApi = {
  login: (username: string, password: string) =>
    data(http.post<AuthResponse>('/auth/login', { username, password })),
  register: (body: { username: string; password: string; fullName: string }) =>
    data(http.post<AuthResponse>('/auth/register', body)),
  me: () => data(http.get<User>('/auth/me')),
}

export const subjectApi = {
  list: () => data(http.get<Subject[]>('/subjects')),
  get: (id: number) => data(http.get<Subject>(`/subjects/${id}`)),
  topics: (id: number) => data(http.get<Topic[]>(`/subjects/${id}/topics`)),
}

export const testApi = {
  create: (body: { subjectId: number; topicIds: number[]; questionCount: number; mode: TestMode }) =>
    data(http.post<{ id: number }>('/tests', body)),
  get: (id: number) => data(http.get<SessionView>(`/tests/${id}`)),
  answer: (id: number, sessionQuestionId: number, optionId: number) =>
    data(http.post<AnswerResponse>(`/tests/${id}/answers`, { sessionQuestionId, optionId })),
  /** Kartochka: «Bilardim» (true) / «Bilmasdim» (false) */
  rate: (id: number, sessionQuestionId: number, known: boolean) =>
    data(http.post<AnswerResponse>(`/tests/${id}/answers`, { sessionQuestionId, known })),
  createReview: (body: { subjectId: number; kind: ReviewKind; questionCount: number; mode: TestMode }) =>
    data(http.post<{ id: number }>('/tests/review', body)),
  finish: (id: number) => data(http.post<ResultView>(`/tests/${id}/finish`)),
  result: (id: number) => data(http.get<ResultView>(`/tests/${id}/result`)),
  createBlock: (groups: { groupId: number; subjectIds: number[]; questionCount: number }[]) =>
    data(http.post<{ id: number }>('/tests/block', { groups })),
  retryWrong: (id: number) => data(http.post<{ id: number }>(`/tests/${id}/retry-wrong`)),
  history: (page: number, size: number) =>
    data(http.get<Page<SessionSummary>>('/tests/history', { params: { page, size } })),
  clearHistory: () => http.delete('/tests/history').then(() => undefined),
}

export const statsApi = {
  me: () => data(http.get<MyStats>('/stats/me')),
}

/** Foydalanuvchi vaqt mintaqasi: kunlik faollik va streak shu bo'yicha hisoblanadi */
const tz = () => Intl.DateTimeFormat().resolvedOptions().timeZone

export const progressApi = {
  overview: () => data(http.get<ProgressOverview>('/progress/overview', { params: { tz: tz() } })),
  subject: (subjectId: number) => data(http.get<SubjectProgress>(`/progress/subjects/${subjectId}`)),
  mistakes: (subjectId?: number | null) =>
    data(http.get<MistakeItem[]>('/progress/mistakes', { params: subjectId ? { subjectId } : {} })),
  /** Oxirgi 7 kunlik XP reytingi (mobil ilovada yo'q) */
  leaderboard: () => data(http.get<Leaderboard>('/progress/leaderboard')),
}

export interface SubjectBody {
  name: string
  description: string | null
  active: boolean
  groupName: string | null
  scoreWeight: number
}

export interface TopicBody {
  subjectId: number
  name: string
  description: string | null
  orderIndex: number | null
  active: boolean
}

export interface QuestionFilter {
  subjectId?: number | null
  topicId?: number | null
  difficulty?: Difficulty | null
  active?: boolean | null
  q?: string
  page: number
  size: number
}

export const adminApi = {
  subjects: () => data(http.get<Subject[]>('/admin/subjects')),
  createSubject: (b: SubjectBody) => data(http.post<Subject>('/admin/subjects', b)),
  updateSubject: (id: number, b: SubjectBody) => data(http.put<Subject>(`/admin/subjects/${id}`, b)),
  deleteSubject: (id: number) => http.delete(`/admin/subjects/${id}`),

  topics: (subjectId: number) => data(http.get<Topic[]>('/admin/topics', { params: { subjectId } })),
  createTopic: (b: TopicBody) => data(http.post<Topic>('/admin/topics', b)),
  updateTopic: (id: number, b: TopicBody) => data(http.put<Topic>(`/admin/topics/${id}`, b)),
  deleteTopic: (id: number) => http.delete(`/admin/topics/${id}`),

  questions: (f: QuestionFilter) => {
    const params = Object.fromEntries(Object.entries(f).filter(([, v]) => v !== null && v !== undefined && v !== ''))
    return data(http.get<Page<QuestionAdmin>>('/admin/questions', { params }))
  },
  createQuestion: (b: QuestionRequest) => data(http.post<QuestionAdmin>('/admin/questions', b)),
  updateQuestion: (id: number, b: QuestionRequest) => data(http.put<QuestionAdmin>(`/admin/questions/${id}`, b)),
  deleteQuestion: (id: number) => http.delete(`/admin/questions/${id}`),

  importQuestions: (payload: unknown) => data(http.post<ImportReport>('/admin/import', payload)),
}
