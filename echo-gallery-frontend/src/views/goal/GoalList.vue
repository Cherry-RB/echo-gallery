<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/vue-query'
import { useRouter } from 'vue-router'
import PageHeader from '../../components/ui/PageHeader.vue'
import AppEmptyState from '../../components/ui/AppEmptyState.vue'
import RecordCount from '../../components/ui/RecordCount.vue'
import { Plus, Edit, MoreFilled } from '@element-plus/icons-vue'
import GoalEditor from '../../components/goal/GoalEditor.vue'
import GoalPinnedOrderDialog from '../../components/goal/GoalPinnedOrderDialog.vue'
import { goalApi } from '../../utils/api/goalApi'
import { goalStatuses, isGoalPeriodDue } from '../../types/goal'
import type { GoalQuery, GoalView } from '../../types/goal'
const router = useRouter(), client = useQueryClient()
const defaults = (): GoalQuery => ({ keyword: '', statuses: [], archiveScope: 'UNARCHIVED' })
const draft = ref(defaults()), applied = ref(defaults()), page = ref(0), visible = ref(false), ordering = ref(false), editing = ref<GoalView | null>(null)
const owner = localStorage.getItem('id') || ''
const query = useQuery({ queryKey: computed(() => ['goals', owner, 'list', applied.value, page.value]), queryFn: async () => { const params = { ...applied.value, page: page.value }; return { ...await goalApi.list(params), params } }, placeholderData: keepPreviousData })
const lastResult = ref<typeof query.data.value>()
watch(query.data, value => { if (value && !query.isPlaceholderData.value) lastResult.value = value })
const result = computed(() => query.data.value ?? lastResult.value)
const actionBusy = ref<number | null>(null), actionError = ref('')
async function quickAction(goal: GoalView, command: string) {
  if (actionBusy.value !== null) return
  actionBusy.value = goal.id; actionError.value = ''
  try {
    const definition = { ...goal.definition }
    if (command === 'pin') definition.pinned = !definition.pinned
    else if (command === 'archive') definition.archived = !definition.archived
    else return
    await goalApi.save(goal.id, definition, goal.version, command === 'pin' ? '調整總覽固定設定' : '調整封存狀態', crypto.randomUUID())
    await client.invalidateQueries({ queryKey: ['goals'] })
  } catch { actionError.value = '操作未完成，可能另一裝置已更新。請重新查詢後再試。' }
  finally { actionBusy.value = null }
}
function selectScope(value: NonNullable<GoalQuery['archiveScope']>) {
  draft.value.archiveScope = value
  if (applied.value.archiveScope === value) return
  page.value = 0
  applied.value = { ...applied.value, archiveScope: value }
}
function search() {
  const next = JSON.parse(JSON.stringify(draft.value)) as GoalQuery
  if (JSON.stringify(next) === JSON.stringify(applied.value) && page.value === 0) void query.refetch()
  else { page.value = 0; applied.value = next }
}
const scopeLabel = computed(() => ({ UNARCHIVED: '未封存目標', ARCHIVED: '已封存目標', ALL: '全部目標' })[result.value?.params.archiveScope ?? 'UNARCHIVED'])
function edit(goal: GoalView | null) { editing.value = goal; visible.value = true }
function openGoal(goal: GoalView) { void router.push(`/goals/${goal.id}`) }
async function saved(goal: GoalView) { await client.invalidateQueries({ queryKey: ['goals'] }); router.push(`/goals/${goal.id}`) }
</script>
<template>
  <section class="goal-page app-list-page app-page app-page--workspace">
    <PageHeader title="管理目標" description="為一段時間選定方向，也保留探索後調整的空間。">
      <template #actions>
        <el-button @click="router.push('/board/goals')">目標看板</el-button>
        <el-button @click="ordering = true">排列固定目標</el-button>
        <el-button type="primary" :icon="Plus" @click="edit(null)">建立目標</el-button>
      </template>
    </PageHeader>
    <div class="app-workspace app-workspace--edge-to-edge">
      <form class="goal-toolbar app-toolbar" role="search" aria-label="搜尋與篩選目標" @submit.prevent="search">
        <el-radio-group :model-value="applied.archiveScope" class="app-list-scope" aria-label="封存範圍" @update:model-value="selectScope"><el-radio-button value="UNARCHIVED">未封存</el-radio-button><el-radio-button value="ARCHIVED">已封存</el-radio-button><el-radio-button value="ALL">全部</el-radio-button></el-radio-group>
        <div class="goal-filter-panel"><div class="goal-filter-controls app-toolbar__controls">
        <el-input v-model="draft.keyword" aria-label="搜尋目標" placeholder="搜尋目標" clearable />
        <el-select v-model="draft.statuses" aria-label="目標狀態" multiple collapse-tags collapse-tags-tooltip clearable placeholder="全部狀態">
          <el-option v-for="(label, key) in goalStatuses" :key="key" :value="key" :label="label" />
        </el-select>
        <el-button native-type="submit" type="primary" :loading="query.isFetching.value">查詢</el-button><el-button native-type="button" @click="draft = { ...defaults(), archiveScope: applied.archiveScope }">重設條件</el-button>
        </div></div>
      </form>
      <p class="goal-results" aria-live="polite">{{ scopeLabel }}<template v-if="result"> · {{ result.params.statuses?.length ? result.params.statuses.map(s => goalStatuses[s]).join('、') : '全部狀態' }} · 符合 {{ result.totalElements }} 個目標</template></p>
      <el-alert v-if="query.isError.value" title="目標載入失敗，原結果仍保留。" type="error" :closable="false"><el-button @click="query.refetch()">重新載入</el-button></el-alert>
      <el-alert v-if="actionError" :title="actionError" type="error" :closable="false" />
      <div v-if="query.isPending.value && !result" class="goal-list">
        <el-card v-for="index in 3" :key="index" shadow="never" class="app-collection-card"><el-skeleton :rows="3" animated /></el-card>
      </div>
      <template v-else-if="result">
        <AppEmptyState v-if="!result.content.length" :description="result.params.keyword || result.params.statuses?.length || result.params.archiveScope !== 'UNARCHIVED' ? '沒有符合目前條件的目標，可以調整篩選再試一次。' : '目前還沒有目標。想為一段時間選定方向時，再建立也可以。'">
          <el-button v-if="!result.params.keyword && !result.params.statuses?.length && result.params.archiveScope === 'UNARCHIVED'" type="primary" :icon="Plus" @click="edit(null)">建立第一個目標</el-button>
        </AppEmptyState>
        <div v-else class="goal-list">
          <el-card v-for="goal in result.content" :key="goal.id" shadow="never" class="goal-panel app-collection-card" role="link" tabindex="0" :aria-label="`查看目標：${goal.definition.name}`" @click="openGoal(goal)" @keydown.enter.self="openGoal(goal)" @keydown.space.self.prevent="openGoal(goal)">
            <div class="goal-card-heading">
              <h2 class="goal-title">{{ goal.definition.name }}</h2>
              <div class="goal-card-actions" @click.stop @keydown.stop><el-button type="primary" link size="small" :icon="Edit" :aria-label="`編輯目標：${goal.definition.name}`" :disabled="actionBusy !== null" @click="edit(goal)">編輯</el-button><el-dropdown trigger="click" :disabled="actionBusy !== null" @command="quickAction(goal, $event)"><el-button text circle :icon="MoreFilled" :loading="actionBusy === goal.id" :aria-label="`${goal.definition.name}的更多操作`" /><template #dropdown><el-dropdown-menu><el-dropdown-item command="pin">{{ goal.definition.pinned ? '取消固定於總覽' : '固定於總覽' }}</el-dropdown-item><el-dropdown-item command="archive" divided>{{ goal.definition.archived ? '還原目標' : '封存目標' }}</el-dropdown-item></el-dropdown-menu></template></el-dropdown></div>
            </div>
            <div class="goal-metadata"><el-tag size="small" type="info" effect="plain">{{ goalStatuses[goal.effectiveStatus] }}</el-tag><span>{{ goal.definition.startsOn }} — {{ goal.definition.endsOn }}</span></div>
            <p v-if="isGoalPeriodDue(goal)" class="goal-due">期間已到，待回顧</p>
            <p v-if="goal.definition.expectation" class="goal-expectation">{{ goal.definition.expectation }}</p>
            <div v-if="goal.definition.metrics.includes('M01')" class="goal-card-count"><span>目前相關卡片</span><RecordCount :count="goal.cardCount" /></div>
          </el-card>
        </div>
        <el-pagination v-if="result.totalElements > 20" class="app-list-pagination" :disabled="query.isFetching.value" :current-page="(result.params.page ?? 0) + 1" :page-size="20" :total="result.totalElements" layout="total, prev, pager, next" @current-change="(value: number) => page = value - 1" />
      </template>
    </div>
    <GoalEditor v-model="visible" :goal="editing" @saved="saved" />
    <GoalPinnedOrderDialog v-model="ordering" />
  </section>
