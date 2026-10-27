<script setup lang="ts">
import { computed, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import type { ExplorationThreadDto } from '../../types/experiment'
import type { MethodOption, ActivityRecord } from '../../types/explorationActivity'
import { explorationActivityApi as api } from '../../utils/api/explorationActivityApi'
import ExplorationMethodGroup from './ExplorationMethodGroup.vue'
const props = defineProps<{ experimentId: number; thread: ExplorationThreadDto }>()
const page = ref(1)
const query = useQuery({
  queryKey: computed(() => ['experiment-methods', props.experimentId, props.thread.id, '', page.value]),
  queryFn: () => api.methods(props.experimentId, { threadId: props.thread.id, page: page.value - 1 }),
  enabled: computed(() => page.value > 1),
})
const methods = computed<MethodOption[]>(() => page.value > 1 ? query.data.value?.content ?? [] : props.thread.methods.map(m => ({
  id: m.id, threadId: props.thread.id, threadName: props.thread.name, content: m.content, version: m.version ?? 0,
  discoveryCount: m.discoveryCount ?? props.thread.records.filter(r => r.methodId === m.id).length,
  hasHistory: m.hasHistory ?? false, lastOrganizedAt: m.lastOrganizedAt ?? null,
})))
const records = computed<ActivityRecord[]>(() => props.thread.records.map(r => ({
  id: r.id, threadId: props.thread.id, threadName: props.thread.name, methodId: r.methodId, version: r.version ?? 0,
  tryText: r.tryText ?? props.thread.methods.find(m => m.id === r.methodId)?.content ?? null,
  sourceThreadMethodId: r.methodId, discovery: r.discovery, createdAt: r.createdAt,
  occurredAt: r.occurredAt ?? r.createdAt, updatedAt: r.updatedAt, exports: [],
})))
const unassignedCount = computed(() => props.thread.unassignedRecordCount ?? records.value.filter(r => r.methodId == null).length)
</script>
<template>
  <div class="method-list">
    <el-skeleton v-if="page > 1 && query.isPending.value" :rows="3" />
    <p v-else-if="page > 1 && query.isError.value">載入行動失敗。<el-button type="primary" link @click="query.refetch()">重試</el-button></p>
    <template v-else><ExplorationMethodGroup v-for="method in methods" :key="method.id" :experiment-id="experimentId" :thread-id="thread.id" :method="method" :count="method.discoveryCount" :has-history="method.hasHistory" :initial-records="records.filter(r => r.methodId === method.id)" /></template>
    <el-pagination v-if="(thread.methodCount ?? thread.methods.length) > 20" v-model:current-page="page" :page-size="20" :total="thread.methodCount ?? thread.methods.length" layout="prev, pager, next" />
    <ExplorationMethodGroup v-if="unassignedCount || thread.hasUnassignedHistory" :experiment-id="experimentId" :thread-id="thread.id" :method="null" :count="unassignedCount" :has-history="!!thread.hasUnassignedHistory" :initial-records="records.filter(r => r.methodId == null)" />
    <p v-if="!thread.methodCount && !thread.methods.length && !unassignedCount && !thread.hasUnassignedHistory" class="quiet-empty">無行動與發現</p>
  </div>
</template>
<style scoped>.method-list { min-width: 0; } .quiet-empty { color: var(--el-text-color-secondary); }</style>
