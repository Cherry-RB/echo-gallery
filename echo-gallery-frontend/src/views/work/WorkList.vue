<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ArrowDown, Clock, Link, Plus } from '@element-plus/icons-vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import CurrentAssessmentGuide from '../../components/work/CurrentAssessmentGuide.vue'
import ExpandableText from '../../components/work/ExpandableText.vue'
import WorkProgressUpdateDialog from '../../components/work/WorkProgressUpdateDialog.vue'
import type { CreateWorkRequest, WorkStatus, WorkSummary } from '../../types/work'
import { formatDate } from '../../utils/formatDate'
import { workApi } from '../../utils/api/workApi'

const workStatusMeta: Record<WorkStatus, { label: string; tone: string }> = {
  IDEA: { label: '探索中', tone: 'exploring' },
  DRAFT: { label: '已釐清', tone: 'clarified' },
  ACTIVE: { label: '推進中', tone: 'advancing' },
  DONE: { label: '已完成', tone: 'completed' },
  ARCHIVED: { label: '已封存', tone: 'archived' },
}

type WorkScope = 'OPEN' | 'DONE' | 'ARCHIVED' | 'ALL'
type OpenPhase = 'ALL' | 'IDEA' | 'DRAFT' | 'ACTIVE'

const workScopeOptions: Array<{ value: WorkScope; label: string }> = [
  { value: 'OPEN', label: '進行中' },
  { value: 'DONE', label: '已完成' },
  { value: 'ARCHIVED', label: '已封存' },
  { value: 'ALL', label: '全部' },
]

const openPhaseOptions: Array<{ value: OpenPhase; label: string }> = [
  { value: 'ALL', label: '所有階段' },
  { value: 'IDEA', label: '探索中' },
  { value: 'DRAFT', label: '已釐清' },
  { value: 'ACTIVE', label: '推進中' },
]

