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
const removingCategoryId = ref<string | null>(null)
const modalOpen = ref(false)
const editingCategory = ref<CategoryTreeItem | null>(null)
const parentId = ref<string | null>(null)
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

function openCreate(newParentId: string | null) {
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
  if (removingCategoryId.value !== null) {
    return
  }
  removingCategoryId.value = category.id
  try {
    await deleteCategory(category.id)
    message.success('分类已删除')
    await loadCategories()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '删除失败，请稍后重试。')
  } finally {
    removingCategoryId.value = null
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

    <section class="management-card category-management-panel" aria-label="分类列表" :aria-busy="loading">
      <div class="management-summary"><span>共 {{ categories.length }} 个一级分类</span><span>分类将按创建顺序展示在公共书库</span></div>
      <a-spin :spinning="loading">
        <div v-if="!loading && categories.length === 0" class="state-panel">
          <strong>还没有内容分类</strong>
          <p>先创建一级分类，再在其中添加具体的二级主题。</p>
          <a-button type="primary" @click="openCreate(null)">新增一级分类</a-button>
        </div>
        <div v-else class="category-admin-list">
          <article v-for="category in categories" :key="category.id" class="category-admin-group">
            <header>
              <div><span class="category-order">{{ category.sortOrder }}</span><div><h2>{{ category.name }}</h2><p>{{ category.children.length }} 个二级分类</p></div></div>
              <a-space wrap>
                <a-button type="primary" ghost @click="openCreate(category.id)">新增二级分类</a-button>
                <a-button @click="openEdit(category)">编辑</a-button>
                <a-popconfirm title="仅当一级分类不含二级分类时才可删除，确定继续吗？" ok-text="删除" cancel-text="取消" @confirm="remove(category)">
                  <a-button :loading="removingCategoryId === category.id" danger>删除</a-button>
                </a-popconfirm>
              </a-space>
            </header>
            <div v-if="category.children.length" class="category-admin-children">
              <div v-for="child in category.children" :key="child.id" class="category-admin-child">
                <span class="category-child-dot" aria-hidden="true"></span>
                <div><strong>{{ child.name }}</strong><small>二级分类 · 排序 {{ child.sortOrder }}</small></div>
                <a-space>
                  <a-button type="link" @click="openEdit(child)">编辑</a-button>
                  <a-popconfirm title="仅当二级分类没有电子书时才可删除，确定继续吗？" ok-text="删除" cancel-text="取消" @confirm="remove(child)">
                    <a-button :loading="removingCategoryId === child.id" danger type="link">删除</a-button>
                  </a-popconfirm>
                </a-space>
              </div>
            </div>
            <button v-else type="button" class="category-add-empty" @click="openCreate(category.id)">该分类下暂无二级分类，点击添加主题</button>
          </article>
        </div>
      </a-spin>
    </section>

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
