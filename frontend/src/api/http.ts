import axios, { AxiosError } from 'axios'
import type { ApiError } from '@/types'

export const TOKEN_KEY = 'ts_token'

export const http = axios.create({ baseURL: '/api', timeout: 30000 })

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

/** 401 bo'lganda chaqiriladi (store va router main.ts da ulab qo'yiladi). */
let onUnauthorized: () => void = () => {}
export function setUnauthorizedHandler(fn: () => void) {
  onUnauthorized = fn
}

http.interceptors.response.use(
  (res) => res,
  (error: AxiosError) => {
    const url = error.config?.url ?? ''
    if (error.response?.status === 401 && !url.startsWith('/auth/login')) {
      onUnauthorized()
    }
    return Promise.reject(error)
  },
)

/** Xatodan foydalanuvchiga ko'rsatiladigan matn. */
export function errorMessage(e: unknown): string {
  if (axios.isAxiosError(e)) {
    const data = e.response?.data as ApiError | undefined
    if (data?.errors?.length) return data.errors.map((x) => x.message).join('; ')
    if (data?.message) return data.message
    if (!e.response) return "Server bilan aloqa yo'q"
  }
  return 'Kutilmagan xatolik'
}
