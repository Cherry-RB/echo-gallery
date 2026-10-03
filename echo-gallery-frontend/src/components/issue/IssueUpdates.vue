<script setup lang="ts">
import { computed, ref } from 'vue'
import { Delete, Edit, MoreFilled, Plus } from '@element-plus/icons-vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { IssueUpdate } from '../../types/issue'
import { issueApi } from '../../utils/api/issueApi'
import { formatDate } from '../../utils/formatDate'
import AppDialog from '../AppDialog.vue'
import ExpandableText from '../ExpandableText.vue'
import IssueUpdateDialog from './IssueUpdateDialog.vue'

const props = withDefaults(defineProps<{
  issueId: string | number
  historyOnly?: boolean
  modelValue?: boolean
}>(), {
  historyOnly: false,
  modelValue: false,
})
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()
const queryClient = useQueryClient()
const updateDialogVisible = ref(false)
const inlineHistoryDialogVisible = ref(false)
const historyDialogVisible = computed({
  get: () => props.historyOnly ? props.modelValue : inlineHistoryDialogVisible.value,
  set: (value: boolean) => {
    if (props.historyOnly) emit('update:modelValue', value)
    else inlineHistoryDialogVisible.value = value
  },
})
const editingUpdate = ref<IssueUpdate | null>(null)
const pendingHistoryEdit = ref<IssueUpdate | null>(null)
const reopenHistoryAfterEdit = ref(false)
const historyPage = ref(0)
const historyPageSize = 10

const summaryQueryKey = computed(() => ['issue-progress-updates', String(props.issueId), 'summary'])
const historyQueryKey = computed(() => [
  'issue-progress-updates', String(props.issueId), 'history', historyPage.value,
])

const {
  data: summaryPage,
  isLoading,
  isError,
  refetch,
} = useQuery({
  queryKey: summaryQueryKey,
  queryFn: () => issueApi.getIssueUpdates(props.issueId, 0, 1),
  enabled: computed(() => !props.historyOnly),
})

const {
  data: historyPageData,
  isLoading: isHistoryLoading,
  isError: isHistoryError,
  refetch: refetchHistory,
} = useQuery({
  queryKey: historyQueryKey,
  queryFn: () => issueApi.getIssueUpdates(props.issueId, historyPage.value, historyPageSize),
  enabled: historyDialogVisible,
})

const latestUpdate = computed(() => summaryPage.value?.items[0] ?? null)
const historyUpdates = computed(() => historyPageData.value?.items ?? [])

const invalidateUpdateQueries = async () => {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: ['issue-progress-updates', String(props.issueId)] }),
    queryClient.invalidateQueries({ queryKey: ['recent-issue-progress-updates'] }),
    queryClient.invalidateQueries({ queryKey: ['issues'] }),
    queryClient.invalidateQueries({ queryKey: ['overview'] }),
  ])
}

const deleteMutation = useMutation({
  mutationFn: (updateId: number) => issueApi.deleteIssueUpdate(props.issueId, updateId),
  onSuccess: async () => {
    await invalidateUpdateQueries()
    ElMessage.success('系統訊號已刪除')
  },
  onError: () => ElMessage.error('刪除系統訊號失敗，請稍後再試'),
})

const openCreateDialog = () => {
  editingUpdate.value = null
  reopenHistoryAfterEdit.value = false
  updateDialogVisible.value = true
}

const requestEditFromHistory = (update: IssueUpdate) => {
  pendingHistoryEdit.value = update
  historyDialogVisible.value = false
}

const handleHistoryClosed = () => {
  if (!pendingHistoryEdit.value) return
  editingUpdate.value = pendingHistoryEdit.value
  pendingHistoryEdit.value = null
  reopenHistoryAfterEdit.value = true
  updateDialogVisible.value = true
}

const handleUpdateDialogClosed = () => {
  if (!reopenHistoryAfterEdit.value) return
  reopenHistoryAfterEdit.value = false
  historyDialogVisible.value = true
}

const openHistoryDialog = () => {
  historyPage.value = 0
  historyDialogVisible.value = true
}

