<script setup lang="ts">
import { computed, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useRouter } from 'vue-router'
import { ArrowRight, InfoFilled } from '@element-plus/icons-vue'
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
  { key: 'recurring', label: '正在回流', value: current.value.recurringCardCount, unit: '張卡片', description: '會依各自節奏，再次出現在 Today。' },
  { key: 'paused', label: '暫停中', value: current.value.pausedCardCount, unit: '張卡片', description: '目前不會進入回流安排。' },
  { key: 'processing', label: '待整理', value: current.value.needsProcessingCardCount, unit: '張卡片', description: '你曾明確留下「之後再處理」的訊號。' },
  { key: 'experiments', label: '未封存實驗場', value: current.value.activeExperimentCount, unit: '個主題', description: '仍在收集或持續觀察相關材料。' },
  { key: 'next-steps', label: '已有下一步的議題', value: current.value.workWithNextStepCount, unit: '個議題', description: '方向已寫下，但不是待辦或完成承諾。' },
])
const metrics = computed(() => [
  { key: 'flow', value: period.value?.flow.reengagedCardCount ?? 0, unit: '張較早建立的卡片', question: '以前留下的卡，有重新回到現在嗎？', description: `其中 ${period.value?.flow.reviewedCardCount ?? 0} 張最後一次完成回顧。`, interpretation: '這些卡片在期間內最後一次完成回顧、被放入實驗場或議題，或成為衍生來源。不是越多越好。' },
  { key: 'generativity', value: period.value?.generativity.derivedCardCount ?? 0, unit: '張衍生卡片', question: '它們有長出新的東西嗎？', description: `${period.value?.generativity.sourceCardCount ?? 0} 張既有卡片成為來源。`, interpretation: '只計由一張或多張既有卡片衍生、且保留來源關係的新卡；單純新增收藏不計入。' },
  { key: 'closure', value: period.value?.closure.workWithFollowUpCount ?? 0, unit: '個議題', question: '有哪些想法已經可以往前走？', description: '先前寫下方向後，又留下新的議題更新。', interpretation: '這不代表 nextStep 已執行或完成，只表示方向後續留下了新的紀錄。' }
])
const activityDefinitions = {
  created: { label: '新增卡片', unit: '張', description: '把新的內容保存到系統。' },
  offered: { label: '最後一次在 Today 出現', unit: '張', description: '目前可知最後一次回流時間落在此期間的卡片。' },
  reviewed: { label: '最後一次完成回顧', unit: '張', description: '目前可知最後一次完成回顧時間落在此期間的卡片。' },
  'experiment-material': { label: '放入實驗場', unit: '次', description: '作為材料加入，未必有來源關係。' },
  'work-linked': { label: '帶入議題', unit: '次', description: '成為某個議題的參考素材。' },
  derived: { label: '長出新卡', unit: '張', description: '保留來源關係的新卡片。' },
  'work-update': { label: '議題更新', unit: '次', description: '留下新的研判或後續。' },
} as const
const attentionContent = computed(() => (current.value?.attentionSignals ?? []).map(signal => ({
  ...signal,
  title: signal.key === 'processing' ? `${signal.cardCount} 張卡片等待你決定如何處理` : `${signal.cardCount} 張卡片自上次回顧後多次稍後再看`,
  description: signal.key === 'processing' ? '它們曾被標記為「待整理」。這只是保留中的訊號，不代表必須立刻清空。' : '這表示它們在上次完成回顧後，多次暫時離開注意力；可能仍在醞釀，也可能不值得目前處理。',
  actionLabel: signal.key === 'processing' ? '查看待整理卡片' : '查看稍後再看',
})))
const getAttentionSymbol = (kind: OverviewAttentionSignal['key']) => ({ processing: '◌', snooze: '↗' })[kind]
const formatDate = (value: string) => new Intl.DateTimeFormat('zh-TW', { month: 'numeric', day: 'numeric' }).format(new Date(value))
const openCard = (cardId: number) => router.push({ name: 'CardDetail', params: { id: cardId }, query: { from: 'overview' } })
const openExperiment = (experimentId: number) => router.push({ name: 'ExperimentDetail', params: { id: experimentId } })
const openWork = (workId: number) => router.push({ name: 'WorkDetail', params: { id: workId } })
const openCardReturn = () => router.push('/overview/cards')
const openCurrentItem = (key: string) => {
  if (key === 'experiments') router.push('/experiments')
  else if (key === 'next-steps') router.push('/works')
  else openCardReturn()
}
</script>

