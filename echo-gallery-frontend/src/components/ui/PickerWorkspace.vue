<script setup lang="ts">
withDefaults(defineProps<{ settingsStyle?: 'surface' | 'plain' }>(), { settingsStyle: 'plain' })
</script>
<template>
  <div class="picker-workspace">
    <section class="picker-browser"><slot /></section>
    <aside :class="['picker-settings', `picker-settings--${settingsStyle}`]"><slot name="selection" /></aside>
  </div>
</template>
<style scoped>
.picker-workspace { display: grid; max-height: min(68vh, 650px); grid-template-columns: minmax(0, 1.5fr) minmax(280px, 0.8fr); gap: var(--space-lg); align-items: start; }
.picker-browser { display: flex; min-width: 0; min-height: 0; flex-direction: column; }
.picker-settings { align-self: start; min-width: 0; max-height: min(68vh, 650px); overflow-y: auto; box-sizing: border-box; padding: var(--space-md); border: 1px solid var(--el-border-color-lighter); border-radius: var(--radius-md); background: var(--surface-subtle); }
.picker-settings--plain { padding: var(--space-2xs) 0 0 var(--space-lg); border: 0; border-left: 1px solid var(--el-border-color-lighter); border-radius: 0; background: transparent; }
.picker-settings :deep(h3) { margin: 0 0 var(--space-sm); font-size: var(--type-card-title); }
.picker-browser :deep(.picker-results) { min-height: 0; margin-top: var(--space-sm); overflow-y: auto; border-top: 1px solid var(--el-border-color-lighter); }
.picker-browser { max-height: min(68vh, 650px); }
.picker-browser :deep(.el-tabs), .picker-browser :deep(.el-input), .picker-browser :deep(.source-rule) { flex-shrink: 0; }
@media (max-width: 760px) {
  .picker-workspace { height: auto; min-height: 0; grid-template-columns: minmax(0, 1fr); }
  .picker-workspace, .picker-browser { max-height: none; }
  .picker-browser :deep(.picker-results) { overflow: visible; }
  .picker-settings { max-height: none; overflow: visible; }
  .picker-settings--plain { padding: var(--space-md) 0 0; border-top: 1px solid var(--el-border-color-lighter); border-left: 0; }
}
</style>
