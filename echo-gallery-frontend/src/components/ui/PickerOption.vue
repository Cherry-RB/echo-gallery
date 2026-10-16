<script setup lang="ts">
defineProps<{ selected: boolean; title: string }>()
defineEmits<{ select: [] }>()
</script>
<template>
  <button type="button" class="picker-option" :class="{ selected }" :aria-pressed="selected" @click="$emit('select')">
    <span class="picker-option-content"><strong>{{ title }}</strong><span class="picker-metadata"><slot /></span></span>
    <span class="selection-label"><span v-if="selected" aria-hidden="true">✓ </span>{{ selected ? '已選擇' : '選擇' }}</span>
  </button>
</template>
<style scoped>
.picker-option { display: flex; width: 100%; align-items: center; justify-content: space-between; gap: var(--space-md); padding: var(--space-sm) var(--space-xs); border: 0; border-bottom: 1px solid var(--el-border-color-lighter); background: transparent; color: inherit; font: inherit; cursor: pointer; text-align: left; }
.picker-option:hover { background: var(--surface-subtle); }
.picker-option.selected { background: var(--el-color-primary-light-9); }
.picker-option.selected { box-shadow: inset 3px 0 var(--el-color-primary); }
.picker-option:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: -2px; }
.picker-option-content { display: block; min-width: 0; }
.picker-option-content strong { display: block; line-height: var(--leading-ui); overflow-wrap: anywhere; }
.picker-metadata { display: flex; align-items: center; flex-wrap: wrap; gap: var(--space-xs); margin-top: var(--space-2xs); color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.selection-label { flex: 0 0 auto; color: var(--el-color-primary); font-size: var(--type-ui); }
</style>
