<script setup lang="ts">
import { computed, ref } from 'vue'
import { useInfiniteQuery } from '@tanstack/vue-query'
import PickerWorkspace from '../ui/PickerWorkspace.vue'
import PickerOption from '../ui/PickerOption.vue'
import TreasureSourceSummary from './TreasureSourceSummary.vue'
import { normalizeIssueCardSearch } from '../../utils/cardSearch'
import { ElMessage } from 'element-plus'
import type { TreasureReference } from '../../types/treasure'
import { treasureApi } from '../../utils/api/treasureApi'
import { cardApi } from '../../utils/api/cardApi'
import { experimentApi } from '../../utils/api/experimentApi'
const props = defineProps<{ treasureId?: number; sourceTreasures: TreasureReference[]; sourceCard: TreasureReference | null; sourceExperiment: TreasureReference | null }>()
const emit = defineEmits<{ cancel: []; confirm: [{ treasures: TreasureReference[]; card: TreasureReference | null; experiment: TreasureReference | null }] }>()
const sourceKind = ref<'treasure' | 'card' | 'experiment'>('treasure')
const keyword = ref('')
// 此元件只編輯來源快照，確認後才帶回成果草稿。
const treasures = ref([...props.sourceTreasures])
const card = ref(props.sourceCard)
const experiment = ref(props.sourceExperiment)
const query = useInfiniteQuery({
  queryKey: computed(() => ['treasure-source-picker', sourceKind.value, keyword.value.trim()]),
  initialPageParam: 0,
  queryFn: async ({ pageParam }) => {
    const kind = sourceKind.value
    const search = keyword.value.trim()
    if (kind === 'card') {
      const result = await cardApi.searchCards({ ...normalizeIssueCardSearch(search), archiveStatus: 'ALL', sortBy: 'UPDATED_AT', direction: 'DESC', page: pageParam, size: 10 })
      return { ...result, content: result.content.map(item => ({ id: Number(item.id), title: item.title, metadata: [item.type === 'link' ? '連結' : item.type === 'note' ? '筆記' : '卡片', ...(item.isArchived ? ['已封存'] : []), ...(item.intervalDays === null ? ['暫停回流'] : []), ...(item.needsProcessing ? ['待整理'] : [])] })) }
    }
    const result = kind === 'treasure' ? await treasureApi.list({ keyword: search, page: pageParam, size: 10 }) : await experimentApi.getExperiments(false, pageParam, 10)
    return { ...result, content: result.content.map(item => ({ id: Number(item.id), title: item.title, metadata: [kind === 'treasure' ? '奇珍' : '實驗'] })) }
  },
  getNextPageParam: (lastPage, _pages, lastPageParam) => lastPageParam + 1 < lastPage.totalPages ? lastPageParam + 1 : undefined,
})
const candidates = computed(() => (query.data.value?.pages.flatMap(page => page.content) ?? []).filter(item => sourceKind.value !== 'treasure' || item.id !== props.treasureId))
const toggleTreasure = (item: TreasureReference) => {
  if (treasures.value.some(source => source.id === item.id)) treasures.value = treasures.value.filter(source => source.id !== item.id)
  else if (treasures.value.length < 20) treasures.value.push(item)
  else ElMessage.warning('來源奇珍最多 20 件')
}
const isSelected = (item: TreasureReference) => sourceKind.value === 'treasure'
  ? treasures.value.some(source => source.id === item.id)
  : (sourceKind.value === 'card' ? card.value : experiment.value)?.id === item.id
const select = (item: TreasureReference) => {
  if (sourceKind.value === 'treasure') toggleTreasure(item)
  else if (sourceKind.value === 'card') card.value = item
  else experiment.value = item
}
const remove = (kind: string, id: number) => {
  if (kind === 'card') card.value = null
  else if (kind === 'experiment') experiment.value = null
  else treasures.value = treasures.value.filter(item => item.id !== id)
}
const confirm = () => emit('confirm', { treasures: [...treasures.value], card: card.value, experiment: experiment.value })
defineExpose({ confirm })
</script>
<template>
  <section class="source-picker" aria-label="管理成果來源">
    <header class="source-intro"><el-button text @click="emit('cancel')">返回編輯奇珍</el-button><p class="app-dialog-intro">選擇來源後確認，最後儲存成果才會生效。</p></header>
    <PickerWorkspace>
        <el-tabs v-model="sourceKind" aria-label="來源種類"><el-tab-pane label="奇珍" name="treasure" /><el-tab-pane label="卡片" name="card" /><el-tab-pane label="實驗" name="experiment" /></el-tabs>
        <p class="source-rule">{{ sourceKind === 'treasure' ? '奇珍：最多選 20 件。' : sourceKind === 'card' ? '卡片：選 1 張；選擇另一張會替換原選項。' : '實驗：選 1 個；選擇另一個會替換原選項。' }}</p>
        <el-input v-if="sourceKind !== 'experiment'" v-model="keyword" :placeholder="sourceKind === 'card' ? '輸入 卡片 ID（例如 #7）或標題' : '依名稱搜尋來源'" clearable aria-label="搜尋來源" />
        <el-skeleton v-if="query.isLoading.value" :rows="3" animated />
        <el-button v-else-if="query.isError.value && !query.data.value" @click="query.refetch()">來源讀取失敗，重試</el-button>
        <div v-else class="source-options picker-results" aria-label="瀏覽來源">
          <PickerOption v-for="item in candidates" :key="item.id" :title="item.title" :selected="isSelected(item)" :multiple="sourceKind === 'treasure'" @select="select(item)"><span>#{{ item.id }}</span><span v-for="text in item.metadata" :key="text">{{ text }}</span></PickerOption>
          <p v-if="!candidates.length" class="app-field-help">{{ keyword ? '沒有符合搜尋條件的來源。' : '目前沒有可選擇的來源。' }}</p>
          <div v-if="query.hasNextPage.value && !query.isFetchNextPageError.value" class="load-more-row"><el-button :loading="query.isFetchingNextPage.value" @click="query.fetchNextPage()">載入更多</el-button></div>
          <el-button v-if="query.isFetchNextPageError.value" @click="query.fetchNextPage()">後續來源讀取失敗，重試</el-button>
        </div>
      <template #selection>
      <section class="source-selection" aria-label="已選來源">
        <h3>已選來源</h3>
        <TreasureSourceSummary :card="card" :experiment="experiment" :treasures="treasures" @remove="remove" />
      </section>
      </template>
    </PickerWorkspace>
  </section>
</template>
<style scoped>
.source-intro { display: grid; justify-items: start; gap: var(--space-xs); margin-bottom: var(--space-md); }
.source-intro p { margin: 0; }
.source-rule { margin: 0 0 var(--space-sm); color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.source-options { flex: 0 1 auto; }
.load-more-row { display: flex; justify-content: center; padding: var(--space-md) 0; }
</style>
