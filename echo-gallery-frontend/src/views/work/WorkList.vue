<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { Link, Plus } from '@element-plus/icons-vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import type { CreateWorkRequest, WorkStatus } from '../../types/work'
import { formatDate } from '../../utils/formatDate'
import { workApi } from '../../utils/api/workApi'

type WorkStatusTagType = 'primary' | 'success' | 'warning' | 'info'

const workStatusMeta: Record<WorkStatus, { label: string; type: WorkStatusTagType }> = {
  IDEA: { label: '構想', type: 'info' },
  DRAFT: { label: '規劃中', type: 'warning' },
  ACTIVE: { label: '進行中', type: 'primary' },
  DONE: { label: '已完成', type: 'success' },
  ARCHIVED: { label: '已封存', type: 'info' },
}

const projectDesignPlaceholder = `培育目標：想培養什麼能力或達成什麼目標？
成果判準：如何知道這項計畫產生效果？
可能產生的 Works：預期會形成哪些具體成果？`

const workStatusOptions = (Object.entries(workStatusMeta) as Array<
  [WorkStatus, { label: string; type: WorkStatusTagType }]
>).map(([value, meta]) => ({ value, label: meta.label }))

const queryClient = useQueryClient()
const router = useRouter()
const createDialogVisible = ref(false)
const selectedStatuses = ref<WorkStatus[]>([])
const createFormRef = ref<FormInstance>()
const createForm = reactive<CreateWorkRequest>({
  title: '',
  description: '',
  externalUrl: '',
})

const validateOptionalUrl = (
  _rule: unknown,
  value: string,
  callback: (error?: Error) => void,
) => {
  if (!value?.trim()) {
    callback()
    return
  }

  try {
    const url = new URL(value)
    if ((url.protocol === 'http:' || url.protocol === 'https:') && url.hostname) {
      callback()
      return
    }
  } catch {
    // 交由下方統一回傳驗證錯誤。
  }

  callback(new Error('請輸入有效的 HTTP 或 HTTPS 連結'))
}

const createFormRules: FormRules<CreateWorkRequest> = {
  title: [
    { required: true, message: '請輸入計畫名稱', trigger: 'blur' },
    { max: 255, message: '計畫名稱不可超過 255 個字', trigger: 'blur' },
  ],
  description: [
    { max: 5000, message: '計畫設計不可超過 5000 個字', trigger: 'blur' },
  ],
  externalUrl: [
    { max: 2048, message: '外部連結不可超過 2048 個字', trigger: 'blur' },
    { validator: validateOptionalUrl, trigger: 'blur' },
  ],
}

const {
  data: works,
  isLoading,
  isError,
  refetch,
} = useQuery({
  queryKey: ['works'],
  queryFn: workApi.getWorks,
  placeholderData: [],
  staleTime: 1000 * 60,
})

const allWorks = computed(() => works.value ?? [])
const workList = computed(() => {
  if (selectedStatuses.value.length === 0) return allWorks.value
  const selectedStatusSet = new Set(selectedStatuses.value)
  return allWorks.value.filter((work) => selectedStatusSet.has(work.status))
})

const createMutation = useMutation({
  mutationFn: workApi.createWork,
  onSuccess: async () => {
    await queryClient.invalidateQueries({ queryKey: ['works'] })
    await queryClient.invalidateQueries({ queryKey: ['sidebar', 'stats'] })
    ElMessage.success('培育計畫建立成功')
    createDialogVisible.value = false
  },
})

const openCreateDialog = () => {
  createDialogVisible.value = true
}

const openWorkDetail = (workId: number) => {
  router.push({ name: 'WorkDetail', params: { id: workId } })
}

const resetCreateForm = () => {
  createForm.title = ''
  createForm.description = ''
  createForm.externalUrl = ''
  createFormRef.value?.clearValidate()
}

const submitCreateWork = async () => {
  if (!createFormRef.value) return

  await createFormRef.value.validate((valid) => {
    if (!valid) return

    createMutation.mutate({
      title: createForm.title.trim(),
      description: createForm.description?.trim() || null,
      externalUrl: createForm.externalUrl?.trim() || null,
    })
  })
}
</script>

