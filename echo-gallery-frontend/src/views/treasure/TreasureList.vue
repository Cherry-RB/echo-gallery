<script setup lang="ts">
import CoverImage from '../../components/ui/CoverImage.vue'
import { computed, ref, watch } from 'vue'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { MoreFilled, Plus, Search } from '@element-plus/icons-vue'
import PageHeader from '../../components/ui/PageHeader.vue'
import AppEmptyState from '../../components/ui/AppEmptyState.vue'
import TreasureRankBadge from '../../components/treasure/TreasureRankBadge.vue'
import TreasureFormDialog from '../../components/treasure/TreasureFormDialog.vue'
import TreasureTrialDialog from '../../components/treasure/TreasureTrialDialog.vue'
import TreasureYieldDialog from '../../components/treasure/TreasureYieldDialog.vue'
import TreasureYieldHistory from '../../components/treasure/TreasureYieldHistory.vue'
import HistoryDialog from '../../components/ui/HistoryDialog.vue'
import TreasureRankSelect from '../../components/treasure/TreasureRankSelect.vue'
import { treasureApi } from '../../utils/api/treasureApi'
import { ranks, errorMessage, challengeState, nextRankOf, yieldHistoryDescription } from '../../utils/treasure'
import type { TreasureDetail, TreasureRank, TreasureSummary } from '../../types/treasure'

const router = useRouter()
const queryClient = useQueryClient()
const keyword = ref('')
const search = ref('')
const rank = ref<TreasureRank | ''>('')
const archived = ref(false)
const page = ref(0)
const createVisible = ref(false)
const editingTreasure = ref<TreasureDetail>()
const pendingEdit = ref<number | null>(null)
const openCreate = () => { editingTreasure.value = undefined; createVisible.value = true }
const openEdit = async (treasure: TreasureSummary) => {
  if (pendingEdit.value !== null) return
  pendingEdit.value = treasure.id
  try { editingTreasure.value = await treasureApi.detail(treasure.id); createVisible.value = true }
  catch (e) { ElMessage.error(errorMessage(e)) }
  finally { pendingEdit.value = null }
}
const pendingArchive = ref<number | null>(null)
const trialVisible = ref(false)
const yieldVisible = ref(false)
const historyVisible = ref(false)
const selectedTreasure = ref<TreasureSummary>()
const openTrial = (treasure: TreasureSummary) => { selectedTreasure.value = treasure; trialVisible.value = true }
const openYield = (treasure: TreasureSummary) => { selectedTreasure.value = treasure; yieldVisible.value = true }
const openHistory = (treasure: TreasureSummary) => { selectedTreasure.value = treasure; historyVisible.value = true }
const challengeMode = (treasure: TreasureSummary) => treasure.activeTrial?.status === 'PASSED' || treasure.isArchived ? 'route' : treasure.activeTrial?.targetRank === nextRankOf(treasure.rank) ? 'attempt' : 'edit'
const query = useQuery({
  queryKey: computed(() => ['treasures', 'list', search.value, rank.value, archived.value, page.value]),
  queryFn: () => treasureApi.list({ keyword: search.value, rank: rank.value || undefined, archived: archived.value, page: page.value }),
})
const stats = useQuery({ queryKey: ['treasures', 'stats'], queryFn: treasureApi.stats })
const rankCounts = computed(() => {
  const data = stats.data.value
  return data ? (Object.keys(ranks) as TreasureRank[]).map(rank => ({ rank, count: data[rank.toLowerCase() as 'stone' | 'bronze' | 'silver' | 'gold'] })).filter(item => item.count > 0) : []
})
watch([search, rank, archived], () => { page.value = 0 })
const archive = async (id: number) => {
  if (pendingArchive.value !== null) return
  pendingArchive.value = id
  try {
    await treasureApi.archive(id, !archived.value)
    await Promise.all([queryClient.invalidateQueries({ queryKey: ['treasures'] }), queryClient.invalidateQueries({ queryKey: ['overview'] })])
    if (!query.data.value?.content.length && page.value > 0) page.value--
  } catch (e) { ElMessage.error(errorMessage(e)) } finally { pendingArchive.value = null }
}
const handleCardCommand = (treasure: TreasureSummary, command: string) => {
  if (command === 'edit') void openEdit(treasure)
  else if (command === 'archive') void archive(treasure.id)
}
const saved = (treasure: TreasureDetail) => { if (!editingTreasure.value) void router.push(`/treasures/${treasure.id}`) }
</script>