const queryClient = useQueryClient()
const router = useRouter()
const createDialogVisible = ref(false)
const recentDrawerVisible = ref(false)
const phasePopoverVisible = ref(false)
const quickUpdateVisible = ref(false)
const quickUpdateWorkId = ref<number | null>(null)
const showCreateDetails = ref(false)
const selectedScope = ref<WorkScope>('OPEN')
const selectedOpenPhase = ref<OpenPhase>('ALL')
const createFormRef = ref<FormInstance>()
const createForm = reactive<CreateWorkRequest>({
  title: '',
  objective: '',
  description: '',
  currentAssessment: '',
  outcomeCriteria: '',
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

const {
  data: recentUpdates,
  isLoading: isRecentUpdatesLoading,
  isError: isRecentUpdatesError,
  refetch: refetchRecentUpdates,
} = useQuery({
  queryKey: ['recent-work-progress-updates'],
  queryFn: () => workApi.getRecentWorkUpdates(12),
  enabled: recentDrawerVisible,
  placeholderData: [],
  staleTime: 1000 * 30,
})

const allWorks = computed(() => works.value ?? [])
const workScopeCounts = computed<Record<WorkScope, number>>(() => ({
  OPEN: allWorks.value.filter((work) => !['DONE', 'ARCHIVED'].includes(work.status)).length,
  DONE: allWorks.value.filter((work) => work.status === 'DONE').length,
  ARCHIVED: allWorks.value.filter((work) => work.status === 'ARCHIVED').length,
  ALL: allWorks.value.length,
}))
const openPhaseCounts = computed<Record<OpenPhase, number>>(() => ({
  ALL: workScopeCounts.value.OPEN,
  IDEA: allWorks.value.filter((work) => work.status === 'IDEA').length,
  DRAFT: allWorks.value.filter((work) => work.status === 'DRAFT').length,
  ACTIVE: allWorks.value.filter((work) => work.status === 'ACTIVE').length,
}))
const recentUpdateList = computed(() => recentUpdates.value ?? [])
const getLatestWorkProgressLead = (work: WorkSummary) => (
  work.latestProgressAssessment
  || work.latestProgressChangeSummary
  || work.latestProgressNextStep
  || ''
)
const workList = computed(() => {
  let filteredWorks = allWorks.value
  if (selectedScope.value === 'ALL') filteredWorks = allWorks.value
  if (selectedScope.value === 'DONE') {
    filteredWorks = allWorks.value.filter((work) => work.status === 'DONE')
  }
  if (selectedScope.value === 'ARCHIVED') {
    filteredWorks = allWorks.value.filter((work) => work.status === 'ARCHIVED')
  }
  if (selectedScope.value === 'OPEN') {
    filteredWorks = allWorks.value.filter((work) => !['DONE', 'ARCHIVED'].includes(work.status))
    if (selectedOpenPhase.value !== 'ALL') {
      filteredWorks = filteredWorks.filter((work) => work.status === selectedOpenPhase.value)
    }
  }
  return [...filteredWorks].sort((first, second) => {
    const firstAt = workActivityAt(first.updatedAt, first.latestProgressAt)
    const secondAt = workActivityAt(second.updatedAt, second.latestProgressAt)
    return new Date(secondAt).getTime() - new Date(firstAt).getTime()
  })
})

const createMutation = useMutation({
  mutationFn: workApi.createWork,
  onSuccess: async () => {
    await queryClient.invalidateQueries({ queryKey: ['works'] })
    await queryClient.invalidateQueries({ queryKey: ['sidebar', 'stats'] })
    ElMessage.success('議題已發起')
    createDialogVisible.value = false
  },
})

const openCreateDialog = () => {
  createDialogVisible.value = true
}

const openQuickUpdate = (workId: number) => {
  quickUpdateWorkId.value = workId
  quickUpdateVisible.value = true
}

const selectOpenPhase = (phase: OpenPhase) => {
  selectedScope.value = 'OPEN'
  selectedOpenPhase.value = phase
  phasePopoverVisible.value = false
}

const closePhasePopoverOutsideOpenScope = (scope: WorkScope) => {
  if (scope !== 'OPEN') phasePopoverVisible.value = false
}

const openFilterLabel = computed(() => {
  if (selectedOpenPhase.value === 'ALL') return '進行中'
  const phaseLabel = openPhaseOptions.find((option) => option.value === selectedOpenPhase.value)?.label
  return `進行中 · ${phaseLabel ?? ''}`
})

const openFilterCount = computed(() => openPhaseCounts.value[selectedOpenPhase.value])

const openWorkDetail = (workId: number) => {
  router.push({ name: 'WorkDetail', params: { id: workId } })
}

const formatUpdatedAt = (value: string) => {
  const currentYear = formatDate(new Date().toISOString(), 'YYYY')
  const updatedYear = formatDate(value, 'YYYY')
  const date = formatDate(value, updatedYear === currentYear ? 'MM/DD' : 'YYYY/MM/DD')
  return `${date} 更新`
}

const workActivityAt = (updatedAt: string, latestProgressAt: string | null) => {
  if (!latestProgressAt) return updatedAt
  return new Date(latestProgressAt).getTime() > new Date(updatedAt).getTime()
    ? latestProgressAt
    : updatedAt
}

const getHostname = (url: string) => {
  try {
    return new URL(url).hostname.replace(/^www\./, '')
  } catch {
    return '相關連結'
  }
}


const resetCreateForm = () => {
  createForm.title = ''
  createForm.objective = ''
  createForm.description = ''
  createForm.currentAssessment = ''
  createForm.outcomeCriteria = ''
  createForm.externalUrl = ''
  showCreateDetails.value = false
  createFormRef.value?.clearValidate()
}

const submitCreateWork = async () => {
  if (!createFormRef.value) return

  await createFormRef.value.validate((valid) => {
    if (!valid) return

    createMutation.mutate({
      title: createForm.title.trim(),
      objective: createForm.objective?.trim() || null,
      description: createForm.description?.trim() || null,
      currentAssessment: createForm.currentAssessment?.trim() || null,
      outcomeCriteria: createForm.outcomeCriteria?.trim() || null,
      externalUrl: createForm.externalUrl?.trim() || null,
    })
  })
}
</script>

<template>
  <section class="work-list-page">
    <header class="page-header">
      <div>
        <h1 class="page-title">議事廳</h1>
        <p class="page-description">
          把正在反覆思考或推進的事情放上桌，讓相關素材、現實變化與自己的判斷在同一處相遇
        </p>
      </div>
      <div class="page-actions">
        <el-button :icon="Clock" @click="recentDrawerVisible = true">
          近期動態
        </el-button>
        <el-button type="primary" :icon="Plus" @click="openCreateDialog">
          發起議題
        </el-button>
      </div>
    </header>

    <div class="work-dashboard">
      <div class="work-content-surface">
      <div class="work-list-toolbar">
        <div class="work-filter-groups">
          <div class="filter-row">
            <el-radio-group
              v-model="selectedScope"
              class="status-filter"
              aria-label="篩選議題範圍"
              @change="closePhasePopoverOutsideOpenScope"
            >
              <el-popover
                v-model:visible="phasePopoverVisible"
                placement="bottom-start"
                :width="260"
                trigger="click"
              >
                <template #reference>
                  <el-radio-button value="OPEN" class="open-scope-trigger">
                    {{ openFilterLabel }}
                    <span class="scope-count">{{ openFilterCount }}</span>
                    <el-icon class="scope-arrow"><ArrowDown /></el-icon>
                  </el-radio-button>
                </template>
                <div class="phase-popover" aria-label="選擇進行階段">
                  <strong>進行中的議題</strong>
                  <button
                    v-for="option in openPhaseOptions"
                    :key="option.value"
                    type="button"
                    :class="{ active: selectedOpenPhase === option.value }"
                    @click="selectOpenPhase(option.value)"
                  >
                    <span>{{ option.label }}</span>
                    <span>{{ openPhaseCounts[option.value] }}</span>
                  </button>
                </div>
              </el-popover>
              <el-radio-button
                v-for="option in workScopeOptions.filter((option) => option.value !== 'OPEN')"
                :key="option.value"
                :value="option.value"
              >
                {{ option.label }}
                <span class="scope-count">{{ workScopeCounts[option.value] }}</span>
              </el-radio-button>
            </el-radio-group>
          </div>
        </div>
      </div>

      <div v-if="isLoading" class="loading-grid" aria-label="議題載入中">
        <el-card v-for="index in 3" :key="index" shadow="never">
          <el-skeleton :rows="3" animated />
        </el-card>
      </div>

      <el-result
        v-else-if="isError"
        icon="error"
        title="無法載入議題"
        sub-title="請確認網路連線後再試一次"
      >
        <template #extra>
          <el-button type="primary" @click="refetch()">重新載入</el-button>
        </template>
      </el-result>

      <el-empty
        v-else-if="allWorks.length === 0"
        description="目前無事可議。從一件反覆思考、正在推進，或尚未有答案的事情開始。"
      >
        <el-button type="primary" :icon="Plus" @click="openCreateDialog">
          發起第一個議題
        </el-button>
      </el-empty>

      <el-empty
        v-else-if="workList.length === 0"
        :description="`目前沒有${workScopeOptions.find((option) => option.value === selectedScope)?.label ?? ''}議題`"
      >
        <el-button @click="selectedScope = 'ALL'">查看全部議題</el-button>
      </el-empty>

      <div v-else class="work-list">
        <el-card
          v-for="work in workList"
          :key="work.id"
          shadow="never"
          class="work-card"
          role="link"
          tabindex="0"
          :aria-label="`查看議題：${work.title}`"
          @click="openWorkDetail(work.id)"
          @keydown.enter="openWorkDetail(work.id)"
          @keydown.space.prevent="openWorkDetail(work.id)"
        >
          <div class="work-card-layout">
            <section class="work-overview">
              <span class="status-label" :class="`status-${workStatusMeta[work.status].tone}`">
                <span class="status-dot" aria-hidden="true"></span>
                {{ workStatusMeta[work.status].label }}
              </span>
              <h2 class="work-title" :title="work.title">{{ work.title }}</h2>

              <div class="work-overview-field">
                <span>議題焦點</span>
                <p v-if="work.objective || work.description" class="work-description">
                  {{ work.objective || work.description }}
                </p>
                <p v-else class="work-description empty-objective">這個議題最需要回答、釐清或推進什麼？</p>
              </div>

              <div class="work-overview-field criteria-preview">
                <span>結案／重議條件</span>
                <p :class="{ 'empty-objective': !work.outcomeCriteria }">
                  {{ work.outcomeCriteria || '尚未設定' }}
                </p>
              </div>

              <footer class="work-card-footer">
                <div class="material-summary" aria-label="議題素材統計">
                  <span class="material-info">素材 {{ work.candidateCount }}</span>
                  <span class="material-divider" aria-hidden="true">·</span>
                  <span class="material-info used-material-info">已運用 {{ work.usedCount }}</span>
                </div>
                <a
                  v-if="work.externalUrl"
                  :href="work.externalUrl"
                  target="_blank"
                  rel="noopener noreferrer"
                  class="external-link"
                  :title="work.externalUrl"
                  @click.stop
                  @keydown.stop
                >
                  <el-icon><Link /></el-icon>
                  <span>{{ getHostname(work.externalUrl) }}</span>
                </a>
                <span class="work-data-updated">議題資料 · {{ formatUpdatedAt(work.updatedAt) }}</span>
              </footer>
            </section>

            <section class="work-latest-progress">
              <header>
                <span class="card-section-label">最新更新</span>
                <div class="latest-progress-actions">
                  <time
                    v-if="getLatestWorkProgressLead(work) && work.latestProgressAt"
                    :datetime="work.latestProgressAt"
                  >
                    {{ formatUpdatedAt(work.latestProgressAt) }}
                  </time>
                  <button type="button" class="quick-update-button" @click.stop="openQuickUpdate(work.id)">
                    ＋ 提出近況
                  </button>
                </div>
              </header>
              <div v-if="getLatestWorkProgressLead(work)" class="work-progress-fields">
                <section v-if="work.latestProgressChangeSummary">
                  <span>最近有什麼改變？</span>
                  <ExpandableText :content="work.latestProgressChangeSummary" :lines="3" />
                </section>
                <section v-if="work.latestProgressAssessment">
                  <span>現在怎麼看？</span>
                  <ExpandableText :content="work.latestProgressAssessment" :lines="3" />
                </section>
                <section v-if="work.latestProgressNextStep" class="work-next-step">
                  <span>所以接下來呢？</span>
                  <ExpandableText :content="work.latestProgressNextStep" :lines="2" />
                </section>
              </div>
              <p v-else class="no-progress-update">
                還沒有近況。有新的變化、判斷或下一步時，再留下一次快照。
              </p>
            </section>
          </div>
        </el-card>
      </div>
      </div>
    </div>

    <el-drawer
      v-model="recentDrawerVisible"
      title="跨議題近期動態"
      size="min(680px, 100%)"
      destroy-on-close
    >
      <section class="recent-updates-panel" aria-label="最近十二筆跨議題更新">
        <header class="recent-updates-heading">
          <p>最近 12 筆議題更新，依提出時間排序</p>
          <span v-if="recentUpdateList.length" class="recent-count">{{ recentUpdateList.length }}</span>
        </header>

        <div v-if="isRecentUpdatesLoading" class="recent-updates-state">
          <el-skeleton :rows="4" animated />
        </div>
        <div v-else-if="isRecentUpdatesError" class="recent-updates-state">
          <p>目前無法載入最近推進。</p>
          <el-button text type="primary" @click="refetchRecentUpdates()">重新載入</el-button>
        </div>
        <div v-else-if="recentUpdateList.length === 0" class="recent-updates-empty">
          <p>議題出現新變化後，最近的更新會彙整在這裡。</p>
        </div>
        <ol v-else class="recent-update-list">
          <li v-for="update in recentUpdateList" :key="update.id">
            <article class="recent-update-item">
              <button
                type="button"
                class="recent-update-header"
                :aria-label="`查看議題：${update.workTitle}`"
                @click="openWorkDetail(update.workId)"
              >
                <span class="recent-update-work">{{ update.workTitle }}</span>
                <time :datetime="update.createdAt">{{ formatUpdatedAt(update.createdAt) }}</time>
              </button>
              <div class="recent-update-fields">
                <section v-if="update.changeSummary">
                  <span>最近有什麼改變？</span>
                  <ExpandableText :content="update.changeSummary" :lines="3" />
                </section>
                <section v-if="update.assessment">
                  <span>現在怎麼看？</span>
                  <ExpandableText :content="update.assessment" :lines="3" />
                </section>
                <section v-if="update.nextStep" class="recent-next-step">
                  <span>所以接下來呢？</span>
                  <ExpandableText :content="update.nextStep" :lines="2" />
                </section>
              </div>
            </article>
          </li>
        </ol>
      </section>
    </el-drawer>

    <WorkProgressUpdateDialog
      v-if="quickUpdateWorkId !== null"
      v-model="quickUpdateVisible"
      :work-id="quickUpdateWorkId"
      @closed="quickUpdateWorkId = null"
    />

    <el-dialog
      v-model="createDialogVisible"
      title="發起議題"
      width="min(620px, calc(100vw - 32px))"
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
        <p class="create-intro">先把事情放上桌。只填名稱就能建立，其餘內容可以之後再慢慢補上。</p>

        <el-form-item label="議題名稱" prop="title">
          <el-input
            v-model="createForm.title"
            maxlength="255"
            placeholder="例如：我是否應該開始轉職？"
          />
        </el-form-item>

        <el-form-item label="議題焦點" prop="objective">
          <el-input
            v-model="createForm.objective"
            type="textarea"
            :rows="2"
            maxlength="50000"
            placeholder="這個議題最需要回答、釐清或推進什麼？（選填）"
          />
        </el-form-item>

        <button
          type="button"
          class="create-details-toggle"
          :aria-expanded="showCreateDetails"
          @click="showCreateDetails = !showCreateDetails"
        >
          {{ showCreateDetails ? '收起背景與條件' : '＋ 補充背景與條件' }}
        </button>

        <div v-if="showCreateDetails" class="create-details">
          <el-form-item label="背景與脈絡" prop="description">
            <el-input
              v-model="createForm.description"
              type="textarea"
              :rows="3"
              maxlength="50000"
              placeholder="哪些經歷、條件或變化，使這件事值得處理？（選填）"
            />
          </el-form-item>

          <el-form-item label="整體研判" prop="currentAssessment">
            <CurrentAssessmentGuide v-model="createForm.currentAssessment" />
            <el-input
              v-model="createForm.currentAssessment"
              type="textarea"
              :rows="5"
              maxlength="50000"
              placeholder="綜合長期累積的資訊，你如何理解整個議題？（選填）"
            />
          </el-form-item>

          <el-form-item label="結案／重議條件" prop="outcomeCriteria">
            <el-input
              v-model="createForm.outcomeCriteria"
              type="textarea"
              :rows="2"
              maxlength="50000"
              placeholder="何時算有結論？出現什麼變化時需要重新議定？（選填）"
            />
          </el-form-item>

          <el-form-item label="相關連結" prop="externalUrl">
            <el-input
              v-model="createForm.externalUrl"
              maxlength="2048"
              placeholder="外部工作區、文件或成果連結（選填）"
            />
          </el-form-item>
        </div>
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
          發起議題
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.work-list-page {
  width: 100%;
  max-width: 1240px;
  margin: 0 auto;
  box-sizing: border-box;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 24px;
}

.page-title {
  font-size: var(--type-page-title);
  line-height: 1.35;
}

.page-description {
  margin-top: 8px;
  color: var(--el-text-color-secondary);
  font-size: var(--type-ui);
  line-height: var(--leading-ui);
}

.page-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.status-filter {
  flex: 0 0 auto;
}

.work-list-toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: flex-end;
  gap: 16px;
  margin-bottom: 12px;
}

.work-filter-groups {
  display: grid;
  justify-items: end;
  gap: 8px;
}

.filter-row {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}

.scope-arrow {
  margin-left: 5px;
  font-size: var(--type-meta);
}

.phase-popover {
  display: grid;
  gap: 4px;
}

.phase-popover strong {
  padding: 4px 8px 8px;
  color: var(--el-text-color-primary);
  font-size: var(--type-caption);
}

.phase-popover button {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 9px 10px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--el-text-color-regular);
  font: inherit;
  font-size: var(--type-ui);
  text-align: left;
  cursor: pointer;
}

