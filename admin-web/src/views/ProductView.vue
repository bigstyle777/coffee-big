<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  ElMessage,
  ElMessageBox,
  type FormInstance,
  type FormRules,
  type UploadRequestOptions,
} from 'element-plus'
import {
  createProduct,
  deleteProduct,
  getProductDetail,
  listCategories,
  listSpecGroups,
  pageProducts,
  updateProduct,
  updateProductStatus,
  uploadImage,
} from '@/api/admin'
import type { AdminCategory, AdminProduct, AdminSpecGroup, SaveProductPayload } from '@/types'
import { formatMoney } from '@/utils/format'

interface ProductForm {
  id: number
  categoryId?: number
  name: string
  picUrl: string
  priceYuan: number
  stock: number
  sort: number
  status: number
  specGroupIds: number[]
}

const loading = ref(false)
const submitting = ref(false)
const products = ref<AdminProduct[]>([])
const total = ref(0)
const categories = ref<AdminCategory[]>([])
const specGroups = ref<AdminSpecGroup[]>([])

const query = reactive<{
  pageNum: number
  pageSize: number
  keyword: string
  categoryId?: number
  status?: number
}>({
  pageNum: 1,
  pageSize: 10,
  keyword: '',
  categoryId: undefined,
  status: undefined,
})

const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<ProductForm>({
  id: 0,
  categoryId: undefined,
  name: '',
  picUrl: '',
  priceYuan: 18,
  stock: 100,
  sort: 0,
  status: 1,
  specGroupIds: [],
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入商品名称', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择商品分类', trigger: 'change' }],
  priceYuan: [{ required: true, message: '请输入售价', trigger: 'blur' }],
  stock: [{ required: true, message: '请输入库存', trigger: 'blur' }],
}

const isEdit = computed(() => form.id > 0)
const dialogTitle = computed(() => (isEdit.value ? '编辑商品' : '新增商品'))
const selectedGroups = computed(() =>
  specGroups.value.filter((group) => form.specGroupIds.includes(group.id)),
)

async function loadProducts(): Promise<void> {
  loading.value = true
  try {
    const result = await pageProducts({ ...query })
    products.value = result.records
    total.value = result.total
  } catch {
    // 错误提示由请求层统一处理
  } finally {
    loading.value = false
  }
}

async function loadOptions(): Promise<void> {
  try {
    const [categoryList, groupList] = await Promise.all([listCategories(), listSpecGroups()])
    categories.value = categoryList
    specGroups.value = groupList
  } catch {
    // 错误提示由请求层统一处理
  }
}

function handleSearch(): void {
  query.pageNum = 1
  loadProducts()
}

function handleReset(): void {
  query.keyword = ''
  query.categoryId = undefined
  query.status = undefined
  query.pageNum = 1
  loadProducts()
}

function handleSizeChange(): void {
  query.pageNum = 1
  loadProducts()
}

function resetForm(): void {
  form.id = 0
  form.categoryId = undefined
  form.name = ''
  form.picUrl = ''
  form.priceYuan = 18
  form.stock = 100
  form.sort = 0
  form.status = 1
  form.specGroupIds = []
  formRef.value?.clearValidate()
}

function openCreate(): void {
  resetForm()
  dialogVisible.value = true
}

async function openEdit(row: AdminProduct): Promise<void> {
  try {
    const detail = await getProductDetail(row.id)
    resetForm()
    form.id = detail.id
    form.categoryId = detail.categoryId
    form.name = detail.name
    form.picUrl = detail.picUrl
    form.priceYuan = detail.price / 100
    form.stock = detail.stock
    form.sort = detail.sort
    form.status = detail.status
    form.specGroupIds = [...detail.specGroupIds]
    dialogVisible.value = true
  } catch {
    // 错误提示由请求层统一处理
  }
}

