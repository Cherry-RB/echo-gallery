<script setup lang="ts">
import RecordCount from './RecordCount.vue'
defineProps<{ label: string; count: number; countOnly?: boolean }>()
const emit = defineEmits<{ click: [] }>()
</script>
<template>
  <button type="button" class="record-history-trigger" :aria-label="`查看 ${count} 筆${label}`" @click="emit('click')"><span v-if="!countOnly">{{ label }}</span><RecordCount :count="count" /></button>
</template>
<style scoped>
/* 沿用實驗場探索紀錄入口的互動尺寸與排版。 */
.record-history-trigger { display: inline-flex; align-items: center; gap: var(--space-xs); padding: var(--space-2xs) 0; border: 0; background: transparent; color: var(--el-color-primary); cursor: pointer; font: inherit; font-size: var(--type-caption); line-height: var(--leading-ui); white-space: nowrap; }
.record-history-trigger:hover, .record-history-trigger:focus-visible { color: var(--el-color-primary-light-3); }
.record-history-trigger:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 2px; }
</style>
