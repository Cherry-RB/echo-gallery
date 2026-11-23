<script setup lang="ts">
import { ArrowDown, Filter, Grid, Search, Tickets } from '@element-plus/icons-vue'
import { computed, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'
import ExperimentRecordEditor from './ExperimentRecordEditor.vue'
import ExperimentRecordCard from './ExperimentRecordCard.vue'
import ExperimentRecordList from './ExperimentRecordList.vue'
import ExperimentRecordReader from './ExperimentRecordReader.vue'
import ExperimentTopicDirectory from './ExperimentTopicDirectory.vue'
import ExperimentTopicManagerDialog from './ExperimentTopicManagerDialog.vue'
import SectionHeader from '../ui/SectionHeader.vue'
import { experimentApi } from '../../utils/api/experimentApi'
import type { ExperimentRecordDto, ExperimentRecordHighlight, ExperimentRecordRequest, ExperimentTopicDto } from '../../types/experiment'

const props = defineProps<{ experimentId: number; desiredOutcome?: string | null }>()
const client = useQueryClient()
const page = ref(0), keyword = ref(''), keywordDraft = ref(''), sort = ref('recordedOn,DESC')
const editorVisible = ref(false), readerVisible = ref(false), topicManagerVisible = ref(false)
const editing = ref<ExperimentRecordDto | null>(null), reading = ref<ExperimentRecordDto | null>(null)
type RecordFilters = { understandingNode?: boolean; methodCandidate?: boolean; highlight?: ExperimentRecordHighlight; dateFrom?: string; dateTo?: string; topicIds?: number[]; topicMatch?: 'ANY' | 'ALL'; unclassified?: boolean }
const appliedFilters = ref<RecordFilters>({})
const pendingFilters = ref<RecordFilters>({})
const filtersOpen = ref(false)
const workspaceElement = ref<HTMLElement | null>(null)
const viewKey = `experiment-record-view:${props.experimentId}`
const view = ref<'grid' | 'list'>(localStorage.getItem(viewKey) === 'list' ? 'list' : 'grid')
const displayPage = computed(() => page.value + 1)
const returnToReader = ref(false)
let createAttempt: { requestId: string; payload: string } | null = null
const query = useQuery({ queryKey: computed(() => ['experiment-records', props.experimentId, page.value, keyword.value, sort.value, appliedFilters.value]), queryFn: () => experimentApi.getRecords(props.experimentId, page.value, 20, { keyword: keyword.value, sort: sort.value, ...appliedFilters.value }) })
const practices = useQuery({ queryKey: computed(() => ['experiment-practices', props.experimentId]), queryFn: () => experimentApi.getPinnedPractices(props.experimentId) })
const recentPractices = useQuery({ queryKey: computed(() => ['experiment-recent-practices', props.experimentId]), queryFn: () => experimentApi.getRecentPractices(props.experimentId) })
const topics = useQuery<ExperimentTopicDto[]>({ queryKey: computed(() => ['experiment-topics', props.experimentId]), queryFn: () => experimentApi.getTopics(props.experimentId) })
const activeTopicId = computed(() => appliedFilters.value.topicIds?.length === 1 ? appliedFilters.value.topicIds[0] : undefined)
const mobileTopicsVisible = ref(false)
const activeTopicLabel = computed(() => appliedFilters.value.unclassified ? '未分類' : activeTopicId.value != null ? topics.data.value?.find(topic => topic.id === activeTopicId.value)?.name ?? '已選主題' : appliedFilters.value.topicIds?.length ? `${appliedFilters.value.topicIds.length} 個主題` : '全部')
const filterSummary = computed(() => [
  appliedFilters.value.unclassified ? '未分類' : '',
  ...(appliedFilters.value.topicIds ?? []).map(id => topics.data.value?.find(topic => topic.id === id)?.name ?? '已選主題'),
  (appliedFilters.value.topicIds?.length ?? 0) > 1 ? appliedFilters.value.topicMatch === 'ALL' ? '符合全部主題' : '符合任一主題' : '',
  appliedFilters.value.understandingNode ? '理解節點' : '',
  appliedFilters.value.methodCandidate ? '方法備選' : '',
  appliedFilters.value.highlight === 'GAIN' ? '有所收穫' : appliedFilters.value.highlight === 'CHALLENGE_OVERCOME' ? '克服困難' : appliedFilters.value.highlight === 'PEAK_EXPERIENCE' ? '高峰體驗' : '',
  appliedFilters.value.dateFrom || appliedFilters.value.dateTo ? `日期：${appliedFilters.value.dateFrom ?? '不限'} 至 ${appliedFilters.value.dateTo ?? '不限'}` : '',
].filter(Boolean))
const invalidate = () => Promise.all([
  client.invalidateQueries({ queryKey: ['experiment-records', props.experimentId] }),
  client.invalidateQueries({ queryKey: ['experiment-recent-practices', props.experimentId] }),
  client.invalidateQueries({ queryKey: ['experiment', props.experimentId] }),
  client.invalidateQueries({ queryKey: ['experiments'] }),
  client.invalidateQueries({ queryKey: ['overview'] }),
])
const save = useMutation({
  mutationFn: (payload: ExperimentRecordRequest) => {
    if (editing.value) return experimentApi.updateRecord(props.experimentId, editing.value.id, payload)
    const serialized = JSON.stringify(payload)
    if (createAttempt && createAttempt.payload !== serialized) throw new Error('新增紀錄的內容已變更，請重新確認後再儲存。')
    createAttempt ??= { requestId: crypto.randomUUID(), payload: serialized }
    return experimentApi.createRecord(props.experimentId, { ...payload, requestId: createAttempt.requestId })
  },
  onSuccess: async record => { createAttempt = null; await invalidate(); editing.value = null; editorVisible.value = false; if (returnToReader.value) { reading.value = record; readerVisible.value = true; returnToReader.value = false }; ElMessage.success('已儲存探索紀錄。') },
  onError: error => { if (!editing.value && axios.isAxiosError(error) && [400, 422].includes(error.response?.status ?? 0)) createAttempt = null; ElMessage.error(error instanceof Error && error.message.startsWith('新增紀錄') ? error.message : '儲存失敗，請稍後再試。') },
})
const openCreate = () => { createAttempt = null; editing.value = null; editorVisible.value = true }
const repeat = (record: ExperimentRecordDto) => { createAttempt = null; editing.value = { ...record, id: -1, title: null, content: null, recordedOn: new Date().toISOString().slice(0, 10), version: 0 }; editorVisible.value = true }
const openEdit = (record: ExperimentRecordDto) => { editing.value = record; editorVisible.value = true }
const openRead = (record: ExperimentRecordDto) => { reading.value = record; readerVisible.value = true }
const editFromReader = () => { if (!reading.value) return; returnToReader.value = true; readerVisible.value = false; openEdit(reading.value) }
const handleEditorVisibility = (visible: boolean) => { editorVisible.value = visible; if (!visible && returnToReader.value && reading.value) { readerVisible.value = true; returnToReader.value = false } }
const cloneFilters = (filters: RecordFilters): RecordFilters => ({ ...filters, topicIds: filters.topicIds ? [...filters.topicIds] : undefined })
const applyFilters = () => { appliedFilters.value = cloneFilters(pendingFilters.value); page.value = 0; filtersOpen.value = false }
const clearPendingFilters = () => { pendingFilters.value = {} }
const clearAppliedFilters = () => { pendingFilters.value = {}; appliedFilters.value = {}; page.value = 0; filtersOpen.value = false }
const handlePendingTopics = (topicIds: number[]) => {
  pendingFilters.value.unclassified = undefined
  if (topicIds.length > 1 && !pendingFilters.value.topicMatch) pendingFilters.value.topicMatch = 'ANY'
  if (topicIds.length <= 1) pendingFilters.value.topicMatch = 'ANY'
}
const prepareFilters = () => {
  pendingFilters.value = cloneFilters(appliedFilters.value)
}
const applyDirectoryFilter = (filters: RecordFilters) => {
  appliedFilters.value = cloneFilters(filters)
  pendingFilters.value = cloneFilters(filters)
  page.value = 0
  filtersOpen.value = false
  mobileTopicsVisible.value = false
}
const selectTopic = (topicId: number) => applyDirectoryFilter({ topicIds: [topicId], topicMatch: 'ANY' })
const selectUnclassified = () => applyDirectoryFilter({ unclassified: true })
const selectAll = () => applyDirectoryFilter({})
const manageTopics = () => { mobileTopicsVisible.value = false; topicManagerVisible.value = true }
const applyKeyword = () => { keyword.value = keywordDraft.value.trim(); page.value = 0 }
const setView = (value: 'grid' | 'list') => { view.value = value; localStorage.setItem(viewKey, value) }
const changePage = (value: number) => {
  if (value === displayPage.value) return
  page.value = value - 1
  workspaceElement.value?.scrollIntoView({ block: 'start', behavior: 'smooth' })
}
const remove = async (record: ExperimentRecordDto) => {
  try { await ElMessageBox.confirm('刪除後無法復原；若已有作品引用，系統會阻擋刪除。', '刪除探索紀錄', { type: 'warning' }); await experimentApi.deleteRecord(props.experimentId, record.id, record.version); await invalidate() }
  catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error('無法刪除探索紀錄。') }
}
</script>

