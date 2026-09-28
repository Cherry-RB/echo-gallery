<script setup lang="ts">
import type { CardDto } from '../../types/card'

defineProps<{
  card: CardDto | null
  emptyText?: string
}>()
</script>

<template>
  <div class="card-picker-selection-summary" :class="{ empty: !card }">
    <span>已選擇</span>
    <strong v-if="card">{{ card.title }}</strong>
    <p v-else>{{ emptyText ?? '請先從左側選擇一張卡片。' }}</p>
    <small v-if="card">#{{ card.id }} · {{ card.type === 'link' ? '連結' : '筆記' }}</small>
  </div>
</template>

<style scoped>
.card-picker-selection-summary {
  display: grid;
  min-width: 0;
  gap: var(--space-2xs);
  padding: var(--space-sm);
  border: 1px solid var(--el-color-primary-light-7);
  border-radius: var(--radius-md);
  background: var(--el-color-primary-light-9);
}

.card-picker-selection-summary.empty {
  border-color: var(--el-border-color-lighter);
  background: var(--el-fill-color-extra-light);
}

.card-picker-selection-summary > span,
.card-picker-selection-summary > small {
  color: var(--el-text-color-secondary);
  font-size: var(--type-meta);
  line-height: var(--leading-metadata);
}

.card-picker-selection-summary > strong,
.card-picker-selection-summary > p {
  min-width: 0;
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: var(--type-secondary);
  font-weight: var(--weight-semibold);
  line-height: var(--leading-ui);
  overflow-wrap: anywhere;
}

.card-picker-selection-summary.empty > p {
  color: var(--el-text-color-placeholder);
  font-weight: var(--weight-regular);
}
</style>
