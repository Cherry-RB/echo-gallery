<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, ArrowLeft, Delete, Edit, MagicStick, Plus } from '@element-plus/icons-vue'
import ExperimentCardItem from '../../components/experiment/ExperimentCardItem.vue'
import ExperimentExplorationEditor from '../../components/experiment/ExperimentExplorationEditor.vue'
import ExperimentExplorationHistoryDialog from '../../components/experiment/ExperimentExplorationHistoryDialog.vue'
import QuickCreateCardDialog from '../../components/QuickCreateCardDialog.vue'
import CardPickerDialog from '../../components/CardPickerDialog.vue'
import AppDialog from '../../components/AppDialog.vue'
import ExpandableText from '../../components/ExpandableText.vue'
import CardPickerSelectionSummary from '../../components/ui/CardPickerSelectionSummary.vue'
import type { CardContentRequest, CardDto } from '../../types/card'
import type { ExperimentCardDto, ExperimentExplorationDto, ExperimentExplorationRecordDto, ExperimentRequest, ExperimentStage } from '../../types/experiment'
import { getTextLength, trimToTextLength } from '../../utils/textLength'
import { formatDate } from '../../utils/formatDate'
import { experimentApi } from '../../utils/api/experimentApi'
import { experimentThemeOptions, getExperimentThemeStyle } from '../../utils/experimentTheme'

const route = useRoute()
const router = useRouter()
const queryClient = useQueryClient()
const experimentId = computed(() => Number(route.params.id))
const materialsRef = ref<HTMLElement | null>(null)
const emptyExploration: ExperimentExplorationDto = { currentTry: '', favoriteTries: [], records: [] }
const explorationQuery = useQuery({
  queryKey: computed(() => ['experiment-exploration', experimentId.value]),
  queryFn: () => experimentApi.getExploration(experimentId.value),
})
const exploration = computed(() => explorationQuery.data.value ?? emptyExploration)
const latestObservation = computed(() => {
  const latest = exploration.value.records[0]
  return latest ? { text: latest.discovery, createdAt: latest.createdAt } : null
})
const explorationEditorVisible = ref(false)
const explorationEditorMode = ref<'TRY' | 'DISCOVERY'>('TRY')
const editingExplorationRecord = ref<ExperimentExplorationRecordDto | null>(null)
const historyDialogVisible = ref(false)
const exportVisible = ref(false)
const exportCreateVisible = ref(false)
const exportDestination = ref<'NEW' | 'EXISTING'>('NEW')
const exportTopic = ref('')
const exportTitle = ref('')
const selectedExportRecordIds = ref<number[]>([])
const selectedExportCardId = ref<number | null>(null)
const exportCardInitialData = ref<Partial<CardDto>>({})
const pendingExportRecordIds = ref<number[]>([])
const isExporting = ref(false)

watch(experimentId, () => {
  explorationEditorVisible.value = false
  historyDialogVisible.value = false
  exportVisible.value = false
})

const setExploration = (value: ExperimentExplorationDto) => {
  queryClient.setQueryData(['experiment-exploration', experimentId.value], value)
  void queryClient.invalidateQueries({ queryKey: ['experiments'] })
}

const openExplorationEditor = (mode: 'TRY' | 'DISCOVERY', record: ExperimentExplorationRecordDto | null = null) => {
  explorationEditorMode.value = mode
  editingExplorationRecord.value = record
  explorationEditorVisible.value = true
}

const clearExploration = async () => {
  try {
    await ElMessageBox.confirm('清除目前試法、探索紀錄與常用試法？已整理成卡片的內容不會被刪除。此操作無法復原。', '清除探索資料', {
      confirmButtonText: '清除', cancelButtonText: '取消', type: 'warning',
    })
  } catch {
    return
  }

  try {
    await experimentApi.clearExploration(experimentId.value)
    setExploration({ ...emptyExploration })
    ElMessage.success('探索資料已清除；既有卡片仍保留。')
  } catch {
    ElMessage.error('清除探索資料失敗，請稍後再試')
  }
}

const deleteExplorationRecord = async (recordId: number) => {
  try {
    await ElMessageBox.confirm('刪除這筆探索紀錄？已整理成卡片的內容仍會保留。', '刪除探索紀錄', {
      confirmButtonText: '刪除', cancelButtonText: '取消', type: 'warning',
    })
  } catch {
    return
  }

  try {
    await experimentApi.deleteExplorationRecord(experimentId.value, recordId)
    await queryClient.invalidateQueries({ queryKey: ['experiment-exploration', experimentId.value] })
    void queryClient.invalidateQueries({ queryKey: ['experiments'] })
    ElMessage.success('探索紀錄已刪除。')
  } catch {
    ElMessage.error('刪除探索紀錄失敗，請稍後再試')
  }
}

const openExport = () => {
  const experiment = experimentQuery.data.value
  selectedExportRecordIds.value = exportRecords.value.map(record => record.id)
  exportDestination.value = 'NEW'
  exportTopic.value = experiment?.title ?? ''
  exportTitle.value = ''
  selectedExportCardId.value = null
  pendingExportRecordIds.value = []
  exportVisible.value = true
}

const organizeFromHistory = () => {
  historyDialogVisible.value = false
  openExport()
}

const toggleExportRecord = (recordId: number) => {
  selectedExportRecordIds.value = selectedExportRecordIds.value.includes(recordId)
    ? selectedExportRecordIds.value.filter(id => id !== recordId)
    : [...selectedExportRecordIds.value, recordId]
}

const openCreateExportCard = () => {
  if (!canExport.value) return
  pendingExportRecordIds.value = [...exportableRecordIds.value]
  exportCardInitialData.value = {
    type: 'note',
    title: resolvedExportTitle.value,
    content: exportContent.value,
  }
  exportVisible.value = false
  exportCreateVisible.value = true
}

const createExportCard = async (request: CardContentRequest) => {
  const card = await experimentApi.createExplorationCard(experimentId.value, {
    ...request,
    recordIds: pendingExportRecordIds.value,
  })
  pendingExportRecordIds.value = []
  return card
}

const handleExportCardCreated = async () => {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: ['cards'] }),
    queryClient.invalidateQueries({ queryKey: ['experiment', experimentId.value] }),
    queryClient.invalidateQueries({ queryKey: ['experiment-cards', experimentId.value] }),
    queryClient.invalidateQueries({ queryKey: ['experiment-exploration', experimentId.value] }),
    queryClient.invalidateQueries({ queryKey: ['experiments'] }),
  ])
  ElMessage.success('探索紀錄已整理成新卡，並放入茁壯土壤；原紀錄仍保留。')
}

const appendToExistingCard = async () => {
  if (!canExport.value || !selectedExportCard.value) return
  isExporting.value = true
  try {
    await experimentApi.appendExplorationToCard(
      experimentId.value,
      selectedExportCard.value.cardId,
      exportableRecordIds.value,
      exportContent.value,
    )
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['cards'] }),
      queryClient.invalidateQueries({ queryKey: ['experiment-exploration', experimentId.value] }),
      queryClient.invalidateQueries({ queryKey: ['experiments'] }),
    ])
    exportVisible.value = false
    ElMessage.success('探索紀錄已加入卡片內容；原紀錄仍保留。')
  } catch {
    ElMessage.error('加入卡片內容失敗，探索紀錄尚未標記為已整理。')
  } finally {
    isExporting.value = false
  }
}

