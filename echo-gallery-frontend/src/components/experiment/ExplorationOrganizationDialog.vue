<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import AppDialog from '../AppDialog.vue'
import RecordPickerOption from '../ui/RecordPickerOption.vue'
import ExpandableText from '../ExpandableText.vue'
import CardPickerDialog from '../CardPickerDialog.vue'
import QuickCreateCardDialog from '../QuickCreateCardDialog.vue'
import ExplorationMethodPicker from './ExplorationMethodPicker.vue'
import type { ActivityRecord, MethodOption, OrganizationMode, OrganizationSelection, OrganizationPreview, OrganizationCommit, OrganizationResult } from '../../types/explorationActivity'
import type { CardContentRequest, CardDto } from '../../types/card'
import { explorationActivityApi as api } from '../../utils/api/explorationActivityApi'
import { cardApi } from '../../utils/api/cardApi'
import { apiErrorMessage } from '../../utils/api/errorMessage'
import { useExplorationInvalidation } from '../../composables/useExplorationInvalidation'
import { useWorkDialogGuard } from '../../composables/useWorkDialogGuard'
import { workUseTarget } from '../../utils/workFlow'
import { formatDate } from '../../utils/formatDate'

const props = withDefaults(defineProps<{
  modelValue: boolean; experimentId: number; threadId?: number; method?: MethodOption | null;
  initialRecords?: ActivityRecord[]; mode?: OrganizationMode; newMethodContent?: string; unassignedOnly?: boolean;
  expectedFocusVersion?: number; focusFavoriteMethodIds?: number[]; returnToEditor?: boolean
}>(), { mode: 'HISTORY', initialRecords: () => [] })
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; 'return-to-editor': [content: string]; saved: [result: OrganizationResult] }>()
const shell = ref<InstanceType<typeof AppDialog>>()
const creatingCard = ref(false), cardPicker = ref(false)
const createShell = ref<InstanceType<typeof QuickCreateCardDialog>>()
useWorkDialogGuard(shell, () => props.modelValue, async () => {
  if (creatingCard.value) { await createShell.value?.requestClose(); return true }
  if (cardPicker.value) { cardPicker.value = false; return true }
  return false
})
const draftBaseline = ref<string>()
const invalidate = useExplorationInvalidation()
const step = ref<'SELECT' | 'PREVIEW' | 'DONE'>('SELECT')
const mode = ref<OrganizationMode>('HISTORY')
const page = ref(1), selected = ref(new Map<number, ActivityRecord>()), selectedMethods = ref(new Map<number, MethodOption>())
const includeMethod = ref(true), includeHistory = ref(false), chooseMethods = ref(false), wholeThread = ref(false)
const clearRecords = ref(false), cardTarget = ref<'NEW' | 'EXISTING'>('NEW'), targetCard = ref<CardDto | null>(null)
const preview = ref<OrganizationPreview | null>(null), drafts = ref<Record<string, string>>({})
const newContent = ref(''), cardContent = ref(''), error = ref(''), busy = ref(false), uncertain = ref(false)
const updateValidation = computed(() => {
  if (mode.value !== 'UPDATE_METHOD') return ''
  if (!newContent.value.trim()) return '請輸入更新後行動。'
  return newContent.value.trim() === preview.value?.groups[0]?.methodContent.trim()
    ? '更新後的行動內容必須與原內容不同。若只要整理紀錄，請使用整理功能。' : ''
})
const refreshing = ref(false), previewStatus = ref('')
const result = ref<OrganizationResult | null>(null), requestId = ref(crypto.randomUUID())
let submitted: OrganizationCommit | undefined
let generation = 0
let resumeDraft = false
const recordsQuery = useQuery({
  queryKey: computed(() => ['experiment-activity-records', props.experimentId, props.threadId, props.method?.id, props.unassignedOnly, page.value]),
  queryFn: () => api.records(props.experimentId, { threadId: props.threadId, methodId: props.method?.id, unassigned: props.unassignedOnly, page: page.value - 1 }),
  enabled: computed(() => props.modelValue && step.value === 'SELECT'),
})
watch(() => props.modelValue, visible => {
  generation++
  if (!visible) return
  // 返回編輯不代表放棄整理；父表單保留此元件，重新進入時沿用草稿。
  if (resumeDraft && preview.value && props.mode === 'UPDATE_METHOD') {
    resumeDraft = false
    newContent.value = props.newMethodContent ?? newContent.value
    return
  }
  resumeDraft = false
  step.value = 'SELECT'; mode.value = props.mode; page.value = 1; error.value = ''; busy.value = false; uncertain.value = false
  refreshing.value = false; previewStatus.value = ''
  selected.value = new Map(props.initialRecords.map(r => [r.id, r]))
  selectedMethods.value = new Map(props.method ? [[props.method.id, props.method]] : [])
  includeMethod.value = true; includeHistory.value = false; chooseMethods.value = false; wholeThread.value = false
  clearRecords.value = props.mode === 'UPDATE_METHOD'; preview.value = null; drafts.value = {}; result.value = null
  newContent.value = props.newMethodContent ?? props.method?.content ?? ''; cardContent.value = ''
  draftBaseline.value = JSON.stringify([step.value, [...selected.value.keys()], [...selectedMethods.value.keys()], mode.value, drafts.value, clearRecords.value, newContent.value, cardContent.value])
  cardTarget.value = 'NEW'; targetCard.value = null; submitted = undefined; requestId.value = crypto.randomUUID()
  if (props.mode === 'UPDATE_METHOD') void preparePreview()
}, { immediate: true })
const toggleRecord = (record: ActivityRecord) => {
  const next = new Map(selected.value)
  if (next.has(record.id)) next.delete(record.id)
  else if (next.size < 100) next.set(record.id, record)
  else ElMessage.info('一次最多選取 100 筆發現。')
  selected.value = next
}
const toggleMethod = (method: MethodOption) => {
  const next = new Map(selectedMethods.value)
  if (next.has(method.id)) next.delete(method.id); else next.set(method.id, method)
  selectedMethods.value = next
}
const groupKey = (threadId: number, methodId: number | null) => methodId == null ? 'thread:' + threadId : 'method:' + methodId
async function selections(): Promise<OrganizationSelection[]> {
  if (mode.value === 'UPDATE_METHOD' && props.method)
    return [{ threadId: props.method.threadId, methodId: props.method.id, recordIds: [], allRecords: true, includeMethod: true, includeHistory: false }]
  if (wholeThread.value && props.threadId != null) {
    if (props.unassignedOnly) return [{ threadId: props.threadId, methodId: null, recordIds: [], allRecords: true, includeMethod: false, includeHistory: mode.value === 'CARD' && includeHistory.value }]
    if (props.method) return [{ threadId: props.method.threadId, methodId: props.method.id, recordIds: [], allRecords: true, includeMethod: true, includeHistory: mode.value === 'CARD' && includeHistory.value }]
    const methods: MethodOption[] = []
    for (let p = 0; ; p++) {
      const response = await api.methods(props.experimentId, { threadId: props.threadId, page: p, size: 50 })
      methods.push(...response.content)
      if (methods.length > 99) throw new Error('此探索線超過 99 種行動，請取消整線選取並分組整理。')
      if (p + 1 >= response.totalPages) break
    }
    return [...methods.map(m => ({ threadId: m.threadId, methodId: m.id, recordIds: [], allRecords: true, includeMethod: true, includeHistory: mode.value === 'CARD' && includeHistory.value })),
      { threadId: props.threadId, methodId: null, recordIds: [], allRecords: true, includeMethod: false, includeHistory: mode.value === 'CARD' && includeHistory.value }]
  }
  const groups = new Map<string, OrganizationSelection>()
  for (const record of selected.value.values()) {
    const key = groupKey(record.threadId, record.methodId)
    const group = groups.get(key) ?? { threadId: record.threadId, methodId: record.methodId, recordIds: [], allRecords: false, includeMethod: includeMethod.value && record.methodId != null, includeHistory: mode.value === 'CARD' && includeHistory.value }
    group.recordIds.push(record.id); groups.set(key, group)
  }
  for (const method of selectedMethods.value.values()) {
    const key = groupKey(method.threadId, method.id)
    const group = groups.get(key) ?? { threadId: method.threadId, methodId: method.id, recordIds: [], allRecords: false, includeMethod: true, includeHistory: mode.value === 'CARD' && includeHistory.value }
    group.includeMethod = true; groups.set(key, group)
  }
  return [...groups.values()]
}
async function preparePreview() {
  if (busy.value) return
  if (mode.value === 'CARD' && cardTarget.value === 'EXISTING' && !targetCard.value) { error.value = '請先選擇要追加的卡片。'; return }
  const current = generation
  const previousPreview = preview.value
  const isRefresh = step.value === 'PREVIEW'
  refreshing.value = isRefresh; previewStatus.value = ''
  busy.value = true; error.value = ''
  try {
    const groups = await selections()
    if (!groups.length) { error.value = '請選擇行動或發現。'; return }
    const response = await api.preview(props.experimentId, mode.value, groups, mode.value === 'CARD' && cardTarget.value === 'EXISTING' ? Number(targetCard.value!.id) : undefined)
    if (current !== generation) return
    const previous = drafts.value
    const generated = new Map(previousPreview?.groups.map(g => [g.key, g.text]) ?? [])
    let preserved = false
    drafts.value = Object.fromEntries(response.groups.map(g => {
      const edited = previous[g.key] != null && previous[g.key] !== generated.get(g.key)
      preserved ||= edited
      return [g.key, edited ? previous[g.key]! : g.text]
    }))
    if (!previousPreview || cardContent.value === previousPreview.cardContent) cardContent.value = response.cardContent
    else preserved = true
    if (isRefresh) {
      const changed = JSON.stringify(previousPreview?.groups) !== JSON.stringify(response.groups)
      previewStatus.value = !changed ? '已重新讀取原文，內容沒有變更。'
        : preserved ? '原文已更新；已編輯的草稿保留，請核對新增內容。' : '原文與未編輯的草稿已更新。'
    }
    preview.value = response
    step.value = 'PREVIEW'; requestId.value = crypto.randomUUID(); submitted = undefined
  } catch (e) { error.value = apiErrorMessage(e, e instanceof Error ? e.message : '無法產生整理預覽，選取內容已保留。') }
  finally { if (current === generation) { busy.value = false; refreshing.value = false } }
}
async function performCommit(newCard?: CardContentRequest): Promise<OrganizationResult> {
  if (result.value) return result.value
  if (!preview.value) throw new Error('請先預覽')
  const payload: OrganizationCommit = submitted ?? {
    token: preview.value.token, requestId: requestId.value,
    drafts: preview.value.groups.map(g => ({ key: g.key, text: drafts.value[g.key] ?? g.text })),
    clearRecords: clearRecords.value || mode.value === 'UPDATE_METHOD',
    ...(mode.value === 'UPDATE_METHOD' ? { newMethodContent: newContent.value } : {}),
    ...(mode.value === 'UPDATE_METHOD' && props.expectedFocusVersion != null ? { expectedFocusVersion: props.expectedFocusVersion, focusFavoriteMethodIds: props.focusFavoriteMethodIds } : {}),
    ...(mode.value === 'CARD' ? newCard ? { newCard } : { cardContent: cardContent.value } : {}),
  }
  submitted = payload; busy.value = true; error.value = ''
  try {
    const response = await api.commit(props.experimentId, payload)
    uncertain.value = false; result.value = response
    await invalidate(props.experimentId); emit('saved', response)
    if (!newCard) step.value = 'DONE'
    return response
  } catch (e) {
    const status = (e as { response?: { status?: number } }).response?.status
    const definitive = status != null && status >= 400 && status < 500 && status !== 408
    uncertain.value = !definitive
    if (definitive) { submitted = undefined; requestId.value = crypto.randomUUID() }
    error.value = apiErrorMessage(e, '尚無法確認提交結果，請重試原請求；草稿已保留。')
    throw e
  } finally { busy.value = false }
}
async function confirm() {
  if (updateValidation.value && !uncertain.value) return
  if (mode.value === 'CARD' && cardTarget.value === 'NEW' && !submitted) { creatingCard.value = true; return }
  try { await performCommit() } catch { /* 錯誤保留在原工作區。 */ }
}
async function createCard(request: CardContentRequest) {
  try {
    const response = await performCommit(request)
    return await cardApi.getCard(response.cardId!)
  } catch (e) {
    // 結果不明時回到父層，只允許以已凍結的同一份卡片請求重試。
    if (result.value) {
      creatingCard.value = false; step.value = 'DONE'
      error.value = '整理與卡片已儲存，暫時無法讀取卡片詳情。可稍後由卡片列表查看。'
    } else if (uncertain.value) creatingCard.value = false
    throw e
  }
}
function cardCreated() { step.value = 'DONE'; creatingCard.value = false }
function returnToEditing() {
  if (busy.value || uncertain.value) return
  resumeDraft = true
  emit('return-to-editor', newContent.value)
  emit('update:modelValue', false)
}
function useForWork() {
  if (!result.value?.cardId) return
  workUseTarget.value = { cardIds: [result.value.cardId] }
  emit('update:modelValue', false)
}
</script>
<template>
  <AppDialog v-if="!creatingCard" ref="shell" :model-value="modelValue" :title="mode === 'UPDATE_METHOD' ? '整理舊紀錄並更新行動' : '整理探索內容'" pattern="workspace" width="min(900px, calc(100vw - 32px))" scroll-body :busy="busy || uncertain" :draft-ready="step !== 'DONE'" :draft-baseline="draftBaseline" :draft="[step, [...selected.keys()], [...selectedMethods.keys()], mode, drafts, clearRecords, newContent, cardContent]" @update:model-value="emit('update:modelValue', $event)">
    <template v-if="step === 'SELECT'">
      <p class="app-dialog-intro">選取行動或完整發現，再編輯整理文字。選取跨頁保留，確認後才保存。</p>
      <el-radio-group v-model="mode"><el-radio-button value="HISTORY">存入歷次整理</el-radio-button><el-radio-button value="CARD">整理成卡片</el-radio-button></el-radio-group>
      <div v-if="mode === 'CARD'" class="settings"><el-radio-group v-model="cardTarget"><el-radio value="NEW">新建卡片</el-radio><el-radio value="EXISTING">追加既有卡片</el-radio></el-radio-group><el-button v-if="cardTarget === 'EXISTING'" type="primary" link @click="cardPicker = true">{{ targetCard?.title || '選擇卡片' }}</el-button><el-checkbox v-model="includeHistory">包含所選分組的歷次整理文字</el-checkbox></div>
      <div class="settings"><el-checkbox v-if="threadId != null" v-model="wholeThread">{{ method ? '選取這個行動及其全部發現' : unassignedOnly ? '選取未指定行動的全部發現' : '選取整條探索線的行動與發現' }}</el-checkbox><el-checkbox v-model="includeMethod" :disabled="wholeThread">勾選發現時包含行動文字</el-checkbox></div>
      <template v-if="!wholeThread">
        <el-button type="primary" link @click="chooseMethods = !chooseMethods">{{ chooseMethods ? '收合行動選取' : '另外選取行動（可只輸出行動）' }}</el-button>
        <ExplorationMethodPicker v-if="chooseMethods" :experiment-id="experimentId" :thread-id="threadId" :model-value="null" :show-shortcuts="false" @select="toggleMethod" />
        <div class="selected-methods"><el-tag v-for="method in selectedMethods.values()" :key="method.id" closable @close="toggleMethod(method)">{{ method.content }}</el-tag></div>
        <p class="metadata">已選 {{ selected.size }} 筆發現、{{ selectedMethods.size }} 種行動</p>
        <el-skeleton v-if="recordsQuery.isPending.value" :rows="3" animated />
        <p v-else-if="recordsQuery.isError.value">無法讀取紀錄。<el-button type="primary" link @click="recordsQuery.refetch()">重試</el-button></p>
        <div v-else class="records"><RecordPickerOption v-for="record in recordsQuery.data.value?.content ?? []" :key="record.id" :title="formatDate(record.occurredAt)" :selected="selected.has(record.id)" :try-text="record.tryText" :discovery="record.discovery" @select="toggleRecord(record)" /><p v-if="!recordsQuery.data.value?.content.length" class="metadata">尚無活動發現，可另外選取行動。</p></div>
        <el-pagination v-if="(recordsQuery.data.value?.totalPages ?? 0) > 1" v-model:current-page="page" :page-size="20" :total="recordsQuery.data.value?.totalElements ?? 0" layout="prev, pager, next" />
      </template>
    </template>
    <template v-else-if="step === 'PREVIEW'">
      <p class="app-dialog-intro">請核對原文並編輯整理文字。重新讀取原文會保留你已編輯的草稿。</p>
      <p v-if="previewStatus" class="preview-status" role="status">{{ previewStatus }}</p>
      <div class="organization-preview">
        <section v-for="group in preview?.groups ?? []" :key="group.key" class="draft-group">
          <h3>{{ group.threadName }} · {{ group.methodContent }}</h3>
          <p v-if="group.lastOrganizedAt" class="metadata">最近整理 {{ formatDate(group.lastOrganizedAt) }} · 本次會追加文字</p>
          <details class="source-panel"><summary>查看本次選取原文<span class="source-count">{{ group.recordCount }} 筆發現</span></summary><div class="source-content"><ExpandableText :content="group.text" :lines="6" /><template v-if="group.historyText"><h4>既有歷次整理</h4><ExpandableText :content="group.historyText" :lines="6" /></template></div></details>
          <div class="organization-field"><label :for="'draft-' + group.key">本輪整理文字</label><el-input :id="'draft-' + group.key" v-model="drafts[group.key]" type="textarea" :rows="7" :disabled="busy || uncertain" maxlength="500000" /></div>
        </section>
        <div v-if="mode === 'UPDATE_METHOD'" class="organization-field"><label for="organized-action">更新後行動</label><el-input id="organized-action" v-model="newContent" type="textarea" :rows="3" maxlength="2000" :disabled="busy || uncertain" :aria-invalid="!!updateValidation" :aria-describedby="updateValidation ? 'organized-action-error' : undefined" /><p v-if="updateValidation" id="organized-action-error" class="field-error" role="alert">{{ updateValidation }}</p></div>
        <template v-if="mode === 'CARD'"><p class="metadata">卡片內容可自由整理，不回寫原紀錄。新建卡片會接續完整建立表單。</p><div class="organization-field"><label for="organized-card">卡片輸出草稿</label><el-input id="organized-card" v-model="cardContent" type="textarea" :rows="7" :disabled="busy || uncertain" /></div></template>
        <el-checkbox v-if="mode !== 'UPDATE_METHOD'" v-model="clearRecords" :disabled="busy || uncertain">保存後清除本次選取的活動發現</el-checkbox>
        <el-alert v-if="clearRecords || mode === 'UPDATE_METHOD'" type="warning" :closable="false" :title="mode === 'UPDATE_METHOD' ? '確認更新後，會先將本輪整理文字保存到探索線的歷次整理，再移除此行動的全部活動發現並更新行動。請先核對整理文字是否保留需要回顧的內容。' : '確認後只清除本次選取的發現；本輪文字也會追加到各自歷次整理，歷史、卡片與作品保留。'" />
      </div>
    </template>
    <template v-else><el-result icon="success" title="整理已完成" :sub-title="'已清除 ' + (result?.clearedCount ?? 0) + ' 筆活動發現，既有歷史保留。'" /><el-button v-if="result?.cardId" type="primary" link @click="useForWork">將成果卡片用於作品</el-button></template>
    <el-alert v-if="error" class="flow-error" :title="error" type="error" :closable="false" />
    <template #footer="{ requestClose }">
      <el-button v-if="step === 'DONE'" @click="requestClose">關閉</el-button>
      <template v-else><el-button v-if="step === 'PREVIEW' && mode !== 'UPDATE_METHOD'" :disabled="busy || uncertain" @click="step = 'SELECT'">返回選取</el-button><el-button v-if="mode === 'UPDATE_METHOD' && returnToEditor" :disabled="busy || uncertain" @click="returnToEditing">返回編輯</el-button><el-button v-else :disabled="busy || uncertain" @click="requestClose">取消</el-button>
        <el-button v-if="step === 'SELECT'" type="primary" :loading="busy" @click="preparePreview">預覽整理</el-button>
        <template v-else><el-button v-if="!uncertain" type="primary" link :loading="refreshing" :disabled="busy" @click="preparePreview">重新讀取原文</el-button><el-button type="primary" :loading="busy && !refreshing" :disabled="busy || !preview || !!updateValidation" @click="confirm">{{ uncertain ? '重試確認上次操作' : mode === 'UPDATE_METHOD' ? '確認更新' : mode === 'CARD' && cardTarget === 'NEW' ? '接續建立卡片' : '確認保存' }}</el-button></template>
      </template>
    </template>
  </AppDialog>
  <CardPickerDialog v-model="cardPicker" title="選擇追加卡片" description="本次整理文字會追加至卡片正文，既有內容保留。" @confirm="(card: CardDto) => { targetCard = card; cardPicker = false }" />
  <QuickCreateCardDialog v-if="creatingCard" ref="createShell" v-model="creatingCard" title="建立整理成果卡片" description="確認卡片內容與設定；建立成功時才會一併保存本次整理與清除選項。" :initial-data="{ type: 'note', title: '探索整理', content: cardContent }" :create-card="createCard" :create-error-message="error || '建立卡片失敗，草稿已保留。'" @created="cardCreated" />
