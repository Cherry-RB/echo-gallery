<script setup lang="ts">
import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { ArrowLeft, InfoFilled } from '@element-plus/icons-vue'
import { overviewApi } from '../../utils/api/overviewApi'

const { data: overview, isLoading, isError, refetch } = useQuery({
  queryKey: ['overview', 'card-return'],
  queryFn: overviewApi.getCardReturnOverview,
})

const snapshot = computed(() => overview.value)
const stateItems = computed(() => snapshot.value ? [
  { key: 'recurring', label: '回流中', value: snapshot.value.state.recurringCardCount, unit: '張' },
  { key: 'paused', label: '暫停中', value: snapshot.value.state.pausedCardCount, unit: '張' },
  { key: 'archived', label: '已封存', value: snapshot.value.state.archivedCardCount, unit: '張' },
  { key: 'processing', label: '待整理', value: snapshot.value.state.needsProcessingCardCount, unit: '張' },
  { key: 'never-reviewed', label: '從未回顧', value: snapshot.value.state.neverReviewedCardCount, unit: '張' },
  { key: 'schedule-issue', label: '排程需留意', value: snapshot.value.state.scheduleIssueCardCount, unit: '張' },
] : [])

const cadenceDefinitions: Record<string, { label: string; range: string }> = {
  '1-7': { label: '短期', range: '1–7 天' },
  '8-14': { label: '短期', range: '8–14 天' },
  '15-30': { label: '中期', range: '15–30 天' },
  '31-60': { label: '中期', range: '31–60 天' },
  '61-90': { label: '長期', range: '61–90 天' },
  '91-180': { label: '長期', range: '91–180 天' },
  '181+': { label: '長期醞釀', range: '181 天以上' },
}
const cadenceBands = computed(() => (snapshot.value?.cadenceBands ?? []).map(item => ({ ...item, ...cadenceDefinitions[item.key] })))
const largestCadenceValue = computed(() => Math.max(...cadenceBands.value.map(item => item.cardCount), 1))
const totalForecast = computed(() => (snapshot.value?.forecastDays ?? []).reduce((total, day) => total + day.cardCount, 0))
const forecastDays = computed(() => (snapshot.value?.forecastDays ?? []).map(day => ({
  ...day,
  dateLabel: new Intl.DateTimeFormat('zh-TW', { month: 'numeric', day: 'numeric' }).format(new Date(`${day.date}T00:00:00`)),
  dayLabel: new Intl.DateTimeFormat('zh-TW', { weekday: 'short' }).format(new Date(`${day.date}T00:00:00`)),
})))
const largestForecastValue = computed(() => Math.max(...forecastDays.value.map(day => day.cardCount), 1))

const snoozeDefinitions: Record<string, string> = {
  '3-5': '稍後 3–5 次',
  '6-10': '稍後 6–10 次',
  '11+': '稍後超過 10 次',
}
const snoozeBands = computed(() => (snapshot.value?.snoozeBands ?? []).map(item => ({ ...item, label: snoozeDefinitions[item.key] })))
</script>

