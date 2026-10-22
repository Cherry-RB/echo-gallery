<script setup lang="ts">
import WorkResume from '../../components/work/WorkResume.vue'
import { computed, ref } from 'vue'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '../../components/ui/PageHeader.vue'
import SectionHeader from '../../components/ui/SectionHeader.vue'
import RecordHistoryTrigger from '../../components/ui/RecordHistoryTrigger.vue'
import HistoryDialog from '../../components/ui/HistoryDialog.vue'
import TreasureRankBadge from '../../components/treasure/TreasureRankBadge.vue'
import TreasureTrialDialog from '../../components/treasure/TreasureTrialDialog.vue'
import TreasureYieldDialog from '../../components/treasure/TreasureYieldDialog.vue'
import TreasureYieldHistory from '../../components/treasure/TreasureYieldHistory.vue'
import TreasureFormDialog from '../../components/treasure/TreasureFormDialog.vue'
import OverviewNavigation from '../../components/overview/OverviewNavigation.vue'
import ExperimentExplorationEditor from '../../components/experiment/ExperimentExplorationEditor.vue'
import ExperimentExplorationHistoryDialog from '../../components/experiment/ExperimentExplorationHistoryDialog.vue'
import ExperimentQuickRecordAction from '../../components/experiment/ExperimentQuickRecordAction.vue'
import { experimentApi } from '../../utils/api/experimentApi'
import { ranks, challengeState, nextRankOf, yieldHistoryDescription } from '../../utils/treasure'
import type { TreasureSummary, TreasureTrial } from '../../types/treasure'
import { overviewApi } from '../../utils/api/overviewApi'
import type { OverviewAttentionSignal } from '../../types/overview'
import type { ExperimentExplorationRecordDto } from '../../types/experiment'

const router = useRouter()
const queryClient = useQueryClient()
const quickRecordExperimentId = ref<number | null>(null)
const quickRecordMode = ref<'TRY' | 'DISCOVERY'>('TRY')
const editingExplorationRecord = ref<ExperimentExplorationRecordDto | null>(null)
const quickRecordVisible = ref(false)
const historyExperimentId = ref<number | null>(null)
const historyVisible = ref(false)
const selectedTreasure = ref<TreasureSummary | null>(null)
const treasureTrialVisible = ref(false)
const treasureYieldVisible = ref(false)
const treasureHistoryVisible = ref(false)
const treasureCreateVisible = ref(false)
const { data: overview, isLoading, isError, refetch } = useQuery({
  queryKey: ['overview', 'current'],
  queryFn: overviewApi.getCurrentOverview,
})

const current = computed(() => overview.value ?? {
  todayReturnPoolCount: 0,
  recurringCardCount: 0,
  pausedCardCount: 0,
  needsProcessingCardCount: 0,
  activeExperimentCount: 0,
  treasureCount: 0,
  experimentTries: [],
  treasureActions: [],
  attentionSignals: [],
})
const treasureActions = computed(() => current.value.treasureActions.filter(
  (item): item is TreasureSummary & { activeTrial: TreasureTrial } => !!item.activeTrial
    && item.activeTrial.targetRank === nextRankOf(item.rank)
    && ['PLANNED', 'ATTEMPTED', 'NOT_PASSED'].includes(item.activeTrial.status),
))
const historyQuery = useQuery({
  queryKey: computed(() => ['experiment-exploration', historyExperimentId.value]),
  queryFn: () => experimentApi.getExploration(historyExperimentId.value!),
  enabled: computed(() => historyVisible.value && historyExperimentId.value !== null),
})

const currentItems = computed(() => [
  { key: 'today-return', label: '今日回流', value: current.value.todayReturnPoolCount, unit: '張卡片' },
  { key: 'recurring', label: '回流中', value: current.value.recurringCardCount, unit: '張卡片' },
  { key: 'paused', label: '暫停中', value: current.value.pausedCardCount, unit: '張卡片' },
  { key: 'processing', label: '待整理', value: current.value.needsProcessingCardCount, unit: '張卡片' },
  { key: 'experiments', label: '活躍實驗場', value: current.value.activeExperimentCount, unit: '個' },
  { key: 'treasures', label: '館藏奇珍', value: current.value.treasureCount, unit: '件' },
])

