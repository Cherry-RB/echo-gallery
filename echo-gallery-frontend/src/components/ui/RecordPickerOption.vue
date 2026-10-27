<script setup lang="ts">
import PickerOption from './PickerOption.vue'
defineProps<{ title: string; selected: boolean; disabled?: boolean; tryText?: string | null; discovery?: string | null }>()
defineEmits<{ select: [] }>()
</script>

<template>
  <PickerOption :title="title" :selected="selected" :disabled="disabled" multiple @select="$emit('select')">
    <span class="record-picker-fields"><span v-if="tryText"><small>行動</small>{{ tryText }}</span><span v-if="discovery"><small>發現</small>{{ discovery }}</span><slot /></span>
  </PickerOption>
</template>

<style scoped>
.record-picker-fields { display: grid; width: 100%; gap: var(--space-xs); color: var(--el-text-color-regular); font-size: var(--type-secondary); line-height: var(--leading-ui); white-space: pre-wrap; overflow-wrap: anywhere; }
.record-picker-fields small { display: block; color: var(--el-text-color-secondary); font-size: var(--type-meta); }
</style>
