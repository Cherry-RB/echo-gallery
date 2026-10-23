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
  ExplorationThreadImportItem,
  ExplorationThreadMethodDto,
  ExplorationThreadRecordItemDto,
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
import RecordPickerOption from '../ui/RecordPickerOption.vue'
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
const methodShell = ref<InstanceType<typeof AppDialog>>()
const recordShell = ref<InstanceType<typeof AppDialog>>()
const sourceShell = ref<InstanceType<typeof AppDialog>>()
const noteShell = ref<InstanceType<typeof AppDialog>>()
const workShell = ref<InstanceType<typeof AppDialog>>()
const sourceVisible = ref(false)
const detailVisible = ref(false)
const detailThreadId = ref<number | null>(null)
useWorkDialogGuard(editorShell, () => editorVisible.value)
useWorkDialogGuard(methodShell, () => methodVisible.value)
useWorkDialogGuard(recordShell, () => recordVisible.value)
useWorkDialogGuard(sourceShell, () => sourceVisible.value)
useWorkDialogGuard(noteShell, () => noteVisible.value)
useWorkDialogGuard(workShell, () => workVisible.value)
const threadPage = ref(1)
const expandedThreadIds = ref(new Set<number>())
const initializedThreadPages = ref(new Set<number>())
const threadsQuery = useQuery({
  queryKey: computed(() => ['experiment-threads', props.experimentId]),
  queryFn: () => experimentApi.getThreads(props.experimentId),
  enabled: computed(() => sourceVisible.value),
})
const threads = computed(() => threadsQuery.data.value ?? [])
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
const explorationQuery = useQuery({
  queryKey: computed(() => ['experiment-exploration', props.experimentId]),
  queryFn: () => experimentApi.getExploration(props.experimentId),
  enabled: computed(() => props.dialogOnly),
})
const records = computed(() => props.dialogOnly ? explorationQuery.data.value?.records ?? [] : props.explorationRecords)
const busy = ref(false)
const editorVisible = ref(false)
const editingThread = ref<Pick<ExplorationThreadDto, 'id'> | null>(null)
const threadForm = reactive<{ name: string; status: ExplorationThreadStatus; conclusion: string }>({
  name: '', status: 'NOT_STARTED', conclusion: '',
})
const methodVisible = ref(false)
const methodThread = ref<ExplorationThreadDto | null>(null)
const editingMethod = ref<ExplorationThreadMethodDto | null>(null)
const methodDraft = ref('')
const recordVisible = ref(false)
const recordThread = ref<ExplorationThreadDto | null>(null)
const editingRecord = ref<ExplorationThreadRecordItemDto | null>(null)
const recordMethodId = ref<number | null>(null)
const recordDraft = ref('')
const sourceThread = ref<ExplorationThreadDto | null>(null)
interface SourceDraft extends ExplorationThreadImportItem {
  selected: boolean
  createdAt: string
}
const sourceDrafts = ref<SourceDraft[]>([])
const selectedSourceDrafts = computed(() => sourceDrafts.value.filter(item => item.selected))
const hasInvalidSourceDraft = computed(() => selectedSourceDrafts.value.some(item =>
  (!item.includeMethod && !item.includeDiscovery)
  || (item.includeMethod && !item.methodContent?.trim())
  || (item.includeDiscovery && !item.discovery?.trim()),
))
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
const rawEditorMethod = ref<ExplorationThreadMethodDto | null>(null)
const noteVisible = ref(false)
const noteCard = ref<ExplorationThreadCardDto>()
const noteThread = ref<ExplorationThreadDto>()
const noteDraft = ref('')
const sourceStep = ref<'SELECT' | 'EDIT'>('SELECT')
const newSourceThread = ref(false)
const sourceThreadName = ref('')
const pendingImportIds = ref<number[] | undefined>()
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
const isChildOpen = computed(() => editorVisible.value || methodVisible.value || recordVisible.value || sourceVisible.value || cardPickerVisible.value || workVisible.value || noteVisible.value || rawEditorVisible.value)
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
const readSource = (sourceId: number | null) => {
  const record = records.value.find(item => item.id === sourceId)
  if (!record) return
  rememberDetail()
  readingCard.value = { id: null, cardId: null, recordId: record.id, title: formatDate(record.createdAt), text: [record.tryText, record.discovery].filter(Boolean).join('\n\n'), tryText: record.tryText, discovery: record.discovery, experimentId: props.experimentId, experimentTitle: null, archived: false }
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
const openEdit = (thread: Pick<ExplorationThreadDto, 'id' | 'name' | 'status' | 'conclusion'> | Pick<ExplorationThreadSummaryDto, 'id' | 'name' | 'status' | 'conclusion'>) => {
  flowError.value = ''
  editingThread.value = thread
  Object.assign(threadForm, { name: thread.name, status: thread.status, conclusion: thread.conclusion ?? '' })
  editorVisible.value = true
}
const saveThread = async () => {
  if (!threadForm.name.trim() || busy.value) return
  busy.value = true
  try {
    const data = { name: threadForm.name.trim(), status: threadForm.status, conclusion: threadForm.conclusion.trim() }
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
    await ElMessageBox.confirm('刪除「' + thread.name + '」後，材料、試法、發現與成果關聯也會一併移除。', '刪除探索線', {
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
    await experimentApi.moveThread(props.experimentId, threadId, targetId, after)
    await queryClient.invalidateQueries({ queryKey: ['experiment-thread-summaries', props.experimentId] })
  } catch {
    ElMessage.error('調整探索線排序失敗')
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

const openMethod = (thread: ExplorationThreadDto, method: ExplorationThreadMethodDto | null = null) => {
  flowError.value = ''
  methodThread.value = thread
  editingMethod.value = method
  methodDraft.value = method?.content ?? ''
  methodVisible.value = true
}
const saveMethod = async () => {
  if (!methodThread.value || !methodDraft.value.trim() || busy.value) return
  busy.value = true
  try {
    const thread = methodThread.value
    const updated = editingMethod.value
      ? await experimentApi.updateThreadMethod(props.experimentId, thread.id, editingMethod.value.id, methodDraft.value.trim(), editingMethod.value.sourceRecordId)
      : await experimentApi.addThreadMethod(props.experimentId, thread.id, methodDraft.value.trim())
    replaceThread(updated)
    methodVisible.value = false
    ElMessage.success(editingMethod.value ? '試法已更新' : '試法已加入探索線')
  } catch (error) {
    flowError.value = apiErrorMessage(error, '儲存試法失敗，內容已保留。')
  } finally { busy.value = false }
}
const useAsCurrentTry = async (methodId: number) => {
  rawEditorMode.value = 'TRY'
  rawEditorMethod.value = detailThread.value?.methods.find(method => method.id === methodId) ?? null
  rawEditorThreadId.value = undefined
  rawEditorVisible.value = true
}
const removeMethod = async (thread: ExplorationThreadDto, methodId: number) => {
  try {
    await ElMessageBox.confirm('移除此試法後，相關發現仍會保留在探索線中。', '移除試法', {
      confirmButtonText: '移除', cancelButtonText: '取消', type: 'warning',
    })
    replaceThread(await experimentApi.deleteThreadMethod(props.experimentId, thread.id, methodId))
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error('移除試法失敗')
  }
}

const openRecord = (thread: ExplorationThreadDto, methodId: number | null = null, record: ExplorationThreadRecordItemDto | null = null) => {
  flowError.value = ''
  if (!record) {
    rawEditorMode.value = 'DISCOVERY'
    rawEditorMethod.value = thread.methods.find(method => method.id === methodId) ?? null
    rawEditorThreadId.value = thread.id
    rawEditorVisible.value = true
    return
  }
  recordThread.value = thread
  editingRecord.value = record
  recordMethodId.value = record ? record.methodId : methodId
  recordDraft.value = record?.discovery ?? ''
  recordVisible.value = true
}
const saveRecord = async () => {
  if (!recordThread.value || !recordDraft.value.trim() || busy.value) return
  busy.value = true
  try {
    const thread = recordThread.value
    const data = {
      methodId: recordMethodId.value,
      discovery: recordDraft.value.trim(),
      sourceRecordId: editingRecord.value?.sourceRecordId ?? null,
      occurredAt: editingRecord.value?.occurredAt ?? new Date().toISOString(),
    }
    const updated = editingRecord.value
      ? await experimentApi.updateThreadRecord(props.experimentId, thread.id, editingRecord.value.id, data)
      : await experimentApi.addThreadRecord(props.experimentId, thread.id, data)
    replaceThread(updated)
    recordVisible.value = false
    ElMessage.success(editingRecord.value ? '發現已更新' : '發現已加入探索線')
  } catch (error) {
    flowError.value = apiErrorMessage(error, '儲存發現失敗，內容已保留。')
  } finally { busy.value = false }
}
const removeRecord = async (thread: ExplorationThreadDto, record: ExplorationThreadRecordItemDto) => {
  try {
    await ElMessageBox.confirm('移除這筆發現不會刪除原始探索紀錄。', '移除發現', {
      confirmButtonText: '移除', cancelButtonText: '取消', type: 'warning',
    })
    replaceThread(await experimentApi.deleteThreadRecord(props.experimentId, thread.id, record.id))
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error('移除發現失敗')
  }
}

const prepareSourceDrafts = (thread: ExplorationThreadDto | null, selectedIds: number[] = []) => {
  const selected = new Set(selectedIds)
  sourceDrafts.value = records.value.map(record => ({
    recordId: record.id,
    selected: selected.has(record.id),
    createdAt: record.createdAt,
    includeMethod: Boolean(record.tryText) && !thread?.methods.some(method => method.id === record.sourceThreadMethodId || method.sourceRecordId === record.id),
    includeDiscovery: Boolean(record.discovery) && !thread?.records.some(item => item.sourceRecordId === record.id),
    methodContent: record.tryText ?? '',
    discovery: record.discovery,
    targetMethodId: record.sourceThreadMethodId != null && thread?.methods.some(method => method.id === record.sourceThreadMethodId)
      ? record.sourceThreadMethodId
      : thread?.methods.find(method => method.sourceRecordId === record.id)?.id ?? null,
  }))
  for (const draft of sourceDrafts.value) {
    if (!draft.includeMethod && !draft.includeDiscovery) draft.selected = false
  }
}
const openSource = (thread: ExplorationThreadDto) => {
  sourceStep.value = 'SELECT'
  newSourceThread.value = false
  sourceThreadName.value = ''
  flowError.value = ''
  sourceThread.value = thread
  prepareSourceDrafts(thread)
  sourceVisible.value = true
}
const initializeImport = (selectedIds: number[]) => {
  sourceStep.value = 'SELECT'
  newSourceThread.value = !threads.value.length
  sourceThreadName.value = ''
  flowError.value = ''
  const selectedRecord = selectedIds.length === 1
    ? records.value.find(record => record.id === selectedIds[0])
    : null
  const originalThread = selectedRecord?.sourceThreadMethodId == null
    ? null
    : threads.value.find(thread => thread.methods.some(method => method.id === selectedRecord.sourceThreadMethodId))
  sourceThread.value = originalThread ?? (threads.value.length === 1 ? threads.value[0]! : null)
  prepareSourceDrafts(sourceThread.value, selectedIds)
  sourceVisible.value = true
}
const openImport = (selectedIds: number[] = records.value.slice(0, 100).map(record => record.id)) => {
  if (!threadsQuery.isSuccess.value) {
    pendingImportIds.value = selectedIds
    sourceVisible.value = true
    return
  }
  initializeImport(selectedIds)
}
watch([sourceVisible, threadsQuery.isSuccess], ([visible, ready]) => {
  if (!visible || !ready || !pendingImportIds.value) return
  const ids = pendingImportIds.value
  pendingImportIds.value = undefined
  initializeImport(ids)
})
const retargetSourceDrafts = (thread: ExplorationThreadDto | null) => {
  const previous = new Map(sourceDrafts.value.map(item => [item.recordId, item]))
  prepareSourceDrafts(thread, selectedSourceDrafts.value.map(item => item.recordId))
  for (const draft of sourceDrafts.value) {
    const edited = previous.get(draft.recordId)
    if (edited) {
      draft.methodContent = edited.methodContent
      draft.discovery = edited.discovery
    }
  }
}
const selectSourceThread = (threadId: number) => {
  sourceThread.value = threads.value.find(thread => thread.id === threadId) ?? null
  retargetSourceDrafts(sourceThread.value)
}
const alreadyImported = (id: number, part: 'method' | 'discovery') => !newSourceThread.value && Boolean(part === 'method'
  ? sourceThread.value?.methods.some(item => item.sourceRecordId === id)
  : sourceThread.value?.records.some(item => item.sourceRecordId === id))
defineExpose({ openImport })
const importSource = async () => {
  const thread = sourceThread.value
  const items = selectedSourceDrafts.value
  if ((!newSourceThread.value && !thread) || (newSourceThread.value && !sourceThreadName.value.trim()) || !items.length || items.length > 100 || hasInvalidSourceDraft.value || busy.value) return
  busy.value = true
  try {
    const payload = items.map(item => ({
      recordId: item.recordId,
      includeMethod: item.includeMethod,
      includeDiscovery: item.includeDiscovery,
      methodContent: item.methodContent,
      discovery: item.discovery,
      targetMethodId: item.includeMethod ? null : item.targetMethodId,
    }))
    if (newSourceThread.value) {
      await experimentApi.createThreadWithImports(props.experimentId, sourceThreadName.value.trim(), payload)
      await refreshRelated()
    } else replaceThread(await experimentApi.importThreadRecords(props.experimentId, thread!.id, payload))
    sourceVisible.value = false
    ElMessage.success(items.length + ' 筆探索紀錄已加入探索線，原始紀錄仍保留')
  } catch (error) {
    flowError.value = apiErrorMessage(error, '無法加入探索線，選取與編輯內容已保留。')
  } finally { busy.value = false }
}

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
                <el-dropdown-item divided :icon="Delete" @click="removeThread(thread)">刪除探索線</el-dropdown-item>
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
            <header class="column-heading"><div class="heading-label"><h3>試法與發現</h3><span v-if="thread.recordCount" class="metadata">最近 {{ Math.min(3, thread.recordCount) }} 筆</span></div><div class="count-actions">
              <RecordHistoryTrigger label="試法" :count="thread.methodCount" @click="openThreadDetail(thread, 'RECORDS')" />
              <RecordHistoryTrigger label="發現" :count="thread.recordCount" @click="openThreadDetail(thread, 'RECORDS')" />
            </div></header>
            <div v-for="record in thread.recentRecords" :key="record.id" class="record-preview">
              <time class="metadata">{{ formatDate(record.occurredAt ?? record.createdAt) }}</time>
              <p v-if="record.methodContent" class="summary-method">試法：{{ record.methodContent }}</p>
              <p class="summary-discovery">發現：{{ record.discovery }}</p>
            </div>
            <p v-if="!thread.recordCount && thread.methodCount" class="summary-method">{{ thread.firstMethodContent }}</p>
            <p v-if="!thread.recordCount && !thread.methodCount" class="quiet-empty">無試法與發現</p>
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
          <header class="column-heading"><div class="heading-label"><h3>試法與發現</h3><RecordCount :count="detailThread.methods.length" aria-label="試法數量" /></div><div class="column-actions">
            <el-button type="primary" link size="small" @click="openMethod(detailThread)">新增試法</el-button>
            <el-button type="primary" link size="small" @click="openRecord(detailThread)">記下發現</el-button>
            <el-button type="primary" link size="small" :disabled="!records.length" @click="openSource(detailThread)">加入紀錄</el-button>
          </div></header>
          <div class="column-surface">
            <div v-if="detailThread.methods.length || detailThread.records.length" class="method-list">
              <article v-for="method in detailThread.methods" :key="method.id" class="method-group">
                <header class="method-heading"><div class="method-content"><div class="heading-label"><span class="metadata">試法</span><RecordCount :count="detailThread.records.filter(item => item.methodId === method.id).length" aria-label="此試法的發現數量" /></div><p>{{ method.content }}</p></div>
                  <el-dropdown trigger="click"><el-button text circle size="small" :icon="MoreFilled" aria-label="試法操作" />
                    <template #dropdown><el-dropdown-menu>
                      <el-dropdown-item @click="useAsCurrentTry(method.id)">設為當前試法</el-dropdown-item>
                      <el-dropdown-item @click="openMethod(detailThread, method)">編輯試法</el-dropdown-item>
                      <el-dropdown-item @click="openRecord(detailThread, method.id)">記下發現</el-dropdown-item>
                      <el-dropdown-item v-if="records.some(item => item.id === method.sourceRecordId)" @click="readSource(method.sourceRecordId)">查看原始紀錄</el-dropdown-item>
                      <el-dropdown-item divided @click="removeMethod(detailThread, method.id)">移除試法</el-dropdown-item>
                    </el-dropdown-menu></template>
                  </el-dropdown>
                </header>
                <div v-for="record in detailThread.records.filter(item => item.methodId === method.id)" :key="record.id" class="finding-row">
                  <div class="finding-content"><span class="metadata">發現 · {{ formatDate(record.occurredAt ?? record.createdAt) }}</span><p>{{ record.discovery }}</p></div>
                  <el-dropdown trigger="click"><el-button text circle size="small" :icon="MoreFilled" aria-label="發現操作" />
                    <template #dropdown><el-dropdown-menu>
                      <el-dropdown-item @click="openRecord(detailThread, record.methodId, record)">編輯發現</el-dropdown-item>
                      <el-dropdown-item v-if="records.some(item => item.id === record.sourceRecordId)" @click="readSource(record.sourceRecordId)">查看原始紀錄</el-dropdown-item>
                      <el-dropdown-item divided @click="removeRecord(detailThread, record)">移除發現</el-dropdown-item>
                    </el-dropdown-menu></template>
                  </el-dropdown>
                </div>
              </article>
              <article v-for="record in detailThread.records.filter(item => item.methodId == null)" :key="record.id" class="finding-row standalone">
                <div class="finding-content"><span class="metadata">發現 · {{ formatDate(record.occurredAt ?? record.createdAt) }}</span><p>{{ record.discovery }}</p></div>
                <el-dropdown trigger="click"><el-button text circle size="small" :icon="MoreFilled" aria-label="發現操作" /><template #dropdown><el-dropdown-menu>
                  <el-dropdown-item @click="openRecord(detailThread, null, record)">編輯發現</el-dropdown-item>
                  <el-dropdown-item v-if="records.some(item => item.id === record.sourceRecordId)" @click="readSource(record.sourceRecordId)">查看原始紀錄</el-dropdown-item>
                  <el-dropdown-item divided @click="removeRecord(detailThread, record)">移除發現</el-dropdown-item>
                </el-dropdown-menu></template></el-dropdown>
              </article>
            </div><p v-else class="quiet-empty">無試法與發現</p>
          </div>
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
  <ExperimentExplorationEditor v-if="rawEditorVisible" v-model="rawEditorVisible" :experiment-id="experimentId" :mode="rawEditorMode" :thread-id="rawEditorThreadId" :method="rawEditorMethod" />

  <AppDialog ref="editorShell" v-model="editorVisible" :busy="busy" :draft="threadForm" :title="editingThread ? '編輯探索線' : '建立探索線'" width="min(640px, calc(100vw - 32px))" scroll-body>
    <el-form label-position="top" :disabled="busy" @submit.prevent="saveThread">
      <el-form-item label="探索線名稱" required><el-input v-model="threadForm.name" maxlength="255" /><FieldCounter :value="threadForm.name" :limit="255" /></el-form-item>
      <el-form-item label="探索狀態"><el-select v-model="threadForm.status"><el-option v-for="option in statusOptions" :key="option.value" :label="option.label" :value="option.value" /></el-select></el-form-item>
      <el-form-item label="階段性結論（選填）"><el-input v-model="threadForm.conclusion" type="textarea" :rows="4" maxlength="10000" /><FieldCounter :value="threadForm.conclusion" :limit="10000" /></el-form-item>
    </el-form>
    <el-alert v-if="flowError" :title="flowError" type="error" :closable="false" />
    <template #footer="{ requestClose }"><el-button @click="requestClose">取消</el-button><el-button type="primary" :loading="busy" :disabled="!threadForm.name.trim()" @click="saveThread">{{ editingThread ? '儲存變更' : '建立探索線' }}</el-button></template>
  </AppDialog>
  <AppDialog ref="methodShell" v-model="methodVisible" class="app-record-form" :busy="busy" :draft="methodDraft" :title="editingMethod ? '編輯試法' : '新增試法'" width="min(560px, calc(100vw - 32px))">
    <label class="record-label" for="thread-method">試法</label><el-input id="thread-method" v-model="methodDraft" :disabled="busy" type="textarea" :rows="4" maxlength="2000" /><FieldCounter :value="methodDraft" :limit="2000" />
    <el-alert v-if="flowError" :title="flowError" type="error" :closable="false" />
    <template #footer="{ requestClose }"><el-button @click="requestClose">取消</el-button><el-button type="primary" :loading="busy" :disabled="!methodDraft.trim()" @click="saveMethod">{{ editingMethod ? '儲存變更' : '新增試法' }}</el-button></template>
  </AppDialog>
  <AppDialog ref="recordShell" v-model="recordVisible" class="app-record-form" :busy="busy" :draft="[recordMethodId, recordDraft]" title="編輯探索線發現" width="min(560px, calc(100vw - 32px))" scroll-body>
    <p class="app-dialog-intro">修改探索線整理內容，原始探索紀錄會保留。</p>
    <el-form label-position="top" :disabled="busy"><el-form-item label="對應試法（選填）"><el-select v-model="recordMethodId" clearable placeholder="未指定試法"><el-option v-for="method in recordThread?.methods ?? []" :key="method.id" :label="method.content" :value="method.id" /></el-select></el-form-item><el-form-item label="發現" required><el-input v-model="recordDraft" type="textarea" :rows="5" maxlength="10000" /><FieldCounter :value="recordDraft" :limit="10000" /></el-form-item></el-form>
    <el-alert v-if="flowError" :title="flowError" type="error" :closable="false" />
    <template #footer="{ requestClose }"><el-button @click="requestClose">取消</el-button><el-button type="primary" :loading="busy" :disabled="!recordDraft.trim()" @click="saveRecord">儲存變更</el-button></template>
  </AppDialog>

  <AppDialog ref="sourceShell" v-model="sourceVisible" :busy="busy" :draft="[sourceDrafts, newSourceThread, sourceThreadName, sourceThread?.id]" :title="sourceStep === 'SELECT' ? '加入探索線' : '整理選取內容'" width="min(900px, calc(100vw - 32px))" scroll-body @closed="emit('import-closed')">
    <template v-if="sourceStep === 'SELECT'">
      <div class="source-target">
        <el-radio-group v-model="newSourceThread" :disabled="busy" @change="retargetSourceDrafts(newSourceThread ? null : sourceThread)"><el-radio-button :value="false">既有探索線</el-radio-button><el-radio-button :value="true">建立探索線</el-radio-button></el-radio-group>
        <el-input v-if="newSourceThread" v-model="sourceThreadName" maxlength="255" placeholder="探索線名稱" :disabled="busy" aria-label="新探索線名稱" />
        <el-select v-else :model-value="sourceThread?.id" placeholder="選擇探索線" :disabled="busy" @update:model-value="selectSourceThread"><el-option v-for="thread in threads" :key="thread.id" :label="thread.name" :value="thread.id" /></el-select>
      </div>
      <p class="selection-count">已選 {{ selectedSourceDrafts.length }}／100 筆</p>
      <div class="source-records">
        <RecordPickerOption v-for="draft in sourceDrafts" :key="draft.recordId" :title="formatDate(draft.createdAt)" :selected="draft.selected" :try-text="draft.methodContent" :discovery="draft.discovery" :disabled="busy || (!draft.selected && selectedSourceDrafts.length >= 100) || (!draft.includeMethod && !draft.includeDiscovery)" @select="draft.selected = !draft.selected"><small v-if="alreadyImported(draft.recordId, 'method') || alreadyImported(draft.recordId, 'discovery')">????????????</small></RecordPickerOption>
      </div>
    </template>
    <template v-else>
      <p class="app-dialog-intro">選擇試法、發現或兩者；可修改整理文字，原始紀錄仍會保留。</p>
      <div class="source-records">
        <article v-for="draft in selectedSourceDrafts" :key="draft.recordId" class="source-record selected">
          <time class="metadata">{{ formatDate(draft.createdAt) }}</time>
          <div class="source-editor">
            <el-checkbox v-model="draft.includeMethod" :disabled="busy || alreadyImported(draft.recordId, 'method') || !records.find(item => item.id === draft.recordId)?.tryText">建立新試法</el-checkbox>
            <template v-if="draft.includeMethod"><el-input v-model="draft.methodContent" :disabled="busy" type="textarea" :rows="2" maxlength="2000" aria-label="整理後試法" /><FieldCounter :value="draft.methodContent ?? ''" :limit="2000" /></template>
            <el-checkbox v-model="draft.includeDiscovery" :disabled="busy || alreadyImported(draft.recordId, 'discovery')">加入發現</el-checkbox>
            <template v-if="draft.includeDiscovery"><el-input v-model="draft.discovery" :disabled="busy" type="textarea" :rows="3" maxlength="10000" aria-label="整理後發現" /><FieldCounter :value="draft.discovery ?? ''" :limit="10000" /></template>
            <el-select v-if="!draft.includeMethod && draft.includeDiscovery" v-model="draft.targetMethodId" :disabled="busy || newSourceThread" clearable placeholder="未指定試法" aria-label="發現歸屬的試法"><el-option v-for="method in sourceThread?.methods ?? []" :key="method.id" :label="method.content" :value="method.id" /></el-select>
            <small v-if="!draft.includeMethod && !draft.includeDiscovery" class="error-copy">請選擇試法或發現</small>
          </div>
        </article>
      </div>
    </template>
    <el-alert v-if="flowError" :title="flowError" type="error" :closable="false" />
    <template #footer="{ requestClose }">
      <el-button @click="sourceStep === 'EDIT' ? sourceStep = 'SELECT' : requestClose()">{{ sourceStep === 'EDIT' ? '上一步' : '取消' }}</el-button>
      <el-button v-if="sourceStep === 'SELECT'" type="primary" :disabled="!selectedSourceDrafts.length || (!newSourceThread && !sourceThread) || (newSourceThread && !sourceThreadName.trim())" @click="sourceStep = 'EDIT'">下一步</el-button>
      <el-button v-else type="primary" :loading="busy" :disabled="hasInvalidSourceDraft" @click="importSource">{{ newSourceThread ? '建立並加入' : '加入探索線' }}</el-button>
    </template>
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
