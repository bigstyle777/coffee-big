<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { onHide, onPullDownRefresh, onShow, onUnload } from '@dcloudio/uni-app'
import type {
  OrderCreateResult,
  SeckillActivity,
  SeckillProduct,
  SpecGroup,
  SpecOption,
} from '@/types/api'
import { request } from '@/utils/request'
import { fenToYuan } from '@/utils/money'
import { formatDateTime } from '@/utils/order'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const activities = ref<SeckillActivity[]>([])
const loading = ref(true)
const loadError = ref('')
const now = ref(Date.now())

let countdownTimer: ReturnType<typeof setInterval> | null = null

async function loadActivities(options: { silent?: boolean } = {}): Promise<void> {
  const silent = options.silent === true
  if (!silent) {
    loading.value = true
    loadError.value = ''
  }
  try {
    activities.value = (await request<SeckillActivity[]>({
      url: '/activity/seckill/list',
      auth: false,
      silent,
    })) || []
    loadError.value = ''
  } catch (error) {
    if (!silent) {
      loadError.value = '秒杀活动加载失败，点击重试'
    }
  } finally {
    if (!silent) {
      loading.value = false
    }
  }
}

function startCountdown(): void {
  stopCountdown()
  now.value = Date.now()
  countdownTimer = setInterval(() => {
    now.value = Date.now()
  }, 1000)
}

function stopCountdown(): void {
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
}

function parseDateTime(value: string): number {
  const normalized = String(value || '').replace('T', ' ')
  const [datePart, timePart = '00:00:00'] = normalized.split(' ')
  const [year, month, day] = datePart.split('-').map(Number)
  const [hour, minute, second] = timePart.split(':').map(Number)
  return new Date(year, (month || 1) - 1, day || 1, hour || 0, minute || 0, second || 0).getTime()
}

function pad2(value: number): string {
  return value < 10 ? `0${value}` : String(value)
}