const attentionItems = computed(() => (current.value.attentionSignals ?? []).map(signal => ({
  ...signal,
  title: signal.key === 'processing'
    ? `${signal.cardCount} 張卡片等待你決定如何處理`
    : `${signal.cardCount} 張卡片累積多次稍後再看`,
  actionLabel: signal.key === 'processing' ? '查看待整理卡片' : '查看稍後再看',
})))

const openCard = (cardId: number) => router.push({ name: 'CardDetail', params: { id: cardId }, query: { from: 'overview' } })
const openExperiment = (experimentId: number) => router.push({ name: 'ExperimentDetail', params: { id: experimentId } })
const openTreasureTrial = (treasure: TreasureSummary) => {
  selectedTreasure.value = treasure
  treasureTrialVisible.value = true
}
const openTreasureYield = (treasure: TreasureSummary) => {
  selectedTreasure.value = treasure
  treasureYieldVisible.value = true
}
const openTreasureHistory = (treasure: TreasureSummary) => {
  selectedTreasure.value = treasure
  treasureHistoryVisible.value = true
}
const openQuickRecord = (experimentId: number, mode: 'TRY' | 'DISCOVERY') => {
  quickRecordExperimentId.value = experimentId
  quickRecordMode.value = mode
  editingExplorationRecord.value = null
  quickRecordVisible.value = true
}
const editExplorationRecord = (record: ExperimentExplorationRecordDto) => {
  if (historyExperimentId.value === null) return
  quickRecordExperimentId.value = historyExperimentId.value
  quickRecordMode.value = 'DISCOVERY'
  editingExplorationRecord.value = record
  quickRecordVisible.value = true
}
const deleteExplorationRecord = async (recordId: number) => {
  if (historyExperimentId.value === null) return
  try {
    await ElMessageBox.confirm('刪除這筆探索紀錄？刪除後無法復原；已整理成卡片的內容仍會保留。', '刪除探索紀錄', {
      confirmButtonText: '刪除', cancelButtonText: '取消', type: 'warning',
    })
  } catch {
    return
  }
  try {
    await experimentApi.deleteExplorationRecord(historyExperimentId.value, recordId)
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['experiment-exploration', historyExperimentId.value] }),
      queryClient.invalidateQueries({ queryKey: ['experiments'] }),
      queryClient.invalidateQueries({ queryKey: ['overview'] }),
    ])
    ElMessage.success('探索紀錄已刪除。')
  } catch {
    ElMessage.error('刪除探索紀錄失敗，請稍後再試。')
  }
}
const openHistory = (experimentId: number) => {
  historyExperimentId.value = experimentId
  historyVisible.value = true
}
const openCardReturn = () => router.push('/overview/cards')
const openCurrentItem = (key: string) => {
  if (key === 'today-return') router.push('/board/today')
  else if (key === 'experiments') router.push('/experiments')
  else if (key === 'treasures') router.push('/treasures')
  else openCardReturn()
}
const attentionSymbol = (kind: OverviewAttentionSignal['key']) => kind === 'processing' ? '□' : '↗'
</script>

