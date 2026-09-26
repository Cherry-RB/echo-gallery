<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, ArrowLeft, Delete, Edit, MagicStick, Plus } from '@element-plus/icons-vue'
import ExperimentCardItem from '../../components/experiment/ExperimentCardItem.vue'
import QuickCreateCardDialog from '../../components/QuickCreateCardDialog.vue'
import CardPickerDialog from '../../components/CardPickerDialog.vue'
import AppDialog from '../../components/AppDialog.vue'
import ExpandableText from '../../components/ExpandableText.vue'
import type { CardContentRequest, CardDto } from '../../types/card'
import type { ExperimentCardDto, ExperimentExplorationDto, ExperimentRequest, ExperimentStage } from '../../types/experiment'
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
const recentTries = computed(() => [...new Set(
  exploration.value.records.map(record => record.tryText).filter((text): text is string => Boolean(text))
)].slice(0, 3))
const tryDialogVisible = ref(false)
const tryDialogMode = ref<'COMPOSE' | 'FAVORITES'>('COMPOSE')
const tryDraft = ref('')
const selectedTrySuggestion = ref('')
const newFavoriteTryDraft = ref('')
const editingFavoriteTry = ref<string | null>(null)
const editingFavoriteTryDraft = ref('')
const observationDialogVisible = ref(false)
const observationDraft = ref('')
const includeTryInDiscovery = ref(true)
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
  tryDialogVisible.value = false
  observationDialogVisible.value = false
})

const setExploration = (value: ExperimentExplorationDto) => {
  queryClient.setQueryData(['experiment-exploration', experimentId.value], value)
  void queryClient.invalidateQueries({ queryKey: ['experiments'] })
}

const openTryDialog = () => {
  tryDraft.value = exploration.value.currentTry
  selectedTrySuggestion.value = ''
  tryDialogMode.value = 'COMPOSE'
  newFavoriteTryDraft.value = ''
  editingFavoriteTry.value = null
  tryDialogVisible.value = true
}

const selectTrySuggestion = (text: string) => {
  tryDraft.value = text
}

const saveTry = async () => {
  const currentTry = trimToTextLength(tryDraft.value, 500).trim()
  if (!currentTry) return
  if (currentTry === exploration.value.currentTry) {
    tryDialogVisible.value = false
    return
  }
  try {
    setExploration(await experimentApi.updateCurrentTry(experimentId.value, currentTry))
    tryDialogVisible.value = false
  } catch {
    ElMessage.error('儲存試法失敗，請稍後再試')
  }
}

const openFavoriteManagement = (initialText = '') => {
  newFavoriteTryDraft.value = initialText
  editingFavoriteTry.value = null
  tryDialogMode.value = 'FAVORITES'
}

const saveFavoriteTries = async (favoriteTries: string[], successMessage: string) => {
  try {
    setExploration(await experimentApi.updateFavoriteTries(experimentId.value, favoriteTries))
    ElMessage.success(successMessage)
    return true
  } catch {
    ElMessage.error('更新常用試法失敗，請稍後再試')
    return false
  }
}

const addFavoriteTry = async () => {
  const text = trimToTextLength(newFavoriteTryDraft.value, 500).trim()
  if (!text) return
  if (exploration.value.favoriteTries.includes(text)) {
    ElMessage.info('這個試法已存入常用試法。')
    return
  }
  if (exploration.value.favoriteTries.length >= 3) {
    ElMessage.info('最多保存 3 種常用試法；請先移除一種。')
    return
  }
  if (await saveFavoriteTries([...exploration.value.favoriteTries, text], '已新增常用試法。')) {
    newFavoriteTryDraft.value = ''
  }
}

const startEditFavoriteTry = (text: string) => {
  editingFavoriteTry.value = text
  editingFavoriteTryDraft.value = text
}

const updateFavoriteTry = async () => {
  const original = editingFavoriteTry.value
  const text = trimToTextLength(editingFavoriteTryDraft.value, 500).trim()
  if (!original || !text) return
  if (text !== original && exploration.value.favoriteTries.includes(text)) {
    ElMessage.info('已有相同的常用試法。')
    return
  }
  if (await saveFavoriteTries(
    exploration.value.favoriteTries.map(saved => saved === original ? text : saved),
    '常用試法已更新。',
  )) {
    editingFavoriteTry.value = null
  }
}

const removeFavoriteTry = async (text: string) => {
  try {
    await ElMessageBox.confirm('從常用試法移除這段文字？已留下的探索紀錄不受影響。', '移除常用試法', {
      confirmButtonText: '移除', cancelButtonText: '取消', type: 'warning',
    })
    await saveFavoriteTries(
      exploration.value.favoriteTries.filter(saved => saved !== text),
      '已移除常用試法。',
    )
  } catch {
    // 使用者取消移除，不需處理。
  }
}

