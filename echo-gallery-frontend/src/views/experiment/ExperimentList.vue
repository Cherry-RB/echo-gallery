<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import { MoreFilled, Plus } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import type { ExperimentDto, ExperimentExplorationRecordDto, ExperimentRequest } from '../../types/experiment'
import { formatDate } from '../../utils/formatDate'
import { getTextLength, trimToTextLength } from '../../utils/textLength'
import { experimentApi } from '../../utils/api/experimentApi'
import { experimentThemeOptions, getExperimentThemeStyle } from '../../utils/experimentTheme'
import AppDialog from '../../components/AppDialog.vue'
import ExperimentExplorationEditor from '../../components/experiment/ExperimentExplorationEditor.vue'
import ExperimentExplorationHistoryDialog from '../../components/experiment/ExperimentExplorationHistoryDialog.vue'
import ExperimentQuickRecordAction from '../../components/experiment/ExperimentQuickRecordAction.vue'
import PageHeader from '../../components/ui/PageHeader.vue'
import AppEmptyState from '../../components/ui/AppEmptyState.vue'

const router = useRouter()
const queryClient = useQueryClient()
type ExperimentScope = 'ACTIVE' | 'ARCHIVED' | 'ALL'
const selectedScope = ref<ExperimentScope>('ACTIVE')
const currentPage = ref(0)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const quickRecordExperimentId = ref<number | null>(null)
const quickRecordMode = ref<'TRY' | 'DISCOVERY'>('TRY')
const editingExplorationRecord = ref<ExperimentExplorationRecordDto | null>(null)
const quickRecordVisible = ref(false)
const historyExperimentId = ref<number | null>(null)
const historyVisible = ref(false)
const form = reactive<ExperimentRequest>({ title: '', hypothesis: '', conclusion: '', description: '', themeColor: 'LEAF' })

const experimentsQuery = useQuery({
  queryKey: computed(() => ['experiments', selectedScope.value, currentPage.value]),
  queryFn: () => experimentApi.getExperiments(
    selectedScope.value === 'ALL' ? 'all' : selectedScope.value === 'ARCHIVED', currentPage.value, 20),
})
const experiments = computed(() => experimentsQuery.data.value?.content ?? [])
const historyQuery = useQuery({
  queryKey: computed(() => ['experiment-exploration', historyExperimentId.value]),
  queryFn: () => experimentApi.getExploration(historyExperimentId.value!),
  enabled: computed(() => historyVisible.value && historyExperimentId.value !== null),
})

function selectScope(scope: ExperimentScope) {
  if (selectedScope.value === scope) return
  selectedScope.value = scope
  currentPage.value = 0
}

const saveMutation = useMutation({
  mutationFn: () => editingId.value
    ? experimentApi.updateExperiment(editingId.value, form)
    : experimentApi.createExperiment(form),
  onSuccess: async (experiment) => {
    const wasEditing = editingId.value !== null
    await queryClient.invalidateQueries({ queryKey: ['experiments'] })
    dialogVisible.value = false
    ElMessage.success(wasEditing ? '實驗主題已更新' : '已建立實驗主題')
    if (!wasEditing) router.push(`/experiments/${experiment.id}`)
  },
  onError: () => ElMessage.error('儲存實驗主題失敗，請稍後再試')
})

const archiveMutation = useMutation({
  mutationFn: ({ id, archived }: { id: number; archived: boolean }) => experimentApi.setExperimentArchived(id, archived),
  onSuccess: async (experiment) => {
    await queryClient.invalidateQueries({ queryKey: ['experiments'] })
    if (!experimentsQuery.data.value?.content.length && currentPage.value > 0) currentPage.value--
    ElMessage.success(experiment.isArchived ? '實驗主題已封存' : '實驗主題已恢復')
  },
  onError: () => ElMessage.error('更新實驗主題狀態失敗')
})

const openCreate = () => {
  editingId.value = null
  form.title = ''
  form.hypothesis = ''
  form.conclusion = ''
  form.description = ''
  form.themeColor = 'LEAF'
  dialogVisible.value = true
}

const openEdit = (experiment: ExperimentDto) => {
  editingId.value = experiment.id
  form.title = experiment.title
  form.hypothesis = experiment.hypothesis ?? ''
  form.conclusion = experiment.conclusion ?? ''
  form.description = experiment.description ?? ''
  form.themeColor = experiment.themeColor
  dialogVisible.value = true
}

const handleCardCommand = (experiment: ExperimentDto, command: string) => {
  if (command === 'edit') openEdit(experiment)
  if (command === 'archive') archiveMutation.mutate({ id: experiment.id, archived: true })
  if (command === 'restore') archiveMutation.mutate({ id: experiment.id, archived: false })
}

