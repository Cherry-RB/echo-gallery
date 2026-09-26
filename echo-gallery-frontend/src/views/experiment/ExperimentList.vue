<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import { MoreFilled, Plus } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import type { ExperimentDto, ExperimentRequest } from '../../types/experiment'
import { formatDate } from '../../utils/formatDate'
import { getTextLength, trimToTextLength } from '../../utils/textLength'
import { experimentApi } from '../../utils/api/experimentApi'
import { experimentThemeOptions, getExperimentThemeStyle } from '../../utils/experimentTheme'
import AppDialog from '../../components/AppDialog.vue'

const router = useRouter()
const queryClient = useQueryClient()
const selectedScope = ref<'ACTIVE' | 'ARCHIVED'>('ACTIVE')
const currentPage = ref(0)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const form = reactive<ExperimentRequest>({ title: '', hypothesis: '', description: '', themeColor: 'LEAF' })

const experimentsQuery = useQuery({
  queryKey: computed(() => ['experiments', selectedScope.value, currentPage.value]),
  queryFn: () => experimentApi.getExperiments(selectedScope.value === 'ARCHIVED', currentPage.value, 20)
})
const experiments = computed(() => experimentsQuery.data.value?.content ?? [])

watch(selectedScope, () => {
  currentPage.value = 0
})

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
    ElMessage.success(experiment.isArchived ? '實驗主題已封存' : '實驗主題已恢復')
  },
  onError: () => ElMessage.error('更新實驗主題狀態失敗')
})

const openCreate = () => {
  editingId.value = null
  form.title = ''
  form.hypothesis = ''
  form.description = ''
  form.themeColor = 'LEAF'
  dialogVisible.value = true
}

const openEdit = (experiment: ExperimentDto) => {
  editingId.value = experiment.id
  form.title = experiment.title
  form.hypothesis = experiment.hypothesis ?? ''
  form.description = experiment.description ?? ''
  form.themeColor = experiment.themeColor
  dialogVisible.value = true
}

const handleCardCommand = (experiment: ExperimentDto, command: string) => {
  if (command === 'edit') openEdit(experiment)
  if (command === 'archive') archiveMutation.mutate({ id: experiment.id, archived: true })
  if (command === 'restore') archiveMutation.mutate({ id: experiment.id, archived: false })
}

const submit = () => {
  form.title = trimToTextLength(form.title, 255).trim()
  form.hypothesis = trimToTextLength(form.hypothesis ?? '', 2000).trim()
  form.description = trimToTextLength(form.description ?? '', 5000)
  if (!form.title || saveMutation.isPending.value) return
  saveMutation.mutate()
}
</script>

