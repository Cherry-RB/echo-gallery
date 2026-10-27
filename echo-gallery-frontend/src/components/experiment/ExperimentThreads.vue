<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useInfiniteQuery, useQuery, useQueryClient } from '@tanstack/vue-query'
import { Delete, Edit, Plus, MoreFilled, ArrowDown, ArrowUp } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { CardDto } from '../../types/card'
import type {
  ExplorationThreadDto,
  ExplorationThreadSummaryDto,
  ExplorationThreadCardRole,
  ExplorationThreadMethodDto,
  ExplorationThreadStatus,
  ExperimentExplorationRecordDto,
  ExplorationThreadCardDto,
} from '../../types/experiment'
import type { WorkSummary } from '../../types/work'
import { experimentApi } from '../../utils/api/experimentApi'
import { workApi } from '../../utils/api/workApi'
import { formatDate } from '../../utils/formatDate'
import AppDialog from '../AppDialog.vue'
import CardPickerDialog from '../CardPickerDialog.vue'
import ExpandableText from '../ExpandableText.vue'
import SourceReadingDialog, { type SourceReadingItem } from '../SourceReadingDialog.vue'
import SectionHeader from '../ui/SectionHeader.vue'
import ThreadCardPreview from './ThreadCardPreview.vue'
import { workStages, type WorkStage } from '../../types/work'
import ExperimentExplorationEditor from './ExperimentExplorationEditor.vue'
import RecordHistoryTrigger from '../ui/RecordHistoryTrigger.vue'
import RecordCount from '../ui/RecordCount.vue'
import PickerOption from '../ui/PickerOption.vue'
import ExplorationThreadActivity from './ExplorationThreadActivity.vue'
import ExplorationOrganizationDialog from './ExplorationOrganizationDialog.vue'
import type { ActivityRecord } from '../../types/explorationActivity'
import FieldCounter from '../ui/FieldCounter.vue'
import { apiErrorMessage } from '../../utils/api/errorMessage'
import { useWorkDialogGuard } from '../../composables/useWorkDialogGuard'

const props = withDefaults(defineProps<{
  experimentId: number
  explorationRecords?: ExperimentExplorationRecordDto[]
  dialogOnly?: boolean
  initialThreadId?: number
}>(), { explorationRecords: () => [], dialogOnly: false })
const emit = defineEmits<{ 'import-closed': []; closed: [] }>()

const queryClient = useQueryClient()
const route = useRoute()
const editorShell = ref<InstanceType<typeof AppDialog>>()
const noteShell = ref<InstanceType<typeof AppDialog>>()
const workShell = ref<InstanceType<typeof AppDialog>>()
const organizationVisible = ref(false)
const organizationThreadId = ref<number>()
const organizationRecords = ref<ActivityRecord[]>([])
const detailVisible = ref(false)
const detailThreadId = ref<number | null>(null)
useWorkDialogGuard(editorShell, () => editorVisible.value)
useWorkDialogGuard(noteShell, () => noteVisible.value)
useWorkDialogGuard(workShell, () => workVisible.value)
const threadPage = ref(1)
const expandedThreadIds = ref(new Set<number>())
const initializedThreadPages = ref(new Set<number>())
const summariesQuery = useQuery({
  queryKey: computed(() => ['experiment-thread-summaries', props.experimentId, threadPage.value]),
  queryFn: () => experimentApi.getThreadSummaries(props.experimentId, threadPage.value - 1, 20),
  enabled: computed(() => !props.dialogOnly),
})
const summaryThreads = computed(() => summariesQuery.data.value?.content ?? [])
const summaryTotal = computed(() => summariesQuery.data.value?.totalElements ?? 0)
const detailQuery = useQuery({
  queryKey: computed(() => ['experiment-thread', props.experimentId, detailThreadId.value]),
  queryFn: () => experimentApi.getThread(props.experimentId, detailThreadId.value!),
  enabled: computed(() => detailVisible.value && detailThreadId.value !== null),
})
const busy = ref(false)
const editorVisible = ref(false)
const editingThread = ref<Pick<ExplorationThreadDto, 'id' | 'version' | 'systemKey'> | null>(null)
const threadForm = reactive<{ name: string; status: ExplorationThreadStatus; conclusion: string }>({
  name: '', status: 'NOT_STARTED', conclusion: '',
})
const cardPickerVisible = ref(false)
const cardThread = ref<ExplorationThreadDto | null>(null)
const cardRole = ref<ExplorationThreadCardRole>('MATERIAL')
const workVisible = ref(false)
const workThread = ref<ExplorationThreadDto | null>(null)
const selectedWorkId = ref<number | null>(null)
const detailFocus = ref<'MATERIALS' | 'RECORDS' | 'OUTCOMES'>('MATERIALS')
const readingCard = ref<SourceReadingItem | null>(null)
const draggingThreadId = ref<number | null>(null)
const dropAfter = ref(false)
const dropTarget = ref<number | null>(null)
const rawEditorVisible = ref(false)
const rawEditorMode = ref<'TRY' | 'DISCOVERY'>('DISCOVERY')
const rawEditorThreadId = ref<number>()
const rawEditorIntent = ref<'SELECT' | 'CREATE'>('CREATE')
const rawEditorMethod = ref<ExplorationThreadMethodDto | null>(null)
const noteVisible = ref(false)
const noteCard = ref<ExplorationThreadCardDto>()
const noteThread = ref<ExplorationThreadDto>()
const noteDraft = ref('')
const flowError = ref('')
const workKeyword = ref('')
const workSearch = ref('')
const scrollPosition = ref(0)
const detailBody = ref<HTMLElement>()
const materialSection = ref<HTMLElement>()
const recordSection = ref<HTMLElement>()
const outcomeSection = ref<HTMLElement>()

