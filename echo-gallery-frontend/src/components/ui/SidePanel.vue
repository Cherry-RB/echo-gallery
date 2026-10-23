<script setup lang="ts">
withDefaults(defineProps<{ as?: 'section' | 'details' | 'div' }>(), { as: 'section' })
</script>

<template><component :is="as" class="side-panel"><slot /></component></template>

<style scoped>
.side-panel { min-width: 0; padding: var(--space-md); border: 1px solid var(--el-border-color-light); border-radius: var(--radius-md); background: var(--el-bg-color); box-shadow: var(--el-box-shadow-lighter); }
.side-panel :deep(h2) { margin: 0; font-size: var(--type-section-title); font-weight: var(--weight-semibold); line-height: var(--leading-section-title); }
.side-panel :deep(.side-panel-heading) { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--space-xs); margin-bottom: var(--space-sm); }
.side-panel :deep(.side-panel-heading .el-button) { margin: 0; }
.side-panel :deep(.side-panel-list) { display: grid; gap: var(--space-xs); }
.side-panel :deep(.side-relation) { box-sizing: border-box; width: 100%; padding: var(--space-sm); border: 1px solid var(--el-border-color-light); border-radius: var(--radius-md); background: var(--surface-subtle); color: var(--el-text-color-primary); text-align: left; font: inherit; cursor: pointer; transition: border-color .2s, background-color .2s; }
.side-panel :deep(.side-relation:hover), .side-panel :deep(.side-relation:focus-visible) { border-color: var(--el-color-primary-light-5); background: var(--el-color-primary-light-9); }
.side-panel :deep(.side-relation:hover strong), .side-panel :deep(.side-relation:focus-visible strong), .side-panel :deep(.side-relation:hover > span:first-child), .side-panel :deep(.side-relation:focus-visible > span:first-child), .side-panel :deep(.side-relation:hover .side-relation-title), .side-panel :deep(.side-relation:focus-visible .side-relation-title) { color: var(--el-color-primary); }
.side-panel :deep(.side-relation:focus-visible) { outline: 2px solid var(--el-color-primary); outline-offset: 2px; }
.side-panel :deep(.side-relation > span:first-child), .side-panel :deep(.side-relation strong) { font-size: var(--type-secondary); font-weight: var(--weight-semibold); line-height: var(--leading-ui); overflow-wrap: anywhere; }
@media (prefers-reduced-motion: reduce) { .side-panel :deep(.side-relation) { transition: none; } }
</style>
