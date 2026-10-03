<script setup lang="ts">
import { computed, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useRouter } from 'vue-router'
import { InfoFilled } from '@element-plus/icons-vue'
import PageHeader from '../../components/ui/PageHeader.vue'
import OverviewNavigation from '../../components/overview/OverviewNavigation.vue'
import { overviewApi } from '../../utils/api/overviewApi'
import type { OverviewPeriodDays } from '../../types/overview'

const selectedPeriod = ref<OverviewPeriodDays>(30)
const router = useRouter()
const { data: overview, isLoading, isError, refetch } = useQuery({
  queryKey: computed(() => ['overview', 'recent', selectedPeriod.value]),
  queryFn: () => overviewApi.getRecentOverview(selectedPeriod.value),
})

const period = computed(() => overview.value?.period ?? {
  flow: { reengagedCardCount: 0, reviewedCardCount: 0 },
  generativity: { derivedCardCount: 0, sourceCardCount: 0 },
  closure: { issueWithFollowUpCount: 0 },
  activities: [],
  derivedCards: [],
  recentExperimentMaterials: [],
})

const periodFigures = computed(() => [
  { key: 'reengaged', value: period.value.flow.reengagedCardCount, unit: '張', label: '舊卡重新參與', description: `其中 ${period.value.flow.reviewedCardCount} 張完成回顧`, explanation: '建立於這段時間之前，且在這段時間重新被閱讀、放入實驗場、帶入議題或成為衍生來源的卡片。不是越高越好。' },
  { key: 'derived', value: period.value.generativity.derivedCardCount, unit: '張', label: '由既有內容長出', description: `${period.value.generativity.sourceCardCount} 張既有卡片成為來源`, explanation: '保留一張或多張來源卡關係而建立的新卡；單純新增收藏或放入實驗場不計入。' },
  { key: 'follow-up', value: period.value.closure.issueWithFollowUpCount, unit: '個議題', label: '下一步後留下更新', description: '代表議題後續有新的研判或經驗', explanation: '議題曾留下 nextStep，之後又新增進度。這不是任務完成數，也不能證明 nextStep 已執行。' },
])

const activityDefinitions = {
  created: '新增卡片', offered: 'Today 再次出現', reviewed: '完成回顧', 'experiment-material': '放入實驗場', 'issue-linked': '帶入議題', derived: '長出新卡', 'exploration-record': '留下探索發現', 'issue-update': '議題更新',
} as const

const formatDate = (value: string) => new Intl.DateTimeFormat('zh-TW', { month: 'numeric', day: 'numeric' }).format(new Date(value))
const openCard = (cardId: number) => router.push({ name: 'CardDetail', params: { id: cardId }, query: { from: 'overview' } })
const openExperiment = (experimentId: number) => router.push({ name: 'ExperimentDetail', params: { id: experimentId } })
</script>

