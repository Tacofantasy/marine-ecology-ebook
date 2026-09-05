<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ArrowRightOutlined, BookOutlined, CompassOutlined, SearchOutlined } from '@ant-design/icons-vue'
import EbookCard from '../components/EbookCard.vue'
import { authState } from '../auth/session'
import { getCategories, type CategoryTreeItem } from '../category/category-api'
import { getPublicEbooks, type EbookItem } from '../ebook/ebook-api'
import StatsPanel from '../stats/StatsPanel.vue'

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
let ebookRequest = 0

const secondaryCategoryCount = computed(() => categories.value.reduce((total, item) => total + item.children.length, 0))
const selectedCategoryName = computed(() => categories.value.flatMap((item) => item.children).find((item) => item.id === selectedCategoryId.value)?.name ?? '')
const hasFilters = computed(() => Boolean(selectedCategoryId.value || keyword.value.trim()))

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

function selectCategory(categoryId: string | null) {
  selectedCategoryId.value = categoryId
  ebookPage.value = 1
  void loadEbooks()
}

async function loadEbooks() {
  const requestId = ++ebookRequest
  ebookLoading.value = true
  ebookError.value = ''
  try {
    const data = await getPublicEbooks({
      categoryId: selectedCategoryId.value,
      keyword: keyword.value,
      page: ebookPage.value,
      pageSize: ebookPageSize.value,
    })
    if (requestId !== ebookRequest) return
    ebooks.value = data.list
    ebookTotal.value = data.total
  } catch (error) {
    if (requestId !== ebookRequest) return
    ebookError.value = error instanceof Error ? error.message : '电子书加载失败，请稍后重试。'
  } finally {
    if (requestId === ebookRequest) ebookLoading.value = false
  }
}

