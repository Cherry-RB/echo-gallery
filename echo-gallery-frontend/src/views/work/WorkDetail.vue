<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ArrowDown, ArrowLeft, Delete, Edit, Link } from '@element-plus/icons-vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import CurrentAssessmentGuide from '../../components/work/CurrentAssessmentGuide.vue'
import AppDialog from '../../components/AppDialog.vue'
import ExpandableText from '../../components/ExpandableText.vue'
import WorkMaterialManager from '../../components/work/WorkMaterialManager.vue'
import WorkProgressUpdates from '../../components/work/WorkProgressUpdates.vue'
import type { UpdateWorkRequest, WorkStatus } from '../../types/work'
import { formatDate } from '../../utils/formatDate'
import { workApi } from '../../utils/api/workApi'

const props = defineProps<{ id: string }>()
const router = useRouter()
const queryClient = useQueryClient()

const workStatusMeta: Record<WorkStatus, { label: string }> = {
  IDEA: { label: '探索中' },
  DRAFT: { label: '已釐清' },
  ACTIVE: { label: '推進中' },
  DONE: { label: '已完成' },
  ARCHIVED: { label: '已封存' },
}

const workStatusOptions: Array<{ value: WorkStatus; label: string }> = [
  { value: 'IDEA', label: '探索中' },
  { value: 'DRAFT', label: '已釐清' },
  { value: 'ACTIVE', label: '推進中' },
  { value: 'DONE', label: '已完成' },
  { value: 'ARCHIVED', label: '已封存' },
]

const phaseStatuses: Array<{ value: WorkStatus; label: string }> = [
  { value: 'IDEA', label: '探索' },
  { value: 'DRAFT', label: '已釐清' },
  { value: 'ACTIVE', label: '推進' },
  { value: 'DONE', label: '完成' },
]

const editDialogVisible = ref(false)
type EditSection = 'all' | 'basic' | 'assessment' | 'background' | 'criteria' | 'link'
const editSection = ref<EditSection>('all')
const editFormRef = ref<FormInstance>()
const editForm = reactive<UpdateWorkRequest>({
  title: '',
  objective: '',
  description: '',
  currentAssessment: '',
  outcomeCriteria: '',
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
    { required: true, message: '請輸入議題名稱', trigger: 'blur' },
    { max: 255, message: '議題名稱不可超過 255 個字', trigger: 'blur' },
  ],
  objective: [
    { max: 50000, message: '議題焦點不可超過 50000 個字', trigger: 'blur' },
  ],
  description: [
    { max: 50000, message: '背景與脈絡不可超過 50000 個字', trigger: 'blur' },
  ],
  currentAssessment: [
    { max: 50000, message: '整體研判不可超過 50000 個字', trigger: 'blur' },
  ],
  outcomeCriteria: [
    { max: 50000, message: '結案或重議條件不可超過 50000 個字', trigger: 'blur' },
  ],
  externalUrl: [
    { max: 2048, message: '外部連結不可超過 2048 個字', trigger: 'blur' },
    { validator: validateOptionalUrl, trigger: 'blur' },
  ],
  status: [
    { required: true, message: '請選擇議題狀態', trigger: 'change' },
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

const currentPhaseIndex = computed(() => {
  if (!work.value) return -1
  return phaseStatuses.findIndex((phase) => phase.value === work.value?.status)
})

const editDialogTitle = computed(() => {
  const titles: Record<EditSection, string> = {
    all: '編輯議題',
    basic: '編輯議題焦點',
    assessment: '編輯整體研判',
    background: '編輯背景與脈絡',
    criteria: '編輯結案／重議條件',
    link: '編輯相關連結',
  }
  return titles[editSection.value]
})

const updateMutation = useMutation({
  mutationFn: (data: UpdateWorkRequest) => workApi.updateWork(props.id, data),
  onSuccess: async (updatedWork) => {
    queryClient.setQueryData(['work', String(props.id)], updatedWork)
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['works'] }),
      queryClient.invalidateQueries({ queryKey: ['sidebar', 'stats'] }),
    ])
    ElMessage.success('議題更新成功')
    editDialogVisible.value = false
  },
})

