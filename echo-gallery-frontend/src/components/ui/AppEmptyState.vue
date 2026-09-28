<script setup lang="ts">
defineProps<{
  description: string
}>()
</script>

<template>
  <div class="app-empty-state">
    <el-empty :description="description">
      <slot />
    </el-empty>
  </div>
</template>
