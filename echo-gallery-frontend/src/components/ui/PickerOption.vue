<script setup lang="ts">
defineOptions({ inheritAttrs: false })
defineProps<{ selected: boolean; title: string; disabled?: boolean; multiple?: boolean }>()
defineEmits<{ select: [] }>()
</script>
<template>
  <div v-if="$slots.actions" class="picker-option-row" :class="{ selected }">
  <button v-bind="$attrs" type="button" class="picker-option in-row" :disabled="disabled" :class="{ selected }" :aria-pressed="selected" @click="$emit('select')">
    <span class="selection-control" :class="{ multiple }" aria-hidden="true" />
    <span class="picker-option-content"><strong>{{ title }}</strong><span class="picker-metadata"><slot /></span></span>
    <span class="selection-label"><span v-if="selected" aria-hidden="true">✓ </span>{{ selected ? (multiple ? '已勾選' : '已選擇') : (multiple ? '勾選' : '選擇') }}</span>
  </button>
  <div v-if="$slots.actions" class="picker-option-actions"><slot name="actions" /></div>
  </div>
  <button v-else v-bind="$attrs" type="button" class="picker-option" :disabled="disabled" :class="{ selected }" :aria-pressed="selected" @click="$emit('select')">
    <span class="selection-control" :class="{ multiple }" aria-hidden="true" />
    <span class="picker-option-content"><strong>{{ title }}</strong><span class="picker-metadata"><slot /></span></span>
    <span class="selection-label"><span v-if="selected" aria-hidden="true">✓ </span>{{ selected ? (multiple ? '已勾選' : '已選擇') : (multiple ? '勾選' : '選擇') }}</span>
  </button>
</template>
<style scoped>
.picker-option-row { display: flex; align-items: center; min-width: 0; border: 1px solid var(--el-border-color-light); border-radius: var(--radius-md); }
.picker-option-row.selected { border-color: var(--el-color-primary-light-7); background: var(--el-color-primary-light-9); }
.picker-option { position: relative; display: flex; box-sizing: border-box; width: 100%; min-width: 0; flex: 1; align-items: center; gap: var(--space-sm); padding: var(--space-sm); border: 1px solid var(--el-border-color-light); border-radius: var(--radius-md); background: transparent; color: inherit; font: inherit; cursor: pointer; text-align: left; }
.picker-option:disabled { cursor: not-allowed; opacity: 0.6; }
.picker-option.in-row { border: 0; }
.picker-option-actions { flex-shrink: 0; padding-inline: var(--space-xs); }
.picker-option:hover { background: var(--surface-subtle); }
.picker-option.selected { background: var(--el-color-primary-light-9); }
.picker-option.selected { border-color: var(--el-color-primary-light-7); }
.picker-option:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: -2px; }
.picker-option-content { display: block; min-width: 0; flex: 1; }
.picker-option-content strong { display: block; line-height: var(--leading-ui); overflow-wrap: anywhere; }
.picker-metadata { display: flex; align-items: center; flex-wrap: wrap; gap: var(--space-xs); margin-top: var(--space-2xs); color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.selection-label { position: absolute; width: 1px; height: 1px; overflow: hidden; clip-path: inset(50%); white-space: nowrap; }
.selection-control { display: grid; place-items: center; flex: 0 0 14px; width: 14px; height: 14px; box-sizing: border-box; border: 1px solid var(--el-border-color); border-radius: 50%; background: var(--el-bg-color); }
.selection-control.multiple { border-radius: 3px; }
.selected .selection-control { background: var(--el-color-primary); border-color: var(--el-color-primary); }
.selected .selection-control::after { content: ''; width: 4px; height: 4px; border-radius: 50%; background: var(--el-color-white); }
.selected .selection-control.multiple::after { content: '✓'; width: auto; height: auto; border-radius: 0; background: transparent; color: var(--el-color-white); font-size: 11px; line-height: 1; }
</style>
