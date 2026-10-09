import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { authApi } from '@/api'
import { TOKEN_KEY } from '@/api/http'
import type { AuthResponse, User } from '@/types'

const USER_KEY = 'ts_user'

function readUser(): User | null {
  try {
    const raw = localStorage.getItem(USER_KEY)
    return raw ? (JSON.parse(raw) as User) : null
  } catch {
    return null
  }
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem(TOKEN_KEY))
  const user = ref<User | null>(readUser())

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => user.value?.role === 'ADMIN')

  function setSession(res: AuthResponse) {
    token.value = res.token
    user.value = res.user
    localStorage.setItem(TOKEN_KEY, res.token)
    localStorage.setItem(USER_KEY, JSON.stringify(res.user))
  }

  async function login(username: string, password: string) {
    setSession(await authApi.login(username, password))
  }

  async function register(body: { username: string; password: string; fullName: string }) {
    setSession(await authApi.register(body))
  }

  /** Token hali amal qilayotganini tekshiradi va foydalanuvchi ma'lumotini yangilaydi. */
  async function refresh() {
    if (!token.value) return
    const me = await authApi.me()
    user.value = me
    localStorage.setItem(USER_KEY, JSON.stringify(me))
  }

  function logout() {
    token.value = null
    user.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  }

  return { token, user, isLoggedIn, isAdmin, login, register, refresh, logout }
})