const confirmDelete = async (update: IssueUpdate) => {
  try {
    await ElMessageBox.confirm(
      '刪除這次系統訊號？刪除後無法復原，但不會改動議題的系統快照或當前決策。',
      '刪除系統訊號',
      {
        confirmButtonText: '刪除',
        cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger',
        type: 'warning',
      },
    )
    deleteMutation.mutate(update.id)
  } catch {
    // 使用者取消刪除，不需要顯示額外訊息。
  }
}

const wasEdited = (update: IssueUpdate) => (
  new Date(update.updatedAt).getTime() - new Date(update.createdAt).getTime() > 1000
)
</script>

<template>
  <section :class="['updates-panel', { 'history-only': historyOnly }]" aria-labelledby="updates-title">
    <template v-if="!historyOnly">
    <header class="updates-heading">
      <div>
        <h2 id="updates-title">最新系統訊號</h2>
        <p>只呈現世界最新回傳的一筆資料；歷史訊號可另行展開。</p>
      </div>
      <div class="updates-actions">
        <time v-if="latestUpdate" :datetime="latestUpdate.createdAt">
          {{ formatDate(latestUpdate.createdAt, 'YYYY/MM/DD') }}
        </time>
        <button v-if="latestUpdate" type="button" class="text-action" @click="openHistoryDialog">
          歷次更新
          <span class="progress-update-count">{{ summaryPage?.totalCount ?? 0 }}</span>
        </button>
        <el-button type="primary" plain :icon="Plus" @click="openCreateDialog">記錄訊號</el-button>
      </div>
    </header>

    <div v-if="isLoading" class="updates-state"><el-skeleton :rows="3" animated /></div>
    <div v-else-if="isError" class="updates-state error-state">
      <p>目前無法載入系統訊號。</p>
      <el-button text type="primary" @click="refetch()">重新載入</el-button>
    </div>
    <div v-else-if="latestUpdate" class="feedback-grid">
      <section>
        <h3>新訊號</h3>
        <ExpandableText
          v-if="latestUpdate.changeSummary"
          :content="latestUpdate.changeSummary"
          :lines="5"
        />
        <p v-else class="field-empty">這次未記錄新事件或結果。</p>
      </section>
      <section>
        <h3>模型更新</h3>
        <ExpandableText
          v-if="latestUpdate.assessment"
          :content="latestUpdate.assessment"
          :lines="5"
        />
        <p v-else class="field-empty">這次未記錄模型變化。</p>
      </section>
      <section>
        <h3>介入／等待</h3>
        <ExpandableText
          v-if="latestUpdate.nextStep"
          :content="latestUpdate.nextStep"
          :lines="5"
        />
        <p v-else class="field-empty">這次未記錄介入方向或等待訊號。</p>
      </section>
    </div>
    <div v-else class="updates-empty">
      <p>目前沒有新的系統回饋。局勢沒有變化時，不需要為了維護議題而更新。</p>
      <button type="button" class="text-action" @click="openCreateDialog">記下第一筆系統訊號</button>
    </div>
    </template>

    <AppDialog
      v-model="historyDialogVisible"
      title="歷次系統訊號"
      width="min(900px, calc(100vw - 32px))"
      scroll-body
      destroy-on-close
      @closed="handleHistoryClosed"
    >
      <p class="history-intro">依時間回顧世界回傳的訊號，以及當時對系統的理解與介入選擇。</p>
      <div v-if="isHistoryLoading" class="history-state"><el-skeleton :rows="6" animated /></div>
      <div v-else-if="isHistoryError" class="history-state error-state">
        <p>目前無法載入歷次系統訊號。</p>
        <el-button text type="primary" @click="refetchHistory()">重新載入</el-button>
      </div>
      <ol v-else-if="historyUpdates.length" class="history-list">
        <li v-for="(update, index) in historyUpdates" :key="update.id" class="history-item">
          <span class="timeline-marker" aria-hidden="true"></span>
          <article class="history-entry">
            <header>
              <div class="history-time">
                <span v-if="historyPage === 0 && index === 0" class="latest-label">最新</span>
                <time :datetime="update.createdAt">
                  {{ formatDate(update.createdAt, 'YYYY/MM/DD HH:mm') }}{{ wasEdited(update) ? ' · 已編輯' : '' }}
                </time>
              </div>
              <el-dropdown trigger="click" @command="(command: string) => command === 'edit' ? requestEditFromHistory(update) : confirmDelete(update)">
                <button type="button" class="more-button" :aria-label="`${formatDate(update.createdAt, 'YYYY/MM/DD HH:mm')} 系統訊號操作`">
                  <el-icon><MoreFilled /></el-icon>
                </button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="edit" :icon="Edit">修改</el-dropdown-item>
                    <el-dropdown-item command="delete" :icon="Delete" divided>刪除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </header>
            <div class="history-fields">
              <section>
                <span>新訊號</span>
                <ExpandableText v-if="update.changeSummary" :content="update.changeSummary" :lines="5" />
                <p v-else class="field-empty">尚未記錄</p>
              </section>
              <section>
                <span>模型更新</span>
                <ExpandableText v-if="update.assessment" :content="update.assessment" :lines="5" />
                <p v-else class="field-empty">尚未記錄</p>
              </section>
              <section>
                <span>介入／等待</span>
                <ExpandableText v-if="update.nextStep" :content="update.nextStep" :lines="5" />
                <p v-else class="field-empty">尚未記錄</p>
              </section>
            </div>
          </article>
        </li>
      </ol>
      <p v-else class="history-empty">這個議題還沒有系統訊號。</p>

      <template #footer>
        <div class="history-pagination">
          <span>第 {{ historyPage + 1 }} 頁 · 每頁最多 {{ historyPageSize }} 筆</span>
          <div>
            <el-button :disabled="historyPage === 0" @click="historyPage -= 1">較新的訊號</el-button>
            <el-button :disabled="!historyPageData?.hasNext" @click="historyPage += 1">較舊的訊號</el-button>
          </div>
        </div>
      </template>
    </AppDialog>

    <IssueUpdateDialog
      v-model="updateDialogVisible"
      :issue-id="issueId"
      :update="editingUpdate"
      @closed="handleUpdateDialogClosed"
    />
  </section>