</template>
<style scoped>
.settings { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-sm); margin-block: var(--space-sm); }
.records, .selected-methods { display: grid; gap: var(--space-xs); margin-block: var(--space-sm); }
.selected-methods { display: flex; flex-wrap: wrap; }
.organization-preview, .draft-group { display: grid; gap: var(--space-md); min-width: 0; }
.draft-group + .draft-group { border-top: 1px solid var(--el-border-color-light); padding-top: var(--space-md); }
h3 { margin: 0; font-size: var(--type-ui); } .metadata { margin: var(--space-xs) 0; color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.organization-field { display: grid; gap: var(--space-xs); min-width: 0; }
.organization-field > label { margin: 0; }
.field-error { margin: 0; color: var(--el-color-danger); font-size: var(--type-meta); }
.organization-preview .metadata { margin: 0; }
summary { cursor: pointer; color: var(--el-color-primary); } .flow-error { margin-top: var(--space-sm); }
.source-panel { background: var(--surface-summary); border-radius: var(--radius-md); padding: var(--space-sm); min-width: 0; }
.source-count { display: inline-block; margin-left: var(--space-xs); color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.source-content { display: grid; gap: var(--space-sm); margin-top: var(--space-sm); min-width: 0; overflow-wrap: anywhere; }
.source-content h4 { margin: 0; color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.preview-status { margin-block: var(--space-sm); color: var(--el-text-color-regular); font-size: var(--type-secondary); }
</style>
