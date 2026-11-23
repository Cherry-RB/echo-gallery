<script setup lang="ts">
import GoalBadge from '../../components/goal/GoalBadge.vue'
import { computed, reactive, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import type { ExperimentRecordRequest, ExperimentRequest } from '../../types/experiment'
import { formatDate } from '../../utils/formatDate'
import { getTextLength, trimToTextLength } from '../../utils/textLength'
import { experimentApi } from '../../utils/api/experimentApi'
import { experimentThemeOptions, getExperimentThemeStyle } from '../../utils/experimentTheme'
import AppDialog from '../../components/AppDialog.vue'
import ExperimentRecordEditor from '../../components/experiment/ExperimentRecordEditor.vue'
import PageHeader from '../../components/ui/PageHeader.vue'
import AppEmptyState from '../../components/ui/AppEmptyState.vue'

const router = useRouter()
const queryClient = useQueryClient()
type ExperimentScope = 'ACTIVE' | 'ARCHIVED' | 'ALL'
const selectedScope = ref<ExperimentScope>('ACTIVE')
const currentPage = ref(0)
const dialogVisible = ref(false)
const quickRecordExperimentId = ref<number | null>(null)
const quickRecordVisible = ref(false)
const quickRecordSaving = ref(false)
const form = reactive<ExperimentRequest>({ title: '', startingPoint: '', desiredOutcome: '', hypothesis: '', conclusion: '', description: '', themeColor: 'LEAF' })

const experimentsQuery = useQuery({
  queryKey: computed(() => ['experiments', selectedScope.value, currentPage.value]),
  queryFn: () => experimentApi.getExperiments(
    selectedScope.value === 'ALL' ? 'all' : selectedScope.value === 'ARCHIVED', currentPage.value, 20),
})
const experiments = computed(() => experimentsQuery.data.value?.content ?? [])

function selectScope(scope: ExperimentScope) {
  if (selectedScope.value === scope) return
  selectedScope.value = scope
  currentPage.value = 0
}

const saveMutation = useMutation({
  mutationFn: () => experimentApi.createExperiment(form),
  onSuccess: async (experiment) => {
    await queryClient.invalidateQueries({ queryKey: ['experiments'] })
    dialogVisible.value = false
    ElMessage.success('已建立實驗主題')
    router.push(`/experiments/${experiment.id}`)
  },
  onError: () => ElMessage.error('儲存實驗主題失敗，請稍後再試')
})

const openCreate = () => {
  form.title = ''
  form.startingPoint = ''
  form.desiredOutcome = ''
  form.hypothesis = ''
  form.conclusion = ''
  form.description = ''
  form.themeColor = 'LEAF'
  dialogVisible.value = true
}

const openQuickRecord = (experimentId: number) => {
  quickRecordExperimentId.value = experimentId
  quickRecordVisible.value = true
}
const saveQuickRecord = async (value: ExperimentRecordRequest) => {
  if (quickRecordExperimentId.value === null) return
  quickRecordSaving.value = true
  try {
    await experimentApi.createRecord(quickRecordExperimentId.value, value)
    await queryClient.invalidateQueries({ queryKey: ['experiments'] })
    quickRecordVisible.value = false
    ElMessage.success('已新增探索紀錄')
  } catch { ElMessage.error('儲存探索紀錄失敗，請稍後重試') } finally { quickRecordSaving.value = false }
}

const submit = () => {
  form.title = trimToTextLength(form.title, 255).trim()
  form.startingPoint = trimToTextLength(form.startingPoint ?? '', 5000).trim()
  form.desiredOutcome = trimToTextLength(form.desiredOutcome ?? '', 5000).trim()
  form.hypothesis = trimToTextLength(form.hypothesis ?? '', 2000).trim()
  form.conclusion = trimToTextLength(form.conclusion ?? '', 10000).trim()
  form.description = trimToTextLength(form.description ?? '', 5000)
  if (!form.title || saveMutation.isPending.value) return
  saveMutation.mutate()
}
</script>

<template>
  <section class="experiment-list-page app-list-page app-page app-page--workspace">
    <PageHeader title="實驗場" description="每個尚未解答的問題，都值得一次嘗試。收集線索、留下發現，看看事情會如何發展。">
      <template #actions>
        <el-button type="primary" :icon="Plus" @click="openCreate">建立實驗主題</el-button>
      </template>
    </PageHeader>

    <div class="experiment-dashboard app-workspace">
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
        :description="selectedScope === 'ARCHIVED' ? '目前沒有已封存的實驗主題' : selectedScope === 'ALL' ? '目前沒有實驗主題。' : '目前沒有未封存的實驗主題。可以從一個想弄懂的問題開始，留下一次嘗試。'"
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
            <header class="experiment-card-header">
              <el-tooltip :content="experiment.title" :show-after="400" placement="top" popper-class="content-tooltip">
                <h2 class="experiment-title">{{ experiment.title }}</h2>
              </el-tooltip>
              <div class="experiment-card-actions">
                <GoalBadge kind="EXPERIMENT" :id="experiment.id" />
              </div>
            </header>
            <el-tooltip :content="experiment.desiredOutcome || experiment.startingPoint || experiment.hypothesis || experiment.description || '尚未補充實驗方向。'" :show-after="400" placement="top" popper-class="content-tooltip">
              <div :class="['experiment-direction', { empty: !experiment.desiredOutcome && !experiment.startingPoint && !experiment.hypothesis && !experiment.description }]">
                <span class="experiment-direction-label">{{ experiment.desiredOutcome ? '終點' : experiment.startingPoint ? '起點' : '實驗方向' }}</span>
                <p>{{ experiment.desiredOutcome || experiment.startingPoint || experiment.hypothesis || experiment.description || '尚未補充實驗方向。' }}</p>
              </div>
            </el-tooltip>
            <footer class="experiment-card-footer">
              <span>{{ experiment.explorationRecordCount }} 筆</span>
              <time :datetime="formatDate(experiment.updatedAt, 'YYYY-MM-DD')">{{ formatDate(experiment.updatedAt, 'YYYY-MM-DD') }}</time>
              <el-button class="quick-record-trigger" text type="primary" @click.stop="openQuickRecord(experiment.id)">＋紀錄</el-button>
            </footer>
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
    title="建立實驗主題"
    width="min(680px, calc(100vw - 32px))"
    scroll-body
    destroy-on-close
  >
    <p class="dialog-intro">先為想探索的方向取個名字；起點與想達成的改變之後再補也可以。</p>
    <el-form label-position="top" @submit.prevent="submit">
      <el-form-item label="實驗主題名稱" required>
        <el-input v-model="form.title" maxlength="255" placeholder="例如：尋找一種適合自己的學習方式" />
        <p class="field-counter">總字數：{{ getTextLength(form.title) }} / 255</p>
      </el-form-item>
      <el-form-item label="目前的狀況">
        <el-input v-model="form.startingPoint" type="textarea" :rows="3" maxlength="5000" placeholder="現在正處於什麼狀況？" />
        <p class="field-counter">總字數：{{ getTextLength(form.startingPoint ?? '') }} / 5,000</p>
      </el-form-item>
      <el-form-item label="想達成的改變">
        <el-input v-model="form.desiredOutcome" type="textarea" :rows="3" maxlength="5000" placeholder="希望因為這次實驗，出現什麼改變？" />
        <p class="field-counter">總字數：{{ getTextLength(form.desiredOutcome ?? '') }} / 5,000</p>
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
      <el-button type="primary" :loading="saveMutation.isPending.value" :disabled="!form.title.trim()" @click="submit">建立實驗主題</el-button>
    </template>
  </AppDialog>

  <ExperimentRecordEditor
    v-if="quickRecordExperimentId !== null"
    v-model="quickRecordVisible"
    :saving="quickRecordSaving"
    @save="saveQuickRecord"
    @update:model-value="!$event && (quickRecordExperimentId = null)"
  />
</template>

<style scoped>
.experiment-list-page { width: 100%; box-sizing: border-box; }
.experiment-list-toolbar { display: flex; justify-content: space-between; gap: var(--space-md); margin-bottom: var(--space-md); }
.experiment-results { margin: 0 0 var(--space-md); color: var(--el-text-color-regular); font-size: var(--type-secondary); }
.experiment-dashboard { padding: var(--space-lg); }
.experiment-list { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: var(--space-md); }
.experiment-list-card { box-shadow: none; cursor: pointer; }
.experiment-list-card :deep(.el-card__body) { display: flex; height: 100%; box-sizing: border-box; padding: var(--space-md); }
.experiment-card-content { display: flex; flex: 1; min-height: 288px; flex-direction: column; gap: var(--space-sm); }
.experiment-card-header { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--space-sm); min-width: 0; }
.experiment-card-header > :first-child { min-width: 0; }
.experiment-card-actions { display: flex; flex: 0 0 auto; align-items: center; gap: var(--space-2xs); }
.experiment-title { display: -webkit-box; margin: 0; overflow: hidden; color: var(--el-text-color-primary); font-size: var(--type-card-title); font-weight: var(--weight-semibold); line-height: var(--leading-card-title); overflow-wrap: anywhere; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.experiment-direction { min-width: 0; }
.experiment-direction-label { display: block; margin-bottom: var(--space-2xs); color: var(--el-color-primary); font-size: var(--type-meta); font-weight: var(--weight-semibold); line-height: var(--leading-ui); }
.experiment-direction p { display: -webkit-box; margin: 0; overflow: hidden; color: var(--el-text-color-secondary); font-size: var(--type-secondary); line-height: var(--leading-ui); overflow-wrap: break-word; white-space: pre-line; -webkit-box-orient: vertical; -webkit-line-clamp: 6; }
.experiment-direction.empty p { color: var(--el-text-color-placeholder); }
.experiment-card-footer { display: flex; align-items: center; gap: var(--space-sm); margin-top: auto; color: var(--el-text-color-placeholder); font-size: var(--type-meta); line-height: var(--leading-ui); }
.experiment-card-footer time { white-space: nowrap; }
.quick-record-trigger { margin-left: auto; }
.dialog-intro { margin: 0 0 var(--space-md); color: var(--el-text-color-secondary); font-size: var(--type-caption); line-height: var(--leading-ui); }
.field-counter { width: 100%; margin: 6px 0 0; color: var(--el-text-color-placeholder); font-size: var(--type-meta); }
.theme-picker { display: flex; flex-wrap: wrap; gap: 8px; }
.theme-option { display: inline-flex; align-items: center; gap: 7px; padding: 7px 10px; border: 1px solid var(--el-border-color); border-radius: 7px; background: var(--el-bg-color); color: var(--el-text-color-regular); font: inherit; cursor: pointer; }
.theme-option > span { width: 14px; height: 14px; border-radius: 50%; background: var(--option-color); }
.theme-option.active { border-color: color-mix(in srgb, var(--option-color) 40%, var(--el-bg-color)); background: color-mix(in srgb, var(--option-color) 26%, var(--el-bg-color)); color: var(--el-text-color-primary); }
@media (max-width: 760px) { .experiment-dashboard { padding: var(--space-md); } .experiment-list-toolbar { width: 100%; } .experiment-card-content { min-height: 0; } .experiment-card-footer { flex-wrap: wrap; } .quick-record-trigger { margin-left: 0; } }
</style>
