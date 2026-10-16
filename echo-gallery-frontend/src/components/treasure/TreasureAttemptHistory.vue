<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { treasureApi } from '../../utils/api/treasureApi'
import { trialStatuses } from '../../utils/treasure'
import { formatDate } from '../../utils/formatDate'
import ExpandableText from '../ExpandableText.vue'
import HistoryDialog from '../ui/HistoryDialog.vue'
import HistoryTimeline from '../ui/HistoryTimeline.vue'
import HistoryFields from '../ui/HistoryFields.vue'
import HistoryEvent from '../ui/HistoryEvent.vue'
import RecordHistoryTrigger from '../ui/RecordHistoryTrigger.vue'
const props = defineProps<{ treasureId: number; trialId: number; compact?: boolean; recordable?: boolean }>()
const emit = defineEmits<{ record: [] }>()
const page = ref(0)
const historyVisible = ref(false)
watch(() => props.trialId, () => { page.value = 0; historyVisible.value = false })
const query = useQuery({ queryKey: computed(() => ['treasures', props.treasureId, 'attempts', props.trialId, page.value]), queryFn: () => treasureApi.attempts(props.treasureId, props.trialId, page.value) })
</script>
<template>
  <section class="attempt-history" aria-label="這一階的嘗試歷程">
    <p v-if="query.isLoading.value" class="history-meta">正在讀取…</p>
    <el-button v-else-if="query.isError.value" text @click="query.refetch()">歷程讀取失敗，重試</el-button>
    <template v-else>
      <div v-if="compact && query.data.value?.content[0]" class="attempt-preview"><header class="attempt-summary-heading"><p class="history-meta">最近一次：{{ formatDate(query.data.value.content[0].attemptedAt) }} · {{ trialStatuses[query.data.value.content[0].status] }}</p><RecordHistoryTrigger label="嘗試紀錄" :count="query.data.value.totalElements" @click="historyVisible = true" /></header><ExpandableText :content="query.data.value.content[0].resultNote" :lines="2" /></div>
      <HistoryTimeline v-if="!compact">
      <HistoryEvent v-for="(attempt, index) in query.data.value?.content ?? []" :key="attempt.id" :date="attempt.attemptedAt" :latest="page === 0 && index === 0">
        <p class="attempt-status">{{ trialStatuses[attempt.status] }}</p>
        <HistoryFields left-label="當時的檢定" right-label="這次嘗試">
          <template #left><strong class="snapshot-title">{{ attempt.titleSnapshot }}</strong><ExpandableText :content="attempt.criteriaSnapshot" :lines="6" /></template>
          <template #right><ExpandableText :content="attempt.resultNote" :lines="6" /></template>
        </HistoryFields>
        <div v-if="attempt.evidenceUrl" class="attempt-evidence"><a :href="attempt.evidenceUrl" target="_blank" rel="noopener noreferrer">佐證連結</a></div>
      </HistoryEvent>
      </HistoryTimeline>
      <p v-if="!query.data.value?.content.length" class="history-meta">尚無嘗試紀錄。每次嘗試都會獨立保留。</p>
    </template>
    <el-pagination v-if="!compact && (query.data.value?.totalElements ?? 0) > 10" :current-page="page + 1" :page-size="10" :total="query.data.value?.totalElements ?? 0" layout="prev, pager, next" @current-change="page = $event - 1" />
    <HistoryDialog v-if="historyVisible" v-model="historyVisible" title="嘗試歷程" description="每次嘗試獨立保留當時的檢定條件與實際結果。">
      <template v-if="recordable" #actions><button type="button" class="history-record-action" @click="emit('record')">＋記錄</button></template>
      <TreasureAttemptHistory :treasure-id="treasureId" :trial-id="trialId" />
      <template #footer><el-button @click="historyVisible = false">關閉</el-button></template>
    </HistoryDialog>
  </section>
</template>
<style scoped>
.attempt-history { margin-top: var(--space-md); }
.attempt-preview { padding: var(--space-md); background: var(--surface-summary); border: 1px solid var(--el-border-color-lighter); border-radius: var(--radius-md); }
.attempt-summary-heading { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: var(--space-sm); }
.history-meta { margin: var(--space-xs) 0; }
.attempt-history h3 { margin: 0 0 var(--space-sm); font-size: var(--type-ui); }
.attempt-history h3 span, .history-meta { color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.attempt-status { margin: 0; color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.snapshot-title { display: block; margin-bottom: var(--space-xs); font-size: var(--type-ui); overflow-wrap: anywhere; }
.attempt-evidence { margin-top: var(--space-sm); padding-top: var(--space-xs); border-top: 1px solid var(--el-border-color-lighter); }
.attempt-evidence a { color: var(--el-color-primary); overflow-wrap: anywhere; }
.history-record-action { display: inline-flex; align-items: center; padding: 0; border: 0; background: transparent; color: var(--el-color-primary); font: inherit; font-size: var(--type-caption); cursor: pointer; }
.history-record-action:hover { text-decoration: underline; }
.history-record-action:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 2px; }
</style>
