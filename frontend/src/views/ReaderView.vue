<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { useRoute } from 'vue-router'
import { LeftOutlined, RightOutlined } from '@ant-design/icons-vue'
import { getPublicChapter, getPublicChapters, recordChapterRead, type ChapterDetail, type ChapterItem } from '../chapter/chapter-api'
import { getPublicEbook, type EbookItem } from '../ebook/ebook-api'

const route = useRoute()
const ebookId = String(route.params.ebookId)
const ebook = ref<EbookItem | null>(null)
const chapters = ref<ChapterItem[]>([])
const activeChapter = ref<ChapterDetail | null>(null)
const loading = ref(true)
const chapterLoading = ref(false)
const error = ref('')

const activeIndex = computed(() => chapters.value.findIndex((chapter) => chapter.id === activeChapter.value?.id))
const prevChapter = computed(() => (activeIndex.value > 0 ? chapters.value[activeIndex.value - 1] : null))
const nextChapter = computed(() => (activeIndex.value >= 0 && activeIndex.value < chapters.value.length - 1 ? chapters.value[activeIndex.value + 1] : null))

async function openChapter(chapterId: string) {
  if (activeChapter.value?.id === chapterId || chapterLoading.value) return
  chapterLoading.value = true
  try {
    activeChapter.value = await getPublicChapter(ebookId, chapterId)
    window.scrollTo({ top: 0, behavior: 'smooth' })
    void recordChapterRead(ebookId, chapterId).catch(() => message.warning('阅读记录暂未保存，但不影响继续阅读。'))
  } catch (requestError) {
    message.error(requestError instanceof Error ? requestError.message : '章节加载失败')
  } finally {
    chapterLoading.value = false
  }
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [book, items] = await Promise.all([getPublicEbook(ebookId), getPublicChapters(ebookId)])
    ebook.value = book
    chapters.value = items
    if (items.length > 0) await openChapter(items[0].id)
  } catch (requestError) {
    error.value = requestError instanceof Error ? requestError.message : '阅读内容加载失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}

onMounted(() => { void load() })
</script>

<template>
  <main class="reader-page page-shell">
    <a-spin :spinning="loading">
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <template v-else-if="ebook">
        <header class="reader-header">
          <div>
            <p class="section-kicker">海洋生态 · 在线阅读</p>
            <h1>{{ ebook.title }}</h1>
            <p>{{ ebook.summary || '海洋生态数字阅读内容' }}</p>
          </div>
          <div class="reader-header-actions">
            <img v-if="ebook.coverUrl" class="reader-cover" :src="ebook.coverUrl" :alt="`${ebook.title} 封面`" />
            <RouterLink class="secondary-button on-dark" to="/">返回书库</RouterLink>
          </div>
        </header>
        <section v-if="chapters.length === 0" class="reader-empty">本书暂未提供可阅读章节。</section>
        <div v-else class="reader-layout">
          <aside class="reader-toc" aria-label="章节目录">
            <h2>目录</h2>
            <a-button v-for="chapter in chapters" :key="chapter.id" class="reader-toc-item" block :type="activeChapter?.id === chapter.id ? 'primary' : 'text'" :disabled="chapterLoading" @click="openChapter(chapter.id)"><span>{{ chapter.sortOrder }}.</span>{{ chapter.title }}</a-button>
          </aside>
          <article class="reader-article" aria-live="polite" :aria-busy="chapterLoading">
            <a-spin :spinning="chapterLoading">
              <template v-if="activeChapter">
                <p class="reader-chapter-index">第 {{ activeChapter.sortOrder }} 章 · 共 {{ chapters.length }} 章</p>
                <h2>{{ activeChapter.title }}</h2>
                <div class="reader-content" v-html="activeChapter.content"></div>
                <p v-if="activeChapter.sourceNote" class="reader-source">章节来源补充：{{ activeChapter.sourceNote }}</p>
                <nav class="reader-nav" aria-label="章节上下文导航">
                  <button v-if="prevChapter" type="button" class="reader-nav-button" @click="openChapter(prevChapter.id)">
                    <LeftOutlined aria-hidden="true" />
                    <span class="reader-nav-title">上一章 · {{ prevChapter.title }}</span>
                  </button>
                  <span v-else class="reader-nav-button is-disabled" aria-disabled="true"><LeftOutlined aria-hidden="true" />已是第一章</span>
                  <button v-if="nextChapter" type="button" class="reader-nav-button" @click="openChapter(nextChapter.id)">
                    <span class="reader-nav-title">下一章 · {{ nextChapter.title }}</span>
                    <RightOutlined aria-hidden="true" />
                  </button>
                  <span v-else class="reader-nav-button is-disabled" aria-disabled="true">已是最后一章<RightOutlined aria-hidden="true" /></span>
                </nav>
              </template>
            </a-spin>
          </article>
        </div>
      </template>
    </a-spin>
  </main>
</template>
