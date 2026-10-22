<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { minimalSetup } from 'codemirror'
import { Compartment, EditorSelection, EditorState, Transaction } from '@codemirror/state'
import { EditorView, keymap } from '@codemirror/view'
import { indentWithTab, redo, undo } from '@codemirror/commands'
import { markdown } from '@codemirror/lang-markdown'
import { Link, Picture, RefreshLeft, RefreshRight } from '@element-plus/icons-vue'
import AppDialog from '../AppDialog.vue'
import { renderWork } from '../../utils/workMarkdown'
import { useWorkDialogGuard } from '../../composables/useWorkDialogGuard'

const props = defineProps<{ modelValue: string; mode: 'write' | 'split' | 'read'; readonly?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [string] }>()
const host = ref<HTMLElement>(), shell = ref<InstanceType<typeof AppDialog>>()
const linkKind = ref<'link' | 'image' | ''>(''), linkError = ref('')
const linkForm = reactive({ label: '', url: '' })
const editable = new Compartment()
let view: EditorView | undefined
let selection = { from: 0, to: 0 }
useWorkDialogGuard(shell, () => !!linkKind.value)
const html = computed(() => renderWork(props.modelValue))
onMounted(() => {
  view = new EditorView({ parent: host.value, state: EditorState.create({
    doc: props.modelValue,
    extensions: [minimalSetup, markdown(), EditorView.lineWrapping, keymap.of([indentWithTab]),
      editable.of([EditorState.readOnly.of(!!props.readonly), EditorView.editable.of(!props.readonly)]),
      EditorView.contentAttributes.of({ 'aria-label': '作品 Markdown 正文', spellcheck: 'false' }),
      EditorView.updateListener.of(update => {
        if (update.docChanged && !update.view.composing) emit('update:modelValue', update.state.doc.toString())
      }),
      EditorView.domEventHandlers({ compositionend: () => {
        queueMicrotask(() => { if (view) emit('update:modelValue', view.state.doc.toString()) })
      } }),
    ],
  }) })
})
watch(() => props.modelValue, value => {
  if (view && value !== view.state.doc.toString()) view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: value }, annotations: Transaction.addToHistory.of(false) })
})
watch(() => props.readonly, value => view?.dispatch({ effects: editable.reconfigure([EditorState.readOnly.of(!!value), EditorView.editable.of(!value)]) }))
watch(() => props.mode, async () => { await nextTick(); view?.requestMeasure() })
onBeforeUnmount(() => { view?.destroy(); view = undefined })
function wrap(before: string, after = '') {
  if (!view || props.readonly || view.composing) return
  const { from, to } = view.state.selection.main
  const selected = view.state.sliceDoc(from, to)
  view.dispatch({ changes: { from, to, insert: before + selected + after }, selection: EditorSelection.range(from + before.length, to + before.length), userEvent: 'input' })
  view.focus()
}
function prefix(text: string) {
  if (!view || props.readonly || view.composing) return
  const { from, to } = view.state.selection.main
  const start = view.state.doc.lineAt(from).from
  const end = view.state.doc.lineAt(to > from && view.state.doc.lineAt(to).from === to ? to - 1 : to).to
  const insert = view.state.sliceDoc(start, end).split('\n').map(line => text + line).join('\n')
  view.dispatch({ changes: { from: start, to: end, insert }, userEvent: 'input' }); view.focus()
}
function history(command: typeof undo) { if (view && !props.readonly && !view.composing) { command(view); view.focus() } }
function openLink(kind: 'link' | 'image') {
  if (!view || props.readonly || view.composing) return
  selection = { from: view.state.selection.main.from, to: view.state.selection.main.to }
  linkForm.label = view.state.sliceDoc(selection.from, selection.to); linkForm.url = ''
  linkError.value = ''; linkKind.value = kind
}
function insertLink() {
  if (!view || props.readonly) return
  const url = linkForm.url.trim()
  try { if (!['http:', 'https:'].includes(new URL(url).protocol)) throw new Error() }
  catch { linkError.value = '請輸入有效的 HTTP(S) 網址。'; return }
  const label = linkForm.label.replace(/[\[\]\\]/g, '\\$&')
  const text = `${linkKind.value === 'image' ? '!' : ''}[${label}](<${url.replace(/>/g, '%3E')}>)`
  view.dispatch({ changes: { ...selection, insert: text }, selection: { anchor: selection.from + text.length }, userEvent: 'input' })
  linkKind.value = ''; nextTick(() => view?.focus())
}
</script>

