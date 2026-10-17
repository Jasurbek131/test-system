import type {
  AnswerResponse,
  Difficulty,
  MistakeItem,
  MyStats,
  ProgressOverview,
  ReviewKind,
  SubjectDue,
  SubjectProgress,
  TopicProgress,
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
import { answerXps, isDue, level, localDay, recentByTopic, replay, sessionBonus, type AnswerEvent, type QState } from './progress'

const MAX_QUESTIONS = 500
/** Blok testda har bir fan (guruh) ga 1 soat (backenddagi BLOCK_SECONDS_PER_SUBJECT kabi) */
const BLOCK_SECONDS_PER_SUBJECT = 3600
/** Blits rejimi davomiyligi (backenddagi BLITZ_SECONDS) */
const BLITZ_SECONDS = 60
/** Javob berilishi bilan to'g'ri javob ochiladigan va javobni o'zgartirib bo'lmaydigan rejimlar */
const revealsInstantly = (mode: TestMode) => mode === 'LEARNING' || mode === 'BLITZ' || mode === 'FLASHCARD'

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
  timeLimitSeconds?: number,
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
      ...(timeLimitSeconds ? { timeLimitSeconds } : {}),
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
  const mode = req.mode ?? 'LEARNING'
  const difficulty = new Map(questions.map((q) => [q.id, q.difficulty]))
  let picked = pickQuestions(idsByTopic, req.questionCount, Math.random, await personalPriority(mode, questions, difficulty))
  // o'rganishda savollar osondan qiyinga (bir xil qiyinlikdagilar tasodifiy tartibda)
  if (mode === 'LEARNING' || mode === 'FLASHCARD') picked = picked.sort((a, b) => RANK[difficulty.get(a)!] - RANK[difficulty.get(b)!])
  return { id: await newSession(req.subjectId, mode, topicIds, picked, undefined, mode === 'BLITZ' ? BLITZ_SECONDS : undefined) }
}

const RANK: Record<Difficulty, number> = { EASY: 0, MEDIUM: 1, HARD: 2 }

/**
 * Backenddagi personalPriority: mavzu ichida avval yangi va takrorlash vaqti kelgan savollar; imtihondan boshqa
 * rejimlarda qiyinlik mavzu darajasiga moslanadi (yangi – oson, o'rganilmoqda – o'rta, o'zlashtirilgan – qiyin).
 */