const openQuickRecord = (experimentId: number, mode: 'TRY' | 'DISCOVERY') => {
  quickRecordExperimentId.value = experimentId
  quickRecordMode.value = mode
  editingExplorationRecord.value = null
  quickRecordVisible.value = true
}

const openHistory = (experimentId: number) => {
  historyExperimentId.value = experimentId
  historyVisible.value = true
}

const submit = () => {
  form.title = trimToTextLength(form.title, 255).trim()
  form.hypothesis = trimToTextLength(form.hypothesis ?? '', 2000).trim()
  form.conclusion = trimToTextLength(form.conclusion ?? '', 10000).trim()
  form.description = trimToTextLength(form.description ?? '', 5000)
  if (!form.title || saveMutation.isPending.value) return
  saveMutation.mutate()
}
</script>

<template>
  <section class="experiment-list-page app-list-page app-page app-page--workspace">
    <PageHeader title="實驗場" description="讓彼此呼應的卡片在此相遇。沿著一個問題慢慢探索，看看會萌生什麼。">
      <template #actions>
        <el-button type="primary" :icon="Plus" @click="openCreate">建立實驗主題</el-button>
      </template>
    </PageHeader>

    <div class="experiment-dashboard app-workspace app-workspace--edge-to-edge">
      <div class="experiment-list-toolbar app-toolbar">
        <el-radio-group :model-value="selectedScope" class="status-filter app-list-scope" aria-label="實驗主題範圍" @update:model-value="selectScope">
          <el-radio-button value="ACTIVE">觀察中</el-radio-button>
          <el-radio-button value="ARCHIVED">已封存</el-radio-button>
          <el-radio-button value="ALL">全部</el-radio-button>
        </el-radio-group>
      </div>

      <p class="experiment-results" aria-live="polite">{{ selectedScope === 'ARCHIVED' ? '已封存' : selectedScope === 'ALL' ? '全部實驗主題' : '觀察中' }}<template v-if="experimentsQuery.data.value"> · {{ experimentsQuery.data.value.totalElements }} 個實驗主題</template></p>
      <div v-if="experimentsQuery.isLoading.value" class="experiment-list">
        <el-card v-for="index in 3" :key="index" shadow="never" class="experiment-list-card app-collection-card"><el-skeleton :rows="3" animated /></el-card>
      </div>

      <el-result
        v-else-if="experimentsQuery.isError.value"
        icon="error"
        title="無法載入實驗主題"
        sub-title="請稍後重試。"
      >
        <template #extra><el-button type="primary" @click="experimentsQuery.refetch()">重新載入</el-button></template>
      </el-result>

      <AppEmptyState
        v-else-if="experiments.length === 0"
        :description="selectedScope === 'ARCHIVED' ? '目前沒有已封存的實驗主題' : selectedScope === 'ALL' ? '目前沒有實驗主題。' : '探索尚未開始。先為一個想弄懂的問題，留下一處收集材料的地方。'"
      >
        <el-button v-if="selectedScope === 'ACTIVE'" type="primary" :icon="Plus" @click="openCreate">
          建立第一個實驗主題
        </el-button>
      </AppEmptyState>

      <div v-else class="experiment-list">
        <el-card
          v-for="experiment in experiments"
          :key="experiment.id"
          shadow="never"
          class="experiment-list-card app-collection-card"
          :style="getExperimentThemeStyle(experiment.themeColor)"
          role="link"
          tabindex="0"
          :aria-label="`查看實驗主題：${experiment.title}`"
          @click="router.push(`/experiments/${experiment.id}`)"
          @keydown.enter.self="router.push(`/experiments/${experiment.id}`)"
          @keydown.space.self.prevent="router.push(`/experiments/${experiment.id}`)"
        >
          <div class="experiment-card-content">
            <div class="experiment-copy">
              <el-tooltip :content="experiment.title" :show-after="400" placement="top" popper-class="content-tooltip">
                <h2 class="experiment-title">{{ experiment.title }}</h2>
              </el-tooltip>
              <el-tooltip :content="experiment.currentTry || experiment.hypothesis || experiment.description || '還沒設定下一步。不妨先翻翻收集的材料。'" :show-after="400" placement="top" popper-class="content-tooltip">
                <p :class="['experiment-hypothesis', { empty: !experiment.currentTry && !experiment.hypothesis && !experiment.description }]">
                  <span v-if="experiment.currentTry" class="experiment-current-try-label">目前想試</span>
                  {{ experiment.currentTry || experiment.hypothesis || experiment.description || '還沒設定下一步。不妨先翻翻收集的材料。' }}
                </p>
              </el-tooltip>
            </div>
            <aside class="experiment-summary-side">
              <header class="experiment-card-header">
                <ExperimentQuickRecordAction @select="openQuickRecord(experiment.id, $event)" />
                <button v-if="experiment.explorationRecordCount > 0" type="button" class="exploration-record-count" :aria-label="`查看 ${experiment.explorationRecordCount} 筆探索紀錄`" @click.stop="openHistory(experiment.id)">{{ experiment.explorationRecordCount }}</button>
                <el-dropdown trigger="click" @command="handleCardCommand(experiment, $event)">
                  <el-button text circle :icon="MoreFilled" :disabled="archiveMutation.isPending.value" aria-label="實驗主題操作" @click.stop />
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="edit">編輯實驗主題</el-dropdown-item>
                      <el-dropdown-item :command="experiment.isArchived ? 'restore' : 'archive'" divided>
                        {{ experiment.isArchived ? '恢復實驗主題' : '封存實驗主題' }}
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </header>
              <footer class="experiment-card-footer">最近活動於 {{ formatDate(experiment.updatedAt) }}</footer>
            </aside>
          </div>
        </el-card>
      </div>

      <el-pagination
        v-if="(experimentsQuery.data.value?.totalElements ?? 0) > 20"
        class="app-list-pagination"
        layout="total, prev, pager, next"
        :current-page="currentPage + 1"
        :page-size="20"
        :total="experimentsQuery.data.value?.totalElements ?? 0"
        @current-change="currentPage = $event - 1"
      />
    </div>
  </section>

  <AppDialog :busy="saveMutation.isPending.value" :draft="form"
    v-model="dialogVisible"
    :title="editingId ? '編輯實驗主題' : '建立實驗主題'"
    width="min(680px, calc(100vw - 32px))"
    scroll-body
    destroy-on-close
  >
    <p class="dialog-intro">先為想探索的方向取個名字；實驗目的與結論之後再補也可以。</p>
    <el-form label-position="top" @submit.prevent="submit">
      <el-form-item label="實驗主題名稱" required>
        <el-input v-model="form.title" maxlength="255" placeholder="例如：尋找一種適合自己的學習方式" />
        <p class="field-counter">總字數：{{ getTextLength(form.title) }} / 255</p>
      </el-form-item>
      <el-form-item label="實驗目的（選填）">
        <el-input v-model="form.hypothesis" type="textarea" :rows="3" maxlength="2000" placeholder="想弄懂哪個問題？還不清楚，也可以先留白。" />
        <p class="field-counter">總字數：{{ getTextLength(form.hypothesis ?? '') }} / 2000</p>
      </el-form-item>
      <el-form-item label="實驗結論（選填）">
        <el-input v-model="form.conclusion" type="textarea" :rows="4" maxlength="10000" placeholder="形成階段性理解後，再寫下目前的結論。" />
        <p class="field-counter">總字數：{{ getTextLength(form.conclusion ?? '') }} / 10,000</p>
      </el-form-item>
      <el-form-item label="主題說明（選填）">
        <el-input v-model="form.description" type="textarea" :rows="3" maxlength="5000" placeholder="補充背景、動機或預計如何觀察。" />
        <p class="field-counter">總字數：{{ getTextLength(form.description ?? '') }} / 5000</p>
      </el-form-item>
      <el-form-item label="識別色">
        <div class="theme-picker">
          <button v-for="option in experimentThemeOptions" :key="option.value" type="button" class="theme-option" :class="{ active: form.themeColor === option.value }" :style="{ '--option-color': option.vividColor }" :aria-pressed="form.themeColor === option.value" @click="form.themeColor = option.value">
            <span aria-hidden="true"></span>{{ option.label }}
          </button>
        </div>
      </el-form-item>
    </el-form>
    <template #footer="{ requestClose }">
      <el-button :disabled="saveMutation.isPending.value" @click="requestClose">取消</el-button>
      <el-button type="primary" :loading="saveMutation.isPending.value" :disabled="!form.title.trim()" @click="submit">{{ editingId ? '儲存變更' : '建立實驗主題' }}</el-button>
    </template>
  </AppDialog>

  <ExperimentExplorationEditor
    v-if="quickRecordExperimentId !== null"
    v-model="quickRecordVisible"
    :experiment-id="quickRecordExperimentId"
    :mode="quickRecordMode"
    :record="editingExplorationRecord"
    @closed="quickRecordExperimentId = null; editingExplorationRecord = null"
  />

  <ExperimentExplorationHistoryDialog
    :experiment-id="historyExperimentId ?? undefined"
    v-if="historyExperimentId !== null"
    v-model="historyVisible"
    :records="historyQuery.data.value?.records ?? []"
    :loading="historyQuery.isLoading.value"
    manageable
    :show-organize="false"
    @record="historyExperimentId !== null && openQuickRecord(historyExperimentId, $event)"
    @add-thread="router.push({ path: '/experiments/' + historyExperimentId, query: { importRecord: $event == null ? 'all' : String($event) } })"
    @open-card="router.push(`/card/${$event}`)"
  />
