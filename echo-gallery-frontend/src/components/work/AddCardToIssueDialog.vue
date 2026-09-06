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
  IDEA: '探索中',
  DRAFT: '已釐清',
  ACTIVE: '推進中',
  DONE: '已完成',
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
    <p class="dialog-description">
      讓這張卡片進入一個正在思考的議題，成為後續研判、創作或行動的參考。已加入與已封存的議題不會重複顯示。
    </p>

    <el-skeleton v-if="areIssuesLoading || areRelationsLoading" :rows="5" animated />

    <el-result
      v-else-if="areIssuesError"
      icon="warning"
      title="無法載入議題"
    />

    <el-empty
      v-else-if="availableIssues.length === 0"
      :image-size="72"
      description="目前沒有可加入的議題；可以先到議事廳發起一件正在思考的事情。"
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
  font-size: var(--type-meta);
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
  font-size: var(--type-meta);
}
</style>
