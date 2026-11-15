<script setup lang="ts">
import { computed, getCurrentInstance, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { onShow, onHide, onUnload } from '@dcloudio/uni-app'
import type {
  Coupon,
  MenuCategory,
  MenuProduct,
  MenuResult,
  OrderCreateResult,
  SeckillActivity,
  SeckillProduct,
  SpecGroup,
  SpecOption,
} from '@/types/api'
import { request } from '@/utils/request'
import { fenToYuan } from '@/utils/money'
import { cacheTimeText, readMenuCache, writeMenuCache } from '@/utils/menu-cache'
import { watchNetwork } from '@/utils/network'
import { useCartStore } from '@/stores/cart'
import { useUserStore } from '@/stores/user'

const instance = getCurrentInstance()
const cart = useCartStore()
const userStore = useUserStore()

const categories = ref<MenuCategory[]>([])
const loading = ref(true)
const loadError = ref('')
const activeCategoryId = ref<number | null>(null)
const scrollTarget = ref('')
const scrollLocked = ref(false)

/** 秒杀活动：菜单只做标记与跳转，不进入普通购物袋 */
const seckillActivities = ref<SeckillActivity[]>([])
const seckillProductMap = computed(() => {
  const map = new Map<number, SeckillProduct>()
  seckillActivities.value.forEach((activity) => {
    activity.products.forEach((product) => {
      if (!map.has(product.productId)) {
        map.set(product.productId, product)
      }
    })
  })
  return map
})
const seckillProductCount = computed(() => seckillProductMap.value.size)

/** 网络与离线缓存状态 */
const networkOnline = ref(true)
const usingCache = ref(false)
const cacheUpdatedAt = ref<number | null>(null)
const cache = readMenuCache()
if (cache) {
  categories.value = cache.data.categories || []
  activeCategoryId.value = categories.value[0]?.id ?? null
  cacheUpdatedAt.value = cache.updatedAt
  usingCache.value = true
  loading.value = false
}

const cacheTimeLabel = computed(() =>
  cacheUpdatedAt.value ? cacheTimeText(cacheUpdatedAt.value) : '',
)

let sectionTops: number[] = []
let unlockTimer: ReturnType<typeof setTimeout> | null = null
let bootstrapped = false

/** 分类与商品列表联动 */
function measureSections(): void {
  const query = uni.createSelectorQuery().in(instance)
  query.select('.product-list').boundingClientRect()
  query.selectAll('.category-section').boundingClientRect()
  query.exec((result) => {
    const listRect = result?.[0] as UniApp.NodeInfo | null
    const sections = (result?.[1] || []) as UniApp.NodeInfo[]
    if (!listRect || !sections.length) {
      return
    }
    const baseTop = listRect.top || 0
    sectionTops = sections.map((section) => (section.top || 0) - baseTop)
  })
}

function onProductScroll(event: { detail: { scrollTop: number } }): void {
  if (scrollLocked.value || !sectionTops.length) {
    return
  }
  const scrollTop = event.detail.scrollTop + 8
  let index = 0
  for (let i = 0; i < sectionTops.length; i += 1) {
    if (sectionTops[i] <= scrollTop) {
      index = i
    }
  }
  const category = categories.value[index]
  if (category && category.id !== activeCategoryId.value) {
    activeCategoryId.value = category.id
  }
}

function selectCategory(categoryId: number): void {
  if (categoryId === activeCategoryId.value) {
    return
  }
  activeCategoryId.value = categoryId
  scrollLocked.value = true
  // 先清空再赋值，确保连续点击同一分类时也能重新定位
  scrollTarget.value = ''
  nextTick(() => {
    scrollTarget.value = `cat-${categoryId}`
  })
  if (unlockTimer) {
    clearTimeout(unlockTimer)
  }
  unlockTimer = setTimeout(() => {
    scrollLocked.value = false
    scrollTarget.value = ''
  }, 600)
}

/** 最后一个分类可能无法滚动到顶部，触底时直接选中它 */
function onScrollToLower(): void {
  if (scrollLocked.value) {
    return
  }
  const last = categories.value[categories.value.length - 1]
  if (last) {
    activeCategoryId.value = last.id
  }
}

async function loadMenu(options: { silent?: boolean } = {}): Promise<void> {
  const silent = options.silent === true
  if (!silent) {
    loading.value = true
    loadError.value = ''
  }
  try {
    const data = await request<MenuResult>({ url: '/menu/list', silent, auth: false })
    categories.value = data.categories || []
    activeCategoryId.value = categories.value[0]?.id ?? null
    loading.value = false
    usingCache.value = false
    loadError.value = ''
    cacheUpdatedAt.value = Date.now()
    writeMenuCache(data)
    await nextTick()
    measureSections()
  } catch (error) {
    loading.value = false
    if (cache) {
      // 接口失败但有缓存：降级为离线菜单，不阻断点单
      usingCache.value = true
      loadError.value = ''
      await nextTick()
      measureSections()
    } else if (!categories.value.length) {
      loadError.value = '菜单加载失败，点击重试'
    }
  }
}

async function loadSeckill(): Promise<void> {
  try {
    seckillActivities.value = (await request<SeckillActivity[]>({
      url: '/activity/seckill/list',
      auth: false,
      silent: true,
    })) || []
  } catch (error) {
    seckillActivities.value = []
  }
}

async function bootstrap(): Promise<void> {
  if (!categories.value.length) {
    loading.value = true
  }
  loadError.value = ''
  // 有缓存时先按缓存渲染并测量分类位置，保证滚动联动立即可用
  if (categories.value.length) {
    await nextTick()
    measureSections()
  }
  // 秒杀信息独立加载，失败不影响菜单
  void loadSeckill()
  try {
    // 菜单公开，浏览不需要登录；登录与优惠券在购物袋结算时触发
    await loadMenu({ silent: categories.value.length > 0 })
  } catch (error) {
    loading.value = false
    if (!categories.value.length) {
      loadError.value = '菜单加载失败，点击重试'
    }
  }
}

/** 离线横幅上的手动重试 */
async function refreshMenu(): Promise<void> {
  await bootstrap()
}

let unwatchNetwork: (() => void) | null = null
let firstNetworkNotify = true

function startNetworkWatch(): void {
  if (unwatchNetwork) {
    return
  }
  unwatchNetwork = watchNetwork((online) => {
    networkOnline.value = online
    // 订阅瞬间的回调只用于初始化状态，避免与 onShow 的刷新重复
    if (firstNetworkNotify) {
      firstNetworkNotify = false
      return
    }
    // 网络恢复后静默刷新，避免打断用户当前浏览位置
    if (online && bootstrapped) {
      loadMenu({ silent: true })
    }
  })
}

function stopNetworkWatch(): void {
  if (unwatchNetwork) {
    unwatchNetwork()
    unwatchNetwork = null
    firstNetworkNotify = true
  }
}

onShow(() => {
  startNetworkWatch()
  if (!bootstrapped) {
    bootstrapped = true
    bootstrap()
    return
  }
  // 返回菜单页时静默刷新，及时同步价格与上下架变化
  loadMenu({ silent: true })
  loadSeckill()
})

onHide(stopNetworkWatch)
onUnload(stopNetworkWatch)

function goMine(): void {
  uni.navigateTo({ url: '/pages/mine/index' })
}

/* ------------------------------ 规格弹窗 ------------------------------ */

const specVisible = ref(false)
const specProduct = ref<MenuProduct | null>(null)
const specQuantity = ref(1)
/** 校验未通过的规格组，用户首次点「加入购物袋」后才标记（避免页面一打开就报错） */
const invalidGroupIds = ref<number[]>([])
/** groupId -> 已选 optionId 列表 */
const selectedMap = reactive<Record<number, number[]>>({})

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

/** 单杯价 = 基础价 + 已选规格加价 */
const specUnitPrice = computed(() => {
  const basePrice = specProduct.value?.price || 0
  return selectedOptions.value.reduce((sum, option) => sum + (option.extraPrice || 0), basePrice)
})

function openSpec(product: MenuProduct): void {
  if (isSoldOut(product)) {
    uni.showToast({ title: '该商品已售罄', icon: 'none' })
    return
  }
  specProduct.value = product
  specQuantity.value = 1
  invalidGroupIds.value = []
  Object.keys(selectedMap).forEach((key) => {
    delete selectedMap[Number(key)]
  })
  // 必选且单选的规格组默认选中第一项，减少用户操作
  product.specGroups.forEach((group) => {
    selectedMap[group.id] =
      group.required && !group.multiple && group.options.length ? [group.options[0].id] : []
  })
  specVisible.value = true
}

function closeSpec(): void {
  specVisible.value = false
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
    // 非必选的单选规格允许取消
    if (!group.required) {
      selectedMap[group.id] = []
    }
  } else {
    selectedMap[group.id] = [option.id]
  }
  // 已做出选择后立即移除错误标记
  if ((selectedMap[group.id] || []).length) {
    invalidGroupIds.value = invalidGroupIds.value.filter((id) => id !== group.id)
  }
}

