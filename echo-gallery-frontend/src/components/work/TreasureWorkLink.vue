<script setup lang="ts">
import { computed, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { workApi } from '../../utils/api/workApi'
import WorkRelationRow from './WorkRelationRow.vue'

const props = defineProps<{ treasureId: number }>()
const emit = defineEmits<{ count: [number | undefined] }>()
const query = useQuery({ queryKey: computed(() => ['works', 'treasure', props.treasureId]), queryFn: () => workApi.fromTreasure(props.treasureId) })
watch(() => query.data.value, works => emit('count', works?.length), { immediate: true })
</script>

<template>
  <div v-if="query.data.value?.length" class="treasure-work-list">
    <WorkRelationRow v-for="work in query.data.value" :key="work.id" :work="work" />
  </div>
  <p v-else-if="query.isError.value" class="work-feedback">無法載入收錄作品。<el-button text @click="query.refetch()">重試</el-button></p>
  <p v-else-if="query.isLoading.value" class="work-feedback">正在載入收錄作品…</p>
  <p v-else class="work-empty">尚未收錄作品。可從「管理作品」挑選要收入這件展品的創作。</p>
</template>

<style scoped>
.treasure-work-list { display: grid; row-gap: var(--space-sm); margin-top: var(--space-md); }
.work-feedback, .work-empty { margin: var(--space-md) 0 0; color: var(--el-text-color-secondary); font-size: var(--type-secondary); line-height: var(--leading-ui); }
</style>