function countdownText(endTime: string): string {
  const diff = parseDateTime(endTime) - now.value
  if (diff <= 0) {
    return '已结束'
  }
  const totalSeconds = Math.floor(diff / 1000)
  const days = Math.floor(totalSeconds / 86400)
  const hours = Math.floor((totalSeconds % 86400) / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60
  const clock = `${pad2(hours)}:${pad2(minutes)}:${pad2(seconds)}`
  return days > 0 ? `${days}天 ${clock}` : clock
}

function canBuy(product: SeckillProduct): boolean {
  return product.preheated && product.remainingStock > 0
}

function buyText(product: SeckillProduct): string {
  if (!product.preheated) {
    return '准备中'
  }
  if (product.remainingStock <= 0) {
    return '已抢完'
  }
  return '立即抢购'
}

function specHint(product: SeckillProduct): string {
  if (!product.specGroups.length) {
    return '标准规格'
  }
  return product.specGroups.map((group) => group.name).join(' · ')
}

/* ------------------------------ 规格弹窗 ------------------------------ */

const specVisible = ref(false)
const specProduct = ref<SeckillProduct | null>(null)
const specQuantity = ref(1)
const invalidGroupIds = ref<number[]>([])
const selectedMap = reactive<Record<number, number[]>>({})
const buying = ref(false)
const buyRequestId = ref('')

const selectedOptions = computed<SpecOption[]>(() => {
  const product = specProduct.value
  if (!product) {
    return []
  }
  const result: SpecOption[] = []
  product.specGroups.forEach((group) => {
    const selectedIds = selectedMap[group.id] || []
    group.options.forEach((option) => {
      if (selectedIds.includes(option.id)) {
        result.push(option)
      }
    })
  })
  return result
})

const selectedOptionIds = computed<number[]>(() => {
  const product = specProduct.value
  if (!product) {
    return []
  }
  const result: number[] = []
  product.specGroups.forEach((group) => {
    result.push(...(selectedMap[group.id] || []))
  })
  return result
})

/** 单杯价 = 秒杀基础价 + 规格加价 */
const specUnitPrice = computed(() => {
  const basePrice = specProduct.value?.activityPrice || 0
  return selectedOptions.value.reduce((sum, option) => sum + (option.extraPrice || 0), basePrice)
})

const maxQuantity = computed(() => {
  const product = specProduct.value
  if (!product) {
    return 1
  }
  return Math.max(Math.min(product.limitPerUser, product.remainingStock, 99), 1)
})

function newRequestId(): string {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
}

function ensureRequestId(): string {
  if (!buyRequestId.value) {
    buyRequestId.value = newRequestId()
  }
  return buyRequestId.value
}

function resetRequestId(): void {
  buyRequestId.value = ''
}

function openSpec(product: SeckillProduct): void {
  if (!canBuy(product)) {
    uni.showToast({ title: buyText(product), icon: 'none' })
    return
  }
  specProduct.value = product
  specQuantity.value = 1
  invalidGroupIds.value = []
  Object.keys(selectedMap).forEach((key) => {
    delete selectedMap[Number(key)]
  })
  product.specGroups.forEach((group) => {
    selectedMap[group.id] =
      group.required && !group.multiple && group.options.length ? [group.options[0].id] : []
  })
  buyRequestId.value = newRequestId()
  specVisible.value = true
}

function closeSpec(): void {
  if (!buying.value) {
    specVisible.value = false
  }
}

function isSelected(groupId: number, optionId: number): boolean {
  return (selectedMap[groupId] || []).includes(optionId)
}

function toggleOption(group: SpecGroup, option: SpecOption): void {
  const current = selectedMap[group.id] || []
  if (group.multiple) {
    selectedMap[group.id] = current.includes(option.id)
      ? current.filter((id) => id !== option.id)
      : [...current, option.id]
  } else if (current[0] === option.id) {
    if (!group.required) {
      selectedMap[group.id] = []
    }
  } else {
    selectedMap[group.id] = [option.id]
  }
  if ((selectedMap[group.id] || []).length) {
    invalidGroupIds.value = invalidGroupIds.value.filter((id) => id !== group.id)
  }
  resetRequestId()
}

function changeQuantity(delta: number): void {
  const next = specQuantity.value + delta
  if (next >= 1 && next <= maxQuantity.value) {
    specQuantity.value = next
    resetRequestId()
  }
}

async function confirmBuy(): Promise<void> {
  const product = specProduct.value
  if (!product || buying.value) {
    return
  }
  const missingGroups = product.specGroups.filter(
    (group) => group.required && !(selectedMap[group.id] || []).length,
  )
  if (missingGroups.length) {
    invalidGroupIds.value = missingGroups.map((group) => group.id)
    uni.showToast({ title: `请选择${missingGroups[0].name}`, icon: 'none' })
    return
  }
  if (!canBuy(product)) {
    uni.showToast({ title: buyText(product), icon: 'none' })
    return
  }
  if (specQuantity.value > product.limitPerUser) {
    uni.showToast({ title: `每人限购 ${product.limitPerUser} 杯`, icon: 'none' })
    return
  }
  if (specQuantity.value > product.remainingStock) {
    uni.showToast({ title: '库存不足，请减少数量', icon: 'none' })
    return
  }

  invalidGroupIds.value = []
  buying.value = true
  try {
    await userStore.ensureLogin({ silent: false })
    const result = await request<OrderCreateResult>({
      url: '/order/seckill',
      method: 'POST',
      data: {
        activityProductId: product.activityProductId,
        quantity: specQuantity.value,
        optionIds: selectedOptionIds.value,
        pickupType: 'SELF_PICKUP',
        remark: '',
        requestId: ensureRequestId(),
      },
    })
    specVisible.value = false
    uni.showToast({ title: '抢购成功', icon: 'none' })
    uni.navigateTo({ url: `/pages/order/detail?id=${result.orderId}&autoPay=1` })
  } catch (error) {
    // 售罄、限购等业务错误由 request 统一提示，这里刷新库存
    await loadActivities({ silent: true })
    if (specProduct.value) {
      const products: SeckillProduct[] = []
      activities.value.forEach((activity) => {
        products.push(...activity.products)
      })
      const refreshed = products.find(
        (item) => item.activityProductId === specProduct.value?.activityProductId,
      )
      if (refreshed) {
        specProduct.value = refreshed
        specQuantity.value = Math.max(
          Math.min(specQuantity.value, refreshed.remainingStock, refreshed.limitPerUser, 99),
          1,
        )
      }
    }
  } finally {
    buying.value = false
  }
}

function goMenu(): void {
  uni.reLaunch({ url: '/pages/menu/index' })
}

onShow(() => {
  startCountdown()
  loadActivities({ silent: activities.value.length > 0 })
})

onHide(stopCountdown)
onUnload(stopCountdown)

onPullDownRefresh(async () => {
  await loadActivities()
  uni.stopPullDownRefresh()
})
</script>

<template>
  <view class="seckill-page">
    <view class="hero">
      <view class="hero-kicker">LIMITED OFFER</view>
      <text class="hero-title">限时秒杀</text>
      <text class="hero-desc">现磨好咖啡，抢到就是赚到</text>
    </view>

    <view v-if="loading && !activities.length" class="state-block">
      <view class="state-title">正在准备秒杀…</view>
      <view class="state-desc">好咖啡马上开抢</view>
    </view>

    <view v-else-if="loadError" class="state-block" @click="loadActivities()">
      <view class="state-title">{{ loadError }}</view>
      <view class="state-desc">点击重新加载</view>
    </view>

    <view v-else-if="!activities.length" class="state-block">
      <view class="state-title">当前没有进行中的秒杀</view>
      <view class="state-desc">先去点一杯日常咖啡吧</view>
      <view class="empty-btn" @click="goMenu">返回点单</view>
    </view>

    <view
      v-for="activity in activities"
      :key="activity.activityId"
      class="activity-block"
    >
      <view class="activity-head">
        <view class="activity-title-wrap">
          <text class="activity-title">{{ activity.title }}</text>
          <text class="activity-time">{{ formatDateTime(activity.startTime) }} 开始</text>
        </view>
        <view class="countdown">
          <text class="countdown-label">距结束</text>
          <text class="countdown-value">{{ countdownText(activity.endTime) }}</text>
        </view>
      </view>

      <view class="product-grid">
        <view
          v-for="product in activity.products"
          :key="product.activityProductId"
          class="seckill-card"
        >
          <image
            v-if="product.productPic"
            class="product-cover"
            :src="product.productPic"
            mode="aspectFill"
            lazy-load
          />
          <view v-else class="product-cover product-cover-empty">
            {{ product.productName.slice(0, 1) }}
          </view>

          <view class="product-body">
            <text class="product-name">{{ product.productName }}</text>
            <text class="product-hint">{{ specHint(product) }}</text>
            <view class="price-row">
              <view class="price-main">
                <text class="price-symbol">¥</text>
                <text class="price-value">{{ fenToYuan(product.activityPrice) }}</text>
              </view>
              <text class="price-origin">¥{{ fenToYuan(product.originalPrice) }}</text>
            </view>
            <view class="meta-row">
              <text :class="['stock-text', product.remainingStock <= 0 && 'is-empty']">
                剩 {{ product.remainingStock }} 杯
              </text>
              <text class="limit-text">限购 {{ product.limitPerUser }} 杯</text>
            </view>
            <view
              :class="['buy-btn', !canBuy(product) && 'is-disabled']"
              @click="openSpec(product)"
            >
              {{ buyText(product) }}
            </view>
          </view>
        </view>
      </view>
    </view>

    <view v-if="activities.length" class="page-footer">
      秒杀商品数量有限，以支付成功为准
    </view>

    <!-- 规格选择弹窗 -->
    <view v-if="specVisible && specProduct" class="sheet-mask" @click="closeSpec">
      <view class="sheet" @click.stop>
        <view class="sheet-close" @click="closeSpec">×</view>
        <view class="sheet-product">
          <image
            v-if="specProduct.productPic"
            class="sheet-cover"
            :src="specProduct.productPic"
            mode="aspectFill"
          />
          <view v-else class="sheet-cover sheet-cover-empty">
            {{ specProduct.productName.slice(0, 1) }}
          </view>
          <view class="sheet-info">
            <text class="sheet-name">{{ specProduct.productName }}</text>
            <view class="sheet-price-row">
              <text class="sheet-price">¥{{ fenToYuan(specUnitPrice) }}</text>
              <text class="sheet-origin">¥{{ fenToYuan(specProduct.originalPrice) }}</text>
            </view>
            <text class="sheet-limit">每人限购 {{ specProduct.limitPerUser }} 杯</text>
          </view>
        </view>

        <scroll-view class="sheet-scroll" scroll-y :show-scrollbar="false">
          <view
            v-for="group in specProduct.specGroups"
            :key="group.id"
            :class="['spec-group', invalidGroupIds.includes(group.id) && 'is-invalid']"
          >
            <view class="spec-group-head">
              <text class="spec-group-name">{{ group.name }}</text>
              <text v-if="group.required" class="spec-tag">必选</text>
              <text v-if="group.multiple" class="spec-tag is-light">可多选</text>
            </view>
            <text v-if="invalidGroupIds.includes(group.id)" class="spec-error">
              请选择{{ group.name }}
            </text>
            <view class="spec-options">
              <view
                v-for="option in group.options"
                :key="option.id"
                :class="['spec-option', isSelected(group.id, option.id) && 'is-selected']"
                @click="toggleOption(group, option)"
              >
                <text>{{ option.name }}</text>
                <text v-if="option.extraPrice > 0" class="option-extra">
                  +{{ fenToYuan(option.extraPrice) }}
                </text>
              </view>
            </view>
          </view>
        </scroll-view>

        <view class="sheet-footer">
          <view class="qty">
            <view class="qty-btn" @click="changeQuantity(-1)">−</view>
            <text class="qty-value">{{ specQuantity }}</text>
            <view class="qty-btn" @click="changeQuantity(1)">+</view>
          </view>
          <view class="buy-now-btn" @click="confirmBuy">
            {{ buying ? '抢购中…' : '立即抢购' }}
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.seckill-page {
  min-height: 100vh;
  padding-bottom: 60rpx;
  background-color: $coffee-bg;

  /* #ifdef H5 */
  min-height: calc(100vh - var(--window-top));
  min-height: calc(100dvh - var(--window-top));
  /* #endif */
}

.hero {
  position: relative;
  display: flex;
  flex-direction: column;
  padding: 52rpx 40rpx 64rpx;
  background: radial-gradient(circle at 85% 10%, #6b4a35 0%, #3c2a21 45%, #241812 100%);
  border-radius: 0 0 48rpx 48rpx;
  overflow: hidden;

  &::after {
    content: '';
    position: absolute;
    right: -100rpx;
    bottom: -140rpx;
    width: 320rpx;
    height: 320rpx;
    border-radius: 50%;
    border: 2rpx solid rgba(213, 206, 163, 0.2);
    box-shadow: 0 0 0 40rpx rgba(213, 206, 163, 0.05);
  }
}

.hero-kicker {
  font-size: 20rpx;
  letter-spacing: 6rpx;
  color: rgba(213, 206, 163, 0.6);
}

.hero-title {
  margin-top: 14rpx;
  font-size: 64rpx;
  font-weight: 700;
  letter-spacing: 6rpx;
  color: $coffee-accent;
}

.hero-desc {
  margin-top: 12rpx;
  font-size: 24rpx;
  color: rgba(250, 247, 242, 0.62);
  letter-spacing: 2rpx;
}

.state-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 160rpx 40rpx;
}

.state-title {
  font-size: 28rpx;
  color: $coffee-text;
}

.state-desc {
  margin-top: 12rpx;
  font-size: 22rpx;
  color: $coffee-muted;
}

.empty-btn {
  margin-top: 32rpx;
  padding: 18rpx 56rpx;
  border-radius: 999rpx;
  background-color: $coffee-primary;
  color: #fff;
  font-size: 26rpx;
}

.activity-block {
  padding: 28rpx 24rpx 8rpx;
}

.activity-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 4rpx 22rpx;
}

