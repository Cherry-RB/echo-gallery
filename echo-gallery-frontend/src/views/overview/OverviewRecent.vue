<script setup lang="ts">
import { computed, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useRouter } from 'vue-router'
import { InfoFilled } from '@element-plus/icons-vue'
import PageHeader from '../../components/ui/PageHeader.vue'
import SectionHeader from '../../components/ui/SectionHeader.vue'
import OverviewNavigation from '../../components/overview/OverviewNavigation.vue'
import { overviewApi } from '../../utils/api/overviewApi'
import type { OverviewPeriodDays } from '../../types/overview'
import { eventKinds, ranks } from '../../utils/treasure'

const selectedPeriod = ref<OverviewPeriodDays>(30)
const router = useRouter()
const { data: overview, isLoading, isError, refetch } = useQuery({
  queryKey: computed(() => ['overview', 'recent', selectedPeriod.value]),
  queryFn: () => overviewApi.getRecentOverview(selectedPeriod.value),
})

const period = computed(() => overview.value?.period ?? {
  flow: { reengagedCardCount: 0, reviewedCardCount: 0 },
  generativity: { derivedCardCount: 0, sourceCardCount: 0 },
  treasureGrowth: { collectedCount: 0, promotedTreasureCount: 0, yieldTreasureCount: 0, yieldRecordCount: 0 },
  recentTreasureYields: [],
  recentTreasureProgress: [],
  activities: [],
  derivedCards: [],
  recentExperimentMaterials: [],
})

const treasureSections = computed(() => [
  { key: 'yields', id: 'overview-treasure-yields', eyebrow: '價值延續', title: '新的收穫',
    events: period.value.recentTreasureYields, empty: '這段時間尚無新的收益紀錄。' },
  { key: 'progress', id: 'overview-treasure-progress', eyebrow: '成果進展', title: '近期入藏與試煉',
    events: period.value.recentTreasureProgress, empty: '這段時間還沒有新的入藏、試煉嘗試或晉級紀錄。' },
])

const periodFigures = computed(() => [
  { key: 'reengaged', value: period.value.flow.reengagedCardCount, unit: '張', label: '故物重逢', description: `其中 ${period.value.flow.reviewedCardCount} 張完成回顧`, explanation: '建立於這段時間之前，且在這段時間重新被閱讀、加入探索線或成為衍生來源的卡片。不是越高越好。' },
  { key: 'derived', value: period.value.generativity.derivedCardCount, unit: '張', label: '新枝萌生', description: `${period.value.generativity.sourceCardCount} 張既有卡片成為來源`, explanation: '保留一張或多張來源卡關係而建立的新卡；單純新增收藏或加入探索線不計入。' },
  { key: 'treasure-yield', value: period.value.treasureGrowth.yieldTreasureCount, unit: '件', label: '奇珍再現價值', description: `留下 ${period.value.treasureGrowth.yieldRecordCount} 筆收益`, explanation: '以實際發生日期計算。相同成果的多筆收益仍為一件奇珍；設定挑戰不算產出，也不代表已取得收入。' },
])

const activityDefinitions = {
  created: '新增卡片', offered: '今日回流再次出現', reviewed: '完成回顧', 'experiment-material': '加入探索材料', derived: '整理出新卡', 'exploration-record': '留下探索發現', 'treasure-collected': '奇珍入藏', 'treasure-promoted': '曾晉級的奇珍', 'treasure-yield': '奇珍收益',
} as const

const formatDate = (value: string) => new Intl.DateTimeFormat('zh-TW', { month: 'numeric', day: 'numeric' }).format(new Date(value))
const openCard = (cardId: number) => router.push({ name: 'CardDetail', params: { id: cardId }, query: { from: 'overview' } })
const openExperiment = (experimentId: number) => router.push({ name: 'ExperimentDetail', params: { id: experimentId } })
</script>

