<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElCollapseTransition } from 'element-plus'
import type { TreasureRank, TreasureTrial } from '../../types/treasure'
import { challengeRanks, ranks, nextRankOf, challengeState } from '../../utils/treasure'
import ExpandableText from '../ExpandableText.vue'
import TreasureAttemptHistory from './TreasureAttemptHistory.vue'
import TreasureRankBadge from './TreasureRankBadge.vue'
const props = defineProps<{ treasureId: number; rank: TreasureRank; trials: TreasureTrial[]; modelValue: TreasureRank; archived?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [TreasureRank]; edit: [TreasureRank]; attempt: [TreasureTrial]; help: [] }>()
const expanded = ref<TreasureRank | ''>(props.modelValue)
watch(() => props.modelValue, value => { expanded.value = value })
watch(() => props.treasureId, () => { expanded.value = props.modelValue })
const trialFor = (stage: TreasureRank) => props.trials.find(t => t.targetRank === stage)
const acquired = (stage: TreasureRank) => Object.keys(ranks).indexOf(stage) <= Object.keys(ranks).indexOf(props.rank)
const stageState = (stage: TreasureRank) => {
  const trial = trialFor(stage)
  return trial ? challengeState(trial, props.rank) : acquired(stage) ? '已取得' : '未設定'
}
const canAttempt = (stage: TreasureRank) => stage === nextRankOf(props.rank) && !!trialFor(stage) && trialFor(stage)?.status !== 'PASSED'
const canEdit = (stage: TreasureRank) => !props.archived && !acquired(stage) && trialFor(stage)?.status !== 'PASSED'
const selectStage = (stage: TreasureRank) => {
  expanded.value = expanded.value === stage ? '' : stage
  if (expanded.value) emit('update:modelValue', stage)
}
const regionId = (stage: TreasureRank) => `challenge-${props.treasureId}-${stage}`
</script>
<template>
  <section class="challenge-route" aria-label="成長挑戰">
    <header class="route-heading"><h2>成長挑戰</h2><el-button text @click="emit('help')">了解四階成長</el-button></header>
    <p v-if="rank === 'GOLD'" class="route-help">金級並非故事的終點。這件奇珍仍可能產生新的價值。</p>
    <div class="stages">
      <div class="stage stone-stage" data-rank="STONE" :class="{ 'is-current': rank === 'STONE' }">
        <div class="stage-heading"><TreasureRankBadge rank="STONE" /><span class="stage-state">已取得</span><strong v-if="rank === 'STONE'" class="current-label">目前等級</strong></div>
        <p class="stone-note">完成成果並入藏</p>
      </div>
      <section v-for="stage in challengeRanks" :key="stage" class="stage" :data-rank="stage" :class="{ 'is-current': rank === stage, 'is-viewed': expanded === stage }" :aria-label="ranks[stage] + '試煉'">
        <header class="stage-summary">
          <div class="stage-heading"><TreasureRankBadge :rank="stage" /><span class="stage-state">{{ stageState(stage) }}</span><strong v-if="rank === stage" class="current-label">目前等級</strong></div>
          <div class="stage-actions" role="group" :aria-label="ranks[stage] + '試煉操作'">
            <button :id="regionId(stage) + '-toggle'" type="button" class="stage-view" :aria-expanded="expanded === stage" :aria-controls="regionId(stage)" :aria-label="(expanded === stage ? '收合' : '查看') + ranks[stage] + '試煉'" @click="selectStage(stage)">{{ expanded === stage ? '收合' : '查看' }}</button>
            <el-button v-if="canEdit(stage)" text @click="emit('edit', stage)">設定</el-button>
            <el-button v-if="!archived && canAttempt(stage)" type="primary" @click="emit('attempt', trialFor(stage)!)">＋記錄</el-button>
          </div>
          <h3 v-if="trialFor(stage)" class="challenge-name">{{ trialFor(stage)?.title }}</h3>
        </header>
        <el-collapse-transition>
        <div v-show="expanded === stage" :id="regionId(stage)" role="region" :aria-labelledby="regionId(stage) + '-toggle'">
        <div v-if="expanded === stage" class="stage-content">
          <template v-if="trialFor(stage)">
            <p v-if="stage === nextRankOf(rank) && trialFor(stage)!.status === 'ATTEMPTED'" class="route-help">試煉已經展開，結果仍未揭曉。</p>
            <p v-else-if="stage === nextRankOf(rank) && trialFor(stage)!.status === 'NOT_PASSED'" class="route-help">這次試煉未能晉級，但經歷已經留下。</p>
            <span class="criteria-label">通過條件</span><ExpandableText :content="trialFor(stage)!.criteria" :lines="4" />
            <p v-if="!canAttempt(stage) && trialFor(stage)!.status !== 'PASSED' && !acquired(stage)" class="route-help">這份規劃已保存。完成前一階試煉後，就能記錄這一階的嘗試。</p>
            <TreasureAttemptHistory :treasure-id="treasureId" :trial-id="trialFor(stage)!.id" compact :recordable="!archived && canAttempt(stage)" @record="emit('attempt', trialFor(stage)!)" />
          </template>
          <template v-else>
            <p class="route-help">{{ acquired(stage) ? '此階沒有可顯示的試煉紀錄。' : stage === nextRankOf(rank) && !archived ? '這件奇珍尚未接受新的試煉。是否繼續前行，由你決定。' : '尚未設定這一階的試煉。' }}</p>
            <p v-if="!acquired(stage) && !archived" class="route-help">{{ stage === nextRankOf(rank) ? '使用上方「設定」訂下行動與通過條件，再透過「＋記錄」留下每次嘗試。' : '使用上方「設定」先保存規劃，通過前一階後再記錄嘗試。' }}</p>
          </template>
          <p v-if="archived" class="route-help">已封存；還原後可編輯與記錄嘗試。</p>
        </div>
        </div>
        </el-collapse-transition>
      </section>
    </div>
  </section>
