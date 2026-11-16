<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useInfiniteQuery, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import AppDialog from '../AppDialog.vue'
import PickerWorkspace from '../ui/PickerWorkspace.vue'
import PickerOption from '../ui/PickerOption.vue'
import type { WorkSummary } from '../../types/work'
import { workApi } from '../../utils/api/workApi'
import { workStages } from '../../types/work'
import { errorMessage } from '../../utils/treasure'

const props = defineProps<{ modelValue: boolean; treasureId: number }>()
const emit = defineEmits<{ 'update:modelValue': [boolean] }>()
const keyword = ref('')
const selected = ref<WorkSummary[]>([])
const originalIds = ref<number[] | null>(null)
const saving = ref(false)
const error = ref('')
const queryClient = useQueryClient()
const linked = useQuery({ queryKey: computed(() => ['works', 'treasure', props.treasureId]), queryFn: () => workApi.fromTreasure(props.treasureId), enabled: computed(() => props.modelValue) })
watch(() => linked.data.value, value => { if (value && originalIds.value === null) { selected.value = [...value]; originalIds.value = value.map(work => work.id) } }, { immediate: true })
const candidates = useInfiniteQuery({
  queryKey: computed(() => ['works', 'treasure-candidates', props.treasureId, keyword.value.trim()]),
  initialPageParam: 0,
  queryFn: ({ pageParam }) => workApi.treasureCandidates(props.treasureId, { keyword: keyword.value.trim() || undefined, page: pageParam, size: 20 }),
  getNextPageParam: (lastPage, _pages, lastPageParam) => lastPageParam + 1 < lastPage.totalPages ? lastPageParam + 1 : undefined,
  enabled: computed(() => props.modelValue),
})
const options = computed(() => candidates.data.value?.pages.flatMap(page => page.content) ?? [])
const isSelected = (work: WorkSummary) => selected.value.some(item => item.id === work.id)
const toggle = (work: WorkSummary) => {
  if (originalIds.value === null || saving.value) return
  if (isSelected(work)) selected.value = selected.value.filter(item => item.id !== work.id)
  else if (selected.value.length < 100) selected.value = [...selected.value, work]
}
const remove = (id: number) => { selected.value = selected.value.filter(item => item.id !== id) }
const close = () => emit('update:modelValue', false)
const save = async () => {
  if (saving.value || !linked.data.value) return
  saving.value = true; error.value = ''
  try {
    await workApi.replaceTreasureWorks(props.treasureId, selected.value.map(work => work.id), originalIds.value!)
    await Promise.all([queryClient.invalidateQueries({ queryKey: ['works'] }), queryClient.invalidateQueries({ queryKey: ['treasures'] })])
    ElMessage.success('收錄作品已更新。'); close()
  } catch (e) { error.value = errorMessage(e) } finally { saving.value = false }
}
</script>

<template>
  <AppDialog :busy="saving" :draft-ready="originalIds !== null" :model-value="modelValue" :draft="[selected.map(work => work.id)]" title="管理收錄作品" width="min(980px, calc(100vw - 32px))" scroll-body @update:model-value="close">
    <p class="app-dialog-intro">收錄不等於公開分享。一件作品只能收錄於一件展品，最多選 100 件；確認後即儲存。</p>
    <el-alert v-if="linked.isError.value && originalIds === null" title="無法載入已收錄作品" type="error" :closable="false"><el-button @click="linked.refetch()">重新載入</el-button></el-alert>
    <PickerWorkspace>
      <el-input v-model="keyword" placeholder="搜尋作品名稱" clearable aria-label="搜尋可收錄作品" />
      <el-skeleton v-if="candidates.isLoading.value" :rows="3" animated />
      <el-button v-else-if="candidates.isError.value && !candidates.data.value" @click="candidates.refetch()">載入失敗，重試</el-button>
      <div v-else class="picker-results" aria-label="可收錄作品">
        <PickerOption v-for="work in options" :key="work.id" :title="work.title" :selected="isSelected(work)" :disabled="originalIds === null || saving || (!!work.treasureId && work.treasureId !== treasureId)" multiple @select="toggle(work)">
          <span>{{ workStages[work.stage] }}</span><span>{{ work.editorMode === 'INTERNAL' ? '站內稿件' : '外部文件' }}</span><span v-if="work.treasureId && work.treasureId !== treasureId">已收錄於其他展品</span>
        </PickerOption>
        <p v-if="!options.length" class="app-field-help">{{ keyword ? '沒有符合的可收錄作品。' : '目前沒有可收錄作品。' }}</p>
        <div v-if="candidates.hasNextPage.value" class="load-more-row"><el-button :loading="candidates.isFetchingNextPage.value" @click="candidates.fetchNextPage()">載入更多</el-button></div>
      </div>
      <template #selection>
        <section class="selection" aria-label="已收錄作品">
          <h3>已選作品（{{ selected.length }} / 100）</h3>
          <p v-if="!selected.length" class="app-field-help">尚未收錄作品。</p>
          <div v-else class="selection-list">
            <PickerOption v-for="work in selected" :key="work.id" :title="work.title" selected multiple @select="remove(work.id)">
              <span>{{ workStages[work.stage] }}</span><span v-if="work.archived">已封存</span><span>{{ work.editorMode === 'INTERNAL' ? '站內稿件' : '外部文件' }}</span>
            </PickerOption>
          </div>
        </section>
      </template>
    </PickerWorkspace>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <template #footer="{ requestClose }"><el-button :disabled="saving" @click="requestClose">取消</el-button><el-button type="primary" :loading="saving" :disabled="originalIds === null" @click="save">儲存收錄作品</el-button></template>
  </AppDialog>
</template>

<style scoped>
.app-dialog-intro { margin-top: 0; }
.selection { display: grid; gap: var(--space-sm); }
.selection h3, .selection p { margin: 0; }
.selection-list { display: grid; gap: var(--space-xs); }
.load-more-row { display: flex; justify-content: center; padding: var(--space-md) 0; }
</style>
