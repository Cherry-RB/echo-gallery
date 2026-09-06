<script setup lang="ts">
import { computed, ref } from 'vue'
import { Delete, Edit, MoreFilled, Plus } from '@element-plus/icons-vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { WorkProgressUpdate } from '../../types/work'
import { formatDate } from '../../utils/formatDate'
import { workApi } from '../../utils/api/workApi'
import ExpandableText from './ExpandableText.vue'
import WorkProgressUpdateDialog from './WorkProgressUpdateDialog.vue'

const props = defineProps<{ workId: string | number }>()
const queryClient = useQueryClient()
const updateDialogVisible = ref(false)
const historyDialogVisible = ref(false)
const editingUpdate = ref<WorkProgressUpdate | null>(null)
const historyPage = ref(0)
const historyPageSize = 10

const summaryQueryKey = computed(() => ['work-progress-updates', String(props.workId), 'summary'])
const historyQueryKey = computed(() => [
  'work-progress-updates', String(props.workId), 'history', historyPage.value,
])

const {
  data: summaryPage,
  isLoading,
  isError,
  refetch,
} = useQuery({
  queryKey: summaryQueryKey,
  queryFn: () => workApi.getWorkUpdates(props.workId, 0, 5),
})

const {
  data: historyPageData,
  isLoading: isHistoryLoading,
  isError: isHistoryError,
  refetch: refetchHistory,
} = useQuery({
  queryKey: historyQueryKey,
  queryFn: () => workApi.getWorkUpdates(props.workId, historyPage.value, historyPageSize),
  enabled: historyDialogVisible,
})

const summaryUpdates = computed(() => summaryPage.value?.items ?? [])
const latestUpdate = computed(() => summaryUpdates.value[0] ?? null)
const earlierUpdates = computed(() => summaryUpdates.value.slice(1, 5))
const historyUpdates = computed(() => historyPageData.value?.items ?? [])

const invalidateUpdateQueries = async () => {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: ['work-progress-updates', String(props.workId)] }),
    queryClient.invalidateQueries({ queryKey: ['recent-work-progress-updates'] }),
    queryClient.invalidateQueries({ queryKey: ['works'] }),
  ])
}

const deleteMutation = useMutation({
  mutationFn: (updateId: number) => workApi.deleteWorkUpdate(props.workId, updateId),
  onSuccess: async () => {
    await invalidateUpdateQueries()
    ElMessage.success('議題更新已刪除')
  },
  onError: () => ElMessage.error('刪除議題更新失敗，請稍後再試'),
})

const openCreateDialog = () => {
  editingUpdate.value = null
  updateDialogVisible.value = true
}

const openEditDialog = (update: WorkProgressUpdate) => {
  editingUpdate.value = update
  updateDialogVisible.value = true
}

const openHistoryDialog = () => {
  historyPage.value = 0
  historyDialogVisible.value = true
}

