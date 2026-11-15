import { createRouter, createWebHashHistory } from 'vue-router'
import { getToken } from '@/utils/auth'
import AdminLayout from '@/layouts/AdminLayout.vue'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true, title: '登录' },
    },
    {
      path: '/',
      component: AdminLayout,
      redirect: '/dashboard',
      children: [
        {
          path: 'dashboard',
          name: 'dashboard',
          component: () => import('@/views/DashboardView.vue'),
          meta: { title: '首页看板' },
        },
        {
          path: 'products',
          name: 'products',
          component: () => import('@/views/ProductView.vue'),
          meta: { title: '商品管理' },
        },
        {
          path: 'activities',
          name: 'activities',
          component: () => import('@/views/ActivityView.vue'),
          meta: { title: '活动管理' },
        },
        {
          path: 'orders',
          name: 'orders',
          component: () => import('@/views/OrderView.vue'),
          meta: { title: '订单管理' },
        },
      ],
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/dashboard',
    },
  ],
})

router.beforeEach((to) => {
  const token = getToken()
  if (!to.meta.public && !token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.path === '/login' && token) {
    return { path: '/dashboard' }
  }
  return true
})

export default router
