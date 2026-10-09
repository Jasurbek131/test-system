/** Fisher–Yates aralashtirish (asl massiv o'zgarmaydi). */
export function shuffle<T>(items: readonly T[], random: () => number = Math.random): T[] {
  const a = [...items]
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(random() * (i + 1))
    ;[a[i], a[j]] = [a[j], a[i]]
  }
  return a
}

/**
 * Backenddagi QuestionPicker bilan bir xil qoida: mavzulardan navbatma-navbat bittadan savol olinadi,
 * shuning uchun savollar mavzular bo'yicha teng taqsimlanadi; biror mavzuda savol tugasa,
 * qolgani boshqa mavzulardan to'ldiriladi. Natija yana aralashtiriladi.
 */
export function pickQuestions(idsByTopic: Map<number, number[]>, count: number, random: () => number = Math.random): number[] {
  const available = [...idsByTopic.values()].reduce((s, ids) => s + ids.length, 0)
  if (count < 1 || count > available) throw new Error(`count must be between 1 and ${available}`)

  const pools = shuffle(
    [...idsByTopic.values()].filter((ids) => ids.length > 0).map((ids) => shuffle(ids, random)),
    random,
  )
  const result: number[] = []
  while (result.length < count) {
    for (const pool of pools) {
      if (result.length === count) break
      const id = pool.shift()
      if (id !== undefined) result.push(id)
    }
  }
  return shuffle(result, random)
}

export function percent(part: number, total: number): number {
  if (total === 0) return 0
  return Math.round((part * 1000) / total) / 10
}
