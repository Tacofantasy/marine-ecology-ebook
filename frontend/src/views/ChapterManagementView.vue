<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { useRoute, useRouter } from 'vue-router'
import RichTextEditor from '../chapter/RichTextEditor.vue'
import {
  createChapter, deleteChapter, getAdminChapter, getAdminChapters, reorderChapters, updateChapter,
  type ChapterItem,
} from '../chapter/chapter-api'
import { getAdminEbook, type EbookItem } from '../ebook/ebook-api'

const route = useRoute()
const router = useRouter()
const ebookId = computed(() => String(route.params.ebookId))
const ebook = ref<EbookItem | null>(null)
const chapters = ref<ChapterItem[]>([])
const keyword = ref('')
const loading = ref(false)
const saving = ref(false)
const ordering = ref(false)
const drawerOpen = ref(false)
const editing = ref<ChapterItem | null>(null)
const form = reactive({ title: '', content: '', sourceNote: '' })
const initialForm = ref('')
const isDraft = computed(() => ebook.value?.status === 'DRAFT')
const drawerTitle = computed(() => editing.value ? '编辑章节' : '新增章节')
// 本地关键词过滤：章节随电子书全量加载，搜索在前端完成，避免改变后端全量返回语义
const filteredChapters = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return chapters.value
  return chapters.value.filter(
    (chapter) => chapter.title.toLowerCase().includes(kw)
      || (chapter.sourceNote ?? '').toLowerCase().includes(kw),
  )
})

async function load() {
  loading.value = true
  try {
    const [book, items] = await Promise.all([getAdminEbook(ebookId.value), getAdminChapters(ebookId.value)])
    ebook.value = book
    chapters.value = items
  } catch (error) {
    message.error(error instanceof Error ? error.message : '章节数据加载失败')
  } finally {
    loading.value = false
  }
}

function resetForm() {
  editing.value = null
  form.title = ''
  form.content = ''
  form.sourceNote = ''
}

function snapshotForm() {
  return JSON.stringify({ title: form.title, content: form.content, sourceNote: form.sourceNote })
}

function openCreate() {
  resetForm()
  initialForm.value = snapshotForm()
  drawerOpen.value = true
}

async function openEdit(chapter: ChapterItem) {
  try {
    const detail = await getAdminChapter(ebookId.value, chapter.id)
    editing.value = chapter
    form.title = detail.title
    form.content = detail.content
    form.sourceNote = detail.sourceNote ?? ''
    initialForm.value = snapshotForm()
    drawerOpen.value = true
  } catch (error) {
    message.error(error instanceof Error ? error.message : '章节加载失败')
  }
}

async function save() {
  if (!form.title.trim()) {
    message.warning('请输入章节标题')
    return
  }
  if (!form.content.replace(/<[^>]*>/g, '').replace(/&nbsp;/g, ' ').trim()) {
    message.warning('请输入章节正文')
    return
  }
  saving.value = true
  try {
    const payload = { title: form.title.trim(), content: form.content, sourceNote: form.sourceNote.trim() || undefined }
    if (editing.value) await updateChapter(ebookId.value, editing.value.id, payload)
    else await createChapter(ebookId.value, payload)
    message.success(editing.value ? '章节已更新' : '章节已添加')
    drawerOpen.value = false
    await load()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '章节保存失败')
  } finally {
    saving.value = false
  }
}

async function remove(chapter: ChapterItem) {
  try {
    await deleteChapter(ebookId.value, chapter.id)
    message.success('章节已删除')
    await load()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '章节删除失败')
  }
}

async function move(chapter: ChapterItem, direction: -1 | 1) {
  const index = chapters.value.findIndex((item) => item.id === chapter.id)
  if (index < 0) return
  const target = index + direction
  if (target < 0 || target >= chapters.value.length || ordering.value) return
  const next = [...chapters.value]
  ;[next[index], next[target]] = [next[target], next[index]]
  ordering.value = true
  try {
    chapters.value = await reorderChapters(ebookId.value, next.map((chapter) => chapter.id))
  } catch (error) {
    message.error(error instanceof Error ? error.message : '章节排序失败')
    await load()
  } finally {
    ordering.value = false
  }
}