<template>
  <section class="overview-page app-page app-page--workspace">
    <PageHeader title="近期回顧" description="時日流轉，有些舊物再次派上用場，有些嘗試終於長出新枝。看看這段旅程留下了什麼。">
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
            <div><span class="section-eyebrow">回看近期</span><h2 id="overview-period-title">這些日子，留下了什麼？</h2></div>
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
            <div class="section-title-row"><div><span class="section-eyebrow">新理解</span><h2 id="overview-derived-title">從舊日積累中長出的新篇章</h2></div></div>
            <div v-if="period.derivedCards.length" class="content-list">
              <article v-for="card in period.derivedCards.slice(0, 3)" :key="card.cardId" class="content-card">
                <button type="button" class="content-card-main" @click="openCard(card.cardId)"><span>{{ formatDate(card.createdAt) }} 長出</span><h3>{{ card.title }}</h3></button>
                <p>來自 {{ card.sourceCards.map(source => source.title).join('・') }}</p><button type="button" class="context-link" @click="openExperiment(card.experimentId)">{{ card.experimentTitle }}</button>
              </article>
            </div>
            <p v-else class="empty-copy">這段時間沒有保留來源關係的新卡。</p>
          </section>

          <section class="content-panel" aria-labelledby="overview-materials-title">
            <div class="section-title-row"><div><span class="section-eyebrow">材料聚集</span><h2 id="overview-materials-title">新到手的探索材料</h2></div><el-tooltip content="這些是近期加入探索線、但不是從既有卡片衍生而來的材料；它們不計入「新枝萌生」。" placement="top"><el-icon class="info-icon"><InfoFilled /></el-icon></el-tooltip></div>
            <div v-if="period.recentExperimentMaterials.length" class="content-list">
              <article v-for="material in period.recentExperimentMaterials.slice(0, 3)" :key="`${material.experimentId}-${material.cardId}`" class="content-card">
                <button type="button" class="content-card-main" @click="openCard(material.cardId)"><span>{{ formatDate(material.addedAt) }} · {{ material.sourceKind === 'EXPLORATION' ? '探索整理' : '放入材料' }}</span><h3>{{ material.title }}</h3></button>
                <button type="button" class="context-link" @click="openExperiment(material.experimentId)">{{ material.experimentTitle }}</button>
              </article>
            </div>
            <p v-else class="empty-copy">這段時間沒有新加入探索線的材料。</p>
          </section>
        </div>

        <div class="content-grid">
          <section v-for="section in treasureSections" :key="section.key" class="content-panel" :aria-labelledby="section.id" :data-treasure-section="section.key">
            <SectionHeader :id="section.id" :title="section.title" :eyebrow="section.eyebrow" :description="section.events.length ? `近 ${selectedPeriod} 天・最近 ${section.events.length} 筆` : `近 ${selectedPeriod} 天`">
              <template #actions><router-link to="/treasures" class="treasure-gallery-link">查看館藏</router-link></template>
            </SectionHeader>
            <div v-if="section.events.length" class="content-list">
              <article v-for="(event, index) in section.events" :key="`${event.kind}-${event.treasureId}-${event.eventAt}-${index}`" class="content-card">
                <router-link :to="`/treasures/${event.treasureId}`" class="content-card-main treasure-record-main">
                  <span><time :datetime="event.eventAt">{{ formatDate(event.eventAt) }}</time> · {{ event.rank ? `晉級至${ranks[event.rank]}` : eventKinds[event.kind] }}</span>
                  <h3>{{ event.kind === 'COLLECTED' ? event.treasureTitle : event.eventTitle }}</h3>
                </router-link>
                <router-link v-if="event.kind !== 'COLLECTED'" :to="`/treasures/${event.treasureId}`" class="context-link treasure-record-source">{{ event.treasureTitle }}</router-link>
              </article>
            </div>
            <p v-else class="empty-copy">{{ section.empty }}</p>
          </section>
        </div>
        <details class="activity-disclosure"><summary>翻閱近 {{ selectedPeriod }} 天的活動紀錄</summary><div class="activity-list"><div v-for="activity in period.activities" :key="activity.key" class="activity-item"><strong>{{ activity.value }}</strong><span>{{ activityDefinitions[activity.key] }}</span></div></div></details>
      </div>
    </div>
  </section>
</template>