const stages: Array<{ value: ExperimentStage; title: string; hint: string }> = [
  { value: 'SEED', title: '🌱 種子土壤', hint: '值得繼續接觸、等待彼此呼應的材料。' },
  { value: 'GROWING', title: '🌿 茁壯土壤', hint: '已長出新的理解、嘗試或行動。' },
  { value: 'MATURE', title: '🌳 成熟土壤', hint: '這一輪已形成可回看的理解或成果。' }
]

const getStageControlLabel = (stage: { title: string }) => stage.title.replace('土壤', '')

const stagePages = reactive<Record<ExperimentStage, number>>({ SEED: 0, GROWING: 0, MATURE: 0 })
const selectedStage = ref<'ALL' | ExperimentStage>('ALL')
const comparingCards = ref<ExperimentCardDto[]>([])
const comparisonVisible = ref(false)
watch(experimentId, () => {
  selectedStage.value = 'ALL'
  comparingCards.value = []
  comparisonVisible.value = false
})
const isComparing = (cardId: number) => comparingCards.value.some(card => card.cardId === cardId)
const toggleCompare = (card: ExperimentCardDto) => {
  if (isComparing(card.cardId)) {
    comparingCards.value = comparingCards.value.filter(selected => selected.cardId !== card.cardId)
  } else if (comparingCards.value.length < 2) {
    comparingCards.value = [...comparingCards.value, card]
  } else {
    ElMessage.info('一次最多比較兩張卡片；請先移出其中一張。')
  }
}
const editVisible = ref(false)
const editForm = reactive<ExperimentRequest>({ title: '', hypothesis: '', description: '', themeColor: 'LEAF' })
const addVisible = ref(false)
const addStage = ref<ExperimentStage>('SEED')
const addNote = ref('')
const noteVisible = ref(false)
const editingNoteCard = ref<ExperimentCardDto | null>(null)
const noteDraft = ref('')
const growVisible = ref(false)
const growContext = reactive<{
  sourceCardIds: number[]
  stage: ExperimentStage
  note: string
}>({
  sourceCardIds: [],
  stage: 'GROWING',
  note: ''
})

const experimentQuery = useQuery({
  queryKey: computed(() => ['experiment', experimentId.value]),
  queryFn: () => experimentApi.getExperiment(experimentId.value)
})

const stageCount = (stage: ExperimentStage) => {
  const experiment = experimentQuery.data.value
  if (!experiment) return 0
  if (stage === 'SEED') return experiment.seedCount
  if (stage === 'GROWING') return experiment.growingCount
  return experiment.matureCount
}

const stageQueries = Object.fromEntries(stages.map(({ value }) => [value, useQuery({
  queryKey: computed(() => ['experiment-cards', experimentId.value, value, stagePages[value]]),
  queryFn: () => experimentApi.getExperimentCards(experimentId.value, value, stagePages[value])
})])) as Record<ExperimentStage, ReturnType<typeof useQuery>>

const sourceCardsQuery = useQuery({
  queryKey: computed(() => ['experiment-source-cards', experimentId.value]),
  enabled: computed(() => addVisible.value || growVisible.value || exportVisible.value),
  queryFn: async () => {
    const loadStage = async (stage: ExperimentStage) => {
      const first = await experimentApi.getExperimentCards(experimentId.value, stage, 0, 20)
      if (first.totalPages <= 1) return first.content
      const rest = await Promise.all(
        Array.from({ length: first.totalPages - 1 }, (_, index) =>
          experimentApi.getExperimentCards(experimentId.value, stage, index + 1, 20))
      )
      return [first, ...rest].flatMap(page => page.content)
    }
    return (await Promise.all(stages.map(stage => loadStage(stage.value)))).flat()
  }
})

const sourceCards = computed(() => sourceCardsQuery.data.value ?? [])
const plantedCardIds = computed(() => new Set(sourceCards.value.map(card => String(card.cardId))))
const exportRecords = computed(() => exploration.value.records)
const selectedExportRecords = computed(() => exportRecords.value
  .filter(record => selectedExportRecordIds.value.includes(record.id))
  .sort((left, right) => new Date(left.createdAt).getTime() - new Date(right.createdAt).getTime()))
const selectedExportCard = computed(() => sourceCards.value.find(card => card.cardId === selectedExportCardId.value) ?? null)
const exportableRecords = computed(() => {
  if (exportDestination.value !== 'EXISTING' || !selectedExportCardId.value) return selectedExportRecords.value
  return selectedExportRecords.value
    .filter(record => !record.exports.some(recordExport => recordExport.cardId === selectedExportCardId.value))
})
const exportableRecordIds = computed(() => exportableRecords.value.map(record => record.id))
const resolvedExportTopic = computed(() => {
  const experiment = experimentQuery.data.value
  return trimToTextLength(exportTopic.value, 255).trim() || experiment?.title || '未命名主題'
})
const exportTimeRange = computed(() => {
  const records = exportableRecords.value
  if (!records.length) return ''
  const start = formatDate(records[0].createdAt, 'YYYY/MM/DD HH:mm')
  const end = formatDate(records.at(-1)?.createdAt, 'YYYY/MM/DD HH:mm')
  return start === end ? start : `${start}–${end}`
})
const defaultExportTitle = computed(() => `【探索紀錄｜${exportTimeRange.value}｜${resolvedExportTopic.value}】`)
const resolvedExportTitle = computed(() => trimToTextLength(exportTitle.value, 255).trim() || defaultExportTitle.value)
const exportContent = computed(() => {
  if (!exportableRecords.value.length) return ''
  const rows = exportableRecords.value.map(record => [
    formatDate(record.createdAt, 'YYYY/MM/DD HH:mm'),
    record.tryText ? `試：${record.tryText}` : null,
    `發現：${record.discovery}`,
  ].filter((line): line is string => Boolean(line)).join('\n'))
  return [defaultExportTitle.value, ...rows].join('\n\n')
})
const canExport = computed(() => selectedExportRecordIds.value.length > 0
  && (exportDestination.value === 'NEW' || Boolean(selectedExportCardId.value))
  && exportableRecordIds.value.length > 0)

const invalidateExperiment = async () => {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: ['experiment', experimentId.value] }),
    queryClient.invalidateQueries({ queryKey: ['experiment-cards', experimentId.value] }),
    queryClient.invalidateQueries({ queryKey: ['experiment-source-cards', experimentId.value] }),
    queryClient.invalidateQueries({ queryKey: ['experiments'] })
  ])
}

const editMutation = useMutation({
  mutationFn: () => experimentApi.updateExperiment(experimentId.value, editForm),
  onSuccess: async () => {
    await invalidateExperiment()
    editVisible.value = false
    ElMessage.success('實驗主題已更新')
  },
  onError: () => ElMessage.error('更新實驗主題失敗')
})

const archiveMutation = useMutation({
  mutationFn: (archived: boolean) => experimentApi.setExperimentArchived(experimentId.value, archived),
  onSuccess: async (experiment) => {
    await invalidateExperiment()
    ElMessage.success(experiment.isArchived ? '實驗主題已封存' : '實驗主題已恢復')
  },
  onError: () => ElMessage.error('更新實驗主題狀態失敗')
})

const deleteMutation = useMutation({
  mutationFn: () => experimentApi.deleteExperiment(experimentId.value),
  onSuccess: async () => {
    await queryClient.invalidateQueries({ queryKey: ['experiments'] })
    ElMessage.success('實驗主題已永久刪除；原始卡片仍保留')
    router.replace('/experiments')
  },
  onError: () => ElMessage.error('刪除實驗主題失敗，請稍後再試')
})

