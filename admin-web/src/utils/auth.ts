import type { AdminInfo, AdminLoginResult } from '@/types'

const TOKEN_KEY = 'coffee_admin_token'
const ADMIN_KEY = 'coffee_admin_info'

export function getToken(): string {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function getAdminInfo(): AdminInfo | null {
  const raw = localStorage.getItem(ADMIN_KEY)
  if (!raw) {
    return null
  }
  try {
    return JSON.parse(raw) as AdminInfo
  } catch {
    return null
  }
}

export function saveAuth(result: AdminLoginResult): void {
  localStorage.setItem(TOKEN_KEY, result.token)
  localStorage.setItem(ADMIN_KEY, JSON.stringify(result.admin))
}

export function clearAuth(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(ADMIN_KEY)
}
