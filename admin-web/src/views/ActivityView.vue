<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  ElMessage,
  ElMessageBox,
  type FormInstance,
  type FormRules,
} from 'element-plus'
import {
  bindActivityProduct,
  createActivity,
  disableActivityProduct,
  getActivityDetail,
  listAllProducts,
  pageActivities,
  preheatActivity,
  updateActivity,
  updateActivityOpen,
  updateActivityProduct,
} from '@/api/admin'
import type {
  ActivityStatus,
  ActivityType,
  AdminActivity,
  AdminActivityDetail,
  AdminActivityProduct,
  AdminProduct,
  SaveActivityPayload,
} from '@/types'
import { formatDateTime, formatMoney } from '@/utils/format'

const TYPE_META: Record<ActivityType, { label: string; tag: 'danger' | 'warning' }> = {
  SECKILL: { label: '秒杀', tag: 'danger' },
  FULL_REDUCTION: { label: '满减', tag: 'warning' },
}

const STATUS_META: Record<ActivityStatus, { label: string; tag: 'info' | 'success' | 'danger' }> = {
  NOT_STARTED: { label: '未开始', tag: 'info' },
  RUNNING: { label: '进行中', tag: 'success' },
  ENDED: { label: '已结束', tag: 'info' },
  CLOSED: { label: '已关闭', tag: 'danger' },
}

function typeMeta(type: ActivityType) {
  return TYPE_META[type]
}

function statusMeta(status: ActivityStatus) {
  return STATUS_META[status]
}

const loading = ref(false)
const activities = ref<AdminActivity[]>([])
const total = ref(0)

const query = reactive<{
  pageNum: number
  pageSize: number
  keyword: string
  type?: ActivityType
  status?: ActivityStatus
}>({
  pageNum: 1,
  pageSize: 10,
  keyword: '',
  type: undefined,
  status: undefined,
})

const editVisible = ref(false)
const editSubmitting = ref(false)
const editFormRef = ref<FormInstance>()
const editForm = reactive({
  id: 0,
  title: '',
  type: 'SECKILL' as ActivityType,
  timeRange: [] as string[],
  fullAmountYuan: 0,
  reduceAmountYuan: 0,
  remark: '',
})

const isEditing = computed(() => editForm.id > 0)

const validateTimeRange = (
  _rule: unknown,
  value: string[],
  callback: (error?: Error) => void,
): void => {
  if (!value || value.length !== 2) {
    callback(new Error('请选择活动起止时间'))
    return
  }
  callback()
}

const editRules: FormRules = {
  title: [{ required: true, message: '请输入活动名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择活动类型', trigger: 'change' }],
  timeRange: [{ validator: validateTimeRange, trigger: 'change' }],
}

const manageVisible = ref(false)
const manageLoading = ref(false)
const detail = ref<AdminActivityDetail | null>(null)
const preheating = ref(false)

const isSeckill = computed(() => detail.value?.type === 'SECKILL')
const canEditBindings = computed(() => detail.value?.isOpen === 0)
const activeProducts = computed(
  () => detail.value?.products.filter((product) => product.status === 1) ?? [],
)
const preheatedCount = computed(
  () => activeProducts.value.filter((product) => product.preheated).length,
)

const bindVisible = ref(false)
const bindSubmitting = ref(false)
const bindFormRef = ref<FormInstance>()
const productOptions = ref<AdminProduct[]>([])
const bindForm = reactive({
  productId: undefined as number | undefined,
  priceYuan: 0,
  stock: 0,
  limitPerUser: 1,
})

const availableProducts = computed(() => {
  const boundIds = new Set(
    activeProducts.value.map((product) => product.productId),
  )
  return productOptions.value.filter((product) => {
    if (boundIds.has(product.id)) {
      return false
    }
    return isSeckill.value ? product.stock > 0 : true
  })
})

const bindingEditVisible = ref(false)
const bindingEditSubmitting = ref(false)
const bindingEditForm = reactive({
  bindingId: 0,
  productName: '',
  priceYuan: 0,
  stock: 0,
  limitPerUser: 1,
})

async function loadActivities(): Promise<void> {
  loading.value = true
  try {
    const result = await pageActivities({ ...query })
    activities.value = result.records
    total.value = result.total
  } catch {
    // 错误提示由请求层统一处理
  } finally {
    loading.value = false
  }
}

