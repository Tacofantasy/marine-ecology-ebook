<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { authState } from '../auth/session'
import { getCategories, type CategoryTreeItem } from '../category/category-api'

interface HealthPayload {
  service: string
  status: string
}

interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

const loading = ref(true)
const healthy = ref(false)
const statusText = ref('正在检查后端服务…')
const categoryLoading = ref(true)
const categoryError = ref('')
const categories = ref<CategoryTreeItem[]>([])
const selectedCategoryId = ref<number | null>(null)

async function checkHealth() {
  loading.value = true
  try {
    const response = await fetch('/api/health')
    const body = (await response.json()) as ApiResponse<HealthPayload>
    healthy.value = response.ok && body.code === 0 && body.data.status === 'UP'
    statusText.value = healthy.value ? `已连接 ${body.data.service}` : '后端服务暂不可用，请确认后端已启动。'
  } catch {
    healthy.value = false
    statusText.value = '无法连接后端，请确认后端已启动。'
  } finally {
    loading.value = false
  }
}

async function loadCategories() {
  categoryLoading.value = true
  categoryError.value = ''
  try {
    categories.value = await getCategories()
  } catch (error) {
    categoryError.value = error instanceof Error ? error.message : '分类加载失败，请稍后重试。'
  } finally {
    categoryLoading.value = false
  }
}

function selectCategory(categoryId: number) {
  selectedCategoryId.value = categoryId
}

onMounted(() => {
  void checkHealth()
  void loadCategories()
})
</script>

<template>
  <main class="page-shell">
    <section class="hero" aria-labelledby="hero-title">
      <p class="eyebrow">MARINE ECOLOGY · DIGITAL READING</p>
      <h1 id="hero-title">从一页知识，走进蔚蓝生态</h1>
      <p class="hero-copy">面向海洋科普内容的阅读与管理平台。首期将提供分类浏览、电子书阅读、互动收藏与内容管理。</p>

      <a-card v-if="authState.user" class="welcome-card" :bordered="false">
        <p>你好，{{ authState.user.displayName }}</p>
        <span>{{ authState.user.role === 'SUPER_ADMIN' ? '总管理员' : authState.user.role === 'ADMIN' ? '子管理员' : '注册用户' }}</span>
        <RouterLink class="text-link" to="/profile">查看个人资料</RouterLink>
      </a-card>
      <div v-else class="hero-actions">
        <RouterLink class="primary-button" to="/login">登录平台</RouterLink>
        <RouterLink class="secondary-button on-dark" to="/register">注册账号</RouterLink>
      </div>

      <div class="service-card" aria-labelledby="service-title">
        <div>
          <p id="service-title" class="service-label">系统连接状态</p>
          <p class="service-status" :class="{ 'is-ready': healthy, 'is-error': !healthy && !loading }" role="status" aria-live="polite">
            <span class="status-dot" aria-hidden="true"></span>
            {{ statusText }}
          </p>
        </div>
        <a-button :loading="loading" @click="checkHealth">重新检查</a-button>
      </div>
    </section>

    <section class="category-browser" aria-labelledby="category-title">
      <div class="section-heading">
        <div>
          <p class="section-kicker">探索主题</p>
          <h2 id="category-title">按海洋生态分类浏览</h2>
        </div>
        <a-button :loading="categoryLoading" @click="loadCategories">刷新分类</a-button>
      </div>

      <a-spin :spinning="categoryLoading">
        <p v-if="categoryError" class="form-error" role="alert">{{ categoryError }}</p>
        <p v-else-if="!categoryLoading && categories.length === 0" class="category-empty">暂无分类内容。</p>
        <div v-else class="category-groups">
          <section v-for="category in categories" :key="category.id" class="category-group" :aria-label="category.name">
            <h3>{{ category.name }}</h3>
            <div class="category-actions">
              <a-button
                v-for="child in category.children"
                :key="child.id"
                :type="selectedCategoryId === child.id ? 'primary' : 'default'"
                @click="selectCategory(child.id)"
              >
                {{ child.name }}
              </a-button>
            </div>
          </section>
        </div>
      </a-spin>
      <p v-if="selectedCategoryId !== null" class="category-selection" role="status">
        已选择二级分类；电子书列表将在下一模块接入。
      </p>
    </section>

    <section class="next-steps" aria-labelledby="next-steps-title">
      <div>
        <p class="section-kicker">当前进度</p>
        <h2 id="next-steps-title">认证联调已就绪</h2>
      </div>
      <ol>
        <li>Vue 3 前端已连接 Spring Boot 健康检查接口</li>
        <li>可在浏览器完成注册、登录与身份验证</li>
        <li>已接入两级分类公开查询与管理员分类管理</li>
      </ol>
    </section>
  </main>
</template>
