import type {
  AnswerResponse,
  MyStats,
  Page,
  QuestionView,
  ResultView,
  SessionSummary,
  SessionView,
  GroupResult,
  Subject,
  TestMode,
  Topic,
  TopicResult,
} from '@/types'
import { AppError } from '../errors'
import { db, defaultGroupName, type DbOption, type DbQuestion, type DbSession, type DbSessionGroup, type DbSessionQuestion, type DbSubject } from './db'
import { allocate, percent, pickQuestions, shuffle } from './picker'

const MAX_QUESTIONS = 500
/** Blok testda har bir fan (guruh) ga 1 soat (backenddagi BLOCK_SECONDS_PER_SUBJECT kabi) */
const BLOCK_SECONDS_PER_SUBJECT = 3600

// ---------------------------------------------------------------- fanlar

function countBy<T>(items: T[], key: (t: T) => number): Map<number, number> {
  const m = new Map<number, number>()
  for (const it of items) m.set(key(it), (m.get(key(it)) ?? 0) + 1)
  return m
}

interface GroupInfo {
  groupId: number
  groupName: string
  scoreWeight: number
}

/**
 * Fan guruhlari (backenddagi subject_groups o'rnida): guruh id – undagi eng kichik fan id,
 * ball – guruhdagi fanlar seed ballaridan eng kattasi (masalan faqat 1-sinf faylida 1.1 berilgan bo'lsa ham).
 */
function subjectGroups(subjects: DbSubject[]): Map<number, GroupInfo> {
  const byKey = new Map<string, DbSubject[]>()
  for (const s of subjects) {
    const key = (s.groupName ?? defaultGroupName(s.name)).toLowerCase()
    byKey.set(key, [...(byKey.get(key) ?? []), s])
  }
  const result = new Map<number, GroupInfo>()
  for (const list of byKey.values()) {
    const first = list.reduce((a, b) => (a.id < b.id ? a : b))
    const info = {
      groupId: first.id,
      groupName: first.groupName ?? defaultGroupName(first.name),
      scoreWeight: Math.max(...list.map((s) => s.scoreWeight ?? 1)),
    }
    for (const s of list) result.set(s.id, info)
  }
  return result
}

