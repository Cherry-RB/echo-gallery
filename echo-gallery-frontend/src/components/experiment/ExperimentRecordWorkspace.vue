<script setup lang="ts">
import { computed, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'
import ExperimentRecordEditor from './ExperimentRecordEditor.vue'
import ExperimentRecordCard from './ExperimentRecordCard.vue'
import ExperimentRecordReader from './ExperimentRecordReader.vue'
import { experimentApi } from '../../utils/api/experimentApi'
import type { ExperimentRecordDto, ExperimentRecordHighlight, ExperimentRecordRequest, ExperimentTopicDto } from '../../types/experiment'

const props = defineProps<{ experimentId: number; desiredOutcome?: string | null }>()
const client = useQueryClient()
const page = ref(0), keyword = ref(''), sort = ref('recordedOn,DESC'), editorVisible = ref(false), readerVisible = ref(false), editing = ref<ExperimentRecordDto | null>(null), reading = ref<ExperimentRecordDto | null>(null)
type RecordFilters = { understandingNode?: boolean; methodCandidate?: boolean; highlight?: ExperimentRecordHighlight; dateFrom?: string; dateTo?: string; topicIds?: number[]; topicMatch?: 'ANY' | 'ALL'; unclassified?: boolean }
const appliedFilters = ref<RecordFilters>({})
const pendingFilters = ref<RecordFilters>({})
const viewKey = `experiment-record-view:${props.experimentId}`
const view = ref<'grid' | 'list'>(localStorage.getItem(viewKey) === 'list' ? 'list' : 'grid')
const displayPage = computed({ get: () => page.value + 1, set: (value: number) => { page.value = value - 1 } })
const returnToReader = ref(false)
let createAttempt: { requestId: string; payload: string } | null = null
const query = useQuery({ queryKey: computed(() => ['experiment-records', props.experimentId, page.value, keyword.value, sort.value, appliedFilters.value]), queryFn: () => experimentApi.getRecords(props.experimentId, page.value, 20, { keyword: keyword.value, sort: sort.value, ...appliedFilters.value }) })
const practices = useQuery({ queryKey: computed(() => ['experiment-practices', props.experimentId]), queryFn: () => experimentApi.getPinnedPractices(props.experimentId) })
const recentPractices = useQuery({ queryKey: computed(() => ['experiment-recent-practices', props.experimentId]), queryFn: () => experimentApi.getRecentPractices(props.experimentId) })
const topics = useQuery<ExperimentTopicDto[]>({ queryKey: computed(() => ['experiment-topics', props.experimentId]), queryFn: () => experimentApi.getTopics(props.experimentId) })
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
    if (createAttempt && createAttempt.payload !== serialized) {
      throw new Error('前次儲存結果尚未確認。請先恢復原內容重試，確認後再編輯紀錄。')
    }
    createAttempt ??= { requestId: crypto.randomUUID(), payload: serialized }
    return experimentApi.createRecord(props.experimentId, { ...payload, requestId: createAttempt.requestId })
  },
  onSuccess: async (record) => { createAttempt = null; await invalidate(); editing.value = null; editorVisible.value = false; if (returnToReader.value) { reading.value = record; readerVisible.value = true; returnToReader.value = false }; ElMessage.success('探索紀錄已儲存') },
  onError: (error) => {
    if (!editing.value && axios.isAxiosError(error) && [400, 422].includes(error.response?.status ?? 0)) createAttempt = null
    ElMessage.error(error instanceof Error && error.message.startsWith('前次儲存結果') ? error.message : '儲存失敗；輸入內容仍保留，請稍後重試。')
  },
})
const openCreate = () => { createAttempt = null; editing.value = null; editorVisible.value = true }
const repeat = (record: ExperimentRecordDto) => {
  createAttempt = null
  editing.value = { ...record, id: -1, title: null, content: null, recordedOn: new Date().toISOString().slice(0, 10), version: 0 }
  editorVisible.value = true
}
const openEdit = (record: ExperimentRecordDto) => { editing.value = record; editorVisible.value = true }
const openRead = (record: ExperimentRecordDto) => { reading.value = record; readerVisible.value = true }
const editFromReader = () => { if (!reading.value) return; returnToReader.value = true; readerVisible.value = false; openEdit(reading.value) }
const handleEditorVisibility = (visible: boolean) => {
  editorVisible.value = visible
  if (!visible && returnToReader.value && reading.value) { readerVisible.value = true; returnToReader.value = false }
}
const applyFilters = () => { appliedFilters.value = { ...pendingFilters.value }; page.value = 0 }
const resetFilters = () => { pendingFilters.value = {}; appliedFilters.value = {}; page.value = 0 }
const selectTopic = (topicId: number | undefined) => { pendingFilters.value = { ...pendingFilters.value, topicIds: topicId == null ? undefined : [topicId], topicMatch: 'ANY', unclassified: undefined }; applyFilters() }
const selectUnclassified = () => { pendingFilters.value = { ...pendingFilters.value, topicIds: undefined, topicMatch: undefined, unclassified: true }; applyFilters() }
const setView = (value: 'grid' | 'list') => { view.value = value; localStorage.setItem(viewKey, value) }
const remove = async (record: ExperimentRecordDto) => {
  try { await ElMessageBox.confirm('刪除後無法復原；若已有作品引用，系統會阻擋刪除。', '刪除探索紀錄', { type: 'warning' }); await experimentApi.deleteRecord(props.experimentId, record.id, record.version); await invalidate() }
  catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error('無法刪除探索紀錄') }
}
</script>

