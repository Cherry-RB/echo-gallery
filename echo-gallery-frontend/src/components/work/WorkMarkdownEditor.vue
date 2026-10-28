<script setup lang="ts">
import { computed, defineAsyncComponent, nextTick, onBeforeUnmount, onMounted, reactive, ref, shallowRef, watch } from 'vue'
import { Editor, commandsCtx, defaultValueCtx, editorViewCtx, editorViewOptionsCtx, rootCtx, parserCtx, serializerCtx, type CmdKey } from '@milkdown/kit/core'
import { commonmark, wrapInHeadingCommand, turnIntoTextCommand, toggleStrongCommand, toggleEmphasisCommand, toggleInlineCodeCommand, wrapInBulletListCommand, wrapInOrderedListCommand, wrapInBlockquoteCommand, createCodeBlockCommand, insertHrCommand, sinkListItemCommand, liftListItemCommand } from '@milkdown/kit/preset/commonmark'
import { gfm, toggleStrikethroughCommand, insertTableCommand, addRowAfterCommand, addColAfterCommand } from '@milkdown/kit/preset/gfm'
import { history, undoCommand, redoCommand } from '@milkdown/kit/plugin/history'
import { clipboard } from '@milkdown/kit/plugin/clipboard'
import { indent } from '@milkdown/kit/plugin/indent'
import { $prose, insert } from '@milkdown/kit/utils'
import type { Node } from '@milkdown/kit/prose/model'
import { EditorState, Plugin, TextSelection } from '@milkdown/kit/prose/state'
import type { EditorView } from '@milkdown/kit/prose/view'
import { isInTable } from '@milkdown/kit/prose/tables'
import '@milkdown/kit/prose/view/style/prosemirror.css'
import { RefreshLeft, RefreshRight } from '@element-plus/icons-vue'
import AppDialog from '../AppDialog.vue'
import { renderWork } from '../../utils/workMarkdown'
import { compatibleWorkMarkdown, workMatches, workNavigationKey, workNavigationPlugin, workOutline } from '../../utils/workEditorNavigation'
import { useWorkDialogGuard } from '../../composables/useWorkDialogGuard'

const WorkMarkdownSource = defineAsyncComponent(() => import('./WorkMarkdownSource.vue'))
const props = defineProps<{ modelValue: string; mode: 'write' | 'split' | 'read'; readonly?: boolean; saveStatus?: string }>()
const emit = defineEmits<{ 'update:modelValue': [string] }>()
const host = ref<HTMLElement>(), composer = ref<HTMLElement>(), shell = ref<InstanceType<typeof AppDialog>>()
const view = shallowRef<EditorView>(), revision = ref(0), ready = ref(false), source = ref(false), sourceReason = ref('')
const searchOpen = ref(false), outlineOpen = ref(false), query = ref(''), matchIndex = ref(-1), blockStyle = ref('0')
const linkKind = ref<'link' | 'image' | ''>(''), linkError = ref(''), linkForm = reactive({ label: '', url: '' })
let editor: Editor | undefined, disposed = false, replacing = false, original = props.modelValue, latest = props.modelValue
let originalDoc: Node | undefined
let selection = { from: 0, to: 0 }
const html = computed(() => renderWork(props.modelValue))
const headings = computed(() => { void revision.value; return view.value ? workOutline(view.value.state.doc) : [] })
const matches = computed(() => { void revision.value; return view.value ? workMatches(view.value.state.doc, query.value) : [] })
const disabled = computed(() => props.readonly || !ready.value)
const tableEditable = computed(() => { void revision.value; return !disabled.value && !!view.value && isInTable(view.value.state) })
useWorkDialogGuard(shell, () => !!linkKind.value)