<template>
  <section ref="workspaceElement" class="workspace" aria-label="探索紀錄">
    <SectionHeader title="探索紀錄" :count="query.data.value?.totalElements" description="從任何想法、經驗或做法開始記下來。">
      <template #actions><el-button type="primary" @click="openCreate">新增探索紀錄</el-button></template>
    </SectionHeader>
    <div class="record-workspace">
      <aside class="topic-sidebar">
        <ExperimentTopicDirectory :topics="topics.data.value ?? []" :selected-ids="appliedFilters.topicIds" :unclassified="appliedFilters.unclassified" :loading="topics.isPending.value" :error="topics.isError.value" @all="selectAll" @unclassified="selectUnclassified" @select="selectTopic" @manage="manageTopics" @retry="topics.refetch()" />
      </aside>
      <div class="record-results">
        <div class="record-tools">
          <div class="mobile-topic-picker">
            <el-popover v-model:visible="mobileTopicsVisible" placement="bottom-start" :width="280" trigger="click">
              <template #reference><el-button class="mobile-topic-trigger">主題：<span>{{ activeTopicLabel }}</span><el-icon><ArrowDown /></el-icon></el-button></template>
              <ExperimentTopicDirectory :topics="topics.data.value ?? []" :selected-ids="appliedFilters.topicIds" :unclassified="appliedFilters.unclassified" :loading="topics.isPending.value" :error="topics.isError.value" @all="selectAll" @unclassified="selectUnclassified" @select="selectTopic" @manage="manageTopics" @retry="topics.refetch()" />
            </el-popover>
          </div>
          <el-input v-model="keywordDraft" clearable placeholder="搜尋標題、內容或做法" @keyup.enter="applyKeyword" @clear="applyKeyword"><template #prefix><Search /></template><template #append><el-button aria-label="搜尋" @click="applyKeyword">搜尋</el-button></template></el-input>
          <el-popover v-model:visible="filtersOpen" placement="bottom-start" :width="360" :teleported="false" trigger="click" @show="prepareFilters">
            <template #reference><el-button :icon="Filter" :type="filtersOpen ? 'primary' : 'default'" :aria-expanded="filtersOpen" aria-controls="experiment-record-filters">篩選</el-button></template>
            <section id="experiment-record-filters" class="filter-popover" aria-label="探索紀錄篩選條件">
              <div class="filter-field filter-topics">
                <span class="filter-label">主題</span>
                <el-select v-model="pendingFilters.topicIds" multiple filterable clearable collapse-tags :max-collapse-tags="3" :teleported="false" placeholder="選擇主題" @change="handlePendingTopics">
                  <el-option v-for="topic in topics.data.value" :key="topic.id" :label="topic.name" :value="topic.id" />
                </el-select>
                <el-radio-group v-if="pendingFilters.topicIds?.length && pendingFilters.topicIds.length > 1" v-model="pendingFilters.topicMatch" size="small" aria-label="主題符合方式">
                  <el-radio-button value="ANY">符合任一</el-radio-button>
                  <el-radio-button value="ALL">符合全部</el-radio-button>
                </el-radio-group>
                <el-checkbox v-model="pendingFilters.unclassified" :true-value="true" :false-value="undefined" @change="pendingFilters.topicIds = undefined; pendingFilters.topicMatch = undefined">未分類</el-checkbox>
              </div>
              <div class="filter-field">
                <span class="filter-label">用途</span>
                <div class="filter-inline"><el-checkbox v-model="pendingFilters.understandingNode" :true-value="true" :false-value="undefined">理解節點</el-checkbox><el-checkbox v-model="pendingFilters.methodCandidate" :true-value="true" :false-value="undefined">方法備選</el-checkbox></div>
              </div>
              <div class="filter-field">
                <span class="filter-label">本次亮點</span>
                <el-select v-model="pendingFilters.highlight" clearable :teleported="false" placeholder="全部"><el-option label="有所收穫" value="GAIN" /><el-option label="克服困難" value="CHALLENGE_OVERCOME" /><el-option label="高峰體驗" value="PEAK_EXPERIENCE" /></el-select>
              </div>
              <div class="filter-field filter-dates">
                <span class="filter-label">紀錄日期</span>
                <div class="filter-inline"><el-date-picker v-model="pendingFilters.dateFrom" type="date" value-format="YYYY-MM-DD" :teleported="false" placeholder="開始日期" /><span>—</span><el-date-picker v-model="pendingFilters.dateTo" type="date" value-format="YYYY-MM-DD" :teleported="false" placeholder="結束日期" /></div>
              </div>
              <div class="filter-actions"><el-button text @click="clearPendingFilters">清除</el-button><el-button type="primary" @click="applyFilters">套用條件</el-button></div>
            </section>
          </el-popover>
          <el-select v-model="sort" aria-label="排序" @change="page = 0"><el-option label="紀錄日期：新到舊" value="recordedOn,DESC" /><el-option label="紀錄日期：舊到新" value="recordedOn,ASC" /><el-option label="最近更新：新到舊" value="updatedAt,DESC" /></el-select>
          <el-button-group class="view-toggle" aria-label="紀錄顯示方式">
            <el-tooltip content="卡片視圖" placement="top"><el-button :type="view === 'grid' ? 'primary' : 'default'" :icon="Grid" aria-label="卡片視圖" :aria-pressed="view === 'grid'" @click="setView('grid')" /></el-tooltip>
            <el-tooltip content="列表視圖" placement="top"><el-button :type="view === 'list' ? 'primary' : 'default'" :icon="Tickets" aria-label="列表視圖" :aria-pressed="view === 'list'" @click="setView('list')" /></el-tooltip>
          </el-button-group>
        </div>
        <div v-if="keyword || filterSummary.length" class="filter-summary"><span v-if="keyword">搜尋：{{ keyword }}</span><span v-for="label in filterSummary" :key="label">{{ label }}</span><el-button text type="primary" @click="keyword = ''; keywordDraft = ''; clearAppliedFilters()">清除條件</el-button></div>
        <el-skeleton v-if="query.isLoading.value" :rows="5" animated />
        <el-result v-else-if="query.isError.value" icon="error" title="無法載入探索紀錄"><template #extra><el-button @click="query.refetch()">重新載入</el-button></template></el-result>
        <el-empty v-else-if="!query.data.value?.content.length" description="尚無符合條件的探索紀錄"><el-button type="primary" @click="openCreate">新增探索紀錄</el-button></el-empty>
        <ExperimentRecordList v-else-if="view === 'list'" :records="query.data.value.content" :selected-topic-id="activeTopicId" @read="openRead" @edit="openEdit" @repeat="repeat" @remove="remove" />
        <div v-else class="record-grid"><ExperimentRecordCard v-for="record in query.data.value.content" :key="record.id" :record="record" :selected-topic-id="activeTopicId" @read="openRead" @edit="openEdit" @repeat="repeat" @remove="remove" /></div>
      </div>
    </div>
    <el-pagination v-if="(query.data.value?.totalElements ?? 0) > (query.data.value?.size ?? 20)" :current-page="displayPage" :page-size="query.data.value?.size ?? 20" :total="query.data.value?.totalElements ?? 0" layout="prev, pager, next" class="record-pagination" @update:current-page="changePage" />
  </section>
  <ExperimentTopicManagerDialog v-model="topicManagerVisible" :experiment-id="experimentId" />
  <ExperimentRecordReader v-model="readerVisible" :record="reading" :experiment-id="experimentId" :direction="desiredOutcome" @edit="editFromReader" @changed="invalidate" />
  <ExperimentRecordEditor :model-value="editorVisible" :record="editing" :saving="save.isPending.value" :direction="desiredOutcome" :pinned-practices="practices.data.value?.map(item => item.content)" :recent-practices="recentPractices.data.value?.filter(practice => !practices.data.value?.some(pinned => pinned.content.trim() === practice.trim()))" :topic-options="topics.data.value ?? []" :topics-loading="topics.isPending.value" :topics-error="topics.isError.value" @update:model-value="handleEditorVisibility" @retry-topics="topics.refetch()" @save="save.mutate($event)" />
