import axios from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResult } from '@/types'
import { clearAuth, getToken } from '@/utils/auth'

const http = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResult<unknown> | undefined
    if (body && typeof body === 'object' && typeof body.code === 'number') {
      if (body.code === 200) {
        return body.data
      }
      ElMessage.error(body.message || '操作失败')
      return Promise.reject(new Error(body.message || '操作失败'))
    }
    return response.data
  },
  (error) => {
    const status = error.response?.status as number | undefined
    const body = error.response?.data as ApiResult<unknown> | undefined
    const message = body?.message

    if (status === 401) {
      clearAuth()
      ElMessage.error(message || '登录已过期，请重新登录')
      if (!window.location.hash.startsWith('#/login')) {
        window.location.hash = '#/login'
      }
    } else {
      ElMessage.error(message || '网络异常，请稍后重试')
    }
    return Promise.reject(error)
  },
)

export function getData<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  return http.get(url, { params }) as unknown as Promise<T>
}

export function postData<T>(url: string, data?: unknown): Promise<T> {
  return http.post(url, data) as unknown as Promise<T>
}

export function putData<T>(url: string, data?: unknown): Promise<T> {
  return http.put(url, data) as unknown as Promise<T>
}

export function deleteData<T>(url: string): Promise<T> {
  return http.delete(url) as unknown as Promise<T>
}