async function personalPriority(mode: TestMode, questions: DbQuestion[], difficulty: Map<number, Difficulty>) {
  const topicIds = new Set(questions.map((q) => q.topicId))
  const events = (await answerEvents()).filter((e) => topicIds.has(e.topicId))
  const states = replay(events)
  const recent = recentByTopic(events)
  const topicOf = new Map(questions.map((q) => [q.id, q.topicId]))
  const target: Record<string, number> = { NEW: 0, LEARNING: 1, MASTERED: 2 }
  const now = Date.now()
  return (id: number) => {
    const st = states.get(id)
    const tier = !st || isDue(st, now) ? 0 : 1
    if (mode === 'EXAM') return tier
    return tier * 10 + Math.abs(RANK[difficulty.get(id)!] - target[level(recent.get(topicOf.get(id)!))])
  }
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
  // Blitsdagi xatolar vaqt cheklovisiz, o'rganish rejimida qayta yechiladi
  return { id: await newSession(s.subjectId, s.mode === 'BLITZ' ? 'LEARNING' : s.mode, topicIds, ids) }
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

/** Yakunlangan test; o'rganish/blitsda javob berilgan savol; kartochkada – doim (baho esa baholagandan keyin) */
function canReveal(s: DbSession, sq: DbSessionQuestion): boolean {
  return s.status === 'FINISHED' || s.mode === 'FLASHCARD' || (revealsInstantly(s.mode) && sq.selectedOptionId !== null)
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

/** Kartochka rejimida optionId o'rniga known («Bilardim» / «Bilmasdim») beriladi */
export async function answer(sessionId: number, sessionQuestionId: number, optionId: number | null, known?: boolean): Promise<AnswerResponse> {
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
    if (revealsInstantly(s.mode) && sq.selectedOptionId !== null) throw new AppError('Bu savolga javob berilgan')
    const options = await db.options.where('questionId').equals(sq.questionId).toArray()
    let selected: number
    let correct: boolean
    if (s.mode === 'FLASHCARD') {
      // o'quvchi o'zini baholaydi; tanlangan variant sifatida to'g'ri javob yoziladi (backenddagi kabi)
      if (known === undefined) throw new AppError('«Bilardim» yoki «Bilmasdim» ni tanlang')
      selected = correctOptionId(sq, options) ?? sq.optionIds[0]
      correct = known
    } else {
      if (optionId === null) throw new AppError('Variant tanlanishi shart')
      if (!sq.optionIds.includes(optionId)) throw new AppError('Variant bu savolga tegishli emas')
      const option = options.find((o) => o.id === optionId)
      if (!option) throw new AppError('Variant topilmadi')
      selected = optionId
      correct = option.correct
    }

    const updated: DbSessionQuestion = { ...sq, selectedOptionId: selected, correct, answeredAt: new Date().toISOString() }
    await db.sessionQuestions.put(updated)
    const reveal = canReveal(s, updated)
    const question = await db.questions.get(sq.questionId)
    return {
      sessionQuestionId: sq.id,
      selectedOptionId: selected,
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

function buildResult(l: Loaded): Omit<ResultView, 'xp'> {
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
    let sqs = await db.sessionQuestions.where('sessionId').equals(sessionId).sortBy('orderIndex')
    if (s.mode === 'BLITZ') {
      // Blitsda hamma savolga ulgurish shart emas: natijada faqat javob berilganlar qoladi
      await db.sessionQuestions.bulkDelete(sqs.filter((sq) => sq.selectedOptionId === null).map((sq) => sq.id))
      sqs = sqs.filter((sq) => sq.selectedOptionId !== null).map((sq, orderIndex) => ({ ...sq, orderIndex }))
      await db.sessionQuestions.bulkPut(sqs)
      s.questionCount = sqs.length
    }
    const correct = sqs.filter((sq) => sq.correct === true).length
    // Vaqti tugagan bo'lsa yakunlash vaqti – muddat (keyinroq ochilsa ham sarflangan vaqt oshib ketmaydi)
    const d = deadline(s)
    const changes: Partial<DbSession> = {
      status: 'FINISHED',
      finishedAt: new Date(d !== null && Date.now() > d ? d : Date.now()).toISOString(),
      correctCount: correct,
      questionCount: s.questionCount,
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
  return withXp(buildResult(await load(sessionId)))
}

export async function result(sessionId: number): Promise<ResultView> {
  const l = await load(sessionId)
  if (l.session.status !== 'FINISHED') throw new AppError('Test hali yakunlanmagan')
  return withXp(buildResult(l))
}

/** Shu testda olingan XP (24 soat qoidasi butun tarix bo'yicha, backenddagi sessionXp kabi) */
async function withXp(r: Omit<ResultView, 'xp'>): Promise<ResultView> {
  const events = await answerEvents()
  const xps = answerXps(events)
  const s = await findSession(r.id)
  const answersXp = events.reduce((sum, e, i) => sum + (e.sessionId === r.id ? xps[i] : 0), 0)
  return { ...r, xp: answersXp + (s.status === 'FINISHED' ? sessionBonus(s.mode, s.questionCount, s.scorePercent) : 0) }
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
 * Backend bilan bir xil qoida: yakunlangan testlar va o'rganish/blits rejimidagi javoblar hisoblanadi,
 * davom etayotgan imtihondagi javoblar va kartochkalar (o'zi baholagan) hisoblanmaydi.
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
    return (
      sq.selectedOptionId !== null &&
      !!s &&
      (s.status === 'FINISHED' || s.mode === 'LEARNING' || s.mode === 'BLITZ') &&
      s.mode !== 'FLASHCARD' &&
      questionById.has(sq.questionId)
    )
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

// ---------------------------------------------------------------- progress (backenddagi ProgressService)

/**
 * Hisobga olinadigan javoblar vaqt bo'yicha: yakunlangan testlar va javobi darhol ochiladigan rejimlar
 * (o'rganish, blits, kartochka); davom etayotgan imtihondagi javoblar kirmaydi.
 */
async function answerEvents(): Promise<AnswerEvent[]> {
  const [sessions, sqs, questions] = await Promise.all([db.sessions.toArray(), db.sessionQuestions.toArray(), db.questions.toArray()])
  const sessionById = new Map(sessions.map((x) => [x.id, x]))
  const questionById = new Map(questions.map((q) => [q.id, q]))
  const events: (AnswerEvent & { sqId: number })[] = []
  for (const sq of sqs) {
    const s = sessionById.get(sq.sessionId)
    const q = questionById.get(sq.questionId)
    if (!s || !q || sq.selectedOptionId === null || !sq.answeredAt) continue
    if (s.status !== 'FINISHED' && !revealsInstantly(s.mode)) continue
    events.push({
      sqId: sq.id,
      sessionId: s.id,
      questionId: q.id,
      correct: sq.correct === true,
      at: new Date(sq.answeredAt).getTime(),
      selectedOptionId: sq.selectedOptionId,
      mode: s.mode,
      topicId: q.topicId,
      subjectId: q.subjectId,
    })
  }
  return events.sort((a, b) => a.at - b.at || a.sqId - b.sqId)
}

export async function createReview(req: { subjectId: number; kind: ReviewKind; questionCount: number; mode: TestMode }) {
  const mode = req.mode ?? 'LEARNING'
  if (mode === 'BLOCK') throw new AppError("Takrorlash blok test bo'lishi mumkin emas")
  await getSubject(req.subjectId)
  const states = replay((await answerEvents()).filter((e) => e.subjectId === req.subjectId))
  const now = Date.now()
  const entries = [...states.entries()]
  const ids = (
    req.kind === 'MISTAKES'
      ? entries.filter(([, st]) => !st.lastCorrect).sort(([, a], [, b]) => b.wrong - a.wrong || b.lastAt - a.lastAt)
      : entries.filter(([, st]) => isDue(st, now)).sort(([, a], [, b]) => a.due - b.due)
  ).map(([id]) => id)
  if (!ids.length) throw new AppError(req.kind === 'MISTAKES' ? "Xatolar daftari bo'sh" : "Hozircha takrorlanadigan savollar yo'q")
  const picked = shuffle(ids.slice(0, Math.max(1, req.questionCount)))
  const questions = (await db.questions.bulkGet(picked)).filter((q): q is DbQuestion => !!q)
  const topics = (await db.topics.bulkGet([...new Set(questions.map((q) => q.topicId))])).filter((t) => !!t)
  const topicIds = topics.sort((a, b) => a!.orderIndex - b!.orderIndex || a!.name.localeCompare(b!.name)).map((t) => t!.id)
  return { id: await newSession(req.subjectId, mode, topicIds, picked, undefined, mode === 'BLITZ' ? BLITZ_SECONDS : undefined) }
}

export async function subjectProgress(subjectId: number): Promise<SubjectProgress> {
  const subject = await getSubject(subjectId)
  const topics = await listTopics(subjectId)
  const events = (await answerEvents()).filter((e) => e.subjectId === subjectId)
  const states = replay(events)
  const recent = recentByTopic(events)
  const topicOf = new Map(events.map((e) => [e.questionId, e.topicId]))
  const now = Date.now()
  const per = new Map<number, { seen: number; answered: number; due: number; mistakes: number }>()
  for (const [qid, st] of states) {
    const tid = topicOf.get(qid)!
    const c = per.get(tid) ?? { seen: 0, answered: 0, due: 0, mistakes: 0 }
    c.seen++
    c.answered += st.answered
    if (isDue(st, now)) c.due++
    if (!st.lastCorrect) c.mistakes++
    per.set(tid, c)
  }
  const result: TopicProgress[] = topics.map((t) => {
    const c = per.get(t.id) ?? { seen: 0, answered: 0, due: 0, mistakes: 0 }
    const r = recent.get(t.id)
    return {
      topicId: t.id,
      topicName: t.name,
      total: t.questionCount,
      ...c,
      recentAnswered: r?.length ?? 0,
      recentCorrect: r?.filter(Boolean).length ?? 0,
      level: level(r),
    }
  })
  return {
    subjectId,
    subjectName: subject.name,
    due: result.reduce((n, t) => n + t.due, 0),
    mistakes: result.reduce((n, t) => n + t.mistakes, 0),
    topics: result,
  }
}

export async function progressOverview(): Promise<ProgressOverview> {
  await finishExpired()
  const events = await answerEvents()
  const now = Date.now()
  const weekAgo = now - 7 * 24 * 3600 * 1000
  const days = new Map<string, { answered: number; correct: number; xp: number }>()
  const day = (key: string) => days.get(key) ?? days.set(key, { answered: 0, correct: 0, xp: 0 }).get(key)!
  let xp = 0
  let xpWeek = 0
  let answered = 0
  let correct = 0
  let flashcards = 0
  const xps = answerXps(events)
  for (const [i, e] of events.entries()) {
    const x = xps[i]
    const d = day(localDay(e.at))
    d.answered++
    d.xp += x
    if (e.correct) d.correct++
    xp += x
    if (e.at > weekAgo) xpWeek += x
    if (e.mode === 'FLASHCARD') flashcards++
    else {
      answered++
      if (e.correct) correct++
    }
  }
  let perfect = 0
  let blitzBest = 0
  let bestBlock = 0
  const finished = (await db.sessions.toArray()).filter((s) => s.status === 'FINISHED')
  for (const s of finished) {
    const bonus = sessionBonus(s.mode, s.questionCount, s.scorePercent)
    const at = new Date(s.finishedAt ?? s.startedAt).getTime()
    if (bonus > 0) {
      day(localDay(at)).xp += bonus
      xp += bonus
      if (at > weekAgo) xpWeek += bonus
    }
    if (s.mode !== 'FLASHCARD' && s.questionCount >= 10 && (s.scorePercent ?? 0) >= 100) perfect++
    if (s.mode === 'BLITZ') blitzBest = Math.max(blitzBest, s.correctCount ?? 0)
    if (s.mode === 'BLOCK') bestBlock = Math.max(bestBlock, s.scorePercent ?? 0)
  }
  const from = localDay(now - 365 * 24 * 3600 * 1000)

  const states = replay(events)
  const subjectOf = new Map(events.map((e) => [e.questionId, e.subjectId]))
  const per = new Map<number, { due: number; mistakes: number }>()
  for (const [qid, st] of states) {
    const sid = subjectOf.get(qid)!
    const c = per.get(sid) ?? { due: 0, mistakes: 0 }
    if (isDue(st, now)) c.due++
    if (!st.lastCorrect) c.mistakes++
    per.set(sid, c)
  }
  const recent = recentByTopic(events)
  const [subjects, topics, questions] = await Promise.all([db.subjects.toArray(), db.topics.toArray(), db.questions.toArray()])
  const withQuestions = new Set(questions.map((q) => q.topicId))
  let masteredTopics = 0
  const masteredSubjects: string[] = []
  const subjectDue: SubjectDue[] = []
  for (const s of [...subjects].sort((a, b) => a.name.localeCompare(b.name))) {
    const list = topics.filter((t) => t.subjectId === s.id && withQuestions.has(t.id))
    if (!list.length) continue
    const mastered = list.filter((t) => level(recent.get(t.id)) === 'MASTERED').length
    masteredTopics += mastered
    if (mastered === list.length) masteredSubjects.push(s.name)
    const c = per.get(s.id) ?? { due: 0, mistakes: 0 }
    subjectDue.push({ subjectId: s.id, subjectName: s.name, due: c.due, mistakes: c.mistakes, masteredTopics: mastered, topicCount: list.length })
  }
  return {
    xp,
    xpWeek,
    days: [...days.entries()]
      .filter(([k]) => k >= from)
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([date, d]) => ({ date, ...d })),
    totals: {
      answered,
      correct,
      finishedTests: finished.length,
      perfectTests: perfect,
      blitzBest,
      flashcards,
      bestBlockPercent: bestBlock,
      masteredTopics,
      masteredSubjects,
    },
    dueCount: subjectDue.reduce((n, x) => n + x.due, 0),
    mistakesCount: subjectDue.reduce((n, x) => n + x.mistakes, 0),
    subjects: subjectDue,
  }
}

/** Xatolar daftari: oxirgi javobi xato bo'lgan savollar, eng yangisi birinchi */
export async function mistakes(subjectId?: number | null): Promise<MistakeItem[]> {
  await finishExpired()
  const events = (await answerEvents()).filter((e) => !subjectId || e.subjectId === subjectId)
  const states: Map<number, QState> = replay(events)
  const lastSelected = new Map<number, number>()
  for (const e of events) {
    if (e.mode === 'FLASHCARD') lastSelected.delete(e.questionId)
    else lastSelected.set(e.questionId, e.selectedOptionId)
  }
  const ids = [...states.entries()].filter(([, st]) => !st.lastCorrect).sort(([, a], [, b]) => b.lastAt - a.lastAt).map(([id]) => id)
  if (!ids.length) return []
  const [qs, opts, subjects] = await Promise.all([db.questions.bulkGet(ids), db.options.where('questionId').anyOf(ids).toArray(), db.subjects.toArray()])
  const topics = (await db.topics.bulkGet([...new Set(qs.filter((q) => !!q).map((q) => q!.topicId))])).filter((t) => !!t)
  const topicName = new Map(topics.map((t) => [t!.id, t!.name]))
  const subjectName = new Map(subjects.map((x) => [x.id, x.name]))
  const optionText = new Map(opts.map((o) => [o.id, o.text]))
  return qs
    .filter((q): q is DbQuestion => !!q)
    .map((q) => {
      const st = states.get(q.id)!
      const sel = lastSelected.get(q.id)
      return {
        questionId: q.id,
        subjectId: q.subjectId,
        subjectName: subjectName.get(q.subjectId) ?? '',
        topicId: q.topicId,
        topicName: topicName.get(q.topicId) ?? '',
        text: q.text,
        explanation: q.explanation,
        correctAnswer: opts.find((o) => o.questionId === q.id && o.correct)?.text ?? null,
        yourAnswer: sel === undefined ? null : (optionText.get(sel) ?? null),
        wrongCount: st.wrong,
        lastAnsweredAt: new Date(st.lastAt).toISOString(),
      }
    })
}
