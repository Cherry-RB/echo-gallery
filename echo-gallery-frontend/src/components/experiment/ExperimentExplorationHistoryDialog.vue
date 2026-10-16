<script setup lang="ts">
import type { ExperimentExplorationRecordDto } from '../../types/experiment'
import { formatDate } from '../../utils/formatDate'
import HistoryDialog from '../ui/HistoryDialog.vue'
import HistoryTimeline from '../ui/HistoryTimeline.vue'
import HistoryFields from '../ui/HistoryFields.vue'
import HistoryEvent from '../ui/HistoryEvent.vue'
import ExperimentQuickRecordAction from './ExperimentQuickRecordAction.vue'
import ExpandableText from '../ExpandableText.vue'

withDefaults(defineProps<{
  modelValue: boolean
  records: ExperimentExplorationRecordDto[]
  manageable?: boolean
  showOrganize?: boolean
  loading?: boolean
}>(), {
  manageable: true,
  showOrganize: true,
  loading: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  edit: [record: ExperimentExplorationRecordDto]
  delete: [recordId: number]
  organize: []
  'open-card': [cardId: number]
  record: ['TRY' | 'DISCOVERY']
}>()
</script>

<template>
  <HistoryDialog :model-value="modelValue" title="探索紀錄" description="沿著留下的痕跡，回看試過的路與帶回的發現。" @update:model-value="emit('update:modelValue', $event)">
    <template v-if="manageable" #actions><ExperimentQuickRecordAction @select="emit('record', $event)" /></template>
    <el-skeleton v-if="loading" :rows="3" animated />
    <HistoryTimeline v-else-if="records.length">
      <HistoryEvent v-for="(record, index) in records" :key="record.id" :date="record.createdAt" :latest="index === 0" :manageable="manageable" :operation-label="`${formatDate(record.createdAt)} 探索紀錄操作`" @command="$event === 'edit' ? emit('edit', record) : emit('delete', record.id)">
        <HistoryFields left-label="試法" right-label="發現">
          <template #left><ExpandableText v-if="record.tryText" :content="record.tryText" :lines="6" /><p v-else class="empty">這次直接留下發現。</p></template>
          <template #right><ExpandableText v-if="record.discovery" :content="record.discovery" :lines="6" /><p v-else class="empty">尚未記下發現</p></template>
        </HistoryFields>

        <div v-if="record.exports.length" class="record-exports">
          <span>整理結果</span>
          <div>
            <button
              v-for="recordExport in record.exports"
              :key="`${recordExport.cardId}-${recordExport.exportedAt}`"
              type="button"
              @click="emit('open-card', recordExport.cardId)"
            >
              {{ recordExport.cardTitle }}
            </button>
          </div>
        </div>

      </HistoryEvent>
    </HistoryTimeline>
    <p v-else class="history-empty">這段探索尚未留下紀錄。等試過或發現了什麼，再回來記上一筆。</p>
    <template v-if="records.length && showOrganize" #footer><div class="history-footer"><span>整理成卡片後，原探索紀錄仍會保留。</span><div><el-button @click="emit('update:modelValue', false)">關閉</el-button><el-button type="primary" plain @click="emit('organize')">整理成卡片</el-button></div></div></template>
  </HistoryDialog>
</template>
<style scoped>
.history-footer { display: flex; align-items: center; justify-content: space-between; gap: var(--space-md); width: 100%; }
.history-footer > span { color: var(--el-text-color-placeholder); font-size: var(--type-meta); }
.history-footer > div { display: flex; justify-content: flex-end; flex-wrap: wrap; gap: var(--space-xs); }
.history-footer > div :deep(.el-button) { margin-left: 0; }
.empty { color: var(--el-text-color-placeholder); }

.record-exports {
  margin-top: var(--space-sm);
  padding-top: var(--space-xs);
  border-top: 1px solid var(--el-border-color-lighter);
}

.record-exports div {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-xs) var(--space-sm);
  margin-top: var(--space-2xs);
}

.record-exports button {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--el-color-primary);
  cursor: pointer;
  font: inherit;
  font-size: var(--type-caption);
  text-align: left;
}

.record-exports button:hover,
.record-exports button:focus-visible {
  text-decoration: underline;
}

.history-empty {
  margin: 0;
  padding: var(--space-xl);
  color: var(--el-text-color-placeholder);
  font-size: var(--type-caption);
  line-height: var(--leading-ui);
  text-align: center;
}

.history-footer {
  width: 100%;
}

@media (max-width: 760px) {
  .history-footer {
    align-items: stretch;
    flex-direction: column;
  }
}

</style>
