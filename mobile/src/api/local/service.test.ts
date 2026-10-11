import 'fake-indexeddb/auto'
import { beforeEach, describe, expect, it } from 'vitest'
import { AppDb, useDb } from './db'
import { pickQuestions, percent } from './picker'
import { importSubject, type SeedFile } from './seed'
import * as svc from './service'
import { AppError } from '../errors'
import type { Subject } from '@/types'

function makeSeed(name: string, topics: number, perTopic: number): SeedFile {
  return {
    subject: name,
    topics: Array.from({ length: topics }, (_, t) => ({
      name: `Mavzu ${t + 1}`,
      questions: Array.from({ length: perTopic }, (_, q) => ({
        text: `${name} ${t + 1}-${q + 1} savol`,
        explanation: `izoh ${q}`,
        difficulty: 'EASY' as const,
        options: [
          { text: 'to\'g\'ri', correct: true },
          { text: 'xato 1', correct: false },
          { text: 'xato 2', correct: false },
        ],
      })),
    })),
  }
}

let n = 0
beforeEach(async () => {
  useDb(new AppDb(`test-${n++}`))
  await importSubject(makeSeed('Fan A', 3, 10))
  await importSubject(makeSeed('Fan B', 2, 5))
})

describe('picker', () => {
  it('mavzular bo\'yicha teng taqsimlaydi', () => {
    const src = new Map([[1, [1, 2, 3, 4, 5, 6]], [2, [11, 12, 13, 14, 15, 16]], [3, [21, 22, 23, 24, 25, 26]]])
    for (let i = 0; i < 20; i++) {
      const picked = pickQuestions(src, 9)
      expect(new Set(picked).size).toBe(9)
      for (const ids of src.values()) expect(picked.filter((x) => ids.includes(x)).length).toBe(3)
    }
  })
  it('bir mavzuda savol tugasa boshqasidan to\'ldiradi', () => {
    const picked = pickQuestions(new Map([[1, [1, 2]], [2, [11, 12, 13, 14, 15, 16, 17, 18]]]), 10)
    expect(picked.filter((x) => x < 10).length).toBe(2)
  })
  it('foizni bir xona aniqlikda yaxlitlaydi', () => {
    expect(percent(1, 3)).toBe(33.3)
    expect(percent(0, 0)).toBe(0)
  })
})

describe('seed', () => {
  it('bir fanni ikki marta yuklamaydi', async () => {
    expect(await importSubject(makeSeed('Fan A', 1, 1))).toBe(false)
    expect((await svc.listSubjects()).map((s) => s.name)).toEqual(['Fan A', 'Fan B'])
  })
  it('noto\'g\'ri savollarni o\'tkazib yuboradi', async () => {
    await importSubject({ subject: 'Fan C', topics: [{ name: 'M', questions: [
      { text: 'ok', options: [{ text: 'a', correct: true }, { text: 'b', correct: false }] },
      { text: 'ikki to\'g\'ri', options: [{ text: 'a', correct: true }, { text: 'b', correct: true }] },
      { text: 'bitta variant', options: [{ text: 'a', correct: true }] },
    ] }] })
    const c = (await svc.listSubjects()).find((s) => s.name === 'Fan C')!
    expect(c.questionCount).toBe(1)
  })
})