<template>
  <section class="treasure-list-page app-list-page app-page app-page--workspace">
    <PageHeader title="奇珍展覽館" description="收錄已完成的成果，讓它在現實試煉與價值產出中繼續成長。">
      <template #actions><el-button type="primary" :icon="Plus" @click="openCreate">收入奇珍</el-button></template>
    </PageHeader>
    <div class="treasure-workspace app-workspace app-workspace--edge-to-edge">
    <form class="treasure-toolbar app-toolbar" @submit.prevent="search = keyword.trim()">
      <el-radio-group v-model="archived" class="app-list-scope" aria-label="館藏範圍"><el-radio-button :value="false">館藏</el-radio-button><el-radio-button :value="true">已封存</el-radio-button></el-radio-group>
      <div class="treasure-search-controls app-toolbar__controls">
        <TreasureRankSelect v-model="rank" />
        <el-input v-model="keyword" :prefix-icon="Search" placeholder="搜尋成果名稱" maxlength="255" clearable aria-label="成果名稱" />
        <el-button native-type="submit">查詢</el-button>
      </div>
    </form>
    <div class="treasure-results-heading" aria-live="polite">
      <span>{{ archived ? '已封存' : '目前館藏' }}<template v-if="query.data.value"> · {{ query.data.value.totalElements }} 件{{ search || rank ? '符合條件' : '' }}</template></span>
      <div v-if="!archived && stats.data.value" class="treasure-stats" aria-label="全部未封存館藏統計">
        <span v-if="search || rank">全部館藏 {{ stats.data.value.total }} 件</span>
        <span v-for="item in rankCounts" :key="item.rank">{{ ranks[item.rank] }} {{ item.count }}</span>
      </div>
    </div>
    <div v-if="query.isLoading.value" class="treasure-grid" aria-label="正在讀取館藏"><article v-for="index in 3" :key="index" class="treasure-card app-collection-card"><el-skeleton :rows="3" animated /></article></div>
    <el-result v-else-if="query.isError.value" icon="error" title="無法讀取館藏" sub-title="請稍後重試。"><template #extra><el-button type="primary" @click="query.refetch()">重新載入</el-button></template></el-result>
    <div v-else>
      <div v-if="query.data.value?.content.length" class="treasure-grid">
        <article v-for="treasure in query.data.value.content" :key="treasure.id" class="treasure-card app-collection-card">
          <div class="treasure-card-heading">
            <TreasureRankBadge :rank="treasure.rank" />
            <div class="card-quick-actions">
            <button type="button" class="quick-output" :aria-label="`為${treasure.title}新增產出`" @click="openYield(treasure)">＋ 產出</button>
            <button v-if="treasure.yieldCount > 0" type="button" class="output-count" :aria-label="`查看 ${treasure.yieldCount} 筆價值產出`" @click="openHistory(treasure)">{{ treasure.yieldCount }}</button>
            <el-dropdown trigger="click" @command="handleCardCommand(treasure, $event)">
              <el-button text circle :icon="MoreFilled" :disabled="pendingArchive !== null || pendingEdit !== null" :loading="pendingArchive === treasure.id || pendingEdit === treasure.id" :aria-label="`${treasure.title}的操作`" />
              <template #dropdown><el-dropdown-menu><el-dropdown-item command="edit">編輯成果</el-dropdown-item><el-dropdown-item command="archive" divided>{{ archived ? '還原奇珍' : '封存奇珍' }}</el-dropdown-item></el-dropdown-menu></template>
            </el-dropdown>
            </div>
          </div>
          <router-link :to="`/treasures/${treasure.id}`" class="treasure-main">
            <CoverImage :src="treasure.coverImageUrl" variant="collection" lazy />
            <h2>{{ treasure.title }}</h2>
            <p v-if="treasure.description" class="treasure-preview">{{ treasure.description }}</p>
          </router-link>
          <footer v-if="treasure.activeTrial" class="treasure-card-footer" :data-rank="treasure.activeTrial.targetRank">
            <el-tooltip :content="treasure.activeTrial.title" :show-after="400" placement="top"><p class="challenge-title"><span class="challenge-status"><span class="challenge-rank">{{ ranks[treasure.activeTrial.targetRank!] }}</span> · {{ challengeState(treasure.activeTrial, treasure.rank) }}</span> {{ treasure.activeTrial.title }}</p></el-tooltip>
            <button type="button" class="challenge-update" @click="openTrial(treasure)">{{ treasure.isArchived || treasure.activeTrial.status === 'PASSED' ? '查看' : treasure.activeTrial.targetRank === nextRankOf(treasure.rank) ? '更新' : '編輯' }}</button>
          </footer>
        </article>
      </div>
      <AppEmptyState v-else :description="search || rank ? '沒有符合條件的成果。' : archived ? '目前沒有封存成果。' : '完成過的作品與實踐，可以成為第一件館藏。'"><el-button v-if="!archived && !search && !rank" type="primary" :icon="Plus" @click="openCreate">收入奇珍</el-button></AppEmptyState>
      <el-pagination v-if="(query.data.value?.totalElements ?? 0) > 20" class="app-list-pagination" :current-page="page + 1" :page-size="20" :total="query.data.value?.totalElements ?? 0" layout="total, prev, pager, next" @current-change="page = $event - 1" />
    </div>
    </div>
    <TreasureFormDialog v-model="createVisible" :treasure="editingTreasure" @saved="saved" />
    <TreasureTrialDialog v-if="trialVisible && selectedTreasure" v-model="trialVisible" :treasure-id="selectedTreasure.id" :current-rank="selectedTreasure.rank" :next-rank="nextRankOf(selectedTreasure.rank)" :archived="selectedTreasure.isArchived" :initial-rank="selectedTreasure.activeTrial?.targetRank ?? undefined" :initial-mode="challengeMode(selectedTreasure)" />
    <HistoryDialog v-if="historyVisible && selectedTreasure" v-model="historyVisible" :title="selectedTreasure.title + '・產出歷程'" :description="yieldHistoryDescription">
      <template #actions><button type="button" class="quick-output" @click="openYield(selectedTreasure)">＋產出</button></template>
      <TreasureYieldHistory :treasure-id="selectedTreasure.id" :archived="selectedTreasure.isArchived" embedded />
      <template #footer><el-button @click="historyVisible = false">關閉</el-button></template>
    </HistoryDialog>
    <TreasureYieldDialog v-if="yieldVisible && selectedTreasure" v-model="yieldVisible" :treasure-id="selectedTreasure.id" />
  </section>