const addMutation = useMutation({
  mutationFn: (card: CardDto) => experimentApi.addExperimentCard(experimentId.value, {
    cardId: Number(card.id),
    stage: addStage.value,
    note: addNote.value
  }),
  onSuccess: async () => {
    await invalidateExperiment()
    addVisible.value = false
    addNote.value = ''
    ElMessage.success('卡片已種入實驗主題')
  },
  onError: () => ElMessage.error('無法種入卡片；請確認它是否已在這個實驗主題中')
})

const stageMutation = useMutation({
  mutationFn: ({ card, stage }: { card: ExperimentCardDto; stage: ExperimentStage }) =>
    experimentApi.updateExperimentCardStage(experimentId.value, card.cardId, stage),
  onSuccess: async () => {
    await invalidateExperiment()
    ElMessage.success('所在土壤已調整')
  },
  onError: () => ElMessage.error('調整土壤失敗')
})

const noteMutation = useMutation({
  mutationFn: () => experimentApi.updateExperimentCardNote(experimentId.value, editingNoteCard.value!.cardId, noteDraft.value),
  onSuccess: async () => {
    await invalidateExperiment()
    noteVisible.value = false
    ElMessage.success('種植備註已更新')
  },
  onError: () => ElMessage.error('更新種植備註失敗')
})

const removeMutation = useMutation({
  mutationFn: (card: ExperimentCardDto) => experimentApi.removeExperimentCard(experimentId.value, card.cardId),
  onSuccess: async (_data, card) => {
    comparingCards.value = comparingCards.value.filter(selected => selected.cardId !== card.cardId)
    await invalidateExperiment()
    ElMessage.success('卡片已移出實驗主題')
  },
  onError: () => ElMessage.error('移出卡片失敗')
})

const goBack = () => window.history.state?.back ? router.back() : router.push('/experiments')

const openAdd = () => {
  addStage.value = 'SEED'
  addNote.value = ''
  addVisible.value = true
}

const confirmDeleteExperiment = async () => {
  const experiment = experimentQuery.data.value
  if (!experiment || deleteMutation.isPending.value) return
  try {
    await ElMessageBox.confirm(
      `「${experiment.title}」及其種植關聯、卡片衍生脈絡都會永久刪除；原始卡片與長出的新卡不會被刪除。`,
      '永久刪除實驗主題',
      { confirmButtonText: '永久刪除', cancelButtonText: '取消', type: 'warning' }
    )
    deleteMutation.mutate()
  } catch {
    // 使用者取消刪除，不需處理。
  }
}

const openGrow = () => {
  Object.assign(growContext, {
    sourceCardIds: [],
    stage: 'GROWING',
    note: ''
  })
  growVisible.value = true
}

const growFromComparison = () => {
  comparisonVisible.value = false
  openGrow()
  growContext.sourceCardIds = comparingCards.value.map(card => card.cardId)
}

const openEdit = () => {
  const experiment = experimentQuery.data.value
  if (!experiment) return
  editForm.title = experiment.title
  editForm.hypothesis = experiment.hypothesis ?? ''
  editForm.description = experiment.description ?? ''
  editForm.themeColor = experiment.themeColor
  editVisible.value = true
}

const submitEdit = () => {
  editForm.title = trimToTextLength(editForm.title, 255).trim()
  editForm.hypothesis = trimToTextLength(editForm.hypothesis ?? '', 2000).trim()
  editForm.description = trimToTextLength(editForm.description ?? '', 5000)
  if (editForm.title) editMutation.mutate()
}

const openNote = (card: ExperimentCardDto) => {
  editingNoteCard.value = card
  noteDraft.value = card.note ?? ''
  noteVisible.value = true
}

const confirmRemove = async (card: ExperimentCardDto) => {
  try {
    await ElMessageBox.confirm(
      `確定要把「${card.cardTitle}」移出這個實驗主題？卡片本體不會被刪除。`,
      '移出實驗主題',
      { type: 'warning', confirmButtonText: '移出', cancelButtonText: '取消' }
    )
    removeMutation.mutate(card)
  } catch {
    // 使用者取消，不需處理。
  }
}

const toggleSource = (cardId: number) => {
  growContext.sourceCardIds = growContext.sourceCardIds.includes(cardId)
    ? growContext.sourceCardIds.filter(id => id !== cardId)
    : [...growContext.sourceCardIds, cardId]
}

const createGrownCard = (request: CardContentRequest) => experimentApi.growCard(experimentId.value, {
  ...request,
  sourceCardIds: growContext.sourceCardIds,
  stage: growContext.stage,
  note: trimToTextLength(growContext.note, 1000).trim() || undefined,
})

const handleGrownCard = async (_card: CardDto) => {
  await invalidateExperiment()
  ElMessage.success('新卡已長出，思想脈絡也已保留')
}

</script>

