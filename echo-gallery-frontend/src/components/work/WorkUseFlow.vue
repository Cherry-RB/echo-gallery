<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import AppDialog from '../AppDialog.vue'
import WorkInfoFields, { WORK_FORM_WIDTH } from './WorkInfoFields.vue'
import PickerOption from '../ui/PickerOption.vue'
import PickerWorkspace from '../ui/PickerWorkspace.vue'
import SelectionSummary from '../ui/SelectionSummary.vue'
import WorkSourceReadingDialog from './WorkSourceReadingDialog.vue'
import { workStages } from '../../types/work'
import { useWorkDialogGuard } from '../../composables/useWorkDialogGuard'
import { workUseTarget } from '../../utils/workFlow'
import { workApi } from '../../utils/api/workApi'
import type { WorkInfo, WorkSource } from '../../types/work'

const router = useRouter()
const client = useQueryClient()
const shell = ref<InstanceType<typeof AppDialog>>()
const step = ref<'sources' | 'target'>('sources')
const mode = ref<'new' | 'existing'>('new')
const sourceKeyword = ref('')
const sourceExperimentId = ref<number>()
const sourcePage = ref(1)
const workKeyword = ref('')
const workPage = ref(1)
const selectedWorkId = ref<number>()
const selectedSources = reactive(new Map<string, WorkSource>())
const reading = ref<WorkSource>()
const busy = ref(false)
const error = ref('')
let form = reactive<WorkInfo>({ title: '', description: '', editorMode: 'INTERNAL', experimentId: null, externalUrl: '' })
const draftBaseline = ref<string>()
const serializeDraft = () => JSON.stringify([step.value, mode.value, [...selectedSources.keys()], form, selectedWorkId.value])

useWorkDialogGuard(shell, () => !!workUseTarget.value, () => {
  if (!reading.value) return false
  reading.value = undefined
  return true
})
onBeforeUnmount(() => { workUseTarget.value = undefined })

const keyOf = (source: WorkSource) => source.cardId ? `card-${source.cardId}` : `record-${source.recordId}`
const sourceMetadata = (source: WorkSource) => [source.recordId ? '探索紀錄' : '卡片', source.experimentTitle, source.archived ? '已封存' : ''].filter(Boolean).join(' · ')
const startedFromCard = computed(() => !!workUseTarget.value?.cardIds?.length && !(workUseTarget.value?.recordIds?.length))
const sourceParams = computed(() => ({ kind: 'RECORD', keyword: sourceKeyword.value, experimentId: sourceExperimentId.value, page: sourcePage.value - 1 }))
const sourceQuery = useQuery({
  queryKey: computed(() => ['work-candidates', sourceParams.value]),
  queryFn: () => workApi.candidates(sourceParams.value),
  enabled: computed(() => !!workUseTarget.value && step.value === 'sources' && !startedFromCard.value),
})
const experiments = useQuery({ queryKey: ['work-experiments'], queryFn: workApi.experiments, enabled: computed(() => !!workUseTarget.value) })
const workParams = computed(() => ({ keyword: workKeyword.value, page: workPage.value - 1 }))
const works = useQuery({
  queryKey: computed(() => ['works', 'source-targets', workParams.value]),
  queryFn: () => workApi.list(workParams.value),
  enabled: computed(() => !!workUseTarget.value && step.value === 'target' && mode.value === 'existing'),
})
const selectedSourceCount = computed(() => selectedSources.size)
const cardOriginTitle = computed(() => workUseTarget.value?.cardTitle || [...selectedSources.values()].find(source => source.cardId)?.title || '目前卡片')

function placeholderRecord(recordId: number, experimentId?: number): WorkSource {
  return { id: null, cardId: null, recordId, title: `探索紀錄 #${recordId}`, text: '', experimentId: experimentId ?? null, experimentTitle: null, archived: false }
}

function placeholderCard(cardId: number, title?: string): WorkSource {
  return { id: null, cardId, recordId: null, title: title || `卡片 #${cardId}`, text: '', experimentId: null, experimentTitle: null, archived: false }
}

