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
  { key: 'recurring', label: '正在回流', value: snapshot.value.state.recurringCardCount, unit: '張卡片', description: '會依各自節奏，再次出現在 Today。' },
  { key: 'paused', label: '暫停中', value: snapshot.value.state.pausedCardCount, unit: '張卡片', description: '目前不會進入回流安排。' },
  { key: 'archived', label: '已封存', value: snapshot.value.state.archivedCardCount, unit: '張卡片', description: '保留在內容庫，但不再主動回流。' },
  { key: 'processing', label: '待整理', value: snapshot.value.state.needsProcessingCardCount, unit: '張卡片', description: '曾明確留下之後要再加工的訊號。', tone: 'attention' },
  { key: 'never-reviewed', label: '從未回顧', value: snapshot.value.state.neverReviewedCardCount, unit: '張卡片', description: '尚未完成過一次重新相遇。' },
  { key: 'schedule-issue', label: '排程需留意', value: snapshot.value.state.scheduleIssueCardCount, unit: '張卡片', description: '回流週期與下次出現時間只有其中一項，無法形成有效安排。', tone: 'attention' },
] : [])
const cadenceDefinitions: Record<string, { label: string; range: string; description: string }> = {
  '1-7': { label: '短期', range: '1–7 天', description: '較常重新遇見。' },
  '8-14': { label: '短期', range: '8–14 天', description: '約兩週一次。' },
  '15-30': { label: '中期', range: '15–30 天', description: '約每月重新遇見。' },
  '31-60': { label: '中期', range: '31–60 天', description: '保留較長的呼吸空間。' },
  '61-90': { label: '長期', range: '61–90 天', description: '目前不需要頻繁出現。' },
  '91-180': { label: '長期', range: '91–180 天', description: '適合慢慢醞釀。' },
  '181+': { label: '長期醞釀', range: '181 天以上', description: '刻意留給未來的自己。' },
}
const cadenceBands = computed(() => (snapshot.value?.cadenceBands ?? []).map(item => ({ ...item, ...cadenceDefinitions[item.key] })))
const largestCadenceValue = computed(() => Math.max(...cadenceBands.value.map(item => item.cardCount), 1))
const totalForecast = computed(() => (snapshot.value?.forecastDays ?? []).reduce((total, day) => total + day.cardCount, 0))
const forecastDays = computed(() => (snapshot.value?.forecastDays ?? []).map(day => ({
  ...day,
  dateLabel: new Intl.DateTimeFormat('zh-TW', { month: 'numeric', day: 'numeric' }).format(new Date(`${day.date}T00:00:00`)),
  dayLabel: new Intl.DateTimeFormat('zh-TW', { weekday: 'short' }).format(new Date(`${day.date}T00:00:00`)),
})))
const snoozeDefinitions: Record<string, { label: string; description: string }> = {
  '3-5': { label: '稍後 3–5 次', description: '自上次完成回顧後，仍反覆暫時離開注意力。' },
  '6-10': { label: '稍後 6–10 次', description: '可能還在醞釀，也可能尚未值得現在處理。' },
  '11+': { label: '稍後超過 10 次', description: '可以重新決定它是否仍需要主動出現。' },
}
const snoozeBands = computed(() => (snapshot.value?.snoozeBands ?? []).map(item => ({ ...item, ...snoozeDefinitions[item.key] })))
</script>

