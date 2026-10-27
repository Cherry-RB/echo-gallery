<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppDialog from '../AppDialog.vue'
import FieldCounter from '../ui/FieldCounter.vue'
import ExplorationThreadSelect from './ExplorationThreadSelect.vue'
import ExplorationActionSelect from './ExplorationActionSelect.vue'
import ExplorationOrganizationDialog from './ExplorationOrganizationDialog.vue'
import type { ExperimentExplorationRecordDto, ExplorationThreadMethodDto } from '../../types/experiment'
import type { ActivityRecord, ActivityState, FocusRequest, MethodOption } from '../../types/explorationActivity'
import { explorationActivityApi as api } from '../../utils/api/explorationActivityApi'
import { apiErrorMessage } from '../../utils/api/errorMessage'
import { useExplorationInvalidation } from '../../composables/useExplorationInvalidation'
import { useWorkDialogGuard } from '../../composables/useWorkDialogGuard'

const props = withDefaults(defineProps<{
  modelValue: boolean; experimentId: number; mode: 'TRY' | 'DISCOVERY';
  record?: ExperimentExplorationRecordDto | null; threadId?: number; method?: ExplorationThreadMethodDto | MethodOption | null;
  intent?: 'SELECT' | 'CREATE' | 'EDIT' | 'COPY' | 'ASSIGN'
}>(), { intent: 'SELECT' })
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; closed: []; saved: [] }>()
const shell = ref<InstanceType<typeof AppDialog>>()
const organizationVisible = ref(false)
const organizationStarted = ref(false)
useWorkDialogGuard(shell, () => props.modelValue && !organizationVisible.value)
const invalidate = useExplorationInvalidation()
const state = ref<ActivityState | null>(null), selectedMethod = ref<MethodOption | null>(null)
const targetThread = ref<number | null>(null), threadLabel = ref(''), tryDraft = ref(''), discovery = ref('')
const action = ref<'SELECT' | 'CREATE' | 'EDIT' | 'COPY'>('SELECT')
const loading = ref(false), saving = ref(false), initializationFailed = ref(false), error = ref('')
const contextOpen = ref(false)
const draftBaseline = ref<string>(), favoriteIds = ref<number[]>([])
const seenMethods = ref(new Map<number, MethodOption>())
const actionField = ref<InstanceType<typeof ExplorationActionSelect>>()
const discoveryField = ref<{ focus: () => void }>()
const threadField = ref<InstanceType<typeof ExplorationThreadSelect>>()
let generation = 0
const settingNext = computed(() => props.mode === 'TRY' && props.intent === 'SELECT')
const editingAction = computed(() => action.value === 'EDIT' || action.value === 'COPY')
const actionLabel = computed(() => settingNext.value ? '下一步行動' : action.value === 'COPY' ? '新行動內容' : '行動內容')
const submitLabel = computed(() => {
  if (props.mode === 'DISCOVERY') return '儲存'
  if (action.value === 'EDIT') return selectedMethod.value?.discoveryCount ? '下一步：整理舊紀錄' : settingNext.value ? '更新並設為下一步' : '更新行動'
  if (action.value === 'COPY') return settingNext.value ? '建立副本並設為下一步' : '建立副本'
  return '儲存'
})
const title = computed(() => props.mode === 'DISCOVERY' ? props.record ? '編輯發現' : '記下發現'
  : settingNext.value ? '設定下一步' : action.value === 'COPY' ? '複製行動' : action.value === 'EDIT' ? '更新行動' : '新增行動備選')
