<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { HeartFilled, ReadOutlined, UserOutlined } from '@ant-design/icons-vue'
import EbookCard from '../components/EbookCard.vue'
import { getFavorites, unfavoriteEbook, type FavoriteEbookItem } from '../interaction/interaction-api'

const favorites = ref<FavoriteEbookItem[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const loading = ref(true)
const error = ref('')
const removingId = ref<string | null>(null)

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
    if (favorites.value.length === 1 && page.value > 1) page.value -= 1
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
  <main class="favorites-page page-shell personal-page">
    <nav class="personal-tabs" aria-label="个人空间">
      <RouterLink to="/favorites"><HeartFilled aria-hidden="true" />我的收藏</RouterLink>
      <RouterLink to="/profile"><UserOutlined aria-hidden="true" />账号资料</RouterLink>
    </nav>

    <header class="personal-header">
      <div>
        <p class="section-kicker">MY READING</p>
        <h1>我的收藏</h1>
        <p>把感兴趣的海洋知识留在这里，随时继续阅读。</p>
      </div>
      <div class="personal-count"><strong>{{ total }}</strong><span>本已收藏</span></div>
    </header>

    <section class="favorites-panel" aria-label="收藏电子书列表" :aria-busy="loading">
      <a-spin :spinning="loading">
        <div v-if="error" class="state-panel state-panel-error" role="alert">
          <strong>收藏内容暂时无法加载</strong>
          <p>{{ error }}</p>
          <a-button type="primary" @click="loadFavorites">重新加载</a-button>
        </div>

        <div v-else-if="!loading && favorites.length === 0" class="state-panel favorites-empty">
          <HeartFilled aria-hidden="true" />
          <strong>还没有收藏电子书</strong>
          <p>在阅读页收藏感兴趣的电子书，它们会出现在这里。</p>
          <RouterLink class="primary-button" to="/"><ReadOutlined aria-hidden="true" />去书库看看</RouterLink>
        </div>

        <div v-else class="ebook-grid-v2 favorites-grid-v2">
          <EbookCard
            v-for="ebook in favorites"
            :key="ebook.id"
            :ebook="ebook"
            :favorite-date="ebook.favoritedAt"
            :removing="removingId === ebook.id"
            @remove="removeFavorite"
          />
        </div>
      </a-spin>
      <a-pagination v-if="!loading && !error && total > pageSize" class="public-pagination" :current="page" :page-size="pageSize" :total="total" :page-size-options="['10', '20']" show-size-changer @change="changePage" />
    </section>
  </main>
</template>
