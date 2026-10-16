<script setup lang="ts">
import { computed } from 'vue'
import TreasureRankBadge from './TreasureRankBadge.vue'
import { rankGuides } from '../../utils/treasure'
import type { TreasureRank } from '../../types/treasure'

const props = defineProps<{ rank: TreasureRank; modelValue: number; hideBadge?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [number] }>()
const guide = computed(() => rankGuides[props.rank])
const example = computed(() => guide.value.examples[props.modelValue] ?? guide.value.examples[0])
</script>

<template>
  <section class="challenge-reference" :data-rank="rank" aria-label="挑戰參考">
    <div class="reference-heading"><TreasureRankBadge v-if="!hideBadge" :rank="rank" /><h3>{{ guide.stage }}</h3></div>
    <p class="reference-meaning">{{ guide.meaning }}</p>
    <div class="example-options" role="group" aria-label="選擇範例主題">
      <el-button v-for="(item, index) in guide.examples" :key="item.title" :type="index === modelValue ? 'primary' : undefined" :plain="index === modelValue" :aria-pressed="index === modelValue" @click="emit('update:modelValue', index)">{{ item.title.split('：')[0] }}</el-button>
    </div>
    <article v-if="example" class="example-preview" aria-live="polite">
      <h4>{{ example.title }}</h4>
      <span class="reference-label">可參考的通過條件</span>
      <p>{{ example.criteria }}</p>
    </article>
    <p class="reference-note">這是參考路線，可依成果調整對象、人數與確認方式，不必完成所有範例。</p>
  </section>
</template>

<style scoped>
.reference-heading { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-sm); }
.reference-heading h3, .example-preview h4 { margin: 0; color: var(--el-text-color-primary); font-size: var(--type-card-title); font-weight: var(--weight-semibold); line-height: var(--leading-card-title); overflow-wrap: anywhere; }
.reference-meaning { margin: var(--space-sm) 0 var(--space-lg); color: var(--el-text-color-regular); font-size: var(--type-body); line-height: var(--leading-body); }
.example-options { display: flex; flex-wrap: wrap; gap: var(--space-xs); }
.example-options :deep(.el-button) { margin-left: 0; }
.example-preview { margin-top: var(--space-md); padding: var(--space-md); border: 1px solid var(--el-border-color-lighter); border-radius: var(--radius-md); background: var(--surface-subtle); }
.reference-label { display: block; margin-top: var(--space-md); font-size: var(--type-meta); line-height: var(--leading-metadata); color: var(--el-text-color-secondary); }
.example-preview p { margin: var(--space-xs) 0 0; font-size: var(--type-body); line-height: var(--leading-body); color: var(--el-text-color-regular); overflow-wrap: anywhere; }
.reference-note { margin: var(--space-lg) 0 0; font-size: var(--type-secondary); line-height: var(--leading-ui); color: var(--el-text-color-secondary); }
</style>
