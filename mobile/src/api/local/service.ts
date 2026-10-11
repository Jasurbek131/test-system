import type {
  AnswerResponse,
  MyStats,
  Page,
  QuestionView,
  ResultView,
  SessionSummary,
  SessionView,
  Subject,
  TestMode,
  Topic,
  TopicResult,
} from '@/types'
import { AppError } from '../errors'
import { db, type DbOption, type DbQuestion, type DbSession, type DbSessionQuestion } from './db'
import { percent, pickQuestions, shuffle } from './picker'

const MAX_QUESTIONS = 500

// ---------------------------------------------------------------- fanlar

function countBy<T>(items: T[], key: (t: T) => number): Map<number, number> {
  const m = new Map<number, number>()
  for (const it of items) m.set(key(it), (m.get(key(it)) ?? 0) + 1)
  return m
}

export async function listSubjects(): Promise<Subject[]> {
  const [subjects, topics] = await Promise.all([db.subjects.toArray(), db.topics.toArray()])
  const topicCount = countBy(topics, (t) => t.subjectId)
  const result: Subject[] = []
  for (const s of subjects) {
    result.push({
      id: s.id,
      name: s.name,
      description: s.description,
      active: true,
      topicCount: topicCount.get(s.id) ?? 0,
      questionCount: await db.questions.where('subjectId').equals(s.id).count(),
      createdAt: s.createdAt,
    })
  }
  return result.sort((a, b) => a.name.localeCompare(b.name))
}

export async function getSubject(id: number): Promise<Subject> {
  const s = (await listSubjects()).find((x) => x.id === id)
  if (!s) throw new AppError('Fan topilmadi')
  return s
}

export async function listTopics(subjectId: number): Promise<Topic[]> {
  await getSubject(subjectId)
  const topics = await db.topics.where('subjectId').equals(subjectId).toArray()
  const questions = await db.questions.where('subjectId').equals(subjectId).toArray()
  const counts = countBy(questions, (q) => q.topicId)
  return topics
    .sort((a, b) => a.orderIndex - b.orderIndex || a.name.localeCompare(b.name))
    .map((t) => ({ ...t, active: true, questionCount: counts.get(t.id) ?? 0 }))
}

// ---------------------------------------------------------------- test yaratish

async function newSession(subjectId: number, mode: TestMode, topicIds: number[], questionIds: number[]): Promise<number> {
  const options = await db.options.where('questionId').anyOf(questionIds).toArray()
  const byQuestion = new Map<number, DbOption[]>()
  for (const o of options) (byQuestion.get(o.questionId) ?? byQuestion.set(o.questionId, []).get(o.questionId)!).push(o)

  return db.transaction('rw', [db.sessions, db.sessionQuestions], async () => {
    const sessionId = await db.sessions.add({
      subjectId,
      mode,
      status: 'IN_PROGRESS',
      questionCount: questionIds.length,
      topicIds,
      startedAt: new Date().toISOString(),
      finishedAt: null,
      correctCount: null,
      scorePercent: null,
    } as never)
    await db.sessionQuestions.bulkAdd(
      questionIds.map((questionId, orderIndex) => ({
        sessionId,
        questionId,
        orderIndex,
        optionIds: shuffle((byQuestion.get(questionId) ?? []).map((o) => o.id)),
        selectedOptionId: null,
        correct: null,
        answeredAt: null,
      })) as never[],
    )
    return sessionId
  })
}

export async function createTest(req: { subjectId: number; topicIds: number[]; questionCount: number; mode: TestMode }) {
  await getSubject(req.subjectId)
  const topicIds = [...new Set(req.topicIds)]
  if (!topicIds.length) throw new AppError('Kamida bitta mavzu tanlanishi shart')
  if (!Number.isInteger(req.questionCount) || req.questionCount < 1) throw new AppError("Savollar soni kamida 1 ta bo'lishi kerak")
  if (req.questionCount > MAX_QUESTIONS) throw new AppError(`Savollar soni ${MAX_QUESTIONS} tadan oshmasligi kerak`)

  const topics = await db.topics.bulkGet(topicIds)
  if (topics.some((t) => !t)) throw new AppError('Ba\'zi mavzular topilmadi')
  if (topics.some((t) => t!.subjectId !== req.subjectId)) throw new AppError("Mavzular faqat tanlangan fanga tegishli bo'lishi kerak")

  const questions = await db.questions.where('topicId').anyOf(topicIds).toArray()
  if (!questions.length) throw new AppError("Tanlangan mavzularda savollar yo'q")
  if (req.questionCount > questions.length) throw new AppError(`Tanlangan mavzularda faqat ${questions.length} ta savol bor`)

  const idsByTopic = new Map<number, number[]>()
  for (const q of questions) (idsByTopic.get(q.topicId) ?? idsByTopic.set(q.topicId, []).get(q.topicId)!).push(q.id)
  const picked = pickQuestions(idsByTopic, req.questionCount)
  return { id: await newSession(req.subjectId, req.mode ?? 'LEARNING', topicIds, picked) }
}

