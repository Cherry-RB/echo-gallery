<script setup lang="ts">
import RecordPickerOption from '../../components/ui/RecordPickerOption.vue'
import WorkResume from '../../components/work/WorkResume.vue'
import { computed, reactive, ref, watch } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, ArrowLeft, Delete, Edit } from '@element-plus/icons-vue'
import ExperimentExplorationEditor from '../../components/experiment/ExperimentExplorationEditor.vue'
import ExperimentExplorationHistoryDialog from '../../components/experiment/ExperimentExplorationHistoryDialog.vue'
import ExperimentThreads from '../../components/experiment/ExperimentThreads.vue'
import QuickCreateCardDialog from '../../components/QuickCreateCardDialog.vue'
import CardPickerDialog from '../../components/CardPickerDialog.vue'
import AppDialog from '../../components/AppDialog.vue'
import RecordHistoryTrigger from '../../components/ui/RecordHistoryTrigger.vue'
import DetailShell from '../../components/ui/DetailShell.vue'
import DetailHeading from '../../components/ui/DetailHeading.vue'
import ExpandableText from '../../components/ExpandableText.vue'
import type { CardContentRequest, CardDto } from '../../types/card'
import type { ExperimentExplorationDto, ExperimentExplorationRecordDto, ExperimentRequest } from '../../types/experiment'
import { getTextLength, trimToTextLength } from '../../utils/textLength'
import { formatDate } from '../../utils/formatDate'
import { experimentApi } from '../../utils/api/experimentApi'
import { experimentThemeOptions, getExperimentThemeStyle } from '../../utils/experimentTheme'

defineProps<{ id?: string }>()

const route = useRoute()
const router = useRouter()
const queryClient = useQueryClient()
const experimentId = computed(() => Number(route.params.id))
const emptyExploration: ExperimentExplorationDto = { currentTry: '', currentTrySourceMethodId: null, favoriteTries: [], records: [] }
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
const returnToHistory = ref(false)
const threadsWorkspace = ref<InstanceType<typeof ExperimentThreads> | null>(null)
const exportVisible = ref(false)
const exportCreateVisible = ref(false)
const exportDestination = ref<'NEW' | 'EXISTING'>('NEW')
const exportTopic = ref('')
const exportTitle = ref('')
const selectedExportRecordIds = ref<number[]>([])
const selectedExportCardId = ref<number | null>(null)
const selectedExportCard = ref<CardDto | null>(null)
const exportCardPickerVisible = ref(false)
const exportCardInitialData = ref<Partial<CardDto>>({})
const pendingExportRecordIds = ref<number[]>([])
const isExporting = ref(false)

watch(experimentId, () => {
  explorationEditorVisible.value = false
  historyDialogVisible.value = false
  exportVisible.value = false
})

watch([experimentId, () => route.query.history], () => {
  historyDialogVisible.value = route.query.history === '1'
}, { immediate: true })

const setExploration = (value: ExperimentExplorationDto) => {
  queryClient.setQueryData(['experiment-exploration', experimentId.value], value)
  void queryClient.invalidateQueries({ queryKey: ['experiment', experimentId.value] })
  void queryClient.invalidateQueries({ queryKey: ['experiments'] })
  void queryClient.invalidateQueries({ queryKey: ['overview'] })
}

const openExplorationEditor = (mode: 'TRY' | 'DISCOVERY', record: ExperimentExplorationRecordDto | null = null) => {
  explorationEditorMode.value = mode
  editingExplorationRecord.value = record
  explorationEditorVisible.value = true
}

const clearExploration = async () => {
  try {
    await ElMessageBox.confirm('將永久刪除目前試法、常用試法與原始探索紀錄。已整理至探索線、卡片或作品的內容會保留。', '清除探索紀錄', {
      confirmButtonText: '清除', cancelButtonText: '取消', type: 'warning',
    })
  } catch {
    return
  }

  try {
    await experimentApi.clearExploration(experimentId.value)
    setExploration({ ...emptyExploration })
    ElMessage.success('原始探索紀錄已清除；整理後的內容仍保留。')
  } catch {
    ElMessage.error('清除探索紀錄失敗，請稍後再試')
  }
}