</template>
<style scoped>
.goal-toolbar { align-items: flex-start; flex-wrap: wrap; gap: var(--space-md) var(--space-xl); margin-bottom: var(--space-md); }
.goal-filter-panel { flex: 1 1 640px; max-width: 820px; margin-left: auto; }
.goal-filter-controls { display: grid; grid-template-columns: minmax(200px, 1.2fr) minmax(160px, .8fr) auto auto; gap: var(--space-sm); }
.goal-filter-controls .el-input, .goal-filter-controls .el-select { width: 100%; }
.goal-toolbar .el-button { margin-left: 0; }
.goal-results { margin-bottom: var(--space-md); color: var(--el-text-color-regular); font-size: var(--type-secondary); }
.goal-list { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 440px), 1fr)); gap: var(--space-md); }
.goal-panel { cursor: pointer; box-shadow: none; }
.goal-card-heading, .goal-metadata, .goal-card-count { display: flex; align-items: center; gap: var(--space-xs); }
.goal-card-heading { align-items: flex-start; justify-content: space-between; gap: var(--space-md); }
.goal-card-actions { display: flex; align-items: center; gap: var(--space-xs); flex-shrink: 0; }
.goal-title { min-width: 0; color: var(--el-text-color-primary); font-size: var(--type-card-title); line-height: var(--leading-card-title); overflow-wrap: anywhere; }
.goal-panel:hover .goal-title, .goal-panel:focus-visible .goal-title { color: var(--el-color-primary); }
.goal-metadata { flex-wrap: wrap; margin-top: var(--space-sm); color: var(--el-text-color-secondary); font-size: var(--type-metadata); }
.goal-expectation { display: -webkit-box; margin-top: var(--space-sm); overflow: hidden; white-space: pre-line; overflow-wrap: anywhere; -webkit-box-orient: vertical; -webkit-line-clamp: 3; }
.goal-card-count { margin-top: var(--space-md); color: var(--el-text-color-secondary); font-size: var(--type-secondary); }
.goal-due { margin-top: var(--space-xs); color: var(--el-color-warning); font-size: var(--type-metadata); }
@media (max-width: 760px) {
  .goal-toolbar { align-items: stretch; flex-direction: column; }
  .goal-filter-panel { flex: 0 0 auto; max-width: none; width: 100%; margin-left: 0; }
  .goal-filter-controls { grid-template-columns: minmax(0, 1fr) auto; }
  .goal-filter-controls > .el-input, .goal-filter-controls > .el-select { grid-column: 1 / -1; }
}
</style>