<template>
  <section class="work-list-page">
    <header class="page-header">
      <div>
        <h1 class="page-title">培育計畫</h1>
        <p class="page-description">組織素材與練習，逐步形成可檢視的具體成果</p>
      </div>
      <div class="page-actions">
        <el-select
          v-model="selectedStatuses"
          class="status-filter"
          multiple
          clearable
          collapse-tags
          collapse-tags-tooltip
          placeholder="全部狀態"
          aria-label="依培育計畫狀態篩選"
        >
          <el-option
            v-for="option in workStatusOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-button type="primary" :icon="Plus" @click="openCreateDialog">
          新增培育計畫
        </el-button>
      </div>
    </header>

    <div class="work-content-surface">
      <div v-if="isLoading" class="loading-grid" aria-label="培育計畫載入中">
        <el-card v-for="index in 3" :key="index" shadow="never">
          <el-skeleton :rows="3" animated />
        </el-card>
      </div>

      <el-result
        v-else-if="isError"
        icon="error"
        title="無法載入培育計畫"
        sub-title="請確認網路連線後再試一次"
      >
        <template #extra>
          <el-button type="primary" @click="refetch()">重新載入</el-button>
        </template>
      </el-result>

      <el-empty v-else-if="allWorks.length === 0" description="還沒有培育計畫，先建立一個想投入的方向吧">
        <el-button type="primary" :icon="Plus" @click="openCreateDialog">
          建立第一個培育計畫
        </el-button>
      </el-empty>

      <el-empty
        v-else-if="workList.length === 0"
        description="沒有符合目前狀態篩選的培育計畫"
      >
        <el-button @click="selectedStatuses = []">清除篩選</el-button>
      </el-empty>

      <div v-else class="work-grid">
        <el-card
          v-for="work in workList"
          :key="work.id"
          shadow="never"
          class="work-card"
          role="link"
          tabindex="0"
          :aria-label="`查看培育計畫：${work.title}`"
          @click="openWorkDetail(work.id)"
          @keydown.enter="openWorkDetail(work.id)"
          @keydown.space.prevent="openWorkDetail(work.id)"
        >
          <div class="work-card-header">
            <h2 class="work-title" :title="work.title">{{ work.title }}</h2>
          </div>

          <p v-if="work.description" class="work-description">
            {{ work.description }}
          </p>

          <div class="work-meta-row">
            <el-tag :type="workStatusMeta[work.status].type" effect="plain" size="small">
              {{ workStatusMeta[work.status].label }}
            </el-tag>
            <div class="material-summary" aria-label="計畫素材統計">
              <span class="material-info">
                素材池 <strong>{{ work.candidateCount }}</strong>
              </span>
              <span class="material-divider" aria-hidden="true">·</span>
              <span class="material-info used-material-info">
                已運用 <strong>{{ work.usedCount }}</strong>
              </span>
            </div>
          </div>

          <footer class="work-card-footer">
            <a
              v-if="work.externalUrl"
              :href="work.externalUrl"
              target="_blank"
              rel="noopener noreferrer"
              class="external-link"
              @click.stop
              @keydown.stop
            >
              <el-icon><Link /></el-icon>
              <span>開啟計畫連結</span>
            </a>
            <span class="updated-at">最後更新：{{ formatDate(work.updatedAt) }}</span>
          </footer>
        </el-card>
      </div>
    </div>

    <el-dialog
      v-model="createDialogVisible"
      title="新增培育計畫"
      width="min(520px, calc(100vw - 32px))"
      destroy-on-close
      @closed="resetCreateForm"
    >
      <el-form
        ref="createFormRef"
        :model="createForm"
        :rules="createFormRules"
        label-position="top"
        @submit.prevent="submitCreateWork"
      >
        <el-form-item label="計畫名稱" prop="title">
          <el-input
            v-model="createForm.title"
            maxlength="255"
            show-word-limit
            placeholder="例如：閱讀心得寫作"
          />
        </el-form-item>

        <el-form-item label="計畫設計" prop="description">
          <el-input
            v-model="createForm.description"
            type="textarea"
            :rows="6"
            maxlength="5000"
            show-word-limit
            :placeholder="projectDesignPlaceholder"
          />
        </el-form-item>

        <el-form-item label="相關連結" prop="externalUrl">
          <el-input
            v-model="createForm.externalUrl"
            maxlength="2048"
            placeholder="https://...（選填）"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button
          :disabled="createMutation.isPending.value"
          @click="createDialogVisible = false"
        >
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="createMutation.isPending.value"
          @click="submitCreateWork"
        >
          建立培育計畫
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.work-list-page {
  width: 100%;
  max-width: 960px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 24px;
}

.page-title {
  font-size: 28px;
}

.page-description {
  margin-top: 8px;
  color: var(--el-text-color-secondary);
  font-size: 14px;
}

.page-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.status-filter {
  width: 240px;
}

.work-content-surface {
  padding: 20px;
  border-radius: 8px;
  background: var(--el-bg-color-page);
}

.loading-grid,
.work-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  align-items: start;
  gap: 20px;
}

.work-card {
  height: auto;
  border-color: var(--el-border-color-light);
  box-shadow: var(--el-box-shadow-lighter);
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.work-card:hover,
.work-card:focus-visible {
  transform: translateY(-3px);
  box-shadow: var(--el-box-shadow-light);
}

.work-card:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}

.work-card:active {
  transform: translateY(-1px);
}

.work-card-header {
  min-width: 0;
}

.work-title {
  display: -webkit-box;
  min-width: 0;
  overflow: hidden;
  overflow-wrap: break-word;
  word-break: break-word;
  font-size: 18px;
  line-height: 1.5;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.work-description {
  display: -webkit-box;
  margin-top: 12px;
  overflow: hidden;
  color: var(--el-text-color-regular);
  font-size: 14px;
  line-height: 1.6;
  overflow-wrap: break-word;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.work-meta-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 18px;
}

.material-summary {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.material-info {
  display: inline-flex;
  align-items: baseline;
  gap: 4px;
}

.material-info strong {
  color: var(--el-text-color-primary);
  font-size: 14px;
}

.material-divider {
  color: var(--el-border-color-darker);
}

.used-material-info,
.used-material-info strong {
  color: var(--el-color-primary);
}

.work-card-footer {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color-lighter);
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}

.external-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--el-color-primary);
  text-decoration: none;
}

.external-link:hover {
  color: var(--el-color-primary-light-3);
}

.updated-at {
  margin-left: auto;
  text-align: right;
}

@media (max-width: 600px) {
  .page-header {
    align-items: stretch;
    flex-direction: column;
    gap: 16px;
  }

  .page-actions {
    width: 100%;
    align-items: stretch;
    flex-direction: column;
  }

  .status-filter,
  .page-actions .el-button {
    width: 100%;
  }

  .loading-grid,
  .work-grid {
    grid-template-columns: 1fr;
  }

  .work-content-surface {
    padding: 12px;
  }
}
</style>