<style scoped>
.section-eyebrow { display: block; color: var(--el-text-color-secondary); font-size: var(--type-meta); font-weight: 600; letter-spacing: .06em; text-transform: uppercase; }
.overview-state { padding: 48px 0; color: var(--el-text-color-secondary); text-align: center; }
.period-section, .content-panel, .activity-disclosure { min-width: 0; border: 1px solid var(--el-border-color-light); border-radius: 8px; background: var(--el-bg-color); }
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
.content-card { min-width: 0; padding: 12px 0; border-top: 1px solid var(--el-border-color-lighter); overflow-wrap: anywhere; }
.content-card:first-child { padding-top: 0; border-top: 0; }
.content-card-main, .context-link { display: block; padding: 0; border: 0; background: transparent; text-align: left; cursor: pointer; font: inherit; }
.content-card-main span, .content-card p { color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.content-card-main h3 { margin: 4px 0 0; color: var(--el-text-color-primary); font-size: var(--type-ui); }
.content-card-main:hover h3, .context-link:hover { color: var(--el-color-primary); }
.content-card p { margin: 5px 0 0; line-height: var(--leading-ui); }
.context-link { margin-top: 6px; color: var(--el-color-primary); font-size: var(--type-meta); }
.treasure-gallery-link { color: var(--el-color-primary); font-size: var(--type-meta); text-decoration: none; white-space: nowrap; }
.treasure-gallery-link:hover { text-decoration: underline; }
.treasure-record-main, .treasure-record-source { text-decoration: none; }
.treasure-record-main h3 { display: -webkit-box; overflow: hidden; overflow-wrap: anywhere; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.treasure-record-source { overflow-wrap: anywhere; }
.treasure-record-main:focus-visible, .treasure-record-source:focus-visible, .treasure-gallery-link:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 2px; }
.empty-copy { margin: 18px 0 0; color: var(--el-text-color-secondary); font-size: var(--type-ui); }
.activity-disclosure { padding: 0 var(--panel-padding); }
.activity-disclosure > summary { padding: 16px 0; color: var(--el-text-color-secondary); cursor: pointer; font-size: var(--type-ui); }
.activity-disclosure[open] > summary { color: var(--el-color-primary); }
.activity-list { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); margin: 0 0 16px; }
.activity-item { min-width: 0; padding: 4px 14px; border-left: 1px solid var(--el-border-color-lighter); }
.activity-item:nth-child(4n + 1) { padding-left: 0; border-left: 0; }
.activity-item:nth-child(n + 5) { padding-top: 16px; border-top: 1px solid var(--el-border-color-lighter); }
.activity-item span { display: block; margin-top: 4px; color: var(--el-text-color-secondary); font-size: var(--type-meta); }
@media (max-width: 900px) { .content-grid { grid-template-columns: minmax(0, 1fr); } .activity-list { grid-template-columns: repeat(3, minmax(0, 1fr)); } .activity-item:nth-child(4n + 1) { padding-left: 14px; border-left: 1px solid var(--el-border-color-lighter); } .activity-item:nth-child(3n + 1) { padding-left: 0; border-left: 0; } .activity-item:nth-child(n + 4) { padding-top: 16px; border-top: 1px solid var(--el-border-color-lighter); } }
@media (max-width: 760px) { .period-section, .content-panel { padding: var(--panel-padding); border-radius: var(--panel-radius); } .period-heading, .section-title-row { gap: 12px; } .period-heading { flex-direction: column; } .period-switcher, .period-switcher :deep(.el-radio-button), .period-switcher :deep(.el-radio-button__inner) { width: 100%; } .period-switcher { display: flex; } .period-switcher :deep(.el-radio-button) { flex: 1 1 0; } .period-figures { grid-template-columns: minmax(0, 1fr); gap: 12px; } .period-figure, .period-figure:first-child { padding: 12px 0 0; border-top: 1px solid var(--el-border-color-lighter); border-left: 0; } .period-figure:first-child { padding-top: 0; border-top: 0; } .period-figure .info-icon, .period-figure:last-child .info-icon { right: 0; } .activity-disclosure { padding: 0 var(--panel-padding); border-radius: var(--panel-radius); } .activity-list { grid-template-columns: repeat(2, minmax(0, 1fr)); } .activity-item, .activity-item:nth-child(4n + 1) { padding: 12px 18px 12px 0; border: 0; border-top: 1px solid var(--el-border-color-lighter); } .activity-item:nth-child(-n + 2) { border-top: 0; } .activity-item:nth-child(even) { padding: 12px 0 12px 18px; border-left: 1px solid var(--el-border-color-lighter); } .activity-item:nth-child(odd) { padding-left: 0; border-left: 0; } }
</style>
