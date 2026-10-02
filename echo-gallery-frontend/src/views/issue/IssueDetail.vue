<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ArrowDown, ArrowLeft, Delete, Edit, Link } from '@element-plus/icons-vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import AppDialog from '../../components/AppDialog.vue'
import ExpandableText from '../../components/ExpandableText.vue'
import CurrentAssessmentGuide from '../../components/issue/CurrentAssessmentGuide.vue'
import IssueMaterialManager from '../../components/issue/IssueMaterialManager.vue'
import IssueUpdates from '../../components/issue/IssueUpdates.vue'
import type { IssueStatus, UpdateIssueRequest } from '../../types/issue'
import { issueApi } from '../../utils/api/issueApi'
import { formatDate } from '../../utils/formatDate'
import { toIssueUpdateRequest } from '../../utils/issueRequest'
import { issueStatusMeta, issueStatusOptions } from '../../utils/issueStatus'

const props = defineProps<{ id: string }>()
const router = useRouter()
const queryClient = useQueryClient()

type EditSection =
  | 'basic'
  | 'systemSnapshot'
  | 'currentDecision'
  | 'supplementalAssessment'
  | 'criteria'
  | 'executionLink'
  | 'background'

const editDialogVisible = ref(false)
const editSection = ref<EditSection>('basic')
const editFormRef = ref<FormInstance>()
const editForm = reactive<UpdateIssueRequest>({
  title: '',
  objective: '',
  description: '',
  currentAssessment: '',
  keyStates: '',
  dominantLoops: '',
  primaryConstraint: '',
  leveragePoint: '',
  watchSignals: '',
  nonInterventionNote: '',
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

const longTextRule = (label: string) => ([
  { max: 50000, message: `${label}不可超過 50000 個字`, trigger: 'blur' },
])

const editFormRules: FormRules<UpdateIssueRequest> = {
  title: [
    { required: true, message: '請輸入議題名稱', trigger: 'blur' },
    { max: 255, message: '議題名稱不可超過 255 個字', trigger: 'blur' },
  ],
  objective: longTextRule('本輪系統問題'),
  description: longTextRule('背景與脈絡'),
  currentAssessment: longTextRule('補充研判'),
  keyStates: longTextRule('關鍵狀態'),
  dominantLoops: longTextRule('主導迴路'),
  primaryConstraint: longTextRule('主要瓶頸'),
  leveragePoint: longTextRule('當前槓桿點'),
  watchSignals: longTextRule('待觀察訊號'),
  nonInterventionNote: longTextRule('暫不介入或延遲提醒'),
  outcomeCriteria: longTextRule('收斂或重議條件'),
  externalUrl: [
    { max: 2048, message: '外部連結不可超過 2048 個字', trigger: 'blur' },
    { validator: validateOptionalUrl, trigger: 'blur' },
  ],
  status: [{ required: true, message: '請選擇目前模式', trigger: 'change' }],
}

const {
  data: issue,
  isLoading,
  isError,
  refetch,
} = useQuery({
  queryKey: computed(() => ['issue', String(props.id)]),
  queryFn: () => issueApi.getIssue(props.id),
})

const editDialogTitle = computed(() => ({
  basic: '編輯議題與系統問題',
  systemSnapshot: '編輯系統快照',
  currentDecision: '編輯當前決策',
  supplementalAssessment: '編輯補充研判',
  criteria: '編輯收斂／重議條件',
  executionLink: '編輯執行／成果入口',
  background: '編輯背景與脈絡',
})[editSection.value])

const updateMutation = useMutation({
  mutationFn: (data: UpdateIssueRequest) => issueApi.updateIssue(props.id, data),
  onSuccess: async (updatedIssue) => {
    queryClient.setQueryData(['issue', String(props.id)], updatedIssue)
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['issues'] }),
      queryClient.invalidateQueries({ queryKey: ['sidebar', 'stats'] }),
    ])
    ElMessage.success('議題更新成功')
    editDialogVisible.value = false
  },
  onError: () => ElMessage.error('更新議題失敗，請稍後再試'),
})

