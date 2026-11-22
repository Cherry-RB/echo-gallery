<script setup lang="ts">
import { computed, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import { experimentApi } from '../../utils/api/experimentApi'

const props = defineProps<{ experimentId: number }>()
const client = useQueryClient()
const topicName = ref('')
const practiceText = ref('')
const topics = useQuery({ queryKey: computed(() => ['experiment-topics', props.experimentId]), queryFn: () => experimentApi.getTopics(props.experimentId) })
const practices = useQuery({ queryKey: computed(() => ['experiment-practices', props.experimentId]), queryFn: () => experimentApi.getPinnedPractices(props.experimentId) })
const nextTopicSlot = computed(() => [1, 2, 3].find(slot => !topics.data.value?.some(topic => topic.pinnedSlot === slot)))
const refresh = () => Promise.all([client.invalidateQueries({ queryKey: ['experiment-topics', props.experimentId] }), client.invalidateQueries({ queryKey: ['experiment-practices', props.experimentId] })])
const addTopic = useMutation({ mutationFn: () => experimentApi.createTopic(props.experimentId, topicName.value.trim()), onSuccess: async () => { topicName.value = ''; await refresh() }, onError: () => ElMessage.error('無法新增主題') })
const pinTopic = useMutation({ mutationFn: ({ id, slot }: { id: number; slot: number }) => experimentApi.pinTopic(props.experimentId, id, slot), onSuccess: refresh, onError: () => ElMessage.error('無法置頂主題') })
const removeTopic = useMutation({ mutationFn: (id: number) => experimentApi.deleteTopic(props.experimentId, id), onSuccess: refresh, onError: () => ElMessage.error('無法移除主題') })
const addPractice = useMutation({ mutationFn: () => experimentApi.pinPractice(props.experimentId, practiceText.value.trim()), onSuccess: async () => { practiceText.value = ''; await refresh() }, onError: () => ElMessage.error('最多可置頂三個不同做法') })
const removePractice = useMutation({ mutationFn: (id: number) => experimentApi.unpinPractice(props.experimentId, id), onSuccess: refresh, onError: () => ElMessage.error('無法移除做法') })
</script>

<template>
  <section class="organization-panel" aria-label="探索整理">
    <div class="panel-section">
      <div class="panel-heading"><h2>主題</h2><span>可置頂 3 個</span></div>
      <div class="quick-add"><el-input v-model="topicName" maxlength="100" placeholder="新增主題" @keyup.enter="topicName.trim() && addTopic.mutate()" /><el-button :loading="addTopic.isPending.value" :disabled="!topicName.trim()" @click="addTopic.mutate()">新增</el-button></div>
      <div v-if="topics.data.value?.length" class="chips"><span v-for="topic in topics.data.value" :key="topic.id" class="topic-chip"><button type="button" :class="{ pinned: topic.pinnedSlot }" :disabled="!topic.pinnedSlot && !nextTopicSlot" @click="pinTopic.mutate({ id: topic.id, slot: topic.pinnedSlot ? 0 : nextTopicSlot! })">{{ topic.name }}</button><button type="button" aria-label="移除主題" @click="removeTopic.mutate(topic.id)">×</button></span></div>
      <p v-else class="empty">以主題整理不同方向；置頂主題會優先顯示。</p>
    </div>
    <div class="panel-section">
      <div class="panel-heading"><h2>常用做法</h2><span>{{ practices.data.value?.length ?? 0 }} / 3</span></div>
      <div class="quick-add"><el-input v-model="practiceText" maxlength="10000" placeholder="從紀錄整理一個可重複使用的做法" @keyup.enter="practiceText.trim() && addPractice.mutate()" /><el-button :loading="addPractice.isPending.value" :disabled="!practiceText.trim() || (practices.data.value?.length ?? 0) >= 3" @click="addPractice.mutate()">置頂</el-button></div>
      <ol v-if="practices.data.value?.length" class="practice-list"><li v-for="practice in practices.data.value" :key="practice.id"><span>{{ practice.content }}</span><el-button text type="danger" size="small" @click="removePractice.mutate(practice.id)">移除</el-button></li></ol>
      <p v-else class="empty">尚未置頂做法。</p>
    </div>
  </section>
</template>

<style scoped>
.organization-panel { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--space-lg); padding: var(--panel-padding); border: 1px solid var(--el-border-color-light); border-radius: var(--radius-md); background: var(--el-bg-color); }.panel-section { min-width: 0; }.panel-section + .panel-section { padding-left: var(--space-lg); border-left: 1px solid var(--el-border-color-lighter); }.panel-heading, .quick-add, .chips, .practice-list li { display: flex; align-items: center; gap: var(--space-sm); }.panel-heading { justify-content: space-between; margin-bottom: var(--space-sm); }.panel-heading h2 { margin: 0; font-size: var(--type-card-title); }.panel-heading span, .empty { color: var(--el-text-color-secondary); font-size: var(--type-meta); }.quick-add :deep(.el-input) { min-width: 0; }.chips { flex-wrap: wrap; margin-top: var(--space-sm); }.topic-chip { display: inline-flex; border: 1px solid var(--el-border-color); border-radius: var(--radius-sm); overflow: hidden; }.topic-chip button { border: 0; background: var(--el-fill-color-blank); color: var(--el-text-color-regular); cursor: pointer; }.topic-chip button:first-child { padding: 4px 8px; }.topic-chip button:first-child.pinned { background: var(--el-color-primary-light-9); color: var(--el-color-primary); }.topic-chip button:last-child { padding: 4px 6px; border-left: 1px solid var(--el-border-color-lighter); }.practice-list { display: grid; gap: var(--space-xs); margin: var(--space-sm) 0 0; padding: 0; list-style: none; }.practice-list li { justify-content: space-between; padding: var(--space-xs) 0; border-bottom: 1px solid var(--el-border-color-lighter); color: var(--el-text-color-regular); font-size: var(--type-secondary); }.practice-list span { min-width: 0; overflow-wrap: anywhere; }.empty { margin: var(--space-sm) 0 0; } @media (max-width: 760px) { .organization-panel { grid-template-columns: 1fr; }.panel-section + .panel-section { padding-top: var(--space-lg); padding-left: 0; border-top: 1px solid var(--el-border-color-lighter); border-left: 0; } }
</style>
