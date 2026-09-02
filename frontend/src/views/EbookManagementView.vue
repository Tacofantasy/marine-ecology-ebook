<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { getAdminCategories, type CategoryTreeItem } from '../category/category-api'
import {
  createEbook,
  deleteEbook,
  getAdminEbooks,
  publishEbook,
  unpublishEbook,
  updateEbook,
  uploadEbookCover,
  type EbookItem,
} from '../ebook/ebook-api'

const categories = ref<CategoryTreeItem[]>([])
const ebooks = ref<EbookItem[]>([])
const total = ref(0)
const loading = ref(false)
const submitting = ref(false)
const modalOpen = ref(false)
const editingEbook = ref<EbookItem | null>(null)
const selectedCover = ref<File | null>(null)
const coverPreviewUrl = ref('')
const filters = reactive({ categoryId: undefined as string | undefined, keyword: '', page: 1, pageSize: 10 })
const form = reactive({ categoryId: undefined as string | undefined, title: '', summary: '', sourceNote: '' })

const secondaryCategories = computed(() => categories.value.flatMap((root) => root.children))
const categoryOptions = computed(() => secondaryCategories.value.map((category) => ({
  value: category.id,
  label: `${categories.value.find((root) => root.id === category.parentId)?.name ?? ''} / ${category.name}`,
})))
const modalTitle = computed(() => editingEbook.value ? '编辑草稿电子书' : '新建电子书')

async function loadCategories() {
  try {
    categories.value = await getAdminCategories()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '分类加载失败，请稍后重试。')
  }
}

async function loadEbooks() {
  loading.value = true
  try {
    const data = await getAdminEbooks(filters)
    ebooks.value = data.list
    total.value = data.total
  } catch (error) {
    message.error(error instanceof Error ? error.message : '电子书加载失败，请稍后重试。')
  } finally {
    loading.value = false
  }
}

function search() {
  filters.page = 1
  void loadEbooks()
}

function resetForm() {
  form.categoryId = undefined
  form.title = ''
  form.summary = ''
  form.sourceNote = ''
  selectedCover.value = null
  coverPreviewUrl.value = ''
}

function openCreate() {
  editingEbook.value = null
  resetForm()
  modalOpen.value = true
}

function openEdit(ebook: EbookItem) {
  editingEbook.value = ebook
  form.categoryId = ebook.categoryId
  form.title = ebook.title
  form.summary = ebook.summary ?? ''
  form.sourceNote = ebook.sourceNote ?? ''
  selectedCover.value = null
  coverPreviewUrl.value = ebook.coverUrl ?? ''
  modalOpen.value = true
}

function beforeUpload(file: File) {
  const supported = ['image/jpeg', 'image/png', 'image/webp'].includes(file.type)
  if (file.type && !supported) {
    message.error('封面仅支持 JPEG、PNG 或 WebP 图片')
    return false
  }
  if (file.size > 5 * 1024 * 1024) {
    message.error('封面图片不能超过 5 MB')
    return false
  }
  selectedCover.value = file
  coverPreviewUrl.value = URL.createObjectURL(file)
  return false
}

async function submit() {
  if (!form.categoryId || !form.title.trim()) {
    message.warning('请填写电子书名称并选择二级分类')
    return
  }
  submitting.value = true
  try {
    const payload = {
      categoryId: form.categoryId,
      title: form.title.trim(),
      summary: form.summary.trim() || undefined,
      sourceNote: form.sourceNote.trim() || undefined,
    }
    const ebook = editingEbook.value
      ? await updateEbook(editingEbook.value.id, payload)
      : await createEbook(payload)
    if (selectedCover.value) {
      try {
        await uploadEbookCover(ebook.id, selectedCover.value)
      } catch (error) {
        modalOpen.value = false
        await loadEbooks()
        message.error(error instanceof Error
          ? `电子书信息已保存，但封面上传失败：${error.message}`
          : '电子书信息已保存，但封面上传失败，请在列表中重新编辑草稿后重试。')
        return
      }
    }
    message.success(editingEbook.value ? '草稿电子书已更新' : '草稿电子书已创建')
    modalOpen.value = false
    await loadEbooks()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '保存失败，请稍后重试。')
  } finally {
    submitting.value = false
  }
}

async function changeStatus(ebook: EbookItem) {
  try {
    if (ebook.status === 'DRAFT') {
      await publishEbook(ebook.id)
      message.success('电子书已发布')
    } else {
      await unpublishEbook(ebook.id)
      message.success('电子书已撤回为草稿')
    }
    await loadEbooks()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '状态更新失败，请稍后重试。')
  }
}

async function remove(ebook: EbookItem) {
  try {
    await deleteEbook(ebook.id)
    message.success('草稿电子书已删除')
    await loadEbooks()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '删除失败，请稍后重试。')
  }
}

function formatTime(value: string | null) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—'
}

function changePage(page: number, pageSize: number) {
  filters.page = page
  filters.pageSize = pageSize
  void loadEbooks()
}