function handleSearch(): void {
  query.pageNum = 1
  loadActivities()
}

function handleReset(): void {
  query.keyword = ''
  query.type = undefined
  query.status = undefined
  query.pageNum = 1
  loadActivities()
}

function handleSizeChange(): void {
  query.pageNum = 1
  loadActivities()
}

function resetEditForm(): void {
  editForm.id = 0
  editForm.title = ''
  editForm.type = 'SECKILL'
  editForm.timeRange = []
  editForm.fullAmountYuan = 0
  editForm.reduceAmountYuan = 0
  editForm.remark = ''
  editFormRef.value?.clearValidate()
}

function openCreate(): void {
  resetEditForm()
  editVisible.value = true
}

function openEdit(activity: AdminActivity): void {
  resetEditForm()
  editForm.id = activity.id
  editForm.title = activity.title
  editForm.type = activity.type
  editForm.timeRange = [activity.startTime, activity.endTime]
  editForm.fullAmountYuan = activity.fullAmount / 100
  editForm.reduceAmountYuan = activity.reduceAmount / 100
  editForm.remark = activity.remark
  editVisible.value = true
}

function handleTypeChange(value: ActivityType): void {
  if (value === 'SECKILL') {
    editForm.fullAmountYuan = 0
    editForm.reduceAmountYuan = 0
  } else if (editForm.fullAmountYuan <= 0 && editForm.reduceAmountYuan <= 0) {
    editForm.fullAmountYuan = 30
    editForm.reduceAmountYuan = 5
  }
}

async function submitEdit(): Promise<void> {
  if (!editFormRef.value) {
    return
  }
  const valid = await editFormRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }
  if (editForm.timeRange.length !== 2) {
    ElMessage.warning('请选择活动起止时间')
    return
  }
  if (editForm.type === 'FULL_REDUCTION') {
    if (editForm.fullAmountYuan <= 0 || editForm.reduceAmountYuan <= 0) {
      ElMessage.warning('满减门槛与金额必须大于 0')
      return
    }
    if (editForm.reduceAmountYuan > editForm.fullAmountYuan) {
      ElMessage.warning('满减金额不能大于门槛金额')
      return
    }
  }

  const payload: SaveActivityPayload = {
    title: editForm.title.trim(),
    type: editForm.type,
    startTime: editForm.timeRange[0],
    endTime: editForm.timeRange[1],
    fullAmount: editForm.type === 'FULL_REDUCTION'
      ? Math.round(editForm.fullAmountYuan * 100)
      : 0,
    reduceAmount: editForm.type === 'FULL_REDUCTION'
      ? Math.round(editForm.reduceAmountYuan * 100)
      : 0,
    remark: editForm.remark,
  }

  editSubmitting.value = true
  try {
    if (isEditing.value) {
      await updateActivity(editForm.id, payload)
      ElMessage.success('活动已更新')
    } else {
      await createActivity(payload)
      ElMessage.success('活动已创建，默认关闭')
    }
    editVisible.value = false
    await loadActivities()
  } catch {
    // 错误提示由请求层统一处理
  } finally {
    editSubmitting.value = false
  }
}

async function handleOpenChange(activity: AdminActivity): Promise<void> {
  const nextOpen = activity.isOpen
  try {
    await updateActivityOpen(activity.id, nextOpen === 1)
    ElMessage.success(nextOpen === 1 ? '活动已开启' : '活动已关闭')
    await loadActivities()
  } catch {
    activity.isOpen = nextOpen === 1 ? 0 : 1
  }
}

async function loadDetail(activityId: number): Promise<void> {
  manageLoading.value = true
  try {
    detail.value = await getActivityDetail(activityId)
  } catch {
    // 错误提示由请求层统一处理
  } finally {
    manageLoading.value = false
  }
}

async function openManage(activity: AdminActivity): Promise<void> {
  manageVisible.value = true
  detail.value = null
  await loadDetail(activity.id)
}

async function handlePreheat(): Promise<void> {
  if (!detail.value) {
    return
  }
  preheating.value = true
  try {
    detail.value = await preheatActivity(detail.value.id)
    ElMessage.success('库存预热完成')
    await loadActivities()
  } catch {
    // 错误提示由请求层统一处理
  } finally {
    preheating.value = false
  }
}

