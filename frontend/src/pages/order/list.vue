<script setup lang="ts">
import { ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import type { OrderDetail, OrderStatus } from '@/types/api'
import { request } from '@/utils/request'
import { fenToYuan } from '@/utils/money'
import { formatDateTime, orderStatusText, pickupTypeText } from '@/utils/order'

interface OrderTab {
  label: string
  status: OrderStatus | ''
}

const tabs: OrderTab[] = [
  { label: '全部', status: '' },
  { label: '待支付', status: 'PENDING_PAY' },
  { label: '制作中', status: 'MAKING' },
  { label: '待取餐', status: 'READY' },
  { label: '已完成', status: 'COMPLETED' },
]

const activeStatus = ref<OrderStatus | ''>('')
const orders = ref<OrderDetail[]>([])
const loading = ref(false)

async function loadOrders(): Promise<void> {
  loading.value = true
  try {
    const data = await request<OrderDetail[]>({
      url: '/order/list',
      data: activeStatus.value ? { status: activeStatus.value } : {},
    })
    orders.value = data || []
  } catch (error) {
    orders.value = []
  } finally {
    loading.value = false
  }
}

function switchTab(status: OrderStatus | ''): void {
  if (status === activeStatus.value) {
    return
  }
  activeStatus.value = status
  loadOrders()
}

function goDetail(order: OrderDetail): void {
  uni.navigateTo({ url: `/pages/order/detail?id=${order.orderId}` })
}

function goMenu(): void {
  uni.reLaunch({ url: '/pages/menu/index' })
}

function orderSummary(order: OrderDetail): string {
  const first = order.items[0]?.productName || '咖啡'
  return order.itemCount > 1 ? `${first} 等 ${order.itemCount} 件` : first
}

onShow(() => {
  loadOrders()
})

onPullDownRefresh(async () => {
  await loadOrders()
  uni.stopPullDownRefresh()
})
</script>

<template>
  <view class="order-list-page">
    <view class="tab-bar">
      <view
        v-for="tab in tabs"
        :key="tab.label"
        :class="['tab-item', activeStatus === tab.status && 'is-active']"
        @click="switchTab(tab.status)"
      >
        {{ tab.label }}
      </view>
    </view>

    <view v-if="loading && !orders.length" class="state-block">
      <view class="state-title">正在查询订单…</view>
    </view>

    <view v-else-if="!orders.length" class="state-block">
      <view class="state-title">暂无订单</view>
      <view class="state-desc">下拉刷新，或者先去点一杯吧</view>
      <view class="empty-btn" @click="goMenu">去点单</view>
    </view>

    <view v-for="order in orders" :key="order.orderId" class="order-card" @click="goDetail(order)">
      <view class="card-head">
        <view class="head-left">
          <text class="order-time">{{ formatDateTime(order.createTime) }}</text>
          <view class="order-no-row">
            <text class="order-no">{{ order.orderNo }}</text>
            <text v-if="order.orderType === 'SECKILL'" class="seckill-mini">秒杀</text>
          </view>
        </view>
        <text :class="['status-tag', `is-${order.status.toLowerCase()}`]">
          {{ orderStatusText(order.status) }}
        </text>
      </view>

      <view class="card-body">
        <view v-if="order.pickupCode" class="pickup-code">
          <text class="pickup-label">取餐码</text>
          <text class="pickup-value">{{ order.pickupCode }}</text>
        </view>
        <view class="summary">
          <text class="summary-text">{{ orderSummary(order) }}</text>
          <text class="summary-meta">
            共 {{ order.itemCount }} 杯 · {{ pickupTypeText(order.pickupType) }}
          </text>
        </view>
      </view>

      <view class="card-foot">
        <text class="foot-amount">¥{{ fenToYuan(order.payAmount) }}</text>
        <text class="foot-link">查看详情 ›</text>
      </view>
    </view>

    <view v-if="orders.length" class="list-end">— 仅展示最近 50 笔订单 —</view>
  </view>
</template>

<style lang="scss" scoped>
.order-list-page {
  min-height: 100vh;
  padding: 20rpx 24rpx 60rpx;
  background-color: $coffee-bg;

  /* #ifdef H5 */
  min-height: calc(100vh - var(--window-top));
  min-height: calc(100dvh - var(--window-top));
  /* #endif */
}

.tab-bar {
  display: flex;
  padding: 6rpx;
  margin-bottom: 8rpx;
  border-radius: 999rpx;
  background-color: $coffee-surface;
}

.tab-item {
  flex: 1;
  padding: 14rpx 0;
  border-radius: 999rpx;
  text-align: center;
  font-size: 24rpx;
  color: $coffee-muted;

  &.is-active {
    background-color: $coffee-primary;
    color: #fff;
    font-weight: 600;
  }
}

.state-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 160rpx 0;
}

