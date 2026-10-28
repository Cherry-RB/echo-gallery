<script setup lang="ts">
defineProps<{ count: number }>()
</script>

<template>
  <span class="record-count">{{ count }}</span>
</template>

<style scoped>
.record-count { display: inline-flex; flex-shrink: 0; align-items: center; justify-content: center; min-width: 22px; min-height: 22px; padding: 0 var(--space-xs); border: 1px solid var(--el-color-primary-light-7); border-radius: var(--radius-sm); background: var(--el-color-primary-light-9); color: var(--el-color-primary); font-size: var(--type-meta); font-weight: var(--weight-semibold); font-variant-numeric: tabular-nums; line-height: 1; white-space: nowrap; }
</style>
