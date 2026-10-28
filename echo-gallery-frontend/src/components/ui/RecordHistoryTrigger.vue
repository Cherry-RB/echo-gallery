<script setup lang="ts">
import RecordCount from './RecordCount.vue'
defineProps<{ label: string; count?: number | null; countOnly?: boolean }>()
const emit = defineEmits<{ click: [event: MouseEvent] }>()
</script>
<template>
  <button type="button" class="record-history-trigger" :aria-label="count != null ? `查看 ${count} 筆${label}` : `查看${label}`" @click="emit('click', $event)"><span v-if="!countOnly || count == null">{{ label }}</span><RecordCount v-if="count != null" :count="count" /></button>
</template>
<style scoped>
/* 沿用實驗場探索紀錄入口的互動尺寸與排版。 */
.record-history-trigger { display: inline-flex; align-items: center; gap: var(--space-xs); padding: var(--space-2xs) 0; border: 0; background: transparent; color: var(--el-color-primary); cursor: pointer; font: inherit; font-size: var(--type-caption); line-height: var(--leading-ui); white-space: nowrap; }
.record-history-trigger:hover, .record-history-trigger:focus-visible { color: var(--el-color-primary-light-3); }
.record-history-trigger:hover :deep(.record-count), .record-history-trigger:focus-visible :deep(.record-count) { border-color: var(--el-color-primary); background: var(--el-color-primary-light-8); }
.record-history-trigger:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 2px; }
</style>
