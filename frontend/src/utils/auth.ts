import type { LoginResult, Role, UserInfo } from '@/types/api'

/**
 * 登录态存储
 *
 * 单独抽成模块提供给 stores/user.ts 与 utils/request.ts 共用，
 * 避免 request -> store -> request 的循环依赖。
 */
const TOKEN_KEY = 'coffee_token'
const ROLE_KEY = 'coffee_role'
const USER_KEY = 'coffee_user_info'

/** 登录态变更监听（401 静默重登成功后通知 userStore 同步内存状态） */
type AuthListener = (result: LoginResult) => void
type AuthClearListener = () => void
let authListener: AuthListener | null = null
let authClearListener: AuthClearListener | null = null

export function onAuthChanged(listener: AuthListener): void {
  authListener = listener
}

export function notifyAuthChanged(result: LoginResult): void {
  authListener?.(result)
}

/** 登录态失效监听（静默重登失败、Token 被清除时通知 userStore） */
export function onAuthCleared(listener: AuthClearListener): void {
  authClearListener = listener
}

export function notifyAuthCleared(): void {
  authClearListener?.()
}

export function getToken(): string {
  return uni.getStorageSync(TOKEN_KEY) || ''
}

export function getRole(): Role {
  return uni.getStorageSync(ROLE_KEY) === 'ADMIN' ? 'ADMIN' : 'CUSTOMER'
}

export function getUserInfo(): UserInfo | null {
  const raw = uni.getStorageSync(USER_KEY)
  if (!raw) {
    return null
  }
  try {
    return typeof raw === 'string' ? (JSON.parse(raw) as UserInfo) : (raw as UserInfo)
  } catch {
    return null
  }
}

/** 保存登录态（登录成功 / 静默重登成功时调用） */
export function saveAuth(result: LoginResult): void {
  uni.setStorageSync(TOKEN_KEY, result.token)
  uni.setStorageSync(ROLE_KEY, result.role)
  uni.setStorageSync(USER_KEY, JSON.stringify(result.userInfo))
}

/** 清除登录态 */
export function clearAuth(): void {
  uni.removeStorageSync(TOKEN_KEY)
  uni.removeStorageSync(ROLE_KEY)
  uni.removeStorageSync(USER_KEY)
}
