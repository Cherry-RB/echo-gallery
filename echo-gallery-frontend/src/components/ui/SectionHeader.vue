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
    <div class="app-section-heading">
      <span v-if="eyebrow" class="app-section-eyebrow">{{ eyebrow }}</span>
      <div class="app-section-title-row"><h2 :id="id" class="app-section-title">{{ title }}</h2><slot name="title-extra" /></div>
      <p v-if="description" class="app-section-description">{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="app-section-actions">
      <slot name="actions" />
    </div>
  </header>
</template>