const statusMutation = useMutation({
  mutationFn: (status: IssueStatus) => {
    if (!issue.value) throw new Error('議題尚未載入')
    return issueApi.updateIssue(props.id, toIssueUpdateRequest(issue.value, status))
  },
  onSuccess: async (updatedIssue) => {
    queryClient.setQueryData(['issue', String(props.id)], updatedIssue)
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['issues'] }),
      queryClient.invalidateQueries({ queryKey: ['sidebar', 'stats'] }),
    ])
    ElMessage.success(`目前模式已改為「${issueStatusMeta[updatedIssue.status].label}」`)
  },
  onError: () => ElMessage.error('更新目前模式失敗，請稍後再試'),
})

const deleteMutation = useMutation({
  mutationFn: () => issueApi.deleteIssue(props.id),
  onSuccess: async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['issues'] }),
      queryClient.invalidateQueries({ queryKey: ['sidebar', 'stats'] }),
      queryClient.invalidateQueries({ queryKey: ['recent-issue-progress-updates'] }),
    ])
    ElMessage.success('議題已永久刪除')
    router.replace({ name: 'IssueList' })
  },
  onError: () => ElMessage.error('刪除議題失敗，請稍後再試'),
})

const changeIssueStatus = (status: IssueStatus) => {
  if (!issue.value || status === issue.value.status || statusMutation.isPending.value) return
  statusMutation.mutate(status)
}

const goBack = () => {
  if (window.history.state?.back) {
    router.back()
    return
  }
  router.push({ name: 'IssueList' })
}