.activity-title-wrap {
  display: flex;
  flex-direction: column;
}

.activity-title {
  font-size: 32rpx;
  font-weight: 700;
  color: $coffee-text;
}

.activity-time {
  margin-top: 8rpx;
  font-size: 20rpx;
  color: $coffee-muted;
}

.countdown {
  display: flex;
  align-items: center;
  padding: 8rpx 18rpx;
  border-radius: 999rpx;
  background-color: rgba(180, 87, 63, 0.1);
}

.countdown-label {
  font-size: 20rpx;
  color: #b4573f;
}

.countdown-value {
  margin-left: 8rpx;
  font-size: 24rpx;
  font-weight: 700;
  color: #b4573f;
  font-variant-numeric: tabular-nums;
}

.product-grid {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
}

.seckill-card {
  display: flex;
  flex-direction: column;
  width: calc(50% - 10rpx);
  margin-bottom: 20rpx;
  padding: 16rpx;
  border-radius: 26rpx;
  background-color: $coffee-card;
  box-shadow: 0 10rpx 26rpx rgba(60, 42, 33, 0.06);
}

.product-cover {
  width: 100%;
  height: 260rpx;
  border-radius: 18rpx;
  background-color: $coffee-surface;
}

.product-cover-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 72rpx;
  font-weight: 700;
  color: $coffee-accent;
  background: linear-gradient(135deg, #4a352a 0%, #3c2a21 100%);
}

