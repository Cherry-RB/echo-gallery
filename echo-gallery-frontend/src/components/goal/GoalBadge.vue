<script lang="ts">
import type { GoalSource } from '../../types/goal'
type Waiting = { source: GoalSource; resolve: (count: number) => void; reject: (error: unknown) => void }
const pending = new Map<string, Waiting[]>()
function countFor(owner: string, source: GoalSource): Promise<number> {
  return new Promise((resolve, reject) => {
    const batch = pending.get(owner) ?? []; batch.push({ source, resolve, reject }); pending.set(owner, batch)
    if (batch.length !== 1) return
    queueMicrotask(async () => {
      const all = pending.get(owner) ?? []; pending.delete(owner)
      for (let index = 0; index < all.length; index += 100) {
        const group = all.slice(index, index + 100)
        try { const counts = await goalApi.counts(group.map(item => item.source)); group.forEach(item => item.resolve(counts[`${item.source.kind}:${item.source.id}`] ?? 0)) }
        catch (error) { group.forEach(item => item.reject(error)) }
      }
    })
  })
}
</script>
<script setup lang="ts">
import { computed, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { Flag } from '@element-plus/icons-vue'
import { goalApi } from '../../utils/api/goalApi'
import type { GoalKind } from '../../types/goal'
const props = defineProps<{ kind: GoalKind; id: number }>()
const owner = localStorage.getItem('id') || '', opened = ref(false)
const count = useQuery({ queryKey: computed(() => ['goals', owner, 'count', props.kind, props.id]), queryFn: () => countFor(owner, { kind: props.kind, id: props.id, excluded: false }), staleTime: 30000 })
const details = useQuery({ queryKey: computed(() => ['goals', owner, 'associations', props.kind, props.id]), queryFn: () => goalApi.associations(props.kind, props.id), enabled: opened })
const reasonLabels = (reasons: Array<{ label: string | null; direct: boolean }>) => reasons
  .filter(reason => !reason.direct && reason.label)
  .map(reason => reason.label)
</script>
<template>
  <span v-if="(count.data.value ?? 0) > 0 || count.isError.value" class="goal-badge-anchor" @click.stop @keydown.stop>
    <el-popover v-if="(count.data.value ?? 0) > 0" v-model:visible="opened" trigger="click" popper-class="goal-associations-popover" placement="bottom">
      <template #reference><button type="button" class="goal-badge" :aria-label="`查看 ${count.data.value} 個相關目標`" :title="`查看 ${count.data.value} 個相關目標`"><el-icon><Flag /></el-icon><span>{{ count.data.value }}</span></button></template>
      <p v-if="details.isPending.value">載入中…</p><el-button v-else-if="details.isError.value" @click="details.refetch()">載入失敗，重試</el-button>
      <ul v-else class="goal-associations"><li v-for="goal in details.data.value" :key="goal.goalId"><router-link :to="`/goals/${goal.goalId}`">{{ goal.name }}</router-link><div v-if="reasonLabels(goal.reasons).length" class="goal-association-reasons"><span v-for="reason in reasonLabels(goal.reasons)" :key="reason">{{ reason }}</span></div></li></ul>
    </el-popover>
    <button v-else-if="count.isError.value" class="goal-badge" type="button" title="目標關聯載入失敗，點擊重試" aria-label="目標關聯載入失敗，重試" @click="count.refetch()"><el-icon><Flag /></el-icon>?</button>
  </span>
</template>
<style scoped>
.goal-badge-anchor { display: inline-flex; align-items: center; vertical-align: middle; }
.goal-badge { display: inline-flex; align-items: center; gap: var(--space-2xs); border: 1px solid var(--goal-border); border-radius: var(--radius-sm); padding: 2px 6px; color: var(--goal-color); background: var(--goal-bg); cursor: pointer; font-size: var(--type-metadata); font-weight: var(--weight-medium); line-height: var(--leading-metadata); font-variant-numeric: tabular-nums; }
.goal-badge:hover { border-color: var(--goal-color); }
.goal-badge:focus-visible { outline: 2px solid var(--goal-color); outline-offset: 2px; }
.goal-associations { display: grid; gap: var(--space-xs); list-style: none; padding: 0; margin: 0; max-height: 320px; overflow: auto; }
.goal-associations li { display: grid; gap: var(--space-2xs); padding-bottom: var(--space-xs); border-bottom: 1px solid var(--el-border-color-lighter); }
.goal-associations li:last-child { padding-bottom: 0; border-bottom: 0; }
.goal-associations a { color: var(--el-color-primary); font-size: var(--type-secondary); overflow-wrap: anywhere; text-decoration: none; }
.goal-association-reasons { display: grid; gap: var(--space-2xs); }
.goal-association-reasons span { color: var(--el-text-color-secondary); font-size: var(--type-metadata); line-height: var(--leading-metadata); overflow-wrap: anywhere; }
.goal-associations a:hover { text-decoration: underline; }
:global(.goal-associations-popover.el-popper) { width: max-content; min-width: 180px; max-width: min(320px, calc(100vw - 32px)); }
</style>