function publish() {
  const current = view.value
  if (!editor || !ready.value || !current || replacing || source.value || current.composing || props.readonly) return
  const value = current.state.doc.eq(originalDoc!) ? original : editor.action(ctx => ctx.get(serializerCtx)(current.state.doc))
  if (value !== latest) { latest = value; emit('update:modelValue', value) }
}
function refreshState() {
  revision.value++
  blockStyle.value = String(view.value?.state.selection.$from.parent.attrs.level ?? 0)
}
function closeMenus(event?: Event) {
  if (!event || !composer.value?.contains(event.target as HTMLElement)) composer.value?.querySelectorAll('details[open]').forEach(menu => menu.removeAttribute('open'))
}
function hasMark(name: string) {
  void revision.value
  const state = view.value?.state, type = state?.schema.marks[name]
  if (!state || !type) return false
  return state.selection.empty ? !!type.isInSet(state.storedMarks ?? state.selection.$from.marks()) : state.doc.rangeHasMark(state.selection.from, state.selection.to, type)
}
onMounted(async () => {
  document.addEventListener('pointerdown', closeMenus)
  const initial = props.modelValue
  editor = Editor.make().config(ctx => {
    ctx.set(rootCtx, host.value)
    ctx.set(defaultValueCtx, initial)
    ctx.set(editorViewOptionsCtx, {
      editable: () => !props.readonly,
      attributes: { 'aria-label': '作品正文', role: 'textbox', 'aria-multiline': 'true', spellcheck: 'false' },
      handleDOMEvents: { compositionend: () => { queueMicrotask(publish); return false } },
      handleClick: (_view, _pos, event) => {
        if ((event.target as HTMLElement).closest('a')) { event.preventDefault(); return true }
        return false
      },
    })
  }).use(commonmark).use(gfm).use(history).use(clipboard).use(indent)
    .use($prose(() => workNavigationPlugin()))
    .use($prose(() => new Plugin({ view: () => ({ update(current, previous) {
      view.value = current
      refreshState()
      if (!current.state.doc.eq(previous.doc)) publish()
    } }) })))
  try {
    await editor.create()
    if (disposed) { await editor.destroy(); return }
    view.value = editor.action(ctx => ctx.get(editorViewCtx))
    originalDoc = view.value.state.doc; original = initial; latest = initial
    const serialized = editor.action(ctx => ctx.get(serializerCtx)(originalDoc!))
    if (!compatibleWorkMarkdown(original, serialized)) {
      source.value = true; sourceReason.value = '這份內容包含需要保留原樣的格式，目前使用原始碼編輯。'
    }
    ready.value = true
    view.value.dispatch(view.value.state.tr.setMeta(workNavigationKey, { query: '' }))
    refreshState(); replaceContent(props.modelValue)
  } catch {
    void editor.destroy().catch(() => undefined)
    if (!disposed) { source.value = true; sourceReason.value = '可視化編輯器載入失敗，可繼續使用原始碼編輯。' }
  }
})
function replaceContent(value: string) {
  if (!editor || !view.value || !ready.value || value === latest) return
  const doc = editor.action(ctx => ctx.get(parserCtx)(value))
  if (!doc) return
  replacing = true
  const serialized = editor.action(ctx => ctx.get(serializerCtx)(doc))
  sourceReason.value = compatibleWorkMarkdown(value, serialized) ? '' : '這份內容包含需要保留原樣的格式，目前使用原始碼編輯。'
  if (sourceReason.value) source.value = true
  original = value; latest = value; originalDoc = doc
  // 外部復原／重新載入建立新的歷史基準；一般編輯回傳不會走此路徑。
  view.value.updateState(EditorState.create({ doc, plugins: view.value.state.plugins }))
  view.value.dispatch(view.value.state.tr.setMeta(workNavigationKey, { query: query.value }))
  replacing = false; refreshState()
}
watch(() => props.modelValue, replaceContent)
watch(() => props.readonly, () => view.value?.setProps({ editable: () => !props.readonly }))
watch(linkKind, value => { if (!value) void nextTick(() => view.value?.focus()) })
onBeforeUnmount(() => { publish(); disposed = true; document.removeEventListener('pointerdown', closeMenus); if (ready.value) void editor?.destroy() })

