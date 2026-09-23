<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
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
import type { ExperimentCardDto, ExperimentRequest, ExperimentStage } from '../../types/experiment'
import { getTextLength, trimToTextLength } from '../../utils/textLength'
import { experimentApi } from '../../utils/api/experimentApi'
import { experimentThemeOptions, getExperimentThemeStyle } from '../../utils/experimentTheme'

const route = useRoute()
const router = useRouter()
const queryClient = useQueryClient()
const experimentId = computed(() => Number(route.params.id))

const stages: Array<{ value: ExperimentStage; title: string; hint: string }> = [
  { value: 'SEED', title: '🌱 種子土壤', hint: '值得繼續接觸、等待彼此呼應的材料。' },
  { value: 'GROWING', title: '🌿 茁壯土壤', hint: '已長出新的理解、嘗試或行動。' },
  { value: 'MATURE', title: '🌳 成熟土壤', hint: '這一輪已形成可回看的理解或成果。' }
]

const getStageControlLabel = (stage: { title: string }) => stage.title.replace('土壤', '')

const stagePages = reactive<Record<ExperimentStage, number>>({ SEED: 0, GROWING: 0, MATURE: 0 })
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

const stageQueries = Object.fromEntries(stages.map(({ value }) => [value, useQuery({
  queryKey: computed(() => ['experiment-cards', experimentId.value, value, stagePages[value]]),
  queryFn: () => experimentApi.getExperimentCards(experimentId.value, value, stagePages[value])
})])) as Record<ExperimentStage, ReturnType<typeof useQuery>>

const sourceCardsQuery = useQuery({
  queryKey: computed(() => ['experiment-source-cards', experimentId.value]),
  enabled: computed(() => addVisible.value || growVisible.value),
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
  onSuccess: async () => {
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
  if (editForm.title && editForm.hypothesis) editMutation.mutate()
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
            <ExpandableText
              class="experiment-hypothesis"
              :content="experimentQuery.data.value.hypothesis || experimentQuery.data.value.description || '尚未寫下這個實驗主題想觀察的假設。'"
              :lines="4"
            />
            <ExpandableText
              v-if="experimentQuery.data.value.description && experimentQuery.data.value.hypothesis"
              class="experiment-description"
              :content="experimentQuery.data.value.description"
              :lines="5"
            />
          </div>
          <div class="experiment-primary-actions">
            <el-button type="primary" :icon="Plus" @click="openAdd">種入卡片</el-button>
            <el-button :icon="MagicStick" :disabled="experimentQuery.data.value.seedCount + experimentQuery.data.value.growingCount + experimentQuery.data.value.matureCount === 0" @click="openGrow">長出新卡</el-button>
          </div>
        </div>
      </section>

      <section class="soil-workspace" aria-label="實驗主題中的卡片土壤">
        <header class="soil-workspace-heading">
          <div><p>三塊土壤彼此獨立，不代表卡片必須依序前進。</p></div>
        </header>
        <div class="soil-grid">
          <section v-for="stage in stages" :key="stage.value" class="soil-column">
            <header class="soil-heading">
              <div><h3>{{ stage.title }}</h3><p>{{ stage.hint }}</p></div>
              <span>{{ (stageQueries[stage.value].data.value as any)?.totalElements ?? 0 }}</span>
            </header>
            <el-skeleton v-if="stageQueries[stage.value].isLoading.value" :rows="3" animated />
            <el-empty v-else-if="!(stageQueries[stage.value].data.value as any)?.content?.length" description="這塊土壤還沒有卡片" :image-size="56" />
            <template v-else>
              <ExperimentCardItem v-for="card in (stageQueries[stage.value].data.value as any).content" :key="card.cardId" :card="card" :stages="stages" @open="router.push(`/card/${$event}`)" @change-stage="(card, target) => stageMutation.mutate({ card, stage: target })" @edit-note="openNote" @remove="confirmRemove" />
              <el-pagination v-if="(stageQueries[stage.value].data.value as any).totalElements > 10" class="soil-pagination" small layout="prev, pager, next" :current-page="stagePages[stage.value] + 1" :page-size="10" :total="(stageQueries[stage.value].data.value as any).totalElements" @current-change="stagePages[stage.value] = $event - 1" />
            </template>
          </section>
        </div>
      </section>
    </main>
  </section>

  <AppDialog v-model="editVisible" title="編輯實驗主題">
    <el-form label-position="top" @submit.prevent="submitEdit">
      <el-form-item label="實驗主題名稱" required>
        <el-input v-model="editForm.title" maxlength="255" />
        <p class="field-counter">總字數：{{ getTextLength(editForm.title) }} / 255</p>
      </el-form-item>
      <el-form-item label="假設" required>
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
    <template #footer><el-button @click="editVisible = false">取消</el-button><el-button type="primary" :loading="editMutation.isPending.value" :disabled="!editForm.title.trim() || !editForm.hypothesis?.trim()" @click="submitEdit">儲存變更</el-button></template>
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
.soil-workspace { max-width: 1320px; margin: 0 auto; padding: 24px; box-sizing: border-box; }
.soil-workspace-heading { display: flex; justify-content: space-between; margin-bottom: 16px; }
.soil-workspace-heading h2 { margin: 0; font-size: var(--type-section-title); line-height: var(--leading-section); }
.soil-workspace-heading p { margin: 6px 0 0; color: var(--el-text-color-secondary); font-size: var(--type-caption); }
.soil-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 20px; align-items: start; }
.soil-column { min-width: 0; padding: 18px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; background: var(--el-fill-color-extra-light); }
.soil-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 14px; }
.soil-heading h3 { margin: 0; font-size: var(--type-card-title); }
.soil-heading p { margin: 6px 0 0; color: var(--el-text-color-secondary); font-size: var(--type-caption); line-height: var(--leading-ui); }
.soil-heading > span { color: var(--el-text-color-placeholder); font-size: var(--type-meta); font-variant-numeric: tabular-nums; }
.soil-pagination { justify-content: center; margin-top: 14px; }
.dialog-intro { margin: -4px 0 18px; color: var(--el-text-color-secondary); font-size: var(--type-caption); line-height: var(--leading-ui); }
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
@media (max-width: 1080px) { .soil-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 1200px) and (min-width: 901px) { .experiment-detail-page { height: calc(100dvh - 88px); } }
@media (max-width: 900px) { .experiment-detail-page { width: 100%; height: auto; margin: 0; min-height: calc(100dvh - 56px); overflow: visible; } .experiment-detail-content { height: auto; overflow: visible; } }
@media (max-width: 720px) { .soil-grid { grid-template-columns: 1fr; } .experiment-heading { flex-direction: column; } .experiment-primary-actions { width: 100%; } .experiment-primary-actions :deep(.el-button) { flex: 1; } .experiment-grow-context { padding: 20px 0 0; border-top: 1px solid var(--el-border-color-lighter); border-left: 0; } }
@media (max-width: 600px) { .detail-navigation { align-items: center; flex-wrap: wrap; padding: 14px 16px; } .detail-navigation > :first-child { flex-basis: 100%; justify-content: flex-start; } .detail-actions { display: grid; width: 100%; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; margin: 0; } .detail-actions > :first-child { grid-column: 1 / -1; width: 100%; } .detail-edit-actions { display: contents; } .status-trigger, .detail-actions :deep(.el-button) { width: 100%; min-width: 0; padding-inline: 6px; font-size: 12px; } .detail-actions :deep(.el-button) { margin-left: 0; } .experiment-overview-panel { padding: 24px 18px; } .soil-workspace { padding: 18px 12px; } }
</style>
