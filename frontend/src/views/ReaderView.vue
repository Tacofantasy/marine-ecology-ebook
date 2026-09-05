<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { useRoute, useRouter } from 'vue-router'
import { BookOutlined, HeartFilled, HeartOutlined, LeftOutlined, LikeFilled, LikeOutlined, MenuUnfoldOutlined, RightOutlined } from '@ant-design/icons-vue'
import { authState } from '../auth/session'
import { getPublicChapter, getPublicChapters, recordChapterRead, type ChapterDetail, type ChapterItem } from '../chapter/chapter-api'
import { getPublicEbook, type EbookItem } from '../ebook/ebook-api'
import { favoriteEbook, getInteractionState, likeEbook, unfavoriteEbook, unlikeEbook, type InteractionState } from '../interaction/interaction-api'

const route = useRoute()
const router = useRouter()
const ebookId = String(route.params.ebookId)
const ebook = ref<EbookItem | null>(null)
const chapters = ref<ChapterItem[]>([])
const activeChapter = ref<ChapterDetail | null>(null)
const loading = ref(true)
const chapterLoading = ref(false)
const error = ref('')
const chapterError = ref('')
const requestedChapterId = ref('')
const interaction = ref<InteractionState | null>(null)
const interactionLoading = ref<'like' | 'favorite' | null>(null)
const interactionError = ref('')
const mobileTocOpen = ref(false)

const activeIndex = computed(() => chapters.value.findIndex((chapter) => chapter.id === activeChapter.value?.id))
const prevChapter = computed(() => (activeIndex.value > 0 ? chapters.value[activeIndex.value - 1] : null))
const nextChapter = computed(() => (activeIndex.value >= 0 && activeIndex.value < chapters.value.length - 1 ? chapters.value[activeIndex.value + 1] : null))
const isReaderUser = computed(() => authState.user?.role === 'USER')
const visibleLikeCount = computed(() => interaction.value?.likeCount ?? ebook.value?.likeCount ?? '0')

async function openChapter(chapterId: string) {
  if (activeChapter.value?.id === chapterId || chapterLoading.value) return
  chapterLoading.value = true
  chapterError.value = ''
  requestedChapterId.value = chapterId
  try {
    activeChapter.value = await getPublicChapter(ebookId, chapterId)
    mobileTocOpen.value = false
    if (route.query.chapter !== chapterId) {
      await router.push({ query: { ...route.query, chapter: chapterId } })
    }
    window.scrollTo({ top: 0, behavior: 'smooth' })
    void recordChapterRead(ebookId, chapterId).catch(() => message.warning('阅读记录暂未保存，但不影响继续阅读。'))
  } catch (requestError) {
    chapterError.value = requestError instanceof Error ? requestError.message : '章节加载失败'
  } finally {
    chapterLoading.value = false
  }
}

