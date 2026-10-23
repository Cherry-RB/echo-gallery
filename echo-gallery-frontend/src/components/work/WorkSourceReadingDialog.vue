<script setup lang="ts">
import SourceReadingDialog from '../SourceReadingDialog.vue'
import type { WorkSource } from '../../types/work'

withDefaults(defineProps<{
  modelValue: boolean
  source: WorkSource
  closeLabel?: string
}>(), { closeLabel: '關閉' })

const emit = defineEmits<{ 'update:modelValue': [boolean] }>()
</script>

<template>
  <SourceReadingDialog :model-value="modelValue" :source="source" :close-label="closeLabel" @update:model-value="emit('update:modelValue', $event)" />
</template>
