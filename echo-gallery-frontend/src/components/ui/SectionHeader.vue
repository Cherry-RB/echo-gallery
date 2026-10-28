<script setup lang="ts">
import RecordCount from './RecordCount.vue'

defineProps<{
  title: string
  count?: number | null
  description?: string
  eyebrow?: string
  id?: string
}>()
</script>

<template>
  <header class="app-section-header">
    <div class="app-section-heading">
      <span v-if="eyebrow" class="app-section-eyebrow">{{ eyebrow }}</span>
      <div class="app-section-title-row"><h2 :id="id" class="app-section-title">{{ title }}</h2><RecordCount v-if="count != null" :count="count" :aria-label="`${title}數量：${count}`" /><slot name="title-extra" /></div>
      <p v-if="description" class="app-section-description">{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="app-section-actions">
      <slot name="actions" />
    </div>
  </header>
</template>
