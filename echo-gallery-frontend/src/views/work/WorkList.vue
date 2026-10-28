<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter, type LocationQueryRaw } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { workApi } from '../../utils/api/workApi'
import { workStages } from '../../types/work'
import WorkPreview from '../../components/work/WorkPreview.vue'
import WorkFormDialog from '../../components/work/WorkFormDialog.vue'
import PageHeader from '../../components/ui/PageHeader.vue'
import AppEmptyState from '../../components/ui/AppEmptyState.vue'
import { Plus, Search } from '@element-plus/icons-vue'
const route = useRoute(), router = useRouter(), creating = ref(false)
function values(value: unknown): string[] {
  return [...new Set((Array.isArray(value) ? value : value ? [value] : []).flatMap(v => String(v).split(',')).filter(Boolean))]
}
type ArchiveScope = 'false' | 'true' | 'all'
function filtersFromRoute() {
  return {
    keyword: String(route.query.keyword || ''),
    experiments: [...values(route.query.experimentIds ?? route.query.experimentId), ...(route.query.unassigned === 'true' ? ['none'] : [])],
    stages: values(route.query.stages ?? route.query.stage),
    published: String(route.query.published || ''),
  }
}
const filters = reactive(filtersFromRoute())
const scope = computed(() => (route.query.archived || 'false') as ArchiveScope)
const appliedExperimentSelection = computed(() => [...values(route.query.experimentIds ?? route.query.experimentId), ...(route.query.unassigned === 'true' ? ['none'] : [])])
const params = computed(() => ({ keyword: route.query.keyword || undefined, experimentIds: appliedExperimentSelection.value.filter(v => v !== 'none').join(',') || undefined,
  unassigned: route.query.unassigned || undefined, stages: values(route.query.stages ?? route.query.stage).join(',') || undefined, published: route.query.published || undefined,
  archived: route.query.archived || 'false', page: Number(route.query.page || 1) - 1 }))