/** Yakunlangan testdagi xato va javobsiz savollardan yangi test. */
export async function retryWrong(sessionId: number) {
  const s = await findSession(sessionId)
  if (s.status !== 'FINISHED') throw new AppError('Avval testni yakunlang')
  const sqs = await db.sessionQuestions.where('sessionId').equals(sessionId).sortBy('orderIndex')
  const wrongIds = sqs.filter((sq) => sq.correct !== true).map((sq) => sq.questionId)
  const existing = (await db.questions.bulkGet(wrongIds)).filter((q): q is DbQuestion => !!q)
  if (!existing.length) throw new AppError("Bu testda xato javoblar yo'q")
  const topicIds = [...new Set(existing.map((q) => q.topicId))]
  return { id: await newSession(s.subjectId, s.mode, topicIds, shuffle(existing.map((q) => q.id))) }
}

// ---------------------------------------------------------------- yechish

async function findSession(id: number): Promise<DbSession> {
  const s = await db.sessions.get(id)
  if (!s) throw new AppError('Test topilmadi')
  return s
}

interface Loaded {
  session: DbSession
  sqs: DbSessionQuestion[]
  questions: Map<number, DbQuestion>
  options: Map<number, DbOption[]>
  topicNames: Map<number, string>
  subjectName: string
}

async function load(sessionId: number): Promise<Loaded> {
  const session = await findSession(sessionId)
  const sqs = await db.sessionQuestions.where('sessionId').equals(sessionId).sortBy('orderIndex')
  const qIds = sqs.map((sq) => sq.questionId)
  const [qs, opts, topics, subject] = await Promise.all([
    db.questions.bulkGet(qIds),
    db.options.where('questionId').anyOf(qIds).toArray(),
    db.topics.where('subjectId').equals(session.subjectId).toArray(),
    db.subjects.get(session.subjectId),
  ])
  const questions = new Map(qs.filter((q): q is DbQuestion => !!q).map((q) => [q.id, q]))
  const options = new Map<number, DbOption[]>()
  for (const o of opts) (options.get(o.questionId) ?? options.set(o.questionId, []).get(o.questionId)!).push(o)
  return { session, sqs, questions, options, topicNames: new Map(topics.map((t) => [t.id, t.name])), subjectName: subject?.name ?? '' }
}

function canReveal(s: DbSession, sq: DbSessionQuestion): boolean {
  return s.status === 'FINISHED' || (s.mode === 'LEARNING' && sq.selectedOptionId !== null)
}

function correctOptionId(sq: DbSessionQuestion, options: DbOption[]): number | null {
  return options.find((o) => o.correct && sq.optionIds.includes(o.id))?.id ?? null
}

function toView(l: Loaded, sq: DbSessionQuestion, reveal: boolean): QuestionView {
  const q = l.questions.get(sq.questionId)!
  const opts = l.options.get(sq.questionId) ?? []
  const byId = new Map(opts.map((o) => [o.id, o]))
  return {
    id: sq.id,
    orderIndex: sq.orderIndex,
    topicId: q.topicId,
    topicName: l.topicNames.get(q.topicId) ?? '',
    text: q.text,
    difficulty: q.difficulty,
    options: sq.optionIds.map((id) => byId.get(id)).filter((o): o is DbOption => !!o).map((o) => ({ id: o.id, text: o.text })),
    selectedOptionId: sq.selectedOptionId,
    answered: sq.selectedOptionId !== null,
    correct: reveal ? sq.correct : null,
    correctOptionId: reveal ? correctOptionId(sq, opts) : null,
    explanation: reveal ? q.explanation : null,
  }
}

export async function getSession(id: number): Promise<SessionView> {
  const l = await load(id)
  const s = l.session
  return {
    id: s.id,
    subjectId: s.subjectId,
    subjectName: l.subjectName,
    mode: s.mode,
    status: s.status,
    questionCount: s.questionCount,
    startedAt: s.startedAt,
    finishedAt: s.finishedAt,
    correctCount: s.correctCount,
    scorePercent: s.scorePercent,
    topics: s.topicIds.map((tid) => ({ id: tid, name: l.topicNames.get(tid) ?? '' })),
    questions: l.sqs.filter((sq) => l.questions.has(sq.questionId)).map((sq) => toView(l, sq, canReveal(s, sq))),
  }
}

