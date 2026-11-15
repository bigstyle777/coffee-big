<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getDashboardSummary } from '@/api/admin'
import type { DashboardSummary, OrderStatus } from '@/types'
import {
  formatDateTime,
  formatMoney,
  ORDER_STATUS_META,
  orderItemsSummary,
} from '@/utils/format'

const router = useRouter()
const loading = ref(false)
const summary = ref<DashboardSummary | null>(null)

const stats = computed(() => [
  {
    label: '今日订单',
    value: summary.value?.todayOrderCount ?? 0,
    suffix: '单',
    icon: 'Tickets',
    tone: 'coffee',
  },
  {
    label: '今日营收',
    value: formatMoney(summary.value?.todayRevenue ?? 0),
    suffix: '',
    icon: 'Money',
    tone: 'gold',
  },
  {
    label: '待出餐',
    value: summary.value?.todayPendingCount ?? 0,
    suffix: '单',
    icon: 'Bell',
    tone: 'orange',
  },
  {
    label: '今日已出餐',
    value: summary.value?.todayReadyCount ?? 0,
    suffix: '单',
    icon: 'CircleCheck',
    tone: 'green',
  },
])

const maxHotQuantity = computed(() => {
  const quantities = summary.value?.hotProducts.map((item) => item.totalQuantity) ?? []
  return Math.max(...quantities, 1)
})

function statusMeta(status: OrderStatus) {
  return ORDER_STATUS_META[status] ?? { label: status, type: 'info' as const }
}

async function loadSummary(): Promise<void> {
  loading.value = true
  try {
    summary.value = await getDashboardSummary()
  } catch {
    // 错误提示由请求层统一处理
  } finally {
    loading.value = false
  }
}

onMounted(loadSummary)
</script>