const activityRecord = computed(() => props.record as ActivityRecord | null | undefined)
const favorites = computed(() => {
  const options = [...(state.value?.favoriteMethods ?? []), ...(state.value?.recentMethods ?? []), ...seenMethods.value.values()]
  return favoriteIds.value.map(id => options.find(m => m.id === id)).filter((m): m is MethodOption => !!m)
})
const favoritesChanged = computed(() => JSON.stringify(favoriteIds.value) !== JSON.stringify(state.value?.favoriteMethods.map(m => m.id) ?? []))
const draft = computed(() => [action.value, targetThread.value, selectedMethod.value?.id, tryDraft.value, discovery.value, favoriteIds.value])
async function initialize() {
  const current = ++generation
  if (!props.modelValue) return
  loading.value = true; error.value = ''; initializationFailed.value = false; organizationVisible.value = false; organizationStarted.value = false
  contextOpen.value = props.intent === 'ASSIGN'
  action.value = props.mode === 'DISCOVERY' || props.intent === 'ASSIGN' ? 'SELECT' : props.intent
  tryDraft.value = ''; discovery.value = props.record?.discovery ?? ''; selectedMethod.value = null
  try {
    const result = await api.state(props.experimentId)
    if (current !== generation) return
    state.value = result; favoriteIds.value = result.favoriteMethods.map(m => m.id)
    seenMethods.value = new Map()
    const methodId = props.record?.methodId ?? props.method?.id ?? props.record?.sourceThreadMethodId
    const method = methodId != null ? await api.method(props.experimentId, methodId)
      : props.record || props.threadId != null ? null : result.currentMethod
    if (current !== generation) return
    selectedMethod.value = method
    if (method) seenMethods.value.set(method.id, method)
    targetThread.value = props.record?.threadId ?? props.threadId ?? method?.threadId ?? result.currentThreadId
    threadLabel.value = props.record?.threadName ?? method?.threadName ?? ''
    tryDraft.value = method?.content ?? ''
    if (props.intent === 'CREATE' && props.mode === 'TRY') { selectedMethod.value = null; tryDraft.value = '' }
  } catch (e) {
    if (current === generation) { initializationFailed.value = true; error.value = apiErrorMessage(e, '無法讀取探索資料，請重試。') }
  } finally {
    if (current === generation) {
      draftBaseline.value = JSON.stringify(draft.value); loading.value = false
      await nextTick()
      if (props.mode === 'DISCOVERY') {
        if (props.intent === 'ASSIGN') threadField.value?.focus?.()
        else discoveryField.value?.focus?.()
      } else if (settingNext.value && !editingAction.value) actionField.value?.focus?.()
    }
  }
}
watch([() => props.modelValue, () => props.mode, () => props.experimentId, () => props.record?.id, () => props.method?.id, () => props.intent], () => { void initialize() }, { immediate: true })
onBeforeUnmount(() => { generation++ })
function choose(method: MethodOption) {
  seenMethods.value.set(method.id, method)
  selectedMethod.value = method; tryDraft.value = method.content; targetThread.value = method.threadId; threadLabel.value = method.threadName
  action.value = 'SELECT'
}
function input(value: string) {
  tryDraft.value = value
  if (selectedMethod.value && value !== selectedMethod.value.content) selectedMethod.value = null
  action.value = value.trim() ? 'CREATE' : 'SELECT'
}
function changeThread(id: number | null) {
  targetThread.value = id; threadLabel.value = ''
  if (selectedMethod.value && selectedMethod.value.threadId !== id && action.value !== 'COPY') {
    selectedMethod.value = null; tryDraft.value = ''; action.value = 'SELECT'
  }
}
function edit(kind: 'EDIT' | 'COPY') {
  if (!selectedMethod.value) return
  error.value = ''
  action.value = kind; tryDraft.value = selectedMethod.value.content
}
function cancelEdit() {
  error.value = ''
  action.value = 'SELECT'; tryDraft.value = selectedMethod.value?.content ?? ''
  if (selectedMethod.value) { targetThread.value = selectedMethod.value.threadId; threadLabel.value = selectedMethod.value.threadName }
}
function toggleFavorite(method: MethodOption) {
  const ids = favoriteIds.value
  if (ids.includes(method.id)) favoriteIds.value = ids.filter(id => id !== method.id)
  else if (ids.length < 3) favoriteIds.value = [...ids, method.id]
  else ElMessage.info('最多 3 筆常用行動，請先移除一筆。')
}
async function reload() {
  try {
    await ElMessageBox.confirm('重新載入會捨棄這次尚未儲存的編輯。請先複製需要保留的文字。', '重新載入資料', { confirmButtonText: '捨棄並重新載入', cancelButtonText: '返回編輯', type: 'warning' })
    await initialize()
  } catch { /* 保留原草稿。 */ }
}
async function finish() {
  await invalidate(props.experimentId)
  emit('saved'); emit('update:modelValue', false)
}
async function save() {
  if (!canSave.value || saving.value || !state.value) return
  error.value = ''
  if (props.mode === 'TRY' && action.value === 'EDIT' && selectedMethod.value?.discoveryCount) {
    organizationStarted.value = true; organizationVisible.value = true; return
  }
  saving.value = true
  try {
    if (props.mode === 'DISCOVERY') {
      const method = selectedMethod.value
      if (activityRecord.value) {
        await api.updateRecord(props.experimentId, activityRecord.value, discovery.value.trim(), method?.id ?? null, method?.version, targetThread.value ?? activityRecord.value.threadId)
      } else {
        await api.createRecord(props.experimentId, { threadId: targetThread.value, methodId: method?.id ?? null, discovery: discovery.value.trim(), expectedMethodVersion: method?.version })
      }
    } else if (settingNext.value) {
      const request: FocusRequest = { threadId: targetThread.value, expectedVersion: state.value.version }
      if (editingAction.value && selectedMethod.value) {
        Object.assign(request, { methodId: selectedMethod.value.id, newContent: tryDraft.value.trim(), action: action.value === 'EDIT' ? 'UPDATE' : 'COPY', expectedMethodVersion: selectedMethod.value.version })
      } else if (selectedMethod.value) Object.assign(request, { methodId: selectedMethod.value.id, expectedMethodVersion: selectedMethod.value.version })
      else if (tryDraft.value.trim()) request.newContent = tryDraft.value.trim()
      else request.methodId = null
      if (favoritesChanged.value) request.favoriteMethodIds = favoriteIds.value
      await api.focus(props.experimentId, request)
    } else if (action.value === 'EDIT' && selectedMethod.value) {
      await api.updateMethod(props.experimentId, selectedMethod.value.id, tryDraft.value.trim(), selectedMethod.value.version)
    } else {
      await api.createMethod(props.experimentId, { threadId: targetThread.value, content: tryDraft.value.trim(),
        ...(action.value === 'COPY' && selectedMethod.value ? { sourceMethodId: selectedMethod.value.id, expectedVersion: selectedMethod.value.version } : {}) })
    }
    ElMessage.success('已儲存')
    await finish()
  } catch (e) { error.value = apiErrorMessage(e, '儲存失敗，草稿已保留。資料如已變更，請重新確認後再試。') }
  finally { saving.value = false }
}
const unchangedUpdate = computed(() => props.mode === 'TRY' && action.value === 'EDIT' && selectedMethod.value != null
  && tryDraft.value.trim() === selectedMethod.value.content.trim())
