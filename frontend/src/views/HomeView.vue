<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { AppstoreOutlined, ArrowRightOutlined, CalendarOutlined, LikeOutlined, ReadOutlined, SettingOutlined } from '@ant-design/icons-vue'
import { authState } from '../auth/session'
import { getCategories, type CategoryTreeItem } from '../category/category-api'
import { getPublicEbooks, type EbookItem } from '../ebook/ebook-api'
import StatsPanel from '../stats/StatsPanel.vue'

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
const selectedCategoryId = ref<string | null>(null)
const ebooks = ref<EbookItem[]>([])
const ebookLoading = ref(true)
const ebookError = ref('')
const ebookTotal = ref(0)
const ebookPage = ref(1)
const ebookPageSize = ref(10)
const keyword = ref('')

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

function selectCategory(categoryId: string) {
  selectedCategoryId.value = selectedCategoryId.value === categoryId ? null : categoryId
  ebookPage.value = 1
  void loadEbooks()
}

async function loadEbooks() {
  ebookLoading.value = true
  ebookError.value = ''
  try {
    const data = await getPublicEbooks({
      categoryId: selectedCategoryId.value,
      keyword: keyword.value,
      page: ebookPage.value,
      pageSize: ebookPageSize.value,
    })
    ebooks.value = data.list
    ebookTotal.value = data.total
  } catch (error) {
    ebookError.value = error instanceof Error ? error.message : '电子书加载失败，请稍后重试。'
  } finally {
    ebookLoading.value = false
  }
}

function searchEbooks() {
  ebookPage.value = 1
  void loadEbooks()
}

function formatPublishedAt(value: string | null) {
  return value ? new Date(value).toLocaleDateString('zh-CN') : ''
}

function changeEbookPage(page: number, pageSize: number) {
  ebookPage.value = page
  ebookPageSize.value = pageSize
  void loadEbooks()
}

onMounted(() => {
  void checkHealth()
  void loadCategories()
  void loadEbooks()
})
</script>

