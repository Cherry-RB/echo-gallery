<script setup lang="ts">
import { computed, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useRouter } from 'vue-router'
import { InfoFilled } from '@element-plus/icons-vue'
import { overviewApi } from '../../utils/api/overviewApi'
import type { OverviewAttentionSignal, OverviewPeriodDays } from '../../types/overview'

const selectedPeriod = ref<OverviewPeriodDays>(30)
const router = useRouter()
const { data: overview, isLoading, isError, refetch } = useQuery({
  queryKey: computed(() => ['overview', selectedPeriod.value]),
  queryFn: () => overviewApi.getOverview(selectedPeriod.value),
})

const current = computed(() => overview.value?.current ?? {
  recurringCardCount: 0,
  pausedCardCount: 0,
  needsProcessingCardCount: 0,
  activeExperimentCount: 0,
  workWithNextStepCount: 0,
  experimentTries: [],
  nextSteps: [],
  attentionSignals: [],
})
const period = computed(() => overview.value?.period ?? {
  flow: { reengagedCardCount: 0, reviewedCardCount: 0 },
  generativity: { derivedCardCount: 0, sourceCardCount: 0 },
  closure: { workWithFollowUpCount: 0 },
  activities: [],
  derivedCards: [],
  recentExperimentMaterials: [],
})

const currentItems = computed(() => [
  { key: 'recurring', label: '回流中', value: current.value.recurringCardCount, unit: '張卡片' },
  { key: 'paused', label: '暫停中', value: current.value.pausedCardCount, unit: '張卡片' },
  { key: 'processing', label: '待整理', value: current.value.needsProcessingCardCount, unit: '張卡片' },
  { key: 'experiments', label: '活躍實驗場', value: current.value.activeExperimentCount, unit: '個' },
  { key: 'next-steps', label: '已有下一步的議題', value: current.value.workWithNextStepCount, unit: '個' },
])

const periodFigures = computed(() => [
  {
    key: 'reengaged',
    value: period.value.flow.reengagedCardCount,
    unit: '張',
    label: '舊卡重新參與',
    description: `其中 ${period.value.flow.reviewedCardCount} 張完成回顧`,
    explanation: '建立於這段時間之前，且在這段時間重新被閱讀、放入實驗場、帶入議題或成為衍生來源的卡片。不是越高越好。',
  },
  {
    key: 'derived',
    value: period.value.generativity.derivedCardCount,
    unit: '張',
    label: '由既有內容長出',
    description: `${period.value.generativity.sourceCardCount} 張既有卡片成為來源`,
    explanation: '保留一張或多張來源卡關係而建立的新卡；單純新增收藏或放入實驗場不計入。',
  },
  {
    key: 'follow-up',
    value: period.value.closure.workWithFollowUpCount,
    unit: '個議題',
    label: '下一步後留下更新',
    description: '代表議題後續有新的研判或經驗',
    explanation: '議題曾留下 nextStep，之後又新增進度。這不是任務完成數，也不能證明 nextStep 已執行。',
  },
])

const attentionItems = computed(() => (current.value.attentionSignals ?? []).map(signal => ({
  ...signal,
  title: signal.key === 'processing'
    ? `${signal.cardCount} 張卡片等待你決定如何處理`
    : `${signal.cardCount} 張卡片累積多次稍後再看`,
  actionLabel: signal.key === 'processing' ? '查看待整理卡片' : '查看稍後再看',
})))

const activityDefinitions = {
  created: '新增卡片',
  offered: 'Today 再次出現',
  reviewed: '完成回顧',
  'experiment-material': '放入實驗場',
  'work-linked': '帶入議題',
  derived: '長出新卡',
  'exploration-record': '留下探索發現',
  'work-update': '議題更新',
} as const

const formatDate = (value: string) => new Intl.DateTimeFormat('zh-TW', {
  month: 'numeric',
  day: 'numeric',
}).format(new Date(value))

const openCard = (cardId: number) => router.push({ name: 'CardDetail', params: { id: cardId }, query: { from: 'overview' } })
const openExperiment = (experimentId: number) => router.push({ name: 'ExperimentDetail', params: { id: experimentId } })
const openWork = (workId: number) => router.push({ name: 'WorkDetail', params: { id: workId } })
const openCardReturn = () => router.push('/overview/cards')
const openCurrentItem = (key: string) => {
  if (key === 'experiments') router.push('/experiments')
  else if (key === 'next-steps') router.push('/works')
  else openCardReturn()
}
const attentionSymbol = (kind: OverviewAttentionSignal['key']) => kind === 'processing' ? '□' : '↗'
</script>

