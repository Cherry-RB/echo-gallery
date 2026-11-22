<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import { experimentApi } from '../../utils/api/experimentApi'
import AppDialog from '../AppDialog.vue'
import FieldCounter from '../ui/FieldCounter.vue'
import { apiErrorMessage } from '../../utils/api/errorMessage'
import { useWorkDialogGuard } from '../../composables/useWorkDialogGuard'

const props = defineProps<{ modelValue: boolean; cardId: string | number }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
const queryClient = useQueryClient()
const selectedExperimentId = ref<number | null>(null)
const title = ref('')
const content = ref('')
const error = ref('')
const shell = ref<InstanceType<typeof AppDialog>>()
useWorkDialogGuard(shell, () => props.modelValue)
const dialogVisible = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) })
const experimentsQuery = useInfiniteQuery({
  queryKey: ['experiments', 'record-card-placement'],
  queryFn: ({ pageParam }) => experimentApi.getExperiments('all', pageParam, 20),
  initialPageParam: 0,
  enabled: dialogVisible,
  getNextPageParam: lastPage => lastPage.page + 1 < lastPage.totalPages ? lastPage.page + 1 : undefined,
})
const experiments = computed(() => experimentsQuery.data.value?.pages.flatMap(page => page.content) ?? [])
watch(dialogVisible, visible => {
  if (!visible) { selectedExperimentId.value = null; title.value = ''; content.value = ''; error.value = '' }
})
const today = () => new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Taipei' })
const saveMutation = useMutation({
  mutationFn: async () => {
    const experimentId = selectedExperimentId.value!
    await experimentApi.createRecordFromCard(experimentId, Number(props.cardId), {
      title: title.value.trim() || `收藏卡片 #${props.cardId}`,
      content: content.value.trim() || null,
      practiceText: null,
      recordedOn: today(),
      understandingNode: false,
      methodCandidate: false,
      highlight: 'GENERAL',
      requestId: crypto.randomUUID(),
    })
    return experimentId
  },
  onSuccess: async experimentId => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['experiment-records', experimentId] }),
      queryClient.invalidateQueries({ queryKey: ['card-record-references', String(props.cardId)] }),
      queryClient.invalidateQueries({ queryKey: ['experiments'] }),
      queryClient.invalidateQueries({ queryKey: ['overview'] }),
    ])
    ElMessage.success('已建立探索紀錄並加入收藏卡片')
    dialogVisible.value = false
  },
  onError: reason => { error.value = apiErrorMessage(reason, '無法建立探索紀錄，輸入內容已保留。') },
})
</script>

<template>
  <AppDialog ref="shell" v-model="dialogVisible" :busy="saveMutation.isPending.value" :draft="[selectedExperimentId, title, content]" title="記到實驗" width="min(560px, calc(100vw - 32px))" scroll-body append-to-body destroy-on-close>
    <p class="dialog-description app-dialog-intro">選擇實驗主題後，會建立一筆探索紀錄，並保留這張收藏卡片作為參考資料。卡片內容不會被修改。</p>
    <el-form label-position="top" :disabled="saveMutation.isPending.value">
      <el-form-item label="實驗主題" required>
        <el-select v-model="selectedExperimentId" class="full-select" filterable placeholder="選擇實驗場" :loading="experimentsQuery.isLoading.value">
          <el-option v-for="experiment in experiments" :key="experiment.id" :label="experiment.isArchived ? `${experiment.title}（已封存）` : experiment.title" :value="experiment.id" />
        </el-select>
        <el-button v-if="experimentsQuery.hasNextPage.value" text :loading="experimentsQuery.isFetchingNextPage.value" @click="experimentsQuery.fetchNextPage()">載入更多實驗場</el-button>
        <p v-if="experimentsQuery.isError.value" class="field-hint">無法載入實驗主題。<el-button text @click="experimentsQuery.refetch()">重試</el-button></p>
        <p v-else-if="!experimentsQuery.isPending.value && !experiments.length" class="field-hint">尚無實驗主題。<router-link to="/experiments">前往實驗場</router-link></p>
      </el-form-item>
      <el-form-item label="標題">
        <el-input v-model="title" maxlength="255" placeholder="例如：從這張卡片得到的線索" />
        <FieldCounter :value="title" :limit="255" />
      </el-form-item>
      <el-form-item label="內容">
        <el-input v-model="content" type="textarea" :rows="4" maxlength="10000" placeholder="想留下什麼觀察或下一步？" />
        <FieldCounter :value="content" :limit="10000" />
      </el-form-item>
    </el-form>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <template #footer="{ requestClose }">
      <el-button :disabled="saveMutation.isPending.value" @click="requestClose">取消</el-button>
      <el-button type="primary" :loading="saveMutation.isPending.value" :disabled="selectedExperimentId === null" @click="saveMutation.mutate()">建立紀錄並加入</el-button>
    </template>
  </AppDialog>
</template>

<style scoped>
.full-select { width: 100%; }
.el-form :deep(.el-form-item) { margin-bottom: var(--space-md); }
.field-hint { margin: var(--space-xs) 0 0; color: var(--el-text-color-secondary); font-size: var(--type-meta); }
</style>
