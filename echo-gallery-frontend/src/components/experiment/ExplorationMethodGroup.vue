<script setup lang="ts">
import { computed, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MoreFilled } from '@element-plus/icons-vue'
import type { ActivityRecord, MethodOption } from '../../types/explorationActivity'
import { explorationActivityApi as api } from '../../utils/api/explorationActivityApi'
import { apiErrorMessage } from '../../utils/api/errorMessage'
import { useExplorationInvalidation } from '../../composables/useExplorationInvalidation'
import ExpandableText from '../ExpandableText.vue'
import RecordCount from '../ui/RecordCount.vue'
import ExplorationRecordItem from './ExplorationRecordItem.vue'
import ExperimentExplorationEditor from './ExperimentExplorationEditor.vue'
import ExplorationOrganizationDialog from './ExplorationOrganizationDialog.vue'
const props = defineProps<{ experimentId: number; threadId: number; method: MethodOption | null; count: number; hasHistory: boolean; initialRecords: ActivityRecord[] }>()
const invalidate = useExplorationInvalidation()
const showAll = ref(false), page = ref(1), historyOpen = ref(false), error = ref('')
const editorOpen = ref(false), editingRecord = ref<ActivityRecord | null>(null)
const editorMode = ref<'TRY' | 'DISCOVERY'>('DISCOVERY'), intent = ref<'SELECT' | 'CREATE' | 'EDIT' | 'COPY' | 'ASSIGN'>('SELECT')
const organizationOpen = ref(false), organizationRecords = ref<ActivityRecord[]>([])
const query = useQuery({
  queryKey: computed(() => ['experiment-activity-records', props.experimentId, props.threadId, props.method?.id ?? 'unassigned', page.value]),
  queryFn: () => api.records(props.experimentId, { threadId: props.threadId, methodId: props.method?.id, unassigned: props.method == null, page: page.value - 1 }),
  enabled: showAll,
})
const history = useQuery({
  queryKey: computed(() => ['experiment-history', props.experimentId, props.threadId, props.method?.id]),
  queryFn: () => api.history(props.experimentId, props.threadId, props.method?.id ?? null),
  enabled: historyOpen,
})
const shown = computed(() => showAll.value ? query.data.value?.content ?? [] : props.initialRecords.slice(0, 3))
function editRecord(record: ActivityRecord, assign = false) { editingRecord.value = record; editorMode.value = 'DISCOVERY'; intent.value = assign ? 'ASSIGN' : 'EDIT'; editorOpen.value = true }
function methodAction(action: 'SELECT' | 'EDIT' | 'COPY') { editingRecord.value = null; editorMode.value = 'TRY'; intent.value = action; editorOpen.value = true }
function newFinding() { editingRecord.value = null; editorMode.value = 'DISCOVERY'; intent.value = 'CREATE'; editorOpen.value = true }
function organize(record?: ActivityRecord) { organizationRecords.value = record ? [record] : []; organizationOpen.value = true }
async function removeRecord(record: ActivityRecord) {
  try { await ElMessageBox.confirm('這筆發現會從上方與探索線同步刪除；已保存歷史、卡片及作品保留。', '刪除發現', { type: 'warning', confirmButtonText: '刪除', cancelButtonText: '取消' }) }
  catch { return }
  try { await api.deleteRecord(props.experimentId, record.id, record.version); await invalidate(props.experimentId) }
  catch (e) { error.value = apiErrorMessage(e, '刪除失敗，請重新確認資料版本。') }
}
async function removeMethod(deleteRecords = false) {
  if (!props.method) return
  try { await ElMessageBox.confirm(deleteRecords ? '將刪除行動與活動發現，歷次文字移入本探索線未指定行動歷史。' : '發現會留在本探索線並解除行動歸屬；歷次文字也會保留。', '移除行動', { type: 'warning', confirmButtonText: deleteRecords ? '刪除行動與活動發現' : '移除行動並保留發現', cancelButtonText: '取消' }) }
  catch { return }
  try { await api.deleteMethod(props.experimentId, props.method.id, props.method.version, deleteRecords); await invalidate(props.experimentId); ElMessage.success('行動已移除，歷史保留') }
  catch (e) { error.value = apiErrorMessage(e, '移除失敗，請重新確認資料版本。') }
}
</script>
<template>
  <article class="method-group">
    <header class="method-heading"><div class="method-content"><div class="heading-label"><span class="metadata">{{ method ? '行動' : '未指定行動' }}</span><RecordCount :count="count" aria-label="發現數量" /></div><p v-if="method">{{ method.content }}</p></div>
      <el-dropdown trigger="click"><el-button text circle size="small" :icon="MoreFilled" :aria-label="method ? '行動操作' : '未指定行動操作'" /><template #dropdown><el-dropdown-menu>
        <template v-if="method"><el-dropdown-item @click="methodAction('SELECT')">設為下一步</el-dropdown-item><el-dropdown-item @click="methodAction('EDIT')">更新行動</el-dropdown-item><el-dropdown-item @click="methodAction('COPY')">複製行動</el-dropdown-item></template>
        <el-dropdown-item @click="newFinding">記下發現</el-dropdown-item><el-dropdown-item @click="organize()">整理紀錄</el-dropdown-item>
        <template v-if="method"><el-dropdown-item divided @click="removeMethod()">移除行動並保留發現</el-dropdown-item><el-dropdown-item @click="removeMethod(true)">刪除行動與活動發現</el-dropdown-item></template>
      </el-dropdown-menu></template></el-dropdown>
    </header>
    <el-skeleton v-if="showAll && query.isPending.value" :rows="2" animated />
    <p v-else-if="showAll && query.isError.value">發現載入失敗。<el-button type="primary" link @click="query.refetch()">重試</el-button></p>
    <ExplorationRecordItem v-for="record in shown" :key="record.id" :record="record" @edit="editRecord(record)" @assign="editRecord(record, true)" @organize="organize(record)" @delete="removeRecord(record)" />
    <p v-if="!count" class="metadata">尚無活動發現。</p>
    <el-button v-if="!showAll && count > shown.length" type="primary" link size="small" @click="showAll = true">查看全部 {{ count }} 筆發現</el-button>
    <el-pagination v-if="showAll && (query.data.value?.totalPages ?? 0) > 1" v-model:current-page="page" :page-size="20" :total="query.data.value?.totalElements ?? 0" layout="prev, pager, next" />
    <div v-if="hasHistory" class="history"><el-button type="primary" link size="small" :aria-expanded="historyOpen" @click="historyOpen = !historyOpen">{{ historyOpen ? '收合歷次整理' : '展開歷次整理' }}</el-button>
      <template v-if="historyOpen"><el-skeleton v-if="history.isPending.value" :rows="2" /><p v-else-if="history.isError.value">歷史載入失敗。<el-button type="primary" link @click="history.refetch()">重試</el-button></p><ExpandableText v-else :content="history.data.value?.text ?? ''" :lines="8" /></template>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
  </article>
  <ExperimentExplorationEditor v-if="editorOpen" v-model="editorOpen" :experiment-id="experimentId" :thread-id="threadId" :mode="editorMode" :method="editingRecord ? undefined : method" :record="editingRecord" :intent="intent" />
  <ExplorationOrganizationDialog v-if="organizationOpen" v-model="organizationOpen" :experiment-id="experimentId" :thread-id="threadId" :method="organizationRecords.length ? undefined : method" :unassigned-only="!method" :initial-records="organizationRecords" />
</template>
<style scoped>
.method-group { min-width: 0; padding-block: var(--space-sm); }
.method-group + .method-group { border-top: 1px solid var(--el-border-color-light); }
.method-heading, .heading-label { display: flex; align-items: flex-start; gap: var(--space-xs); }
.method-heading { justify-content: space-between; } .method-content { flex: 1; min-width: 0; overflow-wrap: anywhere; }
.method-content p { margin: var(--space-xs) 0 0; white-space: pre-wrap; }
.metadata { color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.history { margin-top: var(--space-sm); }
</style>
