<script setup lang="ts">
import CoverImage from './CoverImage.vue'
defineProps<{ modelValue?: string | null; disabled?: boolean }>()
defineEmits<{ 'update:modelValue': [value: string] }>()
</script>
<template>
  <div class="cover-image-field">
    <el-input :model-value="modelValue ?? ''" :disabled="disabled" maxlength="2048" placeholder="https://…（留空則不顯示封面）" clearable aria-label="封面圖片網址" @update:model-value="$emit('update:modelValue', $event)" />
    <CoverImage :src="modelValue" alt="封面圖片預覽" variant="preview" />
  </div>
</template>
<style scoped>
.cover-image-field { width: 100%; min-width: 0; }
</style>