<template>
  <section class="overview-page">
    <header class="overview-header">
      <h1 class="page-title">資訊轉化觀測台</h1>
      <p class="page-description">看見此刻狀態，以及最近留下了什麼。</p>
    </header>

    <div v-if="isLoading" class="overview-state">正在整理觀測資料…</div>
    <el-result v-else-if="isError" icon="error" title="暫時無法取得觀測資料" sub-title="請稍後再試。">
      <template #extra><el-button type="primary" @click="refetch()">重新載入</el-button></template>
    </el-result>

    <div v-else-if="overview" class="overview-workspace">
      <div class="overview-stack">
      <section class="current-strip" aria-labelledby="overview-current-title">
        <div class="section-title-row">
          <div>
            <span class="section-eyebrow">此刻</span>
            <h2 id="overview-current-title">系統現在的狀態</h2>
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

      <section v-if="current.experimentTries.length" class="experiment-tries-panel" aria-labelledby="overview-experiment-tries-title">
        <div class="section-title-row">
          <div>
            <span class="section-eyebrow">實驗場</span>
            <h2 id="overview-experiment-tries-title">目前想試</h2>
          </div>
          <router-link to="/experiments">查看全部</router-link>
        </div>
        <div class="experiment-try-list">
          <button
            v-for="experimentTry in current.experimentTries"
            :key="experimentTry.experimentId"
            type="button"
            class="experiment-try-item"
            @click="openExperiment(experimentTry.experimentId)"
          >
            <span>{{ experimentTry.experimentTitle }}</span>
            <strong>{{ experimentTry.currentTry }}</strong>
          </button>
        </div>
      </section>

      <div class="action-grid">
        <section class="action-panel" aria-labelledby="overview-next-step-title">
          <div class="section-title-row">
            <div>
              <span class="section-eyebrow">現在可以往前走</span>
              <h2 id="overview-next-step-title">已經想得夠清楚的方向</h2>
            </div>
            <el-tooltip content="nextStep 是已留下的方向，不是待辦事項，也不表示必須立刻完成。" placement="top">
              <el-icon class="info-icon"><InfoFilled /></el-icon>
            </el-tooltip>
          </div>
          <div v-if="current.nextSteps.length" class="next-step-list">
            <button v-for="step in current.nextSteps.slice(0, 3)" :key="step.workId" type="button" class="next-step-item" @click="openWork(step.workId)">
              <span>{{ step.workTitle }}</span>
              <strong>{{ step.nextStep }}</strong>
              <small>更新於 {{ formatDate(step.updatedAt) }}</small>
            </button>
          </div>
          <p v-else class="empty-copy">目前沒有已留下下一步的議題。</p>
        </section>

        <section v-if="attentionItems.length" class="action-panel attention-panel" aria-labelledby="overview-attention-title">
          <div class="section-title-row">
            <div>
              <span class="section-eyebrow">值得看一眼</span>
              <h2 id="overview-attention-title">有明確訊號的地方</h2>
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

      <section class="period-section" aria-labelledby="overview-period-title">
        <div class="period-heading">
          <div>
            <span class="section-eyebrow">回看近期</span>
            <h2 id="overview-period-title">這段時間留下了什麼</h2>
          </div>
          <el-radio-group v-model="selectedPeriod" class="period-switcher" aria-label="觀測時間範圍">
            <el-radio-button :value="7">近 7 天</el-radio-button>
            <el-radio-button :value="30">近 30 天</el-radio-button>
            <el-radio-button :value="90">近 90 天</el-radio-button>
          </el-radio-group>
        </div>
        <div class="period-figures">
          <article v-for="figure in periodFigures" :key="figure.key" class="period-figure">
            <div><strong>{{ figure.value }}</strong><span>{{ figure.unit }}</span></div>
            <h3>{{ figure.label }}</h3>
            <p>{{ figure.description }}</p>
            <el-tooltip :content="figure.explanation" placement="top">
              <el-icon class="info-icon"><InfoFilled /></el-icon>
            </el-tooltip>
          </article>
        </div>
      </section>

      <div class="content-grid">
        <section class="content-panel" aria-labelledby="overview-derived-title">
          <div class="section-title-row">
            <div>
              <span class="section-eyebrow">新理解</span>
              <h2 id="overview-derived-title">近期長出的東西</h2>
            </div>
          </div>
          <div v-if="period.derivedCards.length" class="content-list">
            <article v-for="card in period.derivedCards.slice(0, 3)" :key="card.cardId" class="content-card">
              <button type="button" class="content-card-main" @click="openCard(card.cardId)">
                <span>{{ formatDate(card.createdAt) }} 長出</span>
                <h3>{{ card.title }}</h3>
              </button>
              <p>來自 {{ card.sourceCards.map(source => source.title).join('・') }}</p>
              <button type="button" class="context-link" @click="openExperiment(card.experimentId)">{{ card.experimentTitle }}</button>
            </article>
          </div>
          <p v-else class="empty-copy">這段時間沒有保留來源關係的新卡。</p>
        </section>

        <section class="content-panel" aria-labelledby="overview-materials-title">
          <div class="section-title-row">
            <div>
              <span class="section-eyebrow">材料聚集</span>
              <h2 id="overview-materials-title">近期進入實驗場的材料</h2>
            </div>
            <el-tooltip content="這些是近期放入實驗場、但不是從既有卡片衍生而來的材料；它們不計入「長出的東西」。" placement="top">
              <el-icon class="info-icon"><InfoFilled /></el-icon>
            </el-tooltip>
          </div>
          <div v-if="period.recentExperimentMaterials.length" class="content-list">
            <article v-for="material in period.recentExperimentMaterials.slice(0, 3)" :key="`${material.experimentId}-${material.cardId}`" class="content-card">
              <button type="button" class="content-card-main" @click="openCard(material.cardId)">
                <span>{{ formatDate(material.addedAt) }} · {{ material.sourceKind === 'EXPLORATION' ? '探索整理' : '放入材料' }}</span>
                <h3>{{ material.title }}</h3>
              </button>
              <button type="button" class="context-link" @click="openExperiment(material.experimentId)">{{ material.experimentTitle }}</button>
            </article>
          </div>
          <p v-else class="empty-copy">這段時間沒有新放入實驗場的材料。</p>
        </section>
      </div>

      <details class="activity-disclosure">
        <summary>查看近 {{ selectedPeriod }} 天的完整活動</summary>
        <div class="activity-list">
          <div v-for="activity in period.activities" :key="activity.key" class="activity-item">
            <strong>{{ activity.value }}</strong>
            <span>{{ activityDefinitions[activity.key] }}</span>
          </div>
        </div>
      </details>
      </div>
    </div>
  </section>
