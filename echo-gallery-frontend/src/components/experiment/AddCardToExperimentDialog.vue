<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import { experimentApi } from '../../utils/api/experimentApi'
import AppDialog from '../AppDialog.vue'
import FieldCounter from '../ui/FieldCounter.vue'
import PickerOption from '../ui/PickerOption.vue'
import { apiErrorMessage } from '../../utils/api/errorMessage'
import { useWorkDialogGuard } from '../../composables/useWorkDialogGuard'

const props = defineProps<{
  modelValue: boolean
  cardId: string | number
}>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
const queryClient = useQueryClient()
const selectedExperimentId = ref<number | null>(null)
const selectedThreadId = ref<number | null>(null)
const creating = ref(false)
const threadName = ref('')
const threadKeyword = ref('')
const error = ref('')
const shell = ref<InstanceType<typeof AppDialog>>()
useWorkDialogGuard(shell, () => props.modelValue)
const dialogVisible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value),
})

const experimentsQuery = useInfiniteQuery({
  queryKey: ['experiments', 'thread-card-placement'],
  queryFn: ({ pageParam }) => experimentApi.getExperiments(false, pageParam, 20),
  initialPageParam: 0,
  enabled: dialogVisible,
  getNextPageParam: lastPage => lastPage.page + 1 < lastPage.totalPages ? lastPage.page + 1 : undefined,
})
const experiments = computed(() => experimentsQuery.data.value?.pages.flatMap(page => page.content) ?? [])
const threadsQuery = useQuery({
  queryKey: computed(() => ['experiment-threads', selectedExperimentId.value]),
  queryFn: () => experimentApi.getThreads(selectedExperimentId.value!),
  enabled: computed(() => dialogVisible.value && selectedExperimentId.value !== null),
})
const availableThreads = computed(() => (threadsQuery.data.value ?? []).filter(thread =>
  !thread.cards.some(link => link.role === 'MATERIAL' && String(link.cardId) === String(props.cardId)),
))
const filteredThreads = computed(() => {
  const keyword = threadKeyword.value.trim().toLocaleLowerCase()
  return (threadsQuery.data.value ?? []).filter(thread => !keyword || thread.name.toLocaleLowerCase().includes(keyword))
})
const isAvailable = (threadId: number) => availableThreads.value.some(thread => thread.id === threadId)
watch(selectedExperimentId, () => { selectedThreadId.value = null; creating.value = false; threadKeyword.value = ''; error.value = '' })
watch(dialogVisible, visible => {
  if (!visible) {
    selectedExperimentId.value = null
    selectedThreadId.value = null
    creating.value = false
    threadName.value = ''
    threadKeyword.value = ''
    error.value = ''
  }
})

const addMutation = useMutation({
  mutationFn: () => creating.value
    ? experimentApi.createThreadWithMaterial(selectedExperimentId.value!, threadName.value.trim(), Number(props.cardId))
    : experimentApi.addThreadCard(selectedExperimentId.value!, selectedThreadId.value!, Number(props.cardId), 'MATERIAL'),
  onSuccess: async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['experiment-threads', selectedExperimentId.value] }),
      queryClient.invalidateQueries({ queryKey: ['experiment', selectedExperimentId.value] }),
      queryClient.invalidateQueries({ queryKey: ['experiments'] }),
      queryClient.invalidateQueries({ queryKey: ['card-exploration-threads'] }),
      queryClient.invalidateQueries({ queryKey: ['card-experiments', String(props.cardId)] }),
      queryClient.invalidateQueries({ queryKey: ['overview'] }),
    ])
    ElMessage.success('卡片已加入探索線的材料與線索')
    dialogVisible.value = false
  },
  onError: reason => { error.value = apiErrorMessage(reason, '無法加入探索線，選擇內容已保留。') },
})
</script>

