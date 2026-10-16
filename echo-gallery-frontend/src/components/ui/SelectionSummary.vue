<script setup lang="ts">
defineProps<{ title?: string | null; metadata?: string; label?: string; emptyText?: string; hideLabel?: boolean }>()
</script>
<template>
  <div class="selection-summary" :class="{ empty: !title, 'with-actions': !!$slots.actions, 'without-label': hideLabel }">
    <span v-if="!hideLabel" class="summary-label">{{ title ? label ?? '已選擇' : '尚未選擇' }}</span>
    <strong v-if="title">{{ title }}</strong><p v-else>{{ emptyText ?? '請先選擇內容。' }}</p>
    <small v-if="title && metadata">{{ metadata }}</small>
    <div v-if="$slots.actions" class="summary-actions"><slot name="actions" /></div>
  </div>
</template>
<style scoped>
.selection-summary { display: grid; min-width: 0; width: 100%; box-sizing: border-box; gap: var(--space-2xs); padding: var(--space-sm); border: 1px solid var(--el-color-primary-light-7); border-radius: var(--radius-md); background: var(--el-color-primary-light-9); }
.selection-summary.empty { border-color: var(--el-border-color-lighter); background: var(--surface-subtle); }
.summary-label, small { color: var(--el-text-color-secondary); font-size: var(--type-meta); line-height: var(--leading-metadata); }
strong, p { min-width: 0; margin: 0; color: var(--el-text-color-primary); font-size: var(--type-secondary); font-weight: var(--weight-semibold); line-height: var(--leading-ui); overflow-wrap: anywhere; }
.empty p { color: var(--el-text-color-secondary); font-weight: var(--weight-regular); }
.with-actions { grid-template-columns: minmax(0, 1fr) auto; column-gap: var(--space-sm); }
.with-actions > .summary-label, .with-actions > strong, .with-actions > small, .with-actions > p { grid-column: 1; }
.summary-actions { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: var(--space-xs); }
.with-actions .summary-actions { grid-column: 2; grid-row: 1 / span 3; align-self: center; }
.without-label.with-actions .summary-actions { grid-row: 1 / span 2; }
.summary-actions :deep(.el-button) { margin-left: 0; }
</style>