.product-body {
  display: flex;
  flex: 1;
  flex-direction: column;
  padding-top: 16rpx;
}

.product-name {
  font-size: 28rpx;
  font-weight: 600;
  color: $coffee-text;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-hint {
  margin-top: 6rpx;
  font-size: 20rpx;
  color: $coffee-muted;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.price-row {
  display: flex;
  align-items: baseline;
  margin-top: 14rpx;
}

.price-main {
  display: flex;
  align-items: baseline;
  color: #b4573f;
}

.price-symbol {
  font-size: 22rpx;
}

.price-value {
  font-size: 40rpx;
  font-weight: 700;
  line-height: 1;
}

.price-origin {
  margin-left: 10rpx;
  font-size: 20rpx;
  color: rgba(140, 123, 107, 0.7);
  text-decoration: line-through;
}

.meta-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10rpx;
}

.stock-text {
  font-size: 20rpx;
  color: $coffee-muted;

  &.is-empty {
    color: #b4573f;
  }
}

.limit-text {
  font-size: 20rpx;
  color: $coffee-muted;
}

.buy-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 64rpx;
  margin-top: 16rpx;
  border-radius: 32rpx;
  background-color: $coffee-primary;
  color: #fff;
  font-size: 24rpx;
  font-weight: 600;

  &.is-disabled {
    background-color: rgba(60, 42, 33, 0.16);
    color: rgba(60, 42, 33, 0.45);
  }
}