const deleteExplorationRecord = async (recordId: number) => {
  try {
    await ElMessageBox.confirm('刪除這筆探索紀錄？刪除後無法復原；已整理成卡片的內容仍會保留。', '刪除探索紀錄', {
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

const openThreadImport = (recordId?: number) => {
  returnToHistory.value = historyDialogVisible.value
  historyDialogVisible.value = false
  threadsWorkspace.value?.openImport(recordId == null ? undefined : [recordId])
}
const finishThreadImport = () => {
  if (returnToHistory.value) historyDialogVisible.value = true
  returnToHistory.value = false
}

const openExport = () => {
  const experiment = experimentQuery.data.value
  selectedExportRecordIds.value = exportRecords.value.map(record => record.id)
  exportDestination.value = 'NEW'
  exportTopic.value = experiment?.title ?? ''
  exportTitle.value = ''
  selectedExportCardId.value = null
  selectedExportCard.value = null
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
  ElMessage.success('探索紀錄已整理成新卡；原紀錄仍保留。')
}

const appendToExistingCard = async () => {
  if (!canExport.value || !selectedExportCard.value) return
  isExporting.value = true
  try {
    await experimentApi.appendExplorationToCard(
      experimentId.value,
      Number(selectedExportCard.value.id),
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

const editVisible = ref(false)
const editForm = reactive<ExperimentRequest>({ title: '', hypothesis: '', conclusion: '', description: '', themeColor: 'LEAF' })
const experimentQuery = useQuery({
  queryKey: computed(() => ['experiment', experimentId.value]),
  queryFn: () => experimentApi.getExperiment(experimentId.value)
})

const exportRecords = computed(() => exploration.value.records)
const selectedExportRecords = computed(() => exportRecords.value
  .filter(record => selectedExportRecordIds.value.includes(record.id))
  .sort((left, right) => new Date(left.createdAt).getTime() - new Date(right.createdAt).getTime()))
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
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['treasures'] }),
      queryClient.invalidateQueries({ queryKey: ['overview'] })
    ])
    ElMessage.success('實驗主題已永久刪除；原始卡片仍保留')
    router.replace('/experiments')
  },
  onError: () => ElMessage.error('刪除實驗主題失敗，請稍後再試')
})

const goBack = () => window.history.state?.back ? router.back() : router.push('/experiments')

const confirmDeleteExperiment = async () => {
  const experiment = experimentQuery.data.value
  if (!experiment || deleteMutation.isPending.value) return
  try {
    await ElMessageBox.confirm(
      `「${experiment.title}」的探索線、試法、發現、原始探索紀錄與成果關聯都會永久刪除；卡片與作品本身會保留。`,
      '永久刪除實驗主題',
      { confirmButtonText: '永久刪除', cancelButtonText: '取消', type: 'warning' }
    )
    deleteMutation.mutate()
  } catch {
    // 使用者取消刪除，不需處理。
  }
}

const openEdit = () => {
  const experiment = experimentQuery.data.value
  if (!experiment) return
  editForm.title = experiment.title
  editForm.hypothesis = experiment.hypothesis ?? ''
  editForm.conclusion = experiment.conclusion ?? ''
  editForm.description = experiment.description ?? ''
  editForm.themeColor = experiment.themeColor
  editVisible.value = true
}

const submitEdit = () => {
  editForm.title = trimToTextLength(editForm.title, 255).trim()
  editForm.hypothesis = trimToTextLength(editForm.hypothesis ?? '', 2000).trim()
  editForm.conclusion = trimToTextLength(editForm.conclusion ?? '', 10000).trim()
  editForm.description = trimToTextLength(editForm.description ?? '', 5000)
  if (editForm.title) editMutation.mutate()
}

</script>