const openObservationDialog = () => {
  observationDraft.value = ''
  includeTryInDiscovery.value = true
  observationDialogVisible.value = true
}

const saveObservation = async () => {
  const text = trimToTextLength(observationDraft.value, 1000).trim()
  if (!text) return
  try {
    setExploration(await experimentApi.createExplorationRecord(
      experimentId.value,
      text,
      includeTryInDiscovery.value,
    ))
    observationDialogVisible.value = false
  } catch {
    ElMessage.error('儲存發現失敗，請稍後再試')
  }
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
  <section class="experiment-detail-page">
    <header class="detail-navigation">
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
          <el-button type="danger" plain :icon="Delete" :loading="deleteMutation.isPending.value" @click="confirmDeleteExperiment">永久刪除</el-button>
          <el-button type="primary" plain :icon="Edit" @click="openEdit">編輯實驗主題</el-button>
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
            <span class="experiment-focus-label">目前想弄懂</span>
            <ExpandableText
              class="experiment-hypothesis"
              :content="experimentQuery.data.value.hypothesis || '還沒有寫下問題；也可以先從下方材料開始。'"
              :lines="4"
            />
            <ExpandableText
              v-if="experimentQuery.data.value.description"
              class="experiment-description"
              :content="experimentQuery.data.value.description"
              :lines="5"
            />
          </div>
          <div class="experiment-primary-actions">
            <el-button type="primary" :icon="Plus" @click="openAdd">種入卡片</el-button>
            <el-button :icon="MagicStick" :disabled="experimentQuery.data.value.seedCount + experimentQuery.data.value.growingCount + experimentQuery.data.value.matureCount === 0" @click="openGrow()">長出新卡</el-button>
          </div>
        </div>
      </section>

      <section class="exploration-workspace" aria-label="這次想怎麼探索">
        <header class="exploration-heading">
          <div><span class="detail-eyebrow">接著從這裡開始</span><h2>這次想怎麼探索？</h2></div>
        </header>
        <el-skeleton v-if="explorationQuery.isLoading.value" :rows="3" animated />
        <el-result v-else-if="explorationQuery.isError.value" icon="error" title="無法載入探索資料" sub-title="請稍後再試。">
          <template #extra><el-button type="primary" @click="explorationQuery.refetch()">重新載入</el-button></template>
        </el-result>
        <template v-else>
        <div class="exploration-overview">
          <div class="exploration-snapshot">
            <span>目前想試</span>
            <p>{{ exploration.currentTry || '還沒有想試的事，可以先看看材料。' }}</p>
          </div>
          <div class="exploration-snapshot">
            <span>最近一次觀察</span>
            <p>{{ latestObservation?.text || '尚未留下觀察。試過、沒試成或改變想法，都可以記在這裡。' }}</p>
            <time v-if="latestObservation?.createdAt" :datetime="latestObservation.createdAt">{{ formatDate(latestObservation.createdAt) }}</time>
          </div>
        </div>
        <div class="exploration-actions">
          <el-button v-if="exploration.currentTry" type="primary" @click="openObservationDialog">記下發生了什麼</el-button>
          <el-button v-else type="primary" @click="openTryDialog">試一件小事</el-button>
          <el-button v-if="exploration.currentTry" @click="openTryDialog">換個試法</el-button>
          <el-button v-else @click="openObservationDialog">記下一點發現</el-button>
          <el-button @click="materialsRef?.scrollIntoView({ behavior: 'smooth', block: 'start' })">看看材料</el-button>
          <el-button v-if="exploration.records.length" @click="openExport">整理成卡片</el-button>
          <el-button text @click="router.push('/experiments')">這次先放著</el-button>
        </div>
        <details v-if="exploration.records.length" class="exploration-history">
          <summary>查看探索紀錄{{ exploration.records.length ? `（${exploration.records.length} 筆發現）` : '' }}</summary>
          <ol v-if="exploration.records.length">
            <li v-for="record in exploration.records" :key="record.id">
              <div class="exploration-history-meta">
                <time :datetime="record.createdAt">{{ formatDate(record.createdAt) }}</time>
                <el-button text type="danger" size="small" @click="deleteExplorationRecord(record.id)">刪除</el-button>
              </div>
              <div class="exploration-record-flow">
                <template v-if="record.tryText">
                  <div><span>試法</span><p>{{ record.tryText }}</p></div>
                  <span class="exploration-record-arrow" aria-hidden="true">↓</span>
                </template>
                <div><span>發現</span><p>{{ record.discovery }}</p></div>
              </div>
              <div v-if="record.exports.length" class="exploration-record-exports">
                <span>已整理至 {{ record.exports.length }} 張卡片</span>
                <div class="exploration-export-links">
                  <button v-for="recordExport in record.exports" :key="`${recordExport.cardId}-${recordExport.exportedAt}`" type="button" @click="router.push(`/card/${recordExport.cardId}`)">{{ recordExport.cardTitle }}</button>
                </div>
              </div>
            </li>
          </ol>
        </details>
        <div class="exploration-footnote">
          <span>探索紀錄會同步保存；整理成卡片後，原紀錄仍會保留。</span>
          <el-button v-if="exploration.currentTry || exploration.records.length || exploration.favoriteTries.length" text type="danger" size="small" @click="clearExploration">清除探索資料</el-button>
        </div>
        </template>
      </section>

      <section ref="materialsRef" class="soil-workspace" aria-label="實驗主題中的卡片土壤">
        <header class="soil-workspace-heading">
          <div><h2>材料與線索</h2><p>土壤表示卡片在這裡的角色，不必依序前進。選一兩張卡可以並排閱讀。</p></div>
          <el-button :disabled="comparingCards.length === 0" @click="comparisonVisible = true">並排看材料{{ comparingCards.length ? `（${comparingCards.length}/2）` : '' }}</el-button>
        </header>
        <div class="soil-filters" role="group" aria-label="依卡片角色篩選">
          <button type="button" :class="{ active: selectedStage === 'ALL' }" :aria-pressed="selectedStage === 'ALL'" @click="selectedStage = 'ALL'">全部</button>
          <button v-for="stage in stages" :key="stage.value" type="button" :class="{ active: selectedStage === stage.value }" :aria-pressed="selectedStage === stage.value" @click="selectedStage = stage.value">
            {{ getStageControlLabel(stage) }} {{ stageCount(stage.value) }}
          </button>
        </div>
        <el-empty v-if="selectedStage === 'ALL' && experimentQuery.data.value.seedCount + experimentQuery.data.value.growingCount + experimentQuery.data.value.matureCount === 0" description="還沒有材料。先放入一張讓你想繼續看的卡片即可。" :image-size="72" />
        <div class="soil-grid">
          <template v-for="stage in stages" :key="stage.value">
          <section v-if="selectedStage === stage.value || (selectedStage === 'ALL' && stageCount(stage.value) > 0)" class="soil-column">
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
      </section>
    </main>
  </section>

  <AppDialog v-model="tryDialogVisible" :title="tryDialogMode === 'COMPOSE' ? '試一件小事' : '常用試法'" width="min(560px, calc(100vw - 32px))">
    <template v-if="tryDialogMode === 'COMPOSE'">
      <p class="dialog-intro">先寫下一件容易開始的小事。它會留在「目前想試」，等你記下發現時再決定要不要一起收進一筆探索紀錄。</p>
      <label class="try-suggestion-label" for="try-suggestion-select">從既有試法開始（可選）</label>
      <el-select id="try-suggestion-select" v-model="selectedTrySuggestion" class="try-suggestion-select" placeholder="最近用過或常用試法" :disabled="!recentTries.length && !exploration.favoriteTries.length" @change="selectTrySuggestion">
        <el-option-group v-if="recentTries.length" label="最近用過（最多 3 種）">
          <el-option v-for="text in recentTries" :key="`recent-${text}`" :label="text" :value="text" />
        </el-option-group>
        <el-option-group v-if="exploration.favoriteTries.length" label="我的常用試法（最多 3 種）">
          <el-option v-for="text in exploration.favoriteTries" :key="`saved-${text}`" :label="text" :value="text" />
        </el-option-group>
      </el-select>
      <label class="try-input-label" for="try-draft">這次想試什麼？</label>
      <el-input id="try-draft" v-model="tryDraft" type="textarea" :rows="4" maxlength="500" placeholder="例如：晚餐後拿起紙筆，隨意畫兩分鐘。" aria-label="這次想試的小事" @input="selectedTrySuggestion = ''" />
      <p class="field-counter">{{ getTextLength(tryDraft) }} / 500</p>
      <div class="try-secondary-actions">
        <el-button text size="small" @click="openFavoriteManagement(tryDraft)">管理常用試法</el-button>
      </div>
    </template>
    <template v-else>
      <p class="dialog-intro">將想反覆使用的起點放在這裡。最多保存 3 種；修改或移除不會影響已留下的探索紀錄。</p>
      <section class="favorite-try-create">
        <label for="new-favorite-try">新增常用試法</label>
        <el-input id="new-favorite-try" v-model="newFavoriteTryDraft" type="textarea" :rows="2" maxlength="500" placeholder="輸入想保存的試法" />
        <div><span>{{ getTextLength(newFavoriteTryDraft) }} / 500</span><el-button size="small" type="primary" :disabled="!newFavoriteTryDraft.trim() || exploration.favoriteTries.length >= 3" @click="addFavoriteTry">新增</el-button></div>
      </section>
      <p v-if="exploration.favoriteTries.length >= 3" class="dialog-hint">已保存 3 種常用試法；可先修改或移除其中一種。</p>
      <ul v-if="exploration.favoriteTries.length" class="favorite-try-list">
        <li v-for="text in exploration.favoriteTries" :key="text">
          <template v-if="editingFavoriteTry === text">
            <el-input v-model="editingFavoriteTryDraft" type="textarea" :rows="2" maxlength="500" aria-label="編輯常用試法" />
            <div class="favorite-try-row-actions"><el-button size="small" @click="editingFavoriteTry = null">取消</el-button><el-button size="small" type="primary" :disabled="!editingFavoriteTryDraft.trim()" @click="updateFavoriteTry">儲存</el-button></div>
          </template>
          <template v-else>
            <p>{{ text }}</p>
            <div class="favorite-try-row-actions"><el-button text size="small" @click="startEditFavoriteTry(text)">編輯</el-button><el-button text type="danger" size="small" @click="removeFavoriteTry(text)">移除</el-button></div>
          </template>
        </li>
      </ul>
      <p v-else class="empty-copy">還沒有常用試法。</p>
    </template>
    <template #footer>
      <template v-if="tryDialogMode === 'COMPOSE'">
        <el-button @click="tryDialogVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!tryDraft.trim()" @click="saveTry">留下這個試法</el-button>
      </template>
      <el-button v-else @click="tryDialogMode = 'COMPOSE'">返回試法</el-button>
    </template>
  </AppDialog>

  <AppDialog v-model="observationDialogVisible" title="記下發生了什麼" width="min(560px, calc(100vw - 32px))">
    <p class="dialog-intro">試過、沒能開始、或想法變了，都可以寫一句。每次儲存只會產生一筆探索紀錄。</p>
    <el-input v-model="observationDraft" type="textarea" :rows="5" maxlength="1000" placeholder="例如：沒畫成；原來我把紙筆收得太遠了。" aria-label="這次發生了什麼" />
    <p class="field-counter">{{ getTextLength(observationDraft) }} / 1000</p>
    <div v-if="exploration.currentTry" class="discovery-try-choice">
      <p><span>目前試法</span>{{ exploration.currentTry }}</p>
      <el-checkbox v-model="includeTryInDiscovery">把它一起留在這筆探索紀錄</el-checkbox>
      <small v-if="includeTryInDiscovery">儲存後會清空目前試法，並形成「試法 → 發現」的一筆紀錄。</small>
      <small v-else>這次只記發現；目前試法會保留，之後仍可繼續使用。</small>
    </div>
    <template #footer><el-button @click="observationDialogVisible = false">取消</el-button><el-button type="primary" :disabled="!observationDraft.trim()" @click="saveObservation">留下這次發現</el-button></template>
  </AppDialog>

  <AppDialog v-model="exportVisible" title="整理探索紀錄" width="min(760px, calc(100vw - 32px))">
    <p class="dialog-intro">把選取的探索紀錄整理成 Card 內容。成功後會保留原紀錄，並標示它已整理到哪張卡片。</p>
    <section class="export-dialog-section">
      <h3>收錄哪些紀錄？</h3>
      <div class="export-record-list">
        <label v-for="record in exportRecords" :key="record.id" class="export-record-option">
          <input
            type="checkbox"
            :checked="selectedExportRecordIds.includes(record.id)"
            @change="toggleExportRecord(record.id)"
          >
          <span class="export-record-content">
            <time>{{ formatDate(record.createdAt, 'YYYY/MM/DD HH:mm') }}</time>
            <strong v-if="record.tryText">試：{{ record.tryText }}</strong>
            <strong>發現：{{ record.discovery }}</strong>
          </span>
        </label>
      </div>
    </section>
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

  <AppDialog v-model="comparisonVisible" title="並排看材料" class="experiment-comparison-dialog" width="min(900px, calc(100vw - 32px))">
    <section class="comparison-workspace" aria-label="並排比較卡片">
      <div class="comparison-grid">
        <article v-for="card in comparingCards" :key="card.cardId" class="comparison-card">
          <button type="button" class="comparison-card-title" @click="router.push(`/card/${card.cardId}`)">{{ card.cardTitle }}</button>
          <p v-if="card.cardSummary"><span>內容重點</span>{{ card.cardSummary }}</p>
          <p v-if="card.cardReason"><span>留下原因</span>{{ card.cardReason }}</p>
          <p v-if="card.note"><span>在此主題的備註</span>{{ card.note }}</p>
          <p v-if="!card.cardSummary && !card.cardReason && !card.note" class="comparison-empty">這張卡尚無摘要或備註；可點標題閱讀全文。</p>
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

  <AppDialog v-model="editVisible" title="編輯實驗主題">
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
      <div class="plant-selected-card" :class="{ empty: !selectedCard }">
        <span>已選擇</span>
        <p class="plant-selected-card-content">{{ selectedCard ? `#${selectedCard.id}　${selectedCard.title}` : '請先從左側選擇一張卡片。' }}</p>
      </div>
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
            <div v-for="card in sourceCards" :key="card.cardId" class="source-card-option">
              <el-checkbox :model-value="growContext.sourceCardIds.includes(card.cardId)" @change="toggleSource(card.cardId)" />
              <span class="source-card-title">{{ card.cardTitle }}</span>
            </div>
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
.experiment-detail-page { width: 100%; height: calc(100dvh - 40px); overflow: hidden; background: var(--el-bg-color-page); }
.detail-navigation { display: flex; align-items: center; justify-content: space-between; gap: 16px; min-height: 48px; padding: 8px 16px; box-sizing: border-box; border-bottom: 1px solid var(--el-border-color-light); background: var(--el-bg-color); }
.detail-actions { display: flex; align-items: center; gap: 20px; }
.detail-actions :deep(.el-button) { min-width: 120px; }
.detail-edit-actions { display: flex; gap: 8px; }
.status-trigger { display: inline-flex; align-items: center; justify-content: center; gap: 8px; width: 120px; padding: 7px 10px; border: 1px solid var(--el-border-color); border-radius: 7px; background: var(--el-fill-color-blank); color: var(--el-text-color-regular); font: inherit; white-space: nowrap; cursor: pointer; }
.status-trigger:hover, .status-trigger:focus-visible { border-color: var(--el-color-primary-light-5); color: var(--el-color-primary); }
.status-trigger:disabled { cursor: wait; opacity: 0.65; }
.status-trigger-arrow { margin-left: 2px; color: var(--el-text-color-placeholder); }
.property-status-dot { width: 8px; height: 8px; flex: 0 0 auto; border-radius: 50%; background: var(--el-text-color-placeholder); }
.property-status-active { background: var(--el-color-primary); }
.property-status-archived { background: var(--el-text-color-placeholder); }
.state-surface { margin: 16px; padding: 32px; border: 1px solid var(--el-border-color-light); border-radius: 10px; background: var(--el-bg-color); box-shadow: var(--el-box-shadow-lighter); }
.experiment-detail-content { height: calc(100% - 48px); overflow-y: auto; }
.experiment-overview-panel { padding: 28px 32px; border-bottom: 1px solid var(--el-border-color-lighter); background: var(--el-bg-color); }
.experiment-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 28px; max-width: 1240px; margin: 0 auto; }
.detail-eyebrow { display: block; margin-bottom: 6px; color: var(--el-text-color-placeholder); font-size: var(--type-meta); letter-spacing: 0.12em; }
.experiment-heading h1 { margin: 0; font-size: var(--type-detail-title); line-height: var(--leading-title); overflow-wrap: anywhere; }
.experiment-heading p { max-width: 760px; margin: 12px 0 0; font-size: var(--type-ui); line-height: var(--leading-ui); white-space: pre-line; }
.experiment-heading .experiment-hypothesis,
.experiment-heading .experiment-description { max-width: 760px; margin: 12px 0 0; line-height: var(--leading-ui); }
.experiment-heading .experiment-hypothesis { padding-left: 12px; border-left: 3px solid var(--experiment-accent); color: var(--el-text-color-primary); font-size: var(--type-card-title); font-weight: 600; }
.experiment-heading .experiment-description { color: var(--el-text-color-secondary); font-size: var(--type-ui); }
.experiment-primary-actions { display: flex; flex: 0 0 auto; gap: 8px; }
.exploration-workspace { max-width: 1272px; margin: 20px auto 0; padding: 20px 24px; box-sizing: border-box; border: 1px solid var(--el-border-color-light); border-radius: 10px; background: var(--el-bg-color); box-shadow: var(--el-box-shadow-lighter); }
.exploration-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.exploration-heading h2 { margin: 0; font-size: var(--type-section-title); line-height: var(--leading-section); }
.exploration-overview { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; margin-top: 18px; }
.exploration-snapshot { min-width: 0; min-height: 108px; padding: 14px 16px; box-sizing: border-box; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; background: var(--el-fill-color-extra-light); }
.exploration-snapshot > span { color: var(--el-text-color-secondary); font-size: var(--type-caption); }
.exploration-snapshot p { margin: 10px 0 0; color: var(--el-text-color-primary); font-size: var(--type-ui); line-height: var(--leading-ui); white-space: pre-line; overflow-wrap: anywhere; }
.exploration-snapshot time { display: block; margin-top: 8px; color: var(--el-text-color-placeholder); font-size: var(--type-meta); }
.exploration-actions { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 18px; }
.exploration-actions :deep(.el-button) { margin-left: 0; }
.exploration-history { margin-top: 18px; color: var(--el-text-color-regular); font-size: var(--type-caption); }
.exploration-history summary { width: fit-content; color: var(--el-color-primary); cursor: pointer; }
.exploration-history ol { display: grid; gap: 10px; max-height: 400px; overflow-y: auto; margin: 12px 0 0; padding: 0 4px 0 0; list-style: none; }
.exploration-history li { padding: 10px 12px; border: 1px solid var(--el-border-color-lighter); border-radius: 7px; }
.exploration-history-meta { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 8px; }
.exploration-history-meta :deep(.el-button) { margin-left: 0; }
.exploration-history-meta strong { color: var(--el-text-color-primary); font-weight: 600; }
.exploration-history-meta span { color: var(--el-text-color-placeholder); font-size: var(--type-meta); }
.exploration-history time { color: var(--el-text-color-placeholder); font-size: var(--type-meta); }
.exploration-history p { margin: 6px 0 0; white-space: pre-line; overflow-wrap: anywhere; }
.exploration-record-flow { display: grid; gap: 8px; margin-top: 8px; }
.exploration-record-flow > div { padding-left: 10px; border-left: 2px solid var(--el-border-color); }
.exploration-record-flow > div:last-child { border-left-color: var(--el-color-primary-light-5); }
.exploration-record-flow span { display: block; color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.exploration-record-flow p { margin: 3px 0 0; color: var(--el-text-color-primary); line-height: var(--leading-ui); }
.exploration-record-arrow { padding-left: 5px; color: var(--el-text-color-placeholder); line-height: 1; }
.exploration-record-exports { display: grid; gap: 6px; margin-top: 12px; color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.exploration-export-links { display: flex; min-width: 0; flex-wrap: wrap; gap: 6px 12px; }
.exploration-export-links button { max-width: 100%; padding: 0; overflow: hidden; border: 0; background: transparent; color: var(--el-color-primary); cursor: pointer; font: inherit; text-align: left; text-overflow: ellipsis; white-space: nowrap; }
.exploration-export-links button::before { content: '・'; color: var(--el-text-color-placeholder); }
.exploration-export-links button:hover, .exploration-export-links button:focus-visible { text-decoration: underline; }
.exploration-footnote { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-top: 18px; padding-top: 12px; border-top: 1px solid var(--el-border-color-lighter); color: var(--el-text-color-placeholder); font-size: var(--type-meta); line-height: var(--leading-ui); }
.exploration-footnote :deep(.el-button) { flex: 0 0 auto; margin-left: 0; }
.soil-workspace { max-width: 1320px; margin: 0 auto; padding: 24px; box-sizing: border-box; }
.comparison-workspace { padding: 4px 0; }
.comparison-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
.comparison-card, .comparison-placeholder { min-width: 0; padding: 16px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; background: var(--el-fill-color-extra-light); }
.comparison-card-title { padding: 0; border: 0; background: none; color: var(--el-text-color-primary); font: inherit; font-weight: 600; text-align: left; overflow-wrap: anywhere; cursor: pointer; }
.comparison-card-title:hover, .comparison-card-title:focus-visible { color: var(--el-color-primary); }
.comparison-card p { margin: 12px 0 0; color: var(--el-text-color-regular); font-size: var(--type-caption); line-height: var(--leading-ui); white-space: pre-line; overflow-wrap: anywhere; }
.comparison-card p span { display: block; color: var(--el-text-color-placeholder); font-size: var(--type-meta); }
.comparison-placeholder { display: grid; place-items: center; color: var(--el-text-color-secondary); font-size: var(--type-caption); text-align: center; }
.comparison-hint { margin: 16px 0 0; color: var(--el-text-color-secondary); font-size: var(--type-caption); line-height: var(--leading-ui); }
:global(.experiment-comparison-dialog .el-dialog__footer) { display: flex; justify-content: flex-end; flex-wrap: wrap; gap: 8px; }
:global(.experiment-comparison-dialog .el-dialog__footer .el-button) { margin-left: 0; }
.soil-filters { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 16px; }
.soil-filters button { padding: 7px 12px; border: 1px solid var(--el-border-color); border-radius: 7px; background: var(--el-bg-color); color: var(--el-text-color-regular); font: inherit; font-size: var(--type-ui); cursor: pointer; }
.soil-filters button.active { border-color: var(--el-color-primary-light-5); background: var(--el-color-primary-light-9); color: var(--el-color-primary); }
.soil-filters button:hover, .soil-filters button:focus-visible { border-color: var(--el-color-primary-light-5); }
.soil-workspace-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 16px; }
.soil-workspace-heading h2 { margin: 0; font-size: var(--type-section-title); line-height: var(--leading-section); }
.soil-workspace-heading p { margin: 6px 0 0; color: var(--el-text-color-secondary); font-size: var(--type-caption); }
.soil-grid { display: grid; gap: 16px; align-items: start; }
.soil-column { min-width: 0; padding: 18px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; background: var(--el-fill-color-extra-light); }
.experiment-card-list { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.experiment-card-list :deep(.experiment-card-item + .experiment-card-item) { margin-top: 0; }
.soil-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 14px; }
.soil-heading h3 { margin: 0; font-size: var(--type-card-title); }
.soil-heading p { margin: 6px 0 0; color: var(--el-text-color-secondary); font-size: var(--type-caption); line-height: var(--leading-ui); }
.soil-heading > span { color: var(--el-text-color-placeholder); font-size: var(--type-meta); font-variant-numeric: tabular-nums; }
.soil-pagination { justify-content: center; margin-top: 14px; }
.dialog-intro { margin: -4px 0 18px; color: var(--el-text-color-secondary); font-size: var(--type-caption); line-height: var(--leading-ui); }
.try-suggestion-label, .try-input-label { display: block; margin-bottom: 6px; color: var(--el-text-color-regular); font-size: var(--type-caption); }
.try-suggestion-select { width: 100%; margin-bottom: 12px; }
.try-secondary-actions { display: flex; justify-content: flex-end; margin-top: 8px; }
.try-secondary-actions :deep(.el-button) { margin-left: 0; }
.favorite-try-create { display: grid; gap: 8px; }
.favorite-try-create > label { color: var(--el-text-color-regular); font-size: var(--type-caption); }
.favorite-try-create > div { display: flex; align-items: center; justify-content: space-between; gap: 12px; color: var(--el-text-color-placeholder); font-size: var(--type-meta); }
.favorite-try-create > div :deep(.el-button) { margin-left: 0; }
.favorite-try-list { display: grid; gap: 10px; margin: 18px 0 0; padding: 0; list-style: none; }
.favorite-try-list li { display: grid; gap: 10px; padding: 12px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; background: var(--el-fill-color-extra-light); }
.favorite-try-list p { margin: 0; color: var(--el-text-color-primary); line-height: var(--leading-ui); white-space: pre-line; overflow-wrap: anywhere; }
.favorite-try-row-actions { display: flex; justify-content: flex-end; gap: 8px; }
.favorite-try-row-actions :deep(.el-button) { margin-left: 0; }
.discovery-try-choice { margin-top: 16px; padding: 12px 14px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; background: var(--el-fill-color-extra-light); }
.discovery-try-choice p { margin: 0 0 10px; line-height: var(--leading-ui); white-space: pre-line; overflow-wrap: anywhere; }
.discovery-try-choice p span { display: block; margin-bottom: 3px; color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.discovery-try-choice small { display: block; margin-top: 6px; color: var(--el-text-color-secondary); }
.export-dialog-section { display: grid; gap: 10px; margin-top: 22px; }
.export-dialog-section:first-of-type { margin-top: 0; }
.export-dialog-section h3, .export-dialog-section > label { margin: 0; color: var(--el-text-color-primary); font-size: var(--type-card-title); }
.export-record-list { display: grid; gap: 8px; max-height: 220px; overflow-y: auto; padding-right: 4px; }
.export-record-option { display: grid; grid-template-columns: 18px minmax(0, 1fr); align-items: start; gap: 8px; padding: 10px 12px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; background: var(--el-fill-color-extra-light); cursor: pointer; }
.export-record-option:has(input:checked) { border-color: var(--el-color-primary-light-5); background: var(--el-color-primary-light-9); }
.export-record-option input { width: 16px; height: 16px; margin: 2px 0 0; accent-color: var(--el-color-primary); }
.export-record-content { display: grid; min-width: 0; gap: 4px; }
.export-record-content time { color: var(--el-text-color-placeholder); font-size: var(--type-meta); }
.export-record-content strong { color: var(--el-text-color-regular); font-size: var(--type-caption); font-weight: 400; line-height: var(--leading-ui); overflow-wrap: anywhere; }
.export-card-select { width: 100%; }
.export-preview { margin-top: 22px; padding: 14px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; background: var(--el-fill-color-extra-light); }
.export-preview > span { display: block; margin-bottom: 8px; color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.export-preview pre { max-height: 260px; margin: 0; overflow: auto; color: var(--el-text-color-regular); font: inherit; font-size: var(--type-caption); line-height: var(--leading-ui); white-space: pre-wrap; overflow-wrap: anywhere; }
.field-counter { width: 100%; margin: 6px 0 0; color: var(--el-text-color-placeholder); font-size: var(--type-meta); }
.dialog-card-title { margin: -6px 0 14px; font-weight: 600; }
.dialog-hint { margin: 8px 0 0; color: var(--el-text-color-placeholder); font-size: var(--type-meta); }
.plant-settings-heading { margin-bottom: 18px; }
.plant-settings-heading h3 { margin: 0; font-size: var(--type-card-title); }
.plant-settings-heading p { margin: 6px 0 0; color: var(--el-text-color-secondary); font-size: var(--type-caption); line-height: var(--leading-ui); }
.plant-selected-card { display: grid; width: 100%; min-height: 84px; box-sizing: border-box; align-content: center; gap: 5px; margin: 0 0 20px; padding: 10px 12px; border: 1px solid var(--el-border-color); border-radius: 7px; background: var(--el-fill-color); }
.plant-selected-card > span { color: var(--el-text-color-placeholder); font-size: var(--type-meta); }
.plant-selected-card-content { margin: 0; color: var(--el-text-color-placeholder); font-size: var(--type-caption); line-height: var(--leading-ui); overflow-wrap: anywhere; }
.plant-selected-card:not(.empty) .plant-selected-card-content { color: var(--el-text-color-primary); }
.plant-selected-card.empty { background: var(--el-fill-color-extra-light); }
.experiment-grow-context { margin: 0; padding: 4px 0 0 20px; border-left: 1px solid var(--el-border-color-lighter); background: transparent; }
.experiment-grow-context :deep(.el-form-item:last-child) { margin-bottom: 0; }
.experiment-grow-context :deep(.el-radio-group) { display: flex; flex-wrap: wrap; }
.source-picker { display: grid; gap: 8px; padding: 12px; border: 1px solid var(--el-border-color-lighter); border-radius: 7px; background: var(--el-fill-color-light); }
.source-card-option { display: grid; grid-template-columns: 18px minmax(0, 1fr); align-items: start; gap: 8px; min-width: 0; }
.source-card-option :deep(.el-checkbox) { margin-top: 2px; }
.source-card-title { display: -webkit-box; overflow: hidden; color: var(--el-text-color-regular); font-size: var(--type-caption); line-height: var(--leading-ui); overflow-wrap: anywhere; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.theme-picker { display: flex; flex-wrap: wrap; gap: 8px; }
.theme-option { display: inline-flex; align-items: center; gap: 7px; padding: 7px 10px; border: 1px solid var(--el-border-color); border-radius: 7px; background: var(--el-bg-color); color: var(--el-text-color-regular); font: inherit; cursor: pointer; }
.theme-option > span { width: 14px; height: 14px; border-radius: 50%; background: var(--option-color); }
.theme-option.active { border-color: color-mix(in srgb, var(--option-color) 40%, var(--el-bg-color)); background: color-mix(in srgb, var(--option-color) 26%, var(--el-bg-color)); color: var(--el-text-color-primary); }
@media (max-width: 1200px) and (min-width: 901px) { .experiment-detail-page { height: calc(100dvh - 88px); } }
@media (max-width: 900px) { .experiment-detail-page { width: 100%; height: auto; margin: 0; min-height: calc(100dvh - 56px); overflow: visible; } .experiment-detail-content { height: auto; overflow: visible; } }
@media (max-width: 720px) { .comparison-grid, .experiment-card-list, .exploration-overview { grid-template-columns: 1fr; } .comparison-placeholder { min-height: 90px; } .soil-workspace-heading { align-items: flex-start; flex-direction: column; } .experiment-heading { flex-direction: column; } .experiment-primary-actions { width: 100%; } .experiment-primary-actions :deep(.el-button) { flex: 1; } .experiment-grow-context { padding: 20px 0 0; border-top: 1px solid var(--el-border-color-lighter); border-left: 0; } }
@media (max-width: 600px) { .detail-navigation { align-items: center; flex-wrap: wrap; padding: 14px 16px; } .detail-navigation > :first-child { flex-basis: 100%; justify-content: flex-start; } .detail-actions { display: grid; width: 100%; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; margin: 0; } .detail-actions > :first-child { grid-column: 1 / -1; width: 100%; } .detail-edit-actions { display: contents; } .status-trigger, .detail-actions :deep(.el-button) { width: 100%; min-width: 0; padding-inline: 6px; font-size: 12px; } .detail-actions :deep(.el-button) { margin-left: 0; } .experiment-overview-panel { padding: 24px 18px; } .exploration-workspace { margin: 12px 12px 0; padding: 18px 16px; } .exploration-heading { flex-wrap: wrap; } .exploration-actions { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); } .exploration-actions :deep(.el-button) { min-width: 0; padding-inline: 6px; } .exploration-footnote { align-items: flex-start; flex-direction: column; } .soil-workspace { padding: 18px 12px; } }
</style>
