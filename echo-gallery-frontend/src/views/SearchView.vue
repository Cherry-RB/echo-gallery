<script setup lang="ts">
defineOptions({ name: 'SearchView' })

import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { keepPreviousData, useQuery } from '@tanstack/vue-query'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import CardItem from '../components/CardItem.vue'
import AppEmptyState from '../components/ui/AppEmptyState.vue'
import type {
  CardSearchArchiveStatus,
  CardSearchDirection,
  CardSearchParams,
  CardSearchRecurrenceStatus,
  CardSearchSortBy,
  CardSearchTagMode,
} from '../types/card'
import type { TagPage } from '../types/tag'
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
  needsProcessing: boolean
  archiveStatus: CardSearchArchiveStatus
  recurrenceStatus: CardSearchRecurrenceStatus
  minIntervalDays?: number
  maxIntervalDays?: number
  sortBy: CardSearchSortBy
  direction: CardSearchDirection
}

const defaultForm = (): SearchForm => ({
  id: '',
  title: '',
  tagIds: [],
  tagMode: 'OR',
  needsProcessing: false,
  archiveStatus: 'ACTIVE',
  recurrenceStatus: 'ALL',
  minIntervalDays: undefined,
  maxIntervalDays: undefined,
  sortBy: 'UPDATED_AT',
  direction: 'DESC',
})

const toSearchFilters = (source: SearchForm): CardSearchParams => ({
  id: source.id.trim() ? Number(source.id) : undefined,
  title: source.title.trim() || undefined,
  tagIds: [...new Set(source.tagIds)],
  tagMode: source.tagIds.length >= 2 ? source.tagMode : 'OR',
  needsProcessing: source.needsProcessing || undefined,
  archiveStatus: source.archiveStatus,
  recurrenceStatus: source.recurrenceStatus,
  minIntervalDays: source.recurrenceStatus === 'PAUSED' ? undefined : source.minIntervalDays,
  maxIntervalDays: source.recurrenceStatus === 'PAUSED' ? undefined : source.maxIntervalDays,
  sortBy: source.sortBy,
  direction: source.direction,
})

const form = reactive<SearchForm>(defaultForm())
const tagPopoverVisible = ref(false)
const tagSearchQuery = ref('')
const debouncedTagSearchQuery = ref('')
let tagSearchDebounceTimer: ReturnType<typeof setTimeout> | undefined
const appliedFilters = ref<CardSearchParams>(toSearchFilters(defaultForm()))
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


const activeFilterLabels = computed(() => {
  const filters = appliedFilters.value
  const labels: string[] = []
  if (filters.id) labels.push(`Card ID：${filters.id}`)
  if (filters.title) labels.push(`標題：${filters.title}`)
  if (filters.tagIds?.length) {
    const names = filters.tagIds.map(id =>
      tagOptions.value.find(option => option.value === id)?.label ?? `#${id}`)
    labels.push(`標籤：${names.join(filters.tagMode === 'AND' ? ' ＋ ' : '／')}`)
  }
  if (filters.needsProcessing) labels.push('待整理')
  if (filters.archiveStatus && filters.archiveStatus !== 'ACTIVE') {
    labels.push(`使用狀態：${filters.archiveStatus === 'ARCHIVED' ? '已封存' : '全部'}`)
  }
  if (filters.recurrenceStatus && filters.recurrenceStatus !== 'ALL') {
    labels.push(`回流：${filters.recurrenceStatus === 'ACTIVE' ? '回流中' : '已暫停'}`)
  }
  if (filters.minIntervalDays || filters.maxIntervalDays) {
    labels.push(`週期：${filters.minIntervalDays ?? 1}–${filters.maxIntervalDays ?? 365} 天`)
  }
  return labels
})

watch(tagSearchQuery, (query) => {
  if (tagSearchDebounceTimer) clearTimeout(tagSearchDebounceTimer)
  tagSearchDebounceTimer = setTimeout(() => {
    debouncedTagSearchQuery.value = query.trim()
  }, 200)
})