const validation = computed(() => unchangedUpdate.value ? '更新後的行動內容必須與原內容不同。若只要整理紀錄，請使用整理功能。'
  : action.value === 'COPY' && tryDraft.value.trim() === selectedMethod.value?.content?.trim() ? '複製後的行動內容需與來源不同。' : '')
const canSave = computed(() => !loading.value && !initializationFailed.value && !validation.value && !unchangedUpdate.value && (props.mode === 'DISCOVERY'
  ? !!discovery.value.trim() && (!!selectedMethod.value || !tryDraft.value.trim())
  : settingNext.value && !editingAction.value || !!tryDraft.value.trim()))
</script>
<template>
  <AppDialog v-if="!organizationVisible" ref="shell" :model-value="modelValue" :title="title" width="min(560px, calc(100vw - 32px))" scroll-body :busy="saving" :draft-ready="!loading" :draft-baseline="draftBaseline" :draft="draft" @update:model-value="emit('update:modelValue', $event)" @closed="emit('closed')">
    <el-skeleton v-if="loading" :rows="3" animated />
    <el-form v-else label-position="top" :disabled="saving" @submit.prevent="save">
      <details v-if="mode === 'DISCOVERY'" class="discovery-context" :open="contextOpen" @toggle="contextOpen = ($event.target as HTMLDetailsElement).open">
        <summary><span>歸屬設定</span><span class="context-summary">{{ threadLabel || (targetThread == null ? '未設定' : '已選探索線') }} · {{ selectedMethod?.content || (tryDraft.trim() ? '尚未選取行動' : '未指定行動') }}</span></summary>
        <div class="context-fields">
          <el-form-item label="探索線"><ExplorationThreadSelect ref="threadField" :experiment-id="experimentId" :model-value="targetThread" :label="threadLabel" :clearable="!record" @update:model-value="changeThread" @label-change="threadLabel = $event" /></el-form-item>
          <el-form-item label="對應行動（選填）"><ExplorationActionSelect :experiment-id="experimentId" :thread-id="targetThread" :method="selectedMethod" :text="tryDraft" :recent="state?.recentMethods" :favorites="favorites" :disabled="saving" @input="input" @select="choose" /></el-form-item>
        </div>
      </details>
      <template v-if="mode === 'TRY'">
        <el-form-item label="探索線"><ExplorationThreadSelect ref="threadField" :experiment-id="experimentId" :model-value="targetThread" :label="threadLabel" :clearable="!record" :disabled="action === 'EDIT'" @update:model-value="changeThread" @label-change="threadLabel = $event" /></el-form-item>
        <el-form-item :label="actionLabel" :required="!settingNext || editingAction" :error="validation">
          <template #label><span class="action-label"><span>{{ actionLabel }}</span><span v-if="editingAction" class="editing-label">{{ action === 'EDIT' ? '更新原行動' : '複製為新行動' }}</span><el-button v-if="settingNext && editingAction" type="primary" link size="small" @click="cancelEdit">返回行動選擇</el-button></span></template>
          <ExplorationActionSelect v-if="settingNext && !editingAction" ref="actionField" :experiment-id="experimentId" :thread-id="targetThread" :method="selectedMethod" :text="tryDraft" :recent="state?.recentMethods" :favorites="favorites" :disabled="saving" allow-create @input="input" @select="choose" @toggle-favorite="toggleFavorite" />
          <template v-else><el-input v-model="tryDraft" type="textarea" :rows="4" maxlength="2000" aria-label="行動內容" /><FieldCounter :value="tryDraft" :limit="2000" /></template>
        </el-form-item>
        <div v-if="settingNext && selectedMethod && !editingAction" class="method-actions"><el-button type="primary" link size="small" @click="edit('EDIT')">更新</el-button><el-button type="primary" link size="small" @click="edit('COPY')">複製</el-button></div>
        <p v-if="action === 'COPY'" class="metadata">原行動保留；不複製發現、歷次整理或常用標記。</p>
        <el-alert v-if="action === 'EDIT' && selectedMethod?.discoveryCount" type="warning" :closable="false" :title="'下一步將整理此行動的 ' + selectedMethod.discoveryCount + ' 筆發現。確認更新後，整理文字會保存到探索線的歷次整理，原發現將從活動紀錄移除。'" />
      </template>
      <template v-else>
        <el-form-item label="發現" required><el-input ref="discoveryField" v-model="discovery" type="textarea" :rows="5" maxlength="10000" aria-label="發現內容" /><FieldCounter :value="discovery" :limit="10000" /></el-form-item>
      </template>
    </el-form>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <template #footer="{ requestClose }"><el-button :disabled="saving" @click="requestClose">取消</el-button><el-button v-if="error" type="primary" link :disabled="saving" @click="reload">重新載入資料</el-button><el-button type="primary" :loading="saving" :disabled="!canSave" @click="save">{{ submitLabel }}</el-button></template>
  </AppDialog>
  <ExplorationOrganizationDialog v-if="organizationStarted" :key="selectedMethod?.id" v-model="organizationVisible" :experiment-id="experimentId" :thread-id="selectedMethod?.threadId" :method="selectedMethod" mode="UPDATE_METHOD" return-to-editor :new-method-content="tryDraft" :expected-focus-version="settingNext ? state?.version : undefined" :focus-favorite-method-ids="settingNext && favoritesChanged ? favoriteIds : undefined" @return-to-editor="tryDraft = $event" @saved="organizationVisible = false; organizationStarted = false; finish()" />
