<script setup lang="ts">
import { EditorContent, useEditor } from '@tiptap/vue-3'
import Image from '@tiptap/extension-image'
import Link from '@tiptap/extension-link'
import StarterKit from '@tiptap/starter-kit'
import { onBeforeUnmount, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { uploadContentImage } from './chapter-api'

const props = defineProps<{ modelValue: string; disabled?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const uploading = ref(false)
const imageInput = ref<HTMLInputElement | null>(null)

const editor = useEditor({
  content: props.modelValue,
  editable: !props.disabled,
  extensions: [
    StarterKit.configure({ link: false }),
    Image.configure({ inline: false, allowBase64: false }),
    Link.configure({ openOnClick: false, autolink: true, defaultProtocol: 'https' }),
  ],
  editorProps: { attributes: { class: 'rich-text-content', 'aria-label': '章节正文编辑器' } },
  onUpdate: ({ editor: currentEditor }) => emit('update:modelValue', currentEditor.getHTML()),
})

watch(() => props.modelValue, (value) => {
  if (editor.value && editor.value.getHTML() !== value) editor.value.commands.setContent(value, { emitUpdate: false })
})

watch(() => props.disabled, (disabled) => editor.value?.setEditable(!disabled))

function toggle(format: 'bold' | 'italic' | 'bulletList' | 'orderedList') {
  const chain = editor.value?.chain().focus()
  if (!chain) return
  if (format === 'bold') chain.toggleBold().run()
  if (format === 'italic') chain.toggleItalic().run()
  if (format === 'bulletList') chain.toggleBulletList().run()
  if (format === 'orderedList') chain.toggleOrderedList().run()
}

function toggleHeading(level: 2 | 3) {
  editor.value?.chain().focus().toggleHeading({ level }).run()
}

function setLink() {
  const editorInstance = editor.value
  if (!editorInstance) return
  const { empty } = editorInstance.state.selection
  const url = window.prompt(empty ? '请输入链接地址，将作为链接文字插入：' : '请输入链接地址（仅支持 http 或 https）：')
  if (!url) return
  if (!/^https?:\/\//i.test(url)) {
    message.warning('链接仅支持 http 或 https 地址')
    return
  }
  if (empty) {
    // 空选区时 setLink 只会设置暂存标记（界面上看不到），改为把 URL 本身作为链接文字直接插入
    editorInstance.chain().focus().insertContent({
      type: 'text', text: url, marks: [{ type: 'link', attrs: { href: url } }],
    }).run()
    return
  }
  editorInstance.chain().focus().extendMarkRange('link').setLink({ href: url }).run()
}

async function uploadImage(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
    message.warning('正文图片仅支持 JPEG、PNG 或 WebP 图片')
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    message.warning('正文图片不能超过 5 MB')
    return
  }
  uploading.value = true
  try {
    const url = await uploadContentImage(file)
    editor.value?.chain().focus().setImage({ src: url, alt: file.name }).run()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '正文图片上传失败')
  } finally {
    uploading.value = false
    if (imageInput.value) imageInput.value.value = ''
  }
}

onBeforeUnmount(() => editor.value?.destroy())
</script>

<template>
  <section class="rich-text-editor" :aria-busy="uploading">
    <div class="rich-text-toolbar" role="toolbar" aria-label="正文格式工具">
      <a-button size="small" :disabled="disabled" :type="editor?.isActive('bold') ? 'primary' : 'default'" @click="toggle('bold')">加粗</a-button>
      <a-button size="small" :disabled="disabled" :type="editor?.isActive('italic') ? 'primary' : 'default'" @click="toggle('italic')">斜体</a-button>
      <a-button size="small" :disabled="disabled" :type="editor?.isActive('heading', { level: 2 }) ? 'primary' : 'default'" @click="toggleHeading(2)">二级标题</a-button>
      <a-button size="small" :disabled="disabled" :type="editor?.isActive('heading', { level: 3 }) ? 'primary' : 'default'" @click="toggleHeading(3)">三级标题</a-button>
      <a-button size="small" :disabled="disabled" :type="editor?.isActive('bulletList') ? 'primary' : 'default'" @click="toggle('bulletList')">无序列表</a-button>
      <a-button size="small" :disabled="disabled" :type="editor?.isActive('orderedList') ? 'primary' : 'default'" @click="toggle('orderedList')">有序列表</a-button>
      <a-button size="small" :disabled="disabled" @click="setLink">插入链接</a-button>
      <label class="rich-text-upload">
        <input ref="imageInput" type="file" accept="image/jpeg,image/png,image/webp" :disabled="disabled || uploading" @change="uploadImage" />
        <span>{{ uploading ? '图片上传中…' : '插入图片' }}</span>
      </label>
    </div>
    <EditorContent :editor="editor" />
  </section>
</template>