<template>
  <section class="card-return-page">
    <router-link class="back-link" to="/overview">
      <el-icon><ArrowLeft /></el-icon>
      返回資訊轉化觀測台
    </router-link>

    <header class="page-header">
      <h1>卡片與回流</h1>
      <p>看看卡片目前的安排，以及接下來的回流節奏。</p>
    </header>

    <div v-if="isLoading" class="overview-state">正在整理卡片與回流資料…</div>
    <el-result v-else-if="isError" icon="error" title="暫時無法取得卡片與回流資料" sub-title="請稍後再試。">
      <template #extra><el-button type="primary" @click="refetch()">重新載入</el-button></template>
    </el-result>

    <div v-else-if="snapshot" class="return-workspace">
      <div class="page-stack">
        <section class="panel state-panel" aria-labelledby="card-return-state-title">
          <div class="section-heading">
            <div>
              <span class="section-eyebrow">此刻</span>
              <h2 id="card-return-state-title">卡片目前狀態</h2>
            </div>
            <el-tooltip content="這些是目前存量，不是近幾天的活動，也不代表卡片表現好壞。" placement="top">
              <el-icon class="info-icon"><InfoFilled /></el-icon>
            </el-tooltip>
          </div>
          <div class="state-grid">
            <article v-for="item in stateItems" :key="item.key" class="state-item">
              <strong>{{ item.value }}</strong><span>{{ item.unit }}</span>
              <small>{{ item.label }}</small>
            </article>
          </div>
        </section>

        <div class="return-grid">
          <section class="panel cadence-panel" aria-labelledby="card-return-cadence-title">
            <div class="section-heading">
              <div>
                <span class="section-eyebrow">回流節奏</span>
                <h2 id="card-return-cadence-title">卡片多久會再出現</h2>
              </div>
              <el-tooltip content="只計入目前未封存、未暫停，且有回流安排的卡片。較短或較長都不代表比較好。" placement="top">
                <el-icon class="info-icon"><InfoFilled /></el-icon>
              </el-tooltip>
            </div>
            <div class="cadence-list">
              <article v-for="item in cadenceBands" :key="item.key" class="cadence-item">
                <div class="cadence-label"><span>{{ item.label }}</span><strong>{{ item.range }}</strong></div>
                <div class="cadence-track" aria-hidden="true"><i :style="{ width: `${(item.cardCount / largestCadenceValue) * 100}%` }" /></div>
                <div class="cadence-value"><strong>{{ item.cardCount }}</strong><span>張</span></div>
              </article>
            </div>
          </section>

          <section class="panel forecast-panel" aria-labelledby="card-return-forecast-title">
            <div class="section-heading">
              <div>
                <span class="section-eyebrow">接下來 7 天</span>
                <h2 id="card-return-forecast-title">預計回流 {{ totalForecast }} 張</h2>
              </div>
              <el-tooltip content="這是卡片目前排定的下一次出現時間預覽，不代表你必須在當天處理它們。" placement="top">
                <el-icon class="info-icon"><InfoFilled /></el-icon>
              </el-tooltip>
            </div>
            <div class="forecast-chart" aria-label="未來七天預計回流卡片數">
              <article v-for="day in forecastDays" :key="day.date" class="forecast-day">
                <strong>{{ day.cardCount }}</strong>
                <div class="forecast-column"><i :style="{ height: day.cardCount === 0 ? '0%' : `${Math.max(8, (day.cardCount / largestForecastValue) * 100)}%` }" /></div>
                <span>{{ day.dayLabel }}</span>
                <small>{{ day.dateLabel }}</small>
              </article>
            </div>
          </section>
        </div>

        <section class="panel snooze-panel" aria-labelledby="card-return-snooze-title">
          <div class="section-heading">
            <div>
              <span class="section-eyebrow">稍後再看</span>
              <h2 id="card-return-snooze-title">反覆暫時離開注意力的卡片</h2>
            </div>
            <el-tooltip content="次數是自上次完成回顧後的連續次數；它可能代表仍在醞釀，也可能不值得目前處理。" placement="top">
              <el-icon class="info-icon"><InfoFilled /></el-icon>
            </el-tooltip>
          </div>
          <div class="snooze-grid">
            <article v-for="item in snoozeBands" :key="item.key" class="snooze-item">
              <strong>{{ item.cardCount }}<span>張</span></strong>
              <small>{{ item.label }}</small>
            </article>
          </div>
        </section>

        <details class="definition-disclosure">
          <summary>查看這些數字的計算方式</summary>
          <p>此頁由後端依目前資料聚合；稍後再看的次數，是自上次完成回顧後的連續次數。</p>
        </details>
      </div>
    </div>
  </section>
</template>