.phase-popover button:hover,
.phase-popover button:focus-visible {
  background: var(--el-fill-color-light);
}

.phase-popover button.active {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-weight: 600;
}

.scope-count {
  margin-left: 5px;
  opacity: 0.7;
  font-size: var(--type-meta);
}

.create-intro {
  margin: -4px 0 18px;
  color: var(--el-text-color-secondary);
  font-size: var(--type-caption);
  line-height: var(--leading-ui);
}

.create-details-toggle {
  margin: 0 0 18px;
  padding: 2px 0;
  border: 0;
  background: transparent;
  color: var(--el-color-primary);
  font: inherit;
  font-size: var(--type-caption);
  cursor: pointer;
}

.create-details-toggle:hover,
.create-details-toggle:focus-visible {
  color: var(--el-color-primary-light-3);
  text-decoration: underline;
}

.create-details {
  padding-top: 2px;
}

.work-dashboard {
  display: block;
  margin-top: 12px;
  padding: 12px;
  background: var(--el-bg-color-page);
}

.work-content-surface {
  min-width: 0;
}

.loading-grid,
.work-list {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 12px;
}

.work-card {
  height: auto;
  border-color: var(--el-border-color-light);
  box-shadow: none;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.work-card:hover,
.work-card:focus-visible {
  transform: translateY(-2px);
  border-color: var(--el-border-color);
  box-shadow: var(--el-box-shadow-lighter);
}

.work-card:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}