<template>
  <AppDialog
    ref="shell"
    v-model="dialogVisible"
    :busy="addMutation.isPending.value"
    :draft="[selectedExperimentId, selectedThreadId, creating, threadName]"
    title="加入探索線"
    width="min(560px, calc(100vw - 32px))"
    scroll-body
    append-to-body
    destroy-on-close
  >
    <p class="dialog-description app-dialog-intro">選擇這張卡片可以支持的探索方向。卡片內容不會被修改。</p>
    <el-form label-position="top" :disabled="addMutation.isPending.value">
      <el-form-item label="實驗主題" required>
        <el-select v-model="selectedExperimentId" class="full-select" filterable placeholder="選擇實驗場" :loading="experimentsQuery.isLoading.value">
          <el-option v-for="experiment in experiments" :key="experiment.id" :label="experiment.title" :value="experiment.id" />
        </el-select>
        <el-button v-if="experimentsQuery.hasNextPage.value" text :loading="experimentsQuery.isFetchingNextPage.value" @click="experimentsQuery.fetchNextPage()">載入更多實驗場</el-button>
        <p v-if="experimentsQuery.isError.value" class="field-hint">無法載入實驗主題。<el-button text @click="experimentsQuery.refetch()">重試</el-button></p>
        <p v-else-if="!experimentsQuery.isPending.value && !experiments.length" class="field-hint">尚無實驗主題。<router-link to="/experiments">前往實驗場</router-link></p>
        <p v-if="experimentsQuery.isFetchNextPageError.value" class="field-hint">後續實驗主題載入失敗，請再次載入。</p>
      </el-form-item>
      <el-form-item label="探索線" required>
        <template #label><div class="field-label"><span>探索線</span><el-button v-if="selectedExperimentId !== null" type="primary" link size="small" :disabled="addMutation.isPending.value" @click.stop.prevent="creating = !creating">{{ creating ? '選擇既有探索線' : '建立探索線' }}</el-button></div></template>
        <template v-if="!creating">
          <el-input v-model="threadKeyword" class="full-select" clearable placeholder="搜尋探索線名稱" :disabled="selectedExperimentId === null || addMutation.isPending.value" aria-label="搜尋探索線名稱" />
          <div v-if="selectedExperimentId !== null && !threadsQuery.isLoading.value && filteredThreads.length" class="thread-options" role="listbox" aria-label="探索線候選清單">
            <PickerOption v-for="thread in filteredThreads" :key="thread.id" :title="thread.name" :selected="selectedThreadId === thread.id" :disabled="addMutation.isPending.value || !isAvailable(thread.id)" @select="selectedThreadId = thread.id">
              <span>{{ isAvailable(thread.id) ? '可加入材料與線索' : '已加入' }}</span>
            </PickerOption>
          </div>
        </template>
        <template v-else><el-input v-model="threadName" maxlength="255" placeholder="新探索線名稱" aria-label="新探索線名稱" /><FieldCounter :value="threadName" :limit="255" /></template>
        <p v-if="selectedExperimentId !== null && threadsQuery.isError.value" class="field-hint">無法載入探索線。<el-button text @click="threadsQuery.refetch()">重試</el-button></p>
        <p v-else-if="selectedExperimentId !== null && threadsQuery.isSuccess.value && !threadsQuery.data.value?.length && !creating" class="field-hint">尚無探索線，可直接建立。</p>
        <p v-else-if="selectedExperimentId !== null && threadsQuery.isSuccess.value && !filteredThreads.length && !creating" class="field-hint">找不到符合的探索線。</p>
      </el-form-item>
    </el-form>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <template #footer="{ requestClose }">
      <el-button :disabled="addMutation.isPending.value" @click="requestClose">取消</el-button>
      <el-button type="primary" :loading="addMutation.isPending.value" :disabled="selectedExperimentId === null || (creating ? !threadName.trim() : selectedThreadId === null)" @click="addMutation.mutate()">{{ creating ? '建立並加入' : '加入探索線' }}</el-button>
    </template>
  </AppDialog>
</template>

<style scoped>
.full-select { width: 100%; }
.el-form :deep(.el-form-item) { margin-bottom: var(--space-md); }
.el-form :deep(.el-form-item__label) { display: flex; align-items: center; width: 100%; height: auto; margin-bottom: var(--space-xs); padding-bottom: 0; }
.field-label { display: flex; flex: 1; min-width: 0; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--space-xs); }
.field-hint { margin: var(--space-xs) 0 0; color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.thread-options { display: grid; gap: var(--space-xs); max-height: min(320px, 40dvh); margin-top: var(--space-sm); overflow-y: auto; }
</style>
