<script setup lang="ts">
defineOptions({ name: 'SearchView' })

import { computed, reactive, ref } from 'vue'
import { keepPreviousData, useQuery } from '@tanstack/vue-query'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import CardItem from '../components/CardItem.vue'
import type {
  CardGrowthStatus,
  CardSearchArchiveStatus,
  CardSearchDirection,
  CardSearchParams,
  CardSearchSortBy,
  CardSearchTagMode,
} from '../types/card'
import type { TagDto } from '../types/tag'
import { cardApi } from '../utils/api/cardApi'
import { tagApi } from '../utils/api/tagApi'
import { cardSearchQueryKey } from '../utils/cardSearch'

const PAGE_SIZE = 20
const router = useRouter()

interface SearchForm {
  id: string
  title: string
  tagIds: number[]
  tagMode: CardSearchTagMode
  growthStatuses: CardGrowthStatus[]
  archiveStatus: CardSearchArchiveStatus
  sortBy: CardSearchSortBy
  direction: CardSearchDirection
}

const defaultForm = (): SearchForm => ({
  id: '',
  title: '',
  tagIds: [],
  tagMode: 'OR',
  growthStatuses: [],
  archiveStatus: 'ACTIVE',
  sortBy: 'UPDATED_AT',
  direction: 'DESC',
})

const form = reactive<SearchForm>(defaultForm())
const appliedFilters = ref<CardSearchParams>({
  archiveStatus: 'ACTIVE',
  sortBy: 'UPDATED_AT',
  direction: 'DESC',
})
const page = ref(0)

const requestParams = computed<CardSearchParams>(() => ({
  ...appliedFilters.value,
  page: page.value,
  size: PAGE_SIZE,
}))

const visibleRange = computed(() => {
  if (!result.value || result.value.totalElements === 0) return null
  const start = result.value.page * result.value.size + 1
  return {
    start,
    end: start + result.value.content.length - 1,
  }
})

const { data: tagsData, isLoading: areTagsLoading } = useQuery<TagDto[]>({
  queryKey: ['tags'],
  queryFn: tagApi.getTags,
  staleTime: 5 * 60 * 1000,
})
const tagOptions = computed(() => (tagsData.value ?? []).map((tag) => ({
  value: tag.id,
  label: `#${tag.name}`,
})))

const {
  data: result,
  isLoading,
  isFetching,
  isError,
  refetch,
} = useQuery({
  queryKey: computed(() => cardSearchQueryKey(requestParams.value)),
  queryFn: () => cardApi.searchCards(requestParams.value),
  placeholderData: keepPreviousData,
})

const applySearch = () => {
  const normalizedId = form.id.trim() ? Number(form.id) : undefined
  if (normalizedId !== undefined && (!Number.isSafeInteger(normalizedId) || normalizedId <= 0)) {
    ElMessage.warning('Card ID 必須是大於 0 的整數')
    return
  }
  appliedFilters.value = {
    id: normalizedId,
    title: form.title.trim() || undefined,
    tagIds: [...new Set(form.tagIds)],
    tagMode: form.tagMode,
    growthStatuses: [...new Set(form.growthStatuses)],
    archiveStatus: form.archiveStatus,
    sortBy: form.sortBy,
    direction: form.direction,
  }
  page.value = 0
}

const clearSearch = () => {
  Object.assign(form, defaultForm())
  applySearch()
}

const openDetail = (cardId: string) => {
  router.push({ name: 'CardDetail', params: { id: cardId }, query: { from: 'search' } })
}
</script>