describe('test oqimi', () => {
  async function subj(name: string) {
    const s = (await svc.listSubjects()).find((x) => x.name === name)!
    return { s, topics: await svc.listTopics(s.id) }
  }
  // Blok test so'rovi: bitta sinfli guruh
  const blk = (s: Subject, questionCount: number) => ({ groupId: s.groupId, subjectIds: [s.id], questionCount })

  it('o\'rganish rejimi: javob oldindan berilmaydi, javobdan keyin ochiladi, qayta javob berib bo\'lmaydi', async () => {
    const { s, topics } = await subj('Fan A')
    expect(s.questionCount).toBe(30)
    const { id } = await svc.createTest({ subjectId: s.id, topicIds: topics.map((t) => t.id), questionCount: 6, mode: 'LEARNING' })
    const session = await svc.getSession(id)
    expect(session.questions).toHaveLength(6)
    expect(session.questions.every((q) => q.correctOptionId === null && q.explanation === null)).toBe(true)
    const per = new Map<number, number>()
    session.questions.forEach((q) => per.set(q.topicId, (per.get(q.topicId) ?? 0) + 1))
    expect([...per.values()]).toEqual([2, 2, 2])

    const q0 = session.questions[0]
    const ans = await svc.answer(id, q0.id, q0.options[0].id)
    expect(typeof ans.correct).toBe('boolean')
    expect(ans.correctOptionId).not.toBeNull()
    expect(ans.explanation).toBeTruthy()
    await expect(svc.answer(id, q0.id, q0.options[1].id)).rejects.toBeInstanceOf(AppError)

    // sahifa qayta ochilganda variantlar tartibi o'zgarmaydi
    const again = await svc.getSession(id)
    expect(again.questions[1].options.map((o) => o.id)).toEqual(session.questions[1].options.map((o) => o.id))

    const res = await svc.finish(id)
    expect(res.unansweredCount).toBe(5)
    expect(res.correctCount).toBe(ans.correct ? 1 : 0)
    expect(res.questions.every((q) => q.correctOptionId !== null)).toBe(true)

    const retry = await svc.retryWrong(id)
    expect((await svc.getSession(retry.id)).questionCount).toBe(ans.correct ? 5 : 6)

    const hist = await svc.history(0, 20)
    expect(hist.total).toBe(2)
    expect((await svc.myStats()).totalAnswered).toBe(1)
  })

  it('imtihon rejimi: natija oxirida, javobni o\'zgartirish mumkin, statistikaga yakunlanguncha kirmaydi', async () => {
    const { s, topics } = await subj('Fan B')
    const { id } = await svc.createTest({ subjectId: s.id, topicIds: [topics[0].id], questionCount: 3, mode: 'EXAM' })
    const q0 = (await svc.getSession(id)).questions[0]
    const a = await svc.answer(id, q0.id, q0.options[0].id)
    expect(a.correct).toBeNull()
    expect(a.correctOptionId).toBeNull()
    await svc.answer(id, q0.id, q0.options[1].id)
    expect((await svc.myStats()).totalAnswered).toBe(0)
    await expect(svc.result(id)).rejects.toBeInstanceOf(AppError)
    const res = await svc.finish(id)
    expect(res.questions[0].selectedOptionId).toBe(q0.options[1].id)
    expect((await svc.myStats()).totalAnswered).toBe(1)
    await expect(svc.answer(id, q0.id, q0.options[0].id)).rejects.toBeInstanceOf(AppError)
  })

  it('blok test: fanlar aralash, natija oxirida, ball fan balli bo\'yicha', async () => {
    await importSubject({ ...makeSeed('Fan C', 2, 10), scoreWeight: 3.1 })
    const a = await subj('Fan A')
    const c = await subj('Fan C')
    expect(a.s.scoreWeight).toBe(1)
    expect(c.s.scoreWeight).toBe(3.1)

    const { id } = await svc.createBlockTest([blk(c.s, 5), blk(a.s, 4)])
    const session = await svc.getSession(id)
    expect(session.mode).toBe('BLOCK')
    expect(session.subjectId).toBeNull()
    expect(session.groups.map((x) => x.groupName)).toEqual(['Fan C', 'Fan A'])
    expect(session.questions.filter((q) => q.subjectId === c.s.id)).toHaveLength(5)
    // fanlar alohida bo'limlarda: avval 5 ta Fan C, keyin 4 ta Fan A
    expect(session.questions.map((q) => q.groupName)).toEqual([...Array(5).fill('Fan C'), ...Array(4).fill('Fan A')])
    expect(session.questions.every((q) => q.correctOptionId === null)).toBe(true)

    // Fan C dan 3 ta to'g'ri, Fan A dan 1 ta to'g'ri, qolganlari javobsiz
    const db = (await import('./db')).db
    let cRight = 0
    let aRight = 0
    for (const q of session.questions) {
      const opts = await db.options.where('questionId').equals((await db.sessionQuestions.get(q.id))!.questionId).toArray()
      const right = opts.find((o) => o.correct)!.id
      if (q.subjectId === c.s.id && cRight < 3) {
        const ans = await svc.answer(id, q.id, right)
        expect(ans.correct).toBeNull()
        cRight++
      } else if (q.subjectId === a.s.id && aRight < 1) {
        await svc.answer(id, q.id, right)
        aRight++
      }
    }

    const res = await svc.finish(id)
    expect(res.score).toBe(10.3) // 3 × 3.1 + 1 × 1
    expect(res.maxScore).toBe(19.5) // 5 × 3.1 + 4 × 1
    expect(res.byGroup.map((r) => [r.groupName, r.correct, r.score])).toEqual([['Fan C', 3, 9.3], ['Fan A', 1, 1]])

    const hist = (await svc.history(0, 20)).items[0]
    expect(hist.mode).toBe('BLOCK')
    expect(hist.score).toBe(10.3)
    expect(hist.groupLabels).toEqual(['Fan C', 'Fan A'])
    expect((await svc.myStats()).bySubject.find((x) => x.subjectName === 'Fan C')!.tests).toBe(1)

    const retry = await svc.getSession((await svc.retryWrong(id)).id)
    expect(retry.mode).toBe('BLOCK')
    expect(retry.groups.map((x) => [x.groupName, x.questionCount, x.scoreWeight])).toEqual([['Fan C', 2, 3.1], ['Fan A', 3, 1]])

    await expect(svc.createBlockTest([blk(c.s, 21)])).rejects.toThrow('faqat 20 ta')
    await expect(svc.createBlockTest([blk(c.s, 1), blk(c.s, 1)])).rejects.toBeInstanceOf(AppError)
    await expect(svc.createBlockTest([{ groupId: c.s.groupId, subjectIds: [a.s.id], questionCount: 1 }])).rejects.toThrow('tegishli emas')
    await expect(svc.createBlockTest([{ groupId: c.s.groupId, subjectIds: [], questionCount: 1 }])).rejects.toBeInstanceOf(AppError)
    await expect(svc.createBlockTest([])).rejects.toBeInstanceOf(AppError)
  })

  it('blok test: bir fanning sinflari bitta guruh – umumiy savollar soni va ball', async () => {
    await importSubject({ ...makeSeed('1-sinf Fan M', 2, 10), scoreWeight: 1.1 })
    await importSubject(makeSeed('2-sinf Fan M', 2, 5))
    const m1 = (await subj('1-sinf Fan M')).s
    const m2 = (await subj('2-sinf Fan M')).s
    expect(m1.groupName).toBe('Fan M')
    expect(m2.groupId).toBe(m1.groupId)
    // ball faqat 1-sinf faylida berilgan, lekin guruhga umumiy
    expect(m2.scoreWeight).toBe(1.1)

    const { id } = await svc.createBlockTest([{ groupId: m1.groupId, subjectIds: [m1.id, m2.id], questionCount: 10 }])
    const session = await svc.getSession(id)
    expect(session.timeLimitSeconds).toBe(3600)
    expect(session.groups).toHaveLength(1)
    expect(session.questions.filter((q) => q.subjectId === m1.id)).toHaveLength(5)
    expect(session.questions.filter((q) => q.subjectId === m2.id)).toHaveLength(5)
    const res = await svc.finish(id)
    expect(res.maxScore).toBe(11)
    expect(res.subjectName).toBe('Fan M (1-sinf, 2-sinf)')
    expect(res.byGroup[0].total).toBe(10)

    // faqat bitta sinf
    await svc.createBlockTest([{ groupId: m1.groupId, subjectIds: [m2.id], questionCount: 10 }])
    await expect(svc.createBlockTest([{ groupId: m1.groupId, subjectIds: [m2.id], questionCount: 11 }])).rejects.toThrow('faqat 10 ta')
  })

  it('blok test: har bir fanga 1 soat, vaqt tugaganda yakunlanadi', async () => {
    const a = await subj('Fan A')
    const b = await subj('Fan B')
    const { id } = await svc.createBlockTest([blk(a.s, 2), blk(b.s, 2)])
    const session = await svc.getSession(id)
    expect(session.timeLimitSeconds).toBe(7200)
    expect(session.remainingSeconds).toBeGreaterThan(7190)
    await svc.answer(id, session.questions[0].id, session.questions[0].options[0].id)

    const db = (await import('./db')).db
    const shift = async (sid: number, hours: number) => {
      const s = (await db.sessions.get(sid))!
      await db.sessions.update(sid, { startedAt: new Date(new Date(s.startedAt).getTime() - hours * 3600_000).toISOString() })
    }
    await shift(id, 3)
    const q1 = session.questions[1]
    await expect(svc.answer(id, q1.id, q1.options[0].id)).rejects.toThrow('Vaqt tugadi')
    const res = await svc.result(id)
    expect(res.durationSeconds).toBe(7200)
    expect(res.unansweredCount).toBe(3)
    expect(res.score).not.toBeNull()

    const second = (await svc.createBlockTest([blk(a.s, 1)])).id
    await shift(second, 2)
    expect((await svc.history(0, 20)).items[0].status).toBe('FINISHED')

    const plain = await svc.createTest({ subjectId: a.s.id, topicIds: a.topics.map((t) => t.id), questionCount: 1, mode: 'EXAM' })
    expect((await svc.getSession(plain.id)).timeLimitSeconds).toBeNull()
  })

  it('tarixni tozalaydi, savollar saqlanib qoladi', async () => {
    const { s, topics } = await subj('Fan A')
    const { id } = await svc.createTest({ subjectId: s.id, topicIds: topics.map((t) => t.id), questionCount: 3, mode: 'LEARNING' })
    const q0 = (await svc.getSession(id)).questions[0]
    await svc.answer(id, q0.id, q0.options[0].id)
    await svc.finish(id)

    await svc.clearHistory()
    expect((await svc.history(0, 20)).total).toBe(0)
    const stats = await svc.myStats()
    expect(stats.totalTests).toBe(0)
    expect(stats.totalAnswered).toBe(0)
    await expect(svc.getSession(id)).rejects.toBeInstanceOf(AppError)
    expect((await svc.listSubjects()).find((x) => x.name === 'Fan A')!.questionCount).toBe(30)
  })

  it('noto\'g\'ri so\'rovlarni rad etadi', async () => {
    const a = await subj('Fan A')
    const b = await subj('Fan B')
    await expect(svc.createTest({ subjectId: a.s.id, topicIds: [a.topics[0].id], questionCount: 11, mode: 'LEARNING' })).rejects.toThrow('faqat 10 ta')
    await expect(svc.createTest({ subjectId: a.s.id, topicIds: b.topics.map((t) => t.id), questionCount: 1, mode: 'LEARNING' })).rejects.toBeInstanceOf(AppError)
    await expect(svc.createTest({ subjectId: a.s.id, topicIds: [], questionCount: 1, mode: 'LEARNING' })).rejects.toBeInstanceOf(AppError)
  })
})