</template>

<style scoped>
.overview-page { width: 100%; max-width: 1240px; margin: 0 auto; }
.overview-header { margin-bottom: 22px; }
.section-eyebrow { display: block; color: var(--el-text-color-secondary); font-size: var(--type-meta); font-weight: 600; letter-spacing: .06em; text-transform: uppercase; }
.page-title { margin: 0; font-size: var(--type-page-title); line-height: 1.35; }
.page-description { margin: 8px 0 0; color: var(--el-text-color-secondary); font-size: var(--type-ui); line-height: var(--leading-ui); }
.overview-workspace { padding: 12px; background: var(--el-bg-color-page); }
.overview-stack { display: flex; flex-direction: column; gap: 16px; }
.overview-state { padding: 48px 0; color: var(--el-text-color-secondary); text-align: center; }
.current-strip, .experiment-tries-panel, .action-panel, .period-section, .content-panel, .activity-disclosure { border: 1px solid var(--el-border-color-light); border-radius: 8px; background: var(--el-bg-color); }
.current-strip, .experiment-tries-panel, .period-section, .content-panel, .action-panel { padding: 20px 24px; }
.section-title-row, .period-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.section-title-row h2, .period-heading h2 { margin: 4px 0 0; font-size: var(--type-section-title); }
.section-title-row > a { color: var(--el-color-primary); font-size: var(--type-meta); text-decoration: none; white-space: nowrap; }
.section-title-row > a:hover { text-decoration: underline; }
.info-icon { margin-top: 4px; color: var(--el-text-color-placeholder); cursor: help; }
.current-stat-list { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); margin-top: 16px; }
.current-stat { min-width: 0; padding: 3px 14px; border: 0; border-left: 1px solid var(--el-border-color-lighter); background: transparent; color: inherit; cursor: pointer; font: inherit; text-align: left; }
.current-stat:first-child { padding-left: 0; border-left: 0; }
.current-stat:last-child { padding-right: 0; }
.current-stat:hover strong, .current-stat:focus-visible strong { color: var(--el-color-primary); }
.current-stat strong, .period-figure strong, .activity-item strong { color: var(--el-text-color-primary); font-size: 25px; font-variant-numeric: tabular-nums; }
.current-stat > span, .period-figure > div > span { margin-left: 3px; color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.current-stat small { display: block; margin-top: 5px; color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.experiment-try-list { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; margin-top: 16px; }
.experiment-try-item { min-width: 0; padding: 13px 14px; overflow: hidden; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; background: var(--el-fill-color-extra-light); color: inherit; cursor: pointer; font: inherit; text-align: left; }
.experiment-try-item > span { display: block; overflow: hidden; color: var(--el-color-primary); font-size: var(--type-meta); text-overflow: ellipsis; white-space: nowrap; }
.experiment-try-item > strong { display: -webkit-box; margin-top: 6px; overflow: hidden; color: var(--el-text-color-primary); font-size: var(--type-ui); font-weight: 500; line-height: var(--leading-ui); overflow-wrap: anywhere; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.experiment-try-item:hover, .experiment-try-item:focus-visible { border-color: var(--el-color-primary-light-5); }
.experiment-try-item:hover > strong, .experiment-try-item:focus-visible > strong { color: var(--el-color-primary); }
.action-grid, .content-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.next-step-list, .attention-list, .content-list { display: flex; flex-direction: column; gap: 8px; margin-top: 16px; }
.next-step-item { width: 100%; padding: 12px 0; border: 0; border-top: 1px solid var(--el-border-color-lighter); background: transparent; color: inherit; cursor: pointer; font: inherit; text-align: left; }
.next-step-item:first-child { padding-top: 0; border-top: 0; }
.next-step-item > span, .content-card-main > span { display: block; color: var(--el-color-primary); font-size: var(--type-meta); }
.next-step-item > strong { display: block; margin-top: 5px; color: var(--el-text-color-primary); font-size: var(--type-ui); font-weight: 500; line-height: var(--leading-ui); }
.next-step-item > small { display: block; margin-top: 6px; color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.next-step-item:hover > strong, .content-card-main:hover h3, .context-link:hover { color: var(--el-color-primary); }
.attention-panel { background: var(--el-bg-color); }
.attention-item { display: flex; gap: 10px; align-items: flex-start; padding: 11px 0; border-top: 1px solid var(--el-border-color-lighter); }
.attention-item:first-child { padding-top: 0; border-top: 0; }
.attention-item > span { display: grid; width: 22px; height: 22px; place-items: center; border-radius: 50%; background: var(--el-fill-color-light); color: var(--el-text-color-secondary); font-size: 12px; }
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
.content-card { padding: 12px 0; border-top: 1px solid var(--el-border-color-lighter); }
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
.activity-item { display: flex; align-items: baseline; gap: 4px; min-width: 0; padding: 10px; border-radius: 8px; background: var(--el-fill-color-light); }
.activity-item strong { font-size: 18px; }
.activity-item span { overflow: hidden; color: var(--el-text-color-secondary); font-size: var(--type-meta); text-overflow: ellipsis; white-space: nowrap; }
@media (max-width: 900px) { .current-stat-list { grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px 0; } .current-stat:nth-child(4) { padding-left: 0; border-left: 0; } .experiment-try-list { grid-template-columns: repeat(2, minmax(0, 1fr)); } .action-grid, .content-grid { grid-template-columns: 1fr; } .activity-list { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (max-width: 600px) { .overview-page { max-width: none; } .overview-header { margin-bottom: 18px; } .overview-workspace { margin-inline: -16px; padding: 12px 16px; } .overview-stack { gap: 12px; } .current-strip, .experiment-tries-panel, .period-section, .content-panel, .action-panel { padding: 18px; border-radius: 8px; } .section-title-row, .period-heading { gap: 12px; } .current-stat-list { grid-template-columns: repeat(2, minmax(0, 1fr)); } .current-stat, .current-stat:nth-child(4) { padding: 12px 18px 12px 0; border: 0; border-top: 1px solid var(--el-border-color-lighter); } .current-stat:first-child, .current-stat:nth-child(2) { border-top: 0; } .current-stat:nth-child(even) { padding: 12px 0 12px 18px; border-left: 1px solid var(--el-border-color-lighter); } .current-stat:last-child { grid-column: 1 / -1; padding-right: 0; } .experiment-try-list { grid-template-columns: 1fr; gap: 8px; } .period-heading { flex-direction: column; } .period-switcher, .period-switcher :deep(.el-radio-button), .period-switcher :deep(.el-radio-button__inner) { width: 100%; } .period-switcher { display: flex; } .period-switcher :deep(.el-radio-button) { flex: 1 1 0; } .period-figures { grid-template-columns: 1fr; gap: 12px; } .period-figure, .period-figure:first-child { padding: 12px 0 0; border-top: 1px solid var(--el-border-color-lighter); border-left: 0; } .period-figure:first-child { padding-top: 0; border-top: 0; } .period-figure .info-icon, .period-figure:last-child .info-icon { right: 0; } .activity-disclosure { padding: 0 18px; border-radius: 8px; } .activity-list { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
</style>
