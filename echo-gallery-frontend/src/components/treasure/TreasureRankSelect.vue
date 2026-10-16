<script setup lang="ts">
import type { TreasureRank } from '../../types/treasure'
import { ranks } from '../../utils/treasure'
import TreasureRankBadge from './TreasureRankBadge.vue'
defineProps<{ modelValue: TreasureRank | '' }>()
const emit = defineEmits<{ 'update:modelValue': [TreasureRank | ''] }>()
</script>
<template>
  <el-select :model-value="modelValue" clearable placeholder="所有等級" aria-label="篩選等級" @update:model-value="emit('update:modelValue', $event || '')">
    <template #label><TreasureRankBadge v-if="modelValue" :rank="modelValue" /></template>
    <el-option v-for="(label, rank) in ranks" :key="rank" :label="label" :value="rank"><span class="rank-option"><TreasureRankBadge :rank="rank" /><span v-if="modelValue === rank" aria-hidden="true">✓</span></span></el-option>
  </el-select>
</template>
<style scoped>
.rank-option { display: flex; align-items: center; justify-content: space-between; gap: var(--space-sm); }
</style>
