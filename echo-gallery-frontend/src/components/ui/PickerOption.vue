<script setup lang="ts">
defineOptions({ inheritAttrs: false })
defineProps<{ selected: boolean; title: string; disabled?: boolean; multiple?: boolean }>()
defineEmits<{ select: [] }>()
</script>
<template>
  <div v-if="$slots.actions" class="picker-option-row" :class="{ selected }">
  <button v-bind="$attrs" type="button" class="picker-option in-row" :disabled="disabled" :class="{ selected }" :aria-pressed="selected" @click="$emit('select')">
    <span class="picker-option-content"><strong>{{ title }}</strong><span class="picker-metadata"><slot /></span></span>
    <span class="selection-label"><span v-if="selected" aria-hidden="true">✓ </span>{{ selected ? (multiple ? '已勾選' : '已選擇') : (multiple ? '勾選' : '選擇') }}</span>
  </button>
  <div v-if="$slots.actions" class="picker-option-actions"><slot name="actions" /></div>
  </div>
  <button v-else v-bind="$attrs" type="button" class="picker-option" :disabled="disabled" :class="{ selected }" :aria-pressed="selected" @click="$emit('select')">
    <span class="picker-option-content"><strong>{{ title }}</strong><span class="picker-metadata"><slot /></span></span>
    <span class="selection-label"><span v-if="selected" aria-hidden="true">✓ </span>{{ selected ? (multiple ? '已勾選' : '已選擇') : (multiple ? '勾選' : '選擇') }}</span>
  </button>
</template>
<style scoped>
.picker-option-row { display: flex; align-items: center; min-width: 0; border-bottom: 1px solid var(--el-border-color-lighter); }
.picker-option-row.selected { background: var(--el-color-primary-light-9); }
.picker-option { display: flex; width: 100%; min-width: 0; flex: 1; align-items: center; justify-content: space-between; gap: var(--space-md); padding: var(--space-sm) var(--space-xs); border: 0; background: transparent; color: inherit; font: inherit; cursor: pointer; text-align: left; }
.picker-option:disabled { cursor: not-allowed; opacity: 0.6; }
.picker-option:not(.in-row) { border-bottom: 1px solid var(--el-border-color-lighter); }
.picker-option-actions { flex-shrink: 0; padding-inline: var(--space-xs); }
.picker-option:hover { background: var(--surface-subtle); }
.picker-option.selected { background: var(--el-color-primary-light-9); }
.picker-option.selected { box-shadow: inset 3px 0 var(--el-color-primary); }
.picker-option:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: -2px; }
.picker-option-content { display: block; min-width: 0; }
.picker-option-content strong { display: block; line-height: var(--leading-ui); overflow-wrap: anywhere; }
.picker-metadata { display: flex; align-items: center; flex-wrap: wrap; gap: var(--space-xs); margin-top: var(--space-2xs); color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.selection-label { flex: 0 0 auto; color: var(--el-color-primary); font-size: var(--type-ui); }
</style>