<template>
  <section class="overview-page">
    <header class="overview-header">
      <div>
        <h1 class="page-title">資訊轉化觀測台</h1>
        <p class="page-description">把現在的系統狀態，與一段時間內發生的變化分開看。</p>
      </div>
    </header>

    <div v-if="isLoading" class="overview-state">正在整理目前的觀測資料…</div>
    <el-result v-else-if="isError" icon="error" title="暫時無法取得觀測資料" sub-title="請確認後端服務後再試。">
      <template #extra><el-button type="primary" @click="refetch()">重新取得</el-button></template>
    </el-result>
    <div v-else-if="overview" class="overview-stack">
      <section class="current-section" aria-labelledby="overview-current-title">
        <div class="section-heading section-heading--inline">
          <div>
            <span class="section-eyebrow">現在的系統</span>
            <h2 id="overview-current-title">此刻，內容正在怎麼被安放</h2>
          </div>
          <p>這些是目前狀態，不受近 7／30／90 天篩選影響。</p>
        </div>
        <div class="current-groups">
          <section class="current-group" aria-label="內容目前怎麼被安放">
            <h3>內容目前怎麼被安放</h3>
            <div class="current-state-grid">
              <button v-for="item in currentItems.slice(0, 3)" :key="item.key" type="button" class="current-state-item" :aria-label="`查看${item.label}的對應內容`" @click="openCurrentItem(item.key)">
                <strong>{{ item.value }}</strong><span>{{ item.unit }}</span>
                <h4>{{ item.label }}</h4>
                <p>{{ item.description }}</p>
                <span class="drilldown-hint">查看<el-icon><ArrowRight /></el-icon></span>
              </button>
            </div>
          </section>
          <section class="current-group current-direction-group" aria-label="目前有哪些脈絡與方向">
            <h3>目前有哪些脈絡與方向</h3>
            <div class="current-direction-grid">
              <button v-for="item in currentItems.slice(3)" :key="item.key" type="button" class="current-direction-item" :aria-label="`查看${item.label}的對應內容`" @click="openCurrentItem(item.key)">
                <div><strong>{{ item.value }}</strong><span>{{ item.unit }}</span></div>
                <h4>{{ item.label }}</h4>
                <p>{{ item.description }}</p>
                <span class="drilldown-hint">查看<el-icon><ArrowRight /></el-icon></span>
              </button>
            </div>
          </section>
        </div>
        <router-link class="card-return-link" to="/overview/cards">查看卡片與回流狀態</router-link>
      </section>

      <section class="period-review-section" aria-labelledby="overview-period-title">
        <div class="period-review-copy">
          <div>
            <span class="section-eyebrow">回看近期</span>
            <h2 id="overview-period-title">選一段時間，看看留下了什麼</h2>
          </div>
          <p>只影響下方近期觀測與最近長出的內容，不改變現在的系統。</p>
        </div>
        <el-radio-group v-model="selectedPeriod" class="period-switcher" aria-label="回看期間">
          <el-radio-button :value="7">近 7 天</el-radio-button>
          <el-radio-button :value="30">近 30 天</el-radio-button>
          <el-radio-button :value="90">近 90 天</el-radio-button>
        </el-radio-group>
      </section>

      <div class="main-content-grid">
        <section class="content-panel derived-panel" aria-labelledby="overview-derived-title">
          <div class="section-heading">
            <div>
              <span class="section-eyebrow">近期長出的東西</span>
              <h2 id="overview-derived-title">近 {{ selectedPeriod }} 天，長出了什麼</h2>
            </div>
            <el-tooltip content="只顯示由既有卡片衍生、且保留來源關係的新卡片；單純新增收藏不計入。" placement="top">
              <el-icon class="info-icon"><InfoFilled /></el-icon>
            </el-tooltip>
          </div>
          <div class="derived-list">
            <article v-for="card in period.derivedCards" :key="card.cardId" class="derived-card">
              <button type="button" class="derived-card-main" @click="openCard(card.cardId)">
                <span class="derived-date">{{ formatDate(card.createdAt) }} 長出</span>
                <h3>{{ card.title }}</h3>
              </button>
              <button type="button" class="experiment-context-link" @click="openExperiment(card.experimentId)">
                在實驗場「{{ card.experimentTitle }}」中長出
                <el-icon><ArrowRight /></el-icon>
              </button>
              <div class="source-list" aria-label="來源卡片">
                <span>來源</span>
                <em v-for="source in card.sourceCards" :key="source.cardId">{{ source.title }}</em>
              </div>
              <div class="tag-list">
                <el-tag v-for="tag in card.tags" :key="tag" size="small" effect="plain">{{ tag }}</el-tag>
              </div>
            </article>
          </div>
        </section>

        <section class="content-panel next-step-panel" aria-labelledby="overview-next-step-title">
          <div class="section-heading">
            <div>
            <span class="section-eyebrow">現在可以往前走</span>
              <h2 id="overview-next-step-title">已經想得夠清楚的方向</h2>
            </div>
            <el-tooltip content="nextStep 是目前值得帶往現實的方向，不是待辦、期限或完成承諾。" placement="top">
              <el-icon class="info-icon"><InfoFilled /></el-icon>
            </el-tooltip>
          </div>
          <div class="next-step-list">
            <button v-for="step in current.nextSteps" :key="step.workId" type="button" class="next-step-item" @click="openWork(step.workId)">
              <span>{{ step.workTitle }}</span>
              <strong>{{ step.nextStep }}</strong>
              <small>更新於 {{ formatDate(step.updatedAt) }}</small>
              <i class="drilldown-hint">進入議題<el-icon><ArrowRight /></el-icon></i>
            </button>
          </div>
        </section>
      </div>

      <section v-if="period.recentExperimentMaterials.length" class="content-panel materials-panel" aria-labelledby="overview-materials-title">
        <div class="section-heading">
          <div>
            <span class="section-eyebrow">近期進入實驗場的材料</span>
            <h2 id="overview-materials-title">哪些內容正在聚集</h2>
          </div>
          <el-tooltip content="只顯示近期放入實驗場、但不是由既有卡片衍生出的材料。它們正在形成脈絡，不代表已長出新理解。" placement="top">
            <el-icon class="info-icon"><InfoFilled /></el-icon>
          </el-tooltip>
        </div>
        <div class="materials-list">
          <article v-for="material in period.recentExperimentMaterials" :key="`${material.experimentId}-${material.cardId}`" class="material-card">
            <button type="button" class="derived-card-main" @click="openCard(material.cardId)">
              <span class="derived-date">{{ formatDate(material.addedAt) }} 放入</span>
              <h3>{{ material.title }}</h3>
            </button>
            <button type="button" class="experiment-context-link" @click="openExperiment(material.experimentId)">
              放入實驗場「{{ material.experimentTitle }}」
              <el-icon><ArrowRight /></el-icon>
            </button>
            <div v-if="material.tags.length" class="tag-list">
              <el-tag v-for="tag in material.tags" :key="tag" size="small" effect="plain">{{ tag }}</el-tag>
            </div>
          </article>
        </div>
      </section>

      <section class="period-summary-panel" aria-labelledby="overview-summary-title">
        <div class="section-heading">
          <div>
            <span class="section-eyebrow">近 {{ selectedPeriod }} 天的觀測</span>
            <h2 id="overview-summary-title">這段時間留下的線索</h2>
          </div>
          <el-tooltip content="這是根據可被可靠保留的資料所寫的事實摘要，不是健康評分或使用建議。" placement="top">
            <el-icon class="info-icon"><InfoFilled /></el-icon>
          </el-tooltip>
        </div>
        <p class="summary-copy">近 {{ selectedPeriod }} 天，有 {{ metrics[0].value }} 張較早建立的卡片再次參與；從既有材料長出 {{ metrics[1].value }} 張新卡；{{ metrics[2].value }} 個議題在先前方向後留下新更新。</p>
        <div class="observation-list" :aria-label="`近 ${selectedPeriod} 天可見的變化`">
          <article v-for="item in metrics" :key="item.key" class="observation-item">
            <div class="observation-value"><strong>{{ item.value }}</strong><span>{{ item.unit }}</span></div>
            <div>
              <h3>{{ item.question }}</h3>
              <p>{{ item.description }}</p>
            </div>
            <el-tooltip :content="item.interpretation" placement="top">
              <el-icon class="info-icon"><InfoFilled /></el-icon>
            </el-tooltip>
          </article>
        </div>
      </section>

      <details class="activity-disclosure">
        <summary>查看近 {{ selectedPeriod }} 天的完整近期紀錄</summary>
        <p>每一項都是獨立紀錄，不是流程，也不能互相比較。</p>
        <div class="activity-list">
          <article v-for="activity in period.activities" :key="activity.key" class="activity-item">
            <div class="activity-meta">
              <strong>{{ activity.value }}<small>{{ activityDefinitions[activity.key].unit }}</small></strong>
              <div><h3>{{ activityDefinitions[activity.key].label }}</h3><small>{{ activityDefinitions[activity.key].description }}</small></div>
            </div>
          </article>
        </div>
      </details>

      <section class="attention-section" aria-labelledby="overview-attention-title">
        <div class="section-heading section-heading--inline">
          <div>
            <span class="section-eyebrow">值得看一眼</span>
            <h2 id="overview-attention-title">讓摩擦可見，但不催促處理</h2>
          </div>
          <p>只有有明確訊號時才顯示。</p>
        </div>
        <div class="attention-grid">
          <article v-for="item in attentionContent" :key="item.key" class="attention-card">
            <span class="attention-symbol" aria-hidden="true">{{ getAttentionSymbol(item.key) }}</span>
            <div>
              <h3>{{ item.title }}</h3>
              <p>{{ item.description }}</p>
              <el-button link type="primary" @click="openCardReturn">{{ item.actionLabel }}</el-button>
            </div>
          </article>
        </div>
      </section>
    </div>
  </section>