export async function answer(sessionId: number, sessionQuestionId: number, optionId: number): Promise<AnswerResponse> {
  return db.transaction('rw', [db.sessions, db.sessionQuestions, db.options, db.questions], async () => {
    const s = await findSession(sessionId)
    if (s.status === 'FINISHED') throw new AppError('Test yakunlangan')
    const sq = await db.sessionQuestions.get(sessionQuestionId)
    if (!sq || sq.sessionId !== sessionId) throw new AppError('Savol bu testga tegishli emas')
    if (s.mode === 'LEARNING' && sq.selectedOptionId !== null) throw new AppError('Bu savolga javob berilgan')
    if (!sq.optionIds.includes(optionId)) throw new AppError('Variant bu savolga tegishli emas')
    const option = await db.options.get(optionId)
    if (!option) throw new AppError('Variant topilmadi')

    const updated: DbSessionQuestion = { ...sq, selectedOptionId: optionId, correct: option.correct, answeredAt: new Date().toISOString() }
    await db.sessionQuestions.put(updated)
    const reveal = canReveal(s, updated)
    const options = await db.options.where('questionId').equals(sq.questionId).toArray()
    const question = await db.questions.get(sq.questionId)
    return {
      sessionQuestionId: sq.id,
      selectedOptionId: optionId,
      answered: true,
      correct: reveal ? updated.correct : null,
      correctOptionId: reveal ? correctOptionId(updated, options) : null,
      explanation: reveal ? (question?.explanation ?? null) : null,
    }
  })
}

function buildResult(l: Loaded): ResultView {
  const s = l.session
  const sqs = l.sqs.filter((sq) => l.questions.has(sq.questionId))
  const correct = sqs.filter((sq) => sq.correct === true).length
  const unanswered = sqs.filter((sq) => sq.selectedOptionId === null).length

  const groups = new Map<number, DbSessionQuestion[]>()
  for (const sq of sqs) {
    const tid = l.questions.get(sq.questionId)!.topicId
    ;(groups.get(tid) ?? groups.set(tid, []).get(tid)!).push(sq)
  }
  const byTopic: TopicResult[] = [...groups.entries()]
    .map(([topicId, list]) => {
      const c = list.filter((sq) => sq.correct === true).length
      const u = list.filter((sq) => sq.selectedOptionId === null).length
      return { topicId, topicName: l.topicNames.get(topicId) ?? '', total: list.length, correct: c, wrong: list.length - c - u, unanswered: u, percent: percent(c, list.length) }
    })
    .sort((a, b) => a.percent - b.percent || a.topicName.localeCompare(b.topicName))

  const end = s.finishedAt ? new Date(s.finishedAt) : new Date()
  return {
    id: s.id,
    subjectId: s.subjectId,
    subjectName: l.subjectName,
    mode: s.mode,
    questionCount: s.questionCount,
    correctCount: correct,
    wrongCount: sqs.length - correct - unanswered,
    unansweredCount: unanswered,
    scorePercent: percent(correct, s.questionCount),
    startedAt: s.startedAt,
    finishedAt: s.finishedAt,
    durationSeconds: Math.max(0, Math.round((end.getTime() - new Date(s.startedAt).getTime()) / 1000)),
    byTopic,
    questions: sqs.map((sq) => toView(l, sq, true)),
  }
}

export async function finish(sessionId: number): Promise<ResultView> {
  const s = await findSession(sessionId)
  if (s.status !== 'FINISHED') {
    const sqs = await db.sessionQuestions.where('sessionId').equals(sessionId).toArray()
    const correct = sqs.filter((sq) => sq.correct === true).length
    await db.sessions.update(sessionId, {
      status: 'FINISHED',
      finishedAt: new Date().toISOString(),
      correctCount: correct,
      scorePercent: percent(correct, s.questionCount),
    })
  }
  return buildResult(await load(sessionId))
}

export async function result(sessionId: number): Promise<ResultView> {
  const l = await load(sessionId)
  if (l.session.status !== 'FINISHED') throw new AppError('Test hali yakunlanmagan')
  return buildResult(l)
}

