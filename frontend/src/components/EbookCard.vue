<script setup lang="ts">
import { ArrowRightOutlined, CalendarOutlined, DeleteOutlined, LikeOutlined } from '@ant-design/icons-vue'
interface EbookCardItem {
  id: string
  categoryName: string
  title: string
  coverUrl: string | null
  summary: string | null
  publishedAt: string | null
  likeCount?: string
}

const props = defineProps<{
  ebook: EbookCardItem
  favoriteDate?: string
  removing?: boolean
}>()

defineEmits<{ remove: [ebookId: string] }>()

function formatDate(value: string | null | undefined) {
  return value ? new Date(value).toLocaleDateString('zh-CN') : ''
}
</script>

<template>
  <article class="ebook-card-v2">
    <RouterLink class="ebook-cover-link" :to="`/ebooks/${props.ebook.id}/read`" :aria-label="`阅读《${props.ebook.title}》`">
      <img v-if="props.ebook.coverUrl" :src="props.ebook.coverUrl" :alt="`${props.ebook.title} 封面`" class="ebook-cover-v2" loading="lazy" />
      <div v-else class="ebook-cover-v2 ebook-cover-placeholder-v2" aria-hidden="true"><span>MARINE</span>海洋科普</div>
      <span class="ebook-category-badge">{{ props.ebook.categoryName }}</span>
    </RouterLink>

    <div class="ebook-card-content">
      <div class="ebook-card-meta">
        <time v-if="props.favoriteDate" :datetime="props.favoriteDate"><CalendarOutlined aria-hidden="true" />收藏于 {{ formatDate(props.favoriteDate) }}</time>
        <time v-else-if="props.ebook.publishedAt" :datetime="props.ebook.publishedAt"><CalendarOutlined aria-hidden="true" />{{ formatDate(props.ebook.publishedAt) }}</time>
        <span><LikeOutlined aria-hidden="true" />{{ props.ebook.likeCount ?? '0' }}</span>
      </div>
      <RouterLink class="ebook-title-link" :to="`/ebooks/${props.ebook.id}/read`"><h3>{{ props.ebook.title }}</h3></RouterLink>
      <p class="ebook-summary-v2">{{ props.ebook.summary || '这本电子书暂未添加简介，进入阅读页查看章节内容。' }}</p>
      <div class="ebook-card-actions-v2">
        <RouterLink class="reader-link" :to="`/ebooks/${props.ebook.id}/read`">
          {{ props.favoriteDate ? '阅读此书' : '开始阅读' }}<ArrowRightOutlined aria-hidden="true" />
        </RouterLink>
        <a-button v-if="props.favoriteDate" danger type="text" :loading="props.removing" :disabled="props.removing" @click="$emit('remove', props.ebook.id)">
          <DeleteOutlined aria-hidden="true" />取消收藏
        </a-button>
      </div>
    </div>
  </article>
</template>