<template>
  <section class="workspace" aria-label="探索紀錄">
    <header><div><h2>探索紀錄 <span v-if="query.data.value" class="record-count">{{ query.data.value.totalElements }}</span></h2><p>從任何想法、經驗或做法開始記下來。</p></div><el-button type="primary" @click="openCreate">新增探索紀錄</el-button></header>
    <div class="record-tools">
      <el-button-group v-if="topics.data.value?.length" class="topic-filter" aria-label="主題篩選">
        <el-button :type="!appliedFilters.topicIds?.length && !appliedFilters.unclassified ? 'primary' : 'default'" @click="selectTopic(undefined)">全部</el-button>
        <el-button v-for="topic in topics.data.value" :key="topic.id" :type="appliedFilters.topicIds?.length === 1 && appliedFilters.topicIds[0] === topic.id ? 'primary' : 'default'" @click="selectTopic(topic.id)">{{ topic.name }}</el-button>
        <el-button :type="appliedFilters.unclassified ? 'primary' : 'default'" @click="selectUnclassified">未分類</el-button>
      </el-button-group>
      <el-popover placement="bottom-start" :width="300" trigger="click">
        <template #reference><el-button>篩選</el-button></template>
        <div class="filter-popover">
          <el-checkbox v-model="pendingFilters.understandingNode" :true-value="true" :false-value="undefined">理解節點</el-checkbox>
          <el-checkbox v-model="pendingFilters.methodCandidate" :true-value="true" :false-value="undefined">方法備選</el-checkbox>
          <el-select v-model="pendingFilters.highlight" clearable placeholder="本次亮點"><el-option label="有所收穫" value="GAIN" /><el-option label="克服困難" value="CHALLENGE_OVERCOME" /><el-option label="高峰體驗" value="PEAK_EXPERIENCE" /></el-select>
          <el-date-picker v-model="pendingFilters.dateFrom" type="date" value-format="YYYY-MM-DD" placeholder="起始日期" />
          <el-date-picker v-model="pendingFilters.dateTo" type="date" value-format="YYYY-MM-DD" placeholder="結束日期" />
          <el-select v-model="pendingFilters.topicIds" multiple clearable collapse-tags placeholder="選擇主題" @change="pendingFilters.unclassified = undefined"><el-option v-for="topic in topics.data.value" :key="topic.id" :label="topic.name" :value="topic.id" /></el-select>
          <el-radio-group v-if="pendingFilters.topicIds?.length && pendingFilters.topicIds.length > 1" v-model="pendingFilters.topicMatch" aria-label="主題符合方式"><el-radio value="ANY">符合任一主題</el-radio><el-radio value="ALL">同時符合全部</el-radio></el-radio-group>
          <el-checkbox v-model="pendingFilters.unclassified" :true-value="true" :false-value="undefined" @change="pendingFilters.topicIds = undefined; pendingFilters.topicMatch = undefined">未分類</el-checkbox>
          <div><el-button text @click="resetFilters">清除</el-button><el-button type="primary" @click="applyFilters">套用</el-button></div>
        </div>
      </el-popover>
      <el-button-group class="view-toggle"><el-button :type="view === 'grid' ? 'primary' : 'default'" @click="setView('grid')">卡片</el-button><el-button :type="view === 'list' ? 'primary' : 'default'" @click="setView('list')">列表</el-button></el-button-group>
      <el-input v-model="keyword" clearable placeholder="搜尋標題、內容或做法" @clear="page = 0" @input="page = 0" />
      <el-select v-model="sort" aria-label="排序" @change="page = 0"><el-option label="紀錄日期：新到舊" value="recordedOn,DESC" /><el-option label="紀錄日期：舊到新" value="recordedOn,ASC" /><el-option label="最後更新：新到舊" value="updatedAt,DESC" /></el-select>
    </div>
    <el-skeleton v-if="query.isLoading.value" :rows="5" animated />
    <el-result v-else-if="query.isError.value" icon="error" title="無法載入探索紀錄"><template #extra><el-button @click="query.refetch()">重新載入</el-button></template></el-result>
    <el-empty v-else-if="!query.data.value?.content.length" description="還沒有探索紀錄"><el-button type="primary" @click="openCreate">新增第一筆</el-button></el-empty>
    <div v-else :class="['record-grid', { 'record-list': view === 'list' }]">
      <ExperimentRecordCard v-for="record in query.data.value.content" :key="record.id" :record="record" @read="openRead" @edit="openEdit" @repeat="repeat" @remove="remove" />
    </div>
  </section>
  <el-pagination
    v-if="(query.data.value?.totalElements ?? 0) > (query.data.value?.size ?? 20)"
    v-model:current-page="displayPage"
    :page-size="query.data.value?.size ?? 20"
    :total="query.data.value?.totalElements ?? 0"
    layout="prev, pager, next"
    class="record-pagination"
  />
  <ExperimentRecordReader v-model="readerVisible" :record="reading" :experiment-id="experimentId" @edit="editFromReader" />
  <ExperimentRecordEditor :model-value="editorVisible" :record="editing" :saving="save.isPending.value" :direction="desiredOutcome" :pinned-practices="practices.data.value?.map(item => item.content)" :recent-practices="recentPractices.data.value?.filter(practice => !practices.data.value?.some(pinned => pinned.content.trim() === practice.trim()))" @update:model-value="handleEditorVisibility" @save="save.mutate($event)" />
