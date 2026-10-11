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

/**
 * Backenddagi QuestionPicker.allocate: savollar sonini guruhlar (sinflar) orasida teng taqsimlaydi,
 * savoli tugagan guruh o'tkazib yuboriladi. Natija: [kalit -> nechta savol].
 */
export function allocate<K>(available: Map<K, number>, count: number, random: () => number = Math.random): Map<K, number> {
  const total = [...available.values()].reduce((s, n) => s + n, 0)
  if (count < 0 || count > total) throw new Error(`count must be between 0 and ${total}`)
  const keys = shuffle([...available.keys()], random)
  const result = new Map<K, number>([...available.keys()].map((k) => [k, 0]))
  let given = 0
  while (given < count) {
    for (const k of keys) {
      if (given === count) break
      if (result.get(k)! < available.get(k)!) {
        result.set(k, result.get(k)! + 1)
        given++
      }
    }
  }
  return result
}

export function percent(part: number, total: number): number {
  if (total === 0) return 0
  return Math.round((part * 1000) / total) / 10
}
