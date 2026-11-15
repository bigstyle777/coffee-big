/**
 * 后端接口数据结构（与 Java VO / DTO 一一对应）
 * 约定：所有金额字段的单位均为「分」，展示时统一由 utils/money.ts 转成「元」
 */

/** 统一响应结构 */
export interface ApiResult<T> {
  code: number
  message: string
  data: T
}

/** 用户角色 */
export type Role = 'CUSTOMER' | 'ADMIN'

/** 用户信息 */
export interface UserInfo {
  id: number
  nickname: string
  avatar: string
  phone: string | null
  role: Role
}

/** 微信登录结果 */
export interface LoginResult {
  token: string
  role: Role
  userInfo: UserInfo
}

/** 规格选项，extraPrice 为加价（分） */
export interface SpecOption {
  id: number
  name: string
  extraPrice: number
}

/** 规格组，如「杯型」「温度」「甜度」 */
export interface SpecGroup {
  id: number
  name: string
  required: boolean
  multiple: boolean
  options: SpecOption[]
}

/** 菜单商品 */
export interface MenuProduct {
  id: number
  name: string
  picUrl: string
  /** 基础价格（分），不含规格加价 */
  price: number
  /** 当前库存（杯） */
  stock: number
  specGroups: SpecGroup[]
}

/** 菜单分类 */
export interface MenuCategory {
  id: number
  name: string
  products: MenuProduct[]
}

/** 菜单接口返回 */
export interface MenuResult {
  categories: MenuCategory[]
}

/** 秒杀商品 */
export interface SeckillProduct {
  activityProductId: number
  activityId: number
  productId: number
  productName: string
  productPic: string
  /** 商品原价（分） */
  originalPrice: number
  /** 秒杀基础价（分/杯），规格加价另计 */
  activityPrice: number
  /** 活动总限量（杯） */
  activityStock: number
  /** 已售（杯） */
  soldCount: number
  /** 剩余可售（杯），优先取 Redis */
  remainingStock: number
  /** 每人限购（杯） */
  limitPerUser: number
  /** 是否已完成 Redis 预热 */
  preheated: boolean
  specGroups: SpecGroup[]
}

/** 秒杀活动 */
export interface SeckillActivity {
  activityId: number
  title: string
  startTime: string
  endTime: string
  products: SeckillProduct[]
}

/** 取餐方式：到店自取 / 堂食 */
export type PickupType = 'SELF_PICKUP' | 'DINE_IN'

/** 订单状态 */
export type OrderStatus = 'PENDING_PAY' | 'MAKING' | 'READY' | 'COMPLETED' | 'CANCELLED'

/** 订单类型 */
export type OrderType = 'NORMAL' | 'SECKILL'

/** 顾客端订单明细 */
export interface OrderItem {
  productId: number
  /** 秒杀活动商品 ID，普通商品为 null */
  activityProductId: number | null
  productName: string
  productPic: string
  /** 规格文案，如「大杯 / 冰 / 无糖」 */
  specText: string
  quantity: number
  /** 单杯价（分），已含规格加价 */
  unitPrice: number
  /** 小计（分） */
  subtotalAmount: number
}

/** 顾客端订单（列表 / 详情共用） */
export interface OrderDetail {
  orderId: number
  orderNo: string
  orderType: OrderType
  pickupCode: string | null
  pickupType: PickupType
  status: OrderStatus
  totalAmount: number
  /** 优惠金额（分），包含活动优惠与优惠券 */
  discountAmount: number
  /** 活动优惠金额（分） */
  activityDiscountAmount: number
  payAmount: number
  itemCount: number
  remark: string
  /** 使用的活动 ID，可为 null */
  activityId: number | null
  /** 活动名称，可为 null */
  activityTitle: string | null
  createTime: string
  payTime: string | null
  items: OrderItem[]
}

/** 创建订单返回 */
export interface OrderCreateResult {
  orderId: number
  orderNo: string
  pickupCode: string | null
  pickupType: PickupType
  totalAmount: number
  discountAmount: number
  payAmount: number
  itemCount: number
  status: OrderStatus
}

/** 预支付结果 */
export interface PaymentPrepayResult {
  provider: 'MOCK' | 'WECHAT'
  /** 预支付 ID，Mock 渠道也会返回唯一值 */
  prepayId: string | null
  timeStamp: string | null
  nonceStr: string | null
  packageValue: string | null
  signType: string | null
  paySign: string | null
}

/** 创建订单的明细入参：金额由后端重算，前端只传 ID、数量与规格选项 */
export interface CreateOrderItem {
  productId: number
  quantity: number
  optionIds: number[]
}

/** 创建订单入参 */
export interface CreateOrderPayload {
  items: CreateOrderItem[]
  pickupType: PickupType
  remark: string
  /** 使用的优惠券 ID，null 表示不使用 */
  couponId: number | null
  /** 客户端幂等键，重试时保持不变 */
  requestId: string
}

/** 可用优惠券 */
export interface Coupon {
  id: number
  name: string
  /** 优惠金额（分） */
  discountAmount: number
  /** 使用门槛（分） */
  minAmount: number
  endTime: string | null
}