const confirmDeleteIssue = async () => {
  if (!issue.value || deleteMutation.isPending.value) return
  try {
    await ElMessageBox.confirm(
      `「${issue.value.title}」及其所有議題更新、素材關聯都會永久刪除；原始卡片不會被刪除。`,
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

const openEditDialog = (section: EditSection) => {
  if (!issue.value) return
  editSection.value = section
  Object.assign(editForm, toIssueUpdateRequest(issue.value))
  editDialogVisible.value = true
}

const resetEditForm = () => editFormRef.value?.clearValidate()
const normalizeText = (value?: string | null) => value?.trim() || null

const submitUpdateIssue = async () => {
  if (!editFormRef.value) return
  await editFormRef.value.validate((valid) => {
    if (!valid) return
    updateMutation.mutate({
      title: editForm.title.trim(),
      objective: normalizeText(editForm.objective),
      description: normalizeText(editForm.description),
      currentAssessment: normalizeText(editForm.currentAssessment),
      keyStates: normalizeText(editForm.keyStates),
      dominantLoops: normalizeText(editForm.dominantLoops),
      primaryConstraint: normalizeText(editForm.primaryConstraint),
      leveragePoint: normalizeText(editForm.leveragePoint),
      watchSignals: normalizeText(editForm.watchSignals),
      nonInterventionNote: normalizeText(editForm.nonInterventionNote),
      outcomeCriteria: normalizeText(editForm.outcomeCriteria),
      externalUrl: normalizeText(editForm.externalUrl),
      status: editForm.status,
    })
  })
}
</script>

<template>
  <section class="issue-detail-page app-page">
    <header class="detail-navigation app-detail-navigation">
      <el-button :icon="ArrowLeft" text @click="goBack">返回議事廳</el-button>
      <div v-if="issue" class="detail-actions">
        <el-dropdown trigger="click" @command="(status: IssueStatus) => changeIssueStatus(status)">
          <button type="button" class="status-trigger" :disabled="statusMutation.isPending.value">
            <span class="status-dot" :class="`status-${issueStatusMeta[issue.status].tone}`"></span>
            <span>{{ issueStatusMeta[issue.status].label }}</span>
            <el-icon><ArrowDown /></el-icon>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item
                v-for="option in issueStatusOptions"
                :key="option.value"
                :command="option.value"
                :disabled="option.value === issue.status"
                :divided="option.value === 'ARCHIVED'"
              >
                {{ option.label }}
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-button type="primary" plain :icon="Edit" @click="openEditDialog('basic')">編輯議題</el-button>
        <el-button
          type="danger"
          plain
          :icon="Delete"
          :loading="deleteMutation.isPending.value"
          @click="confirmDeleteIssue"
        >
          永久刪除
        </el-button>
      </div>
    </header>

    <div v-if="isLoading" class="state-surface" aria-label="議題詳情載入中">
      <el-skeleton :rows="8" animated />
    </div>
    <div v-else-if="isError" class="state-surface">
      <el-result icon="error" title="無法載入議題" sub-title="議題可能不存在，或目前無法連線">
        <template #extra>
          <el-button @click="goBack">返回列表</el-button>
          <el-button type="primary" @click="refetch()">重新載入</el-button>
        </template>
      </el-result>
    </div>

    <main v-else-if="issue" class="cockpit">
      <header class="cockpit-header">
        <span class="detail-eyebrow">議題名稱</span>
        <h1>{{ issue.title }}</h1>
        <span class="current-mode">目前模式 · {{ issueStatusMeta[issue.status].label }}</span>
      </header>

      <section class="system-question" aria-labelledby="system-question-title">
        <header class="section-heading with-action">
          <div>
            <h2 id="system-question-title">本輪系統問題</h2>
          </div>
          <button type="button" class="inline-edit-button" @click="openEditDialog('basic')">編輯</button>
        </header>
        <ExpandableText v-if="issue.objective" :content="issue.objective" :lines="5" />
        <p v-else class="empty-copy">這一輪想靠哪些現實資料，把問題往前推進？</p>
      </section>

      <div class="dashboard-grid">
        <section class="dashboard-panel" aria-labelledby="snapshot-title">
          <header class="section-heading with-action">
            <div>
              <h2 id="snapshot-title">系統快照</h2>
            </div>
            <button type="button" class="inline-edit-button" @click="openEditDialog('systemSnapshot')">編輯</button>
          </header>
          <div class="dashboard-field">
            <h3>關鍵狀態</h3>
            <ExpandableText v-if="issue.keyStates" :content="issue.keyStates" :lines="7" />
            <p v-else class="empty-copy">還沒有整理關鍵狀態。只留下目前真正影響局勢的幾項即可。</p>
          </div>
          <div class="dashboard-field">
            <h3>主導迴路</h3>
            <ExpandableText v-if="issue.dominantLoops" :content="issue.dominantLoops" :lines="8" />
            <p v-else class="empty-copy">還沒有辨認主導迴路。先累積真實訊號，不必為了填欄位硬找因果。</p>
          </div>
        </section>

        <section class="dashboard-panel" aria-labelledby="decision-title">
          <header class="section-heading with-action">
            <div>
              <h2 id="decision-title">當前決策</h2>
            </div>
            <button type="button" class="inline-edit-button" @click="openEditDialog('currentDecision')">編輯</button>
          </header>
          <div class="dashboard-field">
            <h3>主要瓶頸</h3>
            <ExpandableText v-if="issue.primaryConstraint" :content="issue.primaryConstraint" :lines="4" />
            <p v-else class="empty-copy">尚未辨認主要瓶頸。若目前還在探索，可以先保持空白。</p>
          </div>
          <div class="dashboard-field">
            <h3>當前槓桿點</h3>
            <ExpandableText v-if="issue.leveragePoint" :content="issue.leveragePoint" :lines="4" />
            <p v-else class="empty-copy">尚未選定介入位置。不是每個議題現在都需要行動。</p>
          </div>
          <div class="dashboard-field">
            <h3>待觀察訊號</h3>
            <ExpandableText v-if="issue.watchSignals" :content="issue.watchSignals" :lines="6" />
            <p v-else class="empty-copy">還沒有設定。採取介入後，再留下真正需要等待的現實回饋。</p>
          </div>
          <div class="dashboard-field">
            <h3>暫不介入／延遲提醒</h3>
            <ExpandableText v-if="issue.nonInterventionNote" :content="issue.nonInterventionNote" :lines="5" />
            <p v-else class="empty-copy">沒有特別需要避免過度反應的訊號時，可以保持空白。</p>
          </div>
        </section>
      </div>

      <IssueUpdates :issue-id="props.id" />

      <section class="execution-panel" aria-labelledby="execution-title">
        <header class="section-heading with-action">
          <div>
            <h2 id="execution-title">執行／成果入口</h2>
            <p>真正需要拆解、執行或查看成果時，從這裡前往外部工作區。</p>
          </div>
          <button type="button" class="inline-edit-button" @click="openEditDialog('executionLink')">編輯</button>
        </header>
        <a
          v-if="issue.externalUrl"
          :href="issue.externalUrl"
          target="_blank"
          rel="noopener noreferrer"
          class="external-link"
        >
          <el-icon><Link /></el-icon>
          <span>前往執行／成果工作區</span>
        </a>
        <p v-else class="empty-copy">尚未設定。真正需要拆解、執行或查看成果時再加入。</p>
      </section>

      <IssueMaterialManager :issue-id="props.id" />

      <section class="supporting-panel" aria-label="低頻議題資訊">
        <details class="supporting-disclosure">
          <summary>收斂／重議條件</summary>
          <div class="supporting-content">
            <button type="button" class="inline-edit-button" @click="openEditDialog('criteria')">編輯</button>
            <ExpandableText v-if="issue.outcomeCriteria" :content="issue.outcomeCriteria" :lines="8" />
            <p v-else class="empty-copy">尚未設定；需要形成判準時再補充。</p>
          </div>
        </details>
        <details class="supporting-disclosure">
          <summary>補充研判</summary>
          <div class="supporting-content">
            <button type="button" class="inline-edit-button" @click="openEditDialog('supplementalAssessment')">編輯</button>
            <ExpandableText v-if="issue.currentAssessment" :content="issue.currentAssessment" :lines="10" />
            <p v-else class="empty-copy">沒有需要補充的長篇研判時，可以保持空白。</p>
          </div>
        </details>
        <details class="supporting-disclosure">
          <summary>背景與脈絡</summary>
          <div class="supporting-content">
            <button type="button" class="inline-edit-button" @click="openEditDialog('background')">編輯</button>
            <ExpandableText v-if="issue.description" :content="issue.description" :lines="8" />
            <p v-else class="empty-copy">尚未補充背景；需要時再填即可。</p>
          </div>
        </details>
        <dl class="time-metadata">
          <div><dt>建立時間</dt><dd>{{ formatDate(issue.createdAt) }}</dd></div>
          <div><dt>最後更新</dt><dd>{{ formatDate(issue.updatedAt) }}</dd></div>
          <div v-if="issue.completedAt"><dt>收斂時間</dt><dd>{{ formatDate(issue.completedAt) }}</dd></div>
        </dl>
      </section>
    </main>

    <AppDialog
      v-model="editDialogVisible"
      :title="editDialogTitle"
      width="min(680px, calc(100vw - 32px))"
      scroll-body
      destroy-on-close
      @closed="resetEditForm"
    >
      <el-form
        ref="editFormRef"
        :model="editForm"
        :rules="editFormRules"
        label-position="top"
        @submit.prevent="submitUpdateIssue"
      >
        <template v-if="editSection === 'basic'">
          <el-form-item label="議題名稱" prop="title">
            <el-input v-model="editForm.title" maxlength="255" />
          </el-form-item>
          <el-form-item label="本輪系統問題" prop="objective">
            <el-input
              v-model="editForm.objective"
              type="textarea"
              :rows="3"
              maxlength="50000"
              placeholder="這一輪需要靠什麼現實資料，讓你比現在更接近答案？（選填）"
            />
          </el-form-item>
          <el-form-item label="目前模式" prop="status">
            <el-select v-model="editForm.status" class="status-select">
              <el-option
                v-for="option in issueStatusOptions"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </el-form-item>
        </template>

        <template v-if="editSection === 'systemSnapshot'">
          <el-form-item label="關鍵狀態" prop="keyStates">
            <el-input
              v-model="editForm.keyStates"
              type="textarea"
              :rows="6"
              maxlength="50000"
              placeholder="哪些重要存量或狀態正在累積、停滯或耗損？（選填）"
            />
          </el-form-item>
          <el-form-item label="主導迴路" prop="dominantLoops">
            <el-input
              v-model="editForm.dominantLoops"
              type="textarea"
              :rows="6"
              maxlength="50000"
              placeholder="哪一條因果循環最能解釋系統目前的狀態？（選填）"
            />
          </el-form-item>
        </template>

        <template v-if="editSection === 'currentDecision'">
          <el-form-item label="主要瓶頸" prop="primaryConstraint">
            <el-input v-model="editForm.primaryConstraint" type="textarea" :rows="3" maxlength="50000" />
          </el-form-item>
          <el-form-item label="當前槓桿點" prop="leveragePoint">
            <el-input v-model="editForm.leveragePoint" type="textarea" :rows="3" maxlength="50000" />
          </el-form-item>
          <el-form-item label="待觀察訊號" prop="watchSignals">
            <el-input v-model="editForm.watchSignals" type="textarea" :rows="4" maxlength="50000" />
          </el-form-item>
          <el-form-item label="暫不介入／延遲提醒" prop="nonInterventionNote">
            <el-input v-model="editForm.nonInterventionNote" type="textarea" :rows="4" maxlength="50000" />
          </el-form-item>
        </template>

        <el-form-item
          v-if="editSection === 'supplementalAssessment'"
          label="補充研判"
          prop="currentAssessment"
        >
          <CurrentAssessmentGuide v-model="editForm.currentAssessment" />
          <el-input
            v-model="editForm.currentAssessment"
            type="textarea"
            :rows="7"
            maxlength="50000"
            placeholder="補充無法或不值得放進結構欄位的思考（選填）"
          />
        </el-form-item>

        <el-form-item v-if="editSection === 'criteria'" label="收斂／重議條件" prop="outcomeCriteria">
          <el-input
            v-model="editForm.outcomeCriteria"
            type="textarea"
            :rows="5"
            maxlength="50000"
            placeholder="什麼情況可以收斂？發生什麼重大變化時需要重新議定？（選填）"
          />
        </el-form-item>

        <el-form-item v-if="editSection === 'executionLink'" label="執行／成果入口" prop="externalUrl">
          <el-input
            v-model="editForm.externalUrl"
            maxlength="2048"
            placeholder="Trello、GitHub、稿件、文件或其他工作區（選填）"
          />
          <p class="field-helper">第一版支援一個 HTTP 或 HTTPS 連結。</p>
        </el-form-item>

        <el-form-item v-if="editSection === 'background'" label="背景與脈絡" prop="description">
          <el-input
            v-model="editForm.description"
            type="textarea"
            :rows="7"
            maxlength="50000"
            placeholder="為什麼這個議題會存在？（選填）"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button :disabled="updateMutation.isPending.value" @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="updateMutation.isPending.value" @click="submitUpdateIssue">
          儲存變更
        </el-button>
      </template>
    </AppDialog>
  </section>
</template>

<style scoped>
.issue-detail-page {
  width: 100%;
  min-height: 100%;
  box-sizing: border-box;
  background: var(--surface-page);
}

.detail-navigation {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  padding: var(--space-sm) var(--space-md);
  border-bottom: 1px solid var(--el-border-color-light);
  background: var(--el-bg-color);
}

.detail-actions {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
}

.state-surface,
.cockpit {
  width: min(100%, var(--content-max-width));
  margin: 0 auto;
  box-sizing: border-box;
}

.state-surface {
  padding: var(--panel-padding);
}

.cockpit {
  display: grid;
  gap: var(--space-lg);
  padding: var(--workspace-padding);
}

.cockpit-header {
  position: relative;
  padding: var(--space-md) 0 0;
}

.detail-eyebrow {
  display: block;
  color: var(--el-text-color-placeholder);
  font-size: var(--type-meta);
  font-weight: 600;
  letter-spacing: 0.08em;
}

.cockpit-header h1 {
  margin: var(--space-xs) 0 0;
  padding-right: 150px;
  font-size: var(--type-detail-title);
  line-height: var(--leading-title);
  overflow-wrap: anywhere;
}

.current-mode {
  position: absolute;
  top: var(--space-lg);
  right: 0;
  color: var(--el-text-color-secondary);
  font-size: var(--type-caption);
}

.system-question,
.dashboard-panel,
.execution-panel,
.supporting-panel {
  padding: var(--panel-padding);
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--radius-md);
  background: var(--el-bg-color);
}

.system-question {
  border-left: 3px solid var(--el-color-primary-light-5);
}

.section-heading {
  margin-bottom: var(--space-md);
}

.section-heading.with-action {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-md);
}

.section-heading h2 {
  margin: var(--space-2xs) 0 0;
  font-size: var(--type-section-title);
  line-height: var(--leading-section);
}

.section-heading p {
  margin: var(--space-2xs) 0 0;
  color: var(--el-text-color-secondary);
  font-size: var(--type-caption);
  line-height: var(--leading-ui);
}

.system-question :deep(.expandable-content) {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: var(--type-prominent);
  font-weight: 500;
  line-height: 1.7;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-md);
  align-items: start;
}