<template>
  <section class="card-return-page">
    <router-link class="back-link" to="/overview">
      <el-icon><ArrowLeft /></el-icon>
      返回資訊轉化觀測台
    </router-link>

    <header class="page-header">
      <div>
        <div class="page-kicker">資訊轉化觀測台／第二層</div>
        <h1>卡片與回流</h1>
        <p>看看內容目前如何被安放，以及接下來會如何重新回到你面前。</p>
      </div>
    </header>

    <div v-if="isLoading" class="overview-state">正在整理卡片與回流資料…</div>
    <el-result v-else-if="isError" icon="error" title="暫時無法取得卡片與回流資料" sub-title="請確認後端服務後再試。">
      <template #extra><el-button type="primary" @click="refetch()">重新取得</el-button></template>
    </el-result>
    <div v-else-if="snapshot" class="page-stack">
      <section class="panel state-panel" aria-labelledby="card-return-state-title">
        <div class="section-heading section-heading--inline">
          <div>
            <span class="section-eyebrow">目前狀態</span>
            <h2 id="card-return-state-title">此刻，卡片怎麼被安放</h2>
          </div>
          <p>這些是目前存量，不是近幾天的活動，也不代表卡片表現好壞。</p>
        </div>
        <div class="state-grid">
          <article v-for="item in stateItems" :key="item.key" class="state-item" :class="{ 'state-item--attention': item.tone === 'attention' }">
            <div><strong>{{ item.value }}</strong><span>{{ item.unit }}</span></div>
            <h3>{{ item.label }}</h3>
            <p>{{ item.description }}</p>
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
          <p class="section-intro">這是現在的安排分布，不是催促內容加速成熟的進度表。</p>
          <div class="cadence-list">
            <article v-for="item in cadenceBands" :key="item.key" class="cadence-item">
              <div class="cadence-label"><span>{{ item.label }}</span><strong>{{ item.range }}</strong></div>
              <div class="cadence-track" aria-hidden="true"><i :style="{ width: `${(item.cardCount / largestCadenceValue) * 100}%` }" /></div>
              <div class="cadence-value"><strong>{{ item.cardCount }}</strong><span>張</span></div>
              <p>{{ item.description }}</p>
            </article>
          </div>
        </section>

        <section class="panel forecast-panel" aria-labelledby="card-return-forecast-title">
          <div class="section-heading">
            <div>
              <span class="section-eyebrow">接下來一週</span>
              <h2 id="card-return-forecast-title">預計重新出現 {{ totalForecast }} 張</h2>
            </div>
            <el-tooltip content="這是卡片目前排定的下一次出現時間預覽，不代表你必須在當天處理它們。" placement="top">
              <el-icon class="info-icon"><InfoFilled /></el-icon>
            </el-tooltip>
          </div>
          <p class="section-intro">看見接下來的節奏，不把它變成壓力型日曆。</p>
          <div class="forecast-chart" aria-label="未來七天預計回流卡片數">
            <article v-for="day in forecastDays" :key="day.date" class="forecast-day">
              <strong>{{ day.cardCount }}</strong>
              <div class="forecast-column"><i :style="{ height: `${Math.max(12, day.cardCount * 15)}px` }" /></div>
              <span>{{ day.dayLabel }}</span>
              <small>{{ day.dateLabel }}</small>
            </article>
          </div>
        </section>
      </div>

      <section class="panel snooze-panel" aria-labelledby="card-return-snooze-title">
        <div class="section-heading section-heading--inline">
          <div>
            <span class="section-eyebrow">稍後再看</span>
            <h2 id="card-return-snooze-title">有些內容反覆暫時離開注意力</h2>
          </div>
          <p>這可能代表還在醞釀，也可能代表它不需要持續主動出現。</p>
        </div>
        <div class="snooze-grid">
          <article v-for="item in snoozeBands" :key="item.key" class="snooze-item">
            <strong>{{ item.cardCount }}<span>張</span></strong>
            <h3>{{ item.label }}</h3>
            <p>{{ item.description }}</p>
          </article>
        </div>
      </section>

      <details class="definition-disclosure">
        <summary>查看這些數字的計算方式</summary>
        <p>此頁由後端依目前資料聚合。稍後再看的次數，是自上次完成回顧後的連續次數；不代表卡片一生累積被延後的次數。</p>
      </details>
    </div>
  </section>
</template>