const statusMutation = useMutation({
  mutationFn: (status: WorkStatus) => {
    if (!work.value) throw new Error('議題尚未載入')
    return workApi.updateWork(props.id, {
      title: work.value.title,
      objective: work.value.objective,
      description: work.value.description,
      currentAssessment: work.value.currentAssessment,
      outcomeCriteria: work.value.outcomeCriteria,
      externalUrl: work.value.externalUrl,
      status,
    })
  },
  onSuccess: async (updatedWork) => {
    queryClient.setQueryData(['work', String(props.id)], updatedWork)
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['works'] }),
      queryClient.invalidateQueries({ queryKey: ['sidebar', 'stats'] }),
    ])
    ElMessage.success(`議題已改為「${workStatusMeta[updatedWork.status].label}」`)
  },
  onError: () => {
    ElMessage.error('更新議題階段失敗，請稍後再試')
  },
})

const deleteMutation = useMutation({
  mutationFn: () => workApi.deleteWork(props.id),
  onSuccess: async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['works'] }),
      queryClient.invalidateQueries({ queryKey: ['sidebar', 'stats'] }),
      queryClient.invalidateQueries({ queryKey: ['recent-work-progress-updates'] }),
    ])
    ElMessage.success('議題已永久刪除')
    router.replace({ name: 'WorkList' })
  },
  onError: () => {
    ElMessage.error('刪除議題失敗，請稍後再試')
  },
})

const changeWorkStatus = (status: WorkStatus) => {
  if (!work.value || status === work.value.status || statusMutation.isPending.value) return
  statusMutation.mutate(status)
}

const goBack = () => {
  if (window.history.state?.back) {
    router.back()
    return
  }
  router.push({ name: 'WorkList' })
}

