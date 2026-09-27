<script setup lang="ts">
import { computed } from 'vue'
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import type { ExperimentDto } from '../../types/experiment'
import { experimentApi } from '../../utils/api/experimentApi'
import AppDialog from '../AppDialog.vue'

const props = defineProps<{
  modelValue: boolean
  cardId: string | number
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const queryClient = useQueryClient()
const dialogVisible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const { data: cardExperiments, isLoading: areRelationsLoading } = useQuery({
  queryKey: computed(() => ['card-experiments', String(props.cardId)]),
  queryFn: () => experimentApi.getCardExperiments(props.cardId),
  enabled: computed(() => props.modelValue),
})

const experimentsQuery = useInfiniteQuery({
  queryKey: ['experiments', 'quick-card-placement'],
  queryFn: ({ pageParam }) => experimentApi.getExperiments(false, pageParam, 20),
  initialPageParam: 0,
  enabled: computed(() => props.modelValue),
  getNextPageParam: lastPage => lastPage.page + 1 < lastPage.totalPages
    ? lastPage.page + 1
    : undefined,
})

const linkedExperimentIds = computed(() =>
  new Set((cardExperiments.value ?? []).map(relation => String(relation.experimentId))),
)

const availableExperiments = computed(() =>
  (experimentsQuery.data.value?.pages.flatMap(page => page.content) ?? [])
    .filter(experiment => !linkedExperimentIds.value.has(String(experiment.id))),
)

const addExperimentMutation = useMutation({
  mutationFn: (experiment: ExperimentDto) => experimentApi.addExperimentCard(experiment.id, {
    cardId: Number(props.cardId),
    stage: 'SEED',
  }),
  onSuccess: async (_relation, experiment) => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['experiments'] }),
      queryClient.invalidateQueries({ queryKey: ['experiment', experiment.id] }),
      queryClient.invalidateQueries({ queryKey: ['experiment-cards', experiment.id] }),
      queryClient.invalidateQueries({ queryKey: ['experiment-source-cards', experiment.id] }),
      queryClient.invalidateQueries({ queryKey: ['card-experiments', String(props.cardId)] }),
      queryClient.invalidateQueries({ queryKey: ['overview'] }),
    ])
    ElMessage.success(`已將卡片種入「${experiment.title}」的種子土壤`)
    dialogVisible.value = false
  },
  onError: () => {
    ElMessage.error('種入實驗場失敗，請稍後再試')
  },
})
</script>

<template>
  <AppDialog
    v-model="dialogVisible"
    title="將卡片放入實驗場"
    width="min(560px, calc(100vw - 32px))"
    append-to-body
    destroy-on-close
  >
    <p class="dialog-description">
      選擇一個正在探索的實驗場，這張卡會先放入種子土壤；階段與備註都可以之後再調整。
    </p>

    <el-skeleton v-if="experimentsQuery.isLoading.value || areRelationsLoading" :rows="5" animated />

    <el-result
      v-else-if="experimentsQuery.isError.value"
      icon="warning"
      title="無法載入實驗場"
    />

    <el-empty
      v-else-if="availableExperiments.length === 0"
      :image-size="72"
      description="目前沒有可放入的實驗場；可以先到實驗場建立一個想持續探索的方向。"
    />

    <div v-else class="experiment-option-list">
      <article v-for="experiment in availableExperiments" :key="experiment.id" class="experiment-option">
        <div class="experiment-option-content">
          <strong>{{ experiment.title }}</strong>
          <span v-if="experiment.currentTry">目前想試：{{ experiment.currentTry }}</span>
          <span v-else>尚未留下目前試法</span>
        </div>
        <el-button
          type="primary"
          plain
          size="small"
          :loading="addExperimentMutation.isPending.value"
          @click="addExperimentMutation.mutate(experiment)"
        >
          種入
        </el-button>
      </article>
      <div v-if="experimentsQuery.hasNextPage.value" class="load-more-row">
        <el-button :loading="experimentsQuery.isFetchingNextPage.value" @click="experimentsQuery.fetchNextPage()">載入更多</el-button>
      </div>
    </div>
  </AppDialog>
</template>

<style scoped>
.dialog-description {
  margin: 0 0 18px;
  color: var(--el-text-color-secondary);
  font-size: var(--type-ui);
  line-height: var(--leading-ui);
}

.experiment-option-list {
  min-height: 0;
}

.experiment-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 4px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.experiment-option-content {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 5px;
}

.experiment-option-content strong,
.experiment-option-content span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.experiment-option-content strong {
  line-height: 1.5;
}

.experiment-option-content span {
  color: var(--el-text-color-secondary);
  font-size: var(--type-meta);
}

.load-more-row {
  display: flex;
  justify-content: center;
  padding: 16px 0 4px;
}
</style>