</template>
<style scoped>
.method-actions { display: flex; flex-wrap: wrap; align-items: center; justify-content: flex-end; gap: var(--space-xs); margin-top: calc(-1 * var(--space-xs)); margin-bottom: var(--space-md); }
.action-label { display: flex; flex: 1; flex-wrap: wrap; align-items: center; gap: var(--space-xs); min-width: 0; }
.action-label .el-button { margin-left: auto; }
.editing-label { color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.discovery-context { background: var(--surface-summary); border-radius: var(--radius-md); padding: var(--space-sm); margin-bottom: var(--space-md); min-width: 0; }
.discovery-context summary { cursor: pointer; font-size: var(--type-ui); line-height: var(--leading-ui); }
.context-summary { display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; color: var(--el-text-color-secondary); font-size: var(--type-meta); margin: var(--space-2xs) 0 0 var(--space-md); overflow-wrap: anywhere; white-space: pre-wrap; }
.context-fields { margin-top: var(--space-sm); }
.context-fields :deep(.el-form-item:last-child) { margin-bottom: 0; }
.metadata { color: var(--el-text-color-secondary); font-size: var(--type-meta); margin-block: var(--space-xs) var(--space-md); }
.el-form :deep(.el-form-item) { margin-bottom: var(--space-md); }
.el-form :deep(.el-form-item__label) { display: flex; align-items: baseline; width: 100%; height: auto; margin-bottom: var(--space-xs); padding-bottom: 0; }
</style>