const worksQuery = useInfiniteQuery({
  queryKey: computed(() => ['works', 'thread-picker', workSearch.value]),
  queryFn: ({ pageParam }) => workApi.list({ archived: false, keyword: workSearch.value, page: pageParam, size: 20 }),
  initialPageParam: 0,
  getNextPageParam: page => page.page + 1 < page.totalPages ? page.page + 1 : undefined,
  enabled: workVisible,
})
const works = computed(() => worksQuery.data.value?.pages.flatMap(page => page.content) ?? [])
const statusOptions: Array<{ value: ExplorationThreadStatus; label: string }> = [
  { value: 'NOT_STARTED', label: '未開始' },
  { value: 'IN_PROGRESS', label: '進行中' },
  { value: 'PAUSED', label: '暫放' },
  { value: 'CONCLUDED', label: '已收束' },
]

const statusLabel = (status: ExplorationThreadStatus) => statusOptions.find(option => option.value === status)?.label ?? status
const detailThread = computed(() => detailQuery.data.value ?? null)
const materialCards = (thread: ExplorationThreadDto) => thread.cards.filter(card => card.role === 'MATERIAL')
const resultCards = (thread: ExplorationThreadDto) => thread.cards.filter(card => card.role === 'RESULT')
const isChildOpen = computed(() => editorVisible.value || organizationVisible.value || cardPickerVisible.value || workVisible.value || noteVisible.value || rawEditorVisible.value)
const toSourceReadingItem = (card: { cardId: number; cardTitle: string; cardArchived: boolean }): SourceReadingItem => ({
  id: null,
  cardId: card.cardId,
  recordId: null,
  title: card.cardTitle,
  text: '',
  experimentId: props.experimentId,
  experimentTitle: null,
  archived: card.cardArchived,
})
const openThreadDetail = (thread: Pick<ExplorationThreadDto, 'id'> | Pick<ExplorationThreadSummaryDto, 'id'>, focus: 'MATERIALS' | 'RECORDS' | 'OUTCOMES' = 'MATERIALS') => {
  detailThreadId.value = thread.id
  detailFocus.value = focus
  detailVisible.value = true
  scrollPosition.value = 0
}
const restoreDetail = () => {
  const body = detailBody.value?.closest('.el-dialog__body')
  if (body) body.scrollTop = scrollPosition.value
  if (scrollPosition.value === 0) {
    const section = detailFocus.value === 'RECORDS' ? recordSection.value : detailFocus.value === 'OUTCOMES' ? outcomeSection.value : materialSection.value
    section?.scrollIntoView({ block: 'nearest' })
  }
}
const rememberDetail = () => { scrollPosition.value = detailBody.value?.closest('.el-dialog__body')?.scrollTop ?? scrollPosition.value }
watch(isChildOpen, open => { if (open) rememberDetail() }, { flush: 'sync' })
const readCard = (card: ExplorationThreadCardDto) => { rememberDetail(); readingCard.value = toSourceReadingItem(card) }
const readSummaryMaterial = (card: ExplorationThreadSummaryDto['recentMaterials'][number]) => {
  readingCard.value = { id: null, cardId: card.cardId, recordId: null, title: card.title, text: '', experimentId: props.experimentId, experimentTitle: null, archived: false }
}
let handledNavigation = ''
watch(() => props.initialThreadId, id => {
  if (props.dialogOnly && id) openThreadDetail({ id })
}, { immediate: true })
watch(() => route.fullPath, async () => {
  if (props.dialogOnly) return
  if (handledNavigation === route.fullPath) return
  handledNavigation = route.fullPath
  if (route.query.thread) {
    openThreadDetail({ id: Number(route.query.thread) })
  }
  if (route.query.importRecord) { await nextTick(); openImport(route.query.importRecord === 'all' ? undefined : [Number(route.query.importRecord)]) }
}, { immediate: true })
watch(summariesQuery.data, page => {
  if (!page || initializedThreadPages.value.has(page.page)) return
  const next = new Set(expandedThreadIds.value)
  page.content.slice(0, 5).forEach(thread => next.add(thread.id))
  expandedThreadIds.value = next
  initializedThreadPages.value.add(page.page)
})
const isExpanded = (thread: ExplorationThreadSummaryDto) => expandedThreadIds.value.has(thread.id)
const toggleExpanded = (thread: ExplorationThreadSummaryDto) => {
  const next = new Set(expandedThreadIds.value)
  if (next.has(thread.id)) next.delete(thread.id)
  else next.add(thread.id)
  expandedThreadIds.value = next
}
const replaceThread = (thread: ExplorationThreadDto) => {
  queryClient.setQueryData(['experiment-thread', props.experimentId, thread.id], thread)
  queryClient.invalidateQueries({ queryKey: ['experiment-thread-summaries', props.experimentId] })
  void queryClient.invalidateQueries({ queryKey: ['experiment', props.experimentId] })
  void queryClient.invalidateQueries({ queryKey: ['experiments'] })
  void queryClient.invalidateQueries({ queryKey: ['overview'] })
}
const refreshRelated = () => Promise.all([
  queryClient.invalidateQueries({ queryKey: ['experiment-threads', props.experimentId] }),
  queryClient.invalidateQueries({ queryKey: ['experiment-thread-summaries', props.experimentId] }),
  queryClient.invalidateQueries({ queryKey: ['card-exploration-threads'] }),
  queryClient.invalidateQueries({ queryKey: ['work-exploration-threads'] }),
  queryClient.invalidateQueries({ queryKey: ['experiment', props.experimentId] }),
  queryClient.invalidateQueries({ queryKey: ['experiments'] }),
  queryClient.invalidateQueries({ queryKey: ['overview'] }),
])