</template>

<style scoped>
.updates-panel {
  min-width: 0;
  padding: var(--panel-padding);
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--radius-md);
  background: var(--el-bg-color);
}
.history-only { display: contents; }

.updates-heading,
.updates-actions,
.history-item article > header,
.history-pagination {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-md);
}

.updates-heading h2 {
  margin: var(--space-2xs) 0 0;
  font-size: var(--type-section-title);
  line-height: var(--leading-section);
}

.updates-heading p {
  margin: var(--space-2xs) 0 0;
  color: var(--el-text-color-secondary);
  font-size: var(--type-caption);
  line-height: var(--leading-ui);
}

.updates-actions {
  align-items: center;
  flex: 0 0 auto;
  gap: var(--space-sm);
}

.updates-actions time,
.history-item time {
  color: var(--el-text-color-placeholder);
  font-size: var(--type-meta);
}

.text-action,
.more-button {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--el-color-primary);
  font: inherit;
  font-size: var(--type-caption);
  cursor: pointer;
}
.text-action { display: inline-flex; align-items: center; gap: var(--space-xs); }
.progress-update-count { display: inline-flex; align-items: center; justify-content: center; min-width: 22px; min-height: 22px; padding: 0 var(--space-xs); border: 1px solid var(--el-color-primary-light-7); border-radius: var(--radius-sm); background: var(--el-color-primary-light-9); color: var(--el-color-primary); font-size: var(--type-meta); font-weight: var(--weight-semibold); font-variant-numeric: tabular-nums; line-height: 1; }

.feedback-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin-top: var(--space-md);
  padding: var(--space-md);
  border-radius: var(--radius-md);
  background: var(--surface-summary);
}

.feedback-grid > section {
  min-width: 0;
  padding-right: var(--space-md);
}

.feedback-grid > section:last-child { padding-right: 0; }
.feedback-grid > section + section {
  padding-left: var(--space-md);
  border-left: 1px solid var(--el-border-color-lighter);
}