<template>
  <section class="experiment-detail-page app-page">
    <header class="detail-navigation app-detail-navigation">
      <el-button :icon="ArrowLeft" text @click="goBack">返回實驗場</el-button>
      <div v-if="experimentQuery.data.value" class="detail-actions">
        <el-dropdown trigger="click" @command="archiveMutation.mutate($event === 'archive')">
          <button type="button" class="status-trigger" :disabled="archiveMutation.isPending.value">
            <span class="property-status-dot" :class="experimentQuery.data.value.isArchived ? 'property-status-archived' : 'property-status-active'"></span>
            <span>{{ experimentQuery.data.value.isArchived ? '已封存' : '觀察中' }}</span>
            <el-icon class="status-trigger-arrow"><ArrowDown /></el-icon>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item :command="experimentQuery.data.value.isArchived ? 'restore' : 'archive'">
                {{ experimentQuery.data.value.isArchived ? '恢復實驗主題' : '封存實驗主題' }}
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <div class="detail-edit-actions">
          <el-button type="primary" plain :icon="Edit" @click="openEdit">編輯實驗主題</el-button>
          <el-button type="danger" plain :icon="Delete" :loading="deleteMutation.isPending.value" @click="confirmDeleteExperiment">永久刪除</el-button>
        </div>
      </div>
    </header>

    <div v-if="experimentQuery.isLoading.value" class="state-surface"><el-skeleton :rows="8" animated /></div>
    <div v-else-if="experimentQuery.isError.value" class="state-surface">
      <el-result icon="error" title="無法載入實驗主題" sub-title="實驗主題可能不存在，或目前無法連線。">
        <template #extra><el-button @click="goBack">返回列表</el-button><el-button type="primary" @click="experimentQuery.refetch()">重新載入</el-button></template>
      </el-result>
    </div>

    <main v-else-if="experimentQuery.data.value" class="experiment-detail-content" :style="getExperimentThemeStyle(experimentQuery.data.value.themeColor)">
      <section class="experiment-overview-panel">
        <div class="experiment-heading">
          <div>
            <span class="detail-eyebrow">實驗主題</span>
            <h1>{{ experimentQuery.data.value.title }}</h1>
          </div>
        </div>
      </section>

      <section class="experiment-inquiry" aria-labelledby="experiment-inquiry-title">
        <header class="section-heading">
          <h2 id="experiment-inquiry-title">目前想弄懂</h2>
        </header>
        <ExpandableText
          v-if="experimentQuery.data.value.hypothesis"
          class="experiment-hypothesis"
          :content="experimentQuery.data.value.hypothesis"
          :lines="5"
        />
        <p v-else class="quiet-empty">還沒有寫下問題；也可以先從探索或材料開始。</p>
      </section>

      <section class="exploration-workspace" aria-label="這次想怎麼探索">
        <header class="exploration-heading">
          <h2>這次想怎麼探索？</h2>
          <div v-if="!explorationQuery.isLoading.value && !explorationQuery.isError.value" class="exploration-actions">
            <time v-if="latestObservation?.createdAt" :datetime="latestObservation.createdAt">{{ formatDate(latestObservation.createdAt) }}</time>
            <button v-if="exploration.records.length" type="button" class="exploration-history-trigger" @click="historyDialogVisible = true">
              <span>探索紀錄</span>
              <span class="exploration-record-count">{{ exploration.records.length }}</span>
            </button>
          </div>
        </header>
        <el-skeleton v-if="explorationQuery.isLoading.value" :rows="3" animated />
        <el-result v-else-if="explorationQuery.isError.value" icon="error" title="無法載入探索資料" sub-title="請稍後再試。">
          <template #extra><el-button type="primary" @click="explorationQuery.refetch()">重新載入</el-button></template>
        </el-result>
        <template v-else>
        <div class="exploration-overview">
          <div :class="['exploration-snapshot', 'current-try-snapshot', { 'is-next-action': !exploration.currentTry }]">
            <div class="exploration-snapshot-heading">
              <span>{{ exploration.currentTry ? '目前想試' : '1．設定試法' }}</span>
              <el-button :text="Boolean(exploration.currentTry)" type="primary" size="small" @click="openExplorationEditor('TRY')">{{ exploration.currentTry ? '編輯試法' : '設定試法' }}</el-button>
            </div>
            <p :class="{ empty: !exploration.currentTry }">{{ exploration.currentTry || '還沒有想試的事，可以先看看材料。' }}</p>
          </div>
          <div :class="['exploration-snapshot', { 'is-next-action': Boolean(exploration.currentTry) && !latestObservation }]">
            <div class="exploration-snapshot-heading">
              <span>{{ latestObservation ? '最近一次觀察' : '2．記下發現' }}</span>
              <el-button :text="!exploration.currentTry || Boolean(latestObservation)" type="primary" size="small" @click="openExplorationEditor('DISCOVERY')">{{ exploration.currentTry || latestObservation ? '記下發現' : '直接記下發現' }}</el-button>
            </div>
            <p :class="{ empty: !latestObservation }">{{ latestObservation?.text || (exploration.currentTry ? '試過、沒試成或改變想法，都可以記在這裡。' : '設定試法後，再留下觀察；也可以直接記錄現在的發現。') }}</p>
          </div>
        </div>
        <div class="exploration-footnote">
          <span>探索紀錄會同步保存；整理成卡片後，原紀錄仍會保留。</span>
          <div class="exploration-utility-actions">
            <el-button text @click="materialsRef?.scrollIntoView({ behavior: 'smooth', block: 'start' })">看看材料</el-button>
            <el-button v-if="exploration.currentTry || exploration.records.length || exploration.favoriteTries.length" text type="danger" size="small" @click="clearExploration">清除探索資料</el-button>
          </div>
        </div>
        </template>
      </section>

      <section ref="materialsRef" class="soil-workspace" aria-label="實驗主題中的卡片土壤">
        <header class="soil-workspace-heading">
          <div><h2>材料與線索</h2><p>土壤表示卡片在這裡的角色，不必依序前進。選一兩張卡可以並排閱讀。</p></div>
          <div class="material-primary-actions">
            <el-button type="primary" :icon="Plus" @click="openAdd">種入卡片</el-button>
            <el-button :icon="MagicStick" :disabled="experimentQuery.data.value.seedCount + experimentQuery.data.value.growingCount + experimentQuery.data.value.matureCount === 0" @click="openGrow()">長出新卡</el-button>
          </div>
        </header>
        <div class="soil-toolbar">
          <div class="soil-filters" role="group" aria-label="依卡片角色篩選">
            <button type="button" :class="{ active: selectedStage === 'ALL' }" :aria-pressed="selectedStage === 'ALL'" @click="selectedStage = 'ALL'">全部</button>
            <button v-for="stage in stages" :key="stage.value" type="button" :class="{ active: selectedStage === stage.value }" :aria-pressed="selectedStage === stage.value" @click="selectedStage = stage.value">
              {{ getStageControlLabel(stage) }} {{ stageCount(stage.value) }}
            </button>
          </div>
          <el-button :disabled="comparingCards.length === 0" @click="comparisonVisible = true">並排看材料{{ comparingCards.length ? `（${comparingCards.length}/2）` : '' }}</el-button>
        </div>
        <el-empty v-if="selectedStage === 'ALL' && experimentQuery.data.value.seedCount + experimentQuery.data.value.growingCount + experimentQuery.data.value.matureCount === 0" description="還沒有材料。先放入一張讓你想繼續看的卡片即可。" :image-size="72" />
        <div class="soil-collection-canvas">
          <div class="soil-grid">
          <template v-for="stage in stages" :key="stage.value">
          <section v-if="selectedStage === stage.value || (selectedStage === 'ALL' && stageCount(stage.value) > 0)" :class="['soil-column', `soil-column-${stage.value.toLowerCase()}`]">
            <header class="soil-heading">
              <div><h3>{{ stage.title }}</h3><p>{{ stage.hint }}</p></div>
              <span>{{ stageCount(stage.value) }}</span>
            </header>
            <el-skeleton v-if="stageQueries[stage.value].isLoading.value" :rows="3" animated />
            <el-empty v-else-if="!(stageQueries[stage.value].data.value as any)?.content?.length" description="這塊土壤還沒有卡片" :image-size="56" />
            <template v-else>
              <div class="experiment-card-list">
                <ExperimentCardItem v-for="card in (stageQueries[stage.value].data.value as any).content" :key="card.cardId" :card="card" :stages="stages" :comparing="isComparing(card.cardId)" @open="router.push(`/card/${$event}`)" @toggle-compare="toggleCompare" @change-stage="(card, target) => stageMutation.mutate({ card, stage: target })" @edit-note="openNote" @remove="confirmRemove" />
              </div>
              <el-pagination v-if="(stageQueries[stage.value].data.value as any).totalElements > 10" class="soil-pagination" small layout="prev, pager, next" :current-page="stagePages[stage.value] + 1" :page-size="10" :total="(stageQueries[stage.value].data.value as any).totalElements" @current-change="stagePages[stage.value] = $event - 1" />
            </template>
          </section>
          </template>
        </div>
        </div>
      </section>

      <section class="experiment-supporting" aria-label="實驗主題補充資訊">
        <details>
          <summary>主題說明</summary>
          <div class="supporting-content">
            <ExpandableText
              v-if="experimentQuery.data.value.description"
              :content="experimentQuery.data.value.description"
              :lines="10"
            />
            <p v-else class="quiet-empty">尚未補充主題說明。</p>
          </div>
        </details>
        <dl class="experiment-metadata">
          <div><dt>建立時間</dt><dd>{{ formatDate(experimentQuery.data.value.createdAt) }}</dd></div>
          <div><dt>最近活動</dt><dd>{{ formatDate(experimentQuery.data.value.updatedAt) }}</dd></div>
        </dl>
      </section>
    </main>
  </section>

  <ExperimentExplorationHistoryDialog
    v-model="historyDialogVisible"
    :records="exploration.records"
    @edit="openExplorationEditor('DISCOVERY', $event)"
    @delete="deleteExplorationRecord"
    @organize="organizeFromHistory"
    @open-card="router.push(`/card/${$event}`)"
  />

  <ExperimentExplorationEditor
    v-model="explorationEditorVisible"
    :experiment-id="experimentId"
    :mode="explorationEditorMode"
    :record="editingExplorationRecord"
    @closed="editingExplorationRecord = null"
  />

  <AppDialog v-model="exportVisible" title="整理探索紀錄" width="min(980px, calc(100vw - 32px))" scroll-body>
    <p class="dialog-intro">把選取的探索紀錄整理成 Card 內容。成功後會保留原紀錄，並標示它已整理到哪張卡片。</p>
    <div class="export-workspace">
      <section class="export-selection-pane">
        <h3>選擇探索紀錄</h3>
        <div class="export-record-list">
          <el-checkbox
            v-for="record in exportRecords"
            :key="record.id"
            class="multi-select-option export-record-option"
            :class="{ selected: selectedExportRecordIds.includes(record.id) }"
            :model-value="selectedExportRecordIds.includes(record.id)"
            @change="toggleExportRecord(record.id)"
          >
            <span class="export-record-content">
              <time>{{ formatDate(record.createdAt, 'YYYY/MM/DD HH:mm') }}</time>
              <span class="export-record-fields">
                <span><small>試法</small>{{ record.tryText || '這次直接留下發現。' }}</span>
                <span><small>發現</small>{{ record.discovery }}</span>
              </span>
            </span>
          </el-checkbox>
        </div>
      </section>
      <div class="export-settings-pane">
        <section class="export-dialog-section">
          <h3>整理到哪裡？</h3>
          <el-radio-group v-model="exportDestination">
            <el-radio-button value="NEW">新增卡片</el-radio-button>
            <el-radio-button value="EXISTING">加入既有卡片</el-radio-button>
          </el-radio-group>
          <template v-if="exportDestination === 'EXISTING'">
            <el-select v-model="selectedExportCardId" class="export-card-select" placeholder="選擇這個實驗場中的卡片" :loading="sourceCardsQuery.isLoading.value">
              <el-option v-for="card in sourceCards" :key="card.cardId" :label="card.cardTitle" :value="card.cardId" />
            </el-select>
            <p v-if="!sourceCardsQuery.isLoading.value && !sourceCards.length" class="dialog-hint">這個實驗場目前沒有卡片可加入。</p>
            <p v-else-if="selectedExportCard && !exportableRecordIds.length" class="dialog-hint">所選紀錄都已整理到這張卡片；可改選其他紀錄或其他卡片。</p>
          </template>
        </section>
        <section class="export-dialog-section">
          <label for="export-topic">主題</label>
          <el-input id="export-topic" v-model="exportTopic" maxlength="255" placeholder="例如：畫畫來來來" />
          <p class="dialog-hint">會出現在整理內容的標頭；可自由修改。</p>
          <template v-if="exportDestination === 'NEW'">
            <label for="export-title">卡片標題（選填）</label>
            <el-input id="export-title" v-model="exportTitle" maxlength="255" :placeholder="defaultExportTitle" />
            <p class="dialog-hint">留空時會自動使用上方格式；下一步仍可編輯完整卡片。</p>
          </template>
        </section>
        <section v-if="exportContent" class="export-preview">
          <span>將寫入的內容</span>
          <pre>{{ exportContent }}</pre>
        </section>
      </div>
    </div>
    <template #footer>
      <el-button :disabled="isExporting" @click="exportVisible = false">取消</el-button>
      <el-button v-if="exportDestination === 'NEW'" type="primary" :disabled="!canExport" @click="openCreateExportCard">繼續建立卡片</el-button>
      <el-button v-else type="primary" :loading="isExporting" :disabled="!canExport" @click="appendToExistingCard">加入卡片內容</el-button>
    </template>
  </AppDialog>

  <QuickCreateCardDialog
    v-model="exportCreateVisible"
    title="建立探索紀錄卡片"
    description="已帶入整理內容；建立後會自動放入目前實驗場的茁壯土壤。"
    create-label="建立卡片"
    success-title="探索紀錄卡片已建立"
    success-description="卡片已放入茁壯土壤；原探索紀錄仍保留。"
    :initial-data="exportCardInitialData"
    :create-card="createExportCard"
    create-error-message="建立探索紀錄卡片失敗，請稍後再試"
    @created="handleExportCardCreated"
  />

  <AppDialog v-model="comparisonVisible" title="並排看材料" class="experiment-comparison-dialog" width="min(900px, calc(100vw - 32px))" scroll-body>
    <section class="comparison-workspace" aria-label="並排比較卡片">
      <div class="comparison-grid">
        <article v-for="card in comparingCards" :key="card.cardId" class="comparison-card">
          <button type="button" class="comparison-card-title" @click="router.push(`/card/${card.cardId}`)">{{ card.cardTitle }}</button>
          <p v-if="card.cardNote"><span>卡片筆記</span>{{ card.cardNote }}</p>
          <p v-if="card.note"><span>在此主題的備註</span>{{ card.note }}</p>
          <p v-if="!card.cardNote && !card.note" class="comparison-empty">這張卡尚無筆記或實驗場備註；可點標題閱讀更多內容。</p>
        </article>
        <div v-if="comparingCards.length === 1" class="comparison-placeholder">可以再從材料列表選一張卡，看看它們有什麼相同或不同。</div>
      </div>
      <p class="comparison-hint">可以先保留這些材料；有值得日後再遇見的想法時，再長出新卡。</p>
    </section>
    <template #footer>
      <el-button @click="comparisonVisible = false">返回材料</el-button>
      <el-button type="primary" :icon="MagicStick" @click="growFromComparison">從所選卡片長出新卡</el-button>
    </template>
  </AppDialog>

  <AppDialog v-model="editVisible" title="編輯實驗主題" width="min(680px, calc(100vw - 32px))" scroll-body>
    <el-form label-position="top" @submit.prevent="submitEdit">
      <el-form-item label="實驗主題名稱" required>
        <el-input v-model="editForm.title" maxlength="255" />
        <p class="field-counter">總字數：{{ getTextLength(editForm.title) }} / 255</p>
      </el-form-item>
      <el-form-item label="目前想弄懂什麼？（選填）">
        <el-input v-model="editForm.hypothesis" type="textarea" :rows="3" maxlength="2000" />
        <p class="field-counter">總字數：{{ getTextLength(editForm.hypothesis ?? '') }} / 2000</p>
      </el-form-item>
      <el-form-item label="主題說明（選填）">
        <el-input v-model="editForm.description" type="textarea" :rows="3" maxlength="5000" />
        <p class="field-counter">總字數：{{ getTextLength(editForm.description ?? '') }} / 5000</p>
      </el-form-item>
      <el-form-item label="識別色">
        <div class="theme-picker">
          <button v-for="option in experimentThemeOptions" :key="option.value" type="button" class="theme-option" :class="{ active: editForm.themeColor === option.value }" :style="{ '--option-color': option.vividColor }" :aria-pressed="editForm.themeColor === option.value" @click="editForm.themeColor = option.value">
            <span aria-hidden="true"></span>{{ option.label }}
          </button>
        </div>
      </el-form-item>
    </el-form>
    <template #footer><el-button @click="editVisible = false">取消</el-button><el-button type="primary" :loading="editMutation.isPending.value" :disabled="!editForm.title.trim()" @click="submitEdit">儲存變更</el-button></template>
  </AppDialog>

  <CardPickerDialog
    v-model="addVisible"
    title="種入卡片"
    description="從曾經保存的卡片中，選擇適合種進這個實驗主題的內容。已種入的卡片不會重複顯示；封存與暫停回流不影響種植關係。"
    archive-status="ALL"
    confirm-label="種入卡片"
    settings-style="plain"
    :excluded-card-ids="Array.from(plantedCardIds)"
    :submitting="addMutation.isPending.value"
    empty-description="沒有其他可種入的卡片"
    @confirm="addMutation.mutate"
  >
    <template #settings="{ selectedCard }">
      <header class="plant-settings-heading">
        <h3>實驗場設定</h3>
        <p>選取卡片後，設定它在這個實驗主題中的位置。</p>
      </header>
      <CardPickerSelectionSummary :card="selectedCard" class="plant-selected-card" />
      <el-form label-position="top">
        <el-form-item label="放入土壤"><el-radio-group v-model="addStage"><el-radio-button v-for="stage in stages" :key="stage.value" :value="stage.value">{{ getStageControlLabel(stage) }}</el-radio-button></el-radio-group></el-form-item>
        <el-form-item label="種植備註（選填）"><el-input v-model="addNote" type="textarea" :rows="4" maxlength="1000" placeholder="這張卡為什麼適合放在這個實驗主題？" /><p class="field-counter">總字數：{{ getTextLength(addNote) }} / 1000</p></el-form-item>
      </el-form>
    </template>
  </CardPickerDialog>

  <AppDialog v-model="noteVisible" title="編輯種植備註" width="min(560px, calc(100vw - 32px))">
    <p v-if="editingNoteCard" class="dialog-card-title">{{ editingNoteCard.cardTitle }}</p>
    <el-input v-model="noteDraft" type="textarea" :rows="5" maxlength="1000" placeholder="這張卡為什麼在這個實驗主題？" />
    <p class="field-counter">總字數：{{ getTextLength(noteDraft) }} / 1000</p>
    <p class="dialog-hint">清空內容並儲存即可移除備註。</p>
    <template #footer><el-button @click="noteVisible = false">取消</el-button><el-button type="primary" :loading="noteMutation.isPending.value" @click="noteMutation.mutate()">儲存備註</el-button></template>
  </AppDialog>

  <QuickCreateCardDialog
    v-model="growVisible"
    title="從卡片長出新卡"
    create-label="長出新卡"
    success-title="新卡已長出"
    success-description="來源卡與實驗主題中的位置已保留；你可以查看新卡，或繼續記下下一則內容。"
    :can-submit="growContext.sourceCardIds.length > 0"
    :create-card="createGrownCard"
    create-error-message="長出新卡失敗，請確認來源卡片與必填內容"
    layout="split"
    @created="handleGrownCard"
  >
    <template #context>
      <section class="experiment-grow-context">
        <header class="plant-settings-heading">
          <div><h3>實驗場設定</h3><p>來源卡不會被改寫；新卡會保留它長自哪些卡片，以及所在實驗主題的土壤。</p></div>
        </header>
        <el-form-item label="來源卡片" required>
          <el-skeleton v-if="sourceCardsQuery.isLoading.value" :rows="3" animated />
          <div v-else class="source-picker">
            <el-checkbox
              v-for="card in sourceCards"
              :key="card.cardId"
              class="multi-select-option source-card-option"
              :class="{ selected: growContext.sourceCardIds.includes(card.cardId) }"
              :model-value="growContext.sourceCardIds.includes(card.cardId)"
              @change="toggleSource(card.cardId)"
            >
              <span class="source-card-title">{{ card.cardTitle }}</span>
            </el-checkbox>
          </div>
          <p v-if="!sourceCardsQuery.isLoading.value && sourceCards.length === 0" class="field-counter">這個實驗主題還沒有卡片可作為來源。</p>
        </el-form-item>
        <el-form-item label="新卡放入土壤">
          <el-radio-group v-model="growContext.stage">
            <el-radio-button v-for="stage in stages" :key="stage.value" :value="stage.value">{{ getStageControlLabel(stage) }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="種植備註（選填）">
          <el-input v-model="growContext.note" type="textarea" :rows="2" maxlength="1000" placeholder="這張新卡為什麼放在這個實驗主題？" />
          <p class="field-counter">總字數：{{ getTextLength(growContext.note) }} / 1000</p>
        </el-form-item>
      </section>
    </template>
  </QuickCreateCardDialog>
</template>

<style scoped>
.experiment-detail-page {
  width: 100%;
  min-height: 100%;
  box-sizing: border-box;
  background: var(--surface-page);
}
.detail-navigation {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  padding: var(--space-sm) var(--space-md);
  border-bottom: 1px solid var(--el-border-color-light);
  background: var(--el-bg-color);
}
.detail-actions { display: flex; align-items: center; gap: var(--space-xs); }
.detail-edit-actions { display: flex; gap: var(--space-xs); }
.status-trigger { display: inline-flex; align-items: center; justify-content: center; gap: var(--space-xs); min-width: 120px; padding: 7px 10px; border: 1px solid var(--el-border-color); border-radius: var(--radius-sm); background: var(--el-fill-color-blank); color: var(--el-text-color-regular); font: inherit; white-space: nowrap; cursor: pointer; }
.status-trigger:hover, .status-trigger:focus-visible { border-color: var(--el-color-primary-light-5); color: var(--el-color-primary); }
.status-trigger:disabled { cursor: wait; opacity: 0.65; }
.status-trigger-arrow { margin-left: 2px; color: var(--el-text-color-placeholder); }
.property-status-dot { width: 8px; height: 8px; flex: 0 0 auto; border-radius: 50%; background: var(--el-text-color-placeholder); }
.property-status-active { background: var(--el-color-primary); }
.property-status-archived { background: var(--el-text-color-placeholder); }
.state-surface,
.experiment-detail-content {
  width: min(100%, var(--content-max-width));
  margin: 0 auto;
  box-sizing: border-box;
}
.state-surface { padding: var(--panel-padding); }
.experiment-detail-content {
  display: grid;
  gap: var(--space-lg);
  padding: var(--workspace-padding);
}
.experiment-overview-panel { padding: var(--space-md) 0 0; }
.experiment-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--space-lg); }
.detail-eyebrow { display: block; color: var(--el-text-color-placeholder); font-size: var(--type-meta); font-weight: 600; letter-spacing: 0.08em; }
.experiment-heading h1 { margin: var(--space-xs) 0 0; font-size: var(--type-detail-title); line-height: var(--leading-title); overflow-wrap: anywhere; }
.experiment-inquiry,
.exploration-workspace,
.soil-workspace,
.experiment-supporting {
  padding: var(--panel-padding);
  box-sizing: border-box;
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--radius-md);
  background: var(--el-bg-color);
}
.experiment-inquiry { border-left: 3px solid var(--experiment-accent); }
.section-heading { margin-bottom: var(--space-md); }
.section-heading h2 { margin: var(--space-2xs) 0 0; font-size: var(--type-section-title); line-height: var(--leading-section); }
.experiment-hypothesis :deep(.expandable-content) { margin: 0; color: var(--el-text-color-primary); font-size: var(--type-prominent); font-weight: 500; line-height: 1.7; }
.quiet-empty { margin: 0; color: var(--el-text-color-placeholder); font-size: var(--type-caption); line-height: var(--leading-ui); }
.exploration-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--space-md); }
.exploration-heading h2 { margin: 0; font-size: var(--type-section-title); line-height: var(--leading-section); }
.exploration-overview { display: grid; grid-template-columns: minmax(0, 3fr) minmax(0, 2fr); margin-top: var(--space-md); padding: var(--space-md); border: 1px solid var(--el-border-color-lighter); border-radius: var(--radius-md); background: var(--surface-summary); }
.exploration-snapshot { min-width: 0; padding-right: var(--space-lg); }
.exploration-snapshot + .exploration-snapshot { padding-right: 0; padding-left: var(--space-lg); border-left: 1px solid var(--el-border-color-lighter); }
.exploration-snapshot-heading { display: flex; align-items: center; justify-content: space-between; gap: var(--space-sm); }
.exploration-snapshot-heading > span { color: var(--el-text-color-secondary); font-size: var(--type-caption); font-weight: var(--weight-medium); line-height: var(--leading-ui); }
.exploration-snapshot-heading :deep(.el-button) { flex: 0 0 auto; margin-left: 0; }
.exploration-snapshot.is-next-action .exploration-snapshot-heading > span { color: var(--el-color-primary); font-weight: var(--weight-semibold); }
.exploration-snapshot p { margin: var(--space-2xs) 0 0; color: var(--el-text-color-primary); font-size: var(--type-ui); line-height: var(--leading-ui); white-space: pre-line; overflow-wrap: anywhere; }
.exploration-snapshot p.empty { color: var(--el-text-color-placeholder); font-size: var(--type-caption); }
.exploration-actions { display: flex; flex: 0 1 auto; flex-wrap: wrap; justify-content: flex-end; gap: var(--space-xs); }
.exploration-actions :deep(.el-button) { margin-left: 0; }
.exploration-history-trigger { display: inline-flex; align-items: center; gap: var(--space-xs); padding: var(--space-2xs) 0; border: 0; background: transparent; color: var(--el-color-primary); cursor: pointer; font: inherit; font-size: var(--type-caption); line-height: var(--leading-ui); white-space: nowrap; }
.exploration-history-trigger:hover, .exploration-history-trigger:focus-visible { color: var(--el-color-primary-light-3); }
.exploration-record-count { display: inline-flex; align-items: center; justify-content: center; min-width: 22px; min-height: 22px; padding: 0 var(--space-xs); border: 1px solid var(--el-color-primary-light-7); border-radius: var(--radius-sm); background: var(--el-color-primary-light-9); color: var(--el-color-primary); font-size: var(--type-meta); font-weight: var(--weight-semibold); font-variant-numeric: tabular-nums; line-height: 1; }
.exploration-actions time { align-self: center; color: var(--el-text-color-placeholder); font-size: var(--type-meta); line-height: var(--leading-ui); white-space: nowrap; }
.exploration-footnote { display: flex; align-items: center; justify-content: space-between; gap: var(--space-sm); margin-top: var(--space-md); color: var(--el-text-color-placeholder); font-size: var(--type-meta); line-height: var(--leading-ui); }
.exploration-utility-actions { display: flex; flex: 0 0 auto; align-items: center; gap: var(--space-sm); }
.exploration-utility-actions :deep(.el-button) { margin-left: 0; }
.comparison-workspace { padding: 4px 0; }
.comparison-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
.comparison-card, .comparison-placeholder { min-width: 0; padding: 16px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; background: var(--surface-summary); }
.comparison-card-title { padding: 0; border: 0; background: none; color: var(--el-text-color-primary); font: inherit; font-weight: 600; text-align: left; overflow-wrap: anywhere; cursor: pointer; }
.comparison-card-title:hover, .comparison-card-title:focus-visible { color: var(--el-color-primary); }
.comparison-card p { margin: 12px 0 0; color: var(--el-text-color-regular); font-size: var(--type-caption); line-height: var(--leading-ui); white-space: pre-line; overflow-wrap: anywhere; }
.comparison-card p span { display: block; color: var(--el-text-color-placeholder); font-size: var(--type-meta); }
.comparison-placeholder { display: grid; place-items: center; color: var(--el-text-color-secondary); font-size: var(--type-caption); text-align: center; }
.comparison-hint { margin: 16px 0 0; color: var(--el-text-color-secondary); font-size: var(--type-caption); line-height: var(--leading-ui); }
:global(.experiment-comparison-dialog .el-dialog__footer) { display: flex; justify-content: flex-end; flex-wrap: wrap; gap: 8px; }
:global(.experiment-comparison-dialog .el-dialog__footer .el-button) { margin-left: 0; }
.soil-toolbar { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: var(--space-sm); margin-bottom: var(--space-md); }
.soil-filters { display: flex; flex-wrap: wrap; gap: var(--space-xs); }
.soil-filters button { padding: 7px var(--space-sm); border: 1px solid var(--el-border-color); border-radius: var(--radius-sm); background: var(--el-bg-color); color: var(--el-text-color-regular); font: inherit; font-size: var(--type-ui); cursor: pointer; }
.soil-filters button.active { border-color: var(--el-color-primary-light-5); background: var(--el-color-primary-light-9); color: var(--el-color-primary); }
.soil-filters button:hover, .soil-filters button:focus-visible { border-color: var(--el-color-primary-light-5); }
.soil-workspace-heading { display: flex; align-items: center; justify-content: space-between; gap: var(--space-md); margin-bottom: var(--space-md); }
.soil-workspace-heading h2 { margin: 0; font-size: var(--type-section-title); line-height: var(--leading-section); }
.soil-workspace-heading p { margin: var(--space-2xs) 0 0; color: var(--el-text-color-secondary); font-size: var(--type-caption); }
.material-primary-actions { display: flex; flex: 0 0 auto; gap: var(--space-xs); }
.soil-collection-canvas { padding: var(--space-md); border-radius: var(--radius-md); background: var(--surface-collection); }
.soil-grid { display: grid; gap: var(--space-lg); align-items: start; }
.soil-column { min-width: 0; padding-top: var(--space-md); border-top: 1px solid var(--el-border-color-lighter); }
.soil-column-seed { padding-top: 0; border-top: 0; }
.experiment-card-list { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--space-sm); }
.experiment-card-list :deep(.experiment-card-item + .experiment-card-item) { margin-top: 0; }
.soil-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 14px; }
.soil-heading h3 { margin: 0; font-size: var(--type-card-title); }
.soil-heading p { margin: 6px 0 0; color: var(--el-text-color-secondary); font-size: var(--type-caption); line-height: var(--leading-ui); }
.soil-heading > span { color: var(--el-text-color-placeholder); font-size: var(--type-meta); font-variant-numeric: tabular-nums; }
.soil-pagination { justify-content: center; margin-top: 14px; }
.experiment-supporting { padding-block: 0; }
.experiment-supporting details summary { padding: var(--space-md) 0; color: var(--el-text-color-secondary); font-size: var(--type-ui); font-weight: 600; cursor: pointer; }
.experiment-supporting details[open] summary { color: var(--el-text-color-primary); }
.supporting-content { max-width: var(--reading-max-width); padding-bottom: var(--space-lg); }
.supporting-content :deep(.expandable-content) { margin: 0; color: var(--el-text-color-primary); font-size: var(--type-body); line-height: var(--leading-body); }
.experiment-metadata { display: flex; flex-wrap: wrap; gap: var(--space-md) var(--space-lg); margin: 0; padding: var(--space-md) 0; border-top: 1px solid var(--el-border-color-lighter); }
.experiment-metadata dt { color: var(--el-text-color-placeholder); font-size: var(--type-meta); }
.experiment-metadata dd { margin: var(--space-2xs) 0 0; color: var(--el-text-color-secondary); font-size: var(--type-caption); }
.dialog-intro { margin: 0 0 var(--space-md); color: var(--el-text-color-secondary); font-size: var(--type-caption); line-height: var(--leading-ui); }
.export-workspace { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: var(--space-lg); align-items: start; }
.export-selection-pane { min-width: 0; }
.export-selection-pane > h3 { margin: 0 0 var(--space-sm); color: var(--el-text-color-primary); font-size: var(--type-card-title); }
.export-settings-pane { min-width: 0; padding-left: var(--space-lg); border-left: 1px solid var(--el-border-color-lighter); }
.export-dialog-section { display: grid; gap: var(--space-sm); }
.export-dialog-section + .export-dialog-section { margin-top: var(--space-lg); padding-top: var(--space-lg); border-top: 1px solid var(--el-border-color-lighter); }
.export-dialog-section h3, .export-dialog-section > label { margin: 0; color: var(--el-text-color-primary); font-size: var(--type-card-title); }
.export-record-list { display: grid; gap: var(--space-xs); }
.multi-select-option { display: grid; width: 100%; height: auto; grid-template-columns: 18px minmax(0, 1fr); align-items: start; gap: var(--space-xs); box-sizing: border-box; margin: 0; padding: var(--space-xs) var(--space-sm); border: 1px solid var(--el-border-color-lighter); border-radius: var(--radius-md); background: var(--surface-subtle); white-space: normal; }
.multi-select-option.selected { border-color: var(--el-color-primary-light-5); background: var(--el-color-primary-light-9); }
.multi-select-option :deep(.el-checkbox__input) { margin-top: 2px; }
.multi-select-option :deep(.el-checkbox__label) { min-width: 0; padding-left: 0; color: inherit; white-space: normal; }
.export-record-content { display: grid; min-width: 0; gap: 4px; }
.export-record-content time { color: var(--el-text-color-placeholder); font-size: var(--type-meta); }
.export-record-fields { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--space-sm); }
.export-record-fields > span { min-width: 0; color: var(--el-text-color-regular); font-size: var(--type-caption); line-height: var(--leading-ui); overflow-wrap: anywhere; }
.export-record-fields small { display: block; color: var(--el-text-color-placeholder); font-size: var(--type-meta); }
.export-card-select { width: 100%; }
.export-preview { margin-top: var(--space-lg); padding: var(--space-md); border: 1px solid var(--el-border-color-lighter); border-radius: var(--radius-md); background: var(--surface-subtle); }
.export-preview > span { display: block; margin-bottom: 8px; color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.export-preview pre { max-height: 260px; margin: 0; overflow: auto; color: var(--el-text-color-regular); font: inherit; font-size: var(--type-caption); line-height: var(--leading-ui); white-space: pre-wrap; overflow-wrap: anywhere; }
.field-counter { width: 100%; margin: 6px 0 0; color: var(--el-text-color-placeholder); font-size: var(--type-meta); }
.dialog-card-title { margin: 0 0 var(--space-sm); font-weight: 600; }
.dialog-hint { margin: 8px 0 0; color: var(--el-text-color-placeholder); font-size: var(--type-meta); }
.plant-settings-heading { margin-bottom: 18px; }
.plant-settings-heading h3 { margin: 0; font-size: var(--type-card-title); }
.plant-settings-heading p { margin: 6px 0 0; color: var(--el-text-color-secondary); font-size: var(--type-caption); line-height: var(--leading-ui); }
.plant-selected-card { margin-bottom: var(--space-lg); }
.experiment-grow-context { margin: 0; padding: 4px 0 0 20px; border-left: 1px solid var(--el-border-color-lighter); background: transparent; }
.experiment-grow-context :deep(.el-form-item:last-child) { margin-bottom: 0; }
.experiment-grow-context :deep(.el-radio-group) { display: flex; flex-wrap: wrap; }
.source-picker { display: grid; gap: var(--space-xs); }
.source-card-title { display: -webkit-box; overflow: hidden; color: var(--el-text-color-regular); font-size: var(--type-caption); line-height: var(--leading-ui); overflow-wrap: anywhere; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.theme-picker { display: flex; flex-wrap: wrap; gap: 8px; }
.theme-option { display: inline-flex; align-items: center; gap: 7px; padding: 7px 10px; border: 1px solid var(--el-border-color); border-radius: 7px; background: var(--el-bg-color); color: var(--el-text-color-regular); font: inherit; cursor: pointer; }
.theme-option > span { width: 14px; height: 14px; border-radius: 50%; background: var(--option-color); }
.theme-option.active { border-color: color-mix(in srgb, var(--option-color) 40%, var(--el-bg-color)); background: color-mix(in srgb, var(--option-color) 26%, var(--el-bg-color)); color: var(--el-text-color-primary); }
@media (max-width: 900px) {
  .exploration-overview { grid-template-columns: minmax(0, 1fr); }
  .exploration-snapshot { padding-right: 0; }
  .exploration-snapshot + .exploration-snapshot { margin-top: var(--space-md); padding-top: var(--space-md); padding-left: 0; border-top: 1px solid var(--el-border-color-lighter); border-left: 0; }
  .export-workspace { grid-template-columns: minmax(0, 1fr); }
  .export-settings-pane { padding-top: var(--space-lg); padding-left: 0; border-top: 1px solid var(--el-border-color-lighter); border-left: 0; }
}

@media (max-width: 760px) {
  .detail-navigation { align-items: stretch; flex-direction: column; }
  .detail-actions { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .detail-actions > :first-child { grid-column: 1 / -1; }
  .detail-edit-actions { display: contents; }
  .status-trigger,
  .detail-actions :deep(.el-button) { width: 100%; min-width: 0; margin-left: 0; }
  .soil-workspace-heading { align-items: flex-start; flex-direction: column; }
  .material-primary-actions { width: 100%; }
  .material-primary-actions :deep(.el-button) { flex: 1; min-width: 0; margin-left: 0; }
  .soil-toolbar { align-items: stretch; flex-direction: column; }
  .soil-toolbar > :deep(.el-button) { width: 100%; margin-left: 0; }
  .comparison-grid,
  .experiment-card-list { grid-template-columns: minmax(0, 1fr); }
  .export-record-fields { grid-template-columns: minmax(0, 1fr); }
  .comparison-placeholder { min-height: 90px; }
  .experiment-grow-context { padding: var(--space-lg) 0 0; border-top: 1px solid var(--el-border-color-lighter); border-left: 0; }
}

@media (max-width: 480px) {
  .exploration-heading { flex-direction: column; }
  .exploration-actions { width: 100%; justify-content: space-between; }
  .exploration-actions :deep(.el-button) { min-width: 0; margin-left: 0; padding-inline: var(--space-xs); }
  .exploration-footnote { align-items: flex-start; flex-direction: column; }
}
</style>
