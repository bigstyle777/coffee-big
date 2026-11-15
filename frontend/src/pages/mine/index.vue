<script setup lang="ts">
import { computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useUserStore } from '@/stores/user'
import { useCartStore } from '@/stores/cart'

const userStore = useUserStore()
const cartStore = useCartStore()

const nickname = computed(() => userStore.userInfo?.nickname || '微信用户')
const avatarText = computed(() => nickname.value.slice(0, 1))
const roleText = computed(() => (userStore.isAdmin ? '店长' : '会员'))

onShow(async () => {
  try {
    // 已有 Token 时直接复用，未登录则静默尝试一次微信登录
    await userStore.ensureLogin({ silent: true })
  } catch (error) {
    // 静默登录失败时保持访客态，由页面上的登录按钮兜底
  }
})

async function handleLogin(): Promise<void> {
  try {
    await userStore.login()
    uni.showToast({ title: '登录成功', icon: 'none' })
  } catch (error) {
    // 错误提示已由 request 统一处理
  }
}

function ensureLoggedIn(): boolean {
  if (!userStore.isLogin) {
    uni.showToast({ title: '请先登录', icon: 'none' })
    return false
  }
  return true
}

function goOrderList(): void {
  if (!ensureLoggedIn()) {
    return
  }
  uni.navigateTo({ url: '/pages/order/list' })
}

function showAdminConsoleNotice(): void {
  uni.showModal({
    title: '店长后台已迁移',
    content: '请在电脑浏览器打开店长后台，使用账号密码登录管理商品与订单。',
    showCancel: false,
    confirmText: '知道了',
  })
}

function logout(): void {
  uni.showModal({
    title: '退出登录',
    content: '退出后购物袋会被清空，确定退出吗？',
    confirmText: '退出',
    confirmColor: '#B4573F',
    success: (res) => {
      if (!res.confirm) {
        return
      }
      userStore.logout()
      cartStore.clear()
      uni.showToast({ title: '已退出登录', icon: 'none' })
    },
  })
}
</script>

<template>
  <view class="mine-page">
    <view class="profile-card">
      <view class="profile-main">
        <image
          v-if="userStore.userInfo?.avatar"
          class="avatar"
          :src="userStore.userInfo.avatar"
          mode="aspectFill"
        />
        <view v-else class="avatar avatar-text">{{ avatarText }}</view>
        <view class="profile-info">
          <view class="name-row">
            <text class="nickname">{{ userStore.isLogin ? nickname : '未登录' }}</text>
            <text v-if="userStore.isLogin" class="role-chip">{{ roleText }}</text>
          </view>
          <text class="profile-desc">
            {{ userStore.isLogin ? '今天也要好好喝一杯咖啡' : '登录后可下单、查看订单' }}
          </text>
        </view>
      </view>
      <view v-if="!userStore.isLogin" class="login-btn" @click="handleLogin">
        {{ userStore.logging ? '登录中…' : '微信一键登录' }}
      </view>
    </view>

    <view class="cell-group">
      <view class="cell" @click="goOrderList">
        <view class="cell-left">
          <view class="cell-icon">单</view>
          <text class="cell-title">我的订单</text>
        </view>
        <text class="cell-arrow">›</text>
      </view>
      <view v-if="userStore.isAdmin" class="cell" @click="showAdminConsoleNotice">
        <view class="cell-left">
          <view class="cell-icon is-accent">店</view>
          <view class="cell-title-group">
            <text class="cell-title">店长后台</text>
            <text class="cell-subtitle">请在电脑浏览器登录管理端</text>
          </view>
        </view>
        <text class="cell-arrow">›</text>
      </view>
    </view>

    <view v-if="userStore.isLogin" class="logout-btn" @click="logout">退出登录</view>

    <view class="page-footer">Coffee Big · 一杯好咖啡</view>
  </view>
</template>

<style lang="scss" scoped>
.mine-page {
  min-height: 100vh;
  padding: 32rpx;
  background-color: $coffee-bg;

  /* #ifdef H5 */
  min-height: calc(100vh - var(--window-top));
  min-height: calc(100dvh - var(--window-top));
  /* #endif */
}

.profile-card {
  padding: 40rpx 32rpx 32rpx;
  border-radius: 32rpx;
  background: linear-gradient(135deg, #4a352a 0%, #3c2a21 60%, #2b1d16 100%);
  box-shadow: 0 20rpx 40rpx rgba(60, 42, 33, 0.2);
}

.profile-main {
  display: flex;
  align-items: center;
}

.avatar {
  width: 120rpx;
  height: 120rpx;
  border-radius: 50%;
  background-color: rgba(250, 247, 242, 0.16);
  border: 2rpx solid rgba(213, 206, 163, 0.5);
}

.avatar-text {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 44rpx;
  font-weight: 700;
  color: $coffee-accent;
}

.profile-info {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
  padding-left: 24rpx;
}

.name-row {
  display: flex;
  align-items: center;
}

.nickname {
  max-width: 320rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 36rpx;
  font-weight: 700;
  color: #faf7f2;
}

.role-chip {
  margin-left: 14rpx;
  padding: 2rpx 16rpx;
  border-radius: 999rpx;
  background-color: rgba(213, 206, 163, 0.9);
  color: $coffee-primary;
  font-size: 20rpx;
}

.profile-desc {
  margin-top: 10rpx;
  font-size: 22rpx;
  color: rgba(250, 247, 242, 0.62);
}

.login-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 76rpx;
  margin-top: 32rpx;
  border-radius: 38rpx;
  background-color: $coffee-accent;
  color: $coffee-primary;
  font-size: 28rpx;
  font-weight: 600;
}

.cell-group {
  margin-top: 32rpx;
  border-radius: 28rpx;
  background-color: $coffee-card;
  overflow: hidden;
  box-shadow: 0 8rpx 24rpx rgba(60, 42, 33, 0.05);
}

.cell {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 28rpx 32rpx;
  border-bottom: 2rpx solid $coffee-line;

  &:last-child {
    border-bottom: none;
  }
}

.cell-left {
  display: flex;
  align-items: center;
}

.cell-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64rpx;
  height: 64rpx;
  border-radius: 20rpx;
  background-color: $coffee-surface;
  color: $coffee-primary;
  font-size: 26rpx;
  font-weight: 600;

  &.is-accent {
    background-color: rgba(213, 206, 163, 0.45);
    color: #7a6a3f;
  }
}

.cell-title-group {
  display: flex;
  flex-direction: column;
}

.cell-title {
  margin-left: 20rpx;
  font-size: 28rpx;
  color: $coffee-text;
}

.cell-subtitle {
  margin-top: 4rpx;
  margin-left: 20rpx;
  font-size: 22rpx;
  color: $coffee-muted;
}

.cell-arrow {
  font-size: 36rpx;
  color: rgba(140, 123, 107, 0.5);
}

.logout-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 88rpx;
  margin-top: 32rpx;
  border-radius: 28rpx;
  background-color: $coffee-card;
  color: $coffee-danger;
  font-size: 28rpx;
  box-shadow: 0 8rpx 24rpx rgba(60, 42, 33, 0.05);
}

.page-footer {
  margin-top: 48rpx;
  text-align: center;
  font-size: 22rpx;
  color: rgba(140, 123, 107, 0.6);
}
</style>