<style scoped>
.card-return-page { width: 100%; max-width: 1240px; margin: 0 auto; }
.back-link { display: inline-flex; align-items: center; gap: 5px; color: var(--el-text-color-secondary); font-size: var(--type-ui); text-decoration: none; }
.back-link:hover { color: var(--el-color-primary); }
.page-header { margin: 18px 0 24px; }
.page-header h1 { margin: 0; font-size: var(--type-page-title); line-height: 1.35; }
.page-header p { margin: 8px 0 0; color: var(--el-text-color-secondary); font-size: var(--type-ui); line-height: var(--leading-ui); }
.overview-state { padding: 48px 0; color: var(--el-text-color-secondary); text-align: center; }
.return-workspace { padding: 12px; background: var(--el-bg-color-page); }
.page-stack { display: flex; flex-direction: column; gap: 16px; }
.panel, .definition-disclosure { border: 1px solid var(--el-border-color-light); border-radius: 8px; background: var(--el-bg-color); }
.panel { padding: 20px 24px; }
.section-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.section-eyebrow { display: block; color: var(--el-text-color-secondary); font-size: var(--type-meta); font-weight: 600; letter-spacing: .06em; text-transform: uppercase; }
.section-heading h2 { margin: 4px 0 0; color: var(--el-text-color-primary); font-size: var(--type-section-title); }
.info-icon { margin-top: 4px; color: var(--el-text-color-placeholder); cursor: help; }
.state-grid { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); margin-top: 16px; }
.state-item { min-width: 0; padding: 3px 14px; border-left: 1px solid var(--el-border-color-lighter); }
.state-item:first-child { padding-left: 0; border-left: 0; }
.state-item:last-child { padding-right: 0; }
.state-item strong, .snooze-item > strong { color: var(--el-text-color-primary); font-size: 25px; font-variant-numeric: tabular-nums; }
.state-item > span, .snooze-item > strong > span { margin-left: 3px; color: var(--el-text-color-secondary); font-size: var(--type-meta); font-weight: 400; }
.state-item small, .snooze-item small { display: block; margin-top: 5px; color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.return-grid { display: grid; grid-template-columns: minmax(0, 1.25fr) minmax(300px, .75fr); gap: 16px; }
.cadence-list { display: flex; flex-direction: column; gap: 10px; margin-top: 18px; }
.cadence-item { display: grid; grid-template-columns: 116px minmax(80px, 1fr) 42px; column-gap: 12px; align-items: center; }
.cadence-label { display: flex; flex-direction: column; gap: 1px; }
.cadence-label span { color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.cadence-label strong { color: var(--el-text-color-primary); font-size: var(--type-ui); font-weight: 600; }
.cadence-track { height: 7px; overflow: hidden; border-radius: 99px; background: var(--el-fill-color); }
.cadence-track i { display: block; height: 100%; min-width: 4px; border-radius: inherit; background: var(--el-color-primary-light-3); }
.cadence-value { text-align: right; white-space: nowrap; }
.cadence-value strong { color: var(--el-text-color-primary); font-size: var(--type-ui); font-variant-numeric: tabular-nums; }
.cadence-value span { margin-left: 2px; color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.forecast-chart { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); align-items: end; gap: 8px; min-height: 166px; margin-top: 18px; }
.forecast-day { display: flex; flex-direction: column; align-items: center; min-width: 0; gap: 4px; }
.forecast-day > strong { color: var(--el-text-color-primary); font-size: var(--type-ui); font-variant-numeric: tabular-nums; }
.forecast-column { display: flex; align-items: flex-end; width: 100%; height: 76px; border-bottom: 1px solid var(--el-border-color-light); }
.forecast-column i { display: block; width: 100%; border-radius: 4px 4px 0 0; background: var(--el-color-primary-light-3); }
.forecast-day > span { color: var(--el-text-color-primary); font-size: var(--type-meta); white-space: nowrap; }
.forecast-day > small { color: var(--el-text-color-secondary); font-size: 11px; white-space: nowrap; }
.snooze-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); margin-top: 16px; }
.snooze-item { min-width: 0; padding: 3px 20px; border-left: 1px solid var(--el-border-color-lighter); }
.snooze-item:first-child { padding-left: 0; border-left: 0; }
.definition-disclosure { padding: 0 24px; }
.definition-disclosure > summary { padding: 16px 0; color: var(--el-text-color-secondary); cursor: pointer; font-size: var(--type-ui); }
.definition-disclosure[open] > summary { color: var(--el-color-primary); }
.definition-disclosure > p { margin: 0 0 15px; color: var(--el-text-color-secondary); font-size: var(--type-meta); line-height: var(--leading-ui); }
@media (max-width: 900px) { .state-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px 0; } .state-item:nth-child(4) { padding-left: 0; border-left: 0; } .return-grid { grid-template-columns: 1fr; } }
@media (max-width: 600px) { .card-return-page { max-width: none; } .page-header { margin-bottom: 18px; } .return-workspace { margin-inline: -16px; padding: 12px 16px; } .panel { padding: 18px; border-radius: 8px; } .state-grid, .snooze-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0; } .state-item, .snooze-item { padding: 10px; border-left: 0; border-top: 1px solid var(--el-border-color-lighter); } .state-item:first-child, .state-item:nth-child(2), .snooze-item:first-child, .snooze-item:nth-child(2) { border-top: 0; } .state-item:nth-child(odd), .snooze-item:nth-child(odd) { padding-left: 0; } .state-item:nth-child(even), .snooze-item:nth-child(even) { border-left: 1px solid var(--el-border-color-lighter); } .state-item:last-child { padding-right: 10px; } .cadence-item { grid-template-columns: 104px minmax(50px, 1fr) 36px; column-gap: 8px; } .forecast-chart { gap: 5px; } .forecast-day > span { font-size: 11px; } .forecast-day > small { font-size: 10px; } .definition-disclosure { padding: 0 18px; border-radius: 8px; } }
</style>