<template>
  <section class="overview-page app-page app-page--workspace">
    <PageHeader title="近期回顧" description="回看這段時間，哪些卡片、探索與議題留下了變化。">
      <template #actions><OverviewNavigation /></template>
    </PageHeader>

    <div v-if="isLoading" class="overview-state">正在整理近期資料…</div>
    <el-result v-else-if="isError" icon="error" title="暫時無法取得近期資料" sub-title="請稍後再試。">
      <template #extra><el-button type="primary" @click="refetch()">重新載入</el-button></template>
    </el-result>

    <div v-else-if="overview" class="overview-workspace app-workspace app-workspace--edge-to-edge">
      <div class="overview-stack app-stack">
        <section class="period-section" aria-labelledby="overview-period-title">
          <div class="period-heading">
            <div><span class="section-eyebrow">回看近期</span><h2 id="overview-period-title">這段時間留下了什麼</h2></div>
            <el-radio-group v-model="selectedPeriod" class="period-switcher" aria-label="觀測時間範圍">
              <el-radio-button :value="7">近 7 天</el-radio-button><el-radio-button :value="30">近 30 天</el-radio-button><el-radio-button :value="90">近 90 天</el-radio-button>
            </el-radio-group>
          </div>
          <div class="period-figures">
            <article v-for="figure in periodFigures" :key="figure.key" class="period-figure">
              <div><strong>{{ figure.value }}</strong><span>{{ figure.unit }}</span></div><h3>{{ figure.label }}</h3><p>{{ figure.description }}</p>
              <el-tooltip :content="figure.explanation" placement="top"><el-icon class="info-icon"><InfoFilled /></el-icon></el-tooltip>
            </article>
          </div>
        </section>

        <div class="content-grid">
          <section class="content-panel" aria-labelledby="overview-derived-title">
            <div class="section-title-row"><div><span class="section-eyebrow">新理解</span><h2 id="overview-derived-title">近期長出的東西</h2></div></div>
            <div v-if="period.derivedCards.length" class="content-list">
              <article v-for="card in period.derivedCards.slice(0, 3)" :key="card.cardId" class="content-card">
                <button type="button" class="content-card-main" @click="openCard(card.cardId)"><span>{{ formatDate(card.createdAt) }} 長出</span><h3>{{ card.title }}</h3></button>
                <p>來自 {{ card.sourceCards.map(source => source.title).join('・') }}</p><button type="button" class="context-link" @click="openExperiment(card.experimentId)">{{ card.experimentTitle }}</button>
              </article>
            </div>
            <p v-else class="empty-copy">這段時間沒有保留來源關係的新卡。</p>
          </section>

          <section class="content-panel" aria-labelledby="overview-materials-title">
            <div class="section-title-row"><div><span class="section-eyebrow">材料聚集</span><h2 id="overview-materials-title">近期進入實驗場的材料</h2></div><el-tooltip content="這些是近期放入實驗場、但不是從既有卡片衍生而來的材料；它們不計入「長出的東西」。" placement="top"><el-icon class="info-icon"><InfoFilled /></el-icon></el-tooltip></div>
            <div v-if="period.recentExperimentMaterials.length" class="content-list">
              <article v-for="material in period.recentExperimentMaterials.slice(0, 3)" :key="`${material.experimentId}-${material.cardId}`" class="content-card">
                <button type="button" class="content-card-main" @click="openCard(material.cardId)"><span>{{ formatDate(material.addedAt) }} · {{ material.sourceKind === 'EXPLORATION' ? '探索整理' : '放入材料' }}</span><h3>{{ material.title }}</h3></button>
                <button type="button" class="context-link" @click="openExperiment(material.experimentId)">{{ material.experimentTitle }}</button>
              </article>
            </div>
            <p v-else class="empty-copy">這段時間沒有新放入實驗場的材料。</p>
          </section>
        </div>

        <details class="activity-disclosure"><summary>查看近 {{ selectedPeriod }} 天的完整活動</summary><div class="activity-list"><div v-for="activity in period.activities" :key="activity.key" class="activity-item"><strong>{{ activity.value }}</strong><span>{{ activityDefinitions[activity.key] }}</span></div></div></details>
      </div>
    </div>
  </section>
</template>