<template>
  <DetailShell class="experiment-detail-page">
    <template #navigation>
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
    </template>

    <div v-if="experimentQuery.isLoading.value" class="state-surface"><el-skeleton :rows="8" animated /></div>
    <div v-else-if="experimentQuery.isError.value" class="state-surface">
      <el-result icon="error" title="無法載入實驗主題" sub-title="實驗主題可能不存在，或目前無法連線。">
        <template #extra><el-button @click="goBack">返回列表</el-button><el-button type="primary" @click="experimentQuery.refetch()">重新載入</el-button></template>
      </el-result>
    </div>

    <main v-else-if="experimentQuery.data.value" class="experiment-detail-content" :style="getExperimentThemeStyle(experimentQuery.data.value.themeColor)">
      <DetailHeading eyebrow="實驗主題" :title="experimentQuery.data.value.title" />

      <section class="experiment-inquiry" aria-labelledby="experiment-inquiry-title">
        <header class="section-heading">
          <h2 id="experiment-inquiry-title">實驗目的</h2>
        </header>
        <ExpandableText
          v-if="experimentQuery.data.value.hypothesis"
          class="experiment-hypothesis"
          :content="experimentQuery.data.value.hypothesis"
          :lines="5"
        />
        <p v-else class="quiet-empty">還沒有寫下問題；也可以先從探索或材料開始。</p>
        <div v-if="experimentQuery.data.value.conclusion" class="experiment-conclusion">
          <span>實驗結論</span>
          <ExpandableText :content="experimentQuery.data.value.conclusion" :lines="8" />
        </div>
      </section>

      <section class="exploration-workspace" aria-label="這次想怎麼探索">
        <header class="exploration-heading">
          <h2>這次想怎麼探索？</h2>
          <div v-if="!explorationQuery.isLoading.value && !explorationQuery.isError.value" class="exploration-actions">
            <time v-if="latestObservation?.createdAt" :datetime="latestObservation.createdAt">{{ formatDate(latestObservation.createdAt) }}</time>
            <RecordHistoryTrigger v-if="exploration.records.length" label="探索紀錄" :count="exploration.records.length" @click="historyDialogVisible = true" />
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
            <ExpandableText
              v-if="exploration.currentTry"
              class="exploration-snapshot-copy"
              :content="exploration.currentTry"
              :lines="6"
            />
            <p v-else class="empty">新的試法尚未寫下。不妨先翻翻收集的材料。</p>
          </div>
          <div :class="['exploration-snapshot', { 'is-next-action': Boolean(exploration.currentTry) && !latestObservation }]">
            <div class="exploration-snapshot-heading">
              <span>{{ latestObservation ? '最近一次觀察' : '2．記下發現' }}</span>
              <el-button :text="!exploration.currentTry || Boolean(latestObservation)" type="primary" size="small" @click="openExplorationEditor('DISCOVERY')">{{ exploration.currentTry || latestObservation ? '記下發現' : '直接記下發現' }}</el-button>
            </div>
            <ExpandableText
              v-if="latestObservation?.text"
              class="exploration-snapshot-copy"
              :content="latestObservation.text"
              :lines="6"
            />
            <p v-else class="empty">{{ exploration.currentTry ? '試過、沒試成或改變想法，都可以記在這裡。' : '設定試法後，再留下觀察；也可以直接記錄現在的發現。' }}</p>
          </div>
        </div>
        <div class="exploration-footnote">
          <span>探索紀錄會同步保存；整理成卡片後，原紀錄仍會保留。</span>
          <div class="exploration-utility-actions">
            <el-button v-if="exploration.currentTry || exploration.records.length || exploration.favoriteTries.length" text type="danger" size="small" @click="clearExploration">清除探索紀錄</el-button>
          </div>
        </div>
        </template>
      </section>

      <WorkResume :experiment-id="experimentId" />
      <ExperimentThreads ref="threadsWorkspace" :experiment-id="experimentId" :exploration-records="exploration.records" @import-closed="finishThreadImport" />

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
  </DetailShell>

  <ExperimentExplorationHistoryDialog
    :experiment-id="experimentId"
    v-model="historyDialogVisible"
    :records="exploration.records"
    @record="openExplorationEditor($event)"
    @edit="openExplorationEditor('DISCOVERY', $event)"
    @delete="deleteExplorationRecord"
    @organize="organizeFromHistory"
    @add-thread="openThreadImport"
    @open-card="router.push(`/card/${$event}`)"
  />

  <ExperimentExplorationEditor
    v-model="explorationEditorVisible"
    :experiment-id="experimentId"
    :mode="explorationEditorMode"
    :record="editingExplorationRecord"
    @closed="editingExplorationRecord = null"
  />

  <AppDialog :busy="isExporting" :draft="[exportDestination, exportTopic, exportTitle, selectedExportRecordIds, selectedExportCardId]" v-model="exportVisible" title="整理探索紀錄" width="min(980px, calc(100vw - 32px))" scroll-body>
    <p class="dialog-intro">把選取的探索紀錄整理成卡片內容。成功後會保留原紀錄，並標示它已整理到哪張卡片。</p>
    <div class="export-workspace">
      <section class="export-selection-pane">
        <h3>選擇探索紀錄</h3>
        <div class="export-record-list">
          <RecordPickerOption v-for="record in exportRecords" :key="record.id" :title="formatDate(record.createdAt, 'YYYY/MM/DD HH:mm')" :selected="selectedExportRecordIds.includes(record.id)" :try-text="record.tryText || '?????????'" :discovery="record.discovery" :disabled="isExporting" @select="toggleExportRecord(record.id)" />
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
            <el-button class="export-card-select" @click="exportCardPickerVisible = true">{{ selectedExportCard?.title || '選擇卡片' }}</el-button>
            <p v-if="selectedExportCard && !exportableRecordIds.length" class="dialog-hint">所選紀錄都已整理到這張卡片；可改選其他紀錄或其他卡片。</p>
          </template>
        </section>
        <section class="export-dialog-section">
          <label for="export-topic">主題</label>
          <el-input id="export-topic" v-model="exportTopic" maxlength="255" placeholder="例如：這次探索的線索" />
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
    <template #footer="{ requestClose }">
      <el-button :disabled="isExporting" @click="requestClose">取消</el-button>
      <el-button v-if="exportDestination === 'NEW'" type="primary" :disabled="!canExport" @click="openCreateExportCard">繼續建立卡片</el-button>
      <el-button v-else type="primary" :loading="isExporting" :disabled="!canExport" @click="appendToExistingCard">加入卡片內容</el-button>
    </template>
  </AppDialog>

  <CardPickerDialog
    v-model="exportCardPickerVisible"
    title="選擇整理目的卡片"
    description="選擇要加入整理內容的既有卡片。原始探索紀錄仍會保留。"
    confirm-label="選擇卡片"
    settings-style="plain"
    @confirm="selectedExportCard = $event; selectedExportCardId = Number($event.id); exportCardPickerVisible = false"
  />

  <QuickCreateCardDialog
    v-model="exportCreateVisible"
    title="建立探索紀錄卡片"
    description="已帶入整理內容；建立後仍可從原探索紀錄查看這張卡片。"
    create-label="建立卡片"
    success-title="探索紀錄卡片已建立"
    success-description="卡片已建立；原探索紀錄仍保留。"
    :initial-data="exportCardInitialData"
    :create-card="createExportCard"
    create-error-message="建立探索紀錄卡片失敗，請稍後再試"
    @created="handleExportCardCreated"
  />

  <AppDialog :busy="editMutation.isPending.value" :draft="editForm" v-model="editVisible" title="編輯實驗主題" width="min(680px, calc(100vw - 32px))" scroll-body>
    <el-form label-position="top" @submit.prevent="submitEdit">
      <el-form-item label="實驗主題名稱" required>
        <el-input v-model="editForm.title" maxlength="255" />
        <p class="field-counter">總字數：{{ getTextLength(editForm.title) }} / 255</p>
      </el-form-item>
      <el-form-item label="實驗目的（選填）">
        <el-input v-model="editForm.hypothesis" type="textarea" :rows="3" maxlength="2000" />
        <p class="field-counter">總字數：{{ getTextLength(editForm.hypothesis ?? '') }} / 2000</p>
      </el-form-item>
      <el-form-item label="實驗結論（選填）">
        <el-input v-model="editForm.conclusion" type="textarea" :rows="4" maxlength="10000" placeholder="當實驗形成階段性理解時，再寫下目前的結論。" />
        <p class="field-counter">總字數：{{ getTextLength(editForm.conclusion ?? '') }} / 10,000</p>
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
    <template #footer="{ requestClose }"><el-button :disabled="editMutation.isPending.value" @click="requestClose">取消</el-button><el-button type="primary" :loading="editMutation.isPending.value" :disabled="!editForm.title.trim()" @click="submitEdit">儲存變更</el-button></template>
  </AppDialog>

