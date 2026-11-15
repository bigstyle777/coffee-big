/** 统一响应结构 */
export interface ApiResult<T> {
  code: number
  message: string
  data: T
}

/** 分页结果 */
export interface PageResult<T> {
  total: number
  pageNum: number
  pageSize: number
  records: T[]
}

/** 当前登录店长 */
export interface AdminInfo {
  adminId: number
  username: string
  nickname: string
}

/** 后台登录结果 */
export interface AdminLoginResult {
  token: string
  admin: AdminInfo
}

/** 订单状态 */
export type OrderStatus = 'PENDING_PAY' | 'MAKING' | 'READY' | 'COMPLETED' | 'CANCELLED'

/** 取餐方式 */
export type PickupType = 'SELF_PICKUP' | 'DINE_IN'

/** 订单明细 */
export interface AdminOrderItem {
  productId: number
  productName: string
  specText: string
  quantity: number
  unitPrice: number
  subtotalAmount: number
}

/** 后台订单 */
export interface AdminOrder {
  orderId: number
  orderNo: string
  orderType: 'NORMAL' | 'SECKILL'
  pickupCode: string | null
  pickupType: PickupType
  status: OrderStatus
  totalAmount: number
  discountAmount: number
  activityDiscountAmount: number
  payAmount: number
  itemCount: number
  remark: string
  nickname: string
  phone: string
  payChannel: string | null
  refundStatus: string
  refundAmount: number
  refundTime: string | null
  activityId: number | null
  activityTitle: string | null
  createTime: string
  payTime: string | null
  cancelTime: string | null
  items: AdminOrderItem[]
}

/** 商品分页条目 */
export interface AdminProduct {
  id: number
  categoryId: number
  categoryName: string
  name: string
  picUrl: string
  price: number
  stock: number
  status: number
}

/** 商品编辑回显 */
export interface AdminProductDetail extends AdminProduct {
  sort: number
  specGroupIds: number[]
}

/** 商品分类 */
export interface AdminCategory {
  id: number
  name: string
}

/** 规格选项 */
export interface AdminSpecOption {
  id: number
  name: string
  extraPrice: number
}

/** 规格组 */
export interface AdminSpecGroup {
  id: number
  name: string
  required: boolean
  multiple: boolean
  options: AdminSpecOption[]
}

/** 新增 / 编辑商品入参，price 单位为分 */
export interface SaveProductPayload {
  categoryId: number
  name: string
  picUrl: string
  price: number
  stock: number
  sort: number
  status: number
  specGroupIds: number[]
}

/** 热销饮品 */
export interface HotProduct {
  productName: string
  totalQuantity: number
}

/** 首页看板数据 */
export interface DashboardSummary {
  todayOrderCount: number
  todayRevenue: number
  todayReadyCount: number
  todayPendingCount: number
  hotProducts: HotProduct[]
  recentOrders: AdminOrder[]
}

/** 活动类型 */
export type ActivityType = 'SECKILL' | 'FULL_REDUCTION'

/** 活动实时状态 */
export type ActivityStatus = 'NOT_STARTED' | 'RUNNING' | 'ENDED' | 'CLOSED'

/** 后台活动列表条目 */
export interface AdminActivity {
  id: number
  title: string
  type: ActivityType
  startTime: string
  endTime: string
  isOpen: number
  status: ActivityStatus
  fullAmount: number
  reduceAmount: number
  remark: string
  productCount: number
  totalStock: number
  soldCount: number
  preheatRequired: boolean
  createTime: string
}

/** 后台活动商品绑定 */
export interface AdminActivityProduct {
  bindingId: number
  productId: number
  productName: string
  productPic: string
  productPrice: number
  productStatus: number
  activityPrice: number
  activityStock: number
  soldCount: number
  limitPerUser: number
  status: number
  sort: number
  redisStock: number | null
  preheated: boolean
}

/** 后台活动详情 */
export interface AdminActivityDetail {
  id: number
  title: string
  type: ActivityType
  startTime: string
  endTime: string
  isOpen: number
  status: ActivityStatus
  fullAmount: number
  reduceAmount: number
  remark: string
  allPreheated: boolean
  products: AdminActivityProduct[]
}

/** 新增 / 编辑活动入参，金额单位为分 */
export interface SaveActivityPayload {
  title: string
  type: ActivityType
  startTime: string
  endTime: string
  fullAmount: number
  reduceAmount: number
  remark: string
}

/** 绑定活动商品入参 */
export interface BindActivityProductPayload {
  productId: number
  activityPrice: number
  activityStock: number
  limitPerUser: number
}

/** 修改活动商品配置入参 */
export interface UpdateActivityProductPayload {
  activityPrice: number
  activityStock: number
  limitPerUser: number
}
