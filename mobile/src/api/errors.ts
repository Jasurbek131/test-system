/** Foydalanuvchiga ko'rsatiladigan xato (backenddagi ApiException o'rnida). */
export class AppError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'AppError'
  }
}

/** Veb-versiyadagi errorMessage bilan bir xil imzo: sahifalar o'zgarishsiz ishlaydi. */
export function errorMessage(e: unknown): string {
  if (e instanceof AppError) return e.message
  console.error(e)
  return 'Kutilmagan xatolik'
}
