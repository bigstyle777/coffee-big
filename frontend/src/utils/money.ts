/**
 * 金额工具：后端统一返回「分」，前端展示统一转成「元」
 */

/** 分 -> 元，保留两位小数，如 1850 -> "18.50" */
export function fenToYuan(amount?: number | null): string {
  const fen = Number(amount)
  if (!Number.isFinite(fen)) {
    return '0.00'
  }
  return (fen / 100).toFixed(2)
}

/** 分 -> 带货币符号的文案，如 1850 -> "¥18.50" */
export function formatMoney(amount?: number | null): string {
  return `¥${fenToYuan(amount)}`
}