function command<T>(key: CmdKey<T>, payload?: T) {
  if (!editor || disabled.value || view.value?.composing) return
  editor.action(ctx => ctx.get(commandsCtx).call(key, payload))
  composer.value?.querySelectorAll('details[open]').forEach(menu => menu.removeAttribute('open'))
  view.value?.focus()
}
function chooseStyle(event: Event) {
  const level = Number((event.target as HTMLSelectElement).value)
  if (level) command(wrapInHeadingCommand.key, level)
  else command(turnIntoTextCommand.key)
}
function taskList() {
  if (!view.value || disabled.value || view.value.composing) return
  const inList = () => {
    const { $from } = view.value!.state.selection
    return Array.from({ length: $from.depth }, (_, i) => $from.node(i + 1).type.name).includes('list_item')
  }
  if (!inList()) command(wrapInBulletListCommand.key)
  const current = view.value, transaction = current.state.tr
  current.state.doc.nodesBetween(current.state.selection.from, current.state.selection.to, (node, pos) => {
    if (node.type.name === 'list_item') transaction.setNodeMarkup(pos, undefined, { ...node.attrs, checked: node.attrs.checked === null ? false : null })
  })
  for (let depth = current.state.selection.$from.depth; depth > 0; depth--) {
    const node = current.state.selection.$from.node(depth)
    if (node.type.name === 'list_item') {
      transaction.setNodeMarkup(current.state.selection.$from.before(depth), undefined, { ...node.attrs, checked: node.attrs.checked === null ? false : null }); break
    }
  }
  current.dispatch(transaction); current.focus()
}
function openLink(kind: 'link' | 'image') {
  if (!view.value || disabled.value || view.value.composing) return
  selection = { from: view.value.state.selection.from, to: view.value.state.selection.to }
  linkForm.label = view.value.state.doc.textBetween(selection.from, selection.to); linkForm.url = ''
  linkError.value = ''; linkKind.value = kind
}
function insertLink() {
  if (!editor || !view.value || disabled.value) return
  let url = linkForm.url.trim()
  try { const parsed = new URL(url); if (!['http:', 'https:'].includes(parsed.protocol)) throw new Error(); url = parsed.href }
  catch { linkError.value = '請輸入有效的 HTTP(S) 網址。'; return }
  const label = linkForm.label.replace(/[\[\]\\]/g, '\\$&') || url
  view.value.dispatch(view.value.state.tr.setSelection(TextSelection.create(view.value.state.doc, selection.from, selection.to)))
  editor.action(insert(`${linkKind.value === 'image' ? '!' : ''}[${label}](<${url.replace(/>/g, '%3E')}>)`, true))
  linkKind.value = ''; void nextTick(() => view.value?.focus())
}
function find(direction = 1) {
  if (!view.value || !matches.value.length) return
  matchIndex.value = (matchIndex.value + direction + matches.value.length) % matches.value.length
  const match = matches.value[matchIndex.value]!
  view.value.dispatch(view.value.state.tr.setSelection(TextSelection.create(view.value.state.doc, match.from, match.to)).scrollIntoView())
}
watch(query, () => {
  matchIndex.value = -1
  if (view.value) view.value.dispatch(view.value.state.tr.setMeta(workNavigationKey, { query: query.value }))
  find()
})
function jump(pos: number) {
  if (view.value) { view.value.dispatch(view.value.state.tr.setSelection(TextSelection.near(view.value.state.doc.resolve(pos + 1))).scrollIntoView()); view.value.focus() }
}
function toggleSource() { if (view.value?.composing) return; publish(); source.value = !source.value }
defineExpose({ getEditor: () => view.value, focusEditor: () => { if (source.value) composer.value?.querySelector<HTMLElement>('.cm-content')?.focus(); else view.value?.focus() }, revealSelection: () => { if (view.value && !view.value.composing && !source.value) view.value.dispatch(view.value.state.tr.scrollIntoView()) } })
</script>