<template>
  <div class="markdown-composer" :class="{ 'is-split': mode === 'split' }">
    <div v-show="mode !== 'read'" class="markdown-writing">
      <div class="markdown-tools" role="toolbar" aria-label="正文格式">
        <div class="format-group">
          <el-tooltip content="標題"><el-button text :disabled="readonly" aria-label="插入標題" @mousedown.prevent @click="prefix('## ')">H</el-button></el-tooltip>
          <el-tooltip content="粗體"><el-button text :disabled="readonly" aria-label="粗體" @mousedown.prevent @click="wrap('**', '**')"><strong>B</strong></el-button></el-tooltip>
          <el-tooltip content="斜體"><el-button text :disabled="readonly" aria-label="斜體" @mousedown.prevent @click="wrap('*', '*')"><em>I</em></el-button></el-tooltip>
        </div>
        <div class="format-group">
          <el-button text :disabled="readonly" @mousedown.prevent @click="prefix('- ')">列表</el-button>
          <el-button text :disabled="readonly" @mousedown.prevent @click="prefix('> ')">引用</el-button>
          <el-button text :disabled="readonly" @mousedown.prevent @click="wrap('```\n', '\n```')">程式碼</el-button>
        </div>
        <div class="format-group">
          <el-tooltip content="插入連結"><el-button text :icon="Link" :disabled="readonly" aria-label="插入連結" @mousedown.prevent @click="openLink('link')" /></el-tooltip>
          <el-tooltip content="插入圖片網址"><el-button text :icon="Picture" :disabled="readonly" aria-label="插入圖片網址" @mousedown.prevent @click="openLink('image')" /></el-tooltip>
        </div>
        <div class="format-group">
          <el-tooltip content="復原"><el-button text :icon="RefreshLeft" :disabled="readonly" aria-label="復原正文編輯" @mousedown.prevent @click="history(undo)" /></el-tooltip>
          <el-tooltip content="重做"><el-button text :icon="RefreshRight" :disabled="readonly" aria-label="重做正文編輯" @mousedown.prevent @click="history(redo)" /></el-tooltip>
        </div>
      </div>
      <div ref="host" class="work-editor" />
    </div>
    <section v-if="mode !== 'write'" class="markdown-preview" aria-label="正文閱讀預覽"><div class="markdown-reading" v-html="html" /></section>
    <AppDialog ref="shell" :model-value="!!linkKind" :title="linkKind === 'image' ? '插入圖片網址' : '插入連結'" pattern="compact-form" width="min(560px, calc(100vw - 32px))" :draft="linkForm" @update:model-value="linkKind = ''">
      <el-form label-position="top" @submit.prevent="insertLink">
        <el-form-item :label="linkKind === 'image' ? '圖片說明（選填）' : '連結文字（選填）'"><el-input v-model="linkForm.label" /></el-form-item>
        <el-form-item label="網址" required :error="linkError"><el-input v-model="linkForm.url" placeholder="https://" @input="linkError = ''" /></el-form-item>
      </el-form>
      <template #footer="{ requestClose }"><el-button @click="requestClose">取消</el-button><el-button type="primary" @click="insertLink">插入</el-button></template>
    </AppDialog>
  </div>
</template>

<style scoped>
.markdown-composer { min-width: 0; border: 1px solid var(--el-border-color); border-radius: var(--radius-md); overflow: hidden; background: var(--el-bg-color); }
.markdown-composer:focus-within { border-color: var(--el-color-primary); }
.markdown-writing { min-width: 0; }
.markdown-tools { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-2xs); padding: var(--space-xs); border-bottom: 1px solid var(--el-border-color-light); background: var(--surface-subtle); }
.format-group { display: flex; gap: var(--space-2xs); align-items: center; padding-right: var(--space-xs); border-right: 1px solid var(--el-border-color-light); }
.format-group:last-child { border: 0; padding-right: 0; }
.format-group :deep(.el-button) { margin: 0; padding-inline: var(--space-xs); }
.work-editor :deep(.cm-editor) { min-height: 54dvh; color: var(--el-text-color-primary); background: var(--el-bg-color); font-size: var(--type-body); }
.work-editor :deep(.cm-editor.cm-focused) { outline: none; }
.work-editor :deep(.cm-scroller) { font-family: var(--el-font-family); line-height: var(--leading-body); overflow: auto; }
.work-editor :deep(.cm-content) { padding: var(--space-md); min-height: 54dvh; caret-color: var(--el-text-color-primary); }
.work-editor :deep(.cm-activeLine) { background: var(--surface-subtle); }
.work-editor :deep(.cm-cursor) { border-left-color: var(--el-text-color-primary); }
.work-editor :deep(.cm-selectionBackground), .work-editor :deep(.cm-focused .cm-selectionBackground) { background: var(--el-color-primary-light-7); }
.markdown-preview { min-width: 0; padding: var(--space-md); }
.is-split { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); }
.is-split .markdown-preview { border-left: 1px solid var(--el-border-color-light); }
.markdown-reading { max-width: var(--reading-max-width); margin-inline: auto; overflow-wrap: anywhere; font-size: var(--type-body); line-height: var(--leading-body); min-height: 30dvh; }
.markdown-reading :deep(p + p), .markdown-reading :deep(ul), .markdown-reading :deep(ol), .markdown-reading :deep(pre), .markdown-reading :deep(blockquote) { margin-block: var(--space-md); }
.markdown-reading :deep(img) { max-width: 100%; }
.markdown-reading :deep(pre) { overflow-x: auto; padding: var(--space-sm); background: var(--surface-subtle); }
.markdown-reading :deep(blockquote) { margin-inline: 0; padding-left: var(--space-md); border-left: 3px solid var(--el-border-color); }
.markdown-reading :deep(a) { color: var(--el-color-primary); }
@media (max-width: 760px) { .is-split { grid-template-columns: minmax(0, 1fr); } .is-split .markdown-preview { border-left: 0; border-top: 1px solid var(--el-border-color-light); } }
</style>
