<script setup lang="ts">
import type { TreasureRank } from '../../types/treasure'
import { ranks } from '../../utils/treasure'
defineProps<{ rank: TreasureRank }>()
</script>

<template>
  <span class="rank-badge" :data-rank="rank"><span class="rank-mark" aria-hidden="true">◆</span>{{ ranks[rank] }}</span>
</template>

<style scoped>
.rank-badge { display: inline-flex; align-items: center; gap: var(--space-2xs); padding: var(--space-2xs) var(--space-xs); border: 1px solid var(--rank-border); color: var(--rank-text); background: var(--rank-tint); border-radius: var(--radius-sm); font-size: var(--type-meta); font-weight: var(--weight-semibold); line-height: var(--leading-metadata); white-space: nowrap; }
.rank-mark { font-size: var(--type-meta); }
</style>
