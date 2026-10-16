<script setup lang="ts">
import AppDialog from '../AppDialog.vue'
defineProps<{ modelValue: boolean; title: string; description: string }>()
const emit = defineEmits<{ 'update:modelValue': [boolean] }>()
</script>
<template>
  <AppDialog :model-value="modelValue" :title="title" width="min(760px, calc(100vw - 32px))" scroll-body @update:model-value="emit('update:modelValue', $event)">
    <header class="history-toolbar"><p class="app-dialog-intro">{{ description }}</p><div v-if="$slots.actions" class="history-actions"><slot name="actions" /></div></header>
    <slot />
    <template #footer><slot name="footer"><el-button @click="emit('update:modelValue', false)">關閉</el-button></slot></template>
  </AppDialog>
</template>
<style scoped>
.history-toolbar { display: flex; align-items: flex-start; justify-content: space-between; flex-wrap: wrap; gap: var(--space-sm); margin-bottom: var(--space-md); }
.history-toolbar p { flex: 1 1 240px; margin: 0; }
.history-actions { display: flex; align-items: center; flex: 0 0 auto; }
</style>
