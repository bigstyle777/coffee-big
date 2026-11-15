<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import type { OrderDetail, PaymentPrepayResult } from '@/types/api'
import { request } from '@/utils/request'
import { fenToYuan } from '@/utils/money'
import { formatDateTime, orderStatusText, pickupTypeText } from '@/utils/order'

const orderId = ref(0)
const order = ref<OrderDetail | null>(null)
const loading = ref(true)
const loadError = ref('')
const paying = ref(false)
const cancelling = ref(false)
const autoPay = ref(false)
const autoPaid = ref(false)

let firstShow = true

const statusHintMap: Record<string, string> = {
  PENDING_PAY: '订单已提交，请完成支付',
  MAKING: '咖啡师正在制作，请稍候',
  READY: '已出杯，请凭取餐码取餐',
  COMPLETED: '订单已完成，期待下次光临',
  CANCELLED: '订单已取消，可重新下单',
}

const statusHint = computed(() => (order.value ? statusHintMap[order.value.status] || '' : ''))
const isCancelled = computed(() => order.value?.status === 'CANCELLED')
const canPay = computed(() => order.value?.status === 'PENDING_PAY')
const canCancel = computed(() => order.value?.status === 'PENDING_PAY')

const steps = ['已下单', '制作中', '待取餐', '已完成']
const stepIndex = computed(() => {
  switch (order.value?.status) {
    case 'PENDING_PAY':
      return 0
    case 'MAKING':
      return 1
    case 'READY':
      return 2
    case 'COMPLETED':
      return 3
    default:
      return 0
  }
})

const showPickupCode = computed(() =>
  order.value ? ['MAKING', 'READY', 'COMPLETED'].includes(order.value.status) : false,
)
const isSeckillOrder = computed(() => order.value?.orderType === 'SECKILL')
const couponDiscountAmount = computed(() =>
  Math.max(
    (order.value?.discountAmount || 0) - (order.value?.activityDiscountAmount || 0),
    0,
  ),
)

async function loadOrder(): Promise<void> {
  if (!orderId.value) {
    loading.value = false
    loadError.value = '订单不存在'
    return
  }
  loading.value = true
  try {
    order.value = await request<OrderDetail>({ url: `/order/${orderId.value}` })
    loading.value = false
    // 下单后跳转进来，自动触发一次模拟支付
    if (autoPay.value && !autoPaid.value && order.value.status === 'PENDING_PAY') {
      autoPaid.value = true
      await payOrder()
    }
  } catch (error) {
    loading.value = false
    loadError.value = '订单加载失败，点击重试'
  }
}

/** 统一支付：先向服务端申请预支付参数，再按渠道调起模拟支付或微信支付 */
async function payOrder(): Promise<void> {
  if (!order.value || paying.value) {
    return
  }
  paying.value = true
  try {
    const prepay = await request<PaymentPrepayResult>({
      url: '/pay/prepay',
      method: 'POST',
      data: { orderId: order.value.orderId },
    })
    if (prepay.provider === 'WECHAT') {
      await requestWechatPayment(prepay)
    } else {
      if (!prepay.prepayId) {
        throw new Error('未获取到预支付标识')
      }
      await request<null>({
        url: '/pay/mock/confirm',
        method: 'POST',
        data: {
          orderId: order.value.orderId,
          prepayId: prepay.prepayId,
        },
      })
    }
    uni.showToast({ title: '支付成功，咖啡制作中', icon: 'none' })
    await loadOrder()
  } catch (error) {
    // 支付取消或失败时保持待支付，错误提示由 request / requestPayment 处理
  } finally {
    paying.value = false
  }
}

/** 微信小程序调起支付 */
function requestWechatPayment(prepay: PaymentPrepayResult): Promise<void> {
  return new Promise<void>((resolve, reject) => {
    uni.requestPayment({
      provider: 'wxpay',
      timeStamp: prepay.timeStamp || '',
      nonceStr: prepay.nonceStr || '',
      package: prepay.packageValue || '',
      signType: 'RSA',
      paySign: prepay.paySign || '',
      success: () => resolve(),
      fail: (error) => {
        uni.showToast({ title: '支付未完成', icon: 'none' })
        reject(new Error(error.errMsg || '支付未完成'))
      },
    })
  })
}