function reset(target: NonNullable<typeof workUseTarget.value>) {
  selectedSources.clear()
  for (const recordId of target.recordIds ?? []) {
    const source = placeholderRecord(recordId, target.experimentId)
    selectedSources.set(keyOf(source), source)
  }
  for (const cardId of target.cardIds ?? []) {
    const source = placeholderCard(cardId, target.cardTitle)
    selectedSources.set(keyOf(source), source)
  }
  step.value = target.cardIds?.length && !(target.recordIds?.length) ? 'target' : 'sources'
  mode.value = 'new'
  sourceKeyword.value = ''
  sourceExperimentId.value = target.experimentId
  sourcePage.value = 1
  workKeyword.value = ''
  workPage.value = 1
  selectedWorkId.value = undefined
  reading.value = undefined
  error.value = ''
  Object.assign(form, { title: '', description: '', editorMode: 'INTERNAL', experimentId: target.experimentId ?? null, externalUrl: '' })
  // 閱讀來源會暫時卸載選取對話框；基準由流程持有，回來後仍能辨識未儲存變更。
  draftBaseline.value = serializeDraft()
}

watch(workUseTarget, target => { if (target) reset(target) }, { immediate: true })
watch([sourceKeyword, sourceExperimentId], () => { sourcePage.value = 1 })
watch(workKeyword, () => { workPage.value = 1 })

function toggleSource(source: WorkSource) {
  if (busy.value) return
  const key = keyOf(source)
  if (selectedSources.has(key)) selectedSources.delete(key)
  else if (selectedSources.size < 100) selectedSources.set(key, source)
}

function selectSourceStep() {
  if (!selectedSources.size) return
  step.value = 'target'
}

function returnToHistory(requestClose: () => void) {
  if (step.value === 'target') {
    if (startedFromCard.value) { requestClose(); return }
    step.value = 'sources'; return
  }
  if (workUseTarget.value?.returnToHistory) {
    workUseTarget.value = undefined
    return
  }
  requestClose()
}