function searchEbooks() {
  ebookPage.value = 1
  void loadEbooks()
  document.querySelector('#library-results')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function clearFilters() {
  keyword.value = ''
  selectedCategoryId.value = null
  ebookPage.value = 1
  void loadEbooks()
}

function changeEbookPage(page: number, pageSize: number) {
  ebookPage.value = page
  ebookPageSize.value = pageSize
  void loadEbooks()
}

onMounted(() => {
  void loadCategories()
  void loadEbooks()
})
</script>

<template>
  <main class="page-shell home-page">
    <section class="hero hero-v2" aria-labelledby="hero-title">
      <span class="hero-orbit hero-orbit-one" aria-hidden="true"></span>
      <span class="hero-orbit hero-orbit-two" aria-hidden="true"></span>
      <div class="hero-content-v2">
        <p class="eyebrow">MARINE ECOLOGY LIBRARY</p>
        <h1 id="hero-title">探索海洋，<br /><span>从一本好书开始</span></h1>
        <p class="hero-copy">按生态主题发现可靠的海洋科普内容，在清晰的章节目录中持续阅读。</p>
        <a-input-search
          v-model:value="keyword"
          class="hero-search"
          size="large"
          allow-clear
          placeholder="搜索电子书标题或简介"
          enter-button="搜索书库"
          @search="searchEbooks"
        />
        <div class="hero-sub-actions">
          <a href="#library-results"><CompassOutlined aria-hidden="true" />浏览全部内容</a>
          <RouterLink v-if="!authState.user" to="/register">免费注册，保存收藏<ArrowRightOutlined aria-hidden="true" /></RouterLink>
          <RouterLink v-else-if="authState.user.role === 'USER'" to="/favorites">打开我的收藏<ArrowRightOutlined aria-hidden="true" /></RouterLink>
          <RouterLink v-else to="/admin">进入内容管理台<ArrowRightOutlined aria-hidden="true" /></RouterLink>
        </div>
      </div>

      <div class="hero-visual" aria-label="书库概览">
        <div class="hero-book-card hero-book-main">
          <BookOutlined aria-hidden="true" />
          <strong>{{ ebookLoading ? '正在探索' : `${ebookTotal} 本` }}</strong>
          <span>已发布电子书</span>
        </div>
        <div class="hero-book-card hero-book-small">
          <CompassOutlined aria-hidden="true" />
          <strong>{{ categoryLoading ? '—' : secondaryCategoryCount }}</strong>
          <span>个生态主题</span>
        </div>
        <svg class="hero-contour" viewBox="0 0 420 230" aria-hidden="true">
          <path d="M8 165C75 92 126 202 201 128s124-54 211-118" />
          <path d="M10 201c79-48 127 24 202-48s128-26 200-100" />
        </svg>
      </div>
    </section>

    <section id="library-results" class="library-section" aria-labelledby="library-title">
      <div class="section-heading library-heading">
        <div>
          <p class="section-kicker">数字书库</p>
          <h2 id="library-title">找到你想了解的海洋主题</h2>
          <p>选择分类，或使用关键词缩小范围。</p>
        </div>
        <span class="result-count" aria-live="polite">{{ ebookLoading ? '正在检索…' : `找到 ${ebookTotal} 本电子书` }}</span>
      </div>

      <div class="library-layout">
        <aside class="library-filters" aria-label="电子书分类筛选">
          <div class="filter-title-row">
            <strong>主题分类</strong>
            <a-button v-if="hasFilters" type="link" size="small" @click="clearFilters">清空筛选</a-button>
          </div>
          <a-spin :spinning="categoryLoading">
            <p v-if="categoryError" class="form-error" role="alert">{{ categoryError }}</p>
            <button type="button" class="library-filter-all" :class="{ active: selectedCategoryId === null }" :aria-pressed="selectedCategoryId === null" @click="selectCategory(null)">全部内容</button>
            <div v-for="category in categories" :key="category.id" class="library-filter-group">
              <h3>{{ category.name }}</h3>
              <div class="library-filter-options">
                <button v-for="child in category.children" :key="child.id" type="button" :class="{ active: selectedCategoryId === child.id }" :aria-pressed="selectedCategoryId === child.id" @click="selectCategory(child.id)">{{ child.name }}</button>
              </div>
            </div>
          </a-spin>
        </aside>

        <div class="library-results">
          <div v-if="hasFilters" class="active-filters" role="status">
            <span>当前筛选</span>
            <button v-if="selectedCategoryName" type="button" @click="selectCategory(null)">{{ selectedCategoryName }} ×</button>
            <button v-if="keyword.trim()" type="button" @click="keyword = ''; searchEbooks()">“{{ keyword.trim() }}” ×</button>
          </div>

          <a-spin :spinning="ebookLoading">
            <div v-if="ebookError" class="state-panel state-panel-error" role="alert">
              <strong>书库暂时无法加载</strong>
              <p>{{ ebookError }}</p>
              <a-button type="primary" @click="loadEbooks">重新加载</a-button>
            </div>
            <div v-else-if="!ebookLoading && ebooks.length === 0" class="state-panel">
              <SearchOutlined aria-hidden="true" />
              <strong>{{ hasFilters ? '没有找到匹配的电子书' : '书库正在准备内容' }}</strong>
              <p>{{ hasFilters ? '尝试更换关键词或清空分类筛选。' : '管理员发布内容后会在这里展示。' }}</p>
              <a-button v-if="hasFilters" @click="clearFilters">查看全部内容</a-button>
            </div>
            <div v-else class="ebook-grid-v2">
              <EbookCard v-for="ebook in ebooks" :key="ebook.id" :ebook="ebook" />
            </div>
          </a-spin>
          <a-pagination v-if="ebookTotal > ebookPageSize" class="public-pagination" :current="ebookPage" :page-size="ebookPageSize" :total="ebookTotal" :page-size-options="['10', '20']" show-size-changer @change="changeEbookPage" />
        </div>
      </div>
    </section>

    <section class="home-stats" aria-labelledby="home-stats-title">
      <div class="section-heading compact-heading">
        <div>
          <p class="section-kicker">平台动态</p>
          <h2 id="home-stats-title">知识正在被持续阅读</h2>
        </div>
        <p>阅读与点赞数据每日汇总，帮助内容持续优化。</p>
      </div>
      <StatsPanel />
    </section>
  </main>
</template>