const openCreate = () => {
  flowError.value = ''
  editingThread.value = null
  Object.assign(threadForm, { name: '', status: 'NOT_STARTED', conclusion: '' })
  editorVisible.value = true
}
const openEdit = (thread: Pick<ExplorationThreadDto, 'id' | 'name' | 'status' | 'conclusion' | 'version' | 'systemKey'> | Pick<ExplorationThreadSummaryDto, 'id' | 'name' | 'status' | 'conclusion' | 'version' | 'systemKey'>) => {
  flowError.value = ''
  editingThread.value = thread
  Object.assign(threadForm, { name: thread.name, status: thread.status, conclusion: thread.conclusion ?? '' })
  editorVisible.value = true
}
const saveThread = async () => {
  if (!threadForm.name.trim() || busy.value) return
  busy.value = true
  try {
    const data = { name: threadForm.name.trim(), status: threadForm.status, conclusion: threadForm.conclusion.trim(), expectedVersion: editingThread.value?.version }
    if (editingThread.value) replaceThread(await experimentApi.updateThread(props.experimentId, editingThread.value.id, data))
    else await experimentApi.createThread(props.experimentId, data)
    await refreshRelated()
    editorVisible.value = false
    ElMessage.success(editingThread.value ? '探索線已更新' : '探索線已建立')
  } catch (error) {
    flowError.value = apiErrorMessage(error, '儲存探索線失敗，內容已保留。')
  } finally {
    busy.value = false
  }
}
const removeThread = async (thread: Pick<ExplorationThreadDto, 'id' | 'name'> | Pick<ExplorationThreadSummaryDto, 'id' | 'name'>) => {
  try {
    await ElMessageBox.confirm('刪除「' + thread.name + '」後，行動、發現、歷史與小結論會保存到未設定探索線；材料與成果只解除關聯，本體保留。同名行動衝突時會停止操作。', '刪除探索線', {
      confirmButtonText: '刪除', cancelButtonText: '取消', type: 'warning',
    })
    await experimentApi.deleteThread(props.experimentId, thread.id)
    await refreshRelated()
    ElMessage.success('探索線已刪除')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error('刪除探索線失敗')
  }
}
const repositionThread = async (threadId: number, targetId: number, after: boolean) => {
  if (busy.value) return
  busy.value = true
  try {
    await experimentApi.moveThread(props.experimentId, threadId, targetId, after, summaryThreads.value[0]?.orderVersion ?? 0)
    await queryClient.invalidateQueries({ queryKey: ['experiment-thread-summaries', props.experimentId] })
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '調整探索線排序失敗，請重新載入後再試。'))
    await summariesQuery.refetch()
  } finally {
    busy.value = false
  }
}
const dropThreadBefore = async (targetId: number) => {
  const sourceId = draggingThreadId.value
  draggingThreadId.value = null
  if (sourceId == null || sourceId === targetId) return
  if (!summaryThreads.value.some(thread => thread.id === sourceId) || !summaryThreads.value.some(thread => thread.id === targetId)) return
  dropTarget.value = null
  await repositionThread(sourceId, targetId, dropAfter.value)
}
const dragOver = (event: DragEvent, targetId: number) => {
  dropTarget.value = targetId
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect()
  dropAfter.value = event.clientY > rect.top + rect.height / 2
}
const moveThread = async (id: number, offset: number) => {
  const index = summaryThreads.value.findIndex(thread => thread.id === id)
  const target = summaryThreads.value[index + offset]
  if (target) { await repositionThread(id, target.id, offset > 0); return }
  const page = summariesQuery.data.value?.page ?? 0
  if ((offset < 0 && page === 0) || (offset > 0 && page + 1 >= (summariesQuery.data.value?.totalPages ?? 0))) return
  try {
    const adjacent = await experimentApi.getThreadSummaries(props.experimentId, page + offset, 20)
    const boundary = offset < 0 ? adjacent.content.at(-1) : adjacent.content[0]
    if (boundary) await repositionThread(id, boundary.id, offset > 0)
  } catch { ElMessage.error('讀取相鄰探索線失敗') }
}

const openMethod = (thread: ExplorationThreadDto) => {
  rawEditorMode.value = 'TRY'; rawEditorIntent.value = 'CREATE'; rawEditorMethod.value = null
  rawEditorThreadId.value = thread.id; rawEditorVisible.value = true
}
const openRecord = (thread: ExplorationThreadDto) => {
  rawEditorMode.value = 'DISCOVERY'; rawEditorIntent.value = 'CREATE'; rawEditorMethod.value = null
  rawEditorThreadId.value = thread.id; rawEditorVisible.value = true
}
const openSource = (thread: ExplorationThreadDto) => {
  organizationThreadId.value = thread.id; organizationRecords.value = []; organizationVisible.value = true
}
const openImport = (ids: number[] = []) => {
  organizationThreadId.value = undefined
  organizationRecords.value = props.explorationRecords.filter(record => ids.includes(record.id) && record.threadId != null) as ActivityRecord[]
  organizationVisible.value = true
}
defineExpose({ openImport })

