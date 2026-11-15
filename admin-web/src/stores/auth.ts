import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { loginAdmin, logoutAdmin } from '@/api/admin'
import type { AdminInfo } from '@/types'
import { clearAuth, getAdminInfo, getToken, saveAuth } from '@/utils/auth'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(getToken())
  const admin = ref<AdminInfo | null>(getAdminInfo())
  const isLogin = computed(() => Boolean(token.value))

  async function login(username: string, password: string): Promise<void> {
    const result = await loginAdmin({ username: username.trim(), password })
    saveAuth(result)
    token.value = result.token
    admin.value = result.admin
  }

  async function logout(): Promise<void> {
    try {
      await logoutAdmin()
    } catch {
      // 即使后端删除白名单失败，本地也必须清理登录态
    } finally {
      clearAuth()
      token.value = ''
      admin.value = null
    }
  }

  return { token, admin, isLogin, login, logout }
})
