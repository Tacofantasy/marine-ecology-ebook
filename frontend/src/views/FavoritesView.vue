<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { CalendarOutlined, DeleteOutlined, HeartFilled, LikeOutlined, ReadOutlined } from '@ant-design/icons-vue'
import { getFavorites, unfavoriteEbook, type FavoriteEbookItem } from '../interaction/interaction-api'

const favorites = ref<FavoriteEbookItem[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const loading = ref(true)
const error = ref('')
const removingId = ref<string | null>(null)

function formatDate(value: string | null) {
  return value ? new Date(value).toLocaleDateString('zh-CN') : ''
}

async function loadFavorites() {
  loading.value = true
  error.value = ''
  try {
    const data = await getFavorites(page.value, pageSize.value)
    favorites.value = data.list
    total.value = data.total
  } catch (requestError) {
    error.value = requestError instanceof Error ? requestError.message : '收藏列表加载失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}

async function removeFavorite(ebookId: string) {
  removingId.value = ebookId
  try {
    await unfavoriteEbook(ebookId)
    if (favorites.value.length === 1 && page.value > 1) {
      page.value -= 1
    }
    await loadFavorites()
  } catch (requestError) {
    error.value = requestError instanceof Error ? requestError.message : '取消收藏失败，请稍后重试。'
  } finally {
    removingId.value = null
  }
}

function changePage(nextPage: number, nextPageSize: number) {
  page.value = nextPage
  pageSize.value = nextPageSize
  void loadFavorites()
}

onMounted(() => { void loadFavorites() })
</script>

<template>
  <main class="favorites-page page-shell">
    <section class="favorites-hero" aria-labelledby="favorites-title">
      <div>
        <p class="section-kicker">MY LIBRARY</p>
        <h1 id="favorites-title">我的收藏</h1>
        <p>保存值得反复阅读的海洋科普电子书，随时回到书中的知识世界。</p>
      </div>
      <div class="favorites-hero-mark" aria-hidden="true"><HeartFilled /></div>
    </section>

    <section class="favorites-panel" aria-label="收藏电子书列表" :aria-busy="loading">
      <a-spin :spinning="loading">
        <div v-if="error" class="favorites-feedback" role="alert">
          <p>{{ error }}</p>
          <a-button type="primary" @click="loadFavorites">重新加载</a-button>
        </div>

        <div v-else-if="!loading && favorites.length === 0" class="favorites-feedback favorites-empty">
          <HeartFilled aria-hidden="true" />
          <h2>还没有收藏电子书</h2>
          <p>在阅读页收藏感兴趣的电子书，它们会出现在这里。</p>
          <RouterLink class="primary-button" to="/">去书库看看</RouterLink>
        </div>

        <div v-else class="favorites-grid">
          <article v-for="ebook in favorites" :key="ebook.id" class="favorite-card">
            <img v-if="ebook.coverUrl" :src="ebook.coverUrl" :alt="`${ebook.title} 封面`" class="favorite-cover" loading="lazy" />
            <div v-else class="favorite-cover ebook-cover-placeholder" aria-hidden="true">海洋科普</div>
            <div class="favorite-card-body">
              <div class="favorite-card-meta">
                <span>{{ ebook.categoryName }}</span>
                <span><LikeOutlined aria-hidden="true" /> {{ ebook.likeCount }}</span>
              </div>
              <h2>{{ ebook.title }}</h2>
              <p class="favorite-summary">{{ ebook.summary || '暂无简介' }}</p>
              <p class="favorite-date"><CalendarOutlined aria-hidden="true" /> 收藏于 {{ formatDate(ebook.favoritedAt) }}</p>
              <div class="favorite-card-actions">
                <RouterLink class="reader-link" :to="`/ebooks/${ebook.id}/read`"><ReadOutlined aria-hidden="true" /> 阅读此书</RouterLink>
                <a-button danger type="text" :loading="removingId === ebook.id" :disabled="removingId !== null" @click="removeFavorite(ebook.id)">
                  <DeleteOutlined aria-hidden="true" /> 取消收藏
                </a-button>
              </div>
            </div>
          </article>
        </div>
      </a-spin>
      <a-pagination v-if="!loading && !error && total > 10" class="public-pagination" :current="page" :page-size="pageSize" :total="total" :page-size-options="['10', '20']" show-size-changer @change="changePage" />
    </section>
  </main>
</template>