const query = useQuery({ queryKey: ['works', 'list', params], queryFn: () => workApi.list(params.value) })
watch(() => query.data.value, data => {
  if (data && !data.content.length && Number(route.query.page || 1) > 1) void router.replace({ query: { ...route.query, page: Math.max(1, data.totalPages) } })
})
const experiments = useQuery({ queryKey: ['work-experiments'], queryFn: workApi.experiments })
const filtered = computed(() => ['keyword', 'experimentId', 'experimentIds', 'unassigned', 'stage', 'stages', 'published'].some(key => !!route.query[key]))
const hasPendingFilters = computed(() => JSON.stringify(filters) !== JSON.stringify(filtersFromRoute()))
function selectScope(archived: ArchiveScope) {
  if (scope.value === archived) return
  void router.replace({ query: { ...route.query, archived: archived === 'false' ? undefined : archived, page: undefined } })
}
function applyFilters() {
  const next: LocationQueryRaw = {
    keyword: filters.keyword.trim() || undefined,
    experimentId: undefined,
    experimentIds: filters.experiments.filter(value => value !== 'none').join(',') || undefined,
    unassigned: filters.experiments.includes('none') ? 'true' : undefined,
    stage: undefined,
    stages: filters.stages.join(',') || undefined,
    published: filters.published || undefined,
    archived: scope.value === 'false' ? undefined : scope.value,
    page: undefined,
  }
  void router.replace({ query: next })
}
function resetFilters() { Object.assign(filters, { keyword: '', experiments: [], stages: [], published: '' }); applyFilters() }
watch(() => route.fullPath, () => Object.assign(filters, filtersFromRoute()))
</script>
<template>
  <main class="work-library app-list-page app-page app-page--workspace">
    <PageHeader title="創作台" description="草稿可以重寫，構想可以推翻。從初稿到完成，作品都能留在這裡繼續打磨。"><template #actions><el-button type="primary" :icon="Plus" @click="creating = true">建立作品</el-button></template></PageHeader>
    <div class="app-workspace app-workspace--edge-to-edge app-stack">
    <form class="work-toolbar app-toolbar" role="search" aria-label="搜尋與篩選作品" @submit.prevent="applyFilters">
      <el-radio-group :model-value="scope" class="app-list-scope" aria-label="作品範圍" @update:model-value="selectScope"><el-radio-button value="false">未封存</el-radio-button><el-radio-button value="true">已封存</el-radio-button><el-radio-button value="all">全部</el-radio-button></el-radio-group>
      <div class="work-filter-panel">
      <div class="work-filters app-toolbar__controls">
      <el-input v-model="filters.keyword" :prefix-icon="Search" clearable maxlength="255" placeholder="搜尋作品名稱" aria-label="搜尋作品名稱" />
      <el-select v-model="filters.experiments" multiple clearable collapse-tags collapse-tags-tooltip placeholder="所有實驗" aria-label="篩選主要實驗，可多選"><el-option label="無主要實驗" value="none" /><el-option v-for="e in experiments.data.value" :key="e.id" :value="String(e.id)" :label="e.title + (e.archived ? '（已封存）' : '')" /></el-select>
      <el-select v-model="filters.stages" multiple clearable collapse-tags collapse-tags-tooltip placeholder="所有階段" aria-label="篩選作品階段，可多選"><el-option v-for="(label, value) in workStages" :key="value" :label="label" :value="value" /></el-select>
      <el-select v-model="filters.published" placeholder="所有發表狀態" aria-label="篩選發表狀態"><el-option label="不限" value="" /><el-option label="未發表" value="false" /><el-option label="已發表" value="true" /></el-select>
      <el-button native-type="submit" type="primary">套用篩選</el-button>
      <el-button v-if="hasPendingFilters || filtered" native-type="button" @click="resetFilters">清除</el-button>
      </div>
      </div>
    </form>
    <div v-if="query.isPending.value" class="work-grid" aria-label="正在載入作品" aria-busy="true"><el-skeleton v-for="i in 3" :key="i" class="work-skeleton app-collection-card" :rows="4" animated /></div>
    <el-result v-else-if="query.isError.value" icon="error" title="無法載入作品"><template #extra><el-button @click="query.refetch()">重新載入</el-button></template></el-result>
    <template v-else>
      <div class="work-results-heading" aria-live="polite"><p>{{ route.query.archived === 'true' ? '已封存' : route.query.archived === 'all' ? '全部作品' : '未封存' }} · {{ query.data.value?.totalElements ?? 0 }} 件作品{{ filtered ? '符合條件' : '' }}</p><span>最近編輯優先</span></div>
      <AppEmptyState v-if="!query.data.value?.content.length" :description="filtered ? '找不到符合條件的作品，可以調整搜尋或篩選。' : route.query.archived === 'true' ? '目前沒有已封存的作品。' : '桌面還空著。不必等到構想完整，第一份粗糙的草稿，也足以成為開始。'" />
      <div v-else class="work-grid"><WorkPreview v-for="w in query.data.value?.content" :key="w.id" :work="w" /></div>
    </template>
    <div v-if="(query.data.value?.totalElements ?? 0) > 20" class="app-list-pagination"><el-pagination :current-page="Number(route.query.page || 1)" :page-size="20" :total="query.data.value?.totalElements ?? 0" layout="total, prev, pager, next" @current-change="router.replace({ query: { ...route.query, page: $event } })" /></div>
    </div>
    <WorkFormDialog v-if="creating" v-model="creating" :experiment-id="appliedExperimentSelection.length === 1 && appliedExperimentSelection[0] !== 'none' ? Number(appliedExperimentSelection[0]) : undefined" @saved="router.push(`/works/${$event.work.id}`)" />
  </main>
</template>
<style scoped>
.work-toolbar { align-items: flex-start; flex-wrap: wrap; gap: var(--space-md) var(--space-xl); }
.work-filter-panel { flex: 1 1 920px; max-width: 1240px; margin-left: auto; }
.work-filters { display: grid; grid-template-columns: minmax(220px, 1.3fr) minmax(180px, 1fr) minmax(160px, .9fr) minmax(150px, .8fr) auto auto; gap: var(--space-sm); }
.work-results-heading { display: flex; align-items: center; justify-content: space-between; gap: var(--space-sm); flex-wrap: wrap; font-size: var(--type-secondary); line-height: var(--leading-ui); color: var(--el-text-color-regular); }
.work-results-heading span { color: var(--el-text-color-secondary); font-size: var(--type-metadata); }
.work-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: var(--space-md); }
.work-skeleton { box-sizing: border-box; padding: var(--space-md); }
@media (max-width: 1200px) { .work-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 1100px) { .work-filter-panel { flex-basis: 100%; max-width: none; } .work-filters { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 760px) { .work-filter-panel { margin-left: 0; width: 100%; } .work-filters { grid-template-columns: minmax(0, 1fr) auto; } .work-filters > .el-input, .work-filters > .el-select { grid-column: 1 / -1; } .work-grid { grid-template-columns: minmax(0, 1fr); } }
</style>
