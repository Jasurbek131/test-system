// Mobil ilovada foydalanuvchi va admin yo'q. Umumiy sahifalar uchun soddalashtirilgan store.
export function useAuthStore() {
  return { isAdmin: false, isLoggedIn: true, user: null }
}