export async function listSubjects(): Promise<Subject[]> {
  const [subjects, topics] = await Promise.all([db.subjects.toArray(), db.topics.toArray()])
  const topicCount = countBy(topics, (t) => t.subjectId)
  const groups = subjectGroups(subjects)
  const result: Subject[] = []
  for (const s of subjects) {
    const g = groups.get(s.id)!
    result.push({
      id: s.id,
      name: s.name,
      description: s.description,
      active: true,
      groupId: g.groupId,
      groupName: g.groupName,
      scoreWeight: g.scoreWeight,
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

async function newSession(
  subjectId: number | null,
  mode: TestMode,
  topicIds: number[],
  questionIds: number[],
  groups?: DbSessionGroup[],
): Promise<number> {
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
      ...(groups ? { groups, score: null, maxScore: null, timeLimitSeconds: groups.length * BLOCK_SECONDS_PER_SUBJECT } : {}),
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
  if (req.mode === 'BLOCK') throw new AppError('Blok test uchun fanlarni alohida tanlang')
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

/**
 * Blok test (DTM uslubida): har bir fan guruhidan (masalan Matematika) tanlangan sinflar bo'yicha umumiy sondagi
 * savol olinadi – avval sinflar orasida, keyin sinf ichida mavzular bo'yicha teng. Hammasi aralashtiriladi.
 * Guruhning 1 savol balli shu paytdagi qiymati bilan saqlanadi (backenddagi createBlock kabi).
 */
export async function createBlockTest(groups: { groupId: number; subjectIds: number[]; questionCount: number }[]) {
  if (!groups.length) throw new AppError('Kamida bitta fan tanlanishi shart')
  if (new Set(groups.map((x) => x.groupId)).size !== groups.length) throw new AppError('Fanlar takrorlanmasligi kerak')
  for (const x of groups) {
    if (!x.subjectIds.length) throw new AppError('Kamida bitta sinf tanlanishi shart')
    if (!Number.isInteger(x.questionCount) || x.questionCount < 1) throw new AppError("Savollar soni kamida 1 ta bo'lishi kerak")
  }
  if (groups.reduce((sum, x) => sum + x.questionCount, 0) > MAX_QUESTIONS) {
    throw new AppError(`Bitta testda savollar soni ${MAX_QUESTIONS} tadan oshmasligi kerak`)
  }

  const subjects = await db.subjects.toArray()
  const info = subjectGroups(subjects)
  const picked: number[] = []
  const sessionGroups: DbSessionGroup[] = []
  for (const x of groups) {
    const group = [...info.values()].find((g) => g.groupId === x.groupId)
    if (!group) throw new AppError('Fan topilmadi')
    const subjectIds = [...new Set(x.subjectIds)]
    const idsBySubject = new Map<number, Map<number, number[]>>()
    for (const sid of subjectIds) {
      const subject = subjects.find((s) => s.id === sid)
      if (!subject) throw new AppError('Fan topilmadi')
      if (info.get(sid)!.groupId !== group.groupId) throw new AppError(`${subject.name} – ${group.groupName} faniga tegishli emas`)
      const idsByTopic = new Map<number, number[]>()
      for (const q of await db.questions.where('subjectId').equals(sid).toArray()) {
        ;(idsByTopic.get(q.topicId) ?? idsByTopic.set(q.topicId, []).get(q.topicId)!).push(q.id)
      }
      idsBySubject.set(sid, idsByTopic)
    }
    const available = new Map([...idsBySubject].map(([sid, m]) => [sid, [...m.values()].reduce((n, ids) => n + ids.length, 0)]))
    const availableTotal = [...available.values()].reduce((a, b) => a + b, 0)
    if (x.questionCount > availableTotal) {
      throw new AppError(`${group.groupName}: tanlangan sinflarda faqat ${availableTotal} ta savol bor`)
    }
    // Har bir fan alohida bo'limda (backenddagi kabi), sinflar savollari bo'lim ichida aralash
    const section: number[] = []
    for (const [sid, n] of allocate(available, x.questionCount)) {
      if (n > 0) section.push(...pickQuestions(idsBySubject.get(sid)!, n))
    }
    picked.push(...shuffle(section))
    sessionGroups.push({ ...group, subjectIds, questionCount: x.questionCount })
  }
  return { id: await newSession(null, 'BLOCK', [], picked, sessionGroups) }
}

/** Yakunlangan testdagi xato va javobsiz savollardan yangi test. */
export async function retryWrong(sessionId: number) {
  const s = await findSession(sessionId)
  if (s.status !== 'FINISHED') throw new AppError('Avval testni yakunlang')
  const sqs = await db.sessionQuestions.where('sessionId').equals(sessionId).sortBy('orderIndex')
  const wrongIds = sqs.filter((sq) => sq.correct !== true).map((sq) => sq.questionId)
  const existing = (await db.questions.bulkGet(wrongIds)).filter((q): q is DbQuestion => !!q)
  if (!existing.length) throw new AppError("Bu testda xato javoblar yo'q")
  const ids = shuffle(existing.map((q) => q.id))
  if (s.mode === 'BLOCK') {
    // Guruhlar tartibi va ballari eski testdagidek, savollar soni – faqat xatolar
    const wrongSubjects = new Set(existing.map((q) => q.subjectId))
    const groups = (s.groups ?? [])
      .map((g) => ({
        ...g,
        subjectIds: g.subjectIds.filter((id) => wrongSubjects.has(id)),
        questionCount: existing.filter((q) => g.subjectIds.includes(q.subjectId)).length,
      }))
      .filter((g) => g.questionCount > 0)
    // savollar fanlar bo'limlari tartibida, bo'lim ichida aralash
    const subjectOf = new Map(existing.map((q) => [q.id, q.subjectId]))
    const sectionOf = (id: number) => groups.findIndex((g) => g.subjectIds.includes(subjectOf.get(id)!))
    ids.sort((a, b) => sectionOf(a) - sectionOf(b))
    return { id: await newSession(null, 'BLOCK', [], ids, groups) }
  }
  const topicIds = [...new Set(existing.map((q) => q.topicId))]
  return { id: await newSession(s.subjectId, s.mode, topicIds, ids) }
}

// ---------------------------------------------------------------- yechish

async function findSession(id: number): Promise<DbSession> {
  const s = await db.sessions.get(id)
  if (!s) throw new AppError('Test topilmadi')
  return s
}

function deadline(s: DbSession): number | null {
  return s.timeLimitSeconds ? new Date(s.startedAt).getTime() + s.timeLimitSeconds * 1000 : null
}

function isExpired(s: DbSession, now = Date.now()): boolean {
  const d = deadline(s)
  return s.status !== 'FINISHED' && d !== null && now >= d
}

/** Vaqti tugagan testni yakunlaydi, keyin qayta o'qiydi. */
async function findFresh(id: number): Promise<DbSession> {
  const s = await findSession(id)
  if (!isExpired(s)) return s
  await finish(id)
  return findSession(id)
}

/** Vaqti tugagan barcha testlarni yakunlaydi (tarix va statistika uchun). */
async function finishExpired(): Promise<void> {
  const now = Date.now()
  for (const s of await db.sessions.toArray()) if (isExpired(s, now)) await finish(s.id)
}

interface Loaded {
  session: DbSession
  sqs: DbSessionQuestion[]
  questions: Map<number, DbQuestion>
  options: Map<number, DbOption[]>
  topicNames: Map<number, string>
  subjectNames: Map<number, string>
  subjectName: string
}

/** "Matematika (1-sinf, 2-sinf)"; guruhda fan o'zi bitta va nomi bir xil bo'lsa – faqat guruh nomi (backenddagi kabi). */
function groupLabel(g: DbSessionGroup, names: Map<number, string>): string {
  const parts = g.subjectIds
    .map((id) => names.get(id) ?? '')
    .map((n) => n.replace(new RegExp(g.groupName.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'i'), '').trim() || n)
    .filter((p) => p && p.toLowerCase() !== g.groupName.toLowerCase())
  return parts.length ? `${g.groupName} (${parts.join(', ')})` : g.groupName
}

/** Blok testda – guruhlar vergul bilan: "Matematika (1-sinf, 2-sinf), Biologiya (5-sinf)". */
function sessionSubjectName(s: DbSession, names: Map<number, string>): string {
  if (s.subjectId !== null) return names.get(s.subjectId) ?? ''
  return (s.groups ?? []).map((g) => groupLabel(g, names)).join(', ')
}

async function load(sessionId: number): Promise<Loaded> {
  const session = await findFresh(sessionId)
  const sqs = await db.sessionQuestions.where('sessionId').equals(sessionId).sortBy('orderIndex')
  const qIds = sqs.map((sq) => sq.questionId)
  const [qs, opts, subjects] = await Promise.all([
    db.questions.bulkGet(qIds),
    db.options.where('questionId').anyOf(qIds).toArray(),
    db.subjects.toArray(),
  ])
  const questions = new Map(qs.filter((q): q is DbQuestion => !!q).map((q) => [q.id, q]))
  const options = new Map<number, DbOption[]>()
  for (const o of opts) (options.get(o.questionId) ?? options.set(o.questionId, []).get(o.questionId)!).push(o)
  const topicIds = [...new Set([...session.topicIds, ...[...questions.values()].map((q) => q.topicId)])]
  const topics = (await db.topics.bulkGet(topicIds)).filter((t) => !!t)
  const subjectNames = new Map(subjects.map((x) => [x.id, x.name]))
  return {
    session,
    sqs,
    questions,
    options,
    topicNames: new Map(topics.map((t) => [t.id, t.name])),
    subjectNames,
    subjectName: sessionSubjectName(session, subjectNames),
  }
}

function canReveal(s: DbSession, sq: DbSessionQuestion): boolean {
  return s.status === 'FINISHED' || (s.mode === 'LEARNING' && sq.selectedOptionId !== null)
}

function correctOptionId(sq: DbSessionQuestion, options: DbOption[]): number | null {
  return options.find((o) => o.correct && sq.optionIds.includes(o.id))?.id ?? null
}

function toView(l: Loaded, sq: DbSessionQuestion, reveal: boolean): QuestionView {
  const group = l.session.groups?.find((g) => g.subjectIds.includes(l.questions.get(sq.questionId)!.subjectId))
  const q = l.questions.get(sq.questionId)!
  const opts = l.options.get(sq.questionId) ?? []
  const byId = new Map(opts.map((o) => [o.id, o]))
  return {
    id: sq.id,
    orderIndex: sq.orderIndex,
    subjectId: q.subjectId,
    subjectName: l.subjectNames.get(q.subjectId) ?? '',
    // Blok testda savol qaysi fan bo'limiga tegishli
    groupId: group?.groupId ?? null,
    groupName: group?.groupName ?? null,
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
  const d = deadline(s)
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
    timeLimitSeconds: s.timeLimitSeconds ?? null,
    remainingSeconds: d === null ? null : s.status === 'FINISHED' ? 0 : Math.max(0, Math.floor((d - Date.now()) / 1000)),
    topics: s.topicIds.map((tid) => ({ id: tid, name: l.topicNames.get(tid) ?? '' })),
    groups: (s.groups ?? []).map((g) => ({
      groupId: g.groupId,
      groupName: g.groupName,
      subjectNames: g.subjectIds.map((id) => l.subjectNames.get(id) ?? ''),
      questionCount: g.questionCount,
      scoreWeight: g.scoreWeight,
    })),
    questions: l.sqs.filter((sq) => l.questions.has(sq.questionId)).map((sq) => toView(l, sq, canReveal(s, sq))),
  }
}

export async function answer(sessionId: number, sessionQuestionId: number, optionId: number): Promise<AnswerResponse> {
  // Tranzaksiyadan oldin: xato tranzaksiyani bekor qilgani uchun yakunlash ichkarida saqlanmay qolardi
  if (isExpired(await findSession(sessionId))) {
    await finish(sessionId)
    throw new AppError('Vaqt tugadi, test yakunlandi')
  }
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

const round1 = (n: number) => Math.round(n * 10) / 10

/** Blok test: fan guruhlari bo'yicha natija va ball (score = to'g'ri × 1 savol balli). */
function groupResults(s: DbSession, sqs: DbSessionQuestion[], questions: Map<number, DbQuestion>, names: Map<number, string>): GroupResult[] {
  return (s.groups ?? []).map((x) => {
    const list = sqs.filter((sq) => x.subjectIds.includes(questions.get(sq.questionId)?.subjectId ?? -1))
    const c = list.filter((sq) => sq.correct === true).length
    const u = list.filter((sq) => sq.selectedOptionId === null).length
    return {
      groupId: x.groupId,
      groupName: x.groupName,
      subjectNames: x.subjectIds.map((id) => names.get(id) ?? ''),
      scoreWeight: x.scoreWeight,
      total: list.length,
      correct: c,
      wrong: list.length - c - u,
      unanswered: u,
      percent: percent(c, list.length),
      score: round1(c * x.scoreWeight),
      maxScore: round1(list.length * x.scoreWeight),
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
    scorePercent: s.scorePercent ?? percent(correct, s.questionCount),
    score: s.score ?? null,
    maxScore: s.maxScore ?? null,
    startedAt: s.startedAt,
    finishedAt: s.finishedAt,
    durationSeconds: Math.max(0, Math.round((end.getTime() - new Date(s.startedAt).getTime()) / 1000)),
    timeLimitSeconds: s.timeLimitSeconds ?? null,
    byGroup: s.mode === 'BLOCK' ? groupResults(s, sqs, l.questions, l.subjectNames) : [],
    byTopic,
    questions: sqs.map((sq) => toView(l, sq, true)),
  }
}

export async function finish(sessionId: number): Promise<ResultView> {
  const s = await findSession(sessionId)
  if (s.status !== 'FINISHED') {
    const sqs = await db.sessionQuestions.where('sessionId').equals(sessionId).toArray()
    const correct = sqs.filter((sq) => sq.correct === true).length
    // Vaqti tugagan bo'lsa yakunlash vaqti – muddat (keyinroq ochilsa ham sarflangan vaqt oshib ketmaydi)
    const d = deadline(s)
    const changes: Partial<DbSession> = {
      status: 'FINISHED',
      finishedAt: new Date(d !== null && Date.now() > d ? d : Date.now()).toISOString(),
      correctCount: correct,
      scorePercent: percent(correct, s.questionCount),
    }
    if (s.mode === 'BLOCK') {
      const questions = new Map((await db.questions.bulkGet(sqs.map((sq) => sq.questionId))).filter((q) => !!q).map((q) => [q.id, q]))
      const results = groupResults(s, sqs.filter((sq) => questions.has(sq.questionId)), questions, new Map())
      changes.score = round1(results.reduce((sum, r) => sum + r.score, 0))
      changes.maxScore = round1(results.reduce((sum, r) => sum + r.maxScore, 0))
      changes.scorePercent = percent(changes.score, changes.maxScore)
    }
    await db.sessions.update(sessionId, changes)
  }
  return buildResult(await load(sessionId))
}

export async function result(sessionId: number): Promise<ResultView> {
  const l = await load(sessionId)
  if (l.session.status !== 'FINISHED') throw new AppError('Test hali yakunlanmagan')
  return buildResult(l)
}

export async function history(page: number, size: number): Promise<Page<SessionSummary>> {
  await finishExpired()
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
      subjectName: sessionSubjectName(s, subjectName),
      mode: s.mode,
      status: s.status,
      questionCount: s.questionCount,
      answeredCount: answered,
      correctCount: s.correctCount,
      scorePercent: s.scorePercent,
      score: s.score ?? null,
      maxScore: s.maxScore ?? null,
      startedAt: s.startedAt,
      finishedAt: s.finishedAt,
      topicNames: s.topicIds.map((id) => topicName.get(id) ?? ''),
      groupLabels: (s.groups ?? []).map((g) => groupLabel(g, subjectName)),
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
  await finishExpired()
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
  // Oddiy testda fan subjectId da, blok testda – groups ichidagi sinflarda
  const testsBySubject = countBy(
    sessions.flatMap((s) => (s.subjectId !== null ? [s.subjectId] : (s.groups ?? []).flatMap((g) => g.subjectIds))),
    (id) => id,
  )
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