async function openBind(): Promise<void> {
  if (!detail.value) {
    return
  }
  try {
    const products = await listAllProducts()
    productOptions.value = products.filter((product) => product.status === 1)
    bindForm.productId = undefined
    bindForm.priceYuan = 0
    bindForm.stock = 0
    bindForm.limitPerUser = 1
    bindVisible.value = true
  } catch {
    // 错误提示由请求层统一处理
  }
}

function handleProductChange(productId: number): void {
  const product = productOptions.value.find((item) => item.id === productId)
  if (!product) {
    return
  }
  if (isSeckill.value) {
    bindForm.priceYuan = Math.max(Math.round(product.price * 0.8) / 100, 0.01)
    bindForm.stock = Math.min(product.stock, 50)
    bindForm.limitPerUser = 1
  }
}

async function submitBind(): Promise<void> {
  if (!detail.value || !bindForm.productId) {
    ElMessage.warning('请选择要绑定的商品')
    return
  }
  if (isSeckill.value) {
    if (bindForm.priceYuan <= 0 || bindForm.stock <= 0 || bindForm.limitPerUser <= 0) {
      ElMessage.warning('活动价、限量与限购必须大于 0')
      return
    }
  }

  bindSubmitting.value = true
  try {
    await bindActivityProduct(detail.value.id, {
      productId: bindForm.productId,
      activityPrice: isSeckill.value ? Math.round(bindForm.priceYuan * 100) : 0,
      activityStock: isSeckill.value ? bindForm.stock : 0,
      limitPerUser: isSeckill.value ? bindForm.limitPerUser : 1,
    })
    ElMessage.success('商品已绑定，请重新预热后再开启活动')
    bindVisible.value = false
    await loadDetail(detail.value.id)
    await loadActivities()
  } catch {
    // 错误提示由请求层统一处理
  } finally {
    bindSubmitting.value = false
  }
}

function openBindingEdit(binding: AdminActivityProduct): void {
  bindingEditForm.bindingId = binding.bindingId
  bindingEditForm.productName = binding.productName
  bindingEditForm.priceYuan = binding.activityPrice / 100
  bindingEditForm.stock = binding.activityStock
  bindingEditForm.limitPerUser = binding.limitPerUser
  bindingEditVisible.value = true
}

async function submitBindingEdit(): Promise<void> {
  if (!detail.value) {
    return
  }
  if (bindingEditForm.priceYuan <= 0
    || bindingEditForm.stock <= 0
    || bindingEditForm.limitPerUser <= 0) {
    ElMessage.warning('活动价、限量与限购必须大于 0')
    return
  }

  bindingEditSubmitting.value = true
  try {
    await updateActivityProduct(detail.value.id, bindingEditForm.bindingId, {
      activityPrice: Math.round(bindingEditForm.priceYuan * 100),
      activityStock: bindingEditForm.stock,
      limitPerUser: bindingEditForm.limitPerUser,
    })
    ElMessage.success('活动商品已更新，请重新预热')
    bindingEditVisible.value = false
    await loadDetail(detail.value.id)
    await loadActivities()
  } catch {
    // 错误提示由请求层统一处理
  } finally {
    bindingEditSubmitting.value = false
  }
}

