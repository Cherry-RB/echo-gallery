<script setup lang="ts">
import { computed } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import type { WorkStatus, WorkSummary } from '../../types/work'
import { workApi } from '../../utils/api/workApi'

const props = defineProps<{
  modelValue: boolean
  cardId: string | number
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const queryClient = useQueryClient()

const workStatusMeta: Record<WorkStatus, string> = {
  IDEA: '構想中',
  DRAFT: '整理中',
  ACTIVE: '議事中',
  DONE: '已結案',
  ARCHIVED: '已封存',
}

const dialogVisible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const { data: cardIssues, isLoading: areRelationsLoading } = useQuery({
  queryKey: computed(() => ['cardWorks', String(props.cardId)]),
  queryFn: () => workApi.getCardWorks(props.cardId),
  enabled: computed(() => props.modelValue),
})

const {
  data: issues,
  isLoading: areIssuesLoading,
  isError: areIssuesError,
} = useQuery({
  queryKey: ['works'],
  queryFn: workApi.getWorks,
  enabled: computed(() => props.modelValue),
})

const linkedIssueIds = computed(() =>
  new Set((cardIssues.value ?? []).map((relation) => String(relation.workId))),
)

const availableIssues = computed(() =>
  (issues.value ?? []).filter((issue) =>
    issue.status !== 'ARCHIVED' && !linkedIssueIds.value.has(String(issue.id)),
  ),
)

const addIssueMutation = useMutation({
  mutationFn: (issue: WorkSummary) =>
    workApi.addWorkCard(issue.id, { cardId: Number(props.cardId) }),
  onSuccess: async (_relation, issue) => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['cardWorks', String(props.cardId)] }),
      queryClient.invalidateQueries({ queryKey: ['workCards', String(issue.id)] }),
      queryClient.invalidateQueries({ queryKey: ['works'] }),
    ])
    ElMessage.success(`已加入議題「${issue.title}」`)
    dialogVisible.value = false
  },
  onError: () => {
    ElMessage.error('加入議題失敗，請稍後再試')
  },
})
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    title="將卡片加入議題"
    width="min(560px, calc(100vw - 32px))"
    append-to-body
    destroy-on-close
  >
    <p class="dialog-description">已加入與已封存的議題不會出現在選項中。</p>

    <el-skeleton v-if="areIssuesLoading || areRelationsLoading" :rows="5" animated />

    <el-result
      v-else-if="areIssuesError"
      icon="warning"
      title="無法載入議題"
    />

    <el-empty
      v-else-if="availableIssues.length === 0"
      :image-size="72"
      description="沒有其他可加入的議題"
    />

    <div v-else class="issue-option-list">
      <div v-for="issue in availableIssues" :key="issue.id" class="issue-option">
        <div class="issue-option-content">
          <strong>{{ issue.title }}</strong>
          <span>{{ workStatusMeta[issue.status] }}</span>
        </div>
        <el-button
          type="primary"
          plain
          size="small"
          :loading="addIssueMutation.isPending.value"
          @click="addIssueMutation.mutate(issue)"
        >
          加入
        </el-button>
      </div>
    </div>
  </el-dialog>
</template>

<style scoped>
.dialog-description {
  margin: -8px 0 14px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.issue-option-list {
  max-height: min(56vh, 480px);
  overflow-y: auto;
}

.issue-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 4px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.issue-option-content {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 5px;
}

.issue-option-content strong {
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.issue-option-content span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