function cancelOrder(): void {
  if (!order.value || cancelling.value) {
    return
  }
  uni.showModal({
    title: '取消订单',
    content: '确定取消这笔未支付订单吗？',
    confirmText: '取消订单',
    confirmColor: '#B4573F',
    success: async (res) => {
      if (!res.confirm || !order.value) {
        return
      }
      cancelling.value = true
      try {
        await request<null>({
          url: `/order/cancel/${order.value.orderId}`,
          method: 'POST',
        })
        uni.showToast({ title: '订单已取消', icon: 'none' })
        await loadOrder()
      } catch (error) {
        // 错误提示已由 request 统一处理
      } finally {
        cancelling.value = false
      }
    },
  })
}

function goBackToMenu(): void {
  uni.reLaunch({ url: '/pages/menu/index' })
}

onLoad((options) => {
  orderId.value = Number(options?.id || 0)
  autoPay.value = options?.autoPay === '1'
  loadOrder()
})

onShow(() => {
  if (firstShow) {
    firstShow = false
    return
  }
  // 从支付 / 其他页面返回时刷新一次状态
  if (orderId.value) {
    loadOrder()
  }
})
</script>

<template>
  <view class="detail-page">
    <view v-if="loading && !order" class="state-block">
      <view class="state-title">正在查询订单…</view>
    </view>

    <view v-else-if="loadError" class="state-block" @click="loadOrder">
      <view class="state-title">{{ loadError }}</view>
      <view class="state-desc">点击重新加载</view>
    </view>

    <template v-else-if="order">
      <view :class="['status-hero', isCancelled && 'is-cancelled']">
        <view class="status-main">
          <text class="status-text">{{ orderStatusText(order.status) }}</text>
          <text class="status-hint">{{ statusHint }}</text>
        </view>
        <view class="refresh-link" @click="loadOrder">刷新状态</view>
      </view>

      <view v-if="!isCancelled" class="step-bar">
        <view
          v-for="(step, index) in steps"
          :key="step"
          :class="['step-item', index <= stepIndex && 'is-done']"
        >
          <view class="step-dot" />
          <text class="step-text">{{ step }}</text>
          <view v-if="index < steps.length - 1" class="step-line" />
        </view>
      </view>

      <view v-if="showPickupCode" class="pickup-card">
        <view class="pickup-left">
          <text class="pickup-label">取餐码</text>
          <text class="pickup-code">{{ order.pickupCode }}</text>
        </view>
        <view class="pickup-right">
          <text class="pickup-tip">
            {{ order.pickupType === 'DINE_IN' ? '堂食订单，请留意送餐' : '请凭取餐码到吧台取餐' }}
          </text>
          <text class="pickup-type">{{ pickupTypeText(order.pickupType) }}</text>
        </view>
      </view>

      <view class="card">
        <view class="card-title">商品明细</view>
        <view v-for="(item, index) in order.items" :key="index" class="item-row">
          <image v-if="item.productPic" class="item-cover" :src="item.productPic" mode="aspectFill" />
          <view v-else class="item-cover item-cover-empty">{{ item.productName.slice(0, 1) }}</view>
          <view class="item-main">
            <view class="item-name-row">
              <text class="item-name">{{ item.productName }}</text>
              <text v-if="item.activityProductId" class="item-seckill">秒杀</text>
            </view>
            <text v-if="item.specText" class="item-spec">{{ item.specText }}</text>
            <view class="item-bottom">
              <text class="item-unit">¥{{ fenToYuan(item.unitPrice) }}</text>
              <text class="item-qty">×{{ item.quantity }}</text>
            </view>
          </view>
          <text class="item-subtotal">¥{{ fenToYuan(item.subtotalAmount) }}</text>
        </view>

        <view class="amount-row">
          <text class="amount-label">商品合计</text>
          <text class="amount-value">¥{{ fenToYuan(order.totalAmount) }}</text>
        </view>
        <view v-if="order.activityDiscountAmount > 0" class="amount-row">
          <text class="amount-label">{{ order.activityTitle || '活动优惠' }}</text>
          <text class="amount-discount">-¥{{ fenToYuan(order.activityDiscountAmount) }}</text>
        </view>
        <view v-if="couponDiscountAmount > 0" class="amount-row">
          <text class="amount-label">优惠券</text>
          <text class="amount-discount">-¥{{ fenToYuan(couponDiscountAmount) }}</text>
        </view>
        <view class="amount-row is-total">
          <text class="amount-label">实付金额</text>
          <text class="amount-total">¥{{ fenToYuan(order.payAmount) }}</text>
        </view>
      </view>

      <view class="card">
        <view class="card-title">订单信息</view>
        <view class="info-row">
          <text class="info-label">订单编号</text>
          <text class="info-value">{{ order.orderNo }}</text>
        </view>
        <view v-if="isSeckillOrder" class="info-row">
          <text class="info-label">订单类型</text>
          <text class="info-value">秒杀订单</text>
        </view>
        <view v-if="order.activityTitle" class="info-row">
          <text class="info-label">活动</text>
          <text class="info-value">{{ order.activityTitle }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">取餐方式</text>
          <text class="info-value">{{ pickupTypeText(order.pickupType) }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">下单时间</text>
          <text class="info-value">{{ formatDateTime(order.createTime) }}</text>
        </view>
        <view v-if="order.payTime" class="info-row">
          <text class="info-label">支付时间</text>
          <text class="info-value">{{ formatDateTime(order.payTime) }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">备注</text>
          <text class="info-value">{{ order.remark || '无' }}</text>
        </view>
      </view>

      <view v-if="isCancelled" class="relaunch-btn" @click="goBackToMenu">重新点单</view>

      <view v-if="canPay || canCancel" class="action-bar">
        <view v-if="canCancel" class="action-plain" @click="cancelOrder">
          {{ cancelling ? '处理中…' : '取消订单' }}
        </view>
        <view v-if="canPay" class="action-primary" @click="payOrder">
          {{ paying ? '支付中…' : '立即支付' }}
        </view>
      </view>
    </template>
  </view>
</template>

<style lang="scss" scoped>
.detail-page {
  min-height: 100vh;
  padding: 24rpx 24rpx 200rpx;
  background-color: $coffee-bg;

  /* #ifdef H5 */
  min-height: calc(100vh - var(--window-top));
  min-height: calc(100dvh - var(--window-top));
  /* #endif */
}

.state-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 200rpx 0;
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

.status-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 36rpx 32rpx;
  border-radius: 28rpx;
  background: linear-gradient(135deg, #4a352a 0%, #3c2a21 100%);
  box-shadow: 0 16rpx 32rpx rgba(60, 42, 33, 0.18);

  &.is-cancelled {
    background: linear-gradient(135deg, #6d5b4e 0%, #55453a 100%);
  }
}

.status-main {
  display: flex;
  flex-direction: column;
}

.status-text {
  font-size: 40rpx;
  font-weight: 700;
  color: $coffee-accent;
}

.status-hint {
  margin-top: 10rpx;
  font-size: 24rpx;
  color: rgba(250, 247, 242, 0.7);
}

.refresh-link {
  padding: 10rpx 24rpx;
  border: 2rpx solid rgba(213, 206, 163, 0.4);
  border-radius: 999rpx;
  font-size: 22rpx;
  color: rgba(250, 247, 242, 0.8);
}

.step-bar {
  display: flex;
  align-items: flex-start;
  padding: 36rpx 24rpx 12rpx;
}

.step-item {
  position: relative;
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
}

.step-dot {
  width: 20rpx;
  height: 20rpx;
  border-radius: 50%;
  background-color: rgba(60, 42, 33, 0.15);
}

.step-text {
  margin-top: 10rpx;
  font-size: 20rpx;
  color: $coffee-muted;
}

.step-line {
  position: absolute;
  top: 8rpx;
  left: 50%;
  width: 100%;
  height: 4rpx;
  background-color: rgba(60, 42, 33, 0.1);
  transform: translateX(50%);
}

.step-item.is-done {
  .step-dot {
    background-color: $coffee-primary;
  }

  .step-text {
    color: $coffee-primary;
    font-weight: 600;
  }
}

.pickup-card {
  display: flex;
  align-items: center;
  margin-top: 24rpx;
  padding: 32rpx;
  border-radius: 28rpx;
  background-color: $coffee-card;
  box-shadow: 0 8rpx 24rpx rgba(60, 42, 33, 0.05);
}

.pickup-left {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 220rpx;
  padding: 16rpx 0;
  border-right: 2rpx dashed $coffee-line;
}

.pickup-label {
  font-size: 22rpx;
  color: $coffee-muted;
}

.pickup-code {
  margin-top: 6rpx;
  font-size: 72rpx;
  font-weight: 700;
  letter-spacing: 4rpx;
  color: $coffee-primary;
  line-height: 1;
}

.pickup-right {
  display: flex;
  flex: 1;
  flex-direction: column;
  padding-left: 32rpx;
}

.pickup-tip {
  font-size: 26rpx;
  color: $coffee-text;
}

.pickup-type {
  margin-top: 10rpx;
  font-size: 22rpx;
  color: $coffee-muted;
}

.card {
  margin-top: 24rpx;
  padding: 28rpx;
  border-radius: 28rpx;
  background-color: $coffee-card;
  box-shadow: 0 8rpx 24rpx rgba(60, 42, 33, 0.05);
}

.card-title {
  padding-bottom: 20rpx;
  font-size: 28rpx;
  font-weight: 600;
  color: $coffee-text;
  border-bottom: 2rpx solid $coffee-line;
}

.item-row {
  display: flex;
  align-items: center;
  padding: 20rpx 0;
  border-bottom: 2rpx solid $coffee-line;
}

.item-cover {
  width: 96rpx;
  height: 96rpx;
  border-radius: 16rpx;
  background-color: $coffee-surface;
  flex-shrink: 0;
}

.item-cover-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 36rpx;
  font-weight: 700;
  color: $coffee-accent;
  background: linear-gradient(135deg, #4a352a 0%, #3c2a21 100%);
}

.item-main {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
  padding-left: 20rpx;
}

.item-name {
  font-size: 28rpx;
  color: $coffee-text;
}

.item-name-row {
  display: flex;
  align-items: center;
  gap: 10rpx;
}

.item-seckill {
  flex-shrink: 0;
  padding: 2rpx 10rpx;
  border-radius: 999rpx;
  background-color: rgba(180, 87, 63, 0.12);
  color: #b4573f;
  font-size: 18rpx;
  font-weight: 600;
}

.item-spec {
  margin-top: 4rpx;
  font-size: 22rpx;
  color: $coffee-muted;
}

.item-bottom {
  display: flex;
  align-items: center;
  margin-top: 6rpx;
}

.item-unit {
  font-size: 22rpx;
  color: $coffee-muted;
}

.item-qty {
  margin-left: 12rpx;
  font-size: 22rpx;
  color: $coffee-muted;
}

.item-subtotal {
  padding-left: 16rpx;
  font-size: 28rpx;
  color: $coffee-primary;
}

.amount-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 20rpx;
}

.amount-label {
  font-size: 24rpx;
  color: $coffee-muted;
}

.amount-value {
  font-size: 26rpx;
  color: $coffee-text;
}

.amount-discount {
  font-size: 26rpx;
  color: #b4573f;
}

.amount-row.is-total {
  .amount-label {
    color: $coffee-text;
  }
}

.amount-total {
  font-size: 36rpx;
  font-weight: 700;
  color: $coffee-primary;
}

.info-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding-top: 20rpx;
}

.info-label {
  flex-shrink: 0;
  font-size: 24rpx;
  color: $coffee-muted;
}

.info-value {
  margin-left: 24rpx;
  font-size: 24rpx;
  color: $coffee-text;
  text-align: right;
  word-break: break-all;
}

.relaunch-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 88rpx;
  margin-top: 32rpx;
  border-radius: 44rpx;
  background-color: $coffee-primary;
  color: #fff;
  font-size: 28rpx;
}

.action-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  padding: 20rpx 24rpx;
  background-color: $coffee-card;
  box-shadow: 0 -8rpx 24rpx rgba(60, 42, 33, 0.06);
  padding-bottom: calc(20rpx + env(safe-area-inset-bottom));
}

.action-plain {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
  height: 84rpx;
  margin-right: 20rpx;
  border: 2rpx solid rgba(60, 42, 33, 0.2);
  border-radius: 42rpx;
  font-size: 28rpx;
  color: $coffee-text;
}

.action-primary {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 2;
  height: 84rpx;
  border-radius: 42rpx;
  background-color: $coffee-primary;
  color: #fff;
  font-size: 28rpx;
  font-weight: 600;
}
</style>
