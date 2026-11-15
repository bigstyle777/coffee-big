import type { MenuResult } from '@/types/api'

/** 菜单缓存：断网 / 接口异常时用于兜底展示 */
export interface MenuCache {
  /** 缓存时间戳（毫秒） */
  updatedAt: number
  data: MenuResult
}

const MENU_CACHE_KEY = 'coffee_menu_cache'

/** 读取菜单缓存，结构不合法时返回 null */
export function readMenuCache(): MenuCache | null {
  const raw = uni.getStorageSync(MENU_CACHE_KEY) as MenuCache | ''
  if (!raw || typeof raw !== 'object') {
    return null
  }
  if (!raw.updatedAt || !raw.data || !Array.isArray(raw.data.categories)) {
    return null
  }
  return raw
}

/** 写入菜单缓存，失败时忽略（缓存只影响离线体验，不影响主流程） */
export function writeMenuCache(data: MenuResult): void {
  try {
    uni.setStorageSync(MENU_CACHE_KEY, { updatedAt: Date.now(), data })
  } catch (error) {
    console.warn('[menu-cache] 写入失败', error)
  }
}

/** 清空菜单缓存 */
export function clearMenuCache(): void {
  uni.removeStorageSync(MENU_CACHE_KEY)
}

/** 缓存时间格式化：HH:mm */
export function cacheTimeText(updatedAt: number): string {
  const date = new Date(updatedAt)
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return `${hours}:${minutes}`
}