</template>

<style scoped>
.workspace { padding: 0; }.workspace > header { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--space-sm); } h2, p { margin: 0; } .record-count { display: inline-grid; min-width: 1.5em; place-items: center; padding: 0 var(--space-xs); border-radius: 999px; background: var(--el-fill-color-light); color: var(--el-text-color-secondary); font-size: var(--type-caption); font-weight: var(--weight-medium); vertical-align: middle; } header p { color: var(--el-text-color-secondary); font-size: var(--type-secondary); } .record-tools { display: flex; gap: var(--space-sm); margin-top: var(--space-md); }.record-tools :deep(.el-input) { max-width: 320px; }.record-tools :deep(.el-select) { width: 180px; }.record-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: var(--space-md); margin-top: var(--space-md); } @media (max-width: 640px) { .workspace > header, .record-tools { flex-direction: column; } header :deep(.el-button), .record-tools :deep(.el-input), .record-tools :deep(.el-select) { width: 100%; max-width: none; } .record-grid { grid-template-columns: 1fr; } }
</style>

<style scoped>
.record-pagination { margin-top: var(--space-lg); }
.record-tools { flex-wrap: wrap; }
.filter-popover { display: grid; gap: var(--space-sm); }
.filter-popover > div:last-child { display: flex; justify-content: flex-end; gap: var(--space-xs); }
.record-grid.record-list { grid-template-columns: 1fr; }
.record-grid.record-list :deep(.record-card) { gap: var(--space-xs); }
@media (max-width: 640px) { .view-toggle { width: 100%; } .view-toggle :deep(.el-button) { flex: 1; } }
</style>
