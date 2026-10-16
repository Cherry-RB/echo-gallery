<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
defineOptions({ inheritAttrs: false })

type DialogPattern = 'confirmation' | 'compact-form' | 'form' | 'large-form' | 'picker' | 'workspace'

const props = withDefaults(defineProps<{
  modelValue: boolean
  title: string
  width?: string
  pattern?: DialogPattern
  mode?: 'form' | 'workspace'
  scrollBody?: boolean
  destroyOnClose?: boolean
  appendToBody?: boolean
  closeOnClickModal?: boolean
  closeOnPressEscape?: boolean
  beforeClose?: (done: () => void) => void
  busy?: boolean
  draft?: unknown
  draftReady?: boolean
}>(), {
  width: 'min(680px, calc(100vw - 32px))',
  pattern: 'form',
  mode: 'form',
  scrollBody: false,
  destroyOnClose: true,
  appendToBody: true,
  closeOnClickModal: true,
  closeOnPressEscape: true,
  beforeClose: undefined,
  busy: false,
  draftReady: true,
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  opened: []
  closed: []
}>()

const initialDraft = ref<string>()
const serializeDraft = () => JSON.stringify(props.draft)
let generation = 0
onBeforeUnmount(() => { generation++ })
watch([() => props.modelValue, () => props.draftReady], async ([visible, ready]) => {
  const current = ++generation
  initialDraft.value = undefined
  if (!visible || !ready) return
  // 等待呼叫端完成該次開啟的欄位初始化，再建立比較基準。
  await nextTick()
  if (generation === current && props.modelValue) initialDraft.value = serializeDraft()
}, { immediate: true, flush: 'post' })
const hasDraft = computed(() => props.draft !== undefined)
let confirming = false
const confirmClose = async (done: () => void) => {
  if (props.busy || confirming) return
  const current = generation
  if (initialDraft.value !== undefined && serializeDraft() !== initialDraft.value) {
    confirming = true
    try {
      await ElMessageBox.confirm('尚有未儲存內容，確定要放棄嗎？', '放棄變更', {
        confirmButtonText: '放棄內容', cancelButtonText: '繼續編輯', type: 'warning',
      })
    } catch { return } finally { confirming = false }
  }
  if (!props.busy && props.modelValue && generation === current) done()
}
const beforeClose = (done: () => void) => {
  if (props.busy || confirming) return
  if (props.beforeClose) props.beforeClose(() => { void confirmClose(done) })
  else void confirmClose(done)
}
const requestClose = () => beforeClose(() => emit('update:modelValue', false))
defineExpose({ requestClose })
</script>

<template>
  <el-dialog
    v-bind="$attrs"
    :model-value="modelValue"
    :title="title"
    :width="width"
    :class="['app-dialog', `app-dialog--${mode}`, `app-dialog--${pattern}`, { 'app-dialog--scroll-body': scrollBody }]"
    :destroy-on-close="destroyOnClose"
    :append-to-body="appendToBody"
    :close-on-click-modal="!busy && !hasDraft && closeOnClickModal"
    :close-on-press-escape="!busy && closeOnPressEscape"
    :show-close="!busy && $attrs['show-close'] !== false"
    :before-close="beforeClose"
    align-center
    @update:model-value="emit('update:modelValue', $event)"
    @opened="emit('opened')"
    @closed="emit('closed')"
  >
    <slot />
    <template v-if="$slots.footer" #footer><slot name="footer" :request-close="requestClose" /></template>
  </el-dialog>
</template>

<style scoped>
:global(.app-dialog.el-dialog) {
  display: flex;
  max-height: calc(100dvh - 32px);
  margin: auto;
  flex-direction: column;
}

:global(.app-dialog .el-dialog__header),
:global(.app-dialog .el-dialog__footer) {
  flex: 0 0 auto;
}

:global(.app-dialog .el-dialog__body) {
  min-height: 0;
}

:global(.app-dialog--scroll-body .el-dialog__body) {
  overflow-y: auto;
}

:global(.app-dialog .el-dialog__footer) {
  padding-top: 14px;
  border-top: 1px solid var(--el-border-color-lighter);
}

@media (max-width: 760px) {
  :global(.app-dialog.el-dialog) {
    width: calc(100vw - 24px) !important;
    max-height: calc(100dvh - 24px);
    margin: 12px auto !important;
  }
}
</style>