</template>

<style scoped>
.workspace, .record-results { min-width: 0; }
.record-results { container-type: inline-size; }
.record-workspace { --topic-sticky-top: var(--space-md); display: grid; grid-template-columns: 220px minmax(0, 1fr); align-items: start; gap: var(--space-lg); margin-top: var(--space-lg); }
.topic-sidebar { position: sticky; top: var(--topic-sticky-top); min-width: 0; padding: var(--space-md); border: 1px solid var(--el-border-color-light); border-radius: var(--radius-md); background: var(--el-bg-color); }
.record-tools { display: flex; align-items: center; flex-wrap: wrap; gap: var(--space-sm); }
.record-tools > :deep(.el-input) { flex: 1 1 260px; min-width: 0; max-width: 420px; }
.record-tools > :deep(.el-select) { width: 180px; }
.filter-popover { display: grid; gap: var(--space-md); }
.filter-field { display: grid; align-content: start; gap: var(--space-xs); min-width: 0; }
.filter-label { color: var(--el-text-color-regular); font-size: var(--type-meta); font-weight: var(--weight-semibold); line-height: var(--leading-ui); }
.filter-field > :deep(.el-select), .filter-field > :deep(.el-date-editor) { width: 100%; }
.filter-inline { display: flex; align-items: center; flex-wrap: wrap; gap: var(--space-xs) var(--space-sm); min-width: 0; }
.filter-inline :deep(.el-checkbox) { margin-right: 0; }
.filter-dates .filter-inline { align-items: stretch; flex-direction: column; }
.filter-dates .filter-inline > span { display: none; }
.filter-dates .filter-inline :deep(.el-date-editor) { width: 100%; }
.filter-actions { display: flex; justify-content: flex-end; gap: var(--space-xs); padding-top: var(--space-sm); border-top: 1px solid var(--el-border-color-lighter); }
.filter-summary { display: flex; align-items: center; flex-wrap: wrap; gap: var(--space-xs); margin-top: var(--space-sm); color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.filter-summary span { max-width: 100%; padding: var(--space-2xs) var(--space-xs); border-radius: var(--radius-sm); background: var(--el-fill-color-light); overflow-wrap: anywhere; }
.record-grid { display: grid; grid-template-columns: minmax(0, 1fr); gap: var(--space-md); margin-top: var(--space-md); }
.record-pagination { display: flex; justify-content: flex-end; margin-top: var(--space-lg); }
.mobile-topic-picker { display: none; min-width: 0; }
.mobile-topic-trigger { max-width: 100%; }
.mobile-topic-trigger :deep(span) { min-width: 0; }
.mobile-topic-trigger > :deep(span) { display: flex; gap: var(--space-xs); }
.mobile-topic-trigger :deep(span span) { overflow: hidden; text-overflow: ellipsis; }
@media (max-width: 1200px) { .record-workspace { --topic-sticky-top: 72px; } }
@media (max-height: 600px) {
  .record-workspace { grid-template-columns: minmax(0, 1fr); }
  .topic-sidebar { display: none; }
  .mobile-topic-picker { display: block; }
}
@media (max-width: 760px) {
  .record-workspace { display: block; margin-top: var(--space-md); }
  .topic-sidebar { display: none; }
  .mobile-topic-picker { display: block; }
  .record-tools { align-items: stretch; }
  .record-tools > :deep(.el-input) { flex-basis: 100%; width: 100%; max-width: none; }
  .record-tools > :deep(.el-select) { flex: 1; min-width: 160px; }
  .view-toggle :deep(.el-button) { flex: 1; }
  .record-pagination { justify-content: center; }
}
@container (min-width: 460px) { .record-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@container (min-width: 680px) { .record-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@container (min-width: 900px) { .record-grid { grid-template-columns: repeat(4, minmax(0, 1fr)); } }
</style>