const confirmDelete = async (update: WorkProgressUpdate) => {
  try {
    await ElMessageBox.confirm(
      '刪除這次議題更新？刪除後無法復原，但不會改動議題的整體資料。',
      '刪除議題更新',
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

const wasEdited = (update: WorkProgressUpdate) => (
  new Date(update.updatedAt).getTime() - new Date(update.createdAt).getTime() > 1000
)
</script>

<template>
  <section class="updates-panel" aria-labelledby="updates-title">
    <header class="updates-heading">
      <div>
        <h2 id="updates-title">最近推進</h2>
        <p>從最新變化看見此刻的判斷，以及準備往哪裡走。</p>
      </div>
      <el-button type="primary" plain :icon="Plus" @click="openCreateDialog">提出近況</el-button>
    </header>

    <div v-if="isLoading" class="updates-state">
      <el-skeleton :rows="4" animated />
    </div>
    <div v-else-if="isError" class="updates-state error-state">
      <p>目前無法載入議題近況。</p>
      <el-button text type="primary" @click="refetch()">重新載入</el-button>
    </div>

    <template v-else-if="latestUpdate">
      <ol class="update-timeline" aria-label="最近五筆議題近況">
        <li class="timeline-entry latest-timeline-entry">
          <span class="timeline-dot" aria-hidden="true"></span>
          <article class="update-record">
            <header class="update-entry-header">
              <span>最新更新</span>
              <time :datetime="latestUpdate.createdAt">
                {{ formatDate(latestUpdate.createdAt, 'YYYY/MM/DD HH:mm') }}{{ wasEdited(latestUpdate) ? ' · 已編輯' : '' }}
              </time>
            </header>
            <section v-if="latestUpdate.changeSummary" class="update-block">
              <h3>最近有什麼改變？</h3>
              <ExpandableText :content="latestUpdate.changeSummary" :lines="6" />
            </section>
            <section v-if="latestUpdate.assessment" class="update-block">
              <h3>現在怎麼看？</h3>
              <ExpandableText :content="latestUpdate.assessment" :lines="6" />
            </section>
            <section v-if="latestUpdate.nextStep" class="next-step-block">
              <h3>所以接下來呢？</h3>
              <ExpandableText :content="latestUpdate.nextStep" :lines="4" />
            </section>
          </article>
        </li>
        <li v-for="update in earlierUpdates" :key="update.id" class="timeline-entry compact-timeline-entry">
          <span class="timeline-dot" aria-hidden="true"></span>
          <article class="compact-update-record">
            <div class="compact-update-line">
              <ExpandableText
                :content="update.changeSummary || '這次未記錄事件變化。'"
                :lines="1"
              />
              <time :datetime="update.createdAt">{{ formatDate(update.createdAt, 'MM/DD HH:mm') }}</time>
            </div>
          </article>
        </li>
      </ol>

      <button type="button" class="history-button" @click="openHistoryDialog">查看歷次更新</button>
    </template>

    <div v-else class="updates-empty">
      <p>還沒有近況。等局勢真的有變化時，再留下一次快照就好。</p>
      <button type="button" @click="openCreateDialog">寫下第一筆近況</button>
    </div>

    <el-dialog
      v-model="historyDialogVisible"
      title="歷次更新"
      width="min(760px, calc(100vw - 32px))"
      destroy-on-close
    >
      <div v-if="isHistoryLoading" class="history-state">
        <el-skeleton :rows="6" animated />
      </div>
      <div v-else-if="isHistoryError" class="history-state error-state">
        <p>目前無法載入歷次更新。</p>
        <el-button text type="primary" @click="refetchHistory()">重新載入</el-button>
      </div>
      <ol v-else-if="historyUpdates.length" class="history-list">
        <li v-for="update in historyUpdates" :key="update.id" class="history-item">
          <span class="timeline-dot" aria-hidden="true"></span>
          <article class="update-record">
            <header>
              <time :datetime="update.createdAt">
                {{ formatDate(update.createdAt, 'YYYY/MM/DD HH:mm') }}{{ wasEdited(update) ? ' · 已編輯' : '' }}
              </time>
              <el-dropdown trigger="click" @command="(command: string) => command === 'edit' ? openEditDialog(update) : confirmDelete(update)">
                <button type="button" class="more-button" aria-label="議題更新操作">
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
              <section v-if="update.changeSummary">
                <span>最近有什麼改變？</span>
                <ExpandableText :content="update.changeSummary" :lines="5" />
              </section>
              <section v-if="update.assessment">
                <span>現在怎麼看？</span>
                <ExpandableText :content="update.assessment" :lines="5" />
              </section>
              <section v-if="update.nextStep" class="history-next-step">
                <span>所以接下來呢？</span>
                <ExpandableText :content="update.nextStep" :lines="4" />
              </section>
            </div>
          </article>
        </li>
      </ol>
      <p v-else class="history-empty">這個議題還沒有更新紀錄。</p>

      <template #footer>
        <div class="history-pagination">
          <span>第 {{ historyPage + 1 }} 頁 · 每頁最多 {{ historyPageSize }} 筆</span>
          <div>
            <el-button :disabled="historyPage === 0" @click="historyPage -= 1">上一頁</el-button>
            <el-button :disabled="!historyPageData?.hasNext" @click="historyPage += 1">下一頁</el-button>
          </div>
        </div>
      </template>
    </el-dialog>

    <WorkProgressUpdateDialog v-model="updateDialogVisible" :work-id="workId" :update="editingUpdate" />
  </section>
</template>

<style scoped>
.updates-panel {
  min-width: 0;
}

.updates-heading,
.update-entry-header,
.history-item article > header,
.history-pagination {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.updates-heading h2 {
  margin: 0 0 6px;
  font-size: var(--type-section-title);
  line-height: var(--leading-section);
}

.updates-heading p {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: var(--type-caption);
  line-height: var(--leading-ui);
}

.update-timeline,
.history-list {
  margin: 24px 0 0;
  padding: 0;
  list-style: none;
}

.timeline-entry,
.history-item {
  position: relative;
  display: grid;
  grid-template-columns: 14px minmax(0, 1fr);
  gap: 12px;
  padding-bottom: 24px;
}

.timeline-entry::before,
.history-item::before {
  position: absolute;
  top: 12px;
  bottom: 0;
  left: 5px;
  width: 1px;
  background: var(--el-border-color-light);
  content: '';
}

.timeline-entry:last-child::before,
.history-item:last-child::before {
  display: none;
}

.timeline-dot {
  z-index: 1;
  width: 11px;
  height: 11px;
  margin-top: 4px;
  border: 2px solid var(--el-bg-color);
  border-radius: 50%;
  background: var(--el-color-primary-light-5);
  box-shadow: 0 0 0 1px var(--el-color-primary-light-5);
}

.latest-timeline-entry .timeline-dot {
  background: var(--el-color-primary);
  box-shadow: 0 0 0 3px var(--el-color-primary-light-9);
}

.timeline-entry article,
.history-item article {
  min-width: 0;
}

.update-record {
  padding: 16px;
  border-radius: 10px;
  background: var(--el-fill-color-light);
}

.update-entry-header {
  color: var(--el-text-color-placeholder);
  font-size: var(--type-meta);
}

.update-entry-header > span {
  color: var(--el-text-color-primary);
  font-weight: 700;
}

.update-entry-header time,
.history-item time {
  flex: 0 0 auto;
  color: var(--el-text-color-placeholder);
  font-size: var(--type-meta);
}

.update-block {
  margin-top: 14px;
}

.update-block h3,
.next-step-block h3 {
  margin: 0 0 4px;
  color: var(--el-text-color-placeholder);
  font-size: var(--type-meta);
  font-weight: 600;
}

.update-block :deep(.expandable-content) {
  margin: 0;
  color: var(--el-text-color-regular);
  font-size: var(--type-ui);
  line-height: 1.65;
}

.next-step-block {
  margin-top: 14px;
  padding-top: 2px;
}

.next-step-block h3 {
  color: var(--el-text-color-placeholder);
}

.next-step-block :deep(.expandable-content) {
  color: var(--el-color-primary);
}

.compact-update-record {
  min-width: 0;
  padding: 0;
}

.compact-timeline-entry {
  padding-bottom: 10px;
}

.compact-update-line {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: baseline;
  gap: 12px;
}

.compact-update-line time {
  white-space: nowrap;
  color: var(--el-text-color-placeholder);
  font-size: var(--type-meta);
}

.compact-update-line :deep(.expandable-text) {
  min-width: 0;
}

.compact-update-line :deep(.expandable-content) {
  margin: 0;
  color: var(--el-text-color-regular);
  font-size: var(--type-ui);
  line-height: 1.65;
}

.compact-update-line :deep(.expand-button) {
  display: none;
}

.history-button {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--el-color-primary);
  font: inherit;
  font-size: var(--type-caption);
  cursor: pointer;
}

.history-button:hover,
.history-button:focus-visible {
  text-decoration: underline;
}

.updates-state,
.updates-empty,
.history-state,
.history-empty {
  margin-top: 22px;
  padding: 22px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-secondary);
  text-align: center;
}

.updates-empty p,
.error-state p,
.history-empty {
  margin-bottom: 0;
}

.updates-empty button {
  margin-top: 8px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--el-color-primary);
  cursor: pointer;
}

.history-list {
  max-height: min(62vh, 680px);
  overflow-y: auto;
}

.history-item {
  padding-bottom: 24px;
}

.history-item article {
  padding: 16px;
}

.more-button {
  padding: 2px 5px;
  border: 0;
  background: transparent;
  color: var(--el-text-color-secondary);
  cursor: pointer;
}

.history-fields {
  display: grid;
  gap: 14px;
  margin-top: 14px;
}

.history-fields section > span {
  display: block;
  margin-bottom: 4px;
  color: var(--el-text-color-placeholder);
  font-size: var(--type-meta);
  font-weight: 600;
}

.history-fields :deep(.expandable-content) {
  margin: 0;
  color: var(--el-text-color-regular);
  font-size: var(--type-ui);
  line-height: 1.65;
}

.history-next-step {
  padding-top: 2px;
}

.history-next-step :deep(.expandable-content) {
  color: var(--el-color-primary);
}

.history-pagination {
  align-items: center;
  width: 100%;
}

.history-pagination > span {
  color: var(--el-text-color-placeholder);
  font-size: var(--type-meta);
}

@media (max-width: 600px) {
  .updates-heading,
  .history-pagination {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
