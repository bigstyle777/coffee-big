<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const formRef = ref<FormInstance>()
const form = reactive({ username: '', password: '' })
const submitting = ref(false)
const showDevHint = import.meta.env.DEV

const rules: FormRules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleSubmit(): Promise<void> {
  if (!formRef.value) {
    return
  }
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }

  submitting.value = true
  try {
    await authStore.login(form.username, form.password)
    ElMessage.success('登录成功')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard'
    router.replace(redirect)
  } catch {
    // 错误提示由请求层统一处理
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <section class="brand-panel">
      <div class="brand-inner">
        <div class="brand-mark">C</div>
        <h1>拾光咖啡</h1>
        <p class="slogan">把每一杯咖啡，准时交到顾客手里</p>
        <div class="brand-points">
          <div class="point">
            <span class="point-index">01</span>
            <div>
              <strong>今日生意一眼看清</strong>
              <p>订单、营收、待出餐与热销榜</p>
            </div>
          </div>
          <div class="point">
            <span class="point-index">02</span>
            <div>
              <strong>商品与规格集中维护</strong>
              <p>上下架、库存、图片、糖度冰度</p>
            </div>
          </div>
          <div class="point">
            <span class="point-index">03</span>
            <div>
              <strong>接单到出餐一气呵成</strong>
              <p>筛选、详情、取消与流水留痕</p>
            </div>
          </div>
        </div>
      </div>
      <div class="brand-footer">COFFEE BIG · STORE CONSOLE</div>
    </section>

    <section class="form-panel">
      <div class="login-card">
        <div class="login-head">
          <span class="eyebrow">店长后台</span>
          <h2>欢迎回来</h2>
          <p class="muted">请使用店长账号登录，顾客小程序账号无法访问后台。</p>
        </div>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          size="large"
          @submit.prevent="handleSubmit"
        >
          <el-form-item label="账号" prop="username">
            <el-input
              v-model="form.username"
              placeholder="请输入后台账号"
              autocomplete="username"
              :prefix-icon="'User'"
              @keyup.enter="handleSubmit"
            />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="请输入密码"
              show-password
              autocomplete="current-password"
              :prefix-icon="'Lock'"
              @keyup.enter="handleSubmit"
            />
          </el-form-item>
          <el-button
            type="primary"
            class="submit-btn"
            :loading="submitting"
            @click="handleSubmit"
          >
            进入后台
          </el-button>
        </el-form>

        <p v-if="showDevHint" class="dev-hint">
          本地初始账号请查看后端 ADMIN_USERNAME / ADMIN_PASSWORD 环境变量，仓库不提供默认密码。
        </p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.login-page {
  display: flex;
  min-height: 100vh;
  background-color: var(--coffee-bg);
}

.brand-panel {
  position: relative;
  display: flex;
  flex: 1.1;
  flex-direction: column;
  justify-content: space-between;
  padding: 64px 72px 40px;
  background: radial-gradient(circle at 20% 15%, #5a4136 0%, #3c2a21 45%, #241812 100%);
  color: #f5ede4;
  overflow: hidden;
}

.brand-panel::after {
  content: '';
  position: absolute;
  right: -160px;
  bottom: -160px;
  width: 460px;
  height: 460px;
  border-radius: 50%;
  border: 1px solid rgba(213, 206, 163, 0.18);
  box-shadow: 0 0 0 60px rgba(213, 206, 163, 0.04),
    0 0 0 120px rgba(213, 206, 163, 0.03);
}

.brand-inner {
  position: relative;
  z-index: 1;
  max-width: 460px;
}

.brand-mark {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 54px;
  height: 54px;
  border-radius: 16px;
  background-color: rgba(213, 206, 163, 0.14);
  border: 1px solid rgba(213, 206, 163, 0.4);
  color: #d5cea3;
  font-size: 26px;
  font-weight: 700;
}

.brand-inner h1 {
  margin: 28px 0 10px;
  font-size: 34px;
  font-weight: 600;
  letter-spacing: 0.08em;
}

.slogan {
  margin: 0 0 48px;
  font-size: 14px;
  color: rgba(245, 237, 228, 0.6);
  letter-spacing: 0.08em;
}

.brand-points {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.point {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.point-index {
  font-size: 12px;
  color: rgba(213, 206, 163, 0.65);
  letter-spacing: 0.12em;
  padding-top: 3px;
}

.point strong {
  display: block;
  margin-bottom: 6px;
  font-size: 15px;
  font-weight: 500;
}

.point p {
  margin: 0;
  font-size: 12px;
  color: rgba(245, 237, 228, 0.5);
}

.brand-footer {
  position: relative;
  z-index: 1;
  font-size: 11px;
  color: rgba(245, 237, 228, 0.3);
  letter-spacing: 0.22em;
}

.form-panel {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  padding: 40px;
}

.login-card {
  width: 100%;
  max-width: 400px;
}

.login-head {
  margin-bottom: 32px;
}

.eyebrow {
  display: inline-block;
  margin-bottom: 14px;
  padding: 4px 12px;
  border-radius: 999px;
  background-color: rgba(213, 206, 163, 0.32);
  color: #7a5a3a;
  font-size: 12px;
  letter-spacing: 0.12em;
}

.login-head h2 {
  margin: 0 0 10px;
  font-size: 26px;
  font-weight: 600;
  letter-spacing: 0.02em;
}

.login-head p {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
}

.submit-btn {
  width: 100%;
  height: 46px;
  margin-top: 8px;
  font-size: 15px;
  letter-spacing: 0.08em;
}

.dev-hint {
  margin: 22px 0 0;
  padding: 12px 14px;
  border-radius: 10px;
  background-color: rgba(213, 206, 163, 0.18);
  color: #7a6a3f;
  font-size: 12px;
  line-height: 1.6;
}

@media (max-width: 900px) {
  .brand-panel {
    display: none;
  }

  .form-panel {
    padding: 24px;
  }
}
</style>
