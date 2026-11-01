<script setup lang="ts">
import { computed, ref } from 'vue'
import { goalMetrics } from '../../types/goal'
import type { GoalSource } from '../../types/goal'
import { hasGoalCards, metricDescriptions } from '../../utils/goalPresentation'
const props = defineProps<{ modelValue: string[]; sources: GoalSource[] }>()
const emit = defineEmits<{ 'update:modelValue': [string[]] }>()
const expanded = ref(false)
const selected = computed(() => props.modelValue.includes('M01'))
const savedPreferences = computed(() => props.modelValue.filter(key => key !== 'M01'))
const canSuggest = computed(() => hasGoalCards({ sources: props.sources }))
const summary = computed(() => props.modelValue.length
  ? `已選 ${props.modelValue.length} 項：${props.modelValue.map(key => goalMetrics[key] || key).join('、')}`
  : '尚未設定要顯示的數據。')
function toggle(value: boolean) {
  emit('update:modelValue', value ? [...new Set([...props.modelValue, 'M01'])] : props.modelValue.filter(key => key !== 'M01'))
}
</script>
<template>
  <section class="metric-settings" aria-label="顯示數據">
    <div class="metric-heading"><h3>顯示數據（選填）</h3><el-button type="primary" link size="small" @click="expanded = !expanded">{{ expanded ? '收合' : '設定' }}</el-button></div>
    <p class="metric-summary">{{ summary }}</p>
    <div v-if="expanded" class="metric-options">
      <div class="metric-option"><el-checkbox :model-value="selected" @update:model-value="toggle(Boolean($event))">{{ goalMetrics.M01 }}</el-checkbox><p>{{ metricDescriptions.M01 }}</p></div>
      <el-button v-if="canSuggest && !selected" type="primary" link size="small" @click="toggle(true)">採用建議</el-button>
      <p v-if="!canSuggest" class="metric-help">選擇卡片相關來源後，才能顯示目前相關卡片。</p>
      <details v-if="savedPreferences.length" class="saved-preferences">
        <summary>保留的舊版偏好 · {{ savedPreferences.length }} 項</summary>
        <p class="metric-help">這些設定目前尚未開放編輯，會照原設定保留。</p>
        <ul><li v-for="key in savedPreferences" :key="key">{{ goalMetrics[key] || key }}</li></ul>
      </details>
    </div>
  </section>
</template>
<style scoped>
.metric-settings { display: grid; gap: var(--space-xs); }
.metric-heading { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--space-xs); }
h3 { font-size: var(--type-secondary); font-weight: var(--weight-medium); line-height: var(--leading-ui); }
.metric-help, .metric-summary, .metric-option p, li { color: var(--el-text-color-secondary); font-size: var(--type-metadata); line-height: var(--leading-ui); }
.metric-options { display: grid; gap: var(--space-sm); margin-top: var(--space-xs); }
.metric-summary { margin: 0; }
.metric-option { display: grid; gap: var(--space-2xs); }
.metric-option :deep(.el-checkbox) { height: auto; margin-right: 0; white-space: normal; }
.metric-option :deep(.el-checkbox__label) { white-space: normal; }
.metric-option p { padding-left: var(--space-lg); }
.saved-preferences { border-top: 1px solid var(--el-border-color-lighter); padding-top: var(--space-sm); }
summary { cursor: pointer; font-size: var(--type-secondary); line-height: var(--leading-ui); }
.saved-preferences .metric-help { margin-top: var(--space-xs); }
ul { margin: var(--space-xs) 0 0; padding-left: var(--space-lg); }
</style>