const confirmDeleteWork = async () => {
  if (!work.value || deleteMutation.isPending.value) return

  try {
    await ElMessageBox.confirm(
      `「${work.value.title}」及其所有議題更新、素材關聯都會永久刪除；原始卡片不會被刪除。`,
      '永久刪除議題',
      {
        confirmButtonText: '永久刪除',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
    deleteMutation.mutate()
  } catch {
    // 使用者取消刪除，不需顯示訊息。
  }
}

const openEditDialog = (section: EditSection = 'all') => {
  if (!work.value) return

  editSection.value = section
  editForm.title = work.value.title
  editForm.objective = work.value.objective ?? ''
  editForm.description = work.value.description ?? ''
  editForm.currentAssessment = work.value.currentAssessment ?? ''
  editForm.outcomeCriteria = work.value.outcomeCriteria ?? ''
  editForm.externalUrl = work.value.externalUrl ?? ''
  editForm.status = work.value.status
  editDialogVisible.value = true
}

const resetEditForm = () => {
  editSection.value = 'all'
  editFormRef.value?.clearValidate()
}

const submitUpdateWork = async () => {
  if (!editFormRef.value) return

  await editFormRef.value.validate((valid) => {
    if (!valid) return

    updateMutation.mutate({
      title: editForm.title.trim(),
      objective: editForm.objective?.trim() || null,
      description: editForm.description?.trim() || null,
      currentAssessment: editForm.currentAssessment?.trim() || null,
      outcomeCriteria: editForm.outcomeCriteria?.trim() || null,
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
        返回議事廳
      </el-button>
      <div v-if="work" class="detail-actions">
        <el-dropdown trigger="click" @command="(status: WorkStatus) => changeWorkStatus(status)">
          <button type="button" class="status-trigger" :disabled="statusMutation.isPending.value">
            <span class="property-status-dot" :class="`property-status-${work.status.toLowerCase()}`"></span>
            <span>{{ workStatusMeta[work.status].label }}</span>
            <el-icon class="status-trigger-arrow"><ArrowDown /></el-icon>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item
                v-for="option in workStatusOptions"
                :key="option.value"
                :command="option.value"
                :disabled="option.value === work.status"
                :divided="option.value === 'ARCHIVED'"
              >
                {{ option.label }}
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <div class="detail-edit-actions">
          <el-button
            type="danger"
            plain
            :icon="Delete"
            :loading="deleteMutation.isPending.value"
            @click="confirmDeleteWork"
          >
            刪除議題
          </el-button>
          <el-button type="primary" plain :icon="Edit" @click="openEditDialog('all')">
            編輯議題
          </el-button>
        </div>
      </div>
    </header>

    <div v-if="isLoading" class="state-surface" aria-label="議題詳情載入中">
      <el-skeleton :rows="8" animated />
    </div>

    <div v-else-if="isError" class="state-surface">
      <el-result
        icon="error"
        title="無法載入議題"
        sub-title="議題可能不存在，或目前無法連線"
      >
        <template #extra>
          <el-button @click="goBack">返回列表</el-button>
          <el-button type="primary" @click="refetch()">重新載入</el-button>
        </template>
      </el-result>
    </div>

    <div v-else-if="work" class="detail-layout">
      <main class="issue-overview-panel" aria-label="議題整體資訊">
        <article>
          <section class="overview-group overview-primary-group" aria-label="議題主軸">
            <div class="detail-heading">
              <div class="title-group">
                <span class="detail-eyebrow">議題</span>
                <h1 class="work-title">{{ work.title }}</h1>
              </div>
            </div>

            <ol class="phase-track" aria-label="議題階段">
              <li
                v-for="(phase, index) in phaseStatuses"
                :key="phase.value"
                :class="{
                  active: index === currentPhaseIndex,
                  passed: currentPhaseIndex >= 0 && index < currentPhaseIndex,
                }"
              >
                <span class="phase-marker" aria-hidden="true"></span>
                <span>{{ phase.label }}</span>
              </li>
            </ol>

            <section class="focus-section" aria-labelledby="focus-title">
              <header class="overview-heading">
                <h2 id="focus-title">議題焦點</h2>
              </header>
              <ExpandableText
                v-if="work.objective"
                class="focus-content"
                :content="work.objective"
                :lines="5"
              />
              <div v-else class="quiet-empty-state">
                <p>這個議題最需要回答、釐清或推進什麼？</p>
                <button type="button" class="inline-edit-button" @click="openEditDialog('basic')">補上焦點</button>
              </div>
            </section>

            <section class="detail-section" aria-labelledby="assessment-title">
              <header class="overview-heading with-action">
                <div>
                  <h2 id="assessment-title">整體研判</h2>
                  <p>綜合一路累積的資訊，留下目前對整件事較穩定的理解。</p>
                </div>
                <button type="button" class="inline-edit-button" @click="openEditDialog('assessment')">編輯</button>
              </header>
              <ExpandableText
                v-if="work.currentAssessment"
                class="assessment-content"
                :content="work.currentAssessment"
                :lines="8"
              />
              <div v-else class="quiet-empty-state">
                <p>還沒有形成整體研判。可以先累積素材與經驗，不必急著下結論。</p>
              </div>
            </section>
          </section>

          <section class="overview-group" aria-label="議題判準與成果">
            <section aria-labelledby="criteria-title">
            <header class="overview-heading with-action">
              <div>
                <h2 id="criteria-title">結案／重議條件</h2>
                <p>什麼情況代表這一輪已有結論，或需要重新議定？</p>
              </div>
              <button type="button" class="inline-edit-button" @click="openEditDialog('criteria')">編輯</button>
            </header>
            <ExpandableText v-if="work.outcomeCriteria" :content="work.outcomeCriteria" :lines="5" />
            <p v-else class="context-empty">尚未設定；需要形成判準時再補充。</p>
          </section>

            <section class="detail-section compact-section" aria-labelledby="reference-title">
              <header class="overview-heading with-action">
                <h2 id="reference-title">成果連結</h2>
                <button type="button" class="inline-edit-button" @click="openEditDialog('link')">編輯</button>
              </header>
              <a
                v-if="work.externalUrl"
                :href="work.externalUrl"
                target="_blank"
                rel="noopener noreferrer"
                class="external-link"
              >
                <el-icon><Link /></el-icon>
                <span>開啟連結</span>
              </a>
              <p v-else class="context-empty">尚未加入成果連結。</p>
            </section>
          </section>

          <section class="overview-group" aria-label="背景與議題時間">
            <section aria-labelledby="background-title">
              <header class="overview-heading with-action">
                <div>
                  <h2 id="background-title">背景與脈絡</h2>
                  <p>需要回顧這件事為何出現時，再展開閱讀。</p>
                </div>
                <button type="button" class="inline-edit-button" @click="openEditDialog('background')">編輯</button>
              </header>
              <ExpandableText
                v-if="work.description"
                class="background-content"
                :content="work.description"
                :lines="5"
              />
              <p v-else class="context-empty">尚未補充背景；需要時再填即可。</p>
            </section>

            <dl class="time-metadata">
              <div class="property-row">
                <dt>建立時間</dt>
                <dd>{{ formatDate(work.createdAt) }}</dd>
              </div>
              <div class="property-row">
                <dt>最後更新</dt>
                <dd>{{ formatDate(work.updatedAt) }}</dd>
              </div>
              <div v-if="work.completedAt">
                <dt>完成時間</dt>
                <dd>{{ formatDate(work.completedAt) }}</dd>
              </div>
            </dl>
          </section>

        </article>
      </main>

      <aside class="issue-updates-panel" aria-label="議題更新資訊">
        <section class="updates-side-card" aria-label="最近推進">
          <WorkProgressUpdates :work-id="props.id" />
        </section>
        <section class="materials-side-card" aria-label="參考素材">
          <WorkMaterialManager :work-id="props.id" />
        </section>
      </aside>
    </div>

    <AppDialog
      v-model="editDialogVisible"
      :title="editDialogTitle"
      width="min(680px, calc(100vw - 32px))"
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
        <el-form-item v-if="editSection === 'all' || editSection === 'basic'" label="議題名稱" prop="title">
          <el-input v-model="editForm.title" maxlength="255" />
        </el-form-item>

        <el-form-item v-if="editSection === 'all' || editSection === 'basic'" label="議題焦點" prop="objective">
          <el-input
            v-model="editForm.objective"
            type="textarea"
            :rows="2"
            maxlength="50000"
            placeholder="這個議題最需要回答、釐清或推進什麼？（選填）"
          />
        </el-form-item>

        <el-form-item v-if="editSection === 'all' || editSection === 'background'" label="背景與脈絡" prop="description">
          <el-input
            v-model="editForm.description"
            type="textarea"
            :rows="3"
            maxlength="50000"
            placeholder="哪些經歷、條件或變化，使這件事值得處理？（選填）"
          />
        </el-form-item>

        <el-form-item v-if="editSection === 'all' || editSection === 'assessment'" label="整體研判" prop="currentAssessment">
          <CurrentAssessmentGuide v-model="editForm.currentAssessment" />
          <el-input
            v-model="editForm.currentAssessment"
            type="textarea"
            :rows="5"
            maxlength="50000"
            placeholder="綜合長期累積的資訊，你如何理解整個議題？（選填）"
          />
        </el-form-item>

        <el-form-item v-if="editSection === 'all' || editSection === 'criteria'" label="結案／重議條件" prop="outcomeCriteria">
          <el-input
            v-model="editForm.outcomeCriteria"
            type="textarea"
            :rows="2"
            maxlength="50000"
            placeholder="何時算有結論？出現什麼變化時需要重新議定？（選填）"
          />
        </el-form-item>

        <el-form-item v-if="editSection === 'all' || editSection === 'link'" label="相關連結" prop="externalUrl">
          <el-input
            v-model="editForm.externalUrl"
            maxlength="2048"
            placeholder="外部工作區、文件或成果連結（選填）"
          />
        </el-form-item>

        <el-form-item v-if="editSection === 'all'" label="議題階段" prop="status">
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
    </AppDialog>
  </section>
</template>

<style scoped>
.work-detail-page {
  width: 100%;
  height: calc(100dvh - 40px);
  overflow: hidden;
  background: var(--el-bg-color-page);
}

.detail-navigation {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 48px;
  padding: 8px 16px;
  box-sizing: border-box;
  border-bottom: 1px solid var(--el-border-color-light);
  background: var(--el-bg-color);
}

.detail-actions {
  display: flex;
  align-items: center;
  gap: 20px;
}

.detail-actions :deep(.el-button) {
  min-width: 120px;
}

.detail-edit-actions {
  display: flex;
  gap: 8px;
}

.status-select {
  width: 100%;
}

.state-surface {
  padding: 32px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 10px;
  background: var(--el-bg-color);
  box-shadow: var(--el-box-shadow-lighter);
}

.detail-layout {
  display: grid;
  height: calc(100% - 48px);
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0;
  overflow: hidden;
}

.issue-overview-panel,
.issue-updates-panel {
  min-width: 0;
}

.issue-overview-panel {
  height: 100%;
  padding: 16px;
  box-sizing: border-box;
  background: var(--el-bg-color);
  overflow-y: auto;
}

.issue-updates-panel {
  height: 100%;
  padding: 14px 16px;
  box-sizing: border-box;
  border-left: 1px solid var(--el-border-color-lighter);
  background: var(--el-bg-color-page);
  overflow-y: auto;
}

.detail-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}

.title-group {
  min-width: 0;
}

.detail-eyebrow {
  display: block;
  margin-bottom: 6px;
  color: var(--el-text-color-placeholder);
  font-size: var(--type-meta);
  letter-spacing: 0.12em;
}

.work-title {
  margin: 0;
  min-width: 0;
  overflow-wrap: break-word;
  word-break: break-word;
  font-size: var(--type-detail-title);
  line-height: var(--leading-title);
}

.phase-track {
  display: grid;
  margin: 24px 0 0;
  padding: 0;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  list-style: none;
}

.phase-track li {
  position: relative;
  display: flex;
  align-items: center;
  min-width: 0;
  flex-direction: column;
  gap: 7px;
  color: var(--el-text-color-placeholder);
  font-size: var(--type-meta);
  text-align: center;
}

.phase-track li:not(:last-child)::after {
  position: absolute;
  top: 5px;
  left: calc(50% + 7px);
  width: calc(100% - 14px);
  height: 2px;
  background: var(--el-border-color-lighter);
  content: '';
}

.phase-marker {
  position: relative;
  z-index: 1;
  width: 12px;
  height: 12px;
  box-sizing: border-box;
  border: 2px solid var(--el-border-color);
  border-radius: 50%;
  background: var(--el-bg-color);
}

.phase-track li.passed,
.phase-track li.active {
  color: var(--el-text-color-regular);
}

.phase-track li.passed::after {
  background: var(--el-color-primary-light-5);
}

.phase-track li.passed .phase-marker {
  border-color: var(--el-color-primary-light-3);
  background: var(--el-color-primary-light-3);
}

.phase-track li.active .phase-marker {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary);
  box-shadow: 0 0 0 3px var(--el-color-primary-light-9);
}

.overview-group + .overview-group {
  margin-top: 26px;
  padding-top: 26px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.overview-group {
  padding: 0;
}

.focus-section {
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.detail-section {
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.compact-section {
  padding-bottom: 2px;
}

.updates-side-card,
.materials-side-card {
  padding: 20px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 10px;
  background: var(--el-bg-color);
  box-shadow: var(--el-box-shadow-lighter);
}

.materials-side-card {
  margin-top: 16px;
}

.materials-side-card :deep(.material-surface) {
  margin-top: 0;
  padding: 0;
  border: 0;
  border-radius: 0;
  box-shadow: none;
}

.materials-side-card :deep(.material-columns) {
  grid-template-columns: minmax(0, 1fr);
}

.overview-heading {
  margin-bottom: 10px;
}

.overview-heading.with-action {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
}

.overview-heading h2 {
  margin: 0;
  font-size: var(--type-section-title);
  line-height: var(--leading-section);
}

.overview-heading p {
  margin: 4px 0 0;
  color: var(--el-text-color-secondary);
  font-size: var(--type-caption);
  line-height: var(--leading-ui);
}

.focus-content :deep(.expandable-content) {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: var(--type-prominent);
  font-weight: 500;
  line-height: 1.7;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.assessment-content {
  margin-top: 16px;
}

.assessment-content :deep(.expandable-content),
.background-content :deep(.expandable-content) {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: var(--type-body);
  line-height: var(--leading-body);
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.quiet-empty-state {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 8px 12px;
  margin-top: 10px;
  color: var(--el-text-color-placeholder);
  font-size: var(--type-ui);
  line-height: var(--leading-ui);
}

.quiet-empty-state p {
  margin: 0;
}

.inline-edit-button {
  flex: 0 0 auto;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--el-color-primary);
  font: inherit;
  font-size: var(--type-caption);
  cursor: pointer;
}

.inline-edit-button:hover,
.inline-edit-button:focus-visible {
  color: var(--el-color-primary-light-3);
  text-decoration: underline;
}

.background-content {
  margin-top: 20px;
}

.context-empty {
  margin: 16px 0 0;
  color: var(--el-text-color-placeholder);
  font-size: var(--type-caption);
}

.time-metadata dt {
  color: var(--el-text-color-placeholder);
  font-size: var(--type-meta);
}

.status-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 120px;
  padding: 7px 10px;
  border: 1px solid var(--el-border-color);
  border-radius: 7px;
  background: var(--el-fill-color-blank);
  color: var(--el-text-color-regular);
  font: inherit;
  white-space: nowrap;
  cursor: pointer;
}

.status-trigger:hover,
.status-trigger:focus-visible {
  border-color: var(--el-color-primary-light-5);
  color: var(--el-color-primary);
}

.status-trigger:disabled {
  cursor: wait;
  opacity: 0.65;
}

.status-trigger-arrow {
  margin-left: 2px;
  color: var(--el-text-color-placeholder);
}

.property-status-dot {
  width: 8px;
  height: 8px;
  flex: 0 0 auto;
  border-radius: 50%;
  background: var(--el-text-color-placeholder);
}

.property-status-idea {
  background: var(--el-color-info);
}

.property-status-draft {
  background: var(--el-color-warning);
}

.property-status-active {
  background: var(--el-color-primary);
}

.property-status-done {
  background: var(--el-color-success);
}

.property-status-archived {
  background: var(--el-text-color-placeholder);
}

.external-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--el-color-primary);
  font-size: var(--type-ui);
  text-decoration: none;
}

.external-link:hover {
  color: var(--el-color-primary-light-3);
}

.time-metadata {
  display: flex;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 14px 28px;
  margin: 20px 0 0;
  padding-top: 18px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.time-metadata dd {
  margin: 4px 0 0;
  color: var(--el-text-color-secondary);
  font-size: var(--type-caption);
}

@media (max-width: 1200px) and (min-width: 901px) {
  .work-detail-page {
    height: calc(100dvh - 88px);
  }
}

@media (max-width: 900px) {
  .work-detail-page {
    width: 100%;
    height: auto;
    margin: 0;
    min-height: calc(100dvh - 56px);
    overflow: visible;
  }

  .detail-navigation {
    min-height: auto;
  }

  .detail-layout {
    height: auto;
    grid-template-columns: 1fr;
    overflow: visible;
  }

  .issue-overview-panel {
    height: auto;
    padding: 18px;
    overflow: visible;
  }

  .issue-updates-panel {
    height: auto;
    margin-top: 38px;
    padding: 18px;
    border-top: 1px solid var(--el-border-color-lighter);
    border-left: 0;
    overflow: visible;
  }
}

@media (max-width: 600px) {
  .state-surface {
    padding: 20px 16px;
  }

  .detail-navigation {
    align-items: center;
    flex-wrap: wrap;
    padding: 14px 16px;
  }

  .detail-navigation > :first-child {
    flex-basis: 100%;
    justify-content: flex-start;
  }

  .detail-actions {
    display: grid;
    width: 100%;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
    margin: 0;
  }

  .detail-actions > :first-child {
    grid-column: 1 / -1;
    width: 100%;
  }

  .detail-actions,
  .detail-edit-actions {
    min-width: 0;
  }

  .detail-edit-actions {
    display: contents;
  }

  .status-trigger {
    width: 100%;
    min-width: 0;
    padding-inline: 6px;
  }

  .detail-actions :deep(.el-button) {
    width: 100%;
    min-width: 0;
    margin-left: 0;
    padding-inline: 6px;
    font-size: 12px;
  }

  .updates-side-card,
  .materials-side-card {
    padding: 20px 18px;
  }

  .detail-heading {
    align-items: flex-start;
    flex-direction: column;
    gap: 12px;
  }

  .overview-heading.with-action {
    gap: 12px;
  }

  .phase-track {
    margin-top: 24px;
  }
}

@media (max-width: 420px) {
  .detail-navigation {
    flex-wrap: wrap;
  }

  .detail-navigation > :first-child {
    flex-basis: 100%;
    justify-content: flex-start;
  }

  .detail-actions {
    margin: 0;
  }
}
</style>
