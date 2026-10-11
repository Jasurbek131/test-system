export type Role = 'ADMIN' | 'USER'
export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD'
export type TestMode = 'LEARNING' | 'EXAM' | 'BLOCK'
export type TestStatus = 'IN_PROGRESS' | 'FINISHED'

export interface User {
  id: number
  username: string
  fullName: string
  role: Role
}

export interface AuthResponse {
  token: string
  user: User
}

export interface ApiError {
  timestamp: string
  status: number
  message: string
  errors: { field: string; message: string }[]
}

export interface Page<T> {
  items: T[]
  page: number
  size: number
  total: number
}

export interface Subject {
  id: number
  name: string
  description: string | null
  active: boolean
  /** Blok test guruhi: "1-sinf Matematika" va "2-sinf Matematika" -> "Matematika" */
  groupId: number
  groupName: string
  /** Guruhning 1 ta to'g'ri javob uchun balli (blok test) */
  scoreWeight: number
  topicCount: number
  questionCount: number
  createdAt: string
}

export interface Topic {
  id: number
  subjectId: number
  name: string
  description: string | null
  orderIndex: number
  active: boolean
  questionCount: number
}

// ---------- test ----------
export interface OptionView {
  id: number
  text: string
}

export interface QuestionView {
  id: number
  orderIndex: number
  subjectId: number
  subjectName: string
  /** Blok testda savol qaysi fan bo'limiga tegishli (Matematika, Biologiya); oddiy testda null */
  groupId: number | null
  groupName: string | null
  topicId: number
  topicName: string
  text: string
  difficulty: Difficulty
  options: OptionView[]
  selectedOptionId: number | null
  answered: boolean
  correct: boolean | null
  correctOptionId: number | null
  explanation: string | null
}

/** Blok testdagi fan guruhi va undan tanlangan sinflar */
export interface SessionGroup {
  groupId: number
  groupName: string
  subjectNames: string[]
  questionCount: number
  scoreWeight: number
}

export interface SessionView {
  id: number
  /** Blok testda null, subjectName – fanlar nomi vergul bilan */
  subjectId: number | null
  subjectName: string
  mode: TestMode
  status: TestStatus
  questionCount: number
  startedAt: string
  finishedAt: string | null
  correctCount: number | null
  scorePercent: number | null
  /** Vaqt chegarasi (blok testda har bir fanga 1 soat), null – cheklanmagan */
  timeLimitSeconds: number | null
  /** Server bo'yicha qolgan vaqt */
  remainingSeconds: number | null
  topics: { id: number; name: string }[]
  groups: SessionGroup[]
  questions: QuestionView[]
}

export interface AnswerResponse {
  sessionQuestionId: number
  selectedOptionId: number
  answered: boolean
  correct: boolean | null
  correctOptionId: number | null
  explanation: string | null
}

export interface TopicResult {
  topicId: number
  topicName: string
  total: number
  correct: number
  wrong: number
  unanswered: number
  percent: number
}

export interface GroupResult {
  groupId: number
  groupName: string
  subjectNames: string[]
  scoreWeight: number
  total: number
  correct: number
  wrong: number
  unanswered: number
  percent: number
  score: number
  maxScore: number
}

export interface ResultView {
  id: number
  subjectId: number | null
  subjectName: string
  mode: TestMode
  questionCount: number
  correctCount: number
  wrongCount: number
  unansweredCount: number
  scorePercent: number
  /** score, maxScore, byGroup – faqat blok testda */
  score: number | null
  maxScore: number | null
  startedAt: string
  finishedAt: string | null
  durationSeconds: number
  timeLimitSeconds: number | null
  byGroup: GroupResult[]
  byTopic: TopicResult[]
  questions: QuestionView[]
}

export interface SessionSummary {
  id: number
  subjectId: number | null
  subjectName: string
  mode: TestMode
  status: TestStatus
  questionCount: number
  answeredCount: number
  correctCount: number | null
  scorePercent: number | null
  score: number | null
  maxScore: number | null
  startedAt: string
  finishedAt: string | null
  topicNames: string[]
  /** Blok testda: "Matematika (1-sinf, 2-sinf)" */
  groupLabels: string[]
}

export interface MyStats {
  totalTests: number
  finishedTests: number
  totalAnswered: number
  totalCorrect: number
  overallPercent: number
  averageScore: number
  bySubject: { subjectId: number; subjectName: string; tests: number; answered: number; correct: number; percent: number }[]
  byTopic: {
    subjectId: number
    subjectName: string
    topicId: number
    topicName: string
    answered: number
    correct: number
    percent: number
  }[]
}

// ---------- admin ----------
export interface OptionAdmin {
  id?: number | null
  text: string
  correct: boolean
}

export interface QuestionAdmin {
  id: number
  subjectId: number
  subjectName: string
  topicId: number
  topicName: string
  text: string
  explanation: string | null
  difficulty: Difficulty
  active: boolean
  createdAt: string
  options: OptionAdmin[]
}

export interface QuestionRequest {
  topicId: number
  text: string
  explanation: string | null
  difficulty: Difficulty
  active: boolean
  options: OptionAdmin[]
}

export interface ImportReport {
  subjectId: number
  subjectCreated: boolean
  topicsCreated: number
  questionsImported: number
  questionsSkipped: number
  errors: { path: string; message: string }[]
}