.work-card:active {
  transform: translateY(-1px);
}

.work-card :deep(.el-card__body) {
  padding: 24px 26px;
}

.work-card-layout {
  display: grid;
  grid-template-columns: minmax(0, 0.95fr) minmax(0, 1.05fr);
  align-items: stretch;
}

.work-overview {
  display: flex;
  height: 100%;
  min-width: 0;
  padding-right: 24px;
  flex-direction: column;
}

.card-section-label {
  display: block;
  color: var(--el-text-color-primary);
  font-size: var(--type-caption);
  font-weight: 700;
  letter-spacing: 0.03em;
}

.status-label {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: var(--el-text-color-secondary);
  font-size: var(--type-meta);
  font-weight: 600;
  margin-bottom: 12px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--el-text-color-placeholder);
}

.status-exploring .status-dot {
  background: var(--el-color-info);
}

.status-clarified .status-dot {
  background: var(--el-color-warning);
}

.status-advancing .status-dot {
  background: var(--el-color-primary);
}

.status-completed .status-dot {
  background: var(--el-color-success);
}

.work-title {
  display: -webkit-box;
  margin: 0;
  min-width: 0;
  overflow: hidden;
  overflow-wrap: break-word;
  word-break: break-word;
  font-size: var(--type-card-title);
  line-height: var(--leading-section);
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.work-description {
  display: -webkit-box;
  margin: 8px 0 0;
  overflow: hidden;
  color: var(--el-text-color-regular);
  font-size: var(--type-body);
  line-height: var(--leading-body);
  overflow-wrap: break-word;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 5;
}

.work-overview-field {
  margin-top: 14px;
}

.work-overview-field > span {
  display: block;
  margin-bottom: 4px;
  color: var(--el-text-color-placeholder);
  font-size: var(--type-meta);
  font-weight: 600;
}

.work-overview-field .work-description,
.work-overview-field > p {
  margin-top: 0;
}

.criteria-preview > p {
  display: -webkit-box;
  margin-bottom: 0;
  overflow: hidden;
  color: var(--el-text-color-regular);
  font-size: var(--type-caption);
  line-height: var(--leading-ui);
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.empty-objective {
  color: var(--el-text-color-placeholder);
}

.material-summary {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  color: var(--el-text-color-secondary);
  font-size: var(--type-caption);
}

.work-latest-progress {
  min-width: 0;
  padding: 22px 24px;
  border-radius: 10px;
  background: var(--el-fill-color-light);
}

.work-latest-progress > header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.latest-progress-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.work-latest-progress > header time {
  flex: 0 0 auto;
  color: var(--el-text-color-placeholder);
  font-size: var(--type-meta);
}

.work-progress-fields {
  display: grid;
  gap: 12px;
}

.work-progress-fields section > span {
  display: block;
  margin-bottom: 3px;
  color: var(--el-text-color-placeholder);
  font-size: var(--type-meta);
  font-weight: 600;
}

.work-progress-fields :deep(.expandable-content) {
  margin: 0;
  color: var(--el-text-color-regular);
  font-size: var(--type-ui);
  line-height: 1.65;
}

.work-next-step {
  padding: 4px 0 0;
  border: 0;
  background: transparent;
}

.work-next-step > span,
.work-next-step :deep(.expandable-content) {
  color: var(--el-color-primary);
}

.quick-update-button {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--el-color-primary);
  font: inherit;
  font-size: var(--type-caption);
  cursor: pointer;
  white-space: nowrap;
}

.quick-update-button:hover,
.quick-update-button:focus-visible {
  text-decoration: underline;
}

.no-progress-update {
  margin: 4px 0 0;
  color: var(--el-text-color-placeholder);
  font-size: var(--type-caption);
  line-height: var(--leading-ui);
}

.material-info {
  display: inline-flex;
  align-items: baseline;
  gap: 4px;
}

.material-divider {
  color: var(--el-border-color-darker);
}

.work-card-footer {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: auto;
  padding-top: 20px;
  color: var(--el-text-color-placeholder);
  font-size: var(--type-meta);
}

.external-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--el-text-color-placeholder);
  max-width: min(220px, 45%);
  min-width: 0;
  text-decoration: none;
}

