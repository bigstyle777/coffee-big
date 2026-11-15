import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { LoginResult, Role, UserInfo } from '@/types/api'
import { clearAuth, getRole, getToken, getUserInfo, onAuthChanged, onAuthCleared, saveAuth } from '@/utils/auth'
import { performLogin } from '@/utils/request'

export const useUserStore = defineStore('user', () => {
  const token = ref(getToken())
  const role = ref<Role>(getRole())
  const userInfo = ref<UserInfo | null>(getUserInfo())
  const logging = ref(false)

  const isLogin = computed(() => Boolean(token.value))
  const isAdmin = computed(() => role.value === 'ADMIN')

  let loginTask: Promise<void> | null = null

  function applyLogin(result: LoginResult): void {
    saveAuth(result)
    token.value = result.token
    role.value = result.role
    userInfo.value = result.userInfo
  }

  // request.ts 在 401 静默重登成功后回调，保持内存状态与本地存储一致
  onAuthChanged((result: LoginResult) => {
    token.value = result.token
    role.value = result.role
    userInfo.value = result.userInfo
  })

  // 静默重登失败时清空内存状态，避免页面继续展示店长入口
  onAuthCleared(() => {
    token.value = ''
    role.value = 'CUSTOMER'
    userInfo.value = null
  })

  async function doLogin(silent: boolean): Promise<void> {
    logging.value = true
    try {
      const result = await performLogin(silent)
      applyLogin(result)
    } finally {
      logging.value = false
    }
  }

  /** 微信静默登录（并发调用共用同一个任务） */
  function login(options: { silent?: boolean } = {}): Promise<void> {
    if (!loginTask) {
      loginTask = doLogin(options.silent === true).finally(() => {
        loginTask = null
      })
    }
    return loginTask
  }

  /** 已登录则直接返回，否则发起静默登录 */
  async function ensureLogin(options: { silent?: boolean } = {}): Promise<void> {
    if (token.value) {
      return
    }
    await login(options)
  }

  function logout(): void {
    clearAuth()
    token.value = ''
    role.value = 'CUSTOMER'
    userInfo.value = null
  }

  return {
    token,
    role,
    userInfo,
    logging,
    isLogin,
    isAdmin,
    login,
    ensureLogin,
    logout,
  }
})