<template>
  <div v-loading="loading" class="dashboard-page">
    <div class="page-head">
      <div>
        <h2>今日经营概览</h2>
        <p class="muted">数据实时取自订单库，支付成功后即计入营收与待出餐。</p>
      </div>
      <div class="head-actions">
        <el-button :icon="'Refresh'" @click="loadSummary">刷新</el-button>
        <el-button type="primary" :icon="'Tickets'" @click="router.push('/orders')">
          进入订单管理
        </el-button>
      </div>
    </div>

    <div class="stat-grid">
      <div v-for="stat in stats" :key="stat.label" class="stat-card" :class="`tone-${stat.tone}`">
        <div class="stat-top">
          <span class="stat-label">{{ stat.label }}</span>
          <span class="stat-icon">
            <el-icon><component :is="stat.icon" /></el-icon>
          </span>
        </div>
        <div class="stat-value">
          {{ stat.value }}<span v-if="stat.suffix" class="stat-suffix">{{ stat.suffix }}</span>
        </div>
      </div>
    </div>

    <div class="board-grid">
      <section class="page-card hot-card">
        <div class="card-header">
          <span class="card-title">热销饮品 TOP3</span>
          <span class="muted small">按今日售出杯数</span>
        </div>
        <div class="card-body">
          <el-empty
            v-if="!summary || summary.hotProducts.length === 0"
            description="今天还没有已支付订单"
            :image-size="72"
          />
          <div v-else class="hot-list">
            <div
              v-for="(item, index) in summary.hotProducts"
              :key="item.productName"
              class="hot-item"
            >
              <div class="hot-rank" :class="`rank-${index + 1}`">{{ index + 1 }}</div>
              <div class="hot-main">
                <div class="hot-row">
                  <span class="hot-name">{{ item.productName }}</span>
                  <span class="hot-count">{{ item.totalQuantity }} 杯</span>
                </div>
                <div class="hot-bar">
                  <span
                    class="hot-bar-inner"
                    :style="{ width: `${Math.max((item.totalQuantity / maxHotQuantity) * 100, 8)}%` }"
                  />
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section class="page-card recent-card">
        <div class="card-header">
          <span class="card-title">最近订单</span>
          <el-button text @click="router.push('/orders')">查看全部</el-button>
        </div>
        <div class="card-body">
          <el-table
            :data="summary?.recentOrders ?? []"
            empty-text="暂无订单"
            size="default"
            style="width: 100%"
          >
            <el-table-column prop="orderNo" label="订单号" min-width="180" />
            <el-table-column label="取餐码" width="92">
              <template #default="{ row }">
                <span class="pickup-code">{{ row.pickupCode || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="商品" min-width="160">
              <template #default="{ row }">
                <span class="muted">{{ orderItemsSummary(row) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="实付" width="100" align="right">
              <template #default="{ row }">
                <span class="money">{{ formatMoney(row.payAmount) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="96" align="center">
              <template #default="{ row }">
                <el-tag :type="statusMeta(row.status).type" effect="light" round>
                  {{ statusMeta(row.status).label }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="下单时间" width="164">
              <template #default="{ row }">
                <span class="muted">{{ formatDateTime(row.createTime) }}</span>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.dashboard-page {
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

.head-actions {
  display: flex;
  gap: 10px;
}

.small {
  font-size: 12px;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.stat-card {
  position: relative;
  padding: 20px 22px;
  border-radius: 14px;
  background-color: #fff;
  border: 1px solid var(--coffee-line);
  box-shadow: 0 10px 30px rgba(60, 42, 33, 0.04);
  overflow: hidden;
}

.stat-card::after {
  content: '';
  position: absolute;
  right: -30px;
  bottom: -46px;
  width: 120px;
  height: 120px;
  border-radius: 50%;
  opacity: 0.08;
  background-color: currentColor;
}

.tone-coffee {
  color: #6f462a;
}

.tone-gold {
  color: #a3833f;
}

.tone-orange {
  color: #b4573f;
}

.tone-green {
  color: #4f7a52;
}

.stat-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.stat-label {
  font-size: 13px;
  color: var(--coffee-muted);
  letter-spacing: 0.04em;
}

.stat-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background-color: rgba(140, 94, 60, 0.09);
  font-size: 17px;
}

.stat-value {
  margin-top: 18px;
  font-size: 30px;
  font-weight: 600;
  letter-spacing: -0.01em;
  font-variant-numeric: tabular-nums;
  color: var(--coffee-text);
}

.stat-suffix {
  margin-left: 4px;
  font-size: 13px;
  font-weight: 400;
  color: var(--coffee-muted);
}

.board-grid {
  display: grid;
  grid-template-columns: minmax(300px, 1fr) minmax(0, 1.7fr);
  gap: 18px;
}

.hot-card,
.recent-card {
  min-width: 0;
}

.hot-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.hot-item {
  display: flex;
  align-items: center;
  gap: 14px;
}

.hot-rank {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  flex-shrink: 0;
  border-radius: 9px;
  background-color: #f0ebe2;
  color: var(--coffee-muted);
  font-size: 13px;
  font-weight: 600;
}

.hot-rank.rank-1 {
  background-color: #3c2a21;
  color: #e8d9b6;
}

.hot-rank.rank-2 {
  background-color: #d5cea3;
  color: #5f4a2f;
}

.hot-rank.rank-3 {
  background-color: #e5d9c8;
  color: #7a5a3a;
}

.hot-main {
  flex: 1;
  min-width: 0;
}

.hot-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 8px;
}

.hot-name {
  font-size: 14px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.hot-count {
  font-size: 12px;
  color: var(--coffee-muted);
  flex-shrink: 0;
  margin-left: 10px;
}

.hot-bar {
  height: 6px;
  border-radius: 999px;
  background-color: #f0ebe2;
  overflow: hidden;
}

.hot-bar-inner {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, #b08968, #d5cea3);
}

.pickup-code {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 6px;
  background-color: #f5efe5;
  color: #7a5a3a;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.06em;
}

@media (max-width: 1180px) {
  .stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .board-grid {
    grid-template-columns: 1fr;
  }
}
</style>
