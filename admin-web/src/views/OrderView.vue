<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { cancelOrder, getOrderDetail, pageOrders, updateOrderStatus } from '@/api/admin'
import type { AdminOrder, OrderStatus } from '@/types'
import {
  formatDateTime,
  formatMoney,
  ORDER_STATUS_META,
  orderItemsSummary,
  pickupTypeLabel,
} from '@/utils/format'

const loading = ref(false)
const orders = ref<AdminOrder[]>([])
const total = ref(0)
const timeRange = ref<string[]>([])

const query = reactive<{
  pageNum: number
  pageSize: number
  status?: OrderStatus
  phone: string
  keyword: string
}>({
  pageNum: 1,
  pageSize: 10,
  status: undefined,
  phone: '',
  keyword: '',
})

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<AdminOrder | null>(null)

const cancelVisible = ref(false)
const cancelTarget = ref<AdminOrder | null>(null)
const cancelReason = ref('')
const cancelling = ref(false)

function statusMeta(status: OrderStatus) {
  return ORDER_STATUS_META[status] ?? { label: status, type: 'info' as const }
}

function payChannelLabel(channel: string | null): string {
  if (channel === 'WECHAT') {
    return '微信支付'
  }
  if (channel === 'MOCK') {
    return '模拟支付'
  }
  return '未支付'
}

function refundStatusLabel(status: string): string {
  const labels: Record<string, string> = {
    NONE: '无',
    REFUNDING: '退款中',
    REFUNDED: '已退款',
    FAILED: '退款失败',
  }
  return labels[status] || status
}

async function loadOrders(): Promise<void> {
  loading.value = true
  try {
    const result = await pageOrders({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      status: query.status,
      phone: query.phone.trim() || undefined,
      keyword: query.keyword.trim() || undefined,
      startTime: timeRange.value.length === 2 ? timeRange.value[0] : undefined,
      endTime: timeRange.value.length === 2 ? timeRange.value[1] : undefined,
    })
    orders.value = result.records
    total.value = result.total
  } catch {
    // 错误提示由请求层统一处理
  } finally {
    loading.value = false
  }
}

function handleSearch(): void {
  query.pageNum = 1
  loadOrders()
}

function handleReset(): void {
  query.status = undefined
  query.phone = ''
  query.keyword = ''
  timeRange.value = []
  query.pageNum = 1
  loadOrders()
}

function handleSizeChange(): void {
  query.pageNum = 1
  loadOrders()
}

async function openDetail(row: AdminOrder): Promise<void> {
  detail.value = row
  detailVisible.value = true
  detailLoading.value = true
  try {
    detail.value = await getOrderDetail(row.orderId)
  } catch {
    // 错误提示由请求层统一处理
  } finally {
    detailLoading.value = false
  }
}

async function handleMarkReady(row: AdminOrder): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确认「${row.pickupCode || row.orderNo}」已制作完成，可以叫号取餐？`,
      '标记出餐',
      { confirmButtonText: '确认出餐', cancelButtonText: '取消', type: 'info' },
    )
  } catch {
    return
  }
  try {
    await updateOrderStatus(row.orderId, 'READY')
    ElMessage.success('已标记为待取餐')
    detailVisible.value = false
    await loadOrders()
  } catch {
    // 错误提示由请求层统一处理
  }
}

async function handleComplete(row: AdminOrder): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确认顾客已取走「${row.pickupCode || row.orderNo}」？`,
      '完成取餐',
      { confirmButtonText: '确认完成', cancelButtonText: '取消', type: 'info' },
    )
  } catch {
    return
  }
  try {
    await updateOrderStatus(row.orderId, 'COMPLETED')
    ElMessage.success('订单已完成')
    detailVisible.value = false
    await loadOrders()
  } catch {
    // 错误提示由请求层统一处理
  }
}

function openCancel(row: AdminOrder): void {
  cancelTarget.value = row
  cancelReason.value = ''
  cancelVisible.value = true
}

async function submitCancel(): Promise<void> {
  if (!cancelTarget.value) {
    return
  }
  cancelling.value = true
  try {
    await cancelOrder(cancelTarget.value.orderId, cancelReason.value.trim())
    ElMessage.success('订单已取消，退款待后续支付接口处理')
    cancelVisible.value = false
    detailVisible.value = false
    await loadOrders()
  } catch {
    // 错误提示由请求层统一处理
  } finally {
    cancelling.value = false
  }
}

onMounted(loadOrders)
</script>

