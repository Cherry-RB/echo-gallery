<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { imageUrlError } from '../../utils/imageUrl'

const props = withDefaults(defineProps<{ src?: string | null; alt?: string; variant?: 'collection' | 'reading' | 'preview'; lazy?: boolean }>(), { alt: '', variant: 'reading', lazy: false })
const source = computed(() => props.src?.trim() ?? '')
const formatError = computed(() => imageUrlError(source.value))
const state = ref<'loading' | 'loaded' | 'error'>('loading')
const element = ref<HTMLImageElement>()
watch(source, () => { state.value = 'loading' }, { flush: 'sync' })
const settle = (event: Event, value: 'loaded' | 'error') => {
  if (event.target === element.value) state.value = value
}
</script>
<template>
  <div v-if="source && (variant === 'preview' || (!formatError && state !== 'error'))" class="cover-media" :class="['cover-media--' + variant, { 'is-loading': state === 'loading' && !formatError }]">
    <p v-if="formatError" class="cover-feedback cover-feedback--error" role="alert">{{ formatError }}</p>
    <p v-else-if="state === 'error'" class="cover-feedback" role="status">無法載入圖片，請確認網址是否可直接開啟圖片。仍可保留網址並儲存其他內容。</p>
    <template v-else>
      <img ref="element" :key="source" :src="source" :alt="alt" :loading="lazy ? 'lazy' : 'eager'" referrerpolicy="no-referrer" :class="{ 'is-pending': state !== 'loaded' }" @load="settle($event, 'loaded')" @error="settle($event, 'error')" />
      <p v-if="variant === 'preview' && state === 'loading'" class="cover-feedback" role="status">正在載入圖片預覽…</p>
    </template>
  </div>
</template>
<style scoped>
.cover-media { min-width: 0; overflow: hidden; border-radius: var(--radius-sm); }
.cover-media img { display: block; width: 100%; object-fit: contain; }
.cover-media img.is-pending { visibility: hidden; }
.cover-media--collection { aspect-ratio: 16 / 9; }
.cover-media--collection img { height: 100%; object-fit: cover; }
.cover-media--reading img { height: auto; }
.cover-media--preview { width: 100%; margin-top: var(--space-sm); padding: var(--space-sm); box-sizing: border-box; background: var(--surface-subtle); border: 1px solid var(--el-border-color-lighter); }
.cover-media--preview img { max-height: 180px; }
.cover-media--preview img.is-pending { height: 0; }
.cover-feedback { margin: 0; color: var(--el-text-color-secondary); font-size: var(--type-meta); line-height: var(--leading-ui); overflow-wrap: anywhere; }
.cover-feedback--error { color: var(--el-color-danger); }
</style>
