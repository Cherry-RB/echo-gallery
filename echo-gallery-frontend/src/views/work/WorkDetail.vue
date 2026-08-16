<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ArrowLeft, Edit, Link } from '@element-plus/icons-vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import WorkMaterialManager from '../../components/work/WorkMaterialManager.vue'
import type { UpdateWorkRequest, WorkStatus } from '../../types/work'
import { formatDate } from '../../utils/formatDate'
import { workApi } from '../../utils/api/workApi'

const props = defineProps<{ id: string }>()
const router = useRouter()
const queryClient = useQueryClient()

type WorkStatusTagType = 'primary' | 'success' | 'warning' | 'info'

const workStatusMeta: Record<WorkStatus, { label: string; type: WorkStatusTagType }> = {
  IDEA: { label: '構想', type: 'info' },
  DRAFT: { label: '草稿', type: 'warning' },
  ACTIVE: { label: '進行中', type: 'primary' },
  DONE: { label: '已完成', type: 'success' },
  ARCHIVED: { label: '已封存', type: 'info' },
}

const workStatusOptions: Array<{ value: WorkStatus; label: string }> = [
  { value: 'IDEA', label: '構想' },
  { value: 'DRAFT', label: '草稿' },
  { value: 'ACTIVE', label: '進行中' },
  { value: 'DONE', label: '已完成' },
  { value: 'ARCHIVED', label: '已封存' },
]