<style scoped>
.section-eyebrow { display: block; color: var(--el-text-color-secondary); font-size: var(--type-meta); font-weight: 600; letter-spacing: .06em; text-transform: uppercase; }
.overview-state { padding: 48px 0; color: var(--el-text-color-secondary); text-align: center; }
.period-section, .content-panel, .activity-disclosure { border: 1px solid var(--el-border-color-light); border-radius: 8px; background: var(--el-bg-color); }
.period-section, .content-panel { padding: var(--panel-padding); }
.period-heading, .section-title-row { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.period-heading h2, .section-title-row h2 { margin: 4px 0 0; color: var(--el-text-color-primary); font-size: var(--type-section-title); }
.period-figures { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); margin-top: 18px; }
.period-figure { position: relative; min-width: 0; padding: 3px 20px; border-left: 1px solid var(--el-border-color-lighter); }
.period-figure:first-child { padding-left: 0; border-left: 0; }
.period-figure:last-child { padding-right: 0; }
.period-figure strong, .activity-item strong { color: var(--el-text-color-primary); font-size: 25px; font-variant-numeric: tabular-nums; }
.period-figure > div > span { margin-left: 3px; color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.period-figure h3 { margin: 7px 0 2px; color: var(--el-text-color-primary); font-size: var(--type-ui); }
.period-figure p { margin: 0; color: var(--el-text-color-secondary); font-size: var(--type-meta); line-height: var(--leading-ui); }
.info-icon { margin-top: 4px; color: var(--el-text-color-placeholder); cursor: help; }
.period-figure .info-icon { position: absolute; top: 0; right: 10px; }
.period-figure:last-child .info-icon { right: 0; }
.content-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.content-list { display: flex; flex-direction: column; margin-top: 16px; }
.content-card { padding: 12px 0; border-top: 1px solid var(--el-border-color-lighter); }
.content-card:first-child { padding-top: 0; border-top: 0; }
.content-card-main, .context-link { display: block; padding: 0; border: 0; background: transparent; text-align: left; cursor: pointer; font: inherit; }
.content-card-main span, .content-card p { color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.content-card-main h3 { margin: 4px 0 0; color: var(--el-text-color-primary); font-size: var(--type-ui); }
.content-card-main:hover h3, .context-link:hover { color: var(--el-color-primary); }
.content-card p { margin: 5px 0 0; line-height: var(--leading-ui); }
.context-link { margin-top: 6px; color: var(--el-color-primary); font-size: var(--type-meta); }
.empty-copy { margin: 18px 0 0; color: var(--el-text-color-secondary); font-size: var(--type-ui); }
.activity-disclosure { padding: 0 var(--panel-padding); }
.activity-disclosure > summary { padding: 16px 0; color: var(--el-text-color-secondary); cursor: pointer; font-size: var(--type-ui); }
.activity-disclosure[open] > summary { color: var(--el-color-primary); }
.activity-list { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); margin: 0 0 16px; }
.activity-item { min-width: 0; padding: 4px 14px; border-left: 1px solid var(--el-border-color-lighter); }
.activity-item:nth-child(4n + 1) { padding-left: 0; border-left: 0; }
.activity-item:nth-child(n + 5) { padding-top: 16px; border-top: 1px solid var(--el-border-color-lighter); }
.activity-item span { display: block; margin-top: 4px; color: var(--el-text-color-secondary); font-size: var(--type-meta); }
@media (max-width: 900px) { .content-grid { grid-template-columns: 1fr; } .activity-list { grid-template-columns: repeat(3, minmax(0, 1fr)); } .activity-item:nth-child(4n + 1) { padding-left: 14px; border-left: 1px solid var(--el-border-color-lighter); } .activity-item:nth-child(3n + 1) { padding-left: 0; border-left: 0; } .activity-item:nth-child(n + 4) { padding-top: 16px; border-top: 1px solid var(--el-border-color-lighter); } }
@media (max-width: 760px) { .period-section, .content-panel { padding: var(--panel-padding); border-radius: var(--panel-radius); } .period-heading, .section-title-row { gap: 12px; } .period-heading { flex-direction: column; } .period-switcher, .period-switcher :deep(.el-radio-button), .period-switcher :deep(.el-radio-button__inner) { width: 100%; } .period-switcher { display: flex; } .period-switcher :deep(.el-radio-button) { flex: 1 1 0; } .period-figures { grid-template-columns: 1fr; gap: 12px; } .period-figure, .period-figure:first-child { padding: 12px 0 0; border-top: 1px solid var(--el-border-color-lighter); border-left: 0; } .period-figure:first-child { padding-top: 0; border-top: 0; } .period-figure .info-icon, .period-figure:last-child .info-icon { right: 0; } .activity-disclosure { padding: 0 var(--panel-padding); border-radius: var(--panel-radius); } .activity-list { grid-template-columns: repeat(2, minmax(0, 1fr)); } .activity-item, .activity-item:nth-child(4n + 1) { padding: 12px 18px 12px 0; border: 0; border-top: 1px solid var(--el-border-color-lighter); } .activity-item:nth-child(-n + 2) { border-top: 0; } .activity-item:nth-child(even) { padding: 12px 0 12px 18px; border-left: 1px solid var(--el-border-color-lighter); } .activity-item:nth-child(odd) { padding-left: 0; border-left: 0; } }
</style>