<template>
  <div ref="composer" class="markdown-composer" :class="{ 'is-split': mode === 'split', 'is-source': source }">
    <div v-show="mode !== 'read'" class="markdown-writing">
      <div class="markdown-tools" role="toolbar" aria-label="正文格式">
        <template v-if="!source">
          <select :value="blockStyle" :disabled="disabled" aria-label="段落樣式" @change="chooseStyle"><option value="0">內文</option><option v-for="level in 6" :key="level" :value="level">{{ ['大標題', '標題', '小標題', '標題 4', '標題 5', '標題 6'][level - 1] }}</option></select>
          <details class="format-menu"><summary @mousedown.prevent>Aa</summary><div class="format-options">
            <button type="button" :disabled="disabled" :aria-pressed="hasMark('strong')" aria-label="粗體" @mousedown.prevent @click="command(toggleStrongCommand.key)"><strong>粗體</strong></button>
            <button type="button" :disabled="disabled" :aria-pressed="hasMark('emphasis')" aria-label="斜體" @mousedown.prevent @click="command(toggleEmphasisCommand.key)"><em>斜體</em></button>
            <button type="button" :disabled="disabled" :aria-pressed="hasMark('strike_through')" aria-label="刪除線" @mousedown.prevent @click="command(toggleStrikethroughCommand.key)"><s>刪除線</s></button>
            <button type="button" :disabled="disabled" @mousedown.prevent @click="command(toggleInlineCodeCommand.key)">等寬文字</button>
          </div></details>
          <details class="format-menu"><summary @mousedown.prevent>清單</summary><div class="format-options">
            <button type="button" :disabled="disabled" @mousedown.prevent @click="command(wrapInBulletListCommand.key)">項目清單</button><button type="button" :disabled="disabled" @mousedown.prevent @click="command(wrapInOrderedListCommand.key)">編號清單</button><button type="button" :disabled="disabled" @mousedown.prevent @click="taskList">勾選清單</button><button type="button" :disabled="disabled" @mousedown.prevent @click="command(sinkListItemCommand.key)">增加縮排</button><button type="button" :disabled="disabled" @mousedown.prevent @click="command(liftListItemCommand.key)">減少縮排</button>
          </div></details>
          <details class="format-menu"><summary @mousedown.prevent>插入</summary><div class="format-options">
            <button type="button" :disabled="disabled" aria-label="插入連結" @mousedown.prevent @click="openLink('link')">連結</button><button type="button" :disabled="disabled" aria-label="插入圖片網址" @mousedown.prevent @click="openLink('image')">圖片網址</button>
            <button type="button" :disabled="disabled" @mousedown.prevent @click="command(wrapInBlockquoteCommand.key)">引用</button><button type="button" :disabled="disabled" @mousedown.prevent @click="command(createCodeBlockCommand.key)">程式碼區塊</button><button type="button" :disabled="disabled" @mousedown.prevent @click="command(insertHrCommand.key)">分隔線</button>
            <button type="button" :disabled="disabled" @mousedown.prevent @click="command(insertTableCommand.key, { row: 2, col: 2 })">基本表格</button><button type="button" :disabled="!tableEditable" @mousedown.prevent @click="command(addRowAfterCommand.key)">表格新增列</button><button type="button" :disabled="!tableEditable" @mousedown.prevent @click="command(addColAfterCommand.key)">表格新增欄</button>
          </div></details>
          <el-button text :icon="RefreshLeft" :disabled="disabled" aria-label="復原正文編輯" @mousedown.prevent @click="command(undoCommand.key)" /><el-button text :icon="RefreshRight" :disabled="disabled" aria-label="重做正文編輯" @mousedown.prevent @click="command(redoCommand.key)" />
        </template>
        <details class="format-menu utility-menu"><summary @mousedown.prevent>工具</summary><div class="format-options">
          <template v-if="!source"><button type="button" :aria-pressed="searchOpen" @mousedown.prevent @click="searchOpen = !searchOpen; closeMenus()">搜尋</button><button type="button" :aria-pressed="outlineOpen" @mousedown.prevent @click="outlineOpen = !outlineOpen; closeMenus()">大綱</button></template>
          <button type="button" :disabled="!!sourceReason" :aria-pressed="source" @mousedown.prevent @click="toggleSource(); closeMenus()">{{ source ? '返回排版編輯' : '原始碼' }}</button>
        </div></details>
        <span v-if="saveStatus" class="editor-save-state" role="status">{{ saveStatus }}</span>
      </div>
      <p v-if="sourceReason" class="editor-notice" role="status">{{ sourceReason }}</p>
      <div v-if="searchOpen && !source" class="editor-search"><input v-model="query" aria-label="搜尋正文" placeholder="搜尋正文" @keydown.enter.prevent="find($event.shiftKey ? -1 : 1)" /><span aria-live="polite">{{ matches.length ? matchIndex + 1 : 0 }} / {{ matches.length }}{{ matches.length === 1000 ? '+' : '' }}</span><button type="button" @mousedown.prevent @click="find(-1)">上一筆</button><button type="button" @mousedown.prevent @click="find()">下一筆</button></div>
      <nav v-if="outlineOpen && !source" class="editor-outline" aria-label="正文大綱"><p v-if="!headings.length">加入標題後，就能從這裡跳至段落。</p><button type="button" v-for="heading in headings" :key="heading.pos" :style="{ paddingLeft: `${(heading.level - 1) * 12 + 8}px` }" @mousedown.prevent @click="jump(heading.pos)">{{ heading.text }}</button></nav>
      <WorkMarkdownSource v-if="source" :model-value="modelValue" mode="write" :readonly="readonly" @update:model-value="emit('update:modelValue', $event)" />
      <div v-show="!source" ref="host" class="work-editor" />
    </div>
    <section v-if="mode !== 'write'" class="markdown-preview" aria-label="正文閱讀預覽"><div class="markdown-reading" v-html="html" /></section>
    <AppDialog ref="shell" :model-value="!!linkKind" :title="linkKind === 'image' ? '插入圖片網址' : '插入連結'" pattern="compact-form" width="min(560px, calc(100vw - 32px))" :draft="linkForm" @update:model-value="linkKind = ''">
      <el-form label-position="top" @submit.prevent="insertLink"><el-form-item :label="linkKind === 'image' ? '圖片說明（選填）' : '連結文字（選填）'"><el-input v-model="linkForm.label" /></el-form-item><el-form-item label="網址" required :error="linkError"><el-input v-model="linkForm.url" placeholder="https://" @input="linkError = ''" /></el-form-item></el-form>
      <template #footer="{ requestClose }"><el-button @click="requestClose">取消</el-button><el-button type="primary" @click="insertLink">插入</el-button></template>
    </AppDialog>
  </div>