onBeforeUnmount(() => {
  if (tagSearchDebounceTimer) clearTimeout(tagSearchDebounceTimer)
})

const { data: tagsData, isLoading: areTagsLoading } = useQuery<TagPage>({
  queryKey: ['tags', 'search', debouncedTagSearchQuery],
  queryFn: () => tagApi.getTags(debouncedTagSearchQuery.value),
  staleTime: 5 * 60 * 1000,
})
const tagOptions = computed(() => (tagsData.value?.content ?? []).map((tag) => ({
  value: tag.id,
  label: `#${tag.name}`,
  count: tag.cardCount,
})))
const selectedTagOptions = computed(() => form.tagIds.map(id =>
  tagOptions.value.find(option => option.value === id) ?? { value: id, label: `#${id}`, count: undefined }))
const filteredTagOptions = computed(() => tagOptions.value)

const sameValues = <T,>(left: T[] = [], right: T[] = []) =>
  left.length === right.length && [...left].sort().every((value, index) => value === [...right].sort()[index])

type SearchField = 'id' | 'title' | 'tags' | 'processing' | 'archive' | 'recurrence' | 'interval'

const fieldMatchesApplied = (field: SearchField) => {
  const applied = appliedFilters.value
  switch (field) {
    case 'id': return form.id.trim() === String(applied.id ?? '')
    case 'title': return form.title.trim() === (applied.title ?? '')
    case 'tags': return sameValues(form.tagIds, applied.tagIds)
      && (form.tagIds.length < 2 || form.tagMode === (applied.tagMode ?? 'OR'))
    case 'processing': return form.needsProcessing === Boolean(applied.needsProcessing)
    case 'archive': return form.archiveStatus === (applied.archiveStatus ?? 'ACTIVE')
    case 'recurrence': return form.recurrenceStatus === (applied.recurrenceStatus ?? 'ALL')
    case 'interval': return (form.recurrenceStatus === 'PAUSED' ? undefined : form.minIntervalDays) === applied.minIntervalDays
      && (form.recurrenceStatus === 'PAUSED' ? undefined : form.maxIntervalDays) === applied.maxIntervalDays
  }
}

const fieldIsActive = (field: SearchField) => {
  const applied = appliedFilters.value
  switch (field) {
    case 'id': return applied.id !== undefined
    case 'title': return Boolean(applied.title)
    case 'tags': return Boolean(applied.tagIds?.length)
    case 'processing': return Boolean(applied.needsProcessing)
    case 'archive': return applied.archiveStatus !== 'ACTIVE'
    case 'recurrence': return applied.recurrenceStatus !== 'ALL'
    case 'interval': return applied.minIntervalDays !== undefined || applied.maxIntervalDays !== undefined
  }
}

const fieldClass = (field: SearchField) => ({
  'is-selected': fieldMatchesApplied(field) && fieldIsActive(field),
  'has-pending': !fieldMatchesApplied(field),
})

const hasPendingChanges = computed(() => [
  'id', 'title', 'tags', 'processing', 'archive', 'recurrence', 'interval',
].some(field => !fieldMatchesApplied(field as SearchField)))

const toggleTag = (tagId: number) => {
  form.tagIds = form.tagIds.includes(tagId)
    ? form.tagIds.filter(id => id !== tagId)
    : [...form.tagIds, tagId]
}

const removeTag = (tagId: number) => {
  form.tagIds = form.tagIds.filter(id => id !== tagId)
}

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
  if (
    form.recurrenceStatus !== 'PAUSED'
    && form.minIntervalDays !== undefined
    && form.maxIntervalDays !== undefined
    && form.minIntervalDays > form.maxIntervalDays
  ) {
    ElMessage.warning('最少回流天數不可大於最多回流天數')
    return
  }
  appliedFilters.value = { ...toSearchFilters(form), id: normalizedId }
  page.value = 0
}