<template>
  <main class="page-shell">
    <section class="hero" aria-labelledby="hero-title">
      <span class="hero-bubble is-one" aria-hidden="true"></span>
      <span class="hero-bubble is-two" aria-hidden="true"></span>
      <span class="hero-bubble is-three" aria-hidden="true"></span>
      <svg class="hero-waves" viewBox="0 0 1440 120" preserveAspectRatio="none" aria-hidden="true">
        <path d="M0,64 C240,112 480,16 720,48 C960,80 1200,96 1440,56 L1440,120 L0,120 Z" fill="rgba(165, 243, 252, 0.14)" />
        <path d="M0,88 C260,48 520,112 780,84 C1040,56 1240,72 1440,88 L1440,120 L0,120 Z" fill="rgba(103, 232, 249, 0.12)" />
      </svg>

      <p class="eyebrow">MARINE ECOLOGY · DIGITAL READING</p>
      <h1 id="hero-title">从一页知识，走进蔚蓝生态</h1>
      <p class="hero-copy">面向海洋科普内容的阅读与管理平台。在这里按主题浏览分类、沉浸阅读电子书，并与更多海洋知识相遇。</p>

      <div v-if="authState.user" class="glass-card welcome-card">
        <p>你好，{{ authState.user.displayName }}</p>
        <span class="welcome-role">{{ authState.user.role === 'SUPER_ADMIN' ? '总管理员' : authState.user.role === 'ADMIN' ? '子管理员' : '注册用户' }}</span>
        <RouterLink class="text-link" to="/profile">查看个人资料</RouterLink>
      </div>
      <div v-else class="hero-actions">
        <RouterLink class="primary-button" to="/login">登录平台</RouterLink>
        <RouterLink class="secondary-button on-dark" to="/register">注册账号</RouterLink>
      </div>

      <div class="glass-card" aria-labelledby="service-title">
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

    <StatsPanel />

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
                class="category-pill"
                :type="selectedCategoryId === child.id ? 'primary' : 'default'"
                @click="selectCategory(child.id)"
              >
                {{ child.name }}
              </a-button>
            </div>
          </section>
        </div>
      </a-spin>
    </section>

    <section class="ebook-browser" aria-labelledby="ebook-title">
      <div class="section-heading">
        <div>
          <p class="section-kicker">数字阅读</p>
          <h2 id="ebook-title">探索已发布电子书</h2>
        </div>
        <a-input-search v-model:value="keyword" class="ebook-search" allow-clear placeholder="搜索标题或简介" enter-button="搜索" @search="searchEbooks" />
      </div>
      <p v-if="selectedCategoryId !== null" class="category-selection" role="status">已按二级分类筛选；再次点击分类可取消筛选。</p>
      <a-spin :spinning="ebookLoading">
        <p v-if="ebookError" class="form-error" role="alert">{{ ebookError }}</p>
        <p v-else-if="!ebookLoading && ebooks.length === 0" class="category-empty">暂无已发布电子书。请尝试更换分类或关键词。</p>
        <div v-else class="ebook-grid">
          <article v-for="ebook in ebooks" :key="ebook.id" class="ebook-card">
            <div class="ebook-cover-wrap">
              <img v-if="ebook.coverUrl" :src="ebook.coverUrl" :alt="`${ebook.title} 封面`" class="ebook-cover" loading="lazy" />
              <div v-else class="ebook-cover ebook-cover-placeholder" aria-hidden="true">海洋科普</div>
            </div>
            <div class="ebook-card-body">
              <p class="ebook-category">{{ ebook.categoryName }}</p>
              <h3>{{ ebook.title }}</h3>
              <p class="ebook-summary">{{ ebook.summary || '暂无简介' }}</p>
              <div class="ebook-card-footer">
                <div class="ebook-card-stats">
                  <time v-if="ebook.publishedAt" :datetime="ebook.publishedAt"><CalendarOutlined aria-hidden="true" /> 发布于 {{ formatPublishedAt(ebook.publishedAt) }}</time>
                  <span><LikeOutlined aria-hidden="true" /> {{ ebook.likeCount ?? '0' }}</span>
                </div>
                <RouterLink class="reader-link" :to="`/ebooks/${ebook.id}/read`">开始阅读 <ArrowRightOutlined aria-hidden="true" /></RouterLink>
              </div>
            </div>
          </article>
        </div>
      </a-spin>
      <a-pagination v-if="ebookTotal > 10" class="public-pagination" :current="ebookPage" :page-size="ebookPageSize" :total="ebookTotal" :page-size-options="['10', '20']" show-size-changer @change="changeEbookPage" />
    </section>

    <section aria-labelledby="features-title">
      <div class="section-heading">
        <div>
          <p class="section-kicker">平台能力</p>
          <h2 id="features-title">一站式海洋科普阅读体验</h2>
        </div>
      </div>
      <div class="feature-grid">
        <article class="feature-card">
          <span class="feature-icon" aria-hidden="true"><AppstoreOutlined /></span>
          <h3>两级分类导览</h3>
          <p>按海洋生态主题组织内容，一级分类统领方向、二级分类精确归属，快速定位感兴趣的科普领域。</p>
        </article>
        <article class="feature-card">
          <span class="feature-icon" aria-hidden="true"><ReadOutlined /></span>
          <h3>沉浸式章节阅读</h3>
          <p>电子书由线性章节构成，阅读页提供目录导航与上下文切换，阅读量统计帮助内容持续优化。</p>
        </article>
        <article class="feature-card">
          <span class="feature-icon" aria-hidden="true"><SettingOutlined /></span>
          <h3>规范化内容管理</h3>
          <p>草稿先行、发布校验、来源披露，管理员在后台完成从录入、编辑到发布的全流程维护。</p>
        </article>
      </div>
    </section>
  </main>
</template>