</template>

<style scoped>
.detail-edit-actions { display: flex; gap: var(--space-xs); }
.status-trigger { display: inline-flex; align-items: center; justify-content: center; gap: var(--space-xs); min-width: 120px; padding: 7px 10px; border: 1px solid var(--el-border-color); border-radius: var(--radius-sm); background: var(--el-fill-color-blank); color: var(--el-text-color-regular); font: inherit; white-space: nowrap; cursor: pointer; }
.status-trigger:hover, .status-trigger:focus-visible { border-color: var(--el-color-primary-light-5); color: var(--el-color-primary); }
.status-trigger:disabled { cursor: wait; opacity: 0.65; }
.status-trigger-arrow { margin-left: 2px; color: var(--el-text-color-placeholder); }
.property-status-dot { width: 8px; height: 8px; flex: 0 0 auto; border-radius: 50%; background: var(--el-text-color-placeholder); }
.property-status-active { background: var(--el-color-primary); }
.property-status-archived { background: var(--el-text-color-placeholder); }
.state-surface { padding: var(--panel-padding); }
.experiment-detail-content {
  display: grid;
  gap: var(--space-lg);
}
.experiment-inquiry,
.exploration-workspace,
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
.experiment-conclusion { max-width: var(--reading-max-width); padding-top: var(--space-md); margin-top: var(--space-md); border-top: 1px solid var(--el-border-color-lighter); }
.experiment-conclusion > span { display: block; margin-bottom: var(--space-xs); color: var(--el-text-color-primary); font-size: var(--type-section-title); font-weight: var(--weight-semibold); line-height: var(--leading-section-title); }
.experiment-conclusion :deep(.expandable-content) { margin: 0; color: var(--el-text-color-primary); font-size: var(--type-body); line-height: var(--leading-body); }
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
.exploration-snapshot :deep(.expandable-content) { margin: var(--space-2xs) 0 0; color: var(--el-text-color-primary); font-size: var(--type-ui); line-height: var(--leading-ui); }
.exploration-actions { display: flex; flex: 0 1 auto; flex-wrap: wrap; justify-content: flex-end; gap: var(--space-xs); }
.exploration-actions :deep(.el-button) { margin-left: 0; }
.exploration-actions time { align-self: center; color: var(--el-text-color-placeholder); font-size: var(--type-meta); line-height: var(--leading-ui); white-space: nowrap; }
.exploration-footnote { display: flex; align-items: center; justify-content: space-between; gap: var(--space-sm); margin-top: var(--space-md); color: var(--el-text-color-placeholder); font-size: var(--type-meta); line-height: var(--leading-ui); }
.exploration-utility-actions { display: flex; flex: 0 0 auto; align-items: center; gap: var(--space-sm); }
.exploration-utility-actions :deep(.el-button) { margin-left: 0; }
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
  .detail-actions > :first-child { grid-column: 1 / -1; }
  .detail-edit-actions { display: contents; }
  .status-trigger,
  .detail-actions :deep(.el-button) { width: 100%; min-width: 0; margin-left: 0; }
  .export-record-fields { grid-template-columns: minmax(0, 1fr); }
}

@media (max-width: 480px) {
  .exploration-heading { flex-direction: column; }
  .exploration-actions { width: 100%; justify-content: space-between; }
  .exploration-actions :deep(.el-button) { min-width: 0; margin-left: 0; padding-inline: var(--space-xs); }
  .exploration-footnote { align-items: flex-start; flex-direction: column; }
}
</style>