</template>
<style scoped>
.route-heading, .stage-heading { display: flex; align-items: center; gap: var(--space-xs); flex-wrap: wrap; }
.route-heading { justify-content: space-between; }
.route-heading h2 { margin: 0; font-size: var(--type-section-title); }
.stages { list-style: none; padding: 0; margin: var(--space-md) 0 0; display: flex; flex-direction: column; gap: var(--space-xs); }
.stage { min-width: 0; border-radius: var(--radius-md); border: 1px solid transparent; border-bottom-color: var(--el-border-color-lighter); }
.is-viewed { border-color: var(--rank-border); }
/* 目前等級使用比徽章更淺的背景；查看狀態只增加識別框線。 */
.is-current { background: var(--rank-surface); }
.stone-stage { padding: var(--space-sm); }
.stage-summary { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-xs) var(--space-sm); padding: var(--space-sm); }
.stage-heading { min-width: 0; }
.stage-actions { display: flex; align-items: center; flex-wrap: wrap; gap: var(--space-xs); margin-left: auto; }
.stage-actions :deep(.el-button) { margin-left: 0; }
.stage-view { border: 0; border-radius: var(--radius-sm); padding: var(--space-xs); background: transparent; color: var(--el-color-primary); font: inherit; font-size: var(--type-ui); cursor: pointer; }
.stage-view:hover { background: var(--surface-subtle); }
.stage-view:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 2px; }

.stage-state, .stone-note { color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.criteria-label { color: var(--el-color-primary); font-size: var(--type-ui); font-weight: var(--weight-medium); }
.current-label { color: var(--rank-text); font-size: var(--type-meta); }
.challenge-name { flex-basis: 100%; min-width: 0; margin: 0; font-size: var(--type-card-title); font-weight: var(--weight-semibold); line-height: var(--leading-section); overflow-wrap: anywhere; }
.stage-content { padding: 0 var(--space-sm) var(--space-sm); overflow-wrap: anywhere; }
.stone-note { margin: var(--space-xs) 0 0; }
.route-help { margin: 0 0 var(--space-sm); color: var(--el-text-color-secondary); font-size: var(--type-secondary); line-height: var(--leading-ui); }
@media (max-width: 760px) {
  .stage-heading { flex: 1 1 auto; }
  .stage-actions { flex: 0 0 auto; justify-content: flex-end; }
}
</style>