.page-footer {
  padding: 40rpx 0 20rpx;
  text-align: center;
  font-size: 20rpx;
  color: rgba(140, 123, 107, 0.65);
}

/* ------------------------------ 规格弹窗 ------------------------------ */
.sheet-mask {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 100;
  display: flex;
  align-items: flex-end;
  background-color: rgba(30, 20, 14, 0.5);
  animation: mask-in 0.2s ease-out;
}

.sheet {
  position: relative;
  width: 100%;
  max-height: 82vh;
  padding: 32rpx 32rpx 0;
  background-color: $coffee-card;
  border-radius: 36rpx 36rpx 0 0;
  animation: sheet-in 0.28s cubic-bezier(0.22, 0.61, 0.36, 1);
  padding-bottom: constant(safe-area-inset-bottom);
  padding-bottom: env(safe-area-inset-bottom);
}

.sheet-close {
  position: absolute;
  top: 20rpx;
  right: 28rpx;
  width: 56rpx;
  height: 56rpx;
  line-height: 52rpx;
  text-align: center;
  font-size: 40rpx;
  color: $coffee-muted;
}

.sheet-product {
  display: flex;
  align-items: center;
  padding-right: 60rpx;
}

.sheet-cover {
  width: 128rpx;
  height: 128rpx;
  border-radius: 20rpx;
  background-color: $coffee-surface;
  flex-shrink: 0;
}

