<script setup lang="ts">
import { computed, ref } from 'vue'
import { FolderOpened, Plus } from '@element-plus/icons-vue'
import { useQuery } from '@tanstack/vue-query'
import { useRouter } from 'vue-router'
import type { WorkCardStatus, WorkStatus } from '../../types/work'
import { workApi } from '../../utils/api/workApi'
import AddCardToIssueDialog from './AddCardToIssueDialog.vue'

const props = defineProps<{ cardId: string }>()
const router = useRouter()
const addIssueDialogVisible = ref(false)

type StatusTagType = 'primary' | 'success' | 'warning' | 'info'

const relationStatusMeta: Record<WorkCardStatus, { label: string; type: StatusTagType }> = {
  CANDIDATE: { label: '素材池', type: 'info' },
  USED: { label: '已運用', type: 'success' },
}

const workStatusMeta: Record<WorkStatus, string> = {
  IDEA: '構想中',
  DRAFT: '整理中',
  ACTIVE: '議事中',
  DONE: '已結案',
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

const openWork = (workId: number) => {
  router.push({ name: 'WorkDetail', params: { id: workId } })
}
</script>

<template>
  <el-card class="sidebar-card work-relations-card">
    <header class="work-card-header">
      <div class="header-title">
        <el-icon><FolderOpened /></el-icon>
        <span>所在議題</span>
      </div>
      <el-button type="primary" link size="small" :icon="Plus" @click="addIssueDialogVisible = true">
        加入議題
      </el-button>
    </header>

    <el-skeleton v-if="areCardWorksLoading" :rows="2" animated />

    <el-alert
      v-else-if="areCardWorksError"
      title="無法載入議題關聯"
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
      description="尚未加入任何議題"
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

    <AddCardToIssueDialog
      v-if="addIssueDialogVisible"
      v-model="addIssueDialogVisible"
      :card-id="props.cardId"
    />
  </el-card>
</template>

<style scoped>
.sidebar-card {
  margin-bottom: 16px;
}

.work-card-header,
.header-title,
.relation-metadata {
  display: flex;
  align-items: center;
}

.work-card-header {
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
.relation-note {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.relation-note {
  line-height: 1.5;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

</style>