.dashboard-field + .dashboard-field {
  margin-top: var(--space-md);
  padding-top: var(--space-md);
  border-top: 1px solid var(--el-border-color-lighter);
}

.dashboard-field h3 {
  margin: 0 0 var(--space-xs);
  color: var(--el-text-color-secondary);
  font-size: var(--type-caption);
  font-weight: 600;
}

.dashboard-field :deep(.expandable-content),
.supporting-content :deep(.expandable-content) {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: var(--type-body);
  line-height: var(--leading-body);
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.empty-copy {
  margin: 0;
  color: var(--el-text-color-placeholder);
  font-size: var(--type-caption);
  line-height: var(--leading-ui);
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
  text-decoration: underline;
}

.external-link {
  display: inline-flex;
  align-items: center;
  gap: var(--space-xs);
  color: var(--el-color-primary);
  font-size: var(--type-ui);
  text-decoration: none;
}

.external-link:hover {
  color: var(--el-color-primary-light-3);
}

.supporting-panel {
  padding-block: 0;
}

.supporting-disclosure + .supporting-disclosure {
  border-top: 1px solid var(--el-border-color-lighter);
}

.supporting-disclosure > summary {
  padding: var(--space-md) 0;
  color: var(--el-text-color-secondary);
  font-size: var(--type-ui);
  font-weight: 600;
  cursor: pointer;
}

.supporting-disclosure[open] > summary {
  color: var(--el-text-color-primary);
}

.supporting-content {
  position: relative;
  padding: 0 var(--space-xl) var(--space-lg) 0;
}

.supporting-content > .inline-edit-button {
  position: absolute;
  top: 0;
  right: 0;
}

.time-metadata {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-md) var(--space-lg);
  margin: 0;
  padding: var(--space-md) 0;
  border-top: 1px solid var(--el-border-color-lighter);
}

