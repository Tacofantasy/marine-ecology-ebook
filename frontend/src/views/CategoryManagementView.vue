<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import {
  createCategory,
  deleteCategory,
  getAdminCategories,
  updateCategory,
  type CategoryTreeItem,
} from '../category/category-api'

const categories = ref<CategoryTreeItem[]>([])
const loading = ref(false)
const submitting = ref(false)
const modalOpen = ref(false)
const editingCategory = ref<CategoryTreeItem | null>(null)
const parentId = ref<number | null>(null)
const form = reactive({ name: '' })

const modalTitle = computed(() => {
  if (editingCategory.value) {
    return '编辑分类'
  }
  return parentId.value === null ? '新增一级分类' : '新增二级分类'
})

async function loadCategories() {
  loading.value = true
  try {
    categories.value = await getAdminCategories()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '分类加载失败，请稍后重试。')
  } finally {
    loading.value = false
  }
}

function openCreate(newParentId: number | null) {
  editingCategory.value = null
  parentId.value = newParentId
  form.name = ''
  modalOpen.value = true
}

function openEdit(category: CategoryTreeItem) {
  editingCategory.value = category
  parentId.value = null
  form.name = category.name
  modalOpen.value = true
}

async function submit() {
  const name = form.name.trim()
  if (!name) {
    message.warning('请输入分类名称')
    return
  }

  submitting.value = true
  try {
    if (editingCategory.value) {
      await updateCategory(editingCategory.value.id, { name })
      message.success('分类已更新')
    } else {
      await createCategory(parentId.value === null ? { name } : { name, parentId: parentId.value })
      message.success('分类已创建')
    }
    modalOpen.value = false
    await loadCategories()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '保存失败，请稍后重试。')
  } finally {
    submitting.value = false
  }
}

async function remove(category: CategoryTreeItem) {
  try {
    await deleteCategory(category.id)
    message.success('分类已删除')
    await loadCategories()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '删除失败，请稍后重试。')
  }
}

onMounted(loadCategories)
</script>

<template>
  <main class="admin-page page-shell">
    <header class="admin-page-header">
      <div>
        <p class="section-kicker">内容管理</p>
        <h1>分类管理</h1>
        <p>分类固定为两级。二级分类创建后不可调整所属一级分类。</p>
      </div>
      <a-button type="primary" @click="openCreate(null)">新增一级分类</a-button>
    </header>

    <a-card :bordered="false" class="management-card">
      <a-table
        :data-source="categories"
        :loading="loading"
        :pagination="false"
        row-key="id"
        :scroll="{ x: 680 }"
      >
        <a-table-column title="分类名称" data-index="name" key="name" />
        <a-table-column title="层级" key="level" :width="120">
          <template #default="{ record }">
            {{ record.parentId === null ? '一级分类' : '二级分类' }}
          </template>
        </a-table-column>
        <a-table-column title="排序" data-index="sortOrder" key="sortOrder" :width="100" />
        <a-table-column title="操作" key="actions" :width="270">
          <template #default="{ record }">
            <a-space>
              <a-button v-if="record.parentId === null" type="link" @click="openCreate(record.id)">新增二级</a-button>
              <a-button type="link" @click="openEdit(record)">编辑</a-button>
              <a-popconfirm
                title="确定删除该分类吗？"
                ok-text="删除"
                cancel-text="取消"
                @confirm="remove(record)"
              >
                <a-button danger type="link">删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </a-table-column>
      </a-table>
      <p v-if="!loading && categories.length === 0" class="management-empty">暂无分类，请先新增一级分类。</p>
    </a-card>

    <a-modal v-model:open="modalOpen" :confirm-loading="submitting" :title="modalTitle" @ok="submit">
      <a-form layout="vertical" @submit.prevent="submit">
        <a-form-item label="分类名称" required>
          <a-input v-model:value="form.name" :maxlength="100" autocomplete="off" autofocus @press-enter="submit" />
        </a-form-item>
        <p v-if="editingCategory" class="form-hint">分类归属创建后不可更改。</p>
        <p v-else-if="parentId !== null" class="form-hint">将创建为所选一级分类下的二级分类。</p>
      </a-form>
    </a-modal>
  </main>
</template>