function changeSpecQuantity(delta: number): void {
  const next = specQuantity.value + delta
  const stockLimit =
    typeof specProduct.value?.stock === 'number' ? Math.min(20, specProduct.value.stock) : 20
  if (next >= 1 && next <= stockLimit) {
    specQuantity.value = next
  }
}

/** 库存为未知（旧缓存）时不判定售罄 */
function isSoldOut(product: MenuProduct): boolean {
  return typeof product.stock === 'number' && product.stock <= 0
}

function stockText(product: MenuProduct): string {
  if (typeof product.stock !== 'number') {
    return ''
  }
  return product.stock > 0 ? `剩 ${product.stock} 杯` : '售罄'
}

function findSeckillProduct(product: MenuProduct): SeckillProduct | undefined {
  return seckillProductMap.value.get(product.id)
}

function isSeckillProduct(product: MenuProduct): boolean {
  return Boolean(findSeckillProduct(product))
}

function displayPrice(product: MenuProduct): number {
  return findSeckillProduct(product)?.activityPrice ?? product.price
}

function isProductSoldOut(product: MenuProduct): boolean {
  const seckill = findSeckillProduct(product)
  return seckill ? seckill.remainingStock <= 0 : isSoldOut(product)
}

function displayStockText(product: MenuProduct): string {
  const seckill = findSeckillProduct(product)
  if (seckill) {
    return seckill.remainingStock > 0 ? `剩 ${seckill.remainingStock} 杯` : '已抢完'
  }
  return stockText(product)
}

function goSeckill(): void {
  uni.navigateTo({ url: '/pages/seckill/index' })
}

