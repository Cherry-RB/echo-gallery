<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import type { WorkSummary } from '../../types/work'
import { workStages } from '../../types/work'
import { formatDate } from '../../utils/formatDate'

const props = defineProps<{ work: WorkSummary }>()
const route = useRoute()
const target = computed(() => ({ path: `/works/${props.work.id}`, query: { from: route.fullPath } }))
</script>

<template>
  <router-link class="work-relation-row" :to="target">
    <span class="work-relation-main">
      <strong class="work-relation-title">{{ work.title }}</strong>
      <span class="work-relation-stage">{{ workStages[work.stage] }}<template v-if="work.archived"> · 已封存</template></span>
    </span>
    <span class="work-relation-meta">最近編修 {{ formatDate(work.editedAt) }}</span>
  </router-link>
</template>

<style scoped>
.work-relation-row { display: flex; align-items: center; justify-content: space-between; gap: var(--space-md); min-width: 0; padding: var(--space-md); border: 1px solid var(--el-border-color-lighter); border-radius: var(--radius-md); background: var(--surface-subtle); text-decoration: none; cursor: pointer; transition: border-color 160ms ease, background-color 160ms ease; }
.work-relation-main { display: flex; align-items: baseline; flex-wrap: wrap; gap: var(--space-sm); min-width: 0; }
.work-relation-title { color: var(--el-text-color-primary); font-size: var(--type-card-title); font-weight: var(--weight-semibold); line-height: var(--leading-card-title); overflow-wrap: anywhere; }
.work-relation-stage, .work-relation-meta { color: var(--el-text-color-secondary); font-size: var(--type-meta); line-height: var(--leading-metadata); }
.work-relation-meta { flex-shrink: 0; text-align: right; }
.work-relation-row:hover { border-color: var(--el-color-primary-light-5); background: var(--el-color-primary-light-9); }
.work-relation-row:hover .work-relation-title { color: var(--el-color-primary); }
.work-relation-row:active { background: var(--el-color-primary-light-8); }
.work-relation-row:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: var(--space-2xs); }
@media (max-width: 560px) { .work-relation-row { align-items: flex-start; flex-direction: column; gap: var(--space-xs); } .work-relation-meta { text-align: left; } }
</style>