<template>
  <section class="overview-page app-page app-page--workspace">
    <PageHeader title="現在行動" description="有些嘗試尚未落幕，有些珍藏仍有餘響。眼前的一切，正等待你的下一個選擇。">
      <template #actions><OverviewNavigation /></template>
    </PageHeader>

    <div class="overview-workspace app-workspace app-workspace--edge-to-edge">
      <div class="overview-stack app-stack">
        <WorkResume />
    <div v-if="isLoading" class="overview-state">正在整理觀測資料…</div>
    <el-result v-else-if="isError" icon="error" title="暫時無法取得觀測資料" sub-title="請稍後再試。">
      <template #extra><el-button type="primary" @click="refetch()">重新載入</el-button></template>
    </el-result>

    <template v-else-if="overview">
      <section v-if="current.experimentTries.length" class="experiment-tries-panel" aria-labelledby="overview-experiment-tries-title">
        <div class="section-title-row">
          <div>
            <span class="section-eyebrow">實驗場</span>
            <h2 id="overview-experiment-tries-title">尚未落幕的探索（{{ current.activeExperimentCount }}）</h2>
          </div>
          <router-link to="/experiments">查看全部</router-link>
        </div>
        <div class="experiment-try-list">
          <article
            v-for="experimentTry in current.experimentTries"
            :key="experimentTry.experimentId"
            class="experiment-try-item"
          >
            <button type="button" class="experiment-try-main" @click="openExperiment(experimentTry.experimentId)">
              <el-tooltip :content="experimentTry.experimentTitle" :show-after="400" placement="top" popper-class="content-tooltip">
                <span>{{ experimentTry.experimentTitle }}</span>
              </el-tooltip>
              <el-tooltip v-if="experimentTry.currentTry" :content="experimentTry.currentTry" :show-after="400" placement="top" popper-class="content-tooltip">
                <strong>{{ experimentTry.currentTry }}</strong>
              </el-tooltip>
              <p v-else class="experiment-try-empty">新的試法尚未寫下。不妨先翻翻收集的材料。</p>
            </button>
            <div class="experiment-try-record-action">
              <ExperimentQuickRecordAction @select="openQuickRecord(experimentTry.experimentId, $event)" />
              <button v-if="experimentTry.explorationRecordCount > 0" type="button" class="exploration-record-count" :aria-label="`查看 ${experimentTry.explorationRecordCount} 筆探索紀錄`" @click.stop="openHistory(experimentTry.experimentId)">{{ experimentTry.explorationRecordCount }}</button>
            </div>
          </article>
        </div>
      </section>

      <div class="action-grid" :class="{ 'action-grid--single': !attentionItems.length }">
        <section class="action-panel" aria-labelledby="overview-treasure-title">
          <SectionHeader id="overview-treasure-title" title="館藏仍有餘響" eyebrow="奇珍展覽館" :description="treasureActions.length ? '沿著已有的來歷，看看哪件奇珍還有待探索的可能。' : undefined">
            <template #actions><router-link to="/treasures" class="treasure-gallery-link">查看館藏</router-link></template>
          </SectionHeader>
          <div v-if="treasureActions.length" class="treasure-action-list">
            <article v-for="item in treasureActions" :key="item.id" class="treasure-action-item">
              <header class="treasure-action-heading">
                <div class="treasure-action-context">
                  <TreasureRankBadge :rank="item.rank" />
                  <router-link :to="`/treasures/${item.id}`" class="treasure-action-title">{{ item.title }}</router-link>
                </div>
                <div class="treasure-output-controls">
                  <button type="button" class="treasure-yield-trigger" :aria-label="`為${item.title}記錄收益`" @click="openTreasureYield(item)">＋記收益</button>
                  <RecordHistoryTrigger v-if="item.yieldCount > 0" label="收益" :count="item.yieldCount" count-only @click="openTreasureHistory(item)" />
                </div>
              </header>
              <button type="button" class="treasure-challenge-main" :data-rank="item.activeTrial.targetRank" :aria-label="`查看${item.title}的試煉：${item.activeTrial.title}`" @click="openTreasureTrial(item)">
                <span class="treasure-challenge-title">{{ ranks[item.activeTrial.targetRank!] }}試煉進行中：{{ item.activeTrial.title }}</span>
                <span class="treasure-challenge-entry">查看試煉</span>
                <span class="treasure-challenge-criteria">{{ item.activeTrial.criteria }}</span>
                <span class="treasure-action-status">{{ challengeState(item.activeTrial, item.rank) }}</span>
              </button>
            </article>
          </div>
          <div v-else-if="current.treasureCount === 0" class="treasure-empty-state">
            <p>展櫃尚且空著。第一件奇珍，會有什麼來歷？</p>
            <el-button type="primary" @click="treasureCreateVisible = true">收入奇珍</el-button>
          </div>
          <div v-else class="treasure-empty-state">
            <p>目前沒有進行中的試煉。可以前往館藏規劃下一步，或記錄已發生的收益。</p>
            <el-button @click="router.push('/treasures')">查看館藏</el-button>
          </div>
        </section>

        <section v-if="attentionItems.length" class="action-panel attention-panel" aria-labelledby="overview-attention-title">
          <div class="section-title-row">
            <div>
              <span class="section-eyebrow">值得看一眼</span>
              <h2 id="overview-attention-title">尚待留意的事</h2>
            </div>
          </div>
          <div class="attention-list">
            <article v-for="item in attentionItems" :key="item.key" class="attention-item">
              <span aria-hidden="true">{{ attentionSymbol(item.key) }}</span>
              <div>
                <h3>{{ item.title }}</h3>
                <button type="button" @click="openCardReturn">{{ item.actionLabel }}</button>
              </div>
            </article>
          </div>
        </section>
      </div>

      <section class="current-strip" aria-labelledby="overview-current-title">
        <div class="section-title-row">
          <div>
            <span class="section-eyebrow">此刻</span>
            <h2 id="overview-current-title">手中已有的積累</h2>
          </div>
          <router-link to="/overview/cards">查看卡片與回流</router-link>
        </div>
        <div class="current-stat-list">
          <button
            v-for="item in currentItems"
            :key="item.key"
            type="button"
            class="current-stat"
            @click="openCurrentItem(item.key)"
          >
            <strong>{{ item.value }}</strong><span>{{ item.unit }}</span>
            <small>{{ item.label }}</small>
          </button>
        </div>
      </section>

    </template>
      </div>
    </div>

    <TreasureFormDialog v-if="treasureCreateVisible" v-model="treasureCreateVisible" />
    <TreasureTrialDialog v-if="treasureTrialVisible && selectedTreasure" v-model="treasureTrialVisible" :treasure-id="selectedTreasure.id" :current-rank="selectedTreasure.rank" :next-rank="nextRankOf(selectedTreasure.rank)" :initial-rank="nextRankOf(selectedTreasure.rank) ?? undefined" initial-mode="route" @saved="treasureTrialVisible = false" />
    <TreasureYieldDialog v-if="treasureYieldVisible && selectedTreasure" v-model="treasureYieldVisible" :treasure-id="selectedTreasure.id" />
    <HistoryDialog v-if="treasureHistoryVisible && selectedTreasure" v-model="treasureHistoryVisible" :title="selectedTreasure.title + '・收益歷程'" :description="yieldHistoryDescription">
      <template #actions><el-button link type="primary" @click="openTreasureYield(selectedTreasure)">＋記收益</el-button></template>
      <TreasureYieldHistory :treasure-id="selectedTreasure.id" embedded />
    </HistoryDialog>
    <ExperimentExplorationEditor
      v-if="quickRecordExperimentId !== null"
      v-model="quickRecordVisible"
      :experiment-id="quickRecordExperimentId"
      :mode="quickRecordMode"
      :record="editingExplorationRecord"
      @closed="quickRecordExperimentId = null; editingExplorationRecord = null"
    />
    <ExperimentExplorationHistoryDialog
      :experiment-id="historyExperimentId ?? undefined"
      v-if="historyExperimentId !== null"
      v-model="historyVisible"
      :records="historyQuery.data.value?.records ?? []"
      :loading="historyQuery.isLoading.value"
      manageable
      :show-organize="false"
      @edit="editExplorationRecord"
      @delete="deleteExplorationRecord"
      @open-card="openCard"
    />
  </section>