</template>

<style scoped>
.experiment-list-page { width: 100%; box-sizing: border-box; }
.experiment-list-toolbar { display: flex; justify-content: space-between; gap: var(--space-md); margin-bottom: var(--space-md); }
.experiment-results { margin: 0 0 var(--space-md); color: var(--el-text-color-regular); font-size: var(--type-secondary); }
.experiment-list { display: grid; grid-template-columns: repeat(auto-fit, minmax(440px, 1fr)); gap: var(--space-md); }
.experiment-list-card { box-shadow: none; cursor: pointer; }
.experiment-list-card :deep(.el-card__body) { padding: var(--space-md); }
.experiment-card-content { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: var(--space-md); }
.experiment-card-header { display: flex; align-items: center; justify-content: flex-end; min-height: 28px; gap: var(--space-sm); }
.experiment-copy { min-width: 0; padding: 0; }
.experiment-title { margin: 0; color: var(--el-text-color-primary); font-size: var(--type-card-title); font-weight: var(--weight-semibold); line-height: var(--leading-card-title); overflow-wrap: anywhere; }
.experiment-hypothesis { display: -webkit-box; margin: var(--space-xs) 0 0; overflow: hidden; color: var(--el-text-color-secondary); font-size: var(--type-body); font-weight: 400; line-height: var(--leading-body); overflow-wrap: break-word; white-space: pre-line; -webkit-box-orient: vertical; -webkit-line-clamp: 4; }
.experiment-hypothesis.empty { color: var(--el-text-color-placeholder); font-weight: 400; }
.experiment-current-try-label { display: block; margin-bottom: var(--space-2xs); color: var(--el-color-primary); font-size: var(--type-meta); font-weight: var(--weight-semibold); }
.exploration-record-count { display: inline-flex; align-items: center; justify-content: center; min-width: 22px; min-height: 22px; padding: 0 var(--space-xs); border: 1px solid var(--el-color-primary-light-7); border-radius: var(--radius-sm); background: var(--el-color-primary-light-9); color: var(--el-color-primary); cursor: pointer; font: inherit; font-size: var(--type-meta); font-weight: var(--weight-semibold); font-variant-numeric: tabular-nums; line-height: 1; }
.exploration-record-count:hover, .exploration-record-count:focus-visible { border-color: var(--el-color-primary); background: var(--el-color-primary-light-8); }
.experiment-summary-side { display: flex; min-width: 0; flex-direction: column; align-items: stretch; }
.experiment-card-footer { margin-top: auto; color: var(--el-text-color-placeholder); font-size: var(--type-meta); text-align: right; }
.dialog-intro { margin: 0 0 var(--space-md); color: var(--el-text-color-secondary); font-size: var(--type-caption); line-height: var(--leading-ui); }
.field-counter { width: 100%; margin: 6px 0 0; color: var(--el-text-color-placeholder); font-size: var(--type-meta); }
.theme-picker { display: flex; flex-wrap: wrap; gap: 8px; }
.theme-option { display: inline-flex; align-items: center; gap: 7px; padding: 7px 10px; border: 1px solid var(--el-border-color); border-radius: 7px; background: var(--el-bg-color); color: var(--el-text-color-regular); font: inherit; cursor: pointer; }
.theme-option > span { width: 14px; height: 14px; border-radius: 50%; background: var(--option-color); }
.theme-option.active { border-color: color-mix(in srgb, var(--option-color) 40%, var(--el-bg-color)); background: color-mix(in srgb, var(--option-color) 26%, var(--el-bg-color)); color: var(--el-text-color-primary); }
@media (max-width: 900px) { .experiment-list { grid-template-columns: 1fr; } }
@media (max-width: 760px) { .experiment-list-toolbar { width: 100%; } .experiment-card-content { min-height: 0; grid-template-columns: 1fr; gap: var(--space-sm); } .experiment-card-footer { margin-top: var(--space-sm); } }
</style>