async function submitForm(): Promise<void> {
  if (!formRef.value) {
    return
  }
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }
  if (!form.categoryId) {
    ElMessage.warning('请选择商品分类')
    return
  }
  if (form.priceYuan < 0.01) {
    ElMessage.warning('商品售价必须大于 0')
    return
  }

  const payload: SaveProductPayload = {
    categoryId: form.categoryId,
    name: form.name.trim(),
    picUrl: form.picUrl,
    price: Math.round(form.priceYuan * 100),
    stock: form.stock,
    sort: form.sort,
    status: form.status,
    specGroupIds: [...form.specGroupIds],
  }

  submitting.value = true
  try {
    if (isEdit.value) {
      await updateProduct(form.id, payload)
      ElMessage.success('商品已更新')
    } else {
      await createProduct(payload)
      ElMessage.success('商品已新增')
    }
    dialogVisible.value = false
    await loadProducts()
  } catch {
    // 错误提示由请求层统一处理
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row: AdminProduct): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确定删除「${row.name}」吗？删除后小程序不再展示，历史订单不受影响。`,
      '删除商品',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await deleteProduct(row.id)
    ElMessage.success('商品已删除')
    if (products.value.length === 1 && query.pageNum > 1) {
      query.pageNum -= 1
    }
    await loadProducts()
  } catch {
    // 错误提示由请求层统一处理
  }
}

async function handleStatusChange(row: AdminProduct): Promise<void> {
  const nextStatus = row.status
  try {
    await updateProductStatus(row.id, nextStatus)
    ElMessage.success(nextStatus === 1 ? '商品已上架' : '商品已下架')
  } catch {
    row.status = nextStatus === 1 ? 0 : 1
  }
}

async function handleUpload(options: UploadRequestOptions): Promise<void> {
  const file = options.file
  if (!file.type.startsWith('image/')) {
    ElMessage.error('请选择图片文件')
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.error('图片不能超过 5MB')
    return
  }
  try {
    const result = await uploadImage(file)
    form.picUrl = result.url
    ElMessage.success('图片上传成功')
  } catch {
    // 错误提示由请求层统一处理
  }
}

onMounted(async () => {
  await Promise.all([loadOptions(), loadProducts()])
})
</script>

<template>
  <div class="product-page">
    <div class="page-head">
      <div>
        <h2>商品管理</h2>
        <p class="muted">维护饮品售价、库存、图片与规格，下架后小程序立即隐藏。</p>
      </div>
      <el-button type="primary" :icon="'Plus'" @click="openCreate">新增商品</el-button>
    </div>

    <section class="page-card">
      <div class="filter-bar">
        <el-input
          v-model="query.keyword"
          placeholder="搜索商品名称"
          clearable
          class="filter-keyword"
          :prefix-icon="'Search'"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-select
          v-model="query.categoryId"
          placeholder="全部分类"
          clearable
          class="filter-select"
          @change="handleSearch"
        >
          <el-option
            v-for="category in categories"
            :key="category.id"
            :label="category.name"
            :value="category.id"
          />
        </el-select>
        <el-select
          v-model="query.status"
          placeholder="全部状态"
          clearable
          class="filter-select"
          @change="handleSearch"
        >
          <el-option label="已上架" :value="1" />
          <el-option label="已下架" :value="0" />
        </el-select>
        <div class="filter-actions">
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="products" empty-text="暂无商品" style="width: 100%">
        <el-table-column label="图片" width="84">
          <template #default="{ row }">
            <el-image
              v-if="row.picUrl"
              :src="row.picUrl"
              :preview-src-list="[row.picUrl]"
              preview-teleported
              fit="cover"
              class="thumb"
            />
            <div v-else class="thumb thumb-placeholder">{{ row.name.slice(0, 1) }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="商品名称" min-width="160" />
        <el-table-column prop="categoryName" label="分类" width="110" />
        <el-table-column label="售价" width="110" align="right">
          <template #default="{ row }">
            <span class="money">{{ formatMoney(row.price) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="库存" width="96" align="right">
          <template #default="{ row }">
            <span :class="{ 'stock-empty': row.stock <= 0 }">{{ row.stock }}</span>
          </template>
        </el-table-column>
        <el-table-column label="上架" width="90" align="center">
          <template #default="{ row }">
            <el-switch
              v-model="row.status"
              :active-value="1"
              :inactive-value="0"
              @change="handleStatusChange(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" align="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
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
          @current-change="loadProducts"
          @size-change="handleSizeChange"
        />
      </div>
    </section>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="680px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="商品名称" prop="name">
              <el-input v-model="form.name" maxlength="64" placeholder="如 生椰拿铁" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="商品分类" prop="categoryId">
              <el-select v-model="form.categoryId" placeholder="请选择分类" style="width: 100%">
                <el-option
                  v-for="category in categories"
                  :key="category.id"
                  :label="category.name"
                  :value="category.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="售价" prop="priceYuan">
              <el-input-number
                v-model="form.priceYuan"
                :min="0.01"
                :max="9999"
                :precision="2"
                :step="1"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="库存" prop="stock">
              <el-input-number
                v-model="form.stock"
                :min="0"
                :max="99999"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序">
              <el-input-number
                v-model="form.sort"
                :min="0"
                :max="9999"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-radio-group v-model="form.status">
                <el-radio :value="1">上架</el-radio>
                <el-radio :value="0">下架</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="商品图片">
          <div class="upload-row">
            <el-upload
              class="uploader"
              :show-file-list="false"
              accept="image/*"
              :http-request="handleUpload"
            >
              <div class="upload-trigger">
                <el-image v-if="form.picUrl" :src="form.picUrl" fit="cover" class="upload-preview" />
                <div v-else class="upload-empty">
                  <el-icon><Plus /></el-icon>
                  <span>上传图片</span>
                </div>
              </div>
            </el-upload>
            <div class="upload-hint">
              <p>建议 1:1 方形图片，支持 jpg / png / webp，最大 5MB。</p>
              <el-button v-if="form.picUrl" link type="danger" @click="form.picUrl = ''">
                移除图片
              </el-button>
            </div>
          </div>
        </el-form-item>

        <el-form-item label="规格组">
          <div class="spec-block">
            <el-checkbox-group v-model="form.specGroupIds">
              <el-checkbox
                v-for="group in specGroups"
                :key="group.id"
                :value="group.id"
                border
              >
                {{ group.name }}
                <span class="spec-tag">{{ group.required ? '必选' : '可选' }}</span>
              </el-checkbox>
            </el-checkbox-group>
            <div v-if="selectedGroups.length" class="spec-preview">
              <span v-for="group in selectedGroups" :key="group.id" class="spec-chip">
                {{ group.name }}：{{ group.options.map((option) => option.name).join(' / ') }}
              </span>
            </div>
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.product-page {
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

.thumb {
  width: 48px;
  height: 48px;
  border-radius: 10px;
  display: block;
}

.thumb-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #f3ece2;
  color: #9a7b5f;
  font-size: 18px;
  font-weight: 600;
}

.stock-empty {
  color: #b4573f;
  font-weight: 600;
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  padding: 16px 22px 20px;
}

.upload-row {
  display: flex;
  align-items: center;
  gap: 18px;
}

.upload-trigger {
  width: 104px;
  height: 104px;
  border-radius: 12px;
  border: 1px dashed #d9cbb8;
  background-color: #faf7f2;
  overflow: hidden;
  cursor: pointer;
  transition: border-color 0.2s ease;
}

.upload-trigger:hover {
  border-color: #b08968;
}

.upload-preview {
  width: 100%;
  height: 100%;
  display: block;
}

.upload-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  height: 100%;
  color: var(--coffee-muted);
  font-size: 12px;
}

.upload-hint p {
  margin: 0 0 6px;
  font-size: 12px;
  color: var(--coffee-muted);
}

.spec-block {
  width: 100%;
}

.spec-tag {
  margin-left: 4px;
  font-size: 11px;
  color: var(--coffee-muted);
}

.spec-preview {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.spec-chip {
  padding: 4px 10px;
  border-radius: 999px;
  background-color: #f6f1e8;
  color: #7a5a3a;
  font-size: 12px;
}

:deep(.el-checkbox.is-bordered) {
  margin: 0 8px 8px 0;
  border-radius: 8px;
}
</style>