<style scoped>
.card-return-page { width: 100%; max-width: 1240px; margin: 0 auto; }
.back-link { display: inline-flex; align-items: center; gap: 5px; color: var(--el-text-color-secondary); font-size: var(--type-ui); text-decoration: none; }
.back-link:hover { color: var(--el-color-primary); }
.page-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 24px; margin: 18px 0 24px; }
.page-kicker, .section-eyebrow { display: block; color: var(--el-text-color-secondary); font-size: var(--type-meta); font-weight: 600; letter-spacing: .06em; text-transform: uppercase; }
.page-header h1 { margin: 4px 0 0; font-size: var(--type-page-title); }
.page-header p { margin-top: 8px; color: var(--el-text-color-secondary); font-size: var(--type-ui); line-height: var(--leading-ui); }
.page-stack { display: flex; flex-direction: column; gap: 16px; }
.panel, .definition-disclosure { border: 1px solid var(--el-border-color-light); border-radius: 14px; background: var(--el-bg-color); }
.panel { padding: 22px 24px; }
.section-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.section-heading h2 { margin-top: 3px; color: var(--el-text-color-primary); font-size: var(--type-section-title); }
.section-heading--inline { align-items: flex-end; }
.section-heading--inline > p { max-width: 340px; color: var(--el-text-color-secondary); font-size: var(--type-caption); line-height: var(--leading-ui); text-align: right; }
.section-intro { margin-top: 9px; color: var(--el-text-color-secondary); font-size: var(--type-meta); line-height: var(--leading-ui); }
.info-icon { flex: 0 0 auto; margin-top: 3px; color: var(--el-text-color-placeholder); cursor: help; }
.state-grid { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 8px; margin-top: 18px; }
.state-item { min-width: 0; padding: 10px 12px; border-left: 1px solid var(--el-border-color-lighter); }
.state-item:first-child { border-left: none; }
.state-item--attention { background: color-mix(in srgb, var(--el-color-warning) 7%, var(--el-bg-color)); border-radius: 8px; }
.state-item > div { white-space: nowrap; }
.state-item strong, .snooze-item > strong { color: var(--el-text-color-primary); font-size: var(--type-section-title); font-variant-numeric: tabular-nums; }
.state-item > div > span, .snooze-item > strong > span { margin-left: 3px; color: var(--el-text-color-secondary); font-size: var(--type-meta); font-weight: 400; }
.state-item h3, .snooze-item h3, .signal-item h3 { margin-top: 7px; color: var(--el-text-color-primary); font-size: var(--type-ui); font-weight: 600; }
.state-item p, .snooze-item p, .signal-item p { margin-top: 4px; color: var(--el-text-color-secondary); font-size: var(--type-meta); line-height: var(--leading-ui); }
.return-grid { display: grid; grid-template-columns: minmax(0, 1.3fr) minmax(330px, .7fr); gap: 16px; }
.cadence-list { display: flex; flex-direction: column; gap: 8px; margin-top: 18px; }
.cadence-item { display: grid; grid-template-columns: 124px minmax(80px, 1fr) 42px; column-gap: 12px; align-items: center; }
.cadence-label { display: flex; flex-direction: column; gap: 1px; }
.cadence-label span { color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.cadence-label strong { color: var(--el-text-color-primary); font-size: var(--type-ui); font-weight: 600; }
.cadence-track { height: 7px; overflow: hidden; border-radius: 99px; background: var(--el-fill-color); }
.cadence-track i { display: block; height: 100%; min-width: 4px; border-radius: inherit; background: var(--el-color-primary-light-3); }
.cadence-value { text-align: right; white-space: nowrap; }
.cadence-value strong { color: var(--el-text-color-primary); font-size: var(--type-ui); font-variant-numeric: tabular-nums; }
.cadence-value span { margin-left: 2px; color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.cadence-item > p { grid-column: 2 / 4; margin-top: -2px; color: var(--el-text-color-secondary); font-size: var(--type-meta); line-height: var(--leading-ui); }
.forecast-chart { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); align-items: end; gap: 8px; min-height: 208px; margin-top: 14px; padding-top: 14px; }
.forecast-day { display: flex; flex-direction: column; align-items: center; min-width: 0; gap: 5px; }
.forecast-day > strong { color: var(--el-text-color-primary); font-size: var(--type-ui); font-variant-numeric: tabular-nums; }
.forecast-column { display: flex; align-items: flex-end; height: 86px; width: 100%; border-bottom: 1px solid var(--el-border-color-light); }
.forecast-column i { display: block; width: 100%; border-radius: 5px 5px 0 0; background: var(--el-color-primary-light-3); }
.forecast-day > span { color: var(--el-text-color-primary); font-size: var(--type-meta); white-space: nowrap; }
.forecast-day > small { color: var(--el-text-color-secondary); font-size: 11px; white-space: nowrap; }
.snooze-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 0; margin-top: 18px; }
.snooze-item { min-width: 0; padding: 8px 20px; border-left: 1px solid var(--el-border-color-lighter); }
.snooze-item:first-child { padding-left: 0; border-left: none; }
.signal-list { display: flex; flex-direction: column; margin-top: 16px; }
.signal-item { display: flex; align-items: center; justify-content: space-between; gap: 20px; padding: 14px 0; border-top: 1px solid var(--el-border-color-lighter); }
.signal-item:first-child { border-top: none; padding-top: 0; }
.signal-item h3 { margin-top: 0; }
.signal-item :deep(.el-button) { flex: 0 0 auto; height: auto; padding: 0; font-size: var(--type-meta); }
.definition-disclosure { padding: 0 24px; }
.definition-disclosure > summary { padding: 16px 0; color: var(--el-color-primary); cursor: pointer; font-size: var(--type-ui); font-weight: 600; }
.definition-disclosure > p { margin: 0 0 15px; color: var(--el-text-color-secondary); font-size: var(--type-meta); line-height: var(--leading-ui); }
@media (max-width: 1100px) { .state-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); } .state-item:nth-child(4) { border-left: none; } }
@media (max-width: 860px) { .return-grid { grid-template-columns: 1fr; } .state-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } .state-item:nth-child(4) { border-left: 1px solid var(--el-border-color-lighter); } .state-item:nth-child(odd) { border-left: none; } }
@media (max-width: 600px) { .page-header { flex-direction: column; gap: 12px; margin-bottom: 18px; } .panel { padding: 18px; border-radius: 12px; } .section-heading--inline { align-items: flex-start; flex-direction: column; gap: 7px; } .section-heading--inline > p { max-width: none; text-align: left; } .state-grid, .snooze-grid { grid-template-columns: 1fr; gap: 0; } .state-item, .state-item:nth-child(4), .state-item:nth-child(odd), .snooze-item, .snooze-item:first-child { padding: 12px 0; border-top: 1px solid var(--el-border-color-lighter); border-left: none; border-radius: 0; } .state-item:first-child, .snooze-item:first-child { border-top: none; } .cadence-item { grid-template-columns: 104px minmax(50px, 1fr) 36px; column-gap: 8px; } .cadence-item > p { grid-column: 1 / 4; margin: 2px 0 5px; } .forecast-chart { gap: 5px; } .forecast-day > span { font-size: 11px; } .forecast-day > small { font-size: 10px; } .signal-item { align-items: flex-start; flex-direction: column; gap: 7px; } .definition-disclosure { padding: 0 18px; border-radius: 12px; } }
</style>