async function handleDisableBinding(binding: AdminActivityProduct): Promise<void> {
  if (!detail.value) {
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定停用「${binding.productName}」吗？历史销量与对账数据会保留。`,
      '停用活动商品',
      { type: 'warning', confirmButtonText: '停用', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await disableActivityProduct(detail.value.id, binding.bindingId)
    ElMessage.success('活动商品已停用')
    await loadDetail(detail.value.id)
    await loadActivities()
  } catch {
    // 错误提示由请求层统一处理
  }
}

onMounted(loadActivities)
</script>

<template>
  <div class="activity-page">
    <div class="page-head">
      <div>
        <h2>活动管理</h2>
        <p class="muted">活动结束只关闭不删除，数据保留用于对账；秒杀开启前请先预热库存。</p>
      </div>
      <el-button type="primary" :icon="'Plus'" @click="openCreate">新建活动</el-button>
    </div>

    <section class="page-card">
      <div class="filter-bar">
        <el-input
          v-model="query.keyword"
          placeholder="搜索活动名称"
          clearable
          class="filter-keyword"
          :prefix-icon="'Search'"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-select
          v-model="query.type"
          placeholder="全部类型"
          clearable
          class="filter-select"
          @change="handleSearch"
        >
          <el-option label="秒杀" value="SECKILL" />
          <el-option label="满减" value="FULL_REDUCTION" />
        </el-select>
        <el-select
          v-model="query.status"
          placeholder="全部状态"
          clearable
          class="filter-select"
          @change="handleSearch"
        >
          <el-option label="未开始" value="NOT_STARTED" />
          <el-option label="进行中" value="RUNNING" />
          <el-option label="已结束" value="ENDED" />
          <el-option label="已关闭" value="CLOSED" />
        </el-select>
        <div class="filter-actions">
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="activities" empty-text="暂无活动" style="width: 100%">
        <el-table-column label="活动名称" min-width="190">
          <template #default="{ row }">
            <div class="activity-title">{{ row.title }}</div>
            <div class="muted small">创建于 {{ formatDateTime(row.createTime) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="150">
          <template #default="{ row }">
            <el-tag :type="typeMeta(row.type).tag" effect="light" round>
              {{ typeMeta(row.type).label }}
            </el-tag>
            <div v-if="row.type === 'FULL_REDUCTION'" class="muted small">
              满 {{ formatMoney(row.fullAmount) }} 减 {{ formatMoney(row.reduceAmount) }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="起止时间" min-width="210">
          <template #default="{ row }">
            <div>{{ formatDateTime(row.startTime) }}</div>
            <div class="muted small">至 {{ formatDateTime(row.endTime) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusMeta(row.status).tag" effect="light" round>
              {{ statusMeta(row.status).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="开关" width="90" align="center">
          <template #default="{ row }">
            <el-switch
              v-model="row.isOpen"
              :active-value="1"
              :inactive-value="0"
              @change="handleOpenChange(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="商品 / 库存" min-width="160">
          <template #default="{ row }">
            <div v-if="row.type === 'SECKILL'">
              {{ row.productCount }} 个商品 · 限量 {{ row.totalStock }} 杯
            </div>
            <div v-else>{{ row.productCount }} 个商品参与满减</div>
            <div class="muted small">已售 {{ row.soldCount }} 杯</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" align="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openManage(row)">管理商品</el-button>
            <el-button link @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="loadActivities"
          @size-change="handleSizeChange"
        />
      </div>
    </section>

    <el-dialog
      v-model="editVisible"
      :title="isEditing ? '编辑活动' : '新建活动'"
      width="600px"
      destroy-on-close
    >
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="96px">
        <el-form-item label="活动名称" prop="title">
          <el-input v-model="editForm.title" maxlength="64" placeholder="如 周末秒杀 / 满 30 减 5" />
        </el-form-item>
        <el-form-item label="活动类型" prop="type">
          <el-radio-group v-model="editForm.type" :disabled="isEditing" @change="handleTypeChange">
            <el-radio value="SECKILL">秒杀</el-radio>
            <el-radio value="FULL_REDUCTION">满减</el-radio>
          </el-radio-group>
          <div v-if="isEditing" class="form-hint">活动类型创建后不能修改</div>
        </el-form-item>
        <el-form-item label="起止时间" prop="timeRange">
          <el-date-picker
            v-model="editForm.timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            format="YYYY-MM-DD HH:mm:ss"
            value-format="YYYY-MM-DDTHH:mm:ss"
            class="full-width"
          />
        </el-form-item>
        <template v-if="editForm.type === 'FULL_REDUCTION'">
          <el-form-item label="满减门槛">
            <el-input-number
              v-model="editForm.fullAmountYuan"
              :min="0.01"
              :max="99999"
              :precision="2"
              :step="1"
              controls-position="right"
              style="width: 180px"
            />
            <span class="unit">元</span>
          </el-form-item>
          <el-form-item label="满减金额">
            <el-input-number
              v-model="editForm.reduceAmountYuan"
              :min="0.01"
              :max="99999"
              :precision="2"
              :step="1"
              controls-position="right"
              style="width: 180px"
            />
            <span class="unit">元</span>
          </el-form-item>
        </template>
        <el-form-item label="备注">
          <el-input
            v-model="editForm.remark"
            type="textarea"
            :rows="2"
            maxlength="255"
            show-word-limit
            placeholder="活动说明，仅后台可见"
          />
        </el-form-item>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="活动创建后默认关闭。绑定商品、设置价格并完成预热后，再打开开关。"
        />
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSubmitting" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="manageVisible" title="活动商品管理" width="920px">
      <div v-loading="manageLoading">
        <template v-if="detail">
          <div class="manage-head">
            <div>
              <div class="manage-title">{{ detail.title }}</div>
              <div class="manage-tags">
                <el-tag :type="TYPE_META[detail.type].tag" effect="light" round>
                  {{ TYPE_META[detail.type].label }}
                </el-tag>
                <el-tag :type="STATUS_META[detail.status].tag" effect="light" round>
                  {{ STATUS_META[detail.status].label }}
                </el-tag>
                <span class="muted small">
                  {{ formatDateTime(detail.startTime) }} 至 {{ formatDateTime(detail.endTime) }}
                </span>
              </div>
            </div>
            <div v-if="isSeckill" class="preheat-block">
              <span class="muted small">
                已预热 {{ preheatedCount }} / {{ activeProducts.length }} 个商品
              </span>
              <el-button
                type="warning"
                :icon="'Lightning'"
                :loading="preheating"
                @click="handlePreheat"
              >
                一键预热库存
              </el-button>
            </div>
          </div>

          <el-alert
            v-if="!canEditBindings"
            type="warning"
            :closable="false"
            show-icon
            title="活动开启中，商品与价格不可修改，请先关闭活动。"
            class="manage-alert"
          />
          <el-alert
            v-else
            type="info"
            :closable="false"
            show-icon
            title="修改价格或库存后需要重新预热；活动结束只关闭，不删除。"
            class="manage-alert"
          />

          <el-table :data="detail.products" empty-text="暂未绑定商品" size="default">
            <el-table-column label="商品" min-width="200">
              <template #default="{ row }">
                <div class="product-cell">
                  <el-image
                    v-if="row.productPic"
                    :src="row.productPic"
                    fit="cover"
                    class="product-thumb"
                  />
                  <div v-else class="product-thumb product-thumb-empty">
                    {{ row.productName.slice(0, 1) }}
                  </div>
                  <div class="product-meta">
                    <div>{{ row.productName }}</div>
                    <div class="muted small">原价 {{ formatMoney(row.productPrice) }}</div>
                    <el-tag v-if="row.productStatus !== 1" type="danger" size="small">
                      商品已下架
                    </el-tag>
                  </div>
                </div>
              </template>
            </el-table-column>
            <template v-if="isSeckill">
              <el-table-column label="活动价" width="96" align="right">
                <template #default="{ row }">
                  <span class="money">{{ formatMoney(row.activityPrice) }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="activityStock" label="限量" width="76" align="right" />
              <el-table-column prop="soldCount" label="已售" width="76" align="right" />
              <el-table-column prop="limitPerUser" label="限购" width="76" align="right" />
              <el-table-column label="Redis 剩余" width="104" align="right">
                <template #default="{ row }">
                  <span :class="{ 'text-danger': row.redisStock === 0 }">
                    {{ row.redisStock === null ? '—' : row.redisStock }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="预热" width="90" align="center">
                <template #default="{ row }">
                  <el-tag v-if="row.status !== 1" type="info" size="small">已停用</el-tag>
                  <el-tag v-else :type="row.preheated ? 'success' : 'warning'" size="small">
                    {{ row.preheated ? '已预热' : '未预热' }}
                  </el-tag>
                </template>
              </el-table-column>
            </template>
            <el-table-column v-else label="绑定状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                  {{ row.status === 1 ? '生效中' : '已停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="130" align="right">
              <template #default="{ row }">
                <template v-if="canEditBindings && row.status === 1">
                  <el-button
                    v-if="isSeckill"
                    link
                    type="primary"
                    @click="openBindingEdit(row)"
                  >
                    编辑
                  </el-button>
                  <el-button link type="danger" @click="handleDisableBinding(row)">停用</el-button>
                </template>
                <span v-else class="muted small">—</span>
              </template>
            </el-table-column>
          </el-table>
        </template>
      </div>
      <template #footer>
        <el-button @click="manageVisible = false">关闭</el-button>
        <el-button
          v-if="canEditBindings"
          type="primary"
          :icon="'Plus'"
          @click="openBind"
        >
          绑定商品
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="bindVisible" title="绑定活动商品" width="520px">
      <el-form ref="bindFormRef" label-width="92px">
        <el-form-item label="商品">
          <el-select
            v-model="bindForm.productId"
            placeholder="请选择上架商品"
            filterable
            style="width: 100%"
            @change="handleProductChange"
          >
            <el-option
              v-for="product in availableProducts"
              :key="product.id"
              :label="`${product.name}（原价 ${formatMoney(product.price)}，库存 ${product.stock}）`"
              :value="product.id"
            />
          </el-select>
        </el-form-item>
        <template v-if="isSeckill">
          <el-form-item label="秒杀价">
            <el-input-number
              v-model="bindForm.priceYuan"
              :min="0.01"
              :max="99999"
              :precision="2"
              :step="1"
              controls-position="right"
              style="width: 180px"
            />
            <span class="unit">元 / 杯</span>
          </el-form-item>
          <el-form-item label="活动限量">
            <el-input-number
              v-model="bindForm.stock"
              :min="1"
              :max="99999"
              controls-position="right"
              style="width: 180px"
            />
            <span class="unit">杯</span>
          </el-form-item>
          <el-form-item label="每人限购">
            <el-input-number
              v-model="bindForm.limitPerUser"
              :min="1"
              :max="99999"
              controls-position="right"
              style="width: 180px"
            />
            <span class="unit">杯</span>
          </el-form-item>
          <el-alert
            type="info"
            :closable="false"
            show-icon
            title="秒杀价必须低于商品原价，活动限量不能超过商品当前库存。"
          />
        </template>
        <el-alert
          v-else
          type="info"
          :closable="false"
          show-icon
          title="满减活动只需要绑定参与商品，满减门槛与金额在活动上配置。"
        />
      </el-form>
      <template #footer>
        <el-button @click="bindVisible = false">取消</el-button>
        <el-button type="primary" :loading="bindSubmitting" @click="submitBind">绑定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="bindingEditVisible" title="修改活动商品" width="460px">
      <el-form label-width="92px">
        <el-form-item label="商品">
          <span>{{ bindingEditForm.productName }}</span>
        </el-form-item>
        <el-form-item label="秒杀价">
          <el-input-number
            v-model="bindingEditForm.priceYuan"
            :min="0.01"
            :max="99999"
            :precision="2"
            :step="1"
            controls-position="right"
            style="width: 180px"
          />
          <span class="unit">元 / 杯</span>
        </el-form-item>
        <el-form-item label="活动限量">
          <el-input-number
            v-model="bindingEditForm.stock"
            :min="1"
            :max="99999"
            controls-position="right"
            style="width: 180px"
          />
          <span class="unit">杯</span>
        </el-form-item>
        <el-form-item label="每人限购">
          <el-input-number
            v-model="bindingEditForm.limitPerUser"
            :min="1"
            :max="99999"
            controls-position="right"
            style="width: 180px"
          />
          <span class="unit">杯</span>
        </el-form-item>
        <el-alert
          type="warning"
          :closable="false"
          show-icon
          title="保存后需要重新预热库存，否则活动无法开启。"
        />
      </el-form>
      <template #footer>
        <el-button @click="bindingEditVisible = false">取消</el-button>
        <el-button type="primary" :loading="bindingEditSubmitting" @click="submitBindingEdit">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.activity-page {
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

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  padding: 18px 22px;
  border-bottom: 1px solid var(--coffee-line);
}

.filter-keyword {
  width: 240px;
}

.filter-select {
  width: 150px;
}

.filter-actions {
  display: flex;
  gap: 8px;
  margin-left: auto;
}

.activity-title {
  font-size: 14px;
  font-weight: 600;
}

.small {
  font-size: 12px;
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  padding: 16px 22px 20px;
}

.manage-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.manage-title {
  font-size: 17px;
  font-weight: 600;
  margin-bottom: 8px;
}

.manage-tags {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.preheat-block {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.manage-alert {
  margin-bottom: 14px;
}

.product-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.product-thumb {
  width: 42px;
  height: 42px;
  border-radius: 8px;
  flex-shrink: 0;
}

.product-thumb-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #f3ece2;
  color: #9a7b5f;
  font-weight: 600;
}

.product-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.text-danger {
  color: #b4573f;
  font-weight: 600;
}

.form-hint {
  margin-left: 8px;
  font-size: 12px;
  color: var(--coffee-muted);
}

.unit {
  margin-left: 8px;
  font-size: 12px;
  color: var(--coffee-muted);
}

.full-width {
  width: 100%;
}
</style>
