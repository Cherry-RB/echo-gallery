<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { ElMessageBox } from 'element-plus'
import type { ExperimentExplorationRecordDto } from '../../types/experiment'
import type { ActivityRecord } from '../../types/explorationActivity'
import { explorationActivityApi as api } from '../../utils/api/explorationActivityApi'
import { apiErrorMessage } from '../../utils/api/errorMessage'
import { useExplorationInvalidation } from '../../composables/useExplorationInvalidation'
import HistoryDialog from '../ui/HistoryDialog.vue'
import HistoryTimeline from '../ui/HistoryTimeline.vue'
import RecordCount from '../ui/RecordCount.vue'
import ExplorationRecordItem from './ExplorationRecordItem.vue'
import ExperimentExplorationEditor from './ExperimentExplorationEditor.vue'
import ExplorationOrganizationDialog from './ExplorationOrganizationDialog.vue'
import ExperimentQuickRecordAction from './ExperimentQuickRecordAction.vue'
const props = withDefaults(defineProps<{ modelValue: boolean; experimentId?: number; records: ExperimentExplorationRecordDto[]; manageable?: boolean; showOrganize?: boolean; loading?: boolean }>(), { manageable: true, showOrganize: true, loading: false })
const emit = defineEmits<{
  'update:modelValue': [value: boolean]; edit: [record: ExperimentExplorationRecordDto]; delete: [recordId: number];
  organize: []; 'add-thread': [recordId?: number]; 'open-card': [cardId: number]; record: ['TRY' | 'DISCOVERY']
}>()
const invalidate = useExplorationInvalidation()
const page = ref(1), editorVisible = ref(false), organizationVisible = ref(false), error = ref('')
const editing = ref<ActivityRecord | null>(null), initial = ref<ActivityRecord[]>([])
const mode = ref<'TRY' | 'DISCOVERY'>('DISCOVERY'), intent = ref<'SELECT' | 'EDIT' | 'ASSIGN'>('SELECT')
const historyContent = ref<HTMLElement>()
let scrollPosition = 0, returnFocus = ''
const query = useQuery({
  queryKey: computed(() => ['experiment-activity-records', props.experimentId, 'history', page.value]),
  queryFn: () => api.records(props.experimentId!, { page: page.value - 1 }),
  enabled: computed(() => props.modelValue && props.experimentId != null),
})
const shown = computed(() => props.experimentId != null ? query.data.value?.content ?? [] : props.records as ActivityRecord[])
const total = computed(() => props.experimentId != null ? query.data.value?.totalElements : props.records.length)
watch(() => props.experimentId, () => { page.value = 1; scrollPosition = 0; returnFocus = '' })
watch(() => query.data.value, data => {
  if (data && page.value > Math.max(1, data.totalPages)) page.value = Math.max(1, data.totalPages)
})
function rememberPosition(selector: string) {
  scrollPosition = historyContent.value?.closest<HTMLElement>('.el-dialog__body')?.scrollTop ?? 0
  returnFocus = selector
}
async function restorePosition() {
  await nextTick()
  if (!props.modelValue || editorVisible.value || organizationVisible.value) return
  const body = historyContent.value?.closest<HTMLElement>('.el-dialog__body')
  if (body) body.scrollTop = scrollPosition
  if (returnFocus) historyContent.value?.closest('.el-dialog')?.querySelector<HTMLElement>(returnFocus)?.focus({ preventScroll: true })
}
function openEditor(record: ActivityRecord, assign = false) {
  rememberPosition(`[data-record-id="${record.id}"] [aria-label="發現操作"]`)
  editing.value = record; mode.value = 'DISCOVERY'; intent.value = assign ? 'ASSIGN' : 'EDIT'; editorVisible.value = true
}
function quick(value: 'TRY' | 'DISCOVERY') {
  rememberPosition('.quick-record-trigger')
  editing.value = null; mode.value = value; intent.value = 'SELECT'; editorVisible.value = true
}
function organize(record?: ActivityRecord) {
  rememberPosition(record ? `[data-record-id="${record.id}"] [aria-label="發現操作"]` : '.organize-history')
  initial.value = record ? [record] : []; organizationVisible.value = true
}
async function remove(record: ActivityRecord) {
  if (props.experimentId == null) return
  try { await ElMessageBox.confirm('將從上方與探索線同步刪除這筆發現；歷次整理、卡片與作品保留。', '刪除發現', { type: 'warning', confirmButtonText: '刪除', cancelButtonText: '取消' }) }
  catch { return }
  try { await api.deleteRecord(props.experimentId, record.id, record.version); await invalidate(props.experimentId) }
  catch (e) { error.value = apiErrorMessage(e, '無法刪除，請重新確認資料版本。') }
}
</script>
<template>
  <!-- 子流程期間卸下歷程外殼，避免關閉動畫把畫面切換誤當成整個歷程關閉。 -->
  <HistoryDialog v-if="!editorVisible && !organizationVisible" :model-value="modelValue" title="探索紀錄" description="查看各探索線的發現，保留行動與觀察的脈絡。" @opened="restorePosition" @update:model-value="emit('update:modelValue', $event)">
    <template #actions><span v-if="total != null" class="history-total">共 <RecordCount :count="total" /> 筆發現</span><ExperimentQuickRecordAction v-if="manageable && experimentId != null" @select="quick" /></template>
    <div ref="historyContent" class="history-content">
      <el-skeleton v-if="loading || (experimentId != null && query.isPending.value)" :rows="3" animated />
      <p v-else-if="experimentId != null && query.isError.value">紀錄載入失敗。<el-button type="primary" link @click="query.refetch()">重試</el-button></p>
      <template v-else><HistoryTimeline v-if="shown.length" class="exploration-history-list"><li v-for="item in shown" :key="item.id" class="exploration-history-entry" :data-record-id="item.id"><ExplorationRecordItem :record="item" show-method :readonly="!manageable || experimentId == null" @edit="openEditor(item)" @assign="openEditor(item, true)" @organize="organize(item)" @delete="remove(item)" /></li></HistoryTimeline><p v-else class="metadata">尚無活動探索紀錄。</p></template>
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
    </div>
    <template #footer><div class="history-footer"><el-pagination v-if="(query.data.value?.totalPages ?? 0) > 1" v-model:current-page="page" :page-size="20" :total="query.data.value?.totalElements ?? 0" layout="prev, pager, next" /><div><el-button @click="emit('update:modelValue', false)">關閉</el-button><el-button v-if="showOrganize && manageable && experimentId != null" class="organize-history" type="primary" @click="organize()">整理內容</el-button></div></div></template>
  </HistoryDialog>
  <ExperimentExplorationEditor v-if="editorVisible && experimentId != null" v-model="editorVisible" :experiment-id="experimentId" :mode="mode" :record="editing" :intent="intent" />
  <ExplorationOrganizationDialog v-if="organizationVisible && experimentId != null" v-model="organizationVisible" :experiment-id="experimentId" :initial-records="initial" />
</template>
<style scoped>
.history-footer { display: flex; flex-wrap: wrap; justify-content: space-between; gap: var(--space-sm); width: 100%; }
.history-footer > div { margin-left: auto; }
.history-total { display: inline-flex; align-items: center; gap: var(--space-xs); margin-right: var(--space-md); color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.history-content { min-width: 0; }
.exploration-history-list { display: grid; gap: var(--space-sm); padding: 0; }
.exploration-history-entry { min-width: 0; border: 1px solid var(--el-border-color-lighter); border-radius: var(--radius-md); background: var(--surface-summary); }
.exploration-history-entry :deep(.finding-row) { padding: var(--space-md); }
.metadata { color: var(--el-text-color-secondary); }
</style>
