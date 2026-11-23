<script setup lang="ts">
import { MoreFilled } from '@element-plus/icons-vue'
import type { ExperimentRecordDto } from '../../types/experiment'

defineProps<{ records: ExperimentRecordDto[]; selectedTopicId?: number }>()
const emit = defineEmits<{
  read: [record: ExperimentRecordDto]
  edit: [record: ExperimentRecordDto]
  repeat: [record: ExperimentRecordDto]
  remove: [record: ExperimentRecordDto]
}>()
const command = (value: string, record: ExperimentRecordDto) => {
  if (value === 'edit') emit('edit', record)
  else if (value === 'repeat') emit('repeat', record)
  else if (value === 'delete') emit('remove', record)
}
</script>

<template>
  <div class="record-index" aria-label="探索紀錄列表">
    <div class="record-index-heading" aria-hidden="true"><span>紀錄日期</span><span>紀錄</span><span>做法</span><span>主題與標記</span><span /></div>
    <article v-for="record in records" :key="record.id" class="record-index-row" role="button" tabindex="0" :aria-label="`閱讀探索紀錄：${record.title || '探索紀錄'}`" @click="emit('read', record)" @keydown.enter.self.prevent="emit('read', record)" @keydown.space.self.prevent="emit('read', record)">
      <time :datetime="record.recordedOn">{{ record.recordedOn }}</time>
      <div class="index-main"><h3>{{ record.title || '探索紀錄' }}</h3><p v-if="record.content" class="index-excerpt">{{ record.content }}</p></div>
      <p class="index-practice"><span class="mobile-label">做法｜</span>{{ record.practiceText || '—' }}</p>
      <div class="index-tags">
        <el-tag v-for="topic in (record.topics ?? []).filter(topic => topic.id !== selectedTopicId).slice(0, 1)" :key="topic.id" size="small" type="info">{{ topic.name }}</el-tag>
        <el-tag v-if="record.understandingNode" size="small">理解節點</el-tag>
        <el-tag v-if="record.methodCandidate" size="small">方法備選</el-tag>
        <el-tag v-if="record.highlight !== 'GENERAL'" size="small" type="success">{{ record.highlight === 'GAIN' ? '有所收穫' : record.highlight === 'CHALLENGE_OVERCOME' ? '克服困難' : '高峰體驗' }}</el-tag>
      </div>
      <div class="index-actions" @click.stop @keydown.stop>
        <el-dropdown trigger="click" @command="command($event, record)"><el-button text circle :icon="MoreFilled" :aria-label="`紀錄操作：${record.title || '探索紀錄'}`" /><template #dropdown><el-dropdown-menu><el-dropdown-item command="repeat">再記一筆</el-dropdown-item><el-dropdown-item command="edit">編輯</el-dropdown-item><el-dropdown-item command="delete" divided>刪除</el-dropdown-item></el-dropdown-menu></template></el-dropdown>
      </div>
    </article>
  </div>
</template>

<style scoped>
.record-index { container-type: inline-size; margin-top: var(--space-md); border: 1px solid var(--el-border-color-light); border-radius: var(--radius-md); background: var(--el-bg-color); }
.record-index-heading, .record-index-row { display: grid; grid-template-columns: 100px minmax(0, 2fr) minmax(0, 1fr) minmax(0, 1fr) 32px; align-items: center; gap: var(--space-md); padding: var(--space-sm) var(--space-md); }
.record-index-heading { border-radius: var(--radius-md) var(--radius-md) 0 0; background: var(--surface-subtle); color: var(--el-text-color-secondary); font-size: var(--type-meta); line-height: var(--leading-ui); }
.record-index-row { min-width: 0; border-top: 1px solid var(--el-border-color-lighter); cursor: pointer; transition: background-color .15s ease; }
.record-index-row:hover, .record-index-row:focus-within { background: var(--el-color-primary-light-9); }
.record-index-row:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: -2px; }
.record-index-row:last-child { border-radius: 0 0 var(--radius-md) var(--radius-md); }
.record-index-row > * { min-width: 0; }
.record-index-row time { color: var(--el-text-color-secondary); font-size: var(--type-meta); font-variant-numeric: tabular-nums; white-space: nowrap; }
.index-main h3 { margin: 0; color: var(--el-text-color-primary); font-size: var(--type-body); font-weight: var(--weight-semibold); line-height: var(--leading-body); overflow-wrap: anywhere; }
.index-main h3, .index-excerpt, .index-practice { display: -webkit-box; overflow: hidden; -webkit-box-orient: vertical; -webkit-line-clamp: 2; overflow-wrap: anywhere; }
.index-excerpt, .index-practice { margin: 0; color: var(--el-text-color-secondary); font-size: var(--type-secondary); line-height: var(--leading-ui); }
.index-excerpt { margin-top: var(--space-2xs); }
.index-tags { display: flex; align-items: center; flex-wrap: wrap; gap: var(--space-2xs); }
.index-tags :deep(.el-tag) { max-width: 100%; height: auto; white-space: normal; overflow-wrap: anywhere; }
.mobile-label { display: none; }
@container (max-width: 680px) {
  .record-index-heading { display: none; }
  .record-index-row { grid-template-columns: minmax(0, 1fr) auto 32px; gap: var(--space-xs); }
  .record-index-row:first-of-type { border-top: 0; }
  .index-main { grid-column: 1; grid-row: 1 / span 2; }
  .record-index-row time { grid-column: 2; grid-row: 1; }
  .index-actions { grid-column: 3; grid-row: 1; }
  .index-practice { grid-column: 1 / -1; }
  .index-tags { grid-column: 1 / -1; }
  .mobile-label { display: inline; }
}
@media (prefers-reduced-motion: reduce) { .record-index-row { transition: none; } }
</style>