async function save() {
  if (busy.value || !workUseTarget.value || !selectedSources.size) return
  if (mode.value === 'new' && !form.title.trim()) { error.value = '請填寫作品名稱。'; return }
  if (mode.value === 'existing' && !selectedWorkId.value) { error.value = '請選擇要加入的作品。'; return }
  busy.value = true
  error.value = ''
  try {
    const sources = [...selectedSources.values()].map(({ cardId, recordId }) => ({ cardId: cardId ?? undefined, recordId: recordId ?? undefined }))
    let workId = selectedWorkId.value
    if (mode.value === 'new') workId = (await workApi.create({ ...form, sources })).work.id
    else await workApi.addSources(workId!, sources)
    await client.invalidateQueries({ queryKey: ['works'] })
    workUseTarget.value = undefined
    void router.push(`/works/${workId}`)
  } catch {
    error.value = '無法加入作品，請保留目前選擇後再試一次。'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <AppDialog v-if="!reading" ref="shell" :model-value="!!workUseTarget" title="用於作品" :pattern="step === 'sources' ? 'picker' : 'form'" :width="step === 'sources' ? 'min(980px, calc(100vw - 32px))' : mode === 'new' ? WORK_FORM_WIDTH : 'min(560px, calc(100vw - 32px))'" :draft="[step, mode, [...selectedSources.keys()], form, selectedWorkId]" :draft-baseline="draftBaseline" :busy="busy" scroll-body @update:model-value="workUseTarget = undefined">
    <template v-if="step === 'sources'">
      <p class="app-dialog-intro">選擇想用於同一件作品的探索紀錄。原始紀錄會保留。</p>
      <PickerWorkspace>
        <div class="source-controls">
          <el-input v-model="sourceKeyword" :disabled="busy" clearable placeholder="搜尋試法或發現文字" aria-label="搜尋探索紀錄" />
          <el-select v-model="sourceExperimentId" :disabled="busy" clearable placeholder="所有實驗" aria-label="篩選實驗">
            <el-option v-for="experiment in experiments.data.value" :key="experiment.id" :value="experiment.id" :label="experiment.title + (experiment.archived ? '（已封存）' : '')" />
          </el-select>
        </div>
        <p class="source-rule">已選 {{ selectedSourceCount }} 筆素材；可跨實驗選取探索紀錄，最多 100 筆。</p>
        <div class="picker-results">
          <p v-if="sourceQuery.isPending.value">正在載入探索紀錄…</p>
          <el-alert v-else-if="sourceQuery.isError.value" title="無法載入探索紀錄" type="error" :closable="false"><el-button link type="primary" @click="sourceQuery.refetch()">重新載入</el-button></el-alert>
          <p v-else-if="!sourceQuery.data.value?.content.length">找不到符合條件的探索紀錄。</p>
          <PickerOption v-for="source in sourceQuery.data.value?.content" :key="keyOf(source)" :title="source.title" :selected="selectedSources.has(keyOf(source))" multiple :disabled="busy || (selectedSources.size >= 100 && !selectedSources.has(keyOf(source)))" @select="toggleSource(source)">
            <span>{{ sourceMetadata(source) }}</span>
            <template #actions><el-button link type="primary" :disabled="busy" @click.stop="reading = source">閱讀全文</el-button></template>
          </PickerOption>
        </div>
        <el-pagination v-if="(sourceQuery.data.value?.totalElements ?? 0) > 20" v-model:current-page="sourcePage" :disabled="busy" :page-size="20" :total="sourceQuery.data.value?.totalElements ?? 0" layout="prev, pager, next" />
        <template #selection>
          <h3>已選素材（{{ selectedSourceCount }}）</h3>
          <div class="selected-sources">
            <SelectionSummary v-if="!selectedSources.size" empty-text="從左側選擇一筆或多筆探索紀錄。" />
            <SelectionSummary v-for="source in selectedSources.values()" :key="keyOf(source)" :title="source.title" :metadata="sourceMetadata(source)" hide-label>
              <template #actions><el-button link :disabled="busy" @click="selectedSources.delete(keyOf(source))">移除</el-button></template>
            </SelectionSummary>
          </div>
        </template>
      </PickerWorkspace>
    </template>

    <template v-else>
      <p class="app-dialog-intro">{{ startedFromCard ? `來源卡片：〈${cardOriginTitle}〉。這張卡片會加入參考素材，不會自動貼入正文。` : '這些紀錄會加入「參考素材」，不會自動合併或覆寫作品正文。' }}</p>
      <el-radio-group v-model="mode" :disabled="busy"><el-radio-button value="new">建立新作品</el-radio-button><el-radio-button value="existing">加入既有作品</el-radio-button></el-radio-group>
      <el-form v-if="mode === 'new'" label-position="top" :disabled="busy" @submit.prevent="save"><WorkInfoFields v-model="form" :experiments="experiments.data.value" /></el-form>
      <section v-else class="work-targets">
        <el-input v-model="workKeyword" :disabled="busy" clearable placeholder="搜尋作品名稱" aria-label="搜尋作品名稱" />
        <p v-if="works.isPending.value">正在載入作品…</p>
        <el-alert v-else-if="works.isError.value" title="無法載入作品" type="error" :closable="false"><el-button link type="primary" @click="works.refetch()">重新載入</el-button></el-alert>
        <p v-else-if="!works.data.value?.content.length">找不到符合條件的作品。</p>
        <div v-else class="work-options"><PickerOption v-for="work in works.data.value.content" :key="work.id" :title="work.title" :selected="selectedWorkId === work.id" :disabled="busy" @select="selectedWorkId = work.id"><span>{{ work.experimentTitle || '未設定主要實驗' }} · {{ workStages[work.stage] }}</span></PickerOption></div>
        <el-pagination v-if="(works.data.value?.totalElements ?? 0) > 20" v-model:current-page="workPage" :disabled="busy" :page-size="20" :total="works.data.value?.totalElements ?? 0" layout="prev, pager, next" />
      </section>
    </template>
    <el-alert v-if="error" class="flow-error" :title="error" type="error" :closable="false" />
    <template #footer="{ requestClose }">
      <el-button :disabled="busy" @click="returnToHistory(requestClose)">{{ step === 'sources' && workUseTarget?.returnToHistory ? '返回探索紀錄' : step === 'target' ? (startedFromCard ? '取消' : '上一步') : '取消' }}</el-button>
      <el-button v-if="step === 'sources'" type="primary" :disabled="!selectedSources.size" @click="selectSourceStep">下一步（{{ selectedSourceCount }}）</el-button>
      <el-button v-else type="primary" :disabled="mode === 'existing' && !selectedWorkId" :loading="busy" @click="save">{{ mode === 'new' ? '建立並開始' : '加入作品' }}</el-button>
    </template>
  </AppDialog>
  <WorkSourceReadingDialog v-else-if="reading" :model-value="true" :source="reading" close-label="返回選擇" @update:model-value="reading = undefined" />
</template>

<style scoped>
.source-controls { display: flex; flex-wrap: wrap; gap: var(--space-sm); margin-bottom: var(--space-md); }
.source-controls > * { flex: 1; min-width: min(100%, 220px); }
.source-rule { margin: 0; color: var(--el-text-color-secondary); font-size: var(--type-secondary); line-height: var(--leading-ui); }
.selected-sources { display: grid; gap: var(--space-sm); }
.el-form, .work-targets { margin-top: var(--space-md); }
.work-options { display: grid; gap: var(--space-xs); margin-top: var(--space-sm); }
.flow-error { margin-top: var(--space-md); }
.el-pagination { margin-top: var(--space-md); }
</style>
