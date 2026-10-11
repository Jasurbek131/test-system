import 'fake-indexeddb/auto'
import { beforeEach, describe, expect, it } from 'vitest'
import { AppDb, useDb } from './db'
import { pickQuestions, percent } from './picker'
import { importSubject, type SeedFile } from './seed'
import * as svc from './service'
import { AppError } from '../errors'

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