const openCardPicker = (thread: ExplorationThreadDto, role: ExplorationThreadCardRole) => {
  cardThread.value = thread
  cardRole.value = role
  cardPickerVisible.value = true
}
const addCards = async (cards: CardDto[]) => {
  if (!cardThread.value || busy.value) return
  busy.value = true
  try {
    replaceThread(await experimentApi.addThreadCards(props.experimentId, cardThread.value.id, cards.map(card => Number(card.id)), cardRole.value))
    await queryClient.invalidateQueries({ queryKey: ['card-exploration-threads'] })
    cardPickerVisible.value = false
    ElMessage.success('已加入 ' + cards.length + ' 張卡片')
  } catch (error) { ElMessage.error(apiErrorMessage(error, '加入卡片失敗，選取內容已保留。')) }
  finally { busy.value = false }
}
const openNote = (thread: ExplorationThreadDto, card: ExplorationThreadCardDto) => {
  noteThread.value = thread
  noteCard.value = card
  noteDraft.value = card.note ?? ''
  flowError.value = ''
  noteVisible.value = true
}
const saveNote = async () => {
  if (!noteThread.value || !noteCard.value || busy.value) return
  busy.value = true
  try {
    replaceThread(await experimentApi.updateThreadCardNote(props.experimentId, noteThread.value.id, noteCard.value.id, noteDraft.value))
    await queryClient.invalidateQueries({ queryKey: ['card-exploration-threads'] })
    noteVisible.value = false
    ElMessage.success('備註已更新')
  } catch (error) { flowError.value = apiErrorMessage(error, '無法儲存備註，內容已保留。') }
  finally { busy.value = false }
}
const removeCard = async (thread: ExplorationThreadDto, linkId: number) => {
  try { replaceThread(await experimentApi.removeThreadCard(props.experimentId, thread.id, linkId)); await queryClient.invalidateQueries({ queryKey: ['card-exploration-threads'] }) }
  catch { ElMessage.error('解除卡片關聯失敗') }
}

const openWork = (thread: ExplorationThreadDto) => {
  workThread.value = thread
  selectedWorkId.value = null
  workKeyword.value = ''
  workSearch.value = ''
  flowError.value = ''
  workVisible.value = true
}
const addWork = async () => {
  if (!workThread.value || selectedWorkId.value == null || busy.value) return
  busy.value = true
  try {
    replaceThread(await experimentApi.addThreadWork(props.experimentId, workThread.value.id, selectedWorkId.value))
    await queryClient.invalidateQueries({ queryKey: ['work-exploration-threads'] })
    workVisible.value = false
    ElMessage.success('作品已關聯到探索線')
  } catch (error) { flowError.value = apiErrorMessage(error, '關聯作品失敗，選擇內容已保留。') }
  finally { busy.value = false }
}
const removeWork = async (thread: ExplorationThreadDto, linkId: number) => {
  try { replaceThread(await experimentApi.removeThreadWork(props.experimentId, thread.id, linkId)); await queryClient.invalidateQueries({ queryKey: ['work-exploration-threads'] }) }
  catch { ElMessage.error('解除作品關聯失敗') }
}
const availableWorks = computed<WorkSummary[]>(() => works.value.filter(work =>
  !workThread.value?.works.some(link => link.workId === work.id),
))
</script>