.time-metadata dt {
  color: var(--el-text-color-placeholder);
  font-size: var(--type-meta);
}

.time-metadata dd {
  margin: var(--space-2xs) 0 0;
  color: var(--el-text-color-secondary);
  font-size: var(--type-caption);
}

.status-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-xs);
  min-width: 120px;
  padding: 7px 10px;
  border: 1px solid var(--el-border-color);
  border-radius: var(--radius-sm);
  background: var(--el-fill-color-blank);
  color: var(--el-text-color-regular);
  font: inherit;
  white-space: nowrap;
  cursor: pointer;
}

.status-trigger:disabled {
  cursor: wait;
  opacity: 0.65;
}

.status-dot {
  width: 8px;
  height: 8px;
  flex: 0 0 auto;
  border-radius: 50%;
  background: var(--el-text-color-placeholder);
}

.status-exploring { background: var(--el-color-info); }
.status-modeling { background: var(--el-color-warning); }
.status-intervening { background: var(--el-color-primary); }
.status-converged { background: var(--el-color-success); }

.status-select {
  width: 100%;
}

.field-helper {
  margin: var(--space-xs) 0 0;
  color: var(--el-text-color-placeholder);
  font-size: var(--type-meta);
}

.cockpit :deep(.material-surface) {
  margin-top: 0;
}

@media (max-width: 900px) {
  .dashboard-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 760px) {
  .detail-navigation {
    align-items: stretch;
    flex-direction: column;
  }

  .detail-actions {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .detail-actions > :first-child {
    grid-column: 1 / -1;
  }

  .status-trigger {
    width: 100%;
  }

  .cockpit-header h1 {
    padding-right: 0;
  }

  .current-mode {
    position: static;
    display: block;
    margin-top: var(--space-xs);
  }

  .system-question,
  .dashboard-panel,
  .execution-panel {
    padding: var(--panel-padding);
  }
}

@media (max-width: 480px) {
  .detail-actions :deep(.el-button) {
    width: 100%;
    margin-left: 0;
  }

  .section-heading.with-action {
    gap: var(--space-sm);
  }
}
</style>
