<script setup lang="ts">
import { MoreFilled } from '@element-plus/icons-vue'
import ExpandableText from '../ExpandableText.vue'
import type { ActivityRecord } from '../../types/explorationActivity'
import { formatDate } from '../../utils/formatDate'
defineProps<{ record: ActivityRecord; showMethod?: boolean; readonly?: boolean }>()
const emit = defineEmits<{ edit: []; assign: []; organize: []; delete: [] }>()
</script>
<template>
  <article class="finding-row">
    <div class="finding-content">
      <div class="metadata"><time :datetime="record.occurredAt ?? record.createdAt"><template v-if="!showMethod">發現 · </template>{{ formatDate(record.occurredAt ?? record.createdAt) }}</time><span v-if="showMethod" class="record-context">探索線：{{ record.threadName || '未設定' }}</span></div>
      <div v-if="showMethod" class="method-label"><span class="method-prefix">行動</span><ExpandableText v-if="record.tryText" :content="record.tryText" :lines="2" /><span v-else>未指定行動</span></div>
      <ExpandableText :content="record.discovery" :lines="6" />
    </div>
    <el-dropdown v-if="!readonly" trigger="click"><el-button text circle size="small" :icon="MoreFilled" aria-label="發現操作" />
      <template #dropdown><el-dropdown-menu>
        <el-dropdown-item @click="emit('edit')">編輯發現</el-dropdown-item>
        <el-dropdown-item @click="emit('assign')">調整歸屬</el-dropdown-item>
        <el-dropdown-item @click="emit('organize')">整理紀錄</el-dropdown-item>
        <el-dropdown-item divided @click="emit('delete')">刪除發現</el-dropdown-item>
      </el-dropdown-menu></template>
    </el-dropdown>
  </article>
</template>
<style scoped>
.finding-row { display: flex; align-items: flex-start; gap: var(--space-xs); padding-block: var(--space-sm); min-width: 0; }
.finding-content { flex: 1; min-width: 0; overflow-wrap: anywhere; }
.metadata { display: flex; flex-wrap: wrap; align-items: baseline; gap: var(--space-2xs) var(--space-sm); color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.record-context { overflow-wrap: anywhere; }
.method-label { display: grid; grid-template-columns: auto minmax(0, 1fr); align-items: baseline; gap: var(--space-xs); margin: var(--space-xs) 0; font-weight: var(--weight-semibold); }
.method-prefix { color: var(--el-text-color-secondary); font-size: var(--type-meta); font-weight: normal; }
</style>