<template>
  <section v-if="!dialogOnly" class="thread-workspace">
    <SectionHeader title="探索線">
      <template #title-extra><RecordCount :count="summaryTotal" aria-label="探索線數量" /></template>
      <template #actions><el-button type="primary" :icon="Plus" @click="openCreate">建立探索線</el-button></template>
    </SectionHeader>
    <el-skeleton v-if="summariesQuery.isPending.value" :rows="5" animated />
    <el-result v-else-if="summariesQuery.isError.value" icon="error" title="無法讀取探索線">
      <template #extra><el-button type="primary" @click="summariesQuery.refetch()">重新載入</el-button></template>
    </el-result>
    <p v-else-if="!summaryThreads.length" class="quiet-empty workspace-empty">尚無探索線</p>
    <div v-else class="thread-table">
      <article v-for="(thread, index) in summaryThreads" :key="thread.id" class="thread-row"
        :class="{ 'drop-before': dropTarget === thread.id && !dropAfter, 'drop-after': dropTarget === thread.id && dropAfter }"
        @dragover.prevent="dragOver($event, thread.id)" @drop.prevent="dropThreadBefore(thread.id)">
        <header class="thread-row-heading">
          <button class="drag-handle" type="button" :draggable="!busy" title="拖曳排序；方向鍵可上下移動" aria-label="拖曳或用方向鍵調整探索線順序"
            @keydown.up.prevent="moveThread(thread.id, -1)" @keydown.down.prevent="moveThread(thread.id, 1)"
            @dragstart="draggingThreadId = thread.id" @dragend="draggingThreadId = null; dropTarget = null">⠿</button>
          <button class="thread-identity" type="button" @click="openThreadDetail(thread)">{{ thread.name }}</button>
          <div class="thread-heading-actions">
            <el-tag size="small" effect="plain">{{ statusLabel(thread.status) }}</el-tag>
            <span class="metadata">最近整理 {{ formatDate(thread.lastActivityAt) }}</span>
            <el-button text type="primary" @click="openThreadDetail(thread)">查看詳情</el-button>
            <el-button text type="primary" :aria-expanded="isExpanded(thread)" @click="toggleExpanded(thread)">{{ isExpanded(thread) ? '收合' : '展開' }}</el-button>
            <el-dropdown trigger="click">
              <el-button text circle :icon="MoreFilled" aria-label="探索線操作" :disabled="busy" />
              <template #dropdown><el-dropdown-menu>
                <el-dropdown-item :icon="Edit" @click="openEdit(thread)">編輯探索線</el-dropdown-item>
                <el-dropdown-item :icon="ArrowUp" :disabled="index === 0 && threadPage === 1" @click="moveThread(thread.id, -1)">往上移動</el-dropdown-item>
                <el-dropdown-item :icon="ArrowDown" :disabled="index === summaryThreads.length - 1 && threadPage === (summariesQuery.data.value?.totalPages ?? 1)" @click="moveThread(thread.id, 1)">往下移動</el-dropdown-item>
                <el-dropdown-item divided :icon="Delete" :disabled="!!thread.systemKey" @click="removeThread(thread)">刪除探索線</el-dropdown-item>
              </el-dropdown-menu></template>
            </el-dropdown>
          </div>
        </header>
        <el-collapse-transition>
        <div v-show="isExpanded(thread)" class="thread-summary-grid">
          <section class="summary-cell">
            <header class="column-heading"><div class="heading-label"><h3>材料與線索</h3><span v-if="thread.materialCount" class="metadata">最近 {{ Math.min(5, thread.materialCount) }} 筆</span></div><RecordHistoryTrigger label="材料" count-only :count="thread.materialCount" @click="openThreadDetail(thread, 'MATERIALS')" /></header>
            <div v-if="thread.materialCount" class="material-preview">
              <el-tooltip v-for="card in thread.recentMaterials" :key="card.cardId" :content="card.note || card.title" popper-class="content-tooltip" :show-after="400">
                <button type="button" @click="readSummaryMaterial(card)">{{ card.title }} <small>#{{ card.cardId }}</small></button>
              </el-tooltip>
            </div>
            <p v-else class="quiet-empty">無材料</p>
          </section>
          <section class="summary-cell">
            <header class="column-heading"><div class="heading-label"><h3>行動與發現</h3><span v-if="thread.recordCount" class="metadata">最近 {{ Math.min(3, thread.recordCount) }} 筆</span></div><div class="count-actions">
              <RecordHistoryTrigger label="行動" :count="thread.methodCount" @click="openThreadDetail(thread, 'RECORDS')" />
              <RecordHistoryTrigger label="發現" :count="thread.recordCount" @click="openThreadDetail(thread, 'RECORDS')" />
            </div></header>
            <div v-for="record in thread.recentRecords" :key="record.id" class="record-preview">
              <time class="metadata">{{ formatDate(record.occurredAt ?? record.createdAt) }}</time>
              <p v-if="record.methodContent" class="summary-method">行動：{{ record.methodContent }}</p>
              <p class="summary-discovery">發現：{{ record.discovery }}</p>
            </div>
            <p v-if="!thread.recordCount && thread.methodCount" class="summary-method">{{ thread.firstMethodContent }}</p>
            <p v-if="!thread.recordCount && !thread.methodCount" class="quiet-empty">無行動與發現</p>
          </section>
          <section class="summary-cell">
            <header class="column-heading"><h3>結論與成果</h3><div class="count-actions">
              <RecordHistoryTrigger label="卡片" :count="thread.resultCardCount" @click="openThreadDetail(thread, 'OUTCOMES')" />
              <RecordHistoryTrigger label="作品" :count="thread.workCount" @click="openThreadDetail(thread, 'OUTCOMES')" />
            </div></header>
            <ExpandableText v-if="thread.conclusion" :content="thread.conclusion" :lines="3" />
            <p v-else class="quiet-empty">無結論</p>
          </section>
        </div>
        </el-collapse-transition>
      </article>
    </div>
    <el-pagination v-if="(summariesQuery.data.value?.totalPages ?? 0) > 1" v-model:current-page="threadPage" :page-size="20" :total="summaryTotal" layout="prev, pager, next" />
  </section>

  <AppDialog v-if="dialogOnly && !detailThread" :model-value="true" title="探索線" width="min(640px, calc(100vw - 32px))" @update:model-value="emit('closed')">
    <el-skeleton v-if="detailQuery.isPending.value" :rows="4" animated />
    <el-result v-else-if="detailQuery.isError.value" icon="error" title="無法讀取探索線"><template #extra><el-button type="primary" @click="detailQuery.refetch()">重新載入</el-button></template></el-result>
    <p v-else class="quiet-empty">這條探索線已不存在。</p>
    <template #footer><el-button @click="emit('closed')">關閉</el-button></template>
  </AppDialog>
  <AppDialog v-if="detailThread" :model-value="detailVisible" :title="detailThread.name" pattern="workspace"
    :close-on-press-escape="!isChildOpen && !readingCard" :close-on-click-modal="!isChildOpen && !readingCard"
    width="min(1280px, calc(100vw - 32px))" scroll-body @opened="restoreDetail" @closed="dialogOnly && emit('closed')" @update:model-value="detailVisible = $event">
    <template #header>
      <header class="thread-dialog-heading"><h2>{{ detailThread.name }}</h2><div class="thread-detail-meta">
        <el-tag size="small" effect="plain">{{ statusLabel(detailThread.status) }}</el-tag>
        <span class="metadata">最近整理 {{ formatDate(detailThread.lastActivityAt) }}</span>
        <el-button type="primary" link :icon="Edit" @click="openEdit(detailThread)">編輯探索線</el-button>
      </div></header>
    </template>
    <div ref="detailBody">
      <div class="thread-detail-grid">
        <section ref="materialSection" class="thread-detail-section">
          <header class="column-heading"><div class="heading-label"><h3>材料與線索</h3><RecordCount :count="materialCards(detailThread).length" aria-label="材料數量" /></div><el-button type="primary" link :icon="Plus" size="small" @click="openCardPicker(detailThread, 'MATERIAL')">加入卡片</el-button></header>
          <div class="column-surface">
            <div v-if="materialCards(detailThread).length" class="detail-list">
              <ThreadCardPreview v-for="card in materialCards(detailThread)" :key="card.id" :card="card" @read="readCard(card)" @note="openNote(detailThread, card)" @remove="removeCard(detailThread, card.id)" />
            </div><p v-else class="quiet-empty">無材料</p>
          </div>
        </section>
        <section ref="recordSection" class="thread-detail-section">
          <header class="column-heading"><div class="heading-label"><h3>行動與發現</h3><RecordCount :count="detailThread.methodCount ?? detailThread.methods.length" aria-label="行動數量" /></div><div class="column-actions">
            <el-button type="primary" link size="small" @click="openMethod(detailThread)">新增行動備選</el-button>
            <el-button type="primary" link size="small" @click="openRecord(detailThread)">記下發現</el-button>
            <el-button type="primary" link size="small" @click="openSource(detailThread)">整理內容</el-button>
          </div></header>
          <div class="column-surface"><ExplorationThreadActivity :experiment-id="experimentId" :thread="detailThread" /></div>
        </section>
        <section ref="outcomeSection" class="thread-detail-section">
          <header class="column-heading"><h3>結論與成果</h3></header>
          <div class="column-surface">
            <ExpandableText v-if="detailThread.conclusion" :content="detailThread.conclusion" :lines="6" /><p v-else class="quiet-empty">無結論</p>
            <div class="outcome-group">
              <header class="column-heading"><div class="heading-label"><h4>成果卡片</h4><RecordCount :count="resultCards(detailThread).length" aria-label="成果卡片數量" /></div><el-button type="primary" link size="small" :icon="Plus" @click="openCardPicker(detailThread, 'RESULT')">關聯卡片</el-button></header>
              <div class="detail-list"><ThreadCardPreview v-for="card in resultCards(detailThread)" :key="card.id" :card="card" result @read="readCard(card)" @note="openNote(detailThread, card)" @remove="removeCard(detailThread, card.id)" /></div>
            </div>
            <div class="outcome-group">
              <header class="column-heading"><div class="heading-label"><h4>關聯作品</h4><RecordCount :count="detailThread.works.length" aria-label="關聯作品數量" /></div><el-button type="primary" link size="small" :icon="Plus" @click="openWork(detailThread)">關聯作品</el-button></header>
              <div class="detail-list">
                <article v-for="work in detailThread.works" :key="work.id" class="work-row">
                  <div><router-link :to="{ path: '/works/' + work.workId, query: { from: route.fullPath } }">{{ work.workTitle }}</router-link><small>{{ workStages[work.stage as WorkStage] }}{{ work.workArchived ? ' · 已封存' : '' }}</small></div>
                  <el-dropdown trigger="click"><el-button text circle size="small" :icon="MoreFilled" aria-label="作品操作" /><template #dropdown><el-dropdown-menu><el-dropdown-item @click="removeWork(detailThread, work.id)">解除關聯</el-dropdown-item></el-dropdown-menu></template></el-dropdown>
                </article>
              </div>
            </div>
          </div>
        </section>
      </div>
    </div>
    <template #footer="{ requestClose }"><el-button type="primary" link @click="requestClose">關閉</el-button></template>
  </AppDialog>

  <SourceReadingDialog v-if="readingCard" :model-value="true" :source="readingCard" :close-label="detailVisible ? '返回探索線' : '返回摘要'" @update:model-value="readingCard = null" />
  <ExperimentExplorationEditor v-if="rawEditorVisible" v-model="rawEditorVisible" :experiment-id="experimentId" :mode="rawEditorMode" :thread-id="rawEditorThreadId" :method="rawEditorMethod" :intent="rawEditorIntent" />
  <ExplorationOrganizationDialog v-if="organizationVisible" v-model="organizationVisible" :experiment-id="experimentId" :thread-id="organizationThreadId" :initial-records="organizationRecords" />

  <AppDialog ref="editorShell" v-model="editorVisible" :busy="busy" :draft="threadForm" :title="editingThread ? '編輯探索線' : '建立探索線'" width="min(640px, calc(100vw - 32px))" scroll-body>
    <el-form label-position="top" :disabled="busy" @submit.prevent="saveThread">
      <el-form-item label="探索線名稱" required><el-input v-model="threadForm.name" :disabled="!!editingThread?.systemKey" maxlength="255" /><FieldCounter :value="threadForm.name" :limit="255" /></el-form-item>
      <el-form-item label="探索狀態"><el-select v-model="threadForm.status"><el-option v-for="option in statusOptions" :key="option.value" :label="option.label" :value="option.value" /></el-select></el-form-item>
      <el-form-item label="階段性結論（選填）"><el-input v-model="threadForm.conclusion" type="textarea" :rows="4" maxlength="10000" /><FieldCounter :value="threadForm.conclusion" :limit="10000" /></el-form-item>
    </el-form>
    <el-alert v-if="flowError" :title="flowError" type="error" :closable="false" />
    <template #footer="{ requestClose }"><el-button @click="requestClose">取消</el-button><el-button type="primary" :loading="busy" :disabled="!threadForm.name.trim()" @click="saveThread">{{ editingThread ? '儲存變更' : '建立探索線' }}</el-button></template>
  </AppDialog>
  <CardPickerDialog v-model="cardPickerVisible" multiple :title="cardRole === 'MATERIAL' ? '加入材料與線索' : '關聯成果卡片'" description="可一次選擇多張卡片，原卡片內容會保留。" confirm-label="加入" :submitting="busy" :excluded-card-ids="cardThread?.cards.filter(card => card.role === cardRole).map(card => card.cardId) ?? []" settings-style="plain" @confirm-many="addCards" />
  <AppDialog ref="noteShell" v-model="noteVisible" :busy="busy" :draft="noteDraft" title="材料備註" width="min(560px, calc(100vw - 32px))">
    <p class="app-dialog-intro">{{ noteCard?.cardTitle }}</p><el-input v-model="noteDraft" :disabled="busy" type="textarea" :rows="4" maxlength="1000" aria-label="卡片與探索線的關聯備註" /><FieldCounter :value="noteDraft" :limit="1000" />
    <el-alert v-if="flowError" :title="flowError" type="error" :closable="false" /><template #footer="{ requestClose }"><el-button @click="requestClose">取消</el-button><el-button type="primary" :loading="busy" @click="saveNote">儲存備註</el-button></template>
  </AppDialog>
  <AppDialog ref="workShell" v-model="workVisible" :busy="busy" :draft="selectedWorkId" title="關聯作品" width="min(560px, calc(100vw - 32px))" scroll-body>
    <form class="work-search" @submit.prevent="workSearch = workKeyword.trim()"><el-input v-model="workKeyword" clearable placeholder="搜尋作品名稱" aria-label="搜尋作品" :disabled="busy" /><el-button native-type="submit" :disabled="busy">搜尋</el-button></form>
    <el-skeleton v-if="worksQuery.isPending.value" :rows="3" animated />
    <el-alert v-else-if="worksQuery.isError.value" title="無法載入作品" type="error" :closable="false"><el-button @click="worksQuery.refetch()">重試</el-button></el-alert>
    <div v-else class="work-options"><PickerOption v-for="work in availableWorks" :key="work.id" :title="work.title" :selected="selectedWorkId === work.id" :disabled="busy" @select="selectedWorkId = work.id"><span>{{ workStages[work.stage] }}</span></PickerOption></div>
    <p v-if="!worksQuery.isPending.value && !worksQuery.isError.value && !availableWorks.length" class="quiet-empty">目前沒有可關聯的作品</p>
    <el-button v-if="worksQuery.hasNextPage.value" :loading="worksQuery.isFetchingNextPage.value" @click="worksQuery.fetchNextPage()">載入更多</el-button>
    <p v-if="worksQuery.isFetchNextPageError.value" class="error-copy">後續作品載入失敗，可再次載入。</p>
    <el-alert v-if="flowError" :title="flowError" type="error" :closable="false" />
    <template #footer="{ requestClose }"><el-button @click="requestClose">取消</el-button><el-button type="primary" :loading="busy" :disabled="selectedWorkId == null" @click="addWork">關聯作品</el-button></template>
  </AppDialog>