.work-data-updated {
  margin-left: auto;
  white-space: nowrap;
}

.external-link span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.external-link:hover {
  color: var(--el-color-primary-light-3);
}

.updated-at {
  flex: 0 0 auto;
  color: var(--el-text-color-placeholder);
  font-size: var(--type-meta);
  text-align: right;
}

.recent-updates-panel {
  min-width: 0;
  padding: 0 24px 28px;
}

.recent-updates-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 15px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.recent-updates-heading p {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: var(--type-caption);
  line-height: var(--leading-ui);
}

.recent-count {
  min-width: 22px;
  padding: 2px 7px;
  border-radius: 999px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-secondary);
  font-size: var(--type-meta);
  text-align: center;
}

.recent-update-list {
  margin: 20px 0 0;
  padding: 0;
  list-style: none;
}

.recent-update-list > li {
  position: relative;
  display: grid;
  grid-template-columns: 14px minmax(0, 1fr);
  gap: 14px;
  padding-bottom: 24px;
}

.recent-update-list > li::before {
  position: absolute;
  top: 12px;
  bottom: 0;
  left: 5px;
  width: 1px;
  background: var(--el-border-color-light);
  content: '';
}

.recent-update-list > li:last-child::before {
  display: none;
}

.recent-update-list > li::after {
  position: absolute;
  top: 4px;
  left: 0;
  z-index: 1;
  width: 11px;
  height: 11px;
  border: 2px solid var(--el-bg-color);
  border-radius: 50%;
  background: var(--el-color-primary-light-5);
  box-shadow: 0 0 0 1px var(--el-color-primary-light-5);
  content: '';
}

