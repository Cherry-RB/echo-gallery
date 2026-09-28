<script setup lang="ts">
defineProps<{
  title: string
  description?: string
  eyebrow?: string
  id?: string
}>()
</script>

<template>
  <header class="app-section-header">
    <div>
      <span v-if="eyebrow" class="app-section-eyebrow">{{ eyebrow }}</span>
      <h2 :id="id" class="app-section-title">{{ title }}</h2>
      <p v-if="description" class="app-section-description">{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="app-page-actions">
      <slot name="actions" />
    </div>
  </header>
</template>
