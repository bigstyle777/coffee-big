import { API_BASE_URL, REQUEST_TIMEOUT } from '@/config'
import type { ApiResult, LoginResult } from '@/types/api'
import { clearAuth, getToken, notifyAuthChanged, notifyAuthCleared, saveAuth } from '@/utils/auth'

export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE'

export interface RequestOptions {
  /** 相对路径，如 /menu/list */
  url: string
  method?: HttpMethod
  data?: unknown
  header?: Record<string, string>
  /** 是否携带 Token 并处理 401，默认 true */
  auth?: boolean
  /** 是否静默处理错误（只 reject，不弹提示） */
  silent?: boolean
}

/** 并发 401 时共用同一次静默重登 */
let reloginTask: Promise<string | null> | null = null

function toast(message: string): void {
  uni.showToast({ title: message || '操作失败', icon: 'none', duration: 2000 })
}

/** 登录态失效：始终清空本地状态，非静默请求额外弹窗提示 */
function handleLoginExpired(showModal = true): void {
  clearAuth()
  notifyAuthCleared()
  if (!showModal) {
    return
  }
  uni.showModal({
    title: '登录已过期',
    content: '请退出小程序后重新进入，完成微信静默登录',
    showCancel: false,
    confirmText: '知道了',
  })
}

/** 调起微信登录，获取一次性 code */
function getWxLoginCode(): Promise<string> {
  return new Promise<string>((resolve, reject) => {
    uni.login({
      provider: 'weixin',
      success: (res) => {
        if (res.code) {
          resolve(res.code)
        } else {
          reject(new Error('未获取到微信登录 code'))
        }
      },
      fail: (error) => reject(new Error(error.errMsg || '微信登录失败')),
    })
  })
}

/**
 * 401 静默重登：
 * H5 优先走 dev 体验登录，其余端走 uni.login -> /auth/wx-login
 */
function silentRelogin(): Promise<string | null> {
  if (!reloginTask) {
    reloginTask = (async () => {
      try {
        const result = await performLogin(true)
        saveAuth(result)
        notifyAuthChanged(result)
        return result.token
      } catch (error) {
        console.warn('[request] 静默重登失败', error)
        return null
      } finally {
        reloginTask = null
      }
    })()
  }
  return reloginTask
}

function doRequest<T>(options: RequestOptions, retried: boolean): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const header: Record<string, string> = {
      'Content-Type': 'application/json',
      ...(options.header || {}),
    }
    const token = getToken()
    if (options.auth !== false && token) {
      header.Authorization = `Bearer ${token}`
    }

    uni.request({
      url: `${API_BASE_URL}${options.url}`,
      method: options.method || 'GET',
      data: options.data as Record<string, unknown> | undefined,
      header,
      timeout: REQUEST_TIMEOUT,
      success: async (response) => {
        const statusCode = response.statusCode
        const body = response.data as ApiResult<T> | undefined

        // 401：静默重登后重发原请求，仅重试一次，避免死循环
        if (statusCode === 401 && options.auth !== false) {
          if (retried) {
            handleLoginExpired(!options.silent)
            reject(new Error('登录已过期'))
            return
          }
          const newToken = await silentRelogin()
          if (!newToken) {
            handleLoginExpired(!options.silent)
            reject(new Error('登录已过期'))
            return
          }
          try {
            resolve(await doRequest<T>(options, true))
          } catch (error) {
            reject(error)
          }
          return
        }

        // 无权限：后台接口只接受 admin-token，小程序 Token 会被拒绝
        if (statusCode === 403) {
          if (!options.silent) {
            toast(body?.message || '没有操作权限')
          }
          reject(new Error(body?.message || '没有操作权限'))
          return
        }

        if (statusCode < 200 || statusCode >= 300) {
          if (!options.silent) {
            toast(body?.message || '服务异常，请稍后重试')
          }
          reject(new Error(body?.message || `HTTP ${statusCode}`))
          return
        }

        // 统一业务返回：code = 200 时解包 data
        if (body && typeof body === 'object' && 'code' in body) {
          if (body.code === 200) {
            resolve(body.data)
          } else {
            if (!options.silent) {
              toast(body.message || '操作失败')
            }
            reject(new Error(body.message || '操作失败'))
          }
          return
        }

        // 非标准结构（理论上不会出现），直接返回，保证兼容性
        resolve(body as unknown as T)
      },
      fail: (error) => {
        if (!options.silent) {
          toast('网络异常，请检查网络后重试')
        }
        reject(new Error(error.errMsg || '网络异常'))
      },
    })
  })
}

/** 统一请求入口，返回已解包的业务数据 */
export function request<T>(options: RequestOptions): Promise<T> {
  return doRequest<T>(options, false)
}

/**
 * dev 体验登录：仅 H5 产物保留该分支，生产环境后端不会注册该接口，失败后自动回退微信登录
 */
async function loginByDev(): Promise<LoginResult | null> {
  // #ifdef H5
  try {
    return await doRequest<LoginResult>(
      { url: '/auth/dev-login', method: 'POST', auth: false, silent: true },
      false,
    )
  } catch (error) {
    return null
  }
  // #endif
  return null
}

/**
 * 统一登录入口：H5 优先体验登录，其他端与生产环境走微信登录
 *
 * @param silent 失败时是否静默（不弹统一错误提示）
 */
export async function performLogin(silent = false): Promise<LoginResult> {
  const devResult = await loginByDev()
  if (devResult) {
    return devResult
  }
  const code = await getWxLoginCode()
  return doRequest<LoginResult>(
    { url: '/auth/wx-login', method: 'POST', data: { code }, auth: false, silent },
    false,
  )
}