</template>

<style scoped>
.thread-workspace { min-width: 0; padding: var(--panel-padding); border: 1px solid var(--el-border-color-light); border-radius: var(--panel-radius); background: var(--el-bg-color); }
.thread-table { display: grid; margin-top: var(--space-md); border: 1px solid var(--el-border-color); border-radius: var(--radius-md); overflow: hidden; }
.thread-row { min-width: 0; }
.thread-row + .thread-row { border-top: 1px solid var(--el-border-color); }
.thread-row.drop-before { box-shadow: 0 -3px 0 var(--el-color-primary); }
.thread-row.drop-after { box-shadow: 0 3px 0 var(--el-color-primary); }
.thread-row-heading { display: flex; align-items: center; flex-wrap: wrap; gap: var(--space-xs); padding: var(--space-sm) var(--space-md); background: var(--surface-collection); }
.drag-handle, .thread-identity { border: 0; background: transparent; font: inherit; color: var(--el-text-color-primary); }
.drag-handle { padding: var(--space-xs); color: var(--el-text-color-secondary); cursor: grab; }
.thread-identity { padding: 0; font-size: var(--type-card-title); font-weight: var(--weight-semibold); text-align: left; overflow-wrap: anywhere; cursor: pointer; }
.thread-heading-actions { display: flex; flex-wrap: wrap; align-items: center; justify-content: flex-end; gap: var(--space-xs); margin-left: auto; }
.thread-heading-actions :deep(.el-button), .column-heading :deep(.el-button) { margin-left: 0; }
.metadata { color: var(--el-text-color-secondary); font-size: var(--type-meta); line-height: var(--leading-metadata); }
.thread-summary-grid { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1.5fr) minmax(0, 1fr); }
.summary-cell { min-width: 0; padding: var(--space-md); border-top: 1px solid var(--el-border-color); }
.summary-cell + .summary-cell { border-left: 1px solid var(--el-border-color); }
.heading-label { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-xs); min-width: 0; }
.column-heading { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--space-xs); min-height: 28px; margin-bottom: var(--space-sm); }
.column-heading h3, .column-heading h4 { margin: 0; color: var(--el-text-color-primary); font-size: var(--type-ui); font-weight: var(--weight-semibold); line-height: var(--leading-ui); }
.count-actions, .column-actions { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-xs); }
.material-preview { display: grid; gap: var(--space-xs); }
.material-preview button { min-width: 0; padding: 0; overflow: hidden; border: 0; background: transparent; color: var(--el-text-color-primary); font: inherit; font-size: var(--type-body); line-height: var(--leading-ui); white-space: nowrap; text-align: left; text-overflow: ellipsis; cursor: pointer; }
.material-preview small { color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.record-preview + .record-preview { margin-top: var(--space-sm); }
.summary-method, .summary-discovery { display: -webkit-box; margin: var(--space-2xs) 0 0; overflow: hidden; -webkit-box-orient: vertical; font-size: var(--type-secondary); line-height: var(--leading-ui); overflow-wrap: anywhere; white-space: pre-wrap; }
.summary-method { -webkit-line-clamp: 1; }
.summary-discovery { -webkit-line-clamp: 2; }
.quiet-empty { margin: 0; color: var(--el-text-color-secondary); font-size: var(--type-secondary); line-height: var(--leading-ui); }
.workspace-empty { margin-top: var(--space-md); }
.thread-dialog-heading { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--space-sm); padding-right: var(--space-lg); }
.thread-dialog-heading h2 { min-width: 0; font-size: var(--type-section-title); line-height: var(--leading-section-title); overflow-wrap: anywhere; }
.thread-detail-meta { display: flex; flex-wrap: wrap; align-items: center; justify-content: flex-end; gap: var(--space-xs); }
.thread-detail-grid { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1.2fr) minmax(0, .85fr); height: min(640px, calc(100dvh - 220px)); align-items: stretch; gap: var(--space-sm); }
.thread-detail-section { display: flex; min-width: 0; min-height: 0; flex-direction: column; }
.thread-detail-section > .column-heading { flex-shrink: 0; }
.column-surface { flex: 1; min-width: 0; min-height: 0; overflow-y: auto; overscroll-behavior: contain; padding: var(--space-sm); border-radius: var(--radius-md); background: var(--surface-summary); }
.detail-list, .method-list { display: grid; gap: var(--space-sm); }
.method-group + .method-group, .standalone { padding-top: var(--space-md); border-top: 1px solid var(--el-border-color-light); }
.method-heading, .finding-row { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--space-xs); }
.method-content, .finding-content { min-width: 0; flex: 1; }
.method-heading > :deep(.el-dropdown), .finding-row > :deep(.el-dropdown) { flex: 0 0 auto; margin-left: auto; }
.method-heading p, .finding-row p { margin: var(--space-2xs) 0 0; color: var(--el-text-color-regular); font-size: var(--type-body); line-height: var(--leading-body); white-space: pre-wrap; overflow-wrap: anywhere; }
.finding-row:not(.standalone) { padding-left: var(--space-sm); margin-top: var(--space-sm); }
.outcome-group { margin-top: var(--space-lg); }
.work-row { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--space-xs); padding: var(--space-sm); border: 1px solid var(--el-border-color-lighter); border-radius: var(--radius-md); background: var(--el-bg-color); }
.work-row > div { min-width: 0; }
.work-row a { color: var(--el-text-color-primary); font-size: var(--type-body); font-weight: var(--weight-semibold); text-decoration: none; overflow-wrap: anywhere; }
.work-row small { display: block; margin-top: var(--space-2xs); color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.work-row a:hover, .thread-identity:hover, .material-preview button:hover { color: var(--el-color-primary); }
button:focus-visible, a:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 2px; }
.record-label { display: block; margin-bottom: var(--space-xs); color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.source-target { display: grid; gap: var(--space-sm); margin-bottom: var(--space-sm); }
.selection-count { margin: var(--space-sm) 0; color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.source-records { display: grid; gap: var(--space-sm); }
.source-record { min-width: 0; padding: var(--space-sm); border: 1px solid var(--el-border-color-lighter); border-radius: var(--radius-md); }
.source-record.selected { border-color: var(--el-color-primary-light-5); background: var(--el-color-primary-light-9); }
.source-preview { padding-left: var(--space-lg); }
.source-preview p { display: -webkit-box; overflow: hidden; -webkit-box-orient: vertical; -webkit-line-clamp: 2; font-size: var(--type-secondary); line-height: var(--leading-ui); white-space: pre-wrap; overflow-wrap: anywhere; }
.source-preview small { color: var(--el-text-color-secondary); }
.source-editor { display: grid; gap: var(--space-xs); margin-top: var(--space-xs); }
.error-copy { color: var(--el-color-danger); font-size: var(--type-secondary); }
.work-search { display: flex; gap: var(--space-xs); margin-bottom: var(--space-md); }
.work-options { display: grid; gap: var(--space-xs); width: 100%; }
@media (max-width: 900px) {
  .thread-summary-grid, .thread-detail-grid { grid-template-columns: minmax(0, 1fr); }
  .summary-cell + .summary-cell { border-left: 0; }
  .thread-detail-grid { gap: var(--space-md); height: auto; }
  .column-surface { flex: auto; max-height: min(420px, 55dvh); }
}
@media (max-width: 760px) {
  .thread-heading-actions { width: 100%; justify-content: flex-start; margin-left: var(--space-xl); }
  .thread-heading-actions .metadata { flex: 1; }
}
</style>
