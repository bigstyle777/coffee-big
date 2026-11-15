import { deleteData, getData, postData, putData } from './http'
import type {
  ActivityStatus,
  ActivityType,
  AdminActivity,
  AdminActivityDetail,
  AdminCategory,
  AdminInfo,
  AdminLoginResult,
  AdminOrder,
  AdminProduct,
  AdminProductDetail,
  AdminSpecGroup,
  BindActivityProductPayload,
  DashboardSummary,
  OrderStatus,
  PageResult,
  SaveActivityPayload,
  SaveProductPayload,
  UpdateActivityProductPayload,
} from '@/types'

/** 后台登录 */
export function loginAdmin(payload: { username: string; password: string }): Promise<AdminLoginResult> {
  return postData<AdminLoginResult>('/admin/auth/login', payload)
}

/** 退出登录 */
export function logoutAdmin(): Promise<void> {
  return postData<void>('/admin/auth/logout')
}

/** 当前店长信息 */
export function getAdminProfile(): Promise<AdminInfo> {
  return getData<AdminInfo>('/admin/auth/profile')
}

/** 首页看板 */
export function getDashboardSummary(): Promise<DashboardSummary> {
  return getData<DashboardSummary>('/admin/dashboard/summary')
}

/** 商品分页 */
export function pageProducts(query: {
  pageNum: number
  pageSize: number
  keyword?: string
  categoryId?: number
  status?: number
}): Promise<PageResult<AdminProduct>> {
  return getData<PageResult<AdminProduct>>('/admin/product/page', { ...query })
}

/** 商品编辑回显 */
export function getProductDetail(productId: number): Promise<AdminProductDetail> {
  return getData<AdminProductDetail>(`/admin/product/${productId}`)
}

/** 新增商品 */
export function createProduct(payload: SaveProductPayload): Promise<number> {
  return postData<number>('/admin/product', payload)
}

/** 编辑商品 */
export function updateProduct(productId: number, payload: SaveProductPayload): Promise<void> {
  return putData<void>(`/admin/product/${productId}`, payload)
}

/** 删除商品（软删） */
export function deleteProduct(productId: number): Promise<void> {
  return deleteData<void>(`/admin/product/${productId}`)
}

/** 上架 / 下架 */
export function updateProductStatus(productId: number, status: number): Promise<void> {
  return putData<void>('/admin/product/status', { productId, status })
}

/** 分类下拉项 */
export function listCategories(): Promise<AdminCategory[]> {
  return getData<AdminCategory[]>('/admin/category/list')
}

/** 规格组与选项 */
export function listSpecGroups(): Promise<AdminSpecGroup[]> {
  return getData<AdminSpecGroup[]>('/admin/spec-group/list')
}

/** 上传商品图片 */
export function uploadImage(file: File): Promise<{ url: string }> {
  const formData = new FormData()
  formData.append('file', file)
  return postData<{ url: string }>('/admin/upload/image', formData)
}

/** 订单分页 */
export function pageOrders(query: {
  pageNum: number
  pageSize: number
  status?: OrderStatus
  startTime?: string
  endTime?: string
  phone?: string
  keyword?: string
}): Promise<PageResult<AdminOrder>> {
  return getData<PageResult<AdminOrder>>('/admin/order/page', { ...query })
}

/** 订单详情 */
export function getOrderDetail(orderId: number): Promise<AdminOrder> {
  return getData<AdminOrder>(`/admin/order/${orderId}`)
}

/** 状态流转：READY / COMPLETED */
export function updateOrderStatus(orderId: number, status: OrderStatus): Promise<void> {
  return putData<void>('/admin/order/status', { orderId, status })
}

/** 取消订单 */
export function cancelOrder(orderId: number, reason: string): Promise<void> {
  return postData<void>(`/admin/order/${orderId}/cancel`, { reason })
}

/** 商品全部列表，绑定活动商品时使用 */
export function listAllProducts(): Promise<AdminProduct[]> {
  return getData<AdminProduct[]>('/admin/product/list')
}

/** 活动分页 */
export function pageActivities(query: {
  pageNum: number
  pageSize: number
  keyword?: string
  type?: ActivityType
  status?: ActivityStatus
  isOpen?: number
}): Promise<PageResult<AdminActivity>> {
  return getData<PageResult<AdminActivity>>('/admin/activity/page', { ...query })
}

/** 活动详情 */
export function getActivityDetail(activityId: number): Promise<AdminActivityDetail> {
  return getData<AdminActivityDetail>(`/admin/activity/${activityId}`)
}

/** 新建活动 */
export function createActivity(payload: SaveActivityPayload): Promise<number> {
  return postData<number>('/admin/activity', payload)
}

/** 编辑活动 */
export function updateActivity(activityId: number, payload: SaveActivityPayload): Promise<void> {
  return putData<void>(`/admin/activity/${activityId}`, payload)
}

/** 开启 / 关闭活动 */
export function updateActivityOpen(activityId: number, isOpen: boolean): Promise<void> {
  return putData<void>(`/admin/activity/${activityId}/open`, { isOpen })
}

/** 绑定活动商品 */
export function bindActivityProduct(
  activityId: number,
  payload: BindActivityProductPayload,
): Promise<void> {
  return postData<void>(`/admin/activity/${activityId}/products`, payload)
}

/** 修改活动商品配置 */
export function updateActivityProduct(
  activityId: number,
  bindingId: number,
  payload: UpdateActivityProductPayload,
): Promise<void> {
  return putData<void>(`/admin/activity/${activityId}/products/${bindingId}`, payload)
}

/** 停用活动商品绑定 */
export function disableActivityProduct(activityId: number, bindingId: number): Promise<void> {
  return deleteData<void>(`/admin/activity/${activityId}/products/${bindingId}`)
}

/** 一键预热秒杀库存 */
export function preheatActivity(activityId: number): Promise<AdminActivityDetail> {
  return postData<AdminActivityDetail>(`/admin/activity/${activityId}/preheat`)
}

/** 查询活动预热状态 */
export function getActivityPreheatStatus(activityId: number): Promise<AdminActivityDetail> {
  return getData<AdminActivityDetail>(`/admin/activity/${activityId}/preheat-status`)
}