</template>

<style scoped>
.markdown-composer { min-width: 0; background: var(--el-bg-color); }
.markdown-writing { min-width: 0; display: flex; flex-direction: column; height: var(--work-editor-height, 65dvh); min-height: 220px; }
.markdown-tools { position: relative; z-index: 2; display: flex; flex-wrap: wrap; align-items: center; flex: 0 0 auto; gap: var(--space-2xs); padding: var(--space-xs) var(--space-sm); border-block: 1px solid var(--el-border-color-light); background: var(--surface-subtle); }
.markdown-tools > * { flex-shrink: 0; }
.markdown-tools > .el-button { margin: 0; padding-inline: var(--space-xs); }
.markdown-tools button, .markdown-tools select, summary, .editor-search button { min-height: 32px; margin: 0; border: 0; border-radius: var(--radius-sm); padding: 4px 8px; background: transparent; color: var(--el-text-color-primary); font: inherit; font-size: var(--type-secondary); cursor: pointer; }
.markdown-tools button:disabled, .markdown-tools select:disabled { color: var(--el-text-color-placeholder); cursor: default; }
.markdown-tools button:hover, summary:hover { background: var(--surface-summary); }
.markdown-tools button[aria-pressed="true"] { background: var(--el-color-primary-light-9); color: var(--el-color-primary); }
.editor-save-state { margin-left: auto; color: var(--el-text-color-secondary); font-size: var(--type-metadata); white-space: nowrap; }
.format-menu { position: relative; }
summary { list-style: none; display: flex; align-items: center; }
summary::-webkit-details-marker { display: none; }
.format-options { position: absolute; left: 0; top: 100%; display: grid; min-width: 140px; max-height: 40dvh; overflow-y: auto; padding: var(--space-xs); background: var(--el-bg-color); border: 1px solid var(--el-border-color); border-radius: var(--radius-md); box-shadow: var(--el-box-shadow-light); }
.format-options button { display: flex; align-items: center; gap: var(--space-xs); text-align: left; }
.utility-menu .format-options { left: auto; right: 0; }
.work-editor { min-height: 0; flex: 1; overflow-y: auto; overscroll-behavior: contain; overflow-wrap: anywhere; scroll-padding-block: 32px; }
.work-editor :deep(.ProseMirror) { min-height: 100%; outline: none; padding: var(--space-md); font-size: var(--type-body); line-height: var(--leading-body); color: var(--el-text-color-primary); }
.work-editor :deep(.ProseMirror > :first-child) { margin-top: 0; }
.work-editor :deep(p) { margin-block: 0 var(--space-sm); }
.work-editor :deep(h1), .work-editor :deep(h2), .work-editor :deep(h3), .work-editor :deep(h4), .work-editor :deep(h5), .work-editor :deep(h6) { position: relative; line-height: 1.4; margin-block: var(--space-lg) var(--space-sm); }
.work-editor :deep(h1) { font-size: 1.8em; }.work-editor :deep(h2) { font-size: 1.5em; }.work-editor :deep(h3) { font-size: 1.25em; }
.work-editor :deep(img), .markdown-reading :deep(img) { max-width: 100%; }
.work-editor :deep(pre), .markdown-reading :deep(pre) { white-space: pre-wrap; overflow-wrap: anywhere; padding: var(--space-sm); background: var(--surface-subtle); border-radius: var(--radius-sm); }
.work-editor :deep(blockquote), .markdown-reading :deep(blockquote) { margin-inline: 0; padding-left: var(--space-md); border-left: 3px solid var(--el-border-color); }
.work-editor :deep(a), .markdown-reading :deep(a) { color: var(--el-color-primary); }
.work-editor :deep(table), .markdown-reading :deep(table) { width: 100%; border-collapse: collapse; table-layout: fixed; }
.work-editor :deep(td), .work-editor :deep(th), .markdown-reading :deep(td), .markdown-reading :deep(th) { border: 1px solid var(--el-border-color); padding: var(--space-xs); min-width: 0; overflow-wrap: anywhere; }
.work-editor :deep(.selectedCell) { background: var(--el-color-primary-light-9); }
.work-editor :deep(li[data-item-type="task"]) { list-style: none; position: relative; }
.work-editor :deep(.work-task-check) { position: absolute; left: -26px; top: 0; background: transparent; color: var(--el-color-primary); border: 0; font: inherit; padding: 0 3px; cursor: pointer; }
.work-editor :deep(.work-heading-toggle) { border: 0; background: var(--surface-subtle); color: var(--el-text-color-secondary); cursor: pointer; font: inherit; font-size: 14px; padding: 0 4px; margin-right: 4px; vertical-align: middle; }
.work-editor :deep(.work-folded-block) { display: none; }
.work-editor :deep(.work-search-match) { background: var(--el-color-warning-light-7); }
.editor-search { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-xs); padding: var(--space-xs) var(--space-md); background: var(--surface-subtle); font-size: var(--type-secondary); }
.editor-search input { min-width: 100px; flex: 1; border: 1px solid var(--el-border-color); border-radius: var(--radius-sm); padding: 6px; color: inherit; background: var(--el-bg-color); font: inherit; }
.editor-outline { display: grid; overflow-y: auto; max-height: 22dvh; padding: var(--space-xs); border-bottom: 1px solid var(--el-border-color); }
.editor-outline button { background: transparent; border: 0; text-align: left; color: var(--el-color-primary); padding-block: 6px; font: inherit; font-size: var(--type-secondary); overflow-wrap: anywhere; cursor: pointer; }
.editor-notice { padding: var(--space-sm) var(--space-md); margin: 0; color: var(--el-text-color-secondary); font-size: var(--type-secondary); }
.markdown-preview { min-width: 0; max-height: var(--work-editor-height, 65dvh); overflow-y: auto; padding: var(--space-md); }
.markdown-reading { max-width: var(--reading-max-width); margin-inline: auto; overflow-wrap: anywhere; font-size: var(--type-body); line-height: var(--leading-body); }
.is-split { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); }
.is-split .markdown-preview { border-left: 1px solid var(--el-border-color-light); }
.is-source :deep(.markdown-composer) { flex: 1; min-height: 0; overflow-y: auto; border: 0; border-radius: 0; }
@media (min-width: 761px) { .work-editor :deep(.ProseMirror), .markdown-preview { padding: var(--space-lg); } }
@media (max-width: 760px) { .is-split { grid-template-columns: minmax(0, 1fr); } .is-split .markdown-preview { border-left: 0; border-top: 1px solid var(--el-border-color-light); } .markdown-tools { padding-inline: var(--space-xs); } }
@media (max-width: 760px) { .work-editor :deep(.ProseMirror), .editor-search input { font-size: 16px; } }
@media (max-width: 760px) {
  .format-menu { position: static; }
  .format-options, .utility-menu .format-options { left: var(--space-xs); right: var(--space-xs); min-width: 0; }
}
</style>