<template>
  <div class="order-page">
    <div class="page-head">
      <div>
        <h2>订单管理</h2>
        <p class="muted">待制作订单来自顾客支付成功，出餐后顾客即可凭取餐码取餐。</p>
      </div>
      <el-button :icon="'Refresh'" @click="loadOrders">刷新</el-button>
    </div>

    <section class="page-card">
      <div class="filter-bar">
        <el-date-picker
          v-model="timeRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="下单开始时间"
          end-placeholder="下单结束时间"
          value-format="YYYY-MM-DD HH:mm:ss"
          class="filter-range"
        />
        <el-select
          v-model="query.status"
          placeholder="全部状态"
          clearable
          class="filter-select"
          @change="handleSearch"
        >
          <el-option
            v-for="(meta, status) in ORDER_STATUS_META"
            :key="status"
            :label="meta.label"
            :value="status"
          />
        </el-select>
        <el-input
          v-model="query.phone"
          placeholder="顾客手机号"
          clearable
          class="filter-phone"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-input
          v-model="query.keyword"
          placeholder="订单号 / 取餐码"
          clearable
          class="filter-keyword"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <div class="filter-actions">
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="orders" empty-text="暂无订单" style="width: 100%">
        <el-table-column label="订单 / 取餐码" min-width="200">
          <template #default="{ row }">
            <div class="order-no-row">
              <span class="order-no">{{ row.orderNo }}</span>
              <el-tag v-if="row.orderType === 'SECKILL'" type="danger" size="small">秒杀</el-tag>
              <el-tag v-if="row.refundStatus === 'REFUNDING'" type="warning" size="small">退款中</el-tag>
              <el-tag v-else-if="row.refundStatus === 'REFUNDED'" type="info" size="small">已退款</el-tag>
            </div>
            <div class="pickup-code">{{ row.pickupCode || '未生成取餐码' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="顾客" min-width="130">
          <template #default="{ row }">
            <div>{{ row.nickname || '微信顾客' }}</div>
            <div class="muted small">{{ row.phone || '未填写手机号' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="商品" min-width="190">
          <template #default="{ row }">
            <span class="muted">{{ orderItemsSummary(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="实付" width="104" align="right">
          <template #default="{ row }">
            <span class="money">{{ formatMoney(row.payAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusMeta(row.status).type" effect="light" round>
              {{ statusMeta(row.status).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="下单时间" width="166">
          <template #default="{ row }">
            <span class="muted">{{ formatDateTime(row.createTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190" align="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button
              v-if="row.status === 'MAKING'"
              link
              type="success"
              @click="handleMarkReady(row)"
            >
              出餐
            </el-button>
            <el-button
              v-if="row.status === 'READY'"
              link
              type="success"
              @click="handleComplete(row)"
            >
              完成取餐
            </el-button>
            <el-button
              v-if="['PENDING_PAY', 'MAKING', 'READY'].includes(row.status)"
              link
              type="danger"
              @click="openCancel(row)"
            >
              取消
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="loadOrders"
          @size-change="handleSizeChange"
        />
      </div>
    </section>

    <el-dialog v-model="detailVisible" title="订单详情" width="760px">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <div class="detail-head">
            <div>
              <div class="detail-order-no">{{ detail.orderNo }}</div>
              <div class="muted small">
                {{ pickupTypeLabel(detail.pickupType) }} ·
                下单于 {{ formatDateTime(detail.createTime) }}
              </div>
            </div>
            <el-tag :type="statusMeta(detail.status).type" effect="dark" round size="large">
              {{ statusMeta(detail.status).label }}
            </el-tag>
          </div>

          <el-descriptions :column="3" border class="detail-desc">
            <el-descriptions-item label="取餐码">
              <span class="pickup-strong">{{ detail.pickupCode || '—' }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="顾客">{{ detail.nickname || '微信顾客' }}</el-descriptions-item>
            <el-descriptions-item label="手机号">{{ detail.phone || '未填写' }}</el-descriptions-item>
            <el-descriptions-item label="支付渠道">
              {{ payChannelLabel(detail.payChannel) }}
            </el-descriptions-item>
            <el-descriptions-item v-if="detail.refundStatus !== 'NONE'" label="退款状态">
              {{ refundStatusLabel(detail.refundStatus) }}
            </el-descriptions-item>
            <el-descriptions-item v-if="detail.refundStatus !== 'NONE'" label="退款金额">
              {{ formatMoney(detail.refundAmount) }}
            </el-descriptions-item>
            <el-descriptions-item v-if="detail.refundTime" label="退款时间">
              {{ formatDateTime(detail.refundTime) }}
            </el-descriptions-item>
            <el-descriptions-item label="订单类型">
              {{ detail.orderType === 'SECKILL' ? '秒杀订单' : '普通订单' }}
            </el-descriptions-item>
            <el-descriptions-item label="活动">
              {{ detail.activityTitle || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="活动优惠">
              {{ formatMoney(detail.activityDiscountAmount) }}
            </el-descriptions-item>
            <el-descriptions-item label="商品总额">
              {{ formatMoney(detail.totalAmount) }}
            </el-descriptions-item>
            <el-descriptions-item label="优惠">
              {{ formatMoney(detail.discountAmount) }}
            </el-descriptions-item>
            <el-descriptions-item label="实付">
              <span class="money">{{ formatMoney(detail.payAmount) }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="支付时间">
              {{ formatDateTime(detail.payTime) }}
            </el-descriptions-item>
            <el-descriptions-item label="取消时间">
              {{ formatDateTime(detail.cancelTime) }}
            </el-descriptions-item>
            <el-descriptions-item label="备注">
              {{ detail.remark || '无' }}
            </el-descriptions-item>
          </el-descriptions>

          <el-table :data="detail.items" size="small" class="detail-items">
            <el-table-column prop="productName" label="商品" min-width="140" />
            <el-table-column label="规格" min-width="150">
              <template #default="{ row }">
                <span class="muted">{{ row.specText || '默认规格' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="单价" width="96" align="right">
              <template #default="{ row }">{{ formatMoney(row.unitPrice) }}</template>
            </el-table-column>
            <el-table-column prop="quantity" label="数量" width="72" align="center" />
            <el-table-column label="小计" width="96" align="right">
              <template #default="{ row }">
                <span class="money">{{ formatMoney(row.subtotalAmount) }}</span>
              </template>
            </el-table-column>
          </el-table>
        </template>
      </div>

      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button
          v-if="detail && detail.status === 'MAKING'"
          type="success"
          @click="handleMarkReady(detail)"
        >
          标记出餐
        </el-button>
        <el-button
          v-if="detail && detail.status === 'READY'"
          type="success"
          @click="handleComplete(detail)"
        >
          完成取餐
        </el-button>
        <el-button
          v-if="detail && ['PENDING_PAY', 'MAKING', 'READY'].includes(detail.status)"
          type="danger"
          plain
          @click="openCancel(detail)"
        >
          取消订单
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="cancelVisible" title="取消订单" width="440px">
      <p class="cancel-tip">
        将订单
        <strong>{{ cancelTarget?.pickupCode || cancelTarget?.orderNo }}</strong>
        标记为已取消，并回滚库存与优惠券。已支付订单会记录「退款待处理」。
      </p>
      <el-input
        v-model="cancelReason"
        type="textarea"
        :rows="3"
        maxlength="200"
        show-word-limit
        placeholder="请输入取消原因，如 顾客要求取消 / 原料缺货"
      />
      <template #footer>
        <el-button @click="cancelVisible = false">再想想</el-button>
        <el-button type="danger" :loading="cancelling" @click="submitCancel">
          确认取消订单
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.order-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
}

.page-head h2 {
  margin: 0 0 6px;
  font-size: 22px;
  font-weight: 600;
  letter-spacing: 0.02em;
}

.page-head p {
  margin: 0;
  font-size: 13px;
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  padding: 18px 22px;
  border-bottom: 1px solid var(--coffee-line);
}

.filter-range {
  width: 360px;
}

.filter-select {
  width: 132px;
}

.filter-phone {
  width: 150px;
}

.filter-keyword {
  width: 190px;
}

.filter-actions {
  display: flex;
  gap: 8px;
  margin-left: auto;
}

.order-no {
  font-size: 13px;
  font-weight: 500;
  letter-spacing: 0.01em;
}

.order-no-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.pickup-code {
  margin-top: 3px;
  font-size: 12px;
  color: var(--coffee-muted);
}

.small {
  font-size: 12px;
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  padding: 16px 22px 20px;
}

.detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 16px;
  margin-bottom: 16px;
  border-bottom: 1px solid var(--coffee-line);
}

.detail-order-no {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 5px;
}

.detail-desc {
  margin-bottom: 18px;
}

.pickup-strong {
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: #7a5a3a;
}

.detail-items {
  margin-top: 4px;
}

.cancel-tip {
  margin: 0 0 14px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--coffee-muted);
}

.cancel-tip strong {
  color: var(--coffee-text);
}
</style>
