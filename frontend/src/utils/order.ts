import type { OrderStatus, PickupType } from '@/types/api'

/** 订单状态文案 */
const STATUS_TEXT: Record<OrderStatus, string> = {
  PENDING_PAY: '待支付',
  MAKING: '制作中',
  READY: '待取餐',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
}

export function orderStatusText(status?: OrderStatus | string | null): string {
  if (!status) {
    return ''
  }
  return STATUS_TEXT[status as OrderStatus] || status
}

/** 取餐方式文案 */
export function pickupTypeText(type?: PickupType | string | null): string {
  return type === 'DINE_IN' ? '堂食' : '到店自取'
}

/**
 * 后端 LocalDateTime 序列化为 ISO 字符串（2026-11-14T09:30:00.123），
 * 这里只做展示格式化，不参与时间计算。
 */
export function formatDateTime(value?: string | null): string {
  if (!value) {
    return ''
  }
  return String(value).replace('T', ' ').slice(0, 16)
}

/** 只展示时分，如 "09:30" */
export function formatTime(value?: string | null): string {
  const text = formatDateTime(value)
  return text ? text.slice(11, 16) : ''
}
