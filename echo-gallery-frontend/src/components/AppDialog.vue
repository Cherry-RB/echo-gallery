<script setup lang="ts">
defineOptions({ inheritAttrs: false })

type DialogPattern = 'confirmation' | 'compact-form' | 'form' | 'large-form' | 'picker' | 'workspace'

withDefaults(defineProps<{
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
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  opened: []
  closed: []
}>()
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
    :close-on-click-modal="closeOnClickModal"
    :close-on-press-escape="closeOnPressEscape"
    :before-close="beforeClose"
    align-center
    @update:model-value="emit('update:modelValue', $event)"
    @opened="emit('opened')"
    @closed="emit('closed')"
  >
    <slot />
    <template v-if="$slots.footer" #footer><slot name="footer" /></template>
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