<template>
  <section class="experiment-list-page">
    <header class="page-header">
      <div>
        <h1 class="page-title">實驗場</h1>
        <p class="page-description">讓彼此呼應的卡片聚在一起，持續觀察一個想法會長出什麼。</p>
      </div>
      <div class="page-actions">
        <el-button type="primary" :icon="Plus" @click="openCreate">建立實驗主題</el-button>
      </div>
    </header>

    <div class="experiment-dashboard">
      <div class="experiment-list-toolbar">
        <el-radio-group v-model="selectedScope" class="status-filter" aria-label="實驗主題篩選">
          <el-radio-button value="ACTIVE">觀察中</el-radio-button>
          <el-radio-button value="ARCHIVED">已封存</el-radio-button>
        </el-radio-group>
      </div>

      <div v-if="experimentsQuery.isLoading.value" class="experiment-list">
        <el-card v-for="index in 3" :key="index" shadow="never"><el-skeleton :rows="3" animated /></el-card>
      </div>

      <el-result
        v-else-if="experimentsQuery.isError.value"
        icon="error"
        title="無法載入實驗主題"
        sub-title="請稍後重試，或確認後端服務是否正常。"
      >
        <template #extra><el-button type="primary" @click="experimentsQuery.refetch()">重新載入</el-button></template>
      </el-result>

      <el-empty
        v-else-if="experiments.length === 0"
        :description="selectedScope === 'ARCHIVED' ? '目前沒有已封存的實驗主題' : '先建立一個實驗主題，寫下你想持續觀察的假設。'"
      >
        <el-button v-if="selectedScope === 'ACTIVE'" type="primary" :icon="Plus" @click="openCreate">
          建立第一個實驗主題
        </el-button>
      </el-empty>

      <div v-else class="experiment-list">
        <el-card
          v-for="experiment in experiments"
          :key="experiment.id"
          shadow="never"
          class="experiment-list-card"
          :style="getExperimentThemeStyle(experiment.themeColor)"
          role="link"
          tabindex="0"
          :aria-label="`查看實驗主題：${experiment.title}`"
          @click="router.push(`/experiments/${experiment.id}`)"
          @keydown.enter="router.push(`/experiments/${experiment.id}`)"
          @keydown.space.prevent="router.push(`/experiments/${experiment.id}`)"
        >
          <div class="experiment-card-content">
            <div class="experiment-copy">
              <h2 class="experiment-title">{{ experiment.title }}</h2>
              <p :class="['experiment-hypothesis', { empty: !experiment.currentTry && !experiment.hypothesis && !experiment.description }]">
                <span v-if="experiment.currentTry" class="experiment-current-try-label">目前想試</span>
                {{ experiment.currentTry || experiment.hypothesis || experiment.description || '還沒有寫下目前想試的事；可以先從材料開始。' }}
              </p>
            </div>
            <aside class="experiment-summary-side">
              <header class="experiment-card-header">
                <el-dropdown trigger="click" @command="handleCardCommand(experiment, $event)">
                  <el-button text circle :icon="MoreFilled" aria-label="實驗主題操作" @click.stop />
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
              <section class="soil-summary-grid" aria-label="土壤概況">
                <div class="soil-summary" :aria-label="`種子 ${experiment.seedCount} 張`"><strong>{{ experiment.seedCount }}</strong><span aria-hidden="true">🌱</span></div>
                <div class="soil-summary" :aria-label="`茁壯 ${experiment.growingCount} 張`"><strong>{{ experiment.growingCount }}</strong><span aria-hidden="true">🌿</span></div>
                <div class="soil-summary" :aria-label="`成熟 ${experiment.matureCount} 張`"><strong>{{ experiment.matureCount }}</strong><span aria-hidden="true">🌳</span></div>
              </section>
              <footer class="experiment-card-footer">最近活動於 {{ formatDate(experiment.updatedAt) }}</footer>
            </aside>
          </div>
        </el-card>
      </div>

      <el-pagination
        v-if="(experimentsQuery.data.value?.totalElements ?? 0) > 20"
        class="experiment-pagination"
        layout="prev, pager, next"
        :current-page="currentPage + 1"
        :page-size="20"
        :total="experimentsQuery.data.value?.totalElements ?? 0"
        @current-change="currentPage = $event - 1"
      />
    </div>
  </section>

  <AppDialog
    v-model="dialogVisible"
    :title="editingId ? '編輯實驗主題' : '建立實驗主題'"
    width="min(680px, calc(100vw - 32px))"
    destroy-on-close
  >
    <p class="dialog-intro">先為想探索的方向取個名字；目前想弄懂的事，之後再補也可以。</p>
    <el-form label-position="top" @submit.prevent="submit">
      <el-form-item label="實驗主題名稱" required>
        <el-input v-model="form.title" maxlength="255" placeholder="例如：讓生活變得更可靠" />
        <p class="field-counter">總字數：{{ getTextLength(form.title) }} / 255</p>
      </el-form-item>
      <el-form-item label="目前想弄懂什麼？（選填）">
        <el-input v-model="form.hypothesis" type="textarea" :rows="3" maxlength="2000" placeholder="例如：怎樣讓我更容易開始畫畫？還不清楚也可以先留白。" />
        <p class="field-counter">總字數：{{ getTextLength(form.hypothesis ?? '') }} / 2000</p>
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
    <template #footer>
      <el-button :disabled="saveMutation.isPending.value" @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="saveMutation.isPending.value" :disabled="!form.title.trim()" @click="submit">{{ editingId ? '儲存變更' : '建立實驗主題' }}</el-button>
    </template>
  </AppDialog>
</template>

