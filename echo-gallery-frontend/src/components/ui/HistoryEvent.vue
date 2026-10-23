<script setup lang="ts">
import { Delete, Edit, MoreFilled } from '@element-plus/icons-vue'
import { formatDate } from '../../utils/formatDate'

withDefaults(defineProps<{
  date: string
  latest?: boolean
  manageable?: boolean
  useForWork?: boolean
  useForThread?: boolean
  showTimeline?: boolean
  busy?: boolean
  dateFormat?: string
  datePrefix?: string
  operationLabel?: string
}>(), { dateFormat: 'YYYY/MM/DD HH:mm', operationLabel: '紀錄操作', showTimeline: true })

const emit = defineEmits<{ command: ['edit' | 'delete' | 'use-work' | 'use-thread'] }>()
</script>

<template>
  <li class="history-event" :class="{ 'history-event--without-timeline': !showTimeline }">
    <span v-if="showTimeline" class="timeline-marker" aria-hidden="true"></span>
    <article class="record-entry">
      <header class="record-header">
        <div class="record-time"><span v-if="latest" class="latest-label">最新</span><time :datetime="date">{{ datePrefix }}{{ formatDate(date, dateFormat) }}</time></div>
        <el-dropdown v-if="manageable || useForWork || useForThread" trigger="click" @command="emit('command', $event)">
          <button type="button" class="more-button" :disabled="busy" :aria-label="operationLabel"><el-icon><MoreFilled /></el-icon></button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-if="useForThread" command="use-thread">加入探索線</el-dropdown-item>
              <el-dropdown-item v-if="useForWork" command="use-work">用於作品</el-dropdown-item>
              <el-dropdown-item v-if="manageable" command="edit" :icon="Edit" :divided="useForWork || useForThread">編輯</el-dropdown-item>
              <el-dropdown-item v-if="manageable" command="delete" :icon="Delete">刪除</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </header>
      <slot />
      <div v-if="$slots.related" class="record-related"><slot name="related" /></div>
    </article>
  </li>
</template>

<style scoped>
.history-event { display: grid; grid-template-columns: 20px minmax(0, 1fr); gap: var(--space-sm); padding-bottom: var(--space-sm); }
.history-event--without-timeline { grid-template-columns: minmax(0, 1fr); padding-bottom: 0; }
.record-entry { min-width: 0; padding: var(--space-md); border-radius: var(--radius-md); border: 1px solid var(--el-border-color-lighter); background: var(--surface-summary); overflow-wrap: anywhere; }
.timeline-marker { position: relative; display: block; min-height: 100%; }
.timeline-marker::before { position: absolute; top: 10px; bottom: -12px; left: 9px; width: 1px; background: var(--el-border-color); content: ''; }
.timeline-marker::after { position: absolute; top: 6px; left: 5px; width: 7px; height: 7px; border: 2px solid var(--el-bg-color); border-radius: 50%; background: var(--experiment-accent, var(--el-color-primary)); box-shadow: 0 0 0 1px var(--el-color-primary-light-5); content: ''; }
.history-event:last-child .timeline-marker::before { bottom: auto; height: 8px; }
.record-header, .record-time { display: flex; align-items: center; }
.record-header { justify-content: space-between; gap: var(--space-md); }
.record-time { flex-wrap: wrap; gap: var(--space-xs); }
.record-time time { color: var(--el-text-color-placeholder); font-size: var(--type-meta); }
.latest-label { padding: var(--space-2xs) var(--space-xs); border-radius: var(--radius-sm); background: var(--el-color-primary-light-9); color: var(--el-color-primary); font-size: var(--type-meta); font-weight: var(--weight-semibold); }
.more-button { padding: var(--space-2xs); border: 0; background: transparent; color: var(--el-text-color-secondary); cursor: pointer; }
.more-button:focus-visible { outline: 2px solid var(--el-color-primary); }
.record-related { margin-top: var(--space-sm); padding-top: var(--space-xs); border-top: 1px solid var(--el-border-color-lighter); }
</style>