.feedback-grid h3,
.history-fields section > span {
  margin: 0 0 var(--space-2xs);
  color: var(--el-text-color-secondary);
  font-size: var(--type-caption);
  font-weight: var(--weight-medium);
  line-height: var(--leading-ui);
}

.feedback-grid :deep(.expandable-content) {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: var(--type-ui);
  line-height: var(--leading-ui);
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.field-empty,
.updates-state,
.updates-empty,
.history-state,
.history-empty {
  color: var(--el-text-color-placeholder);
  font-size: var(--type-caption);
  line-height: var(--leading-ui);
}

.field-empty { margin: 0; }
.updates-state,
.updates-empty { margin-top: var(--space-lg); }
.updates-empty p { margin: 0 0 var(--space-xs); }

.history-list {
  position: relative;
  margin: 0;
  padding: 0 0 0 var(--space-xs);
  list-style: none;
}

.history-intro {
  margin: 0 0 var(--space-md);
  color: var(--el-text-color-secondary);
  font-size: var(--type-caption);
  line-height: var(--leading-ui);
}

.history-item {
  position: relative;
  display: grid;
  grid-template-columns: 20px minmax(0, 1fr);
  gap: var(--space-sm);
  padding: 0 0 var(--space-sm);
}

.history-entry {
  min-width: 0;
  padding: var(--space-md);
  border-radius: var(--radius-md);
  border: 1px solid var(--el-border-color-lighter);
  background: var(--surface-summary);
}

.timeline-marker {
  position: relative;
  display: block;
  min-height: 100%;
}

.timeline-marker::before {
  position: absolute;
  top: 10px;
  bottom: -12px;
  left: 9px;
  width: 1px;
  background: var(--el-border-color);
  content: '';
}

.timeline-marker::after {
  position: absolute;
  top: 6px;
  left: 5px;
  width: 7px;
  height: 7px;
  border: 2px solid var(--el-bg-color);
  border-radius: 50%;
  background: var(--el-color-primary);
  box-shadow: 0 0 0 1px var(--el-color-primary-light-5);
  content: '';
}

.history-item:last-child .timeline-marker::before {
  bottom: auto;
  height: 8px;
}

.history-time {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
}

.latest-label {
  padding: var(--space-2xs) var(--space-xs);
  border-radius: var(--radius-sm);
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-size: var(--type-meta);
  font-weight: 600;
}

.more-button {
  color: var(--el-text-color-secondary);
}

.history-fields {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin-top: var(--space-sm);
}

.history-fields section {
  min-width: 0;
  padding-right: var(--space-sm);
}

.history-fields section + section {
  padding-left: var(--space-sm);
  border-left: 1px solid var(--el-border-color-lighter);
}

.history-fields section:last-child {
  padding-right: 0;
}

.history-fields section > span { display: block; }

.history-entry > header { align-items: center; }

.history-fields p {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: var(--type-ui);
  line-height: var(--leading-ui);
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.history-pagination {
  width: 100%;
  align-items: center;
}

.history-pagination > span {
  color: var(--el-text-color-secondary);
  font-size: var(--type-meta);
}

@media (max-width: 760px) {
  .updates-heading {
    flex-direction: column;
  }

  .updates-actions {
    width: 100%;
    flex-wrap: wrap;
  }

  .feedback-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .feedback-grid > section {
    padding: 0;
  }

  .feedback-grid > section + section {
    margin-top: var(--space-md);
    padding-top: var(--space-md);
    padding-left: 0;
    border-top: 1px solid var(--el-border-color-lighter);
    border-left: 0;
  }

  .feedback-grid > section:last-child {
    padding-bottom: 0;
  }

  .history-pagination {
    align-items: stretch;
    flex-direction: column;
  }

  .history-fields {
    grid-template-columns: minmax(0, 1fr);
  }

  .history-fields section {
    padding-right: 0;
  }

  .history-fields section + section {
    margin-top: var(--space-sm);
    padding-top: var(--space-sm);
    padding-left: 0;
    border-top: 1px solid var(--el-border-color-lighter);
    border-left: 0;
  }
}
</style>
