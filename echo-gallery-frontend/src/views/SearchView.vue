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
  CardSearchRecurrenceStatus,
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
  growthStatuses: [],
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
  growthStatuses: [...new Set(source.growthStatuses)],
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
const growthPopoverVisible = ref(false)
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

const growthStatusLabels: Record<CardGrowthStatus, string> = {
  UNMARKED: '未標記',
  SEED: '🌱 種子',
  GROWING: '🌿 生長',
  MATURE: '🌳 成熟',
}
const growthStatusTypes: Record<CardGrowthStatus, 'info' | 'success' | 'warning' | 'primary'> = {
  UNMARKED: 'info',
  SEED: 'success',
  GROWING: 'warning',
  MATURE: 'primary',
}
const growthStatusOptions = (Object.entries(growthStatusLabels) as Array<[CardGrowthStatus, string]>)
  .map(([value, label]) => ({ value, label, tagType: growthStatusTypes[value] }))

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
  if (filters.growthStatuses?.length) {
    labels.push(`成長狀態：${filters.growthStatuses.map(status => growthStatusLabels[status]).join('、')}`)
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
  if (filters.sortBy !== 'UPDATED_AT' || filters.direction !== 'DESC') {
    const sortLabels: Record<CardSearchSortBy, string> = {
      UPDATED_AT: '最近更新', CREATED_AT: '建立時間', NEXT_SHOW_AT: '下次回流', ID: 'Card ID',
    }
    labels.push(`排序：${sortLabels[filters.sortBy ?? 'UPDATED_AT']} ${filters.direction === 'ASC' ? '升冪' : '降冪'}`)
  }
  return labels
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
const selectedTagOptions = computed(() => form.tagIds.map(id =>
  tagOptions.value.find(option => option.value === id) ?? { value: id, label: `#${id}` }))
const filteredTagOptions = computed(() => {
  const keyword = tagSearchQuery.value.trim().toLocaleLowerCase()
  if (!keyword) return tagOptions.value
  return tagOptions.value.filter(option => option.label.toLocaleLowerCase().includes(keyword))
})
const selectedGrowthStatuses = computed(() => growthStatusOptions.filter(option =>
  form.growthStatuses.includes(option.value)))

const sameValues = <T,>(left: T[] = [], right: T[] = []) =>
  left.length === right.length && [...left].sort().every((value, index) => value === [...right].sort()[index])

type SearchField = 'id' | 'title' | 'tags' | 'growth' | 'processing' | 'archive' | 'recurrence' | 'interval' | 'sort' | 'direction'

const fieldMatchesApplied = (field: SearchField) => {
  const applied = appliedFilters.value
  switch (field) {
    case 'id': return form.id.trim() === String(applied.id ?? '')
    case 'title': return form.title.trim() === (applied.title ?? '')
    case 'tags': return sameValues(form.tagIds, applied.tagIds)
      && (form.tagIds.length < 2 || form.tagMode === (applied.tagMode ?? 'OR'))
    case 'growth': return sameValues(form.growthStatuses, applied.growthStatuses)
    case 'processing': return form.needsProcessing === Boolean(applied.needsProcessing)
    case 'archive': return form.archiveStatus === (applied.archiveStatus ?? 'ACTIVE')
    case 'recurrence': return form.recurrenceStatus === (applied.recurrenceStatus ?? 'ALL')
    case 'interval': return (form.recurrenceStatus === 'PAUSED' ? undefined : form.minIntervalDays) === applied.minIntervalDays
      && (form.recurrenceStatus === 'PAUSED' ? undefined : form.maxIntervalDays) === applied.maxIntervalDays
    case 'sort': return form.sortBy === (applied.sortBy ?? 'UPDATED_AT')
    case 'direction': return form.direction === (applied.direction ?? 'DESC')
  }
}

const fieldIsActive = (field: SearchField) => {
  const applied = appliedFilters.value
  switch (field) {
    case 'id': return applied.id !== undefined
    case 'title': return Boolean(applied.title)
    case 'tags': return Boolean(applied.tagIds?.length)
    case 'growth': return Boolean(applied.growthStatuses?.length)
    case 'processing': return Boolean(applied.needsProcessing)
    case 'archive': return applied.archiveStatus !== 'ACTIVE'
    case 'recurrence': return applied.recurrenceStatus !== 'ALL'
    case 'interval': return applied.minIntervalDays !== undefined || applied.maxIntervalDays !== undefined
    case 'sort': return applied.sortBy !== 'UPDATED_AT'
    case 'direction': return applied.direction !== 'DESC'
  }
}

const fieldClass = (field: SearchField) => ({
  'is-selected': fieldMatchesApplied(field) && fieldIsActive(field),
  'has-pending': !fieldMatchesApplied(field),
})

const hasPendingChanges = computed(() => [
  'id', 'title', 'tags', 'growth', 'processing', 'archive', 'recurrence', 'interval', 'sort', 'direction',
].some(field => !fieldMatchesApplied(field as SearchField)))

const toggleTag = (tagId: number) => {
  form.tagIds = form.tagIds.includes(tagId)
    ? form.tagIds.filter(id => id !== tagId)
    : [...form.tagIds, tagId]
}

const removeTag = (tagId: number) => {
  form.tagIds = form.tagIds.filter(id => id !== tagId)
}

const toggleGrowthStatus = (status: CardGrowthStatus) => {
  form.growthStatuses = form.growthStatuses.includes(status)
    ? form.growthStatuses.filter(value => value !== status)
    : [...form.growthStatuses, status]
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
      <p>組合關鍵字與篩選條件，快速找到想回顧的卡片。</p>
    </header>

    <el-card class="search-panel" shadow="never">
      <el-form class="compact-search-form" @submit.prevent="applySearch">
        <section class="filter-section" aria-labelledby="primary-filter-title">
          <div class="filter-section-heading">
            <h2 id="primary-filter-title">主要條件</h2>
            <span>可輸入任一條件開始查詢</span>
          </div>
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
            <span>標籤</span>
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
                  <el-button class="picker-trigger" :loading="areTagsLoading">
                    {{ form.tagIds.length ? '更多標籤' : '選擇標籤' }}
                  </el-button>
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
                    >{{ tag.label }}</el-tag>
                    <span v-if="!areTagsLoading && filteredTagOptions.length === 0" class="empty-option-text">
                      沒有符合的既有標籤
                    </span>
                  </div>
                </div>
              </el-popover>
            </div>
          </div>
          <div v-if="form.tagIds.length >= 2" class="compact-field tag-mode-field" :class="fieldClass('tags')">
            <span>多個標籤</span>
            <el-segmented v-model="form.tagMode" :options="[{ label: '符合任一', value: 'OR' }, { label: '符合全部', value: 'AND' }]" />
          </div>
        </div>
        </section>

        <section class="filter-section secondary-section" aria-labelledby="secondary-filter-title">
          <div class="filter-section-heading">
            <h2 id="secondary-filter-title">篩選與排序</h2>
            <span>縮小結果範圍，或調整顯示順序</span>
          </div>
          <div class="secondary-filter-row">
          <div class="compact-field growth-field" :class="fieldClass('growth')">
            <span>成長狀態</span>
            <div class="picker-control" :class="{ 'has-selection': form.growthStatuses.length }">
              <el-tag
                v-for="status in selectedGrowthStatuses"
                :key="status.value"
                :type="status.tagType"
                size="small"
                effect="plain"
                closable
                @close="toggleGrowthStatus(status.value)"
              >{{ status.label }}</el-tag>
              <el-popover
                v-model:visible="growthPopoverVisible"
                placement="bottom-start"
                :width="220"
                trigger="click"
              >
                <template #reference>
                  <el-button class="picker-trigger">
                    {{ form.growthStatuses.length ? '調整狀態' : '全部狀態' }}
                  </el-button>
                </template>
                <div class="growth-option-list" role="listbox" aria-label="成長狀態" aria-multiselectable="true">
                  <button
                    v-for="option in growthStatusOptions"
                    :key="option.value"
                    type="button"
                    class="growth-option"
                    :class="{ selected: form.growthStatuses.includes(option.value) }"
                    :aria-selected="form.growthStatuses.includes(option.value)"
                    @click="toggleGrowthStatus(option.value)"
                  >
                    <span>{{ option.label }}</span>
                    <span class="selection-mark">{{ form.growthStatuses.includes(option.value) ? '✓' : '' }}</span>
                  </button>
                </div>
              </el-popover>
            </div>
          </div>
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
        <div class="sort-section" aria-label="排序結果">
          <span class="sort-section-label">排序結果</span>
          <div class="sort-control-group">
            <label class="compact-field sort-field" :class="fieldClass('sort')">
              <span>排序依據</span>
              <el-select v-model="form.sortBy">
                <el-option label="最近更新" value="UPDATED_AT" />
                <el-option label="建立時間" value="CREATED_AT" />
                <el-option label="下次回流" value="NEXT_SHOW_AT" />
                <el-option label="Card ID" value="ID" />
              </el-select>
            </label>
            <label class="compact-field direction-field" :class="fieldClass('direction')">
              <span>排序方向</span>
              <el-select v-model="form.direction">
                <el-option label="降冪" value="DESC" />
                <el-option label="升冪" value="ASC" />
              </el-select>
            </label>
          </div>
          </div>
        </section>

        <div class="search-action-footer" :class="{ 'has-pending': hasPendingChanges }" aria-live="polite">
          <div class="applied-filters">
            <el-tag v-if="hasPendingChanges" type="warning" effect="light" round>尚有未套用變更</el-tag>
            <el-tag v-else type="success" effect="light" round>條件已套用</el-tag>
            <div class="applied-filter-copy">
              <span class="applied-filters-label">目前結果</span>
              <div v-if="activeFilterLabels.length" class="filter-tags">
                <el-tag v-for="label in activeFilterLabels" :key="label" effect="light" round>{{ label }}</el-tag>
              </div>
              <span v-else class="default-filter-note">預設顯示未封存卡片，依最近更新排序</span>
            </div>
          </div>
          <div class="search-actions">
            <el-button class="clear-button" @click="clearSearch">清除條件</el-button>
            <el-button class="search-button" type="primary" native-type="submit" :loading="isFetching" :disabled="!hasPendingChanges">
              套用條件
            </el-button>
          </div>
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
.page-heading p { margin: 8px 0 0; color: var(--el-text-color-secondary); font-size: var(--type-ui); }
.search-panel { margin-top: 20px; border-color: var(--el-border-color); background: var(--el-bg-color-page); }
.search-panel :deep(.el-card__body) { padding: 20px; }
.compact-search-form { display: flex; flex-direction: column; gap: 16px; }
.filter-section { padding: 16px; border: 1px solid var(--el-border-color-lighter); border-radius: 10px; background: var(--el-bg-color); }
.filter-section-heading { display: flex; align-items: baseline; gap: 10px; margin-bottom: 14px; }
.filter-section-heading h2 { margin: 0; color: var(--el-text-color-primary); font-size: var(--type-ui); font-weight: 650; }
.filter-section-heading span { color: var(--el-text-color-secondary); font-size: var(--type-caption); }
.primary-filter-row { display: flex; align-items: flex-end; flex-wrap: wrap; gap: 12px; }
.secondary-filter-row { display: flex; align-items: flex-end; flex-wrap: wrap; gap: 12px; }
.compact-field { display: flex; min-width: 0; flex-direction: column; gap: 5px; }
.compact-field > span { color: var(--el-text-color-regular); font-size: var(--type-meta); font-weight: 600; line-height: 1; }
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
.picker-control { display: flex; min-height: 32px; align-items: center; flex-wrap: wrap; gap: 6px; padding: 4px 6px; border: 1px solid var(--el-border-color); border-radius: var(--el-border-radius-base); background: var(--el-bg-color); transition: border-color .15s; }
.picker-control:hover, .picker-control:focus-within { border-color: var(--el-color-primary); }
.picker-control.has-selection { padding-block: 3px; border-color: var(--el-color-primary-light-5); background: var(--el-color-primary-light-9); }
.compact-field.has-pending .picker-control { border-color: var(--el-color-warning-light-5); background: var(--el-color-warning-light-9); }
.picker-control.has-selection :deep(.el-tag) { background: var(--el-bg-color); box-shadow: 0 1px 2px rgb(0 0 0 / 8%); font-weight: 600; }
.picker-control.has-selection :deep(.el-tag__close) { color: currentColor; }
.picker-trigger { min-height: 24px; padding-inline: 9px; border-style: dashed; background: var(--el-bg-color); color: var(--el-text-color-regular); }
.filter-popover-content { display: flex; flex-direction: column; gap: 12px; }
.popover-subtitle { color: var(--el-text-color-secondary); font-size: var(--type-caption); }
.popover-tags-list { display: flex; max-height: 220px; flex-wrap: wrap; gap: 8px; overflow-y: auto; }
.clickable-filter-tag { cursor: pointer; user-select: none; }
.empty-option-text { color: var(--el-text-color-secondary); font-size: var(--type-caption); }
.growth-option-list { display: flex; flex-direction: column; margin: -6px; }
.growth-option { display: flex; width: 100%; min-height: 40px; align-items: center; justify-content: space-between; padding: 8px 12px; border: 0; border-radius: 6px; background: transparent; color: var(--el-text-color-regular); cursor: pointer; font: inherit; text-align: left; }
.growth-option:hover { background: var(--el-fill-color-light); }
.growth-option.selected { background: var(--el-color-primary-light-9); color: var(--el-color-primary); font-weight: 600; }
.selection-mark { width: 16px; color: var(--el-color-primary); text-align: center; }
.id-field { width: 110px; }
.title-field { flex: 1 1 180px; }
.tag-field { flex: 1.2 1 220px; }
.tag-mode-field { min-width: 200px; }
.growth-field { width: 220px; }
.archive-field { width: 140px; }
.recurrence-status-field { width: 140px; }
.interval-field { width: 240px; }
.interval-range { display: grid; grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr) auto; align-items: center; gap: 6px; color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.interval-range :deep(.el-input-number) { width: 100%; }
.sort-field { width: 160px; }
.direction-field { width: 120px; }
.sort-section { display: flex; align-items: flex-end; gap: 14px; padding-top: 14px; margin-top: 14px; border-top: 1px solid var(--el-border-color-lighter); }
.sort-section-label { padding-bottom: 9px; color: var(--el-text-color-secondary); font-size: var(--type-meta); font-weight: 600; }
.sort-control-group,
.search-actions { display: flex; flex: 0 0 auto; align-items: flex-end; gap: 8px; }
.search-button, .clear-button { min-width: 88px; }
.search-action-footer { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 12px 14px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; background: var(--el-fill-color-lighter); }
.search-action-footer.has-pending { border-color: var(--el-color-warning-light-5); background: var(--el-color-warning-light-9); }
.applied-filters { display: flex; min-width: 0; align-items: center; gap: 10px; }
.applied-filter-copy { display: flex; min-width: 0; align-items: center; flex-wrap: wrap; gap: 8px; }
.applied-filters-label { flex: 0 0 auto; color: var(--el-text-color-primary); font-size: var(--type-meta); font-weight: 650; }
.filter-tags { display: flex; flex-wrap: wrap; gap: 6px; }
.default-filter-note { color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.search-actions { margin-left: auto; }
.results { margin-top: 28px; }
.result-heading { display: flex; align-items: baseline; justify-content: space-between; margin-bottom: 16px; }
.result-summary { display: flex; align-items: center; gap: 10px; color: var(--el-text-color-secondary); }
.fetching-label { color: var(--el-color-primary); }
.card-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; transition: opacity .15s; }
.card-grid.fetching { opacity: .65; }
.pagination-row { display: flex; align-items: center; justify-content: center; gap: 16px; margin-top: 24px; color: var(--el-text-color-secondary); font-size: var(--type-caption); }
@media (max-width: 1100px) { .card-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 720px) { .secondary-filter-row { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); } .growth-field, .archive-field, .recurrence-status-field, .interval-field { width: auto; } .sort-section { align-items: flex-start; flex-direction: column; gap: 8px; } .sort-section-label { padding-bottom: 0; } .sort-control-group { width: 100%; } .sort-control-group .compact-field { flex: 1; width: auto; } }
@media (max-width: 640px) { .search-page { padding: 16px; } .search-panel :deep(.el-card__body) { padding: 12px; } .filter-section { padding: 14px 12px; } .filter-section-heading { align-items: flex-start; flex-direction: column; gap: 4px; } .primary-filter-row, .secondary-filter-row { display: grid; grid-template-columns: 1fr; } .id-field, .title-field, .tag-field, .tag-mode-field { width: auto; } .sort-control-group { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, .75fr); } .search-action-footer { align-items: stretch; flex-direction: column; } .search-actions { display: grid; width: 100%; grid-template-columns: 1fr 1fr; margin-left: 0; } .search-button, .clear-button { width: 100%; } .applied-filters { align-items: flex-start; } .applied-filter-copy { align-items: flex-start; flex-direction: column; gap: 6px; } .card-grid { grid-template-columns: 1fr; } .result-heading { align-items: flex-start; gap: 8px; } .result-summary { flex-direction: column; align-items: flex-end; gap: 2px; text-align: right; } .pagination-row { flex-direction: column; gap: 6px; } }
</style>