async function load() {
  loading.value = true
  error.value = ''
  interactionError.value = ''
  activeChapter.value = null
  try {
    const [book, items] = await Promise.all([getPublicEbook(ebookId), getPublicChapters(ebookId)])
    ebook.value = book
    chapters.value = items
    if (isReaderUser.value) {
      try {
        interaction.value = await getInteractionState(ebookId)
      } catch (requestError) {
        interactionError.value = requestError instanceof Error ? requestError.message : '互动状态加载失败。'
      }
    }
    if (items.length > 0) {
      const requested = typeof route.query.chapter === 'string' ? route.query.chapter : items[0].id
      await openChapter(requested)
    }
  } catch (requestError) {
    error.value = requestError instanceof Error ? requestError.message : '阅读内容加载失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}

async function toggleInteraction(type: 'like' | 'favorite') {
  if (!isReaderUser.value) {
    await router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  if (interactionLoading.value) return

  interactionError.value = ''
  interactionLoading.value = type
  try {
    const current = interaction.value ?? await getInteractionState(ebookId)
    interaction.value = type === 'like'
      ? current.liked ? await unlikeEbook(ebookId) : await likeEbook(ebookId)
      : current.favorited ? await unfavoriteEbook(ebookId) : await favoriteEbook(ebookId)
    if (ebook.value) ebook.value.likeCount = interaction.value.likeCount
  } catch (requestError) {
    interactionError.value = requestError instanceof Error ? requestError.message : '互动操作失败，请稍后重试。'
    message.error(interactionError.value)
  } finally {
    interactionLoading.value = null
  }
}

onMounted(() => { void load() })
watch(() => route.query.chapter, (chapterId) => {
  if (!loading.value && !chapterLoading.value) {
    const id = typeof chapterId === 'string' ? chapterId : chapters.value[0]?.id
    if (id) void openChapter(id)
  }
})
</script>

<template>
  <main class="reader-page reader-page-v2">
    <a-spin :spinning="loading">
      <div v-if="error" class="state-panel state-panel-error reader-load-error" role="alert">
        <strong>阅读内容暂时无法加载</strong>
        <p>{{ error }}</p>
        <a-button type="primary" @click="load">重新加载</a-button>
      </div>

      <template v-else-if="ebook">
        <div class="reader-toolbar">
          <div class="reader-toolbar-inner">
            <RouterLink class="reader-back" to="/"><LeftOutlined aria-hidden="true" />返回书库</RouterLink>
            <div class="reader-book-context">
              <BookOutlined aria-hidden="true" />
              <span><small>正在阅读</small><strong>{{ ebook.title }}</strong></span>
            </div>
            <div class="reader-toolbar-actions" aria-label="电子书互动">
              <span class="reader-like-count" aria-live="polite"><LikeOutlined aria-hidden="true" />{{ visibleLikeCount }}</span>
              <template v-if="!authState.user || isReaderUser">
                <a-button
                  :type="interaction?.liked ? 'primary' : 'default'"
                  :loading="interactionLoading === 'like'"
                  :disabled="interactionLoading !== null"
                  :aria-pressed="interaction?.liked ?? false"
                  @click="toggleInteraction('like')"
                ><LikeFilled v-if="interaction?.liked" aria-hidden="true" /><LikeOutlined v-else aria-hidden="true" />{{ interaction?.liked ? '已点赞' : '点赞' }}</a-button>
                <a-button
                  :type="interaction?.favorited ? 'primary' : 'default'"
                  :loading="interactionLoading === 'favorite'"
                  :disabled="interactionLoading !== null"
                  :aria-pressed="interaction?.favorited ?? false"
                  @click="toggleInteraction('favorite')"
                ><HeartFilled v-if="interaction?.favorited" aria-hidden="true" /><HeartOutlined v-else aria-hidden="true" />{{ interaction?.favorited ? '已收藏' : '收藏' }}</a-button>
              </template>
              <a-button class="reader-mobile-toc-button" @click="mobileTocOpen = true"><MenuUnfoldOutlined aria-hidden="true" />目录</a-button>
            </div>
          </div>
        </div>

        <div class="reader-stage">
          <aside class="reader-toc reader-toc-v2" aria-label="章节目录">
            <div class="reader-book-mini">
              <img v-if="ebook.coverUrl" :src="ebook.coverUrl" :alt="`${ebook.title} 封面`" />
              <div v-else class="reader-mini-placeholder" aria-hidden="true"><BookOutlined /></div>
              <div><strong>{{ ebook.title }}</strong><span>{{ chapters.length }} 个章节</span></div>
            </div>
            <h2>目录</h2>
            <div class="reader-toc-scroll">
              <a-button v-for="chapter in chapters" :key="chapter.id" class="reader-toc-item" block :type="activeChapter?.id === chapter.id ? 'primary' : 'text'" :disabled="chapterLoading" @click="openChapter(chapter.id)"><span>{{ chapter.sortOrder }}</span>{{ chapter.title }}</a-button>
            </div>
          </aside>

          <section v-if="chapters.length === 0" class="reader-empty">本书暂未提供可阅读章节。</section>
          <article v-else class="reader-article reader-article-v2" aria-live="polite" :aria-busy="chapterLoading">
            <div class="reader-article-heading">
              <p class="reader-chapter-index">CHAPTER {{ activeChapter?.sortOrder || '—' }} · 共 {{ chapters.length }} 章</p>
              <h1>{{ activeChapter?.title || ebook.title }}</h1>
              <p class="reader-book-summary">{{ ebook.summary || '海洋生态数字阅读内容' }}</p>
            </div>

            <p v-if="interactionError" class="reader-inline-error" role="alert">{{ interactionError }}</p>
            <a-alert v-if="chapterError" type="error" :message="chapterError" show-icon />
            <a-button v-if="chapterError" @click="openChapter(requestedChapterId)">重试加载章节</a-button>
            <a-spin :spinning="chapterLoading">
              <template v-if="activeChapter">
                <div class="reader-content" v-html="activeChapter.content"></div>
                <details v-if="activeChapter.sourceNote || ebook.sourceNote" class="reader-sources" open>
                  <summary>查看内容来源</summary>
                  <p v-if="activeChapter.sourceNote">章节来源补充：{{ activeChapter.sourceNote }}</p>
                  <p v-if="ebook.sourceNote">内容来源说明：{{ ebook.sourceNote }}</p>
                </details>
                <nav class="reader-nav" aria-label="章节上下文导航">
                  <button v-if="prevChapter" type="button" class="reader-nav-button" :aria-label="`上一章 · ${prevChapter.title}`" @click="openChapter(prevChapter.id)">
                    <LeftOutlined aria-hidden="true" /><span><small>上一章</small><span class="reader-nav-title">{{ prevChapter.title }}</span></span>
                  </button>
                  <span v-else class="reader-nav-button is-disabled" aria-disabled="true"><LeftOutlined aria-hidden="true" /><span><small>上一章</small>已是第一章</span></span>
                  <button v-if="nextChapter" type="button" class="reader-nav-button is-next" :aria-label="`下一章 · ${nextChapter.title}`" @click="openChapter(nextChapter.id)">
                    <span><small>下一章</small><span class="reader-nav-title">{{ nextChapter.title }}</span></span><RightOutlined aria-hidden="true" />
                  </button>
                  <span v-else class="reader-nav-button is-disabled is-next" aria-disabled="true"><span><small>下一章</small>已是最后一章</span><RightOutlined aria-hidden="true" /></span>
                </nav>
              </template>
            </a-spin>
          </article>
        </div>

        <div v-if="!authState.user || isReaderUser" class="reader-mobile-actions" aria-label="移动端电子书互动">
          <span aria-live="polite"><LikeOutlined aria-hidden="true" />{{ visibleLikeCount }} 人点赞</span>
          <a-button :type="interaction?.liked ? 'primary' : 'default'" :loading="interactionLoading === 'like'" :disabled="interactionLoading !== null" :aria-pressed="interaction?.liked ?? false" @click="toggleInteraction('like')"><LikeFilled v-if="interaction?.liked" aria-hidden="true" /><LikeOutlined v-else aria-hidden="true" />{{ interaction?.liked ? '已点赞' : '点赞' }}</a-button>
          <a-button :type="interaction?.favorited ? 'primary' : 'default'" :loading="interactionLoading === 'favorite'" :disabled="interactionLoading !== null" :aria-pressed="interaction?.favorited ?? false" @click="toggleInteraction('favorite')"><HeartFilled v-if="interaction?.favorited" aria-hidden="true" /><HeartOutlined v-else aria-hidden="true" />{{ interaction?.favorited ? '已收藏' : '收藏' }}</a-button>
        </div>

        <a-drawer v-model:open="mobileTocOpen" placement="left" width="min(340px, 90vw)" title="章节目录">
          <nav class="reader-drawer-toc">
            <a-button v-for="chapter in chapters" :key="chapter.id" class="reader-toc-item" block :type="activeChapter?.id === chapter.id ? 'primary' : 'text'" :disabled="chapterLoading" @click="openChapter(chapter.id)"><span>{{ chapter.sortOrder }}</span>{{ chapter.title }}</a-button>
          </nav>
        </a-drawer>
      </template>
    </a-spin>
  </main>
</template>
