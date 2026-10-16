<script setup lang="ts">
import { computed } from 'vue'
import type { TreasureReference } from '../../types/treasure'
import SelectionSummary from '../ui/SelectionSummary.vue'
const props = defineProps<{ card: TreasureReference | null; experiment: TreasureReference | null; treasures: TreasureReference[]; disabled?: boolean }>()
const emit = defineEmits<{ remove: [kind: 'card' | 'experiment' | 'treasure', id: number] }>()
const groups = computed(() => [
  { kind: 'card' as const, title: '來源卡片', limit: 1, entries: props.card ? [props.card] : [] },
  { kind: 'experiment' as const, title: '來源實驗', limit: 1, entries: props.experiment ? [props.experiment] : [] },
  { kind: 'treasure' as const, title: '來源奇珍', limit: 20, entries: props.treasures },
].filter(group => group.entries.length))
</script>
<template>
  <div class="source-summaries">
    <section v-for="group in groups" :key="group.kind" class="source-group" :class="{ 'treasure-sources': group.kind === 'treasure' }" :aria-label="group.title">
      <h4>{{ group.title }} <span class="source-count" :aria-label="'已選 ' + group.entries.length + ' 筆'">{{ group.entries.length }}</span></h4>
      <ul><li v-for="item in group.entries" :key="item.id">
        <SelectionSummary :title="item.title" :metadata="'#' + item.id" hide-label>
          <template #actions><el-button text size="small" :disabled="disabled" :aria-label="'移除' + group.title + '：' + item.title" @click="emit('remove', group.kind, item.id)">移除關聯</el-button></template>
        </SelectionSummary>
      </li></ul>
    </section>
    <p v-if="!card && !experiment && !treasures.length" class="app-field-help">尚未關聯來源。</p>
  </div>
</template>
<style scoped>
.source-summaries { display: grid; gap: var(--space-md); width: 100%; min-width: 0; }
.source-group h4 { display: flex; align-items: center; flex-wrap: wrap; gap: var(--space-xs); margin: 0 0 var(--space-xs); font-size: var(--type-ui); font-weight: var(--weight-medium); }
.source-count { display: inline-flex; align-items: center; justify-content: center; padding: 0 var(--space-xs); border-radius: var(--radius-sm); background: var(--surface-subtle); color: var(--el-text-color-secondary); font-size: var(--type-meta); line-height: var(--leading-ui); font-variant-numeric: tabular-nums; }
ul { display: grid; gap: var(--space-xs); list-style: none; margin: 0; padding: 0; }
li { min-width: 0; }
</style>
