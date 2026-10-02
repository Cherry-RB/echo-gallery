<script setup lang="ts">
import { computed, ref } from 'vue'
import { FolderOpened, Plus } from '@element-plus/icons-vue'
import { useQuery } from '@tanstack/vue-query'
import { useRouter } from 'vue-router'
import type { IssueCardStatus } from '../../types/issue'
import { issueApi } from '../../utils/api/issueApi'
import { issueStatusMeta } from '../../utils/issueStatus'
import AddCardToIssueDialog from './AddCardToIssueDialog.vue'

const props = defineProps<{ cardId: string }>()
const router = useRouter()
const addIssueDialogVisible = ref(false)

type StatusTagType = 'primary' | 'success' | 'warning' | 'info'

const relationStatusMeta: Record<IssueCardStatus, { label: string; type: StatusTagType }> = {
  CANDIDATE: { label: '素材池', type: 'info' },
  USED: { label: '已運用', type: 'success' },
}

const {
  data: cardIssues,
  isLoading: areCardIssuesLoading,
  isError: areCardIssuesError,
  refetch: refetchCardIssues,
} = useQuery({
  queryKey: computed(() => ['cardIssues', String(props.cardId)]),
  queryFn: () => issueApi.getCardIssues(props.cardId),
})

const openIssue = (issueId: number) => {
  router.push({ name: 'IssueDetail', params: { id: issueId } })
}
</script>

<template>
  <el-card class="sidebar-card issue-relations-card">
    <header class="issue-card-header">
      <div class="header-title">
        <el-icon><FolderOpened /></el-icon>
        <span>所在議題</span>
      </div>
      <el-button type="primary" link size="small" :icon="Plus" @click="addIssueDialogVisible = true">
        加入議題
      </el-button>
    </header>

    <el-skeleton v-if="areCardIssuesLoading" :rows="2" animated />

    <el-alert
      v-else-if="areCardIssuesError"
      title="無法載入議題關聯"
      type="warning"
      :closable="false"
      show-icon
    >
      <template #default>
        <el-button link type="primary" @click="refetchCardIssues()">重新載入</el-button>
      </template>
    </el-alert>

    <el-empty
      v-else-if="!cardIssues?.length"
      :image-size="56"
      description="這張卡片還沒有進入任何議題"
    />

    <div v-else class="relation-list">
      <button
        v-for="relation in cardIssues"
        :key="relation.issueId"
        type="button"
        class="relation-item"
        @click="openIssue(relation.issueId)"
      >
        <span class="relation-title">{{ relation.issueTitle }}</span>
        <span class="relation-metadata">
          <el-tag
            :type="relationStatusMeta[relation.status].type"
            size="small"
            effect="plain"
          >
            {{ relationStatusMeta[relation.status].label }}
          </el-tag>
          <span v-if="relation.issueStatus === 'ARCHIVED'" class="archived-label">
            {{ issueStatusMeta[relation.issueStatus].label }}
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

.issue-card-header,
.header-title,
.relation-metadata {
  display: flex;
  align-items: center;
}

.issue-card-header {
  justify-content: space-between;
  gap: 12px;
}

.issue-card-header {
  margin-bottom: 10px;
}

.header-title {
  gap: 8px;
  color: var(--el-text-color-primary);
  font-size: var(--type-ui);
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
  background: var(--surface-subtle);
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
  font-size: var(--type-caption);
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
  font-size: var(--type-meta);
}

.relation-note {
  line-height: 1.5;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

</style>
