<script setup lang="ts">
defineProps<{ title: string; eyebrow: string }>()
</script>
<template>
  <header class="detail-heading">
    <span class="detail-eyebrow">{{ eyebrow }}</span><h1>{{ title }}</h1>
    <div v-if="$slots.metadata" class="detail-heading-metadata"><slot name="metadata" /></div>
  </header>
</template>
<style scoped>
.detail-heading { padding-top: var(--space-md); }
.detail-eyebrow { display: block; color: var(--el-text-color-placeholder); font-size: var(--type-meta); font-weight: var(--weight-semibold); letter-spacing: 0.08em; }
h1 { margin: var(--space-xs) 0 0; color: var(--el-text-color-primary); font-size: var(--type-detail-title); font-weight: var(--weight-semibold); line-height: var(--leading-title); overflow-wrap: anywhere; }
.detail-heading-metadata { display: flex; align-items: center; flex-wrap: wrap; gap: var(--space-sm); margin-top: var(--space-sm); color: var(--el-text-color-secondary); font-size: var(--type-secondary); }
</style>