<template>
  <main class="search-page">
    <header class="page-heading">
      <h1>卡片查詢</h1>
    </header>

    <el-card class="search-panel" shadow="never">
      <el-form class="compact-search-form" @submit.prevent="applySearch">
        <div class="primary-filter-row">
          <label class="compact-field id-field">
            <span>Card ID</span>
            <el-input v-model="form.id" inputmode="numeric" maxlength="19" clearable placeholder="例如 42" />
          </label>
          <label class="compact-field title-field">
            <span>標題</span>
            <el-input v-model="form.title" maxlength="255" clearable placeholder="輸入部分標題" />
          </label>
          <label class="compact-field tag-field">
            <span>標籤</span>
            <el-select-v2 v-model="form.tagIds" :options="tagOptions" :loading="areTagsLoading" multiple filterable clearable collapse-tags placeholder="選擇標籤" />
          </label>
          <div v-if="form.tagIds.length >= 2" class="compact-field tag-mode-field">
            <span>多個標籤</span>
            <el-segmented v-model="form.tagMode" :options="[{ label: '符合任一', value: 'OR' }, { label: '符合全部', value: 'AND' }]" />
          </div>
          <el-button class="search-button" type="primary" native-type="submit" :loading="isFetching">查詢</el-button>
        </div>

        <div class="secondary-filter-row">
          <label class="compact-field growth-field">
            <span>成長狀態</span>
            <el-select v-model="form.growthStatuses" multiple clearable collapse-tags placeholder="全部狀態">
              <el-option label="未標記" value="UNMARKED" />
              <el-option label="種子" value="SEED" />
              <el-option label="生長" value="GROWING" />
              <el-option label="成熟" value="MATURE" />
            </el-select>
          </label>
          <label class="compact-field archive-field">
            <span>封存狀態</span>
            <el-select v-model="form.archiveStatus">
              <el-option label="未封存" value="ACTIVE" />
              <el-option label="已封存" value="ARCHIVED" />
              <el-option label="全部" value="ALL" />
            </el-select>
          </label>
          <label class="compact-field sort-field">
            <span>排序依據</span>
            <el-select v-model="form.sortBy">
              <el-option label="最近更新" value="UPDATED_AT" />
              <el-option label="建立時間" value="CREATED_AT" />
              <el-option label="下次回流" value="NEXT_SHOW_AT" />
              <el-option label="Card ID" value="ID" />
            </el-select>
          </label>
          <label class="compact-field direction-field">
            <span>排序方向</span>
            <el-select v-model="form.direction">
              <el-option label="降冪" value="DESC" />
              <el-option label="升冪" value="ASC" />
            </el-select>
          </label>
          <el-button class="clear-button" @click="clearSearch">清除條件</el-button>
        </div>
      </el-form>
    </el-card>

    <section class="results" aria-live="polite">
      <div class="result-heading">
        <h2>查詢結果</h2>
        <div v-if="result" class="result-summary">
          <span v-if="visibleRange">顯示第 {{ visibleRange.start }}–{{ visibleRange.end }} 張，共 {{ result.totalElements }} 張</span>
          <span v-else>共 0 張</span>
          <span v-if="isFetching && !isLoading" class="fetching-label">更新中…</span>
        </div>
      </div>

      <el-skeleton v-if="isLoading" :rows="6" animated />
      <el-result v-else-if="isError" icon="warning" title="查詢失敗">
        <template #extra><el-button type="primary" @click="refetch()">重新查詢</el-button></template>
      </el-result>
      <el-empty v-else-if="!result?.content.length" description="沒有符合條件的卡片" />
      <div v-else class="card-grid" :class="{ fetching: isFetching }">
        <CardItem
          v-for="card in result.content"
          :key="card.id"
          :data="card"
          view-mode="text"
          :board-type="card.isArchived ? 'archived' : 'search'"
          @open-detail="openDetail(card.id)"
        />
      </div>

      <div v-if="result && result.totalElements > PAGE_SIZE" class="pagination-row">
        <el-pagination
          layout="prev, pager, next"
          :current-page="page + 1"
          :page-size="PAGE_SIZE"
          :total="result.totalElements"
          @current-change="(nextPage: number) => page = nextPage - 1"
        />
        <span>第 {{ result.page + 1 }} / {{ result.totalPages }} 頁</span>
      </div>
    </section>
  </main>
</template>

<style scoped>
.search-page { padding: 24px; }
.page-heading h1, .result-heading h2 { margin: 0; }
.search-panel { margin-top: 20px; }
.compact-search-form { display: flex; flex-direction: column; gap: 14px; }
.primary-filter-row { display: flex; align-items: flex-end; flex-wrap: wrap; gap: 12px; }
.secondary-filter-row { display: flex; align-items: flex-end; flex-wrap: wrap; gap: 12px; }
.compact-field { display: flex; min-width: 0; flex-direction: column; gap: 5px; }
.compact-field > span { color: var(--el-text-color-secondary); font-size: 12px; line-height: 1; }
.compact-field :deep(.el-select), .compact-field :deep(.el-select-v2) { width: 100%; }
.id-field { width: 110px; }
.title-field { flex: 1 1 180px; }
.tag-field { flex: 1.2 1 220px; }
.tag-mode-field { min-width: 200px; }
.growth-field { width: 220px; }
.archive-field { width: 140px; }
.sort-field { width: 160px; }
.direction-field { width: 120px; }
.search-button, .clear-button { min-width: 88px; }
.clear-button { margin-left: auto; }
.results { margin-top: 28px; }
.result-heading { display: flex; align-items: baseline; justify-content: space-between; margin-bottom: 16px; }
.result-summary { display: flex; align-items: center; gap: 10px; color: var(--el-text-color-secondary); }
.fetching-label { color: var(--el-color-primary); }
.card-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; transition: opacity .15s; }
.card-grid.fetching { opacity: .65; }
.pagination-row { display: flex; align-items: center; justify-content: center; gap: 16px; margin-top: 24px; color: var(--el-text-color-secondary); font-size: 13px; }
@media (max-width: 1100px) { .card-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 720px) { .secondary-filter-row { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); } .growth-field, .archive-field, .sort-field, .direction-field { width: auto; } .clear-button { grid-column: 2; justify-self: end; } }
@media (max-width: 640px) { .search-page { padding: 16px; } .primary-filter-row, .secondary-filter-row { display: grid; grid-template-columns: 1fr; } .id-field, .title-field, .tag-field, .tag-mode-field { width: auto; } .search-button, .clear-button { grid-column: auto; width: 100%; margin-left: 0; } .card-grid { grid-template-columns: 1fr; } .result-heading { align-items: flex-start; gap: 8px; } .result-summary { flex-direction: column; align-items: flex-end; gap: 2px; text-align: right; } .pagination-row { flex-direction: column; gap: 6px; } }
</style>