.recent-update-item {
  grid-column: 2;
  padding: 16px;
  border-radius: 10px;
  background: var(--el-fill-color-light);
  min-width: 0;
  color: inherit;
  text-align: left;
}

.recent-update-header {
  display: flex;
  width: 100%;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  padding: 0;
  border: 0;
  background: transparent;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.recent-update-header:hover .recent-update-work,
.recent-update-header:focus-visible .recent-update-work {
  color: var(--el-color-primary);
}

.recent-update-header:focus-visible {
  outline: 2px solid var(--el-color-primary-light-5);
  outline-offset: 2px;
}

.recent-update-work {
  overflow: hidden;
  color: var(--el-text-color-primary);
  font-size: var(--type-ui);
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-update-fields {
  display: grid;
  gap: 12px;
  margin-top: 14px;
}

.recent-update-fields section > span {
  display: block;
  margin-bottom: 3px;
  color: var(--el-text-color-placeholder);
  font-size: var(--type-meta);
  font-weight: 600;
}

.recent-update-fields :deep(.expandable-content) {
  margin: 0;
  color: var(--el-text-color-regular);
  font-size: var(--type-caption);
  line-height: 1.65;
}

.recent-next-step {
  padding-top: 2px;
}

.recent-next-step :deep(.expandable-content) {
  color: var(--el-color-primary);
}

.recent-update-header time {
  flex: 0 0 auto;
  color: var(--el-text-color-placeholder);
  font-size: var(--type-meta);
}

.recent-updates-state,
.recent-updates-empty {
  padding: 20px 4px 4px;
  color: var(--el-text-color-secondary);
  font-size: var(--type-caption);
  line-height: var(--leading-ui);
  text-align: center;
}

.recent-updates-state p,
.recent-updates-empty p {
  margin: 0;
}

@media (max-width: 900px) {
  .work-card-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .work-overview {
    padding-right: 0;
  }

  .work-latest-progress {
    margin-top: 20px;
    padding: 18px;
  }
}

@media (max-width: 600px) {
  .work-list-page {
    width: 100%;
    margin: 0;
  }

  .page-header {
    align-items: stretch;
    flex-direction: column;
    gap: 16px;
  }

  .page-actions {
    width: 100%;
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .work-list-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .work-filter-groups,
  .filter-row {
    width: 100%;
    align-items: stretch;
    justify-items: stretch;
  }

  .filter-row {
    flex-direction: column;
  }

  .status-filter {
    display: flex;
    flex-wrap: wrap;
  }

  .page-actions .el-button {
    width: 100%;
  }

  .status-filter :deep(.el-radio-button) {
    flex: 1 1 0;
  }

  .status-filter :deep(.el-radio-button__inner) {
    width: 100%;
    padding-inline: 8px;
  }

  .work-card :deep(.el-card__body) {
    padding: 18px;
  }

  .work-dashboard {
    margin-inline: -16px;
    padding: 12px 16px;
  }

  .external-link {
    max-width: 50%;
  }
}
</style>