onMounted(async () => {
  await loadCategories()
  await loadEbooks()
})
</script>

<template>
  <main class="admin-page page-shell">
    <header class="admin-page-header">
      <div>
        <p class="section-kicker">内容管理</p>
        <h1>电子书管理</h1>
        <p>先完善草稿，再上传封面并发布。已发布内容须先撤回才能修改或删除。</p>
      </div>
      <a-button type="primary" @click="openCreate">新建电子书</a-button>
    </header>

    <a-card :bordered="false" class="management-card">
      <div class="management-filters">
        <a-select v-model:value="filters.categoryId" allow-clear placeholder="全部二级分类" :options="categoryOptions" @change="search" />
        <a-input-search v-model:value="filters.keyword" allow-clear placeholder="搜索标题或简介" enter-button="搜索" @search="search" />
      </div>

      <a-table :data-source="ebooks" :loading="loading" :pagination="false" row-key="id" :scroll="{ x: 900 }">
        <a-table-column title="电子书" key="title" :width="260">
          <template #default="{ record }">
            <div class="ebook-table-title">
              <img v-if="record.coverUrl" :src="record.coverUrl" :alt="`${record.title} 封面`" loading="lazy" />
              <span v-else class="cover-placeholder" aria-hidden="true">海洋</span>
              <div><strong>{{ record.title }}</strong><small>{{ record.categoryName }}</small></div>
            </div>
          </template>
        </a-table-column>
        <a-table-column title="状态" key="status" :width="110">
          <template #default="{ record }"><a-tag :color="record.status === 'PUBLISHED' ? 'green' : 'cyan'">{{ record.status === 'PUBLISHED' ? '已发布' : '草稿' }}</a-tag></template>
        </a-table-column>
        <a-table-column title="发布时间" key="publishedAt" :width="180"><template #default="{ record }">{{ formatTime(record.publishedAt) }}</template></a-table-column>
        <a-table-column title="最近更新" key="updatedAt" :width="180"><template #default="{ record }">{{ formatTime(record.updatedAt) }}</template></a-table-column>
        <a-table-column title="操作" key="actions" :width="290" fixed="right">
          <template #default="{ record }">
            <a-space>
              <RouterLink :to="`/admin/ebooks/${record.id}/chapters`"><a-button type="link">章节</a-button></RouterLink>
              <a-button v-if="record.status === 'DRAFT'" type="link" @click="openEdit(record)">编辑</a-button>
              <a-popconfirm :title="record.status === 'DRAFT' ? '发布前会校验简介、封面、来源和章节，确定继续吗？' : '确定撤回该电子书吗？'" ok-text="确定" cancel-text="取消" @confirm="changeStatus(record)">
                <a-button type="link">{{ record.status === 'DRAFT' ? '发布' : '撤回' }}</a-button>
              </a-popconfirm>
              <a-popconfirm v-if="record.status === 'DRAFT'" title="将永久删除该草稿、其章节和封面，且无法恢复。确定继续吗？" ok-text="删除" cancel-text="取消" @confirm="remove(record)">
                <a-button danger type="link">删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </a-table-column>
      </a-table>
      <p v-if="!loading && ebooks.length === 0" class="management-empty">暂无符合条件的电子书。你可以新建一份草稿开始录入。</p>
      <a-pagination class="management-pagination" :current="filters.page" :page-size="filters.pageSize" :total="total" :page-size-options="['10', '20']" show-size-changer @change="changePage" />
    </a-card>

    <a-modal v-model:open="modalOpen" :confirm-loading="submitting" :title="modalTitle" width="680px" @ok="submit">
      <a-form layout="vertical" @submit.prevent="submit">
        <a-form-item label="电子书名称" required><a-input v-model:value="form.title" :maxlength="200" @blur="form.title = form.title.trim()" /></a-form-item>
        <a-form-item label="二级分类" required><a-select v-model:value="form.categoryId" placeholder="请选择二级分类" :options="categoryOptions" /></a-form-item>
        <a-form-item label="简介"><a-textarea v-model:value="form.summary" :maxlength="500" :rows="4" show-count placeholder="草稿可暂不填写；发布时需填写 20–500 字。" /></a-form-item>
        <a-form-item label="内容来源说明"><a-textarea v-model:value="form.sourceNote" :maxlength="1000" :rows="4" show-count placeholder="草稿可暂不填写；发布时必填。" /></a-form-item>
        <a-form-item label="封面图片"><a-upload accept="image/jpeg,image/png,image/webp" :before-upload="beforeUpload" :show-upload-list="false"><a-button>选择封面图片</a-button></a-upload><p class="form-hint">仅本地预览，保存电子书时才上传。支持 JPEG、PNG、WebP，最大 5 MB。</p><img v-if="coverPreviewUrl" class="cover-preview" :src="coverPreviewUrl" alt="待保存的封面预览" /></a-form-item>
      </a-form>
    </a-modal>
  </main>
</template>
