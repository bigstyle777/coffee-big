<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const pageTitle = computed(() => String(route.meta.title || '店长后台'))
const adminName = computed(() => authStore.admin?.nickname || authStore.admin?.username || '店长')
const avatarText = computed(() => adminName.value.slice(0, 1))

async function handleLogout(): Promise<void> {
  try {
    await ElMessageBox.confirm('退出后需要重新输入账号密码登录。', '退出登录', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  await authStore.logout()
  router.replace('/login')
}
</script>

<template>
  <div class="admin-shell">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark">C</div>
        <div class="brand-text">
          <span class="brand-name">拾光咖啡</span>
          <span class="brand-sub">店长后台</span>
        </div>
      </div>

      <el-menu
        :default-active="route.path"
        router
        class="side-menu"
        background-color="transparent"
        text-color="#c9b8a8"
        active-text-color="#f5ede4"
      >
        <el-menu-item index="/dashboard">
          <el-icon><DataLine /></el-icon>
          <span>首页看板</span>
        </el-menu-item>
        <el-menu-item index="/products">
          <el-icon><CoffeeCup /></el-icon>
          <span>商品管理</span>
        </el-menu-item>
        <el-menu-item index="/activities">
          <el-icon><Present /></el-icon>
          <span>活动管理</span>
        </el-menu-item>
        <el-menu-item index="/orders">
          <el-icon><Tickets /></el-icon>
          <span>订单管理</span>
        </el-menu-item>
      </el-menu>

      <div class="sidebar-footer">单店长版 · 数据实时</div>
    </aside>

    <div class="main">
      <header class="topbar">
        <div class="topbar-title">{{ pageTitle }}</div>
        <div class="topbar-right">
          <span class="admin-chip">
            <span class="avatar">{{ avatarText }}</span>
            {{ adminName }}
          </span>
          <el-button text @click="handleLogout">退出登录</el-button>
        </div>
      </header>

      <main class="content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
.admin-shell {
  display: flex;
  height: 100%;
  min-height: 100vh;
}

.sidebar {
  display: flex;
  flex-direction: column;
  width: 232px;
  flex-shrink: 0;
  background: linear-gradient(180deg, #2b1d16 0%, #3c2a21 60%, #34241c 100%);
  color: #f5ede4;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 26px 22px 22px;
}

.brand-mark {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background-color: rgba(213, 206, 163, 0.16);
  border: 1px solid rgba(213, 206, 163, 0.35);
  color: #d5cea3;
  font-size: 20px;
  font-weight: 700;
}

.brand-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.brand-name {
  font-size: 17px;
  font-weight: 600;
  letter-spacing: 0.06em;
}

.brand-sub {
  font-size: 11px;
  color: rgba(245, 237, 228, 0.55);
  letter-spacing: 0.18em;
}

.side-menu {
  flex: 1;
  border-right: none;
  padding: 8px 12px;
}

.side-menu :deep(.el-menu-item) {
  height: 46px;
  margin-bottom: 6px;
  border-radius: 10px;
  font-size: 14px;
}

.side-menu :deep(.el-menu-item:hover) {
  background-color: rgba(213, 206, 163, 0.1);
}

.side-menu :deep(.el-menu-item.is-active) {
  background-color: rgba(213, 206, 163, 0.18);
}

.sidebar-footer {
  padding: 18px 22px 24px;
  font-size: 11px;
  color: rgba(245, 237, 228, 0.35);
  letter-spacing: 0.08em;
}

.main {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 64px;
  flex-shrink: 0;
  padding: 0 28px;
  background-color: #fffdf9;
  border-bottom: 1px solid var(--coffee-line);
}

.topbar-title {
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 0.02em;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.admin-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--coffee-primary);
}

.avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background-color: rgba(213, 206, 163, 0.45);
  color: #6f4c2c;
  font-size: 12px;
  font-weight: 600;
}

.content {
  flex: 1;
  min-height: 0;
  padding: 22px 28px 32px;
  overflow-y: auto;
}

@media (max-width: 900px) {
  .sidebar {
    width: 76px;
  }

  .brand-text,
  .sidebar-footer,
  .side-menu :deep(.el-menu-item span) {
    display: none;
  }

  .brand {
    justify-content: center;
    padding: 22px 12px;
  }

  .content {
    padding: 16px;
  }
}
</style>
