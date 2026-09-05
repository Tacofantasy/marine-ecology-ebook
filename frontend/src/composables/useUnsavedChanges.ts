import { onBeforeUnmount, onMounted } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router'
import { Modal } from 'ant-design-vue'

export function useUnsavedChanges(isDirty: () => boolean, isSaving: () => boolean) {
  function confirmDiscard(): boolean | Promise<boolean> {
    if (isSaving()) return false
    if (!isDirty()) return true
    return new Promise((resolve) => {
      Modal.confirm({
        title: '放弃未保存的修改？',
        content: '本次未保存的文字和图片引用将丢失。',
        okText: '放弃修改', cancelText: '继续编辑',
        okButtonProps: { danger: true },
        onOk: () => resolve(true), onCancel: () => resolve(false),
      })
    })
  }

  function beforeUnload(event: BeforeUnloadEvent) {
    if (!isDirty() && !isSaving()) return
    event.preventDefault()
    event.returnValue = ''
  }

  onBeforeRouteLeave(confirmDiscard)
  onBeforeRouteUpdate(confirmDiscard)
  onMounted(() => window.addEventListener('beforeunload', beforeUnload))
  onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))
  return confirmDiscard
}
