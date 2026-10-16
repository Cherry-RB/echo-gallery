<script setup lang="ts">
defineProps<{ leftLabel: string; rightLabel: string }>()
</script>
<template>
  <div class="history-fields">
    <section :aria-label="leftLabel"><h3>{{ leftLabel }}</h3><slot name="left" /></section>
    <section :aria-label="rightLabel"><h3>{{ rightLabel }}</h3><slot name="right" /></section>
  </div>
</template>
<style scoped>
.history-fields { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); margin-top: var(--space-sm); }
.history-fields section { min-width: 0; padding-right: var(--space-md); overflow-wrap: anywhere; }
.history-fields section + section { padding-right: 0; padding-left: var(--space-md); border-left: 1px solid var(--el-border-color-lighter); }
.history-fields h3 { margin: 0 0 var(--space-2xs); color: var(--el-text-color-secondary); font-size: var(--type-caption); font-weight: var(--weight-medium); line-height: var(--leading-ui); }
.history-fields :deep(p) { margin: 0; font-size: var(--type-ui); line-height: var(--leading-ui); white-space: pre-wrap; overflow-wrap: anywhere; }
@media (max-width: 760px) {
  .history-fields { grid-template-columns: minmax(0, 1fr); }
  .history-fields section { padding-right: 0; }
  .history-fields section + section { margin-top: var(--space-sm); padding-top: var(--space-sm); padding-left: 0; border-top: 1px solid var(--el-border-color-lighter); border-left: 0; }
}
</style>