const applySorting = () => {
  appliedFilters.value = {
    ...appliedFilters.value,
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
    <section class="search-workspace">
      <el-form class="compact-search-form" @submit.prevent="applySearch">
        <div class="search-filter-body">
          <header class="search-filter-header">
            <h1>卡片查詢</h1>
          </header>
          <section class="filter-section" aria-label="主要查詢條件">
          <div class="primary-filter-row">
          <label class="compact-field id-field" :class="fieldClass('id')">
            <span>Card ID</span>
            <el-input v-model="form.id" inputmode="numeric" maxlength="19" clearable placeholder="例如 42" />
          </label>
          <label class="compact-field title-field" :class="fieldClass('title')">
            <span>標題</span>
            <el-input v-model="form.title" maxlength="255" clearable placeholder="輸入部分標題" />
          </label>
          <div class="compact-field tag-field" :class="fieldClass('tags')">
            <div class="tag-filter-toolbar">
              <span>標籤</span>
              <el-radio-group v-if="form.tagIds.length >= 2" v-model="form.tagMode" size="small" aria-label="多個標籤的比對方式">
                <el-radio-button value="OR">或 (OR)</el-radio-button>
                <el-radio-button value="AND">且 (AND)</el-radio-button>
              </el-radio-group>
            </div>
            <div class="picker-control" :class="{ 'has-selection': form.tagIds.length }">
              <el-tag
                v-for="tag in selectedTagOptions"
                :key="tag.value"
                type="info"
                size="small"
                effect="plain"
                closable
                @close="removeTag(tag.value)"
              >{{ tag.label }}</el-tag>
              <el-popover
                v-model:visible="tagPopoverVisible"
                placement="bottom-start"
                :width="320"
                trigger="click"
              >
                <template #reference>
                  <el-button class="picker-trigger" :loading="areTagsLoading">+ 更多標籤</el-button>
                </template>
                <div class="filter-popover-content">
                  <el-input v-model="tagSearchQuery" clearable placeholder="搜尋既有標籤…" />
                  <div class="popover-subtitle">既有標籤（點選切換）</div>
                  <div class="popover-tags-list">
                    <el-tag
                      v-for="tag in filteredTagOptions"
                      :key="tag.value"
                      class="clickable-filter-tag"
                      :effect="form.tagIds.includes(tag.value) ? 'dark' : 'plain'"
                      @click="toggleTag(tag.value)"
                    >{{ tag.label }}<span class="tag-option-count">（{{ tag.count }}）</span></el-tag>
                    <span v-if="!areTagsLoading && filteredTagOptions.length === 0" class="empty-option-text">沒有符合的既有標籤</span>
                  </div>
                </div>
              </el-popover>
            </div>
          </div>
        </div>
        </section>

          <section class="filter-section secondary-section" aria-label="進階篩選與排序條件">
          <div class="secondary-filter-row">
          <label class="compact-field processing-field" :class="fieldClass('processing')">
            <span>整理狀態</span>
            <el-checkbox v-model="form.needsProcessing">只看待整理</el-checkbox>
          </label>
          <label class="compact-field archive-field" :class="fieldClass('archive')">
            <span>使用狀態</span>
            <el-select v-model="form.archiveStatus">
              <el-option label="使用中" value="ACTIVE" />
              <el-option label="已封存" value="ARCHIVED" />
              <el-option label="全部" value="ALL" />
            </el-select>
          </label>
          <label class="compact-field recurrence-status-field" :class="fieldClass('recurrence')">
            <span>回流狀態</span>
            <el-select v-model="form.recurrenceStatus">
              <el-option label="全部" value="ALL" />
              <el-option label="回流中" value="ACTIVE" />
              <el-option label="已暫停" value="PAUSED" />
            </el-select>
          </label>
          <div class="compact-field interval-field" :class="fieldClass('interval')">
            <span>回流週期</span>
            <div class="interval-range">
              <el-input-number
                v-model="form.minIntervalDays"
                :min="1"
                :max="365"
                :controls="false"
                :disabled="form.recurrenceStatus === 'PAUSED'"
                placeholder="最少"
                aria-label="最少回流天數"
              />
              <span>至</span>
              <el-input-number
                v-model="form.maxIntervalDays"
                :min="1"
                :max="365"
                :controls="false"
                :disabled="form.recurrenceStatus === 'PAUSED'"
                placeholder="最多"
                aria-label="最多回流天數"
              />
              <span>天</span>
            </div>
          </div>
        </div>
          </section>
        </div>

        <div class="search-action-footer" :class="{ 'has-pending': hasPendingChanges }" aria-live="polite">
          <div class="search-actions">
            <el-button class="clear-button" @click="clearSearch">清除條件</el-button>
            <el-button class="search-button" type="primary" native-type="submit" :loading="isFetching" :disabled="!hasPendingChanges">
              套用條件
            </el-button>
          </div>
        </div>
      </el-form>

      <section class="results" aria-live="polite">
      <div class="result-heading">
        <div class="result-filter-status">
          <span class="result-filter-label">目前篩選狀態：</span>
          <el-tag :type="hasPendingChanges ? 'warning' : 'success'" effect="light" round>
            {{ hasPendingChanges ? '條件尚未套用' : '條件已套用' }}
          </el-tag>
          <div v-if="activeFilterLabels.length" class="filter-tags">
            <el-tag v-for="label in activeFilterLabels" :key="label" class="active-filter-tag" effect="plain">{{ label }}</el-tag>
          </div>
          <span v-else class="default-filter-note">未封存卡片・最近更新優先</span>
        </div>
        <div class="result-controls">
          <div class="result-sort-controls" aria-label="結果排序">
            <span>排序</span>
            <el-select v-model="form.sortBy" aria-label="排序依據" @change="applySorting">
              <el-option label="最近更新" value="UPDATED_AT" />
              <el-option label="建立時間" value="CREATED_AT" />
              <el-option label="下次回流" value="NEXT_SHOW_AT" />
              <el-option label="Card ID" value="ID" />
            </el-select>
            <el-select v-model="form.direction" aria-label="排序方向" @change="applySorting">
              <el-option label="降冪" value="DESC" />
              <el-option label="升冪" value="ASC" />
            </el-select>
          </div>
          <div v-if="result" class="result-summary">
            <span v-if="visibleRange">顯示第 {{ visibleRange.start }}–{{ visibleRange.end }} 張，共 {{ result.totalElements }} 張</span>
            <span v-else>共 0 張</span>
            <span v-if="isFetching && !isLoading" class="fetching-label">更新中…</span>
          </div>
        </div>
      </div>

        <div class="results-scroll">
          <el-skeleton v-if="isLoading" :rows="6" animated />
          <el-result v-else-if="isError" icon="warning" title="查詢失敗">
            <template #extra><el-button type="primary" @click="refetch()">重新查詢</el-button></template>
          </el-result>
          <AppEmptyState v-else-if="!result?.content.length" description="沒有符合條件的卡片" />
          <template v-else>
            <div class="card-grid" :class="{ fetching: isFetching }">
              <CardItem
                v-for="card in result.content"
                :key="card.id"
                :data="card"
                view-mode="text"
                :board-type="card.isArchived ? 'archived' : 'search'"
                @open-detail="openDetail(card.id)"
              />
            </div>
            <div v-if="result.totalElements > PAGE_SIZE" class="pagination-row">
              <el-pagination
                background
                layout="prev, pager, next"
                :current-page="page + 1"
                :page-size="PAGE_SIZE"
                :total="result.totalElements"
                @current-change="(nextPage: number) => page = nextPage - 1"
              />
              <span>第 {{ result.page + 1 }} / {{ result.totalPages }} 頁・每頁 {{ PAGE_SIZE }} 張</span>
            </div>
          </template>
        </div>
      </section>
    </section>
  </main>
</template>

<style scoped>
.search-page { width: calc(100% + (var(--page-gutter) * 2)); height: 100dvh; margin: calc(var(--page-gutter) * -1); overflow: hidden; }
.search-workspace { display: grid; height: 100%; grid-template-columns: 336px minmax(0, 1fr); background: var(--surface-page); }
.compact-search-form { display: flex; min-width: 0; min-height: 0; flex-direction: column; overflow: hidden; border-right: 1px solid var(--el-border-color-light); background: var(--el-bg-color); }
.search-filter-body { display: flex; min-height: 0; flex: 1; flex-direction: column; gap: var(--space-lg); padding: var(--workspace-padding); overflow-y: auto; }
.search-filter-header h1 { margin: 0; font-size: var(--type-section-title); line-height: var(--leading-section-title); }
.filter-section { display: flex; flex-direction: column; gap: var(--space-md); }
.filter-section-title { margin: 0; font-size: var(--type-card-title); line-height: var(--leading-card-title); }
.primary-filter-row, .secondary-filter-row { display: grid; grid-template-columns: minmax(0, 1fr); gap: var(--space-sm); }
.compact-field { display: flex; min-width: 0; flex-direction: column; gap: var(--space-2xs); }
.compact-field > span { color: var(--el-text-color-regular); font-size: var(--type-metadata); font-weight: var(--weight-semibold); line-height: var(--leading-metadata); }
.compact-field :deep(.el-select), .compact-field :deep(.el-select-v2) { width: 100%; }
.compact-field :deep(.el-input__wrapper),
.compact-field :deep(.el-select__wrapper) { background: var(--el-bg-color); box-shadow: 0 0 0 1px var(--el-border-color) inset; }
.compact-field :deep(.el-input__wrapper:hover),
.compact-field :deep(.el-select__wrapper:hover) { box-shadow: 0 0 0 1px var(--el-border-color-darker) inset; }
.compact-field.is-selected > span { color: var(--el-color-primary); }
.compact-field.is-selected :deep(.el-input__wrapper),
.compact-field.is-selected :deep(.el-select__wrapper) { background: var(--el-color-primary-light-9); box-shadow: 0 0 0 1px var(--el-color-primary-light-5) inset; }
.compact-field.has-pending > span { color: var(--el-color-warning); }
.compact-field.has-pending :deep(.el-input__wrapper),
.compact-field.has-pending :deep(.el-select__wrapper) { background: var(--el-color-warning-light-9); box-shadow: 0 0 0 1px var(--el-color-warning-light-5) inset; }
.tag-filter-toolbar { display: flex; align-items: center; justify-content: space-between; gap: var(--space-xs); }
.tag-filter-toolbar > span { color: var(--el-text-color-regular); font-size: var(--type-metadata); font-weight: var(--weight-semibold); line-height: var(--leading-metadata); }
.tag-field.is-selected .tag-filter-toolbar > span { color: var(--el-color-primary); }
.tag-field.has-pending .tag-filter-toolbar > span { color: var(--el-color-warning); }
.picker-control { display: flex; min-height: 32px; align-items: center; flex-wrap: wrap; gap: var(--space-2xs); padding: 4px 6px; border: 1px solid var(--el-border-color); border-radius: var(--radius-sm); background: var(--el-bg-color); transition: border-color .15s; }
.picker-control:hover, .picker-control:focus-within { border-color: var(--el-color-primary); }
.picker-control.has-selection { border-color: var(--el-color-primary-light-5); background: var(--el-color-primary-light-9); }
.tag-field.has-pending .picker-control { border-color: var(--el-color-warning-light-5); background: var(--el-color-warning-light-9); }
.picker-control.has-selection :deep(.el-tag) { background: var(--el-bg-color); }
.picker-trigger { min-height: 24px; padding-inline: 9px; border-style: dashed; background: var(--el-bg-color); color: var(--el-text-color-regular); }
.filter-popover-content { display: flex; flex-direction: column; gap: var(--space-sm); }
.popover-subtitle { color: var(--el-text-color-secondary); font-size: var(--type-caption); }
.popover-tags-list { display: flex; max-height: 220px; flex-wrap: wrap; gap: var(--space-xs); overflow-y: auto; }
.clickable-filter-tag { cursor: pointer; user-select: none; }
.tag-option-count { flex: 0 0 auto; color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.empty-option-text { color: var(--el-text-color-secondary); font-size: var(--type-caption); }
.secondary-section { padding-top: var(--space-lg); border-top: 1px solid var(--el-border-color-lighter); }
.interval-range { display: grid; grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr) auto; align-items: center; gap: var(--space-xs); color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.interval-range :deep(.el-input-number) { width: 100%; }
.search-actions { display: grid; flex: 0 0 auto; grid-template-columns: 1fr 1fr; align-items: flex-end; gap: var(--space-xs); }
.search-button, .clear-button { min-width: 88px; }
.search-action-footer { display: flex; flex: 0 0 auto; align-items: stretch; flex-direction: column; padding: var(--workspace-padding); border-top: 1px solid var(--el-border-color-light); background: var(--el-bg-color); }
.filter-tags { display: flex; flex-wrap: wrap; gap: var(--space-xs); }
.default-filter-note { color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.search-actions { width: 100%; margin-left: 0; }
.results { display: flex; min-width: 0; min-height: 0; flex-direction: column; overflow: hidden; }
.result-heading { display: flex; flex: 0 0 auto; align-items: center; justify-content: space-between; gap: var(--space-md); padding: var(--workspace-padding); border-bottom: 1px solid var(--el-border-color-light); background: var(--el-bg-color); }
.result-filter-status { display: flex; min-width: 0; align-items: center; flex-wrap: wrap; gap: var(--space-xs); }
.result-filter-label { flex: 0 0 auto; color: var(--el-text-color-secondary); font-size: var(--type-secondary); }
.result-controls { display: flex; flex: 0 0 auto; align-items: center; gap: var(--space-md); }
.result-sort-controls { display: flex; align-items: center; gap: var(--space-xs); color: var(--el-text-color-secondary); font-size: var(--type-caption); white-space: nowrap; }
.result-sort-controls :deep(.el-select:first-of-type) { width: 128px; }
.result-sort-controls :deep(.el-select:last-of-type) { width: 96px; }
.result-summary { display: flex; align-items: center; gap: var(--space-sm); color: var(--el-text-color-secondary); }
.fetching-label { color: var(--el-color-primary); }
.results-scroll { min-height: 0; flex: 1; padding: var(--workspace-padding); overflow-y: auto; }
.card-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); align-content: start; gap: var(--space-md); transition: opacity .15s; }
.card-grid :deep(.app-collection-card) { min-width: 0; min-height: 192px; height: 100%; }
.card-grid.fetching { opacity: .65; }
.results-scroll > :deep(.el-skeleton), .results-scroll > :deep(.el-result), .results-scroll > :deep(.app-empty-state) { min-height: 240px; }
.pagination-row { display: flex; align-items: center; justify-content: flex-end; gap: var(--space-md); padding: var(--space-lg) 0 var(--space-xs); color: var(--el-text-color-secondary); font-size: var(--type-caption); }
@media (max-width: 1200px) { .search-page { height: calc(100dvh - 56px); } }
@media (max-width: 768px) { .search-page { height: auto; min-height: calc(100dvh - 56px); overflow: visible; } .search-workspace { grid-template-columns: minmax(0, 1fr); } .compact-search-form { overflow: visible; border-right: 0; border-bottom: 1px solid var(--el-border-color-light); } .search-filter-body, .results-scroll { overflow: visible; } .card-grid { flex: none; } }
@media (max-width: 1024px) { .result-heading { align-items: flex-start; flex-direction: column; } .result-controls { width: 100%; justify-content: space-between; } }
@media (max-width: 640px) { .search-button, .clear-button { width: 100%; } .card-grid { grid-template-columns: 1fr; } .result-controls { align-items: flex-start; flex-direction: column; gap: var(--space-xs); } .result-sort-controls { width: 100%; } .result-sort-controls :deep(.el-select:first-of-type) { flex: 1; width: auto; } .result-summary { align-items: flex-start; flex-direction: column; gap: var(--space-2xs); text-align: left; } .pagination-row { align-items: center; flex-direction: column; gap: var(--space-xs); } }
</style>
