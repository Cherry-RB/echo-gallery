<script setup lang="ts">
defineProps<{
  title: string
  description?: string
}>()
</script>

<template>
  <header class="app-page-header">
    <div class="app-page-heading">
      <h1 class="app-page-title">{{ title }}</h1>
      <p v-if="description" class="app-page-description">{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="app-page-actions">
      <slot name="actions" />
    </div>
  </header>
</template>
