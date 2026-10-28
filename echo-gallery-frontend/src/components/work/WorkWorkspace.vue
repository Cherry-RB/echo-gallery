<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, ArrowLeft, Edit, MoreFilled, Plus } from '@element-plus/icons-vue'
import { formatDate } from '../../utils/formatDate'
import AppDialog from '../AppDialog.vue'
import DetailShell from '../ui/DetailShell.vue'
import SidePanel from '../ui/SidePanel.vue'
import PickerOption from '../ui/PickerOption.vue'
import DetailHeading from '../ui/DetailHeading.vue'
import ExpandableText from '../ExpandableText.vue'
import WorkMarkdownEditor from './WorkMarkdownEditor.vue'
import WorkFormDialog from './WorkFormDialog.vue'
import WorkSourcePicker from './WorkSourcePicker.vue'
import WorkSourceReadingDialog from './WorkSourceReadingDialog.vue'
import TreasureFormDialog from '../treasure/TreasureFormDialog.vue'
import { workApi } from '../../utils/api/workApi'
import { experimentApi } from '../../utils/api/experimentApi'
import ExperimentThreads from '../experiment/ExperimentThreads.vue'
import { treasureApi } from '../../utils/api/treasureApi'
import { useWorkAutosave } from '../../composables/useWorkAutosave'
import { useWorkDialogGuard } from '../../composables/useWorkDialogGuard'
import { workDrafts, type WorkDraft } from '../../utils/workDrafts'
import { downloadWork, plainWork, workDocument } from '../../utils/workMarkdown'
import { workStages, type WorkDetail, type WorkSource, type WorkStage } from '../../types/work'
import type { TreasureContent, TreasureDetail } from '../../types/treasure'
import { today } from '../../utils/treasure'
const props = defineProps<{ initial: WorkDetail }>()
const shareInitiallyOpen = props.initial.work.published || !!props.initial.shareUrl || !!props.initial.shareNote || !!props.initial.sharedOn || !!props.initial.work.treasureId
const detail = ref(props.initial), id = props.initial.work.id, owner = localStorage.getItem('id') || ''
const route = useRoute(), router = useRouter(), client = useQueryClient()
const auto = useWorkAutosave(owner, id, props.initial.body, props.initial.bodyVersion, async (body, v) => {
  const saved = await workApi.body(id, body, v)
  client.setQueryData<WorkDetail>(['works', 'detail', id], cached => cached ? {
    ...cached, body: saved.body, bodyVersion: saved.bodyVersion, work: { ...cached.work, editedAt: saved.editedAt },
  } : undefined)
  return saved
})
const body = auto.body, saving = auto.saving, status = auto.status
const readonly = computed(() => detail.value.work.archived)
const focusMode = ref(false), viewportHeight = ref(window.innerHeight), viewportTop = ref(0)
const editorComponent = ref<InstanceType<typeof WorkMarkdownEditor>>()
const editorPanel = ref<HTMLElement>()
let previousOverflow = ''
const editorViewport = computed(() => ({ '--work-editor-height': `${Math.max(180, viewportHeight.value * .65)}px`, '--work-viewport-height': `${viewportHeight.value}px`, '--work-viewport-top': `${viewportTop.value}px` }))
watch(focusMode, active => {
  if (active) { previousOverflow = document.body.style.overflow; document.body.style.overflow = 'hidden'; void nextTick(() => editorComponent.value?.focusEditor?.()) }
  else document.body.style.overflow = previousOverflow
})
function focusKey(event: KeyboardEvent) {
  if (!focusMode.value || event.defaultPrevented) return
  if (event.key === 'Escape') {
    const menus = editorPanel.value?.querySelectorAll('details[open]')
    if (menus?.length) menus.forEach(menu => menu.removeAttribute('open'))
    else focusMode.value = false
    event.preventDefault(); return
  }
  if (event.key !== 'Tab' || !editorPanel.value) return
  const controls = [...editorPanel.value.querySelectorAll<HTMLElement>('button:not([disabled]), input, select, summary, a[href], [contenteditable="true"], [tabindex="0"]')].filter(element => element.getClientRects().length)
  const first = controls[0], last = controls.at(-1)
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus() }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus() }
}
function updateViewport() {
  viewportHeight.value = window.visualViewport?.height ?? window.innerHeight
  viewportTop.value = window.visualViewport?.offsetTop ?? 0
  if (editorPanel.value?.contains(document.activeElement) && (document.activeElement as HTMLElement)?.isContentEditable) {
    requestAnimationFrame(() => editorComponent.value?.revealSelection())
  }
}
const preview = ref<'write' | 'split' | 'read'>(route.query.mode === 'read' || readonly.value ? 'read' : 'write')
watch(() => route.query.mode, mode => { preview.value = mode === 'read' || readonly.value ? 'read' : 'write' })
const libraryReturn = computed(() => typeof route.query.from === 'string' && /^\/works(?:\?|$)/.test(route.query.from) ? route.query.from : '/works')
function returnToLibrary() {
  if (window.history.state?.back === libraryReturn.value) router.back()
  else void router.push(libraryReturn.value)
}
const internal = computed(() => detail.value.work.editorMode === 'INTERNAL')
const editingInfo = ref(false), picker = ref(false), busy = ref(false), error = ref(''), reading = ref<WorkSource>()
let deleted = false
const dialog = ref<'next' | 'stage' | 'share' | 'treasure' | ''>('')
const manualShell = ref<InstanceType<typeof AppDialog>>()
useWorkDialogGuard(manualShell, () => !!dialog.value)
const form = reactive({ nextStep: '', stage: 'WORKING' as WorkStage, firstCompletedOn: '', published: false, shareUrl: '', sharedOn: '', shareNote: '' })
const recovery = ref<WorkDraft[]>([])
const reloading = ref(false)
const sources = useQuery({ queryKey: ['works', 'sources', id], queryFn: () => workApi.sources(id) })
const explorationThreads = useQuery({ queryKey: ['work-exploration-threads', id], queryFn: () => experimentApi.getWorkThreads(id) })
const openedThread = ref<{ experimentId: number; threadId: number }>()
const treasureMode = ref('new'), treasureKeyword = ref(''), selectedTreasure = ref<number>(), treasurePage = ref(1), treasureForm = ref(false)
const treasures = useQuery({ queryKey: computed(() => ['treasures', 'work-link', treasureKeyword.value, treasurePage.value]), queryFn: () => treasureApi.list({ keyword: treasureKeyword.value, page: treasurePage.value - 1 }), enabled: computed(() => dialog.value === 'treasure') })
const count = computed(() => Array.from(body.value).length)
async function invalidate() { await client.invalidateQueries({ queryKey: ['works'] }); await client.invalidateQueries({ queryKey: ['treasures'] }) }
function pageCommand(value: string) { return value === 'archive' ? archive() : remove() }
async function safeBody() {
  if (auto.saving.value) { error.value = '正文仍在儲存，請稍候再操作。'; return false }
  if (auto.dirty.value) await auto.flush()
  if (auto.dirty.value || auto.conflict.value || auto.saving.value) { error.value = '請先處理尚未儲存的正文。'; return false }
  return true
}
function openDialog(value: typeof dialog.value) {
  const d = detail.value
  Object.assign(form, { nextStep: d.work.nextStep || '', stage: d.work.stage, firstCompletedOn: d.firstCompletedOn || '', published: d.work.published, shareUrl: d.shareUrl || '', sharedOn: d.sharedOn || '', shareNote: d.shareNote || '' })
  error.value = ''; dialog.value = value
}
async function changeStage(value: string) {
  if (readonly.value || busy.value) return
  if (value === 'EDIT_DATE') { openDialog('stage'); return }
  if (!(value in workStages) || value === detail.value.work.stage) return
  busy.value = true; error.value = ''
  try {
    if (!(await safeBody())) return
    detail.value = await workApi.update(id, 'stage', { stage: value, firstCompletedOn: detail.value.firstCompletedOn || (value === 'FIRST_DRAFT' ? today() : null) })
    await invalidate()
  } catch { error.value = '無法變更作品階段，請重試。' } finally { busy.value = false }
}
async function changePublication(value: string) {
  if (readonly.value || busy.value) return
  if (value === 'EDIT') { openDialog('share'); return }
  if (!['PUBLISHED', 'UNPUBLISHED'].includes(value) || (value === 'PUBLISHED') === detail.value.work.published) return
  busy.value = true; error.value = ''
  try {
    if (!(await safeBody())) return
    const current = detail.value
    detail.value = await workApi.update(id, 'share', { published: value === 'PUBLISHED', shareUrl: current.shareUrl, sharedOn: current.sharedOn, shareNote: current.shareNote })
    await invalidate()
  } catch { error.value = '無法變更發表狀態，請重試。' } finally { busy.value = false }
}
async function saveDialog() {
  if (busy.value) return
  if (dialog.value === 'treasure' && treasureMode.value === 'new') { dialog.value = ''; treasureForm.value = true; return }
  busy.value = true; error.value = ''
  try {
    const section = dialog.value === 'next' ? 'next-step' : dialog.value
    const data = dialog.value === 'next' ? { nextStep: form.nextStep } : dialog.value === 'stage' ? { stage: form.stage, firstCompletedOn: form.firstCompletedOn || null } : dialog.value === 'share' ? { published: form.published, shareUrl: form.shareUrl, sharedOn: form.sharedOn || null, shareNote: form.shareNote } : { treasureId: selectedTreasure.value }
    detail.value = await workApi.update(id, section, data); dialog.value = ''; await invalidate()
  } catch { error.value = '無法儲存變更，請檢查內容後重試。' } finally { busy.value = false }
}
async function archive() {
  if (busy.value || !(await safeBody())) return
  try { await ElMessageBox.confirm(readonly.value ? '還原後即可繼續編輯。' : '封存後仍可閱讀、複製與匯出；還原後才能編輯。', readonly.value ? '還原作品' : '封存作品', { confirmButtonText: readonly.value ? '還原' : '封存', cancelButtonText: '取消' }) } catch { return }
  busy.value = true
  try { detail.value = await workApi.update(id, readonly.value ? 'restore' : 'archive'); if (readonly.value) preview.value = 'read'; await invalidate() }
  catch { error.value = '無法變更封存狀態，請重試。' } finally { busy.value = false }
}
async function remove() {
  if (busy.value) return
  if (auto.saving.value) { error.value = '正文仍在儲存，請稍候再刪除。'; return }
  try { await ElMessageBox.confirm('永久刪除此作品、正文與素材關聯？此操作無法復原，卡片、探索紀錄與展品仍會保留。', '永久刪除作品', { type: 'warning', confirmButtonText: '永久刪除', cancelButtonText: '取消' }) } catch { return }
  busy.value = true
  try {
    await auto.backup(); await workApi.delete(id); deleted = true; auto.reset('', 0)
    await workDrafts.clearWork(owner, id).catch(() => ElMessage.warning('作品已刪除，但無法清理此瀏覽器的復原草稿。'))
    await router.push('/works'); await invalidate()
  }
  catch { error.value = '無法刪除作品，請重試。' } finally { busy.value = false }
}
async function unlink() {
  try { await ElMessageBox.confirm('解除後作品與展品各自保留。重新連結時，作品須為可分享。', '解除展品關聯', { confirmButtonText: '解除關聯', cancelButtonText: '取消' }) } catch { return }
  try { detail.value = await workApi.unlink(id); await invalidate() } catch { error.value = '無法解除關聯，請重試。' }
}
async function removeSource(source: WorkSource) {
  try { await ElMessageBox.confirm('只移除此作品的素材關聯，原始內容與正文仍會保留。', '移除參考素材', { confirmButtonText: '移除關聯', cancelButtonText: '取消' }) } catch { return }
  try { await workApi.removeSource(id, source.id!); await sources.refetch(); await invalidate() } catch { error.value = '無法移除素材，請重試。' }
}
async function copy(text: string) { try { await navigator.clipboard.writeText(text); ElMessage.success('已複製') } catch { error.value = '無法存取剪貼簿，請手動複製或下載。' } }
function exportWork(command: string) {
  if (command === 'markdown') void copy(workDocument(detail.value.work.title, body.value))
  else if (command === 'plain') void copy(plainWork(detail.value.work.title, body.value))
  else if (command === 'download') downloadWork(detail.value.work.title, body.value)
}
async function reload() {
  if (auto.saving.value || reloading.value) return
  try { await ElMessageBox.confirm('重新載入伺服器最新版？目前草稿會留在此瀏覽器；建議先複製或下載。', '重新載入作品', { confirmButtonText: '重新載入', cancelButtonText: '取消' }) } catch { return }
  reloading.value = true
  await auto.backup()
  try { detail.value = await workApi.detail(id); auto.reset(detail.value.body, detail.value.bodyVersion); recovery.value = await workDrafts.list(owner, id) }
  catch { error.value = '無法重新載入，草稿仍保留。' }
  finally { reloading.value = false }
}
async function recover(draft: WorkDraft) {
  if (readonly.value || !internal.value) return
  if (auto.saving.value) { error.value = '正文仍在儲存，請稍候再復原草稿。'; return }
  if (draft.version !== auto.version.value) { auto.conflict.value = true; auto.error.value = '復原草稿的版本與伺服器不同。請先複製或下載草稿，再重新載入。'; return }
  if (auto.dirty.value) {
    try { await ElMessageBox.confirm('復原會取代目前尚未儲存的編輯內容。請先複製或下載需要保留的文字。', '復原草稿', { confirmButtonText: '以草稿取代', cancelButtonText: '繼續編輯' }) } catch { return }
  }
  if (auto.saving.value || draft.version !== auto.version.value) { error.value = '保存狀態已改變，請先保留稿件，再重新確認復原草稿。'; return }
  auto.edit(draft.body)
}
async function discard(draft: WorkDraft) {
  try { await ElMessageBox.confirm('刪除此瀏覽器保留的這份復原草稿？此操作無法復原。', '捨棄復原草稿', { confirmButtonText: '捨棄草稿', cancelButtonText: '取消' }) } catch { return }
  await workDrafts.remove(draft.key); recovery.value = recovery.value.filter(d => d.key !== draft.key)
}
const treasurePrefill = computed<Partial<TreasureContent>>(() => ({ title: detail.value.work.title, description: detail.value.work.description || '', artifactUrl: detail.value.shareUrl || (internal.value ? '' : detail.value.externalUrl || ''), acquiredOn: detail.value.firstCompletedOn || today() }))
async function createTreasure(data: TreasureContent): Promise<TreasureDetail> {
  detail.value = await workApi.createTreasure(id, data); await invalidate()
  return treasureApi.detail(detail.value.work.treasureId!)
}
async function leave() {
  if (deleted) return true
  if (treasureForm.value) {
    try { await ElMessageBox.confirm('入藏表單尚未提交。離開會放棄這次填寫的資訊，作品與既有展品仍會保留。', '離開入藏表單', { confirmButtonText: '放棄並離開', cancelButtonText: '繼續填寫' }); treasureForm.value = false }
    catch { return false }
  }
  await auto.backup()
  if (!auto.dirty.value && !auto.saving.value && !auto.conflict.value) return true
  try { await ElMessageBox.confirm('正文尚未同步。請先複製或下載；離開後可回到此瀏覽器尋找復原草稿。', '離開作品', { confirmButtonText: '保留草稿並離開', cancelButtonText: '繼續編輯' }); return true } catch { return false }
}
onBeforeRouteLeave(leave)
onBeforeRouteUpdate(async (to, from) => to.params.id === from.params.id || await leave())
function beforeUnload(e: BeforeUnloadEvent) { if (auto.dirty.value || auto.saving.value || auto.conflict.value) { e.preventDefault(); e.returnValue = '' } }
function visibility() { if (document.visibilityState === 'hidden') void auto.backup() }
onMounted(async () => {
  updateViewport()
  window.addEventListener('resize', updateViewport)
  window.visualViewport?.addEventListener('resize', updateViewport)
  window.visualViewport?.addEventListener('scroll', updateViewport)
  window.addEventListener('beforeunload', beforeUnload); document.addEventListener('visibilitychange', visibility)
  try { recovery.value = await workDrafts.list(owner, id) }
  catch { auto.storageError.value = '無法讀取本機復原草稿，請定期複製或下載。' }
})
onBeforeUnmount(() => {
  if (focusMode.value) document.body.style.overflow = previousOverflow
  window.removeEventListener('resize', updateViewport)
  window.visualViewport?.removeEventListener('resize', updateViewport)
  window.visualViewport?.removeEventListener('scroll', updateViewport)
  void auto.dispose(); window.removeEventListener('beforeunload', beforeUnload); document.removeEventListener('visibilitychange', visibility)
})
</script>
<template>
  <DetailShell class="work-workspace" :class="{ 'is-focus': focusMode }" :style="editorViewport">
    <template #navigation>
      <nav class="app-detail-navigation">
        <div class="work-return">
          <el-button text :icon="ArrowLeft" @click="returnToLibrary">返回創作台</el-button>
          <router-link v-if="detail.work.experimentId" class="work-experiment-link" :to="`/experiments/${detail.work.experimentId}`">實驗 · {{ detail.work.experimentTitle }}</router-link>
          <span class="work-status" title="可在編輯資訊中更改主要編輯位置">{{ internal ? '內建編輯器' : '外部工具' }}</span>
        </div>
        <div class="detail-actions work-actions">
          <el-dropdown class="work-stage-menu" trigger="click" :disabled="readonly || busy" @command="changeStage">
            <button type="button" class="status-trigger" :disabled="readonly || busy" aria-label="變更作品階段">
              <span class="work-status-dot" :class="`work-stage-${detail.work.stage.toLowerCase()}`"></span>
              <span>{{ workStages[detail.work.stage] }}</span>
              <el-icon class="status-trigger-arrow"><ArrowDown /></el-icon>
            </button>
            <template #dropdown><el-dropdown-menu><el-dropdown-item v-for="(label, value) in workStages" :key="value" :command="value" :disabled="value === detail.work.stage">{{ label }}</el-dropdown-item><el-dropdown-item divided command="EDIT_DATE">編輯首次完成日期</el-dropdown-item></el-dropdown-menu></template>
          </el-dropdown>
          <el-dropdown class="work-publication-menu" trigger="click" :disabled="readonly || busy" @command="changePublication">
            <button type="button" class="status-trigger" :disabled="readonly || busy" aria-label="變更發表狀態">
              <span class="work-status-dot" :class="detail.work.published ? 'work-publication-published' : 'work-publication-unpublished'"></span>
              <span>{{ detail.work.published ? '已發表' : '未發表' }}</span>
              <el-icon class="status-trigger-arrow"><ArrowDown /></el-icon>
            </button>
            <template #dropdown><el-dropdown-menu><el-dropdown-item command="UNPUBLISHED" :disabled="!detail.work.published">未發表</el-dropdown-item><el-dropdown-item command="PUBLISHED" :disabled="detail.work.published">已發表</el-dropdown-item><el-dropdown-item divided command="EDIT">編輯分享資訊</el-dropdown-item></el-dropdown-menu></template>
          </el-dropdown>
          <el-button v-if="!readonly" plain type="primary" :icon="Edit" :disabled="busy" @click="safeBody().then(ok => { if (ok) editingInfo = true })">編輯資訊</el-button>
          <el-button v-if="!readonly && detail.work.stage === 'SHAREABLE' && !detail.work.treasureId" type="primary" :disabled="busy" @click="openDialog('treasure')">收入展品</el-button>
          <el-button v-if="readonly" :disabled="busy" @click="archive">還原作品</el-button>
          <el-dropdown class="work-more-menu" trigger="click" :disabled="busy" @command="pageCommand">
            <el-button text circle :icon="MoreFilled" aria-label="更多作品操作" />
            <template #dropdown><el-dropdown-menu><el-dropdown-item v-if="!readonly" command="archive">封存作品</el-dropdown-item><el-dropdown-item command="delete" divided>永久刪除</el-dropdown-item></el-dropdown-menu></template>
          </el-dropdown>
        </div>
      </nav>
    </template>
    <header class="work-heading"><DetailHeading :title="detail.work.title" eyebrow="創作台" /></header>
    <ExpandableText v-if="detail.work.description" :content="detail.work.description" :lines="3" />
    <el-alert v-if="readonly" title="此作品已封存，可以閱讀、複製與匯出；還原後才能編輯。" type="info" :closable="false" />
    <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
    <el-alert v-if="auto.storageError.value" :title="auto.storageError.value" type="warning" :closable="false" />
    <el-alert v-if="auto.error.value" :title="auto.error.value" type="error" :closable="false" role="alert"><div class="work-actions"><el-button @click="copy(workDocument(detail.work.title, body))">複製目前稿件</el-button><el-button @click="downloadWork(detail.work.title, body)">下載目前稿件</el-button><el-button v-if="auto.conflict.value" :disabled="saving" @click="reload">重新載入</el-button><el-button v-else @click="auto.error.value = ''; auto.flush()">重試儲存</el-button></div></el-alert>
    <details v-if="recovery.length" class="work-panel" open><summary>此瀏覽器有 {{ recovery.length }} 份復原草稿</summary><article v-for="d in recovery" :key="d.key"><p>{{ formatDate(new Date(d.savedAt).toISOString()) }} · {{ d.body.length }} 字元</p><div class="work-actions"><el-button v-if="!readonly && internal" @click="recover(d)">復原草稿</el-button><el-button @click="copy(workDocument(detail.work.title, d.body))">複製</el-button><el-button @click="downloadWork(detail.work.title, d.body)">下載</el-button><el-button @click="discard(d)">捨棄草稿</el-button></div></article></details>

    <div class="work-layout"><section ref="editorPanel" class="work-panel body-panel" :class="{ 'body-panel--internal': internal }" :role="focusMode ? 'dialog' : undefined" :aria-modal="focusMode ? true : undefined" :aria-labelledby="focusMode ? `work-body-title-${id}` : undefined" @keydown="focusKey">
      <template v-if="internal">
        <div class="work-section-heading work-editor-heading">
          <div class="work-editor-title"><h2 :id="`work-body-title-${id}`">作品正文</h2><p class="work-status">{{ count.toLocaleString() }} 字元<template v-if="preview === 'read'"> · {{ readonly ? '唯讀' : status }}</template></p></div>
          <el-button class="work-focus-toggle" text :aria-pressed="focusMode" @click="focusMode = !focusMode">{{ focusMode ? '結束專注' : '專注編輯' }}</el-button>
          <div class="editor-controls">
            <el-radio-group v-model="preview" aria-label="正文顯示方式"><el-radio-button v-if="!readonly" value="write">撰寫</el-radio-button><el-radio-button v-if="!readonly" value="split">並排預覽</el-radio-button><el-radio-button value="read">閱讀預覽</el-radio-button></el-radio-group>
            <el-dropdown class="work-export" trigger="click" @command="exportWork"><el-button text :icon="ArrowDown" aria-label="複製與匯出">匯出</el-button><template #dropdown><el-dropdown-menu><el-dropdown-item command="markdown">複製 Markdown</el-dropdown-item><el-dropdown-item command="plain">複製純文字</el-dropdown-item><el-dropdown-item command="download">匯出 Markdown</el-dropdown-item></el-dropdown-menu></template></el-dropdown>
          </div>
        </div>
        <div v-if="detail.work.nextStep" class="next-step"><div class="next-step-heading"><span>下次接著做</span><el-button v-if="!readonly" link type="primary" @click="openDialog('next')">編輯下一步</el-button></div><ExpandableText :content="detail.work.nextStep" :lines="3" /></div>
        <el-button v-else-if="!readonly" class="next-step-add" text type="primary" @click="openDialog('next')">留下次接著做</el-button>
        <WorkMarkdownEditor ref="editorComponent" :model-value="body" :mode="preview" :save-status="readonly ? '唯讀' : status" :readonly="readonly || reloading" @update:model-value="auto.edit" />

      </template>
      <template v-else><div class="work-section-heading"><h2>外部作品文件</h2><el-button v-if="!readonly" link type="primary" @click="openDialog('next')">{{ detail.work.nextStep ? '編輯下一步' : '＋留下次接著做' }}</el-button></div><div v-if="detail.work.nextStep" class="next-step"><span>下次接著做</span><ExpandableText :content="detail.work.nextStep" :lines="3" /></div><p>正文保存在外部工具。此處不會同步、預覽或匯出該文件。</p><a v-if="detail.externalUrl" :href="detail.externalUrl" target="_blank" rel="noopener noreferrer">開啟原始文件</a><p v-else>尚未設定文件網址，可在作品資訊中補上。</p></template>
    </section>
    <aside class="work-aside">
      <SidePanel v-if="explorationThreads.isPending.value || explorationThreads.isError.value || explorationThreads.data.value?.length" >
        <h2>關聯探索線</h2>
        <p v-if="explorationThreads.isPending.value" class="work-hint">正在載入…</p>
        <p v-else-if="explorationThreads.isError.value" class="work-hint">無法載入探索線。<el-button text @click="explorationThreads.refetch()">重試</el-button></p>
        <div class="work-related-list"><button v-for="thread in explorationThreads.data.value" :key="thread.threadId" type="button" class="work-related-item side-relation" @click="openedThread = thread">
          <strong>{{ thread.threadName }}</strong><small>{{ thread.experimentTitle }} · {{ experimentApi.threadStatusLabel(thread.status) }}</small>
        </button></div>
      </SidePanel>
      <SidePanel as="details" class="work-disclosure" open>
        <summary class="side-panel-heading"><h2>參考素材</h2><div class="disclosure-actions" @keydown.stop><el-button v-if="!readonly" type="primary" link size="small" :icon="Plus" @click.stop.prevent="picker = true">加入來源</el-button><span class="disclosure-toggle" aria-hidden="true"><span class="when-closed">展開</span><span class="when-open">收合</span></span></div></summary>
        <div class="work-disclosure-content">
          <p v-if="sources.isPending.value">正在載入素材…</p>
          <p v-else-if="sources.isError.value">無法載入素材。<el-button text type="primary" @click="sources.refetch()">重試</el-button></p>
          <p v-else-if="!sources.data.value?.length" class="work-hint">尚未加入素材，正文可以獨立開始。</p>
          <article v-for="source in sources.data.value" :key="source.id!" class="work-source side-relation" role="button" tabindex="0" :aria-label="'閱讀全文：' + source.title" @click="reading = source" @keydown.enter.self.prevent="reading = source" @keydown.space.self.prevent="reading = source">
            <div class="work-source-heading"><strong class="work-source-title">{{ source.title }}</strong><div class="work-source-tools" @click.stop @keydown.stop><el-dropdown v-if="!readonly" trigger="click"><el-button text circle size="small" :icon="MoreFilled" aria-label="素材操作" /><template #dropdown><el-dropdown-menu><el-dropdown-item @click="removeSource(source)">移除關聯</el-dropdown-item></el-dropdown-menu></template></el-dropdown></div></div>
            <small>{{ source.cardId ? '卡片' : '探索紀錄' }}<template v-if="source.experimentTitle"> · {{ source.experimentTitle }}</template>{{ source.archived ? ' · 已封存' : '' }}</small>
          </article>
        </div>
      </SidePanel>
      <SidePanel as="details" class="work-disclosure" open>
        <summary class="side-panel-heading"><h2>作品階段</h2><span class="disclosure-toggle" aria-hidden="true"><span class="when-closed">展開</span><span class="when-open">收合</span></span></summary>
        <div class="work-disclosure-content">
          <dl class="work-properties"><div><dt>目前階段</dt><dd>{{ workStages[detail.work.stage] }}</dd></div><div v-if="detail.firstCompletedOn"><dt>首次初版完成</dt><dd>{{ detail.firstCompletedOn }}</dd></div><div><dt>階段切換</dt><dd>{{ formatDate(detail.stageChangedAt) }}</dd></div></dl>
        </div>
      </SidePanel>
      <SidePanel as="details" class="work-disclosure" :open="shareInitiallyOpen">
        <summary class="side-panel-heading"><h2>分享與入藏</h2><div class="disclosure-actions" @keydown.stop><el-button v-if="!readonly" type="primary" link size="small" :icon="Edit" @click.stop.prevent="openDialog('share')">編輯分享資訊</el-button><span class="disclosure-toggle" aria-hidden="true"><span class="when-closed">展開</span><span class="when-open">收合</span></span></div></summary>
        <div class="work-disclosure-content">
          <dl class="work-properties"><div><dt>發表狀態</dt><dd>{{ detail.work.published ? '已發表' : '未發表' }}</dd></div><div v-if="detail.sharedOn"><dt>分享日期</dt><dd>{{ detail.sharedOn }}</dd></div></dl>
          <a v-if="detail.shareUrl" :href="detail.shareUrl" target="_blank" rel="noopener noreferrer">查看分享連結</a>
          <p v-if="detail.shareNote" class="private-note">{{ detail.shareNote }}</p>
          <div class="work-treasure-context"><template v-if="detail.work.treasureId"><router-link :to="`/treasures/${detail.work.treasureId}`">前往展品</router-link><el-button v-if="!readonly" link @click="unlink">解除關聯</el-button></template><el-button v-else-if="!readonly && detail.work.stage === 'SHAREABLE'" text type="primary" @click="openDialog('treasure')">收入展品</el-button><p v-else-if="!readonly" class="work-hint">作品標記可分享後，可以選擇收入展品。</p></div>
        </div>
      </SidePanel>
    </aside></div>
    <ExperimentThreads v-if="openedThread" :key="openedThread.threadId" :experiment-id="openedThread.experimentId" :initial-thread-id="openedThread.threadId" dialog-only @closed="openedThread = undefined" />
    <WorkFormDialog v-if="editingInfo" v-model="editingInfo" :work="detail" @saved="detail = $event; auto.reset($event.body, $event.bodyVersion)" />
    <WorkSourcePicker v-if="picker" v-model="picker" :work-id="id" @saved="sources.refetch(); invalidate()" />
    <WorkSourceReadingDialog v-if="reading" :model-value="true" :source="reading" @update:model-value="reading = undefined" />
    <AppDialog pattern="compact-form" :width="dialog === 'treasure' ? 'min(560px, calc(100vw - 32px))' : 'min(620px, calc(100vw - 32px))'" ref="manualShell" :model-value="!!dialog" :title="dialog === 'next' ? '下次接著做' : dialog === 'stage' ? '編輯首次完成日期' : dialog === 'share' ? '分享資訊' : '收入展品'" :draft="[form, treasureMode, selectedTreasure]" :busy="busy" scroll-body @update:model-value="dialog = ''">
      <el-form class="app-record-form" label-position="top" :disabled="busy" @submit.prevent="saveDialog">
        <el-form-item v-if="dialog === 'next'" label="下次接著做（選填）"><el-input v-model="form.nextStep" type="textarea" :rows="5" maxlength="50000" /><p class="app-field-counter">總字數：{{ form.nextStep.length }} / 50,000</p><p class="app-field-help">留下一個容易接續的切入點即可，不必填寫完整計畫。</p></el-form-item>
        <template v-if="dialog === 'stage'"><el-form-item label="首次初版完成日期（選填）"><el-date-picker v-model="form.firstCompletedOn" type="date" value-format="YYYY-MM-DD" /></el-form-item><p class="app-field-help">作品階段請在頁首選單變更；這裡只調整首次初版完成日期。</p></template>
        <template v-if="dialog === 'share'"><p class="app-dialog-intro">只保存最近一筆分享資訊；儲存會取代原有網址、日期與備註。複製、匯出或分享連結不會自動標記已發表。</p><el-form-item label="發表狀態"><el-switch v-model="form.published" active-text="已發表" inactive-text="未發表" /></el-form-item><el-form-item label="分享網址（選填）"><el-input v-model="form.shareUrl" maxlength="2048" /></el-form-item><el-form-item label="實際分享日期（選填）"><el-date-picker v-model="form.sharedOn" type="date" value-format="YYYY-MM-DD" /></el-form-item><el-form-item label="分享或回饋備註（選填）"><el-input v-model="form.shareNote" type="textarea" :rows="4" maxlength="50000" /><p class="app-field-counter">總字數：{{ form.shareNote.length }} / 50,000</p></el-form-item></template>
        <template v-if="dialog === 'treasure'"><el-radio-group v-model="treasureMode"><el-radio value="new">新增展品</el-radio><el-radio value="existing">連到已有展品</el-radio></el-radio-group><template v-if="treasureMode === 'existing'"><p class="app-dialog-intro">保留展品原有說明、等級與收益；一件展品最多連到一件作品。</p><el-input v-model="treasureKeyword" placeholder="搜尋展品名稱" @input="treasurePage = 1" /><p v-if="treasures.isPending.value" role="status">正在載入展品…</p><el-alert v-else-if="treasures.isError.value" title="無法載入展品" type="error" :closable="false"><el-button @click="treasures.refetch()">重新載入</el-button></el-alert><p v-else-if="!treasures.data.value?.content.length" class="app-field-help">找不到符合條件的展品，可調整搜尋或改為新增展品。</p><div class="treasure-options"><PickerOption v-for="t in treasures.data.value?.content" :key="t.id" :title="t.title" :selected="selectedTreasure === t.id" :disabled="busy" @select="selectedTreasure = t.id" /></div><el-pagination v-if="(treasures.data.value?.totalElements ?? 0) > 20" v-model:current-page="treasurePage" :page-size="20" :total="treasures.data.value?.totalElements ?? 0" layout="prev, pager, next" /></template></template>
        <el-alert v-if="error" :title="error" type="error" :closable="false" />
      </el-form>
      <template #footer="{ requestClose }"><el-button :disabled="busy" @click="requestClose">取消</el-button><el-button type="primary" :loading="busy" :disabled="dialog === 'treasure' && treasureMode === 'existing' && !selectedTreasure" @click="saveDialog">{{ dialog === 'treasure' && treasureMode === 'new' ? '填寫入藏資訊' : '儲存變更' }}</el-button></template>
    </AppDialog>
    <TreasureFormDialog v-if="treasureForm" v-model="treasureForm" :prefill="treasurePrefill" :source-experiment="detail.work.experimentId ? { id: detail.work.experimentId, title: detail.work.experimentTitle || '' } : undefined" :create-handler="createTreasure" />
  </DetailShell>