function handleProductClick(product: MenuProduct): void {
  if (isSeckillProduct(product)) {
    goSeckill()
    return
  }
  openSpec(product)
}

function addToCart(): void {
  const product = specProduct.value
  if (!product) {
    return
  }
  if (isSoldOut(product)) {
    uni.showToast({ title: '该商品已售罄', icon: 'none' })
    return
  }
  if (typeof product.stock === 'number' && specQuantity.value > product.stock) {
    uni.showToast({ title: '库存不足，请减少数量', icon: 'none' })
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
  invalidGroupIds.value = []
  cart.addItem({
    productId: product.id,
    productName: product.name,
    productPic: product.picUrl,
    basePrice: product.price,
    unitPrice: specUnitPrice.value,
    optionIds: selectedOptions.value.map((option) => option.id),
    optionNames: selectedOptions.value.map((option) => option.name),
    quantity: specQuantity.value,
  })
  specVisible.value = false
  uni.showToast({ title: '已加入购物袋', icon: 'none' })
}

function specHint(product: MenuProduct): string {
  if (!product.specGroups.length) {
    return '标准规格'
  }
  return product.specGroups.map((group) => group.name).join(' · ')
}

/* ------------------------------ 购物袋 ------------------------------ */

const cartVisible = ref(false)
const creating = ref(false)
const coupons = ref<Coupon[]>([])
const selectedCouponId = ref<number | null>(null)
const couponVisible = ref(false)
const checkoutRequestId = ref('')

const selectedCoupon = computed(
  () => coupons.value.find((coupon) => coupon.id === selectedCouponId.value) || null,
)
const discountAmount = computed(() => selectedCoupon.value?.discountAmount || 0)
const payAmount = computed(() => Math.max(cart.totalAmount - discountAmount.value, 0))

const cartListHeight = computed(() => {
  const rows = Math.min(cart.items.length, 4)
  return `${Math.max(rows, 1) * 132}rpx`
})

// 购物袋被清空时自动收起面板
watch(
  () => cart.totalCount,
  (count) => {
    if (count === 0) {
      cartVisible.value = false
      couponVisible.value = false
      coupons.value = []
      selectedCouponId.value = null
      checkoutRequestId.value = ''
    }
  },
)

/** 购物袋内容或优惠券变化后，旧的幂等键立即失效 */
const cartSignature = computed(() =>
  [
    cart.items.map((item) => `${item.key}:${item.quantity}`).join('|'),
    cart.pickupType,
    selectedCouponId.value ?? '',
  ].join('#'),
)
watch(cartSignature, () => {
  checkoutRequestId.value = ''
})

function onRemarkInput(event: unknown): void {
  const detail = (event as { detail?: { value?: string } }).detail
  cart.setRemark(detail?.value || '')
}

/** 查询当前金额下可用优惠券，失败时不影响下单 */
let couponSeq = 0
async function loadCoupons(): Promise<void> {
  if (!cart.totalAmount) {
    coupons.value = []
    selectedCouponId.value = null
    return
  }
  const seq = ++couponSeq
  try {
    const data = await request<Coupon[]>({
      url: '/coupon/available',
      data: { amount: cart.totalAmount },
      silent: true,
    })
    if (seq !== couponSeq) {
      return
    }
    coupons.value = data || []
    if (!coupons.value.some((coupon) => coupon.id === selectedCouponId.value)) {
      selectedCouponId.value = coupons.value[0]?.id ?? null
    }
  } catch (error) {
    if (seq === couponSeq) {
      coupons.value = []
      selectedCouponId.value = null
    }
  }
}

/** 购物袋内修改数量后，重新匹配优惠券门槛 */
let couponReloadTimer: ReturnType<typeof setTimeout> | null = null
watch(
  () => cart.totalAmount,
  () => {
    if (!cartVisible.value || !userStore.isLogin) {
      return
    }
    if (couponReloadTimer) {
      clearTimeout(couponReloadTimer)
    }
    couponReloadTimer = setTimeout(() => {
      loadCoupons()
    }, 300)
  },
)

/** 打开购物袋时触发登录，并加载可用优惠券 */
async function openCart(): Promise<void> {
  cartVisible.value = true
  if (!userStore.isLogin) {
    try {
      await userStore.ensureLogin({ silent: true })
    } catch (error) {
      uni.showToast({ title: '登录后可结算', icon: 'none' })
      return
    }
  }
  await loadCoupons()
}

function openCoupon(): void {
  if (!coupons.value.length) {
    uni.showToast({ title: '暂无可用优惠券', icon: 'none' })
    return
  }
  couponVisible.value = true
}

function chooseCoupon(couponId: number | null): void {
  selectedCouponId.value = couponId
  couponVisible.value = false
}

function ensureRequestId(): string {
  if (!checkoutRequestId.value) {
    checkoutRequestId.value = `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
  }
  return checkoutRequestId.value
}

function clearCart(): void {
  cart.clear()
  cartVisible.value = false
  couponVisible.value = false
  coupons.value = []
  selectedCouponId.value = null
  checkoutRequestId.value = ''
}

async function checkout(): Promise<void> {
  if (!cart.totalCount || creating.value) {
    return
  }
  creating.value = true
  try {
    // 下单时才真正要求登录；浏览菜单全程免登录
    await userStore.ensureLogin({ silent: false })
    if (!coupons.value.length) {
      await loadCoupons()
    }
    const result = await request<OrderCreateResult>({
      url: '/order/create',
      method: 'POST',
      data: {
        items: cart.orderItems,
        pickupType: cart.pickupType,
        remark: cart.remark,
        couponId: selectedCouponId.value,
        requestId: ensureRequestId(),
      },
    })
    checkoutRequestId.value = ''
    cart.clear()
    cartVisible.value = false
    couponVisible.value = false
    coupons.value = []
    selectedCouponId.value = null
    // 进入订单详情后自动触发模拟支付
    uni.navigateTo({ url: `/pages/order/detail?id=${result.orderId}&autoPay=1` })
  } catch (error) {
    // 优惠券可能已被占用或失效，静默刷新一次可用列表
    loadCoupons()
  } finally {
    creating.value = false
  }
}

// #ifdef H5
/** H5 端：弹层打开时锁定页面滚动，并支持 Esc 关闭 */
const sheetOpen = computed(() => specVisible.value || cartVisible.value || couponVisible.value)

watch(sheetOpen, (open) => {
  document.body.style.overflow = open ? 'hidden' : ''
  document.documentElement.style.overflow = open ? 'hidden' : ''
})

function onKeydown(event: KeyboardEvent): void {
  if (event.key !== 'Escape') {
    return
  }
  if (specVisible.value) {
    closeSpec()
    return
  }
  if (couponVisible.value) {
    couponVisible.value = false
    return
  }
  if (cartVisible.value) {
    cartVisible.value = false
  }
}

onMounted(() => {
  window.addEventListener('keydown', onKeydown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown)
  document.body.style.overflow = ''
  document.documentElement.style.overflow = ''
})
// #endif
</script>

<template>
  <view class="menu-page">
    <view class="shop-head">
      <view class="shop-meta">
        <text class="shop-name">拾光咖啡</text>
        <text class="shop-desc">现磨现做 · 只做当天新鲜咖啡</text>
      </view>
      <view class="mine-entry" @click="goMine">我的</view>
    </view>

    <view v-if="!networkOnline" class="offline-bar">
      <text>网络已断开，当前展示缓存菜单</text>
    </view>
    <view v-else-if="usingCache" class="offline-bar is-cache">
      <text>离线菜单{{ cacheTimeLabel ? ` · ${cacheTimeLabel} 更新` : '' }}</text>
      <text class="offline-retry" @click="refreshMenu">重新加载</text>
    </view>

    <view v-if="seckillActivities.length" class="seckill-banner" @click="goSeckill">
      <view class="seckill-banner-left">
        <view class="seckill-banner-badge">秒杀</view>
        <view class="seckill-banner-text">
          <text class="seckill-banner-title">限时秒杀进行中</text>
          <text class="seckill-banner-desc">{{ seckillProductCount }} 款饮品限时特价</text>
        </view>
      </view>
      <text class="seckill-banner-action">去抢购 ›</text>
    </view>

    <view class="menu-body">
      <scroll-view class="category-bar" scroll-y :show-scrollbar="false">
        <view
          v-for="category in categories"
          :key="category.id"
          :class="['category-item', category.id === activeCategoryId && 'is-active']"
          @click="selectCategory(category.id)"
        >
          <view class="category-dot" />
          <text class="category-name">{{ category.name }}</text>
        </view>
      </scroll-view>

      <scroll-view
        class="product-list"
        scroll-y
        scroll-with-animation
        :scroll-into-view="scrollTarget"
        :show-scrollbar="false"
        @scroll="onProductScroll"
        @scrolltolower="onScrollToLower"
      >
        <view v-if="loading" class="state-block">
          <view class="state-title">正在磨豆…</view>
          <view class="state-desc">菜单马上就来</view>
        </view>
        <view v-else-if="loadError" class="state-block" @click="bootstrap">
          <view class="state-title">{{ loadError }}</view>
          <view class="state-desc">点击重新加载</view>
        </view>
        <view v-else-if="!categories.length" class="state-block">
          <view class="state-title">今日暂未上架</view>
          <view class="state-desc">稍后再来看看吧</view>
        </view>

        <view
          v-for="category in categories"
          :id="`cat-${category.id}`"
          :key="category.id"
          class="category-section"
        >
          <view class="section-head">
            <text class="section-title">{{ category.name }}</text>
            <text class="section-count">{{ category.products.length }} 款</text>
          </view>

          <view
            v-for="product in category.products"
            :key="product.id"
            :class="[
              'product-card',
              isProductSoldOut(product) && 'is-sold-out',
              isSeckillProduct(product) && 'is-seckill',
            ]"
            @click="handleProductClick(product)"
          >
            <image
              v-if="product.picUrl"
              class="product-cover"
              :src="product.picUrl"
              mode="aspectFill"
              lazy-load
            />
            <view v-else class="product-cover product-cover-empty">
              <text>{{ product.name.slice(0, 1) }}</text>
            </view>

            <view class="product-main">
              <view class="product-name-row">
                <text class="product-name">{{ product.name }}</text>
                <text v-if="isSeckillProduct(product)" class="seckill-chip">秒杀</text>
              </view>
              <text class="product-hint">{{ specHint(product) }}</text>
              <text
                v-if="displayStockText(product)"
                :class="['stock-hint', isProductSoldOut(product) && 'is-empty']"
              >
                {{ displayStockText(product) }}
              </text>
              <view class="product-bottom">
                <view class="product-price">
                  <text class="price-symbol">¥</text>
                  <text class="price-value">{{ fenToYuan(displayPrice(product)) }}</text>
                  <text v-if="isSeckillProduct(product)" class="price-origin">
                    ¥{{ fenToYuan(product.price) }}
                  </text>
                  <text v-else-if="product.specGroups.length" class="price-suffix">起</text>
                </view>
                <text v-if="isProductSoldOut(product)" class="sold-out-tag">
                  {{ isSeckillProduct(product) ? '已抢完' : '售罄' }}
                </text>
                <view v-else class="spec-btn" @click.stop="handleProductClick(product)">
                  {{ isSeckillProduct(product) ? '去秒杀' : '选规格' }}
                </view>
              </view>
            </view>
          </view>
        </view>

        <view v-if="!loading && !loadError && categories.length" class="list-end">
          — 已经到底啦 —
        </view>
      </scroll-view>
    </view>

    <!-- 底部悬浮购物袋 -->
    <view v-if="cart.totalCount > 0" class="cart-bar">
      <view class="cart-summary" @click="openCart">
        <view class="cart-badge">{{ cart.totalCount }}</view>
        <view class="cart-amount">
          <view>
            <text class="cart-currency">¥</text>
            <text class="cart-price">{{ fenToYuan(cart.totalAmount) }}</text>
          </view>
          <text class="cart-label">点击查看购物袋</text>
        </view>
      </view>
      <view class="checkout-btn" @click="openCart">去结算</view>
    </view>

    <!-- 规格选择弹窗 -->
    <view v-if="specVisible && specProduct" class="sheet-mask" @click="closeSpec">
      <view class="sheet" @click.stop>
        <view class="sheet-close" aria-label="关闭规格选择" @click="closeSpec">×</view>
        <view class="sheet-product">
          <image
            v-if="specProduct.picUrl"
            class="sheet-cover"
            :src="specProduct.picUrl"
            mode="aspectFill"
          />
          <view v-else class="sheet-cover sheet-cover-empty">
            {{ specProduct.name.slice(0, 1) }}
          </view>
          <view class="sheet-product-info">
            <text class="sheet-product-name">{{ specProduct.name }}</text>
            <text class="sheet-product-desc">{{ specHint(specProduct) }}</text>
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
          <view class="sheet-price">
            <text class="price-symbol">¥</text>
            <text class="sheet-price-value">{{ fenToYuan(specUnitPrice) }}</text>
            <text class="sheet-price-unit">/ 杯</text>
          </view>
          <view class="qty">
            <view class="qty-btn" aria-label="减少一杯" @click="changeSpecQuantity(-1)">−</view>
            <text class="qty-value">{{ specQuantity }}</text>
            <view class="qty-btn" aria-label="增加一杯" @click="changeSpecQuantity(1)">+</view>
          </view>
          <view class="add-cart-btn" @click="addToCart">加入购物袋</view>
        </view>
      </view>
    </view>

    <!-- 购物袋面板 -->
    <view v-if="cartVisible" class="sheet-mask" @click="cartVisible = false">
      <view class="cart-sheet" @click.stop>
        <view class="cart-sheet-head">
          <text class="cart-sheet-title">购物袋</text>
          <text class="cart-clear" @click="clearCart">清空</text>
        </view>

        <scroll-view
          class="cart-items"
          scroll-y
          :show-scrollbar="false"
          :style="{ height: cartListHeight }"
        >
          <view v-for="item in cart.items" :key="item.key" class="cart-item">
            <view class="cart-item-info">
              <text class="cart-item-name">{{ item.productName }}</text>
              <text class="cart-item-spec">{{ item.specText || '标准规格' }}</text>
              <text class="cart-item-price">¥{{ fenToYuan(item.unitPrice) }}</text>
            </view>
            <view class="qty">
              <view class="qty-btn" :aria-label="`减少${item.productName}数量`" @click="cart.decrease(item.key)">−</view>
              <text class="qty-value">{{ item.quantity }}</text>
              <view class="qty-btn" :aria-label="`增加${item.productName}数量`" @click="cart.increase(item.key)">+</view>
            </view>
          </view>
        </scroll-view>

        <view class="field-row">
          <text class="field-label">优惠券</text>
          <view class="coupon-select" @click="openCoupon">
            <text v-if="selectedCoupon" class="coupon-selected">
              -¥{{ fenToYuan(selectedCoupon.discountAmount) }}
            </text>
            <text v-else class="coupon-empty-text">
              {{ coupons.length ? '不使用优惠券' : '暂无可用' }}
            </text>
            <text class="coupon-arrow">›</text>
          </view>
        </view>

        <view class="field-row">
          <text class="field-label">取餐方式</text>
          <view class="pickup-switch">
            <view
              :class="['pickup-item', cart.pickupType === 'SELF_PICKUP' && 'is-active']"
              @click="cart.setPickupType('SELF_PICKUP')"
            >
              到店自取
            </view>
            <view
              :class="['pickup-item', cart.pickupType === 'DINE_IN' && 'is-active']"
              @click="cart.setPickupType('DINE_IN')"
            >
              堂食
            </view>
          </view>
        </view>

        <view class="field-row">
          <text class="field-label">备注</text>
          <input
            class="remark-input"
            :value="cart.remark"
            maxlength="50"
            placeholder="如：少冰、不要吸管"
            placeholder-class="remark-placeholder"
            @input="onRemarkInput"
          />
        </view>

        <view class="cart-sheet-footer">
          <view class="cart-total">
            <view class="cart-total-line">
              <text class="cart-total-label">商品合计</text>
              <text class="cart-total-value">¥{{ fenToYuan(cart.totalAmount) }}</text>
            </view>
            <view v-if="discountAmount > 0" class="cart-total-line">
              <text class="cart-total-label">优惠券</text>
              <text class="cart-discount">-¥{{ fenToYuan(discountAmount) }}</text>
            </view>
            <view class="cart-total-line">
              <text class="cart-total-label">实付</text>
              <text class="cart-total-value is-pay">¥{{ fenToYuan(payAmount) }}</text>
            </view>
          </view>
          <view class="checkout-btn" @click="checkout">{{ creating ? '提交中…' : '确认下单' }}</view>
        </view>
      </view>
    </view>

    <!-- 优惠券选择面板 -->
    <view v-if="couponVisible" class="sheet-mask" @click="couponVisible = false">
      <view class="coupon-sheet" @click.stop>
        <view class="cart-sheet-head">
          <text class="cart-sheet-title">选择优惠券</text>
          <text class="cart-clear" @click="couponVisible = false">关闭</text>
        </view>
        <scroll-view class="coupon-list" scroll-y :show-scrollbar="false">
          <view
            :class="['coupon-row', selectedCouponId === null && 'is-active']"
            @click="chooseCoupon(null)"
          >
            <text class="coupon-row-name">不使用优惠券</text>
          </view>
          <view
            v-for="coupon in coupons"
            :key="coupon.id"
            :class="['coupon-row', selectedCouponId === coupon.id && 'is-active']"
            @click="chooseCoupon(coupon.id)"
          >
            <view class="coupon-row-main">
              <text class="coupon-row-name">{{ coupon.name }}</text>
              <text class="coupon-row-desc">满 ¥{{ fenToYuan(coupon.minAmount) }} 可用</text>
            </view>
            <text class="coupon-row-amount">-¥{{ fenToYuan(coupon.discountAmount) }}</text>
          </view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.menu-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background-color: $coffee-bg;

  /* H5 端需要减去原生导航栏高度；dvh 跟随移动端浏览器地址栏收起/展开 */
  /* #ifdef H5 */
  height: calc(100vh - var(--window-top));
  height: calc(100dvh - var(--window-top));
  /* #endif */
}

.shop-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 28rpx 32rpx 24rpx;
  background: linear-gradient(180deg, #f3ecdf 0%, $coffee-bg 100%);
}

.shop-meta {
  display: flex;
  flex-direction: column;
}

.shop-name {
  font-size: 40rpx;
  font-weight: 700;
  letter-spacing: 2rpx;
  color: $coffee-primary;
}

.shop-desc {
  margin-top: 6rpx;
  font-size: 22rpx;
  color: $coffee-muted;
}

.mine-entry {
  padding: 12rpx 28rpx;
  border: 2rpx solid rgba(60, 42, 33, 0.16);
  border-radius: 999rpx;
  font-size: 24rpx;
  color: $coffee-primary;
}

.offline-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14rpx 32rpx;
  background-color: rgba(180, 87, 63, 0.1);
  font-size: 22rpx;
  color: $coffee-danger;

  &.is-cache {
    background-color: rgba(213, 206, 163, 0.35);
    color: #7a6a3f;
  }
}

.offline-retry {
  padding-left: 20rpx;
  font-weight: 600;
  text-decoration: underline;
}

.seckill-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 0 24rpx 8rpx;
  padding: 20rpx 24rpx;
  border-radius: 24rpx;
  background: linear-gradient(120deg, #f3e6c9 0%, #e7d4a8 100%);
  border: 2rpx solid rgba(176, 137, 104, 0.25);
  box-shadow: 0 8rpx 20rpx rgba(176, 137, 104, 0.12);
}

.seckill-banner-left {
  display: flex;
  align-items: center;
}

.seckill-banner-badge {
  padding: 8rpx 16rpx;
  border-radius: 12rpx;
  background-color: #b4573f;
  color: #fff;
  font-size: 22rpx;
  font-weight: 700;
  letter-spacing: 2rpx;
}

.seckill-banner-text {
  display: flex;
  flex-direction: column;
  margin-left: 16rpx;
}

.seckill-banner-title {
  font-size: 28rpx;
  font-weight: 700;
  color: #5f4126;
}

.seckill-banner-desc {
  margin-top: 4rpx;
  font-size: 20rpx;
  color: #8a6a45;
}

.seckill-banner-action {
  font-size: 24rpx;
  font-weight: 600;
  color: #7a4a2a;
}

.menu-body {
  display: flex;
  flex: 1;
  min-height: 0;
}

.category-bar {
  width: 168rpx;
  height: 100%;
  background-color: $coffee-surface;
}

.category-item {
  position: relative;
  display: flex;
  align-items: center;
  padding: 30rpx 16rpx 30rpx 28rpx;
  font-size: 26rpx;
  color: $coffee-muted;
  transition: background-color 0.2s;

  &.is-active {
    background-color: $coffee-bg;
    color: $coffee-primary;
    font-weight: 600;

    .category-dot {
      opacity: 1;
    }
  }
}

.category-dot {
  width: 8rpx;
  height: 8rpx;
  margin-right: 12rpx;
  border-radius: 50%;
  background-color: $coffee-primary;
  opacity: 0;
}

.category-name {
  flex: 1;
  line-height: 1.3;
}

.product-list {
  flex: 1;
  height: 100%;
  padding: 0 24rpx;
  padding-bottom: 200rpx;
}

.category-section {
  padding-top: 20rpx;
}

.section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 8rpx 4rpx 16rpx;
}

.section-title {
  font-size: 28rpx;
  font-weight: 600;
  color: $coffee-primary;
}

.section-count {
  font-size: 22rpx;
  color: $coffee-muted;
}

.product-card {
  display: flex;
  padding: 20rpx;
  margin-bottom: 20rpx;
  background-color: $coffee-card;
  border-radius: 24rpx;
  box-shadow: 0 8rpx 24rpx rgba(60, 42, 33, 0.05);
}

.product-card.is-seckill {
  border: 2rpx solid rgba(213, 206, 163, 0.7);
}

.product-name-row {
  display: flex;
  align-items: center;
  min-width: 0;
}

.seckill-chip {
  flex-shrink: 0;
  margin-left: 10rpx;
  padding: 2rpx 10rpx;
  border-radius: 999rpx;
  background-color: rgba(180, 87, 63, 0.12);
  color: #b4573f;
  font-size: 18rpx;
  font-weight: 600;
}

.price-origin {
  margin-left: 10rpx;
  font-size: 20rpx;
  color: rgba(140, 123, 107, 0.6);
  text-decoration: line-through;
}

.product-cover {
  width: 168rpx;
  height: 168rpx;
  border-radius: 18rpx;
  flex-shrink: 0;
  background-color: $coffee-surface;
}

.product-cover-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 56rpx;
  font-weight: 700;
  color: $coffee-accent;
  background: linear-gradient(135deg, #4a352a 0%, #3c2a21 100%);
}

.product-main {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
  padding-left: 20rpx;
}

.product-name {
  flex: 1;
  min-width: 0;
  font-size: 30rpx;
  font-weight: 600;
  color: $coffee-text;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-hint {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: $coffee-muted;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stock-hint {
  margin-top: 4rpx;
  font-size: 20rpx;
  color: $coffee-muted;

  &.is-empty {
    color: #b4573f;
  }
}

.product-bottom {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-top: auto;
  padding-top: 12rpx;
}

.product-price {
  display: flex;
  align-items: baseline;
  color: $coffee-primary;
}

.price-symbol {
  font-size: 22rpx;
}

.price-value {
  font-size: 36rpx;
  font-weight: 700;
  line-height: 1;
}

.price-suffix {
  margin-left: 6rpx;
  font-size: 20rpx;
  color: $coffee-muted;
}

.spec-btn {
  padding: 10rpx 26rpx;
  border-radius: 999rpx;
  background-color: $coffee-primary;
  color: #fff;
  font-size: 24rpx;
  line-height: 1.2;
}

.product-card.is-sold-out {
  opacity: 0.62;
}

.sold-out-tag {
  padding: 10rpx 26rpx;
  border-radius: 999rpx;
  background-color: rgba(60, 42, 33, 0.12);
  color: $coffee-muted;
  font-size: 24rpx;
}

.list-end {
  padding: 40rpx 0 60rpx;
  text-align: center;
  font-size: 22rpx;
  color: rgba(140, 123, 107, 0.7);
}

.state-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 120rpx 0;
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

/* ------------------------------ 购物袋悬浮栏 ------------------------------ */
.cart-bar {
  position: fixed;
  left: 24rpx;
  right: 24rpx;
  bottom: 24rpx;
  z-index: 20;
  display: flex;
  align-items: center;
  height: 104rpx;
  padding: 0 12rpx 0 20rpx;
  background-color: $coffee-primary;
  border-radius: 52rpx;
  box-shadow: 0 16rpx 36rpx rgba(60, 42, 33, 0.28);

  /* 适配 iPhone 底部安全区 */
  bottom: calc(24rpx + constant(safe-area-inset-bottom));
  bottom: calc(24rpx + env(safe-area-inset-bottom));
}

.cart-summary {
  display: flex;
  flex: 1;
  align-items: center;
  height: 100%;
}

.cart-badge {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  background-color: $coffee-accent;
  color: $coffee-primary;
  font-size: 28rpx;
  font-weight: 700;
}

.cart-amount {
  margin-left: 18rpx;
  color: #fff;
}

.cart-currency {
  font-size: 22rpx;
}

.cart-price {
  font-size: 36rpx;
  font-weight: 700;
}

.cart-label {
  display: block;
  margin-top: 2rpx;
  font-size: 20rpx;
  color: rgba(250, 247, 242, 0.6);
}

.checkout-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 80rpx;
  padding: 0 44rpx;
  border-radius: 40rpx;
  background-color: $coffee-accent;
  color: $coffee-primary;
  font-size: 28rpx;
  font-weight: 600;
}

/* ------------------------------ 弹层通用 ------------------------------ */
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

.sheet-product-info {
  display: flex;
  flex-direction: column;
  padding-left: 20rpx;
}

.sheet-product-name {
  font-size: 34rpx;
  font-weight: 700;
  color: $coffee-text;
}

.sheet-product-desc {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: $coffee-muted;
}

.sheet-scroll {
  height: 40vh;
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

  &.is-invalid {
    .spec-group-name {
      color: $coffee-danger;
    }
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
  padding: 20rpx 0 24rpx;
  border-top: 2rpx solid $coffee-line;
}

.sheet-price {
  display: flex;
  align-items: baseline;
  flex: 1;
  color: $coffee-primary;
}

.sheet-price-value {
  font-size: 40rpx;
  font-weight: 700;
  line-height: 1;
}

.sheet-price-unit {
  margin-left: 6rpx;
  font-size: 20rpx;
  color: $coffee-muted;
}

.qty {
  display: flex;
  align-items: center;
  margin-right: 24rpx;
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

.add-cart-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 80rpx;
  padding: 0 40rpx;
  border-radius: 40rpx;
  background-color: $coffee-primary;
  color: #fff;
  font-size: 28rpx;
  font-weight: 600;
}

/* ------------------------------ 购物袋面板 ------------------------------ */
.cart-sheet {
  width: 100%;
  padding: 28rpx 32rpx;
  background-color: $coffee-card;
  border-radius: 36rpx 36rpx 0 0;
  animation: sheet-in 0.28s cubic-bezier(0.22, 0.61, 0.36, 1);
  padding-bottom: calc(28rpx + env(safe-area-inset-bottom));
}

.cart-sheet-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 20rpx;
}

.cart-sheet-title {
  font-size: 32rpx;
  font-weight: 700;
  color: $coffee-text;
}

.cart-clear {
  font-size: 24rpx;
  color: $coffee-muted;
}

.cart-items {
  border-top: 2rpx solid $coffee-line;

  /* #ifdef H5 */
  overscroll-behavior: contain;
  /* #endif */
}

.cart-item {
  display: flex;
  align-items: center;
  height: 132rpx;
  border-bottom: 2rpx solid $coffee-line;
}

.cart-item-info {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
}

.cart-item-name {
  font-size: 28rpx;
  font-weight: 600;
  color: $coffee-text;
}

.cart-item-spec {
  margin-top: 4rpx;
  font-size: 22rpx;
  color: $coffee-muted;
}

.cart-item-price {
  margin-top: 4rpx;
  font-size: 22rpx;
  color: $coffee-primary;
}

.field-row {
  display: flex;
  align-items: center;
  padding: 24rpx 0;
  border-bottom: 2rpx solid $coffee-line;
}

.field-label {
  width: 140rpx;
  font-size: 26rpx;
  color: $coffee-text;
}

.pickup-switch {
  display: flex;
  padding: 4rpx;
  border-radius: 999rpx;
  background-color: $coffee-bg;
}

.pickup-item {
  padding: 10rpx 30rpx;
  border-radius: 999rpx;
  font-size: 24rpx;
  color: $coffee-muted;

  &.is-active {
    background-color: $coffee-primary;
    color: #fff;
  }
}

.remark-input {
  flex: 1;
  height: 60rpx;
  font-size: 26rpx;
  color: $coffee-text;
}

.remark-placeholder {
  color: rgba(140, 123, 107, 0.6);
}

.coupon-select {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: flex-end;
}

.coupon-selected {
  font-size: 26rpx;
  font-weight: 600;
  color: #b4573f;
}

.coupon-empty-text {
  font-size: 24rpx;
  color: $coffee-muted;
}

.coupon-arrow {
  margin-left: 8rpx;
  font-size: 32rpx;
  color: $coffee-muted;
}

.cart-sheet-footer {
  display: flex;
  align-items: center;
  padding-top: 24rpx;
}

.cart-total {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: stretch;
  gap: 4rpx;
}

.cart-total-line {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.cart-total-label {
  font-size: 22rpx;
  color: $coffee-muted;
}

.cart-total-value {
  margin-left: 12rpx;
  font-size: 26rpx;
  color: $coffee-primary;

  &.is-pay {
    font-size: 38rpx;
    font-weight: 700;
  }
}

.cart-discount {
  margin-left: 12rpx;
  font-size: 26rpx;
  color: #b4573f;
}

/* ------------------------------ 优惠券面板 ------------------------------ */
.coupon-sheet {
  width: 100%;
  padding: 28rpx 32rpx;
  background-color: $coffee-card;
  border-radius: 36rpx 36rpx 0 0;
  animation: sheet-in 0.28s cubic-bezier(0.22, 0.61, 0.36, 1);
  padding-bottom: calc(28rpx + env(safe-area-inset-bottom));
}

.coupon-list {
  max-height: 50vh;
  border-top: 2rpx solid $coffee-line;

  /* #ifdef H5 */
  overscroll-behavior: contain;
  /* #endif */
}

.coupon-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 28rpx 8rpx;
  border-bottom: 2rpx solid $coffee-line;

  &.is-active {
    background-color: rgba(213, 206, 163, 0.16);
  }
}

.coupon-row-main {
  display: flex;
  flex-direction: column;
}

.coupon-row-name {
  font-size: 28rpx;
  font-weight: 600;
  color: $coffee-text;
}

.coupon-row-desc {
  margin-top: 6rpx;
  font-size: 22rpx;
  color: $coffee-muted;
}

.coupon-row-amount {
  font-size: 30rpx;
  font-weight: 700;
  color: #b4573f;
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

/* 尊重系统「减少动态效果」设置 */
@media (prefers-reduced-motion: reduce) {
  .sheet,
  .cart-sheet,
  .coupon-sheet,
  .sheet-mask {
    animation: none;
  }

  .spec-option,
  .category-item {
    transition: none;
  }
}
</style>