<style scoped>
.experiment-list-page { width: 100%; max-width: 1240px; margin: 0 auto; box-sizing: border-box; }
.page-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 24px; margin-bottom: 24px; }
.page-title { margin: 0; font-size: var(--type-page-title); line-height: 1.35; }
.page-description { margin: 8px 0 0; color: var(--el-text-color-secondary); font-size: var(--type-ui); line-height: var(--leading-ui); }
.page-actions { display: flex; align-items: center; gap: 12px; }
.experiment-dashboard { margin-top: 12px; padding: 12px; background: var(--el-bg-color-page); }
.experiment-list-toolbar { display: flex; justify-content: flex-end; margin-bottom: 12px; }
.experiment-list { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.experiment-list-card { border-color: var(--el-border-color-light); box-shadow: none; cursor: pointer; transition: transform 0.2s ease, box-shadow 0.2s ease; }
.experiment-list-card:hover, .experiment-list-card:focus-visible { transform: translateY(-2px); border-color: var(--el-border-color); box-shadow: var(--el-box-shadow-lighter); }
.experiment-list-card:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 2px; }
.experiment-list-card :deep(.el-card__body) { padding: 18px 20px; }
.experiment-card-content { display: grid; min-height: 150px; grid-template-columns: minmax(0, 1.55fr) minmax(190px, 0.85fr); gap: 22px; }
.experiment-card-header { display: flex; justify-content: flex-end; min-height: 28px; }
.experiment-copy { min-width: 0; padding: 2px 0; }
.experiment-title { margin: 0; color: var(--el-text-color-primary); font-size: var(--type-card-title); font-weight: 650; line-height: var(--leading-section); overflow-wrap: anywhere; }
.experiment-hypothesis { display: -webkit-box; margin: 9px 0 0; overflow: hidden; color: var(--el-text-color-secondary); font-size: var(--type-body); font-weight: 400; line-height: var(--leading-body); overflow-wrap: break-word; white-space: pre-line; -webkit-box-orient: vertical; -webkit-line-clamp: 4; }
.experiment-hypothesis.empty { color: var(--el-text-color-placeholder); font-weight: 400; }
.experiment-current-try-label { display: block; margin-bottom: 3px; color: var(--el-color-primary); font-size: var(--type-meta); font-weight: 600; }
.experiment-summary-side { display: flex; min-width: 0; flex-direction: column; align-items: stretch; }
.soil-summary-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; margin-top: 20px; padding: 15px 14px; border: 1px solid color-mix(in srgb, var(--experiment-vivid) 40%, var(--el-bg-color)); border-radius: 12px; background: color-mix(in srgb, var(--experiment-vivid) 26%, var(--el-bg-color)); }
.soil-summary { display: flex; align-items: center; justify-content: center; min-width: 0; gap: 4px; font-size: var(--type-card-title); font-variant-numeric: tabular-nums; white-space: nowrap; }
.soil-summary span { line-height: 1; }
.soil-summary strong { color: var(--experiment-vivid); font-size: inherit; font-weight: 650; line-height: 1; }
.experiment-card-footer { margin-top: auto; color: var(--el-text-color-placeholder); font-size: var(--type-meta); text-align: right; }
.experiment-pagination { justify-content: center; margin-top: 18px; }
.dialog-intro { margin: -4px 0 18px; color: var(--el-text-color-secondary); font-size: var(--type-caption); line-height: var(--leading-ui); }
.field-counter { width: 100%; margin: 6px 0 0; color: var(--el-text-color-placeholder); font-size: var(--type-meta); }
.theme-picker { display: flex; flex-wrap: wrap; gap: 8px; }
.theme-option { display: inline-flex; align-items: center; gap: 7px; padding: 7px 10px; border: 1px solid var(--el-border-color); border-radius: 7px; background: var(--el-bg-color); color: var(--el-text-color-regular); font: inherit; cursor: pointer; }
.theme-option > span { width: 14px; height: 14px; border-radius: 50%; background: var(--option-color); }
.theme-option.active { border-color: color-mix(in srgb, var(--option-color) 40%, var(--el-bg-color)); background: color-mix(in srgb, var(--option-color) 26%, var(--el-bg-color)); color: var(--el-text-color-primary); }
@media (max-width: 900px) { .experiment-list { grid-template-columns: 1fr; } }
@media (max-width: 600px) { .page-header { align-items: stretch; flex-direction: column; gap: 16px; } .page-actions, .page-actions :deep(.el-button) { width: 100%; } .page-actions :deep(.el-button) { min-width: 0; margin-left: 0; } .experiment-list-toolbar, .status-filter { width: 100%; } .status-filter :deep(.el-radio-button) { flex: 1 1 0; } .status-filter :deep(.el-radio-button__inner) { width: 100%; } .experiment-dashboard { margin-inline: -16px; padding: 12px 16px; } .experiment-list-card :deep(.el-card__body) { padding: 18px; } .experiment-card-content { min-height: 0; grid-template-columns: 1fr; gap: 14px; } .soil-summary-grid { margin-top: 10px; grid-template-columns: repeat(3, minmax(0, 1fr)); } .experiment-card-footer { margin-top: 12px; } }
</style>
