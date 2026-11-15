import type { OrderStatus } from '@/types'

interface StatusMeta {
  label: string
  type: 'info' | 'warning' | 'success' | 'danger' | 'primary'
}

export const ORDER_STATUS_META: Record<OrderStatus, StatusMeta> = {
  PENDING_PAY: { label: '待支付', type: 'info' },
  MAKING: { label: '待制作', type: 'warning' },
  READY: { label: '待取餐', type: 'success' },
  COMPLETED: { label: '已完成', type: 'info' },
  CANCELLED: { label: '已取消', type: 'danger' },
}

/** 金额分 -> 元 */
export function formatMoney(cents: number | null | undefined): string {
  if (cents === null || cents === undefined) {
    return '¥0.00'
  }
  return `¥${(cents / 100).toFixed(2)}`
}

/** 后端 LocalDateTime -> yyyy-MM-dd HH:mm:ss */
export function formatDateTime(value: string | null | undefined): string {
  if (!value) {
    return '-'
  }
  return value.replace('T', ' ').slice(0, 19)
}

export function pickupTypeLabel(pickupType: string): string {
  return pickupType === 'DINE_IN' ? '堂食' : '到店自取'
}

export function orderItemsSummary(order: { items: { productName: string; quantity: number }[]; itemCount: number }): string {
  if (!order.items || order.items.length === 0) {
    return '-'
  }
  const first = `${order.items[0].productName} ×${order.items[0].quantity}`
  return order.items.length > 1 ? `${first} 等 ${order.itemCount} 件` : first
}
