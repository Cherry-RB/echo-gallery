<script setup lang="ts">
import { computed, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import { experimentApi } from '../../utils/api/experimentApi'

const props = defineProps<{ experimentId: number }>()
const client = useQueryClient()
const practiceText = ref('')
const practices = useQuery({ queryKey: computed(() => ['experiment-practices', props.experimentId]), queryFn: () => experimentApi.getPinnedPractices(props.experimentId) })
const refresh = () => client.invalidateQueries({ queryKey: ['experiment-practices', props.experimentId] })
const addPractice = useMutation({ mutationFn: () => experimentApi.pinPractice(props.experimentId, practiceText.value.trim()), onSuccess: async () => { practiceText.value = ''; await refresh() }, onError: () => ElMessage.error('最多可置頂三個不同做法') })
const removePractice = useMutation({ mutationFn: (id: number) => experimentApi.unpinPractice(props.experimentId, id), onSuccess: refresh, onError: () => ElMessage.error('無法移除做法') })
</script>

<template>
  <section class="organization-panel" aria-label="常用做法管理">
    <div class="panel-section">
      <div class="panel-heading"><h2>常用做法</h2><span>{{ practices.data.value?.length ?? 0 }} / 3</span></div>
      <div class="quick-add"><el-input v-model="practiceText" maxlength="10000" placeholder="從紀錄整理一個可重複使用的做法" @keyup.enter="practiceText.trim() && addPractice.mutate()" /><el-button :loading="addPractice.isPending.value" :disabled="!practiceText.trim() || (practices.data.value?.length ?? 0) >= 3" @click="addPractice.mutate()">置頂</el-button></div>
      <ol v-if="practices.data.value?.length" class="practice-list"><li v-for="practice in practices.data.value" :key="practice.id"><span>{{ practice.content }}</span><el-button text type="danger" size="small" @click="removePractice.mutate(practice.id)">移除</el-button></li></ol>
      <p v-else class="empty">尚未置頂做法。</p>
    </div>
  </section>
</template>

<style scoped>
.organization-panel { padding: var(--panel-padding); border: 1px solid var(--el-border-color-light); border-radius: var(--radius-md); background: var(--el-bg-color); }.panel-section { min-width: 0; }.panel-heading, .quick-add, .practice-list li { display: flex; align-items: center; gap: var(--space-sm); }.panel-heading { justify-content: space-between; margin-bottom: var(--space-sm); }.panel-heading h2 { margin: 0; font-size: var(--type-card-title); }.panel-heading span, .empty { color: var(--el-text-color-secondary); font-size: var(--type-meta); }.quick-add :deep(.el-input) { min-width: 0; }.practice-list { display: grid; gap: var(--space-xs); margin: var(--space-sm) 0 0; padding: 0; list-style: none; }.practice-list li { justify-content: space-between; padding: var(--space-xs) 0; border-bottom: 1px solid var(--el-border-color-lighter); color: var(--el-text-color-regular); font-size: var(--type-secondary); }.practice-list span { min-width: 0; overflow-wrap: anywhere; }.empty { margin: var(--space-sm) 0 0; }
</style>