</template>
<style scoped>
.work-workspace { min-width: 0; }
.work-return, .work-actions, .work-section-heading { display: flex; align-items: center; flex-wrap: wrap; gap: var(--space-sm); }
.work-return { min-width: 0; }
.work-experiment-link { display: inline-flex; max-width: 100%; padding: var(--space-2xs) var(--space-xs); border: 1px solid var(--el-border-color-light); border-radius: var(--radius-sm); color: var(--el-color-primary); font-size: var(--type-meta); line-height: var(--leading-metadata); text-decoration: none; overflow-wrap: anywhere; }
.work-experiment-link:hover { border-color: var(--el-color-primary-light-5); background: var(--surface-subtle); }
a, a:hover, a:focus-visible, .work-actions :deep(.el-button), .work-return :deep(.el-button) { margin: 0; }
.work-section-heading { justify-content: space-between; }
.work-heading { margin-bottom: var(--space-md); }
h2 { margin: 0; font-size: var(--type-section-title); font-weight: var(--weight-semibold); line-height: var(--leading-section-title); }
.work-panel { padding: var(--panel-padding); border: 1px solid var(--el-border-color-light); border-radius: var(--panel-radius); background: var(--el-bg-color); min-width: 0; }
.work-panel > * + * { margin-top: var(--space-sm); }
.body-panel--internal { padding: 0; }
.body-panel > .work-editor-heading { padding: var(--space-md); }
.body-panel > .next-step { margin: 0 var(--space-md) var(--space-sm); }
.body-panel > .next-step-add { margin: 0 var(--space-md) var(--space-sm); }
.body-panel > .markdown-composer { margin-top: 0; }
.is-focus .body-panel { position: fixed; z-index: 1200; top: var(--work-viewport-top); left: 0; right: 0; height: var(--work-viewport-height); display: flex; flex-direction: column; border: 0; border-radius: 0; }
.is-focus .body-panel > .work-editor-heading { flex-shrink: 0; padding-block: var(--space-xs); }
.is-focus .body-panel > .next-step, .is-focus .body-panel > .next-step-add { display: none; }
.is-focus .body-panel > .markdown-composer { flex: 1; min-height: 0; }
.is-focus .body-panel :deep(.markdown-writing) { height: 100%; min-height: 0; }
.is-focus .body-panel :deep(.markdown-preview) { max-height: 100%; }
.is-focus .body-panel :deep(.work-editor .ProseMirror), .is-focus .body-panel :deep(.markdown-reading) { max-width: var(--reading-max-width); margin-inline: auto; }
.work-panel summary { cursor: pointer; font-size: var(--type-section-title); font-weight: var(--weight-semibold); line-height: var(--leading-section-title); color: var(--el-text-color-primary); }
.work-disclosure summary { display: flex; align-items: center; justify-content: space-between; gap: var(--space-sm); list-style: none; }
.work-disclosure summary h2 { flex: 1 1 auto; min-width: 0; }
.disclosure-actions { display: flex; flex-wrap: wrap; justify-content: flex-end; align-items: center; gap: var(--space-xs); }
.disclosure-actions :deep(.el-button) { margin: 0; }
.work-disclosure summary::-webkit-details-marker { display: none; }
.work-disclosure summary:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: var(--space-2xs); }
.disclosure-toggle { flex-shrink: 0; color: var(--el-color-primary); font-size: var(--type-metadata); font-weight: var(--weight-regular); }
.when-open, .work-disclosure[open] .when-closed { display: none; }
.work-disclosure[open] .when-open { display: inline; }
.work-disclosure-content { display: grid; justify-items: stretch; gap: var(--space-sm); padding-top: var(--space-xs); }
.work-disclosure-content > .el-button { margin: 0; justify-self: start; }
.work-properties { display: grid; gap: var(--space-sm); margin: 0; width: 100%; min-width: 0; }
.work-properties > div { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1.4fr); align-items: baseline; gap: var(--space-xs); }
.work-properties dt { color: var(--el-text-color-secondary); font-size: var(--type-metadata); line-height: var(--leading-metadata); }
.work-properties dd { margin: 0; color: var(--el-text-color-primary); font-size: var(--type-secondary); line-height: var(--leading-ui); overflow-wrap: anywhere; }
.work-hint { color: var(--el-text-color-secondary); }
.work-treasure-context { display: flex; width: 100%; flex-wrap: wrap; align-items: center; gap: var(--space-sm); border-top: 1px solid var(--el-border-color-light); padding-top: var(--space-sm); }
.work-disclosure:not([open]) summary { margin-bottom: 0; }
.next-step { padding: var(--space-sm); border-radius: var(--radius-sm); background: var(--surface-summary); }
.next-step-heading { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: var(--space-xs); }
.next-step span { color: var(--el-text-color-secondary); font-size: var(--type-metadata); line-height: var(--leading-metadata); }
.next-step :deep(.expandable-text) { margin-top: var(--space-xs); font-size: var(--type-body); line-height: var(--leading-body); }
.next-step-add { margin-left: 0; }
.next-step p, .private-note { white-space: pre-wrap; overflow-wrap: anywhere; font-size: var(--type-body); line-height: var(--leading-body); }
.work-layout { display: grid; grid-template-columns: minmax(0, 1fr) minmax(270px, 320px); gap: var(--space-lg); align-items: start; margin-top: var(--space-lg); }
.work-aside { display: grid; gap: var(--space-md); min-width: 0; }
.work-aside :deep(.side-panel) { box-shadow: none; }
.editor-controls { display: flex; flex: 1 0 100%; min-width: 0; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: var(--space-sm); }
.work-editor-heading { align-items: center; }
.work-editor-title { display: flex; align-items: baseline; flex-wrap: wrap; gap: var(--space-xs) var(--space-sm); min-width: 0; }
.work-editor-title .work-status { margin: 0; }
.work-focus-toggle { flex-shrink: 0; margin: 0; }
.work-aside p { font-size: var(--type-secondary); line-height: var(--leading-ui); overflow-wrap: anywhere; }
.work-status { color: var(--el-text-color-secondary); font-size: var(--type-metadata); font-weight: var(--weight-medium); line-height: var(--leading-metadata); }
.work-source { min-width: 0; padding: var(--space-sm); border: 1px solid var(--el-border-color-light); border-radius: var(--radius-md); background: var(--surface-subtle); overflow-wrap: anywhere; cursor: pointer; }
.work-source:hover { border-color: var(--el-color-primary-light-5); }
.work-source:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 2px; }
.work-source-heading { display: flex; align-items: flex-start; gap: var(--space-xs); }
.work-source-title { flex: 1; min-width: 0; border: 0; padding: 0; background: transparent; font: inherit; font-size: var(--type-secondary); font-weight: var(--weight-semibold); line-height: var(--leading-ui); color: var(--el-text-color-primary); text-align: left; overflow-wrap: anywhere; cursor: pointer; }
.work-related-list { display: grid; gap: var(--space-xs); }
.work-related-item { display: grid; min-width: 0; width: 100%; gap: var(--space-2xs); border: 1px solid var(--el-border-color-light); border-radius: var(--radius-md); padding: var(--space-sm); background: var(--surface-subtle); color: var(--el-text-color-primary); font: inherit; text-align: left; cursor: pointer; }
.work-related-item:hover { border-color: var(--el-color-primary-light-5); }
.work-related-item strong { font-size: var(--type-secondary); font-weight: var(--weight-semibold); line-height: var(--leading-ui); overflow-wrap: anywhere; }
.work-related-item small { color: var(--el-text-color-secondary); font-size: var(--type-meta); line-height: var(--leading-metadata); overflow-wrap: anywhere; }
.work-related-item:focus-visible, .work-source-title:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 2px; }
.work-source strong { font-size: var(--type-secondary); font-weight: var(--weight-semibold); line-height: var(--leading-ui); }
.work-source small { display: block; margin-block: var(--space-2xs) var(--space-xs); color: var(--el-text-color-secondary); font-size: var(--type-metadata); line-height: var(--leading-metadata); }
.status-trigger { display: inline-flex; align-items: center; justify-content: center; gap: var(--space-xs); min-width: 120px; padding: 7px 10px; border: 1px solid var(--el-border-color); border-radius: var(--radius-sm); background: var(--el-fill-color-blank); color: var(--el-text-color-regular); font: inherit; white-space: nowrap; cursor: pointer; }
.status-trigger:hover, .status-trigger:focus-visible { border-color: var(--el-color-primary-light-5); color: var(--el-color-primary); }
.status-trigger:disabled { cursor: wait; opacity: 0.65; }
.status-trigger-arrow { margin-left: 2px; color: var(--el-text-color-placeholder); }
.work-status-dot { width: 8px; height: 8px; flex: 0 0 auto; border-radius: 50%; background: var(--el-text-color-placeholder); }
.work-stage-drafting, .work-stage-revising { background: var(--el-color-info); }
.work-stage-first_completed, .work-stage-shareable, .work-publication-published { background: var(--el-color-success); }
.work-publication-unpublished { background: var(--el-text-color-placeholder); }
.treasure-options { display: flex; flex-direction: column; align-items: stretch; gap: var(--space-xs); margin-block: var(--space-md); }
.el-alert { margin-block: var(--space-sm); }
@media (max-width: 1060px) { .work-layout { grid-template-columns: minmax(0, 1fr); } .work-aside { display: grid; gap: var(--space-md); grid-template-columns: repeat(auto-fit, minmax(min(100%, 280px), 1fr)); } }
@media (max-width: 760px) { .work-aside { display: grid; grid-template-columns: minmax(0, 1fr); } .work-return { width: 100%; } .editor-controls { justify-content: flex-start; width: 100%; } }
@media (max-width: 760px) { .body-panel--internal { margin-inline: calc(var(--page-gutter) * -1); border-inline: 0; border-radius: 0; } .is-focus .body-panel { margin: 0; } }
@media (max-width: 760px) {
  .work-actions > .el-dropdown { min-width: 0; }
  .work-actions > .work-stage-menu, .work-actions > .work-publication-menu { display: flex; }
  .work-actions .status-trigger { width: 100%; min-width: 0; }
  .work-actions > .work-more-menu { justify-self: end; }
  .editor-controls { justify-content: space-between; flex-wrap: nowrap; }
  .editor-controls > .el-radio-group { display: flex; flex: 1; min-width: 0; }
  .editor-controls :deep(.el-radio-button) { flex: 1; min-width: 0; }
  .editor-controls :deep(.el-radio-button__inner) { width: 100%; padding-inline: var(--space-xs); }
  .is-focus .work-editor-title .work-status, .is-focus .editor-controls { display: none; }
}
</style>
