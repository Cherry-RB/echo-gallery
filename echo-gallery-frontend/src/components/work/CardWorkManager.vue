<script setup lang="ts">
import { computed, ref } from 'vue'
import { FolderOpened, Plus } from '@element-plus/icons-vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import type { WorkCardStatus, WorkStatus, WorkSummary } from '../../types/work'
import { workApi } from '../../utils/api/workApi'

const props = defineProps<{ cardId: string }>()
const router = useRouter()
const queryClient = useQueryClient()
const addWorkDialogVisible = ref(false)

type StatusTagType = 'primary' | 'success' | 'warning' | 'info'

const relationStatusMeta: Record<WorkCardStatus, { label: string; type: StatusTagType }> = {
  CANDIDATE: { label: '候選素材', type: 'info' },
  USED: { label: '已採用', type: 'success' },
}

const workStatusMeta: Record<WorkStatus, string> = {
  IDEA: '構想',
  DRAFT: '草稿',
  ACTIVE: '進行中',
  DONE: '已完成',
  ARCHIVED: '已封存',
}

const {
  data: cardWorks,
  isLoading: areCardWorksLoading,
  isError: areCardWorksError,
  refetch: refetchCardWorks,
} = useQuery({
  queryKey: computed(() => ['cardWorks', String(props.cardId)]),
  queryFn: () => workApi.getCardWorks(props.cardId),
})

const {
  data: works,
  isLoading: areWorksLoading,
  isError: areWorksError,
} = useQuery({
  queryKey: ['works'],
  queryFn: workApi.getWorks,
  enabled: computed(() => addWorkDialogVisible.value),
})

const linkedWorkIds = computed(() =>
  new Set((cardWorks.value ?? []).map((relation) => String(relation.workId))),
)

const availableWorks = computed(() =>
  (works.value ?? []).filter((work) =>
    work.status !== 'ARCHIVED' && !linkedWorkIds.value.has(String(work.id)),
  ),
)

const addWorkMutation = useMutation({
  mutationFn: (work: WorkSummary) =>
    workApi.addWorkCard(work.id, { cardId: Number(props.cardId) }),
  onSuccess: async (_relation, work) => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['cardWorks', String(props.cardId)] }),
      queryClient.invalidateQueries({ queryKey: ['workCards', String(work.id)] }),
      queryClient.invalidateQueries({ queryKey: ['works'] }),
    ])
    ElMessage.success(`已加入「${work.title}」`)
    addWorkDialogVisible.value = false
  },
})

const openWork = (workId: number) => {
  router.push({ name: 'WorkDetail', params: { id: workId } })
}
</script>

<template>
  <el-card class="sidebar-card work-relations-card">
    <header class="work-card-header">
      <div class="header-title">
        <el-icon><FolderOpened /></el-icon>
        <span>所在作品</span>
      </div>
      <el-button type="primary" link size="small" :icon="Plus" @click="addWorkDialogVisible = true">
        加入作品
      </el-button>
    </header>

    <el-skeleton v-if="areCardWorksLoading" :rows="2" animated />

    <el-alert
      v-else-if="areCardWorksError"
      title="無法載入作品關聯"
      type="warning"
      :closable="false"
      show-icon
    >
      <template #default>
        <el-button link type="primary" @click="refetchCardWorks()">重新載入</el-button>
      </template>
    </el-alert>

    <el-empty
      v-else-if="!cardWorks?.length"
      :image-size="56"
      description="尚未加入任何作品"
    />

    <div v-else class="relation-list">
      <button
        v-for="relation in cardWorks"
        :key="relation.workId"
        type="button"
        class="relation-item"
        @click="openWork(relation.workId)"
      >
        <span class="relation-title">{{ relation.workTitle }}</span>
        <span class="relation-metadata">
          <el-tag
            :type="relationStatusMeta[relation.status].type"
            size="small"
            effect="plain"
          >
            {{ relationStatusMeta[relation.status].label }}
          </el-tag>
          <span v-if="relation.workStatus === 'ARCHIVED'" class="archived-label">
            {{ workStatusMeta[relation.workStatus] }}
          </span>
        </span>
        <span v-if="relation.note" class="relation-note">{{ relation.note }}</span>
      </button>
    </div>

    <el-dialog
      v-model="addWorkDialogVisible"
      title="將卡片加入作品"
      width="min(560px, calc(100vw - 32px))"
      append-to-body
      destroy-on-close
    >
      <p class="dialog-description">已加入的作品與封存作品不會出現在選項中。</p>

      <el-skeleton v-if="areWorksLoading" :rows="5" animated />

      <el-result
        v-else-if="areWorksError"
        icon="warning"
        title="無法載入作品"
      />

      <el-empty
        v-else-if="availableWorks.length === 0"
        :image-size="72"
        description="沒有其他可加入的作品"
      />

      <div v-else class="work-option-list">
        <div v-for="work in availableWorks" :key="work.id" class="work-option">
          <div class="work-option-content">
            <strong>{{ work.title }}</strong>
            <span>{{ workStatusMeta[work.status] }}</span>
          </div>
          <el-button
            type="primary"
            plain
            size="small"
            :loading="addWorkMutation.isPending.value"
            @click="addWorkMutation.mutate(work)"
          >
            加入
          </el-button>
        </div>
      </div>
    </el-dialog>
  </el-card>
</template>

<style scoped>
.sidebar-card {
  margin-bottom: 16px;
}

.work-card-header,
.header-title,
.relation-metadata,
.work-option {
  display: flex;
  align-items: center;
}

.work-card-header,
.work-option {
  justify-content: space-between;
  gap: 12px;
}

.work-card-header {
  margin-bottom: 10px;
}

.header-title {
  gap: 8px;
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 600;
}

.relation-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.relation-item {
  display: flex;
  width: 100%;
  padding: 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-fill-color-extra-light);
  flex-direction: column;
  align-items: flex-start;
  gap: 7px;
  color: inherit;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.2s, background-color 0.2s;
}

.relation-item:hover,
.relation-item:focus-visible {
  border-color: var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
}

.relation-title {
  max-width: 100%;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.relation-metadata {
  gap: 8px;
}

.archived-label,
.relation-note,
.dialog-description,
.work-option-content span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.relation-note {
  line-height: 1.5;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.dialog-description {
  margin: -8px 0 14px;
}

.work-option-list {
  max-height: min(56vh, 480px);
  overflow-y: auto;
}

.work-option {
  padding: 12px 4px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.work-option-content {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 5px;
}

.work-option-content strong {
  line-height: 1.5;
  overflow-wrap: anywhere;
}
</style>
