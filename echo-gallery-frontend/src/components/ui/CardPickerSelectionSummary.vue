<script setup lang="ts">
import type { CardDto } from '../../types/card'
import SelectionSummary from './SelectionSummary.vue'
defineProps<{ card: CardDto | null; emptyText?: string }>()
</script>
<template>
  <SelectionSummary class="card-picker-selection-summary" :title="card?.title" :metadata="card ? '#' + card.id + ' · ' + (card.type === 'link' ? '連結' : '筆記') : undefined" :empty-text="emptyText ?? '請先從左側選擇一張卡片。'" />
</template>
