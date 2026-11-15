import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { CreateOrderItem, PickupType } from '@/types/api'

/** 加入购物袋的入参 */
export interface CartItemInput {
  productId: number
  productName: string
  productPic: string
  /** 基础价格（分） */
  basePrice: number
  /** 单杯价（分），已含规格加价 */
  unitPrice: number
  optionIds: number[]
  optionNames: string[]
  quantity: number
}

/** 购物袋条目：商品 + 规格组合 唯一确定一条 */
export interface CartItem extends CartItemInput {
  key: string
  /** 规格展示文案，如「大杯 / 冰 / 无糖」 */
  specText: string
}

const STORAGE_KEY = 'coffee_cart_items'

/**
 * 商品 + 规格组合 生成唯一键：
 * 同一款咖啡选了不同规格（大杯/冰 vs 中杯/热），optionIds 不同，因此是两条独立记录
 */
function buildKey(productId: number, optionIds: number[]): string {
  return `${productId}#${[...optionIds].sort((a, b) => a - b).join(',')}`
}

/** 从本地缓存恢复购物袋（冷启动不丢单） */
function restoreItems(): CartItem[] {
  const raw = uni.getStorageSync(STORAGE_KEY)
  if (!Array.isArray(raw)) {
    return []
  }
  return raw.filter(
    (item: CartItem) =>
      item &&
      typeof item.productId === 'number' &&
      typeof item.key === 'string' &&
      Array.isArray(item.optionIds) &&
      Number(item.quantity) > 0,
  )
}

export const useCartStore = defineStore('cart', () => {
  const items = ref<CartItem[]>(restoreItems())
  const pickupType = ref<PickupType>('SELF_PICKUP')
  const remark = ref('')

  /** 总杯数 */
  const totalCount = computed(() => items.value.reduce((sum, item) => sum + item.quantity, 0))
  /** 总价（分） */
  const totalAmount = computed(() =>
    items.value.reduce((sum, item) => sum + item.unitPrice * item.quantity, 0),
  )
  const isEmpty = computed(() => items.value.length === 0)

  /** 提交给 /order/create 的明细：金额由后端重算 */
  const orderItems = computed<CreateOrderItem[]>(() =>
    items.value.map((item) => ({
      productId: item.productId,
      quantity: item.quantity,
      optionIds: [...item.optionIds],
    })),
  )

  function persist(): void {
    uni.setStorageSync(STORAGE_KEY, items.value)
  }

  function addItem(input: CartItemInput): void {
    const key = buildKey(input.productId, input.optionIds)
    const existing = items.value.find((item) => item.key === key)
    if (existing) {
      existing.quantity += input.quantity
    } else {
      items.value.push({
        ...input,
        key,
        optionIds: [...input.optionIds],
        optionNames: [...input.optionNames],
        specText: input.optionNames.join(' / '),
      })
    }
    persist()
  }

  function increase(key: string): void {
    const item = items.value.find((current) => current.key === key)
    if (!item) {
      return
    }
    item.quantity += 1
    persist()
  }

  function decrease(key: string): void {
    const index = items.value.findIndex((current) => current.key === key)
    if (index === -1) {
      return
    }
    if (items.value[index].quantity > 1) {
      items.value[index].quantity -= 1
    } else {
      items.value.splice(index, 1)
    }
    persist()
  }

  function removeItem(key: string): void {
    const index = items.value.findIndex((current) => current.key === key)
    if (index !== -1) {
      items.value.splice(index, 1)
      persist()
    }
  }

  function clear(): void {
    items.value = []
    remark.value = ''
    persist()
  }

  function setPickupType(type: PickupType): void {
    pickupType.value = type
  }

  function setRemark(value: string): void {
    remark.value = value
  }

  return {
    items,
    pickupType,
    remark,
    totalCount,
    totalAmount,
    isEmpty,
    orderItems,
    addItem,
    increase,
    decrease,
    removeItem,
    clear,
    setPickupType,
    setRemark,
  }
})
