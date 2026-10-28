<script setup lang="ts">
import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { workUseTarget } from '../../utils/workFlow'
import { workApi } from '../../utils/api/workApi'
import type { WorkUsage } from '../../types/work'

const props = withDefaults(defineProps<{
  cardId?: number
  cardTitle?: string
  recordId?: number
  experimentId?: number
  usages?: WorkUsage[]
  showAction?: boolean
}>(), { showAction: true })

const ownUsages = useQuery({
  queryKey: computed(() => ['works', 'usages', props.cardId]),
  queryFn: () => workApi.usages(props.cardId ? [props.cardId] : [], []),
  enabled: computed(() => !!props.cardId && props.usages === undefined),
  retry: false,
  refetchOnWindowFocus: false,
})
const links = computed(() => props.usages ?? ownUsages.data.value ?? [])
const loadsOwnUsages = computed(() => !!props.cardId && props.usages === undefined)
</script>

<template>
  <div v-if="showAction || links.length || loadsOwnUsages" class="use-work">
    <el-button v-if="showAction && cardId" link type="primary" @click.stop="workUseTarget = { cardIds: [cardId], cardTitle, experimentId }">用於作品</el-button>
    <router-link v-for="work in links" :key="work.workId" class="work-usage-link" :to="`/works/${work.workId}`">{{ work.title }}{{ work.archived ? '（已封存）' : '' }}</router-link>
    <p v-if="loadsOwnUsages && ownUsages.isPending.value" class="usage-feedback" role="status">正在載入作品引用…</p>
    <p v-else-if="loadsOwnUsages && ownUsages.isError.value" class="usage-feedback" role="alert">無法載入作品引用。<el-button link type="primary" :loading="ownUsages.isFetching.value" @click="ownUsages.refetch()">重試</el-button></p>
    <p v-else-if="loadsOwnUsages && !links.length" class="usage-feedback">尚未被作品引用。</p>
  </div>
</template>

<style scoped>
.use-work { display: flex; flex-wrap: wrap; gap: var(--space-sm); margin-top: var(--space-sm); align-items: center; min-width: 0; }
.work-usage-link { padding: var(--space-2xs) var(--space-xs); border: 1px solid var(--el-border-color); border-radius: var(--radius-sm); color: var(--el-text-color-regular); font-size: var(--type-metadata); line-height: var(--leading-metadata); text-decoration: none; overflow-wrap: anywhere; }
.work-usage-link:hover { border-color: var(--el-color-primary-light-5); background: var(--el-color-primary-light-9); color: var(--el-color-primary); }
.work-usage-link:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: var(--space-2xs); }
.use-work :deep(.el-button) { margin: 0; }
.usage-feedback { margin: 0; color: var(--el-text-color-secondary); font-size: var(--type-secondary); line-height: var(--leading-ui); }
</style>