</template>

<style scoped>
.section-eyebrow { display: block; color: var(--el-text-color-secondary); font-size: var(--type-meta); font-weight: 600; letter-spacing: .06em; text-transform: uppercase; }
.overview-state { padding: 48px 0; color: var(--el-text-color-secondary); text-align: center; }
.current-strip, .experiment-tries-panel, .action-panel, .period-section, .content-panel, .activity-disclosure { min-width: 0; border: 1px solid var(--el-border-color-light); border-radius: 8px; background: var(--el-bg-color); }
.current-strip, .experiment-tries-panel, .period-section, .content-panel, .action-panel { padding: var(--panel-padding); }
.section-title-row, .period-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.section-title-row h2, .period-heading h2 { margin: 4px 0 0; font-size: var(--type-section-title); }
.section-title-row > a { color: var(--el-color-primary); font-size: var(--type-meta); text-decoration: none; white-space: nowrap; }
.section-title-row > a:hover { text-decoration: underline; }
.info-icon { margin-top: 4px; color: var(--el-text-color-placeholder); cursor: help; }
.current-stat-list { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); margin-top: 16px; }
.current-stat { min-width: 0; padding: 3px 14px; border: 0; border-left: 1px solid var(--el-border-color-lighter); background: transparent; color: inherit; cursor: pointer; font: inherit; text-align: left; }
.current-stat:first-child { padding-left: 0; border-left: 0; }
.current-stat:last-child { padding-right: 0; }
.current-stat:hover strong, .current-stat:focus-visible strong { color: var(--el-color-primary); }
.current-stat strong, .period-figure strong, .activity-item strong { color: var(--el-text-color-primary); font-size: 25px; font-variant-numeric: tabular-nums; }
.current-stat > span, .period-figure > div > span { margin-left: 3px; color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.current-stat small { display: block; margin-top: 5px; color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.experiment-try-list { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; margin-top: 16px; }
.experiment-try-item { display: flex; align-items: flex-start; min-width: 0; padding: 13px 14px; overflow: hidden; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; background: var(--surface-summary); }
.experiment-try-main { min-width: 0; flex: 1; padding: 0; border: 0; background: transparent; color: inherit; cursor: pointer; font: inherit; text-align: left; }
.experiment-try-main span { display: block; overflow: hidden; color: var(--el-color-primary); font-size: var(--type-meta); text-overflow: ellipsis; white-space: nowrap; }
.experiment-try-main strong { display: -webkit-box; margin-top: 6px; overflow: hidden; color: var(--el-text-color-primary); font-size: var(--type-ui); font-weight: 500; line-height: var(--leading-ui); overflow-wrap: anywhere; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.experiment-try-empty { margin: 6px 0 0; color: var(--el-text-color-secondary); font-size: var(--type-caption); line-height: var(--leading-ui); }
.experiment-try-item:hover, .experiment-try-item:focus-within { border-color: var(--el-color-primary-light-5); }
.experiment-try-main:hover strong, .experiment-try-main:focus-visible strong { color: var(--el-color-primary); }
.experiment-try-record-action { display: inline-flex; flex: 0 0 auto; align-items: center; gap: var(--space-xs); margin-left: var(--space-sm); }
.exploration-record-count { display: inline-flex; align-items: center; justify-content: center; min-width: 22px; min-height: 22px; padding: 0 var(--space-xs); border: 1px solid var(--el-color-primary-light-7); border-radius: var(--radius-sm); background: var(--el-color-primary-light-9); color: var(--el-color-primary); cursor: pointer; font: inherit; font-size: var(--type-meta); font-weight: var(--weight-semibold); font-variant-numeric: tabular-nums; line-height: 1; }
.exploration-record-count:hover, .exploration-record-count:focus-visible { border-color: var(--el-color-primary); background: var(--el-color-primary-light-8); }
.action-grid, .content-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.action-grid--single { grid-template-columns: minmax(0, 1fr); }
.action-grid { align-items: start; }
.action-panel { min-width: 0; border-radius: var(--panel-radius); }
.treasure-gallery-link { color: var(--el-color-primary); font-size: var(--type-meta); text-decoration: none; white-space: nowrap; }
.treasure-gallery-link:hover { text-decoration: underline; }
.treasure-action-list { margin-top: var(--space-md); }
.treasure-action-item { min-width: 0; padding: var(--space-md) 0; border-top: 1px solid var(--el-border-color-lighter); }
.treasure-action-item:first-child { padding-top: 0; border-top: 0; }
.treasure-action-heading { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--space-xs) var(--space-sm); }
.treasure-action-context { display: flex; flex: 1 1 180px; min-width: 0; align-items: center; gap: var(--space-sm); }
.treasure-action-context > :first-child { flex-shrink: 0; }
.treasure-action-title { min-width: 0; color: var(--el-text-color-regular); font-size: var(--type-secondary); line-height: var(--leading-ui); text-decoration: none; overflow-wrap: anywhere; }
.treasure-action-title:hover { color: var(--el-color-primary); }
.treasure-challenge-main { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: var(--space-xs) var(--space-sm); width: 100%; min-width: 0; margin-top: var(--space-sm); padding: var(--space-sm) var(--space-md); border: 1px solid var(--rank-border); border-radius: var(--radius-md); background: transparent; color: inherit; font: inherit; text-align: left; cursor: pointer; }
.treasure-challenge-title { min-width: 0; color: var(--el-text-color-primary); font-size: var(--type-ui); font-weight: var(--weight-semibold); line-height: var(--leading-ui); }
.treasure-challenge-entry { align-self: start; color: var(--el-color-primary); font-size: var(--type-secondary); line-height: var(--leading-ui); white-space: nowrap; }
.treasure-challenge-criteria { grid-column: 1 / -1; color: var(--el-text-color-regular); font-size: var(--type-secondary); line-height: var(--leading-ui); white-space: pre-line; }
.treasure-challenge-title, .treasure-challenge-criteria { display: -webkit-box; overflow: hidden; overflow-wrap: anywhere; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.treasure-action-status { grid-column: 1 / -1; color: var(--el-text-color-secondary); font-size: var(--type-meta); line-height: var(--leading-ui); }
.treasure-challenge-main:hover .treasure-challenge-title, .treasure-challenge-main:focus-visible .treasure-challenge-title { color: var(--el-color-primary); }
.treasure-challenge-main:hover .treasure-challenge-entry { text-decoration: underline; }
.treasure-challenge-main:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 2px; }
.treasure-output-controls { display: flex; flex-shrink: 0; align-items: center; gap: var(--space-xs); margin-left: auto; }
.treasure-output-controls :deep(.el-button) { margin-left: 0; }
.treasure-yield-trigger { padding: 0; border: 0; background: transparent; color: var(--el-color-primary); cursor: pointer; font: inherit; font-size: var(--type-caption); line-height: var(--leading-ui); white-space: nowrap; }
.treasure-yield-trigger:hover, .treasure-yield-trigger:focus-visible { color: var(--el-color-primary-light-3); text-decoration: underline; }
.treasure-action-title:focus-visible, .treasure-gallery-link:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 2px; }
.treasure-empty-state { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-sm) var(--space-md); margin-top: var(--space-md); padding: var(--space-md); border-radius: var(--radius-md); background: var(--surface-subtle); }
.treasure-empty-state p { flex: 1 1 220px; margin: 0; color: var(--el-text-color-regular); font-size: var(--type-ui); line-height: var(--leading-ui); }
.next-step-list, .attention-list, .content-list { display: flex; flex-direction: column; gap: 8px; margin-top: 16px; }
.next-step-item { display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: flex-start; min-width: 0; column-gap: var(--space-sm); width: 100%; padding: 12px 0; border-top: 1px solid var(--el-border-color-lighter); }
.next-step-item:first-child { padding-top: 0; border-top: 0; }
.next-step-main { display: block; width: 100%; min-width: 0; overflow: hidden; padding: 0; border: 0; background: transparent; color: inherit; cursor: pointer; font: inherit; text-align: left; }
.next-step-main :deep(.el-tooltip__trigger) { display: block; max-width: 100%; overflow: hidden; }
.next-step-main span, .content-card-main > span { display: block; overflow: hidden; color: var(--el-color-primary); font-size: var(--type-meta); text-overflow: ellipsis; white-space: nowrap; }
.next-step-main strong { display: -webkit-box; margin-top: 5px; overflow: hidden; color: var(--el-text-color-primary); font-size: var(--type-ui); font-weight: 500; line-height: var(--leading-ui); overflow-wrap: anywhere; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.next-step-main small { display: block; margin-top: 6px; color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.next-step-main:hover strong, .next-step-main:focus-visible strong, .content-card-main:hover h3, .context-link:hover { color: var(--el-color-primary); }
.quick-update-button { flex: 0 0 auto; padding: 0; border: 0; background: transparent; color: var(--el-color-primary); cursor: pointer; font: inherit; font-size: var(--type-caption); line-height: var(--leading-ui); white-space: nowrap; }
.quick-update-button:hover, .quick-update-button:focus-visible { color: var(--el-color-primary-light-3); text-decoration: underline; }
.next-step-update-action { display: inline-flex; flex: 0 0 auto; align-items: center; gap: var(--space-xs); white-space: nowrap; }
.progress-update-count { display: inline-flex; align-items: center; justify-content: center; min-width: 22px; min-height: 22px; padding: 0 var(--space-xs); border: 1px solid var(--el-color-primary-light-7); border-radius: var(--radius-sm); background: var(--el-color-primary-light-9); color: var(--el-color-primary); cursor: pointer; font: inherit; font-size: var(--type-meta); font-weight: var(--weight-semibold); font-variant-numeric: tabular-nums; line-height: 1; }
.progress-update-count:hover, .progress-update-count:focus-visible { border-color: var(--el-color-primary); background: var(--el-color-primary-light-8); }
.attention-panel { background: var(--el-bg-color); }
.attention-item { display: flex; gap: 10px; align-items: flex-start; padding: 11px 0; border-top: 1px solid var(--el-border-color-lighter); }
.attention-item:first-child { padding-top: 0; border-top: 0; }
.attention-item > span { display: grid; width: 22px; height: 22px; place-items: center; border-radius: 50%; background: var(--surface-subtle); color: var(--el-text-color-secondary); font-size: 12px; }
.attention-item h3 { margin: 0; color: var(--el-text-color-primary); font-size: var(--type-ui); font-weight: 500; }
.attention-item button, .context-link { margin-top: 5px; padding: 0; border: 0; background: transparent; color: var(--el-color-primary); cursor: pointer; font: inherit; font-size: var(--type-meta); }
.period-section { background: var(--el-bg-color); }
.period-switcher { flex: 0 0 auto; }
.period-figures { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); margin-top: 18px; }
.period-figure { position: relative; min-width: 0; padding: 2px 20px; border-left: 1px solid var(--el-border-color-lighter); }
.period-figure:first-child { padding-left: 0; border-left: 0; }
.period-figure:last-child { padding-right: 0; }
.period-figure h3 { margin: 6px 0 0; color: var(--el-text-color-primary); font-size: var(--type-ui); }
.period-figure p { margin: 5px 24px 0 0; color: var(--el-text-color-secondary); font-size: var(--type-meta); line-height: var(--leading-ui); }
.period-figure .info-icon { position: absolute; top: 2px; right: 14px; }
.period-figure:last-child .info-icon { right: 0; }
.content-card { min-width: 0; padding: 12px 0; border-top: 1px solid var(--el-border-color-lighter); overflow-wrap: anywhere; }
.content-card:first-child { padding-top: 0; border-top: 0; }
.content-card-main { width: 100%; padding: 0; border: 0; background: transparent; color: inherit; cursor: pointer; font: inherit; text-align: left; }
.content-card-main h3 { margin: 5px 0 0; color: var(--el-text-color-primary); font-size: var(--type-ui); font-weight: 600; line-height: var(--leading-ui); }
.content-card p { margin: 6px 0 0; overflow: hidden; color: var(--el-text-color-secondary); font-size: var(--type-meta); text-overflow: ellipsis; white-space: nowrap; }
.context-link { display: inline-block; }
.empty-copy { margin: 18px 0 0; color: var(--el-text-color-secondary); font-size: var(--type-ui); }
.activity-disclosure { padding: 0 24px; }
.activity-disclosure summary { padding: 16px 0; color: var(--el-text-color-secondary); cursor: pointer; font-size: var(--type-ui); }
.activity-disclosure[open] summary { color: var(--el-color-primary); }
.activity-list { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; padding: 0 0 18px; }
.activity-item { display: flex; align-items: baseline; gap: 4px; min-width: 0; padding: 10px; border-radius: 8px; background: var(--surface-summary); }
.activity-item strong { font-size: 18px; }
.activity-item span { overflow: hidden; color: var(--el-text-color-secondary); font-size: var(--type-meta); text-overflow: ellipsis; white-space: nowrap; }
@media (max-width: 900px) { .current-stat-list { grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px 0; } .current-stat:nth-child(4) { padding-left: 0; border-left: 0; } .experiment-try-list { grid-template-columns: repeat(2, minmax(0, 1fr)); } .action-grid, .content-grid { grid-template-columns: minmax(0, 1fr); } .activity-list { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (max-width: 760px) { .current-strip, .experiment-tries-panel, .period-section, .content-panel, .action-panel { padding: var(--panel-padding); border-radius: var(--panel-radius); } .section-title-row, .period-heading { gap: 12px; } .current-stat-list { grid-template-columns: repeat(2, minmax(0, 1fr)); } .current-stat, .current-stat:nth-child(4) { padding: 12px 18px 12px 0; border: 0; border-top: 1px solid var(--el-border-color-lighter); } .current-stat:first-child, .current-stat:nth-child(2) { border-top: 0; } .current-stat:nth-child(even) { padding: 12px 0 12px 18px; border-left: 1px solid var(--el-border-color-lighter); } .experiment-try-list { grid-template-columns: minmax(0, 1fr); gap: 8px; } .period-heading { flex-direction: column; } .period-switcher, .period-switcher :deep(.el-radio-button), .period-switcher :deep(.el-radio-button__inner) { width: 100%; } .period-switcher { display: flex; } .period-switcher :deep(.el-radio-button) { flex: 1 1 0; } .period-figures { grid-template-columns: minmax(0, 1fr); gap: 12px; } .period-figure, .period-figure:first-child { padding: 12px 0 0; border-top: 1px solid var(--el-border-color-lighter); border-left: 0; } .period-figure:first-child { padding-top: 0; border-top: 0; } .period-figure .info-icon, .period-figure:last-child .info-icon { right: 0; } .activity-disclosure { padding: 0 var(--panel-padding); border-radius: var(--panel-radius); } .activity-list { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
</style>
