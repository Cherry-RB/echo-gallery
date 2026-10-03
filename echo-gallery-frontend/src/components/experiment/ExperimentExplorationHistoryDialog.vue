<script setup lang="ts">
import { Delete, Edit, MoreFilled } from '@element-plus/icons-vue'
import type { ExperimentExplorationRecordDto } from '../../types/experiment'
import { formatDate } from '../../utils/formatDate'
import AppDialog from '../AppDialog.vue'
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
}>()
</script>

<template>
  <AppDialog
    :model-value="modelValue"
    title="探索紀錄"
    width="min(760px, calc(100vw - 32px))"
    scroll-body
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <p class="history-intro">回顧曾經試過什麼、留下什麼發現，以及後來整理到了哪裡。</p>

    <el-skeleton v-if="loading" :rows="3" animated />
    <ol v-else-if="records.length" class="history-list">
      <li v-for="(record, index) in records" :key="record.id" class="history-record">
        <span class="timeline-marker" aria-hidden="true"></span>
        <article class="record-entry">
        <header class="record-header">
          <div class="record-time">
            <span v-if="index === 0" class="latest-label">最新</span>
            <time :datetime="record.createdAt">{{ formatDate(record.createdAt, 'YYYY/MM/DD HH:mm') }}</time>
          </div>
          <el-dropdown v-if="manageable" trigger="click" @command="(command: 'edit' | 'delete') => command === 'edit' ? emit('edit', record) : emit('delete', record.id)">
            <button type="button" class="more-button" :aria-label="`${formatDate(record.createdAt, 'YYYY/MM/DD HH:mm')} 探索紀錄操作`">
              <el-icon><MoreFilled /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="edit" :icon="Edit">編輯</el-dropdown-item>
                <el-dropdown-item command="delete" :icon="Delete">刪除</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </header>

        <div class="record-fields">
          <section>
            <span>試法</span>
            <ExpandableText v-if="record.tryText" :content="record.tryText" :lines="6" />
            <p v-else class="empty">這次直接留下發現。</p>
          </section>
          <section>
            <span>發現</span>
            <ExpandableText v-if="record.discovery" :content="record.discovery" :lines="6" />
            <p v-else class="empty">尚未記下發現</p>
          </section>
        </div>

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
        </article>
      </li>
    </ol>
    <p v-else class="history-empty">還沒有探索紀錄。等真正試過或發現了什麼，再回來留下即可。</p>

    <template v-if="records.length && showOrganize" #footer>
      <div class="history-footer">
        <span>整理成卡片後，原探索紀錄仍會保留。</span>
        <el-button type="primary" plain @click="emit('organize')">整理成卡片</el-button>
      </div>
    </template>
  </AppDialog>
</template>

<style scoped>
.history-intro {
  margin: 0 0 var(--space-md);
  color: var(--el-text-color-secondary);
  font-size: var(--type-caption);
  line-height: var(--leading-ui);
}

.history-list {
  position: relative;
  margin: 0;
  padding: 0 0 0 var(--space-xs);
  list-style: none;
}

.history-record {
  display: grid;
  grid-template-columns: 20px minmax(0, 1fr);
  gap: var(--space-sm);
  padding: 0 0 var(--space-sm);
}

.record-entry {
  min-width: 0;
  padding: var(--space-md);
  border-radius: var(--radius-md);
  border: 1px solid var(--el-border-color-lighter);
  background: var(--surface-summary);
}

.timeline-marker {
  position: relative;
  display: block;
  min-height: 100%;
}

.timeline-marker::before {
  position: absolute;
  top: 10px;
  bottom: -12px;
  left: 9px;
  width: 1px;
  background: var(--el-border-color);
  content: '';
}

.timeline-marker::after {
  position: absolute;
  top: 6px;
  left: 5px;
  width: 7px;
  height: 7px;
  border: 2px solid var(--el-bg-color);
  border-radius: 50%;
  background: var(--experiment-accent, var(--el-color-primary));
  box-shadow: 0 0 0 1px var(--el-color-primary-light-5);
  content: '';
}

.history-record:last-child .timeline-marker::before {
  bottom: auto;
  height: 8px;
}

.record-header,
.record-time,
.history-footer {
  display: flex;
  align-items: center;
}

.record-header,
.history-footer {
  justify-content: space-between;
  gap: var(--space-md);
}

.record-time {
  gap: var(--space-xs);
}

.record-time time,
.record-exports > span,
.history-footer > span {
  color: var(--el-text-color-placeholder);
  font-size: var(--type-meta);
}

.latest-label {
  padding: var(--space-2xs) var(--space-xs);
  border-radius: var(--radius-sm);
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-size: var(--type-meta);
  font-weight: 600;
}

.more-button {
  padding: var(--space-2xs);
  border: 0;
  background: transparent;
  color: var(--el-text-color-secondary);
  cursor: pointer;
}

.record-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin-top: var(--space-sm);
}

.record-fields section {
  min-width: 0;
  padding-right: var(--space-md);
}

.record-fields section + section {
  padding-right: 0;
  padding-left: var(--space-md);
  border-left: 1px solid var(--el-border-color-lighter);
}

.record-fields span {
  display: block;
  margin-bottom: var(--space-2xs);
  color: var(--el-text-color-secondary);
  font-size: var(--type-caption);
  font-weight: var(--weight-medium);
  line-height: var(--leading-ui);
}

.record-fields p {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: var(--type-ui);
  line-height: var(--leading-ui);
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.record-fields p.empty {
  color: var(--el-text-color-placeholder);
}

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
  .record-fields {
    grid-template-columns: minmax(0, 1fr);
  }

  .record-fields section {
    padding-right: 0;
  }

  .record-fields section + section {
    margin-top: var(--space-sm);
    padding-top: var(--space-sm);
    padding-left: 0;
    border-top: 1px solid var(--el-border-color-lighter);
    border-left: 0;
  }

  .history-footer {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
