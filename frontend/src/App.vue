<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app'
import { useUserStore } from '@/stores/user'

onLaunch(() => {
  const userStore = useUserStore()
  // 启动即静默登录；失败不阻塞进入首页，后续接口遇到 401 会再次尝试静默重登
  userStore.ensureLogin({ silent: true }).catch(() => {})
})
</script>

<style lang="scss">
page {
  background-color: $coffee-bg;
  color: $coffee-text;
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Helvetica Neue', Helvetica,
    Arial, sans-serif;
  font-size: 28rpx;
  line-height: 1.5;
  -webkit-font-smoothing: antialiased;
}

view,
text,
image,
scroll-view,
button,
input,
textarea {
  box-sizing: border-box;
}

/* 去掉小程序 button 默认边框 */
button::after {
  border: none;
}
</style>
