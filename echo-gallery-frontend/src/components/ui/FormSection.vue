<script setup lang="ts">
defineProps<{
  title?: string
  description?: string
}>()
</script>

<template>
  <section class="app-form-section">
    <header v-if="title || description">
      <h2 v-if="title" class="app-form-section__title">{{ title }}</h2>
      <p v-if="description" class="app-section-description">{{ description }}</p>
    </header>
    <slot />
  </section>
</template>