function closeDrawer() {
  if (saving.value) return
  if (snapshotForm() === initialForm.value) {
    drawerOpen.value = false
    return
  }
  Modal.confirm({
    title: '放弃未保存的章节修改？',
    content: '关闭后，本次未保存的正文和图片引用将丢失。',
    okText: '放弃修改',
    cancelText: '继续编辑',
    okButtonProps: { danger: true },
    onOk: () => { drawerOpen.value = false },
  })
}

onMounted(() => { void load() })
</script>

<template>
  <main class="admin-page page-shell">
    <header class="admin-page-header">
      <div>
        <p class="section-kicker">内容管理 · 线性目录</p>
        <h1>{{ ebook?.title || '章节管理' }}</h1>
        <p>{{ isDraft ? '按目录顺序维护章节。保存后可返回电子书管理页发布。' : '电子书已发布，章节仅可查看；请先撤回电子书后再修改。' }}</p>
      </div>
      <a-space>
        <a-button @click="router.push('/admin/ebooks')">返回电子书管理</a-button>
        <a-button type="primary" :disabled="!isDraft" @click="openCreate">新增章节</a-button>
      </a-space>
    </header>

    <a-alert v-if="ebook && !isDraft" class="management-alert" type="info" show-icon message="已发布电子书的章节内容已锁定" description="为避免前台读到未完成内容，请先返回电子书管理页撤回，再进行新增、编辑、删除或排序。" />

    <a-card :bordered="false" class="management-card" :loading="loading">
      <div class="management-toolbar">
        <a-input-search
          v-model:value="keyword"
          allow-clear
          class="management-search"
          placeholder="搜索章节标题或来源"
        />
      </div>
      <p v-if="!loading && chapters.length === 0" class="management-empty">尚未添加章节。至少添加一篇正文非空章节后，电子书才可发布。</p>
      <p v-else-if="!loading && filteredChapters.length === 0" class="management-empty">未找到匹配的章节，请更换关键词。</p>
      <ol v-else class="chapter-management-list">
        <li v-for="(chapter, index) in filteredChapters" :key="chapter.id" class="chapter-management-item">
          <div class="chapter-number" aria-hidden="true">{{ index + 1 }}</div>
          <div class="chapter-management-copy"><strong>{{ chapter.title }}</strong><span>{{ chapter.sourceNote || '未填写章节来源补充' }}</span></div>
          <a-space class="chapter-management-actions">
            <a-button size="small" :disabled="!isDraft || ordering || index === 0" @click="move(chapter, -1)">上移</a-button>
            <a-button size="small" :disabled="!isDraft || ordering || index === filteredChapters.length - 1" @click="move(chapter, 1)">下移</a-button>
            <a-button size="small" :disabled="!isDraft" @click="openEdit(chapter)">编辑</a-button>
            <a-popconfirm title="确定删除此章节吗？删除后无法恢复。" ok-text="删除" cancel-text="取消" @confirm="remove(chapter)">
              <a-button size="small" danger :disabled="!isDraft">删除</a-button>
            </a-popconfirm>
          </a-space>
        </li>
      </ol>
    </a-card>

    <a-drawer :open="drawerOpen" :title="drawerTitle" :width="760" :mask-closable="!saving" @close="closeDrawer">
      <a-form layout="vertical" @submit.prevent="save">
        <a-form-item label="章节标题" required><a-input v-model:value="form.title" :maxlength="200" @blur="form.title = form.title.trim()" /></a-form-item>
        <a-form-item label="章节正文" required><RichTextEditor v-model="form.content" :disabled="saving" /><p class="form-hint">支持基础排版、列表、链接和正文图片；保存时会由服务端净化不安全 HTML。</p></a-form-item>
        <a-form-item label="章节来源补充"><a-textarea v-model:value="form.sourceNote" :maxlength="1000" :rows="3" show-count placeholder="该章节使用不同来源资料时填写。" /></a-form-item>
        <a-space><a-button type="primary" :loading="saving" @click="save">保存章节</a-button><a-button :disabled="saving" @click="closeDrawer">取消</a-button></a-space>
      </a-form>
    </a-drawer>
  </main>
</template>