.sheet-cover-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 48rpx;
  font-weight: 700;
  color: $coffee-accent;
  background: linear-gradient(135deg, #4a352a 0%, #3c2a21 100%);
}

.sheet-info {
  display: flex;
  flex-direction: column;
  padding-left: 20rpx;
}

.sheet-name {
  font-size: 32rpx;
  font-weight: 700;
  color: $coffee-text;
}

.sheet-price-row {
  display: flex;
  align-items: baseline;
  margin-top: 8rpx;
}

.sheet-price {
  font-size: 36rpx;
  font-weight: 700;
  color: #b4573f;
}

.sheet-origin {
  margin-left: 12rpx;
  font-size: 22rpx;
  color: rgba(140, 123, 107, 0.7);
  text-decoration: line-through;
}

.sheet-limit {
  margin-top: 8rpx;
  font-size: 20rpx;
  color: $coffee-muted;
}

.sheet-scroll {
  height: 38vh;
  margin-top: 12rpx;

  /* #ifdef H5 */
  overscroll-behavior: contain;
  /* #endif */
}

.spec-group {
  padding: 24rpx 0 8rpx;
  border-bottom: 2rpx solid $coffee-line;

  &:last-child {
    border-bottom: none;
  }

  &.is-invalid .spec-group-name {
    color: $coffee-danger;
  }
}

.spec-error {
  display: block;
  margin-top: 8rpx;
  font-size: 22rpx;
  color: $coffee-danger;
}

.spec-group-head {
  display: flex;
  align-items: center;
}

.spec-group-name {
  font-size: 26rpx;
  font-weight: 600;
  color: $coffee-text;
}

.spec-tag {
  margin-left: 12rpx;
  padding: 2rpx 12rpx;
  border-radius: 999rpx;
  background-color: rgba(213, 206, 163, 0.5);
  color: #7a6a3f;
  font-size: 20rpx;

  &.is-light {
    background-color: rgba(60, 42, 33, 0.06);
    color: $coffee-muted;
  }
}

.spec-options {
  display: flex;
  flex-wrap: wrap;
  padding: 16rpx 0 8rpx;
}

.spec-option {
  display: flex;
  align-items: baseline;
  margin: 0 16rpx 16rpx 0;
  padding: 14rpx 28rpx;
  border: 2rpx solid transparent;
  border-radius: 999rpx;
  background-color: $coffee-bg;
  font-size: 26rpx;
  color: $coffee-text;
  transition: all 0.2s;

  &.is-selected {
    border-color: $coffee-primary;
    background-color: $coffee-primary;
    color: #fff;

    .option-extra {
      color: rgba(255, 255, 255, 0.7);
    }
  }
}

.option-extra {
  margin-left: 8rpx;
  font-size: 20rpx;
  color: $coffee-muted;
}

.sheet-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx 0 24rpx;
  border-top: 2rpx solid $coffee-line;
}

.qty {
  display: flex;
  align-items: center;
}

.qty-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 52rpx;
  height: 52rpx;
  border-radius: 50%;
  background-color: $coffee-bg;
  color: $coffee-primary;
  font-size: 32rpx;
  line-height: 1;
}

.qty-value {
  min-width: 60rpx;
  text-align: center;
  font-size: 28rpx;
  font-weight: 600;
  color: $coffee-text;
}

.buy-now-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 80rpx;
  padding: 0 56rpx;
  border-radius: 40rpx;
  background-color: $coffee-primary;
  color: #fff;
  font-size: 28rpx;
  font-weight: 600;
}

@keyframes sheet-in {
  from {
    transform: translateY(100%);
  }
  to {
    transform: translateY(0);
  }
}

@keyframes mask-in {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

@media (prefers-reduced-motion: reduce) {
  .sheet,
  .sheet-mask {
    animation: none;
  }

  .spec-option {
    transition: none;
  }
}
</style>