.state-title {
  font-size: 28rpx;
  color: $coffee-text;
}

.state-desc {
  margin-top: 10rpx;
  font-size: 22rpx;
  color: $coffee-muted;
}

.empty-btn {
  margin-top: 36rpx;
  padding: 18rpx 64rpx;
  border-radius: 999rpx;
  background-color: $coffee-primary;
  color: #fff;
  font-size: 26rpx;
}

.order-card {
  margin-top: 20rpx;
  padding: 24rpx;
  border-radius: 28rpx;
  background-color: $coffee-card;
  box-shadow: 0 8rpx 24rpx rgba(60, 42, 33, 0.05);
}

.card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.head-left {
  display: flex;
  flex-direction: column;
}

.order-time {
  font-size: 24rpx;
  color: $coffee-text;
}

.order-no {
  margin-top: 4rpx;
  font-size: 20rpx;
  color: $coffee-muted;
}

.order-no-row {
  display: flex;
  align-items: center;
  gap: 10rpx;
}

.seckill-mini {
  margin-top: 4rpx;
  padding: 2rpx 10rpx;
  border-radius: 999rpx;
  background-color: rgba(180, 87, 63, 0.12);
  color: #b4573f;
  font-size: 18rpx;
  font-weight: 600;
}

.status-tag {
  padding: 4rpx 18rpx;
  border-radius: 999rpx;
  font-size: 20rpx;

  &.is-pending_pay {
    background-color: rgba(180, 87, 63, 0.1);
    color: $coffee-danger;
  }

  &.is-making {
    background-color: rgba(213, 206, 163, 0.55);
    color: #7a6a3f;
  }

  &.is-ready {
    background-color: rgba(79, 122, 91, 0.12);
    color: $coffee-success;
  }

  &.is-completed,
  &.is-cancelled {
    background-color: rgba(60, 42, 33, 0.06);
    color: $coffee-muted;
  }
}

.card-body {
  display: flex;
  align-items: center;
  margin-top: 20rpx;
  padding: 20rpx;
  border-radius: 20rpx;
  background-color: $coffee-bg;
}

.pickup-code {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-right: 24rpx;
  border-right: 2rpx dashed $coffee-line;
}

.pickup-label {
  font-size: 20rpx;
  color: $coffee-muted;
}

.pickup-value {
  margin-top: 2rpx;
  font-size: 40rpx;
  font-weight: 700;
  color: $coffee-primary;
  line-height: 1.1;
}

.summary {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
  padding-left: 24rpx;
}

.summary-text {
  font-size: 26rpx;
  color: $coffee-text;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.summary-meta {
  margin-top: 6rpx;
  font-size: 22rpx;
  color: $coffee-muted;
}

.card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 20rpx;
}

.foot-amount {
  font-size: 32rpx;
  font-weight: 700;
  color: $coffee-primary;
}

.foot-link {
  font-size: 22rpx;
  color: $coffee-muted;
}

.list-end {
  margin-top: 32rpx;
  text-align: center;
  font-size: 20rpx;
  color: rgba(140, 123, 107, 0.6);
}
</style>