</template>

<style scoped>
.treasure-toolbar { display: flex; flex-wrap: wrap; justify-content: space-between; gap: var(--space-md); align-items: center; }
.treasure-search-controls { display: flex; gap: var(--space-sm); align-items: center; flex-wrap: wrap; }
.treasure-search-controls :deep(.el-input) { width: 260px; }
.treasure-search-controls :deep(.el-select) { width: 140px; }
.treasure-results-heading { display: flex; justify-content: space-between; flex-wrap: wrap; gap: var(--space-sm); margin: var(--space-md) 0 var(--space-md); font-size: var(--type-secondary); color: var(--el-text-color-regular); }
.treasure-stats { display: flex; flex-wrap: wrap; gap: var(--space-md); color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.treasure-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: var(--space-md); }
.treasure-card { padding: var(--space-md); min-width: 0; }
.treasure-card { display: flex; flex-direction: column; gap: var(--space-sm); }
.treasure-card-heading { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--space-sm); }
.card-quick-actions { display: flex; flex-wrap: wrap; align-items: center; min-width: 0; max-width: 100%; margin-left: auto; gap: var(--space-sm); }
.card-quick-actions :deep(.el-button) { margin-left: 0; }
.output-count { display: inline-flex; align-items: center; justify-content: center; min-width: 22px; min-height: 22px; padding: 0 var(--space-xs); border: 1px solid var(--el-color-primary-light-7); border-radius: var(--radius-sm); background: var(--el-color-primary-light-9); color: var(--el-color-primary); cursor: pointer; font: inherit; font-size: var(--type-meta); font-weight: var(--weight-semibold); font-variant-numeric: tabular-nums; line-height: 1; }
.challenge-rank { color: var(--rank-text); background: var(--rank-tint); border-radius: var(--radius-sm); padding: 0 var(--space-2xs); }
.challenge-status { color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.challenge-status { margin-inline-end: var(--space-xs); }
.challenge-rank { margin-inline-end: var(--space-2xs); }
.challenge-title { display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; overflow: hidden; min-width: 0; margin: 0; color: var(--el-text-color-regular); font-size: var(--type-ui); line-height: var(--leading-ui); overflow-wrap: anywhere; }
.treasure-main { color: inherit; text-decoration: none; display: block; flex: 1; }
.treasure-main :deep(.cover-media) { margin-bottom: var(--space-sm); }
.treasure-main h2 { font-size: var(--type-card-title); margin: 0 0 var(--space-xs); line-height: var(--leading-card-title); font-weight: var(--weight-semibold); color: var(--el-text-color-primary); overflow-wrap: anywhere; }
.treasure-preview { display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 3; overflow: hidden; white-space: pre-line; overflow-wrap: anywhere; font-size: var(--type-body); color: var(--el-text-color-secondary); line-height: var(--leading-body); }
.treasure-card-footer { display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: center; gap: var(--space-xs); padding-top: var(--space-sm); border-top: 1px solid var(--el-border-color-lighter); }
.quick-output, .challenge-update { padding: 0; border: 0; background: transparent; color: var(--el-color-primary); cursor: pointer; font: inherit; font-size: var(--type-caption); line-height: var(--leading-ui); white-space: nowrap; }
.quick-output:hover, .quick-output:focus-visible, .challenge-update:hover, .challenge-update:focus-visible { color: var(--el-color-primary-light-3); text-decoration: underline; }
.output-count:hover, .output-count:focus-visible { border-color: var(--el-color-primary); background: var(--el-color-primary-light-8); }
.treasure-main:hover h2 { color: var(--el-color-primary); }
@media (max-width: 1200px) { .treasure-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 760px) {
  .treasure-grid { grid-template-columns: 1fr; }
  .treasure-toolbar { align-items: stretch; flex-direction: column; }
  .treasure-search-controls { display: grid; grid-template-columns: minmax(0, 1fr) auto; }
  .treasure-search-controls :deep(.el-select) { grid-column: 1 / -1; width: 100%; }
  .treasure-search-controls :deep(.el-input) { width: 100%; min-width: 0; }
}
</style>