</template>

<style scoped>
.overview-page { width: 100%; max-width: 1240px; margin: 0 auto; }
.overview-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 24px; margin-bottom: 24px; }
.page-kicker, .section-eyebrow { display: block; color: var(--el-text-color-secondary); font-size: var(--type-meta); font-weight: 600; letter-spacing: .06em; text-transform: uppercase; }
.page-title { margin: 4px 0 0; font-size: var(--type-page-title); }
.page-description { margin-top: 8px; color: var(--el-text-color-secondary); font-size: var(--type-ui); line-height: var(--leading-ui); }
.overview-controls { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; justify-content: flex-end; }
.overview-stack { display: flex; flex-direction: column; gap: 16px; }
.current-section, .period-review-section, .content-panel, .period-summary-panel, .attention-section, .activity-disclosure { border: 1px solid var(--el-border-color-light); border-radius: 14px; background: var(--el-bg-color); }
.current-section { order: 1; padding: 22px 24px; }
.current-groups { display: grid; grid-template-columns: minmax(0, 1.5fr) minmax(250px, .75fr); gap: 28px; margin-top: 18px; }
.current-group + .current-group { padding-left: 28px; border-left: 1px solid var(--el-border-color-lighter); }
.current-group > h3 { color: var(--el-text-color-primary); font-size: var(--type-ui); font-weight: 600; }
.current-state-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; margin-top: 10px; }
.current-state-item { position: relative; min-width: 0; padding: 5px 14px; border: none; border-radius: 8px; background: transparent; color: inherit; cursor: pointer; font: inherit; text-align: left; transition: background-color .2s ease; }
.current-state-item:hover, .current-state-item:focus-visible { background: var(--el-fill-color-light); outline: none; }
.current-state-item:not(:first-child)::before { position: absolute; top: 5px; bottom: 5px; left: -5px; width: 1px; background: var(--el-border-color-lighter); content: ''; }
.current-state-item > strong { color: var(--el-text-color-primary); font-size: var(--type-section-title); font-variant-numeric: tabular-nums; }
.current-state-item > span { margin-left: 3px; color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.current-state-item h4 { margin-top: 7px; color: var(--el-text-color-primary); font-size: var(--type-ui); }
.current-state-item p { margin-top: 4px; color: var(--el-text-color-secondary); font-size: var(--type-meta); line-height: var(--leading-ui); }
.current-direction-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; margin-top: 10px; }
.current-direction-item { min-width: 0; padding: 5px 0; border: none; border-radius: 8px; background: transparent; color: inherit; cursor: pointer; font: inherit; text-align: left; transition: background-color .2s ease; }
.current-direction-item:hover, .current-direction-item:focus-visible { background: var(--el-fill-color-light); outline: none; }
.current-direction-item + .current-direction-item { padding-left: 14px; border-left: 1px solid var(--el-border-color-lighter); }
.current-direction-item > div { white-space: nowrap; }
.current-direction-item strong { color: var(--el-text-color-primary); font-size: var(--type-section-title); font-variant-numeric: tabular-nums; }
.current-direction-item span { margin-left: 3px; color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.current-direction-item h4 { margin-top: 7px; color: var(--el-text-color-primary); font-size: var(--type-ui); }
.current-direction-item p { margin-top: 4px; color: var(--el-text-color-secondary); font-size: var(--type-meta); line-height: var(--leading-ui); }
.drilldown-hint { display: inline-flex; align-items: center; gap: 2px; margin-top: 7px; color: var(--el-color-primary); font-size: var(--type-meta); font-style: normal; opacity: 0; transition: opacity .2s ease; }
.drilldown-hint .el-icon { font-size: 12px; }
.current-state-item:hover .drilldown-hint, .current-state-item:focus-visible .drilldown-hint, .current-direction-item:hover .drilldown-hint, .current-direction-item:focus-visible .drilldown-hint, .next-step-item:hover .drilldown-hint, .next-step-item:focus-visible .drilldown-hint { opacity: 1; }
.card-return-link { display: inline-block; margin-top: 18px; color: var(--el-color-primary); font-size: var(--type-meta); text-decoration: none; }
.card-return-link:hover { text-decoration: underline; }
.period-review-section { display: flex; align-items: center; justify-content: space-between; order: 2; gap: 24px; padding: 15px 24px; background: var(--el-fill-color-blank); }
.period-review-copy h2 { margin-top: 3px; font-size: var(--type-section-title); }
.period-review-copy p { margin-top: 5px; color: var(--el-text-color-secondary); font-size: var(--type-meta); line-height: var(--leading-ui); }
.period-switcher { flex: 0 0 auto; }
.section-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.section-heading h2 { margin-top: 3px; font-size: var(--type-section-title); }
.section-heading--inline { align-items: flex-end; }
.section-heading--inline > p { max-width: 300px; color: var(--el-text-color-secondary); font-size: var(--type-caption); line-height: var(--leading-ui); text-align: right; }
.info-icon { flex: 0 0 auto; margin-top: 3px; color: var(--el-text-color-placeholder); cursor: help; }
.summary-copy { max-width: 880px; margin-top: 14px; color: var(--el-text-color-primary); font-size: var(--type-body); line-height: var(--leading-body); }
.period-summary-panel { order: 4; padding: 22px 24px; background: linear-gradient(120deg, color-mix(in srgb, var(--el-color-primary) 8%, var(--el-bg-color)), var(--el-bg-color) 48%); }
.observation-list { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; margin-top: 18px; }
.observation-item { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; gap: 10px; align-items: flex-start; padding: 12px 0; }
.observation-item:not(:first-child) { border-left: 1px solid var(--el-border-color-lighter); padding-left: 16px; }
.observation-value { display: flex; align-items: baseline; gap: 2px; white-space: nowrap; }
.observation-value strong { color: var(--el-text-color-primary); font-size: var(--type-section-title); font-variant-numeric: tabular-nums; }
.observation-value span { color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.observation-item h3 { color: var(--el-text-color-primary); font-size: var(--type-ui); font-weight: 600; line-height: var(--leading-ui); }
.observation-item p { margin-top: 3px; color: var(--el-text-color-secondary); font-size: var(--type-meta); line-height: var(--leading-ui); }
.activity-disclosure { order: 5; padding: 0 24px; }
.activity-disclosure > summary { padding: 17px 0; color: var(--el-color-primary); cursor: pointer; font-size: var(--type-ui); font-weight: 600; }
.activity-disclosure > summary::marker { color: var(--el-text-color-placeholder); }
.activity-disclosure > p { margin: 0 0 14px; color: var(--el-text-color-secondary); font-size: var(--type-meta); line-height: var(--leading-ui); }
.activity-list { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 28px; margin-top: 18px; }
.activity-disclosure .activity-list { margin: 0 0 12px; }
.activity-item { min-width: 0; padding: 12px 0; border-top: 1px solid var(--el-border-color-lighter); }
.activity-meta { display: flex; align-items: flex-start; gap: 11px; color: var(--el-text-color-primary); }
.activity-meta strong { flex: 0 0 38px; padding-top: 1px; color: var(--el-color-primary); font-size: var(--type-prominent); font-variant-numeric: tabular-nums; }
.activity-meta strong small { margin-left: 1px; color: var(--el-text-color-secondary); font-size: var(--type-meta); font-weight: 400; }
.activity-meta h3 { color: var(--el-text-color-primary); font-size: var(--type-ui); font-weight: 600; line-height: var(--leading-ui); }
.activity-meta div > small { display: block; margin-top: 2px; color: var(--el-text-color-secondary); font-size: var(--type-meta); line-height: var(--leading-ui); }
.main-content-grid { display: grid; order: 3; grid-template-columns: minmax(0, 1.15fr) minmax(300px, .85fr); gap: 16px; }
.content-panel { padding: 22px 24px; }
.derived-list, .next-step-list { display: flex; flex-direction: column; gap: 10px; margin-top: 18px; }
.derived-card, .next-step-item { width: 100%; border: 1px solid var(--el-border-color-lighter); border-radius: 10px; background: var(--el-fill-color-blank); color: inherit; text-align: left; transition: border-color .2s ease, background-color .2s ease, transform .2s ease; }
.derived-card:hover, .next-step-item:hover, .next-step-item:focus-visible { border-color: var(--el-color-primary-light-5); background: var(--el-color-primary-light-9); outline: none; transform: translateY(-1px); }
.derived-card { padding: 15px; }
.materials-panel { order: 4; }
.materials-list { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; margin-top: 18px; }
.material-card { min-width: 0; padding: 15px; border: 1px solid var(--el-border-color-lighter); border-radius: 10px; background: var(--el-fill-color-blank); }
.material-card h3 { display: -webkit-box; margin-top: 5px; overflow: hidden; color: var(--el-text-color-primary); font-size: var(--type-card-title); font-weight: 600; line-height: var(--leading-section); -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.derived-card-main { width: 100%; padding: 0; border: none; background: transparent; color: inherit; cursor: pointer; text-align: left; }
.derived-card-main:focus-visible, .experiment-context-link:focus-visible { border-radius: 4px; outline: 2px solid var(--el-color-primary-light-5); outline-offset: 2px; }
.derived-date { color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.derived-card h3 { display: -webkit-box; margin-top: 5px; overflow: hidden; color: var(--el-text-color-primary); font-size: var(--type-card-title); font-weight: 600; line-height: var(--leading-section); -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.experiment-context-link { display: inline-flex; align-items: center; gap: 3px; max-width: 100%; padding: 0; margin-top: 7px; border: none; background: transparent; color: var(--el-color-primary); cursor: pointer; font: inherit; font-size: var(--type-meta); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.experiment-context-link:hover { text-decoration: underline; }
.experiment-context-link .el-icon { flex: 0 0 auto; font-size: 12px; }
.source-list { display: flex; align-items: baseline; flex-wrap: wrap; gap: 4px 7px; margin-top: 10px; color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.source-list > span { color: var(--el-text-color-placeholder); }
.source-list em { max-width: 150px; overflow: hidden; color: var(--el-text-color-regular); font-style: normal; text-overflow: ellipsis; white-space: nowrap; }
.source-list em:not(:last-child)::after { margin-left: 7px; color: var(--el-text-color-placeholder); content: '·'; }
.tag-list { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 12px; }
.next-step-item { padding: 15px; }
.next-step-item > span { display: block; color: var(--el-color-primary); font-size: var(--type-caption); font-weight: 600; }
.next-step-item > strong { display: -webkit-box; margin-top: 6px; overflow: hidden; color: var(--el-text-color-primary); font-size: var(--type-ui); font-weight: 500; line-height: var(--leading-ui); -webkit-box-orient: vertical; -webkit-line-clamp: 3; }
.next-step-item > small { display: block; margin-top: 9px; color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.attention-section { order: 6; padding: 22px 24px; }
.attention-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; max-width: 820px; margin-top: 18px; }
.attention-card { display: flex; gap: 12px; min-width: 0; padding: 15px; border: 1px solid var(--el-border-color-lighter); border-radius: 10px; background: var(--el-fill-color-blank); }
.attention-symbol { display: flex; align-items: center; justify-content: center; flex: 0 0 auto; width: 26px; height: 26px; border-radius: 50%; background: var(--el-fill-color); color: var(--el-text-color-secondary); font-size: var(--type-prominent); }
.attention-card h3 { color: var(--el-text-color-primary); font-size: var(--type-ui); }
.attention-card p { display: -webkit-box; margin-top: 5px; overflow: hidden; color: var(--el-text-color-secondary); font-size: var(--type-meta); line-height: var(--leading-ui); -webkit-box-orient: vertical; -webkit-line-clamp: 3; }
.attention-card :deep(.el-button) { height: auto; padding: 0; margin-top: 7px; font-size: var(--type-meta); }
@media (max-width: 960px) { .current-groups { grid-template-columns: 1fr; gap: 18px; } .current-group + .current-group { padding: 18px 0 0; border-top: 1px solid var(--el-border-color-lighter); border-left: none; } .observation-list { grid-template-columns: 1fr; } .observation-item:not(:first-child) { padding-left: 0; border-top: 1px solid var(--el-border-color-lighter); border-left: none; } .activity-list, .attention-grid, .materials-list { grid-template-columns: repeat(2, minmax(0, 1fr)); } .main-content-grid { grid-template-columns: 1fr; } }
@media (max-width: 600px) { .overview-header { flex-direction: column; gap: 16px; margin-bottom: 18px; } .overview-controls { width: 100%; justify-content: flex-start; } .overview-stack { gap: 12px; } .current-section, .period-review-section, .content-panel, .period-summary-panel, .attention-section, .activity-disclosure { padding-right: 18px; padding-left: 18px; border-radius: 12px; } .period-review-section { align-items: flex-start; flex-direction: column; gap: 14px; padding-top: 18px; padding-bottom: 18px; } .period-switcher, .period-switcher :deep(.el-radio-button), .period-switcher :deep(.el-radio-button__inner) { width: 100%; } .period-switcher { display: flex; } .period-switcher :deep(.el-radio-button) { flex: 1 1 0; } .current-state-grid, .current-direction-grid, .activity-list, .attention-grid, .materials-list { grid-template-columns: 1fr; gap: 10px; } .current-state-item { padding: 10px 0; border-top: 1px solid var(--el-border-color-lighter); } .current-state-item:not(:first-child)::before { display: none; } .current-state-item:first-child { border-top: none; } .current-direction-item + .current-direction-item { padding-left: 0; border-top: 1px solid var(--el-border-color-lighter); border-left: none; } .section-heading--inline { align-items: flex-start; flex-direction: column; gap: 7px; } .section-heading--inline > p { max-width: none; text-align: left; } .main-content-grid { gap: 12px; } .attention-section { order: 6; } .source-list em { max-width: 190px; } .drilldown-hint { opacity: 1; } }
</style>