const editDialogVisible = ref(false)
const editFormRef = ref<FormInstance>()
const editForm = reactive<UpdateWorkRequest>({
  title: '',
  description: '',
  externalUrl: '',
  status: 'IDEA',
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

const editFormRules: FormRules<UpdateWorkRequest> = {
  title: [
    { required: true, message: '請輸入作品名稱', trigger: 'blur' },
    { max: 255, message: '作品名稱不可超過 255 個字', trigger: 'blur' },
  ],
  description: [
    { max: 5000, message: '作品說明不可超過 5000 個字', trigger: 'blur' },
  ],
  externalUrl: [
    { max: 2048, message: '外部連結不可超過 2048 個字', trigger: 'blur' },
    { validator: validateOptionalUrl, trigger: 'blur' },
  ],
  status: [
    { required: true, message: '請選擇作品狀態', trigger: 'change' },
  ],
}

const {
  data: work,
  isLoading,
  isError,
  refetch,
} = useQuery({
  queryKey: computed(() => ['work', String(props.id)]),
  queryFn: () => workApi.getWork(props.id),
})

const updateMutation = useMutation({
  mutationFn: (data: UpdateWorkRequest) => workApi.updateWork(props.id, data),
  onSuccess: async (updatedWork) => {
    queryClient.setQueryData(['work', String(props.id)], updatedWork)
    await queryClient.invalidateQueries({ queryKey: ['works'] })
    ElMessage.success('作品更新成功')
    editDialogVisible.value = false
  },
})

const goBack = () => {
  if (window.history.state?.back) {
    router.back()
    return
  }
  router.push({ name: 'WorkList' })
}

const openEditDialog = () => {
  if (!work.value) return

  editForm.title = work.value.title
  editForm.description = work.value.description ?? ''
  editForm.externalUrl = work.value.externalUrl ?? ''
  editForm.status = work.value.status
  editDialogVisible.value = true
}

const resetEditForm = () => {
  editFormRef.value?.clearValidate()
}

const submitUpdateWork = async () => {
  if (!editFormRef.value) return

  await editFormRef.value.validate((valid) => {
    if (!valid) return

    updateMutation.mutate({
      title: editForm.title.trim(),
      description: editForm.description?.trim() || null,
      externalUrl: editForm.externalUrl?.trim() || null,
      status: editForm.status,
    })
  })
}
</script>

<template>
  <section class="work-detail-page">
    <header class="detail-navigation">
      <el-button :icon="ArrowLeft" text @click="goBack">
        返回作品列表
      </el-button>
      <el-button v-if="work" type="primary" plain :icon="Edit" @click="openEditDialog">
        編輯作品
      </el-button>
    </header>

    <div class="work-detail-surface">
      <div v-if="isLoading" aria-label="作品詳情載入中">
        <el-skeleton :rows="8" animated />
      </div>

      <el-result
        v-else-if="isError"
        icon="error"
        title="無法載入作品"
        sub-title="作品可能不存在，或目前無法連線"
      >
        <template #extra>
          <el-button @click="goBack">返回列表</el-button>
          <el-button type="primary" @click="refetch()">重新載入</el-button>
        </template>
      </el-result>

      <article v-else-if="work" class="work-detail-content">
        <div class="detail-heading">
          <h1 class="work-title">{{ work.title }}</h1>
          <el-tag :type="workStatusMeta[work.status].type" effect="plain">
            {{ workStatusMeta[work.status].label }}
          </el-tag>
        </div>

        <p v-if="work.description" class="work-description">
          {{ work.description }}
        </p>
        <p v-else class="empty-description">尚未填寫作品說明</p>

        <a
          v-if="work.externalUrl"
          :href="work.externalUrl"
          target="_blank"
          rel="noopener noreferrer"
          class="external-link"
        >
          <el-icon><Link /></el-icon>
          <span>開啟外部作品</span>
        </a>

        <dl class="time-metadata">
          <div>
            <dt>建立時間</dt>
            <dd>{{ formatDate(work.createdAt) }}</dd>
          </div>
          <div>
            <dt>最後更新</dt>
            <dd>{{ formatDate(work.updatedAt) }}</dd>
          </div>
          <div v-if="work.completedAt">
            <dt>完成時間</dt>
            <dd>{{ formatDate(work.completedAt) }}</dd>
          </div>
        </dl>
      </article>
    </div>

    <WorkMaterialManager v-if="work" :work-id="props.id" />

    <el-dialog
      v-model="editDialogVisible"
      title="編輯作品"
      width="min(520px, calc(100vw - 32px))"
      destroy-on-close
      @closed="resetEditForm"
    >
      <el-form
        ref="editFormRef"
        :model="editForm"
        :rules="editFormRules"
        label-position="top"
        @submit.prevent="submitUpdateWork"
      >
        <el-form-item label="作品名稱" prop="title">
          <el-input v-model="editForm.title" maxlength="255" show-word-limit />
        </el-form-item>

        <el-form-item label="作品說明" prop="description">
          <el-input
            v-model="editForm.description"
            type="textarea"
            :rows="4"
            maxlength="5000"
            show-word-limit
            placeholder="這件作品想完成什麼？（選填）"
          />
        </el-form-item>

        <el-form-item label="外部連結" prop="externalUrl">
          <el-input
            v-model="editForm.externalUrl"
            maxlength="2048"
            placeholder="https://...（選填）"
          />
        </el-form-item>

        <el-form-item label="作品狀態" prop="status">
          <el-select v-model="editForm.status" class="status-select">
            <el-option
              v-for="option in workStatusOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button
          :disabled="updateMutation.isPending.value"
          @click="editDialogVisible = false"
        >
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="updateMutation.isPending.value"
          @click="submitUpdateWork"
        >
          儲存變更
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.work-detail-page {
  width: 100%;
  max-width: 1040px;
  margin: 0 auto;
}

.detail-navigation {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.status-select {
  width: 100%;
}

.work-detail-surface {
  min-height: 360px;
  padding: 28px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-bg-color);
  box-shadow: var(--el-box-shadow-lighter);
}

.detail-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}

.work-title {
  min-width: 0;
  overflow-wrap: break-word;
  word-break: break-word;
  font-size: 30px;
  line-height: 1.4;
}

.work-description {
  margin-top: 24px;
  color: var(--el-text-color-regular);
  font-size: 15px;
  line-height: 1.8;
  overflow-wrap: break-word;
  white-space: pre-wrap;
}

.empty-description {
  margin-top: 24px;
  color: var(--el-text-color-placeholder);
  font-size: 14px;
}

.external-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 24px;
  color: var(--el-color-primary);
  font-size: 14px;
  text-decoration: none;
}

.external-link:hover {
  color: var(--el-color-primary-light-3);
}

.time-metadata {
  display: flex;
  flex-wrap: wrap;
  gap: 16px 32px;
  margin: 32px 0 0;
  padding-top: 20px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.time-metadata div {
  min-width: 140px;
}

.time-metadata dt {
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}

.time-metadata dd {
  margin: 4px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

@media (max-width: 600px) {
  .work-detail-surface {
    padding: 20px 16px;
  }

  .detail-heading {
    align-items: flex-start;
    flex-direction: column;
    gap: 12px;
  }

  .work-title {
    font-size: 24px;
  }

  .time-metadata {
    flex-direction: column;
    gap: 14px;
  }
}
</style>