export async function history(page: number, size: number): Promise<Page<SessionSummary>> {
  const all = (await db.sessions.toArray()).sort((a, b) => b.id - a.id)
  const p = Math.max(page, 0)
  const sz = Math.min(Math.max(size, 1), 100)
  const slice = all.slice(p * sz, p * sz + sz)
  const [subjects, topics] = await Promise.all([db.subjects.toArray(), db.topics.toArray()])
  const subjectName = new Map(subjects.map((s) => [s.id, s.name]))
  const topicName = new Map(topics.map((t) => [t.id, t.name]))
  const items: SessionSummary[] = []
  for (const s of slice) {
    const answered = (await db.sessionQuestions.where('sessionId').equals(s.id).toArray()).filter((sq) => sq.selectedOptionId !== null).length
    items.push({
      id: s.id,
      subjectId: s.subjectId,
      subjectName: subjectName.get(s.subjectId) ?? '',
      mode: s.mode,
      status: s.status,
      questionCount: s.questionCount,
      answeredCount: answered,
      correctCount: s.correctCount,
      scorePercent: s.scorePercent,
      startedAt: s.startedAt,
      finishedAt: s.finishedAt,
      topicNames: s.topicIds.map((id) => topicName.get(id) ?? ''),
    })
  }
  return { items, page: p, size: sz, total: all.length }
}

/** Tarixni tozalash: faqat testlar o'chadi, fan/mavzu/savollarga tegilmaydi (backenddagi clearHistory kabi). */
export async function clearHistory(): Promise<void> {
  await db.transaction('rw', [db.sessions, db.sessionQuestions], async () => {
    await db.sessionQuestions.clear()
    await db.sessions.clear()
  })
}

// ---------------------------------------------------------------- statistika

/**
 * Backend bilan bir xil qoida: yakunlangan testlar va o'rganish rejimidagi javoblar hisoblanadi,
 * davom etayotgan imtihondagi javoblar hisoblanmaydi.
 */
export async function myStats(): Promise<MyStats> {
  const [sessions, sqs, questions, topics, subjects] = await Promise.all([
    db.sessions.toArray(),
    db.sessionQuestions.toArray(),
    db.questions.toArray(),
    db.topics.toArray(),
    db.subjects.toArray(),
  ])
  const sessionById = new Map(sessions.map((s) => [s.id, s]))
  const questionById = new Map(questions.map((q) => [q.id, q]))
  const topicById = new Map(topics.map((t) => [t.id, t]))
  const subjectById = new Map(subjects.map((s) => [s.id, s]))

  const counted = sqs.filter((sq) => {
    const s = sessionById.get(sq.sessionId)
    return sq.selectedOptionId !== null && !!s && (s.status === 'FINISHED' || s.mode === 'LEARNING') && questionById.has(sq.questionId)
  })
  const finished = sessions.filter((s) => s.status === 'FINISHED')
  const avg = finished.length ? finished.reduce((sum, s) => sum + (s.scorePercent ?? 0), 0) / finished.length : 0

  const subj = new Map<number, { answered: number; correct: number }>()
  const top = new Map<number, { answered: number; correct: number }>()
  for (const sq of counted) {
    const q = questionById.get(sq.questionId)!
    for (const [m, key] of [[subj, q.subjectId], [top, q.topicId]] as const) {
      const e = m.get(key) ?? { answered: 0, correct: 0 }
      e.answered++
      if (sq.correct) e.correct++
      m.set(key, e)
    }
  }
  const testsBySubject = countBy(sessions, (s) => s.subjectId)
  const totalCorrect = counted.filter((sq) => sq.correct).length

  return {
    totalTests: sessions.length,
    finishedTests: finished.length,
    totalAnswered: counted.length,
    totalCorrect,
    overallPercent: percent(totalCorrect, counted.length),
    averageScore: Math.round(avg * 10) / 10,
    bySubject: [...subj.entries()]
      .map(([id, e]) => ({ subjectId: id, subjectName: subjectById.get(id)?.name ?? '', tests: testsBySubject.get(id) ?? 0, ...e, percent: percent(e.correct, e.answered) }))
      .sort((a, b) => a.subjectName.localeCompare(b.subjectName)),
    byTopic: [...top.entries()]
      .map(([id, e]) => {
        const t = topicById.get(id)
        return { subjectId: t?.subjectId ?? 0, subjectName: subjectById.get(t?.subjectId ?? 0)?.name ?? '', topicId: id, topicName: t?.name ?? '', ...e, percent: percent(e.correct, e.answered), order: t?.orderIndex ?? 0 }
      })
      .sort((a, b) => a.subjectName.localeCompare(b.subjectName) || a.order - b.order)
      .map(({ order: _order, ...rest }) => rest),
  }
}
