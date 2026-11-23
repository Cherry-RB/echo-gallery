<script setup lang="ts">
import { computed, ref } from 'vue'
import { Search, Setting } from '@element-plus/icons-vue'
import type { ExperimentTopicDto } from '../../types/experiment'

const props = defineProps<{
  topics: ExperimentTopicDto[]
  selectedIds?: number[]
  unclassified?: boolean
  loading?: boolean
  error?: boolean
}>()
const emit = defineEmits<{ all: []; unclassified: []; select: [id: number]; manage: []; retry: [] }>()
const search = ref('')
const matchingTopics = computed(() => props.topics.filter(topic => topic.name.toLocaleLowerCase().includes(search.value.trim().toLocaleLowerCase())))
// 釘選決定分組，組內順序沿用後端保存的顯示順序。
const pinned = computed(() => matchingTopics.value.filter(topic => topic.pinnedSlot != null))
const others = computed(() => matchingTopics.value.filter(topic => topic.pinnedSlot == null))
const isSelected = (id: number) => props.selectedIds?.length === 1 && props.selectedIds[0] === id
</script>

<template>
  <nav class="topic-directory" aria-label="主題目錄">
    <header class="directory-heading">
      <h3>主題</h3>
      <el-button link type="primary" size="small" :icon="Setting" aria-label="管理主題" @click="emit('manage')">管理</el-button>
    </header>
    <el-input v-model="search" class="directory-search" size="small" :prefix-icon="Search" clearable placeholder="搜尋主題" aria-label="搜尋主題名稱" />
    <div class="directory-shortcuts">
      <button type="button" class="topic-link" :class="{ active: !selectedIds?.length && !unclassified }" :aria-current="!selectedIds?.length && !unclassified ? 'true' : undefined" @click="emit('all')">全部</button>
      <button type="button" class="topic-link" :class="{ active: unclassified }" :aria-current="unclassified ? 'true' : undefined" @click="emit('unclassified')">未分類</button>
    </div>
    <p v-if="loading" class="directory-message" role="status">正在載入主題…</p>
    <div v-else-if="error" class="directory-message" role="alert">無法載入主題。<el-button link type="primary" @click="emit('retry')">重試</el-button></div>
    <template v-else>
      <section v-if="pinned.length" class="directory-group" aria-label="釘選主題">
        <h4>釘選主題</h4>
        <button v-for="topic in pinned" :key="topic.id" type="button" class="topic-link" :class="{ active: isSelected(topic.id) }" :aria-current="isSelected(topic.id) ? 'true' : undefined" @click="emit('select', topic.id)">{{ topic.name }}</button>
      </section>
      <section v-if="others.length" class="directory-group directory-other" aria-label="其他主題">
        <h4>其他主題</h4>
        <div class="directory-scroll" tabindex="0" aria-label="其他主題清單">
          <button v-for="topic in others" :key="topic.id" type="button" class="topic-link" :class="{ active: isSelected(topic.id) }" :aria-current="isSelected(topic.id) ? 'true' : undefined" @click="emit('select', topic.id)">{{ topic.name }}</button>
        </div>
      </section>
      <p v-if="!matchingTopics.length" class="directory-message">{{ search.trim() ? '沒有符合名稱的主題。' : '尚未建立主題。' }}</p>
    </template>
  </nav>
</template>

<style scoped>
.topic-directory { display: flex; flex-direction: column; min-width: 0; max-height: min(640px, calc(100dvh - var(--topic-sticky-top, 72px) - 48px)); }
.topic-directory > :not(.directory-other) { flex-shrink: 0; }
.directory-heading { display: flex; align-items: center; justify-content: space-between; gap: var(--space-xs); margin-bottom: var(--space-sm); }
.directory-heading h3 { margin: 0; color: var(--el-text-color-primary); font-size: var(--type-card-title); font-weight: var(--weight-semibold); line-height: var(--leading-card-title); }
.directory-search { margin-bottom: var(--space-sm); }
.directory-shortcuts, .directory-group { display: grid; gap: var(--space-2xs); }
.directory-shortcuts { margin-bottom: var(--space-sm); }
.directory-scroll { display: grid; gap: var(--space-2xs); min-height: 0; overflow-y: auto; overscroll-behavior: contain; scrollbar-gutter: stable; }
.directory-scroll:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 2px; }
.directory-group h4 { display: flex; align-items: center; gap: var(--space-xs); margin: var(--space-xs) 0; color: var(--el-text-color-secondary); font-size: var(--type-meta); font-weight: var(--weight-medium); line-height: var(--leading-ui); }
.directory-group h4::after { content: ''; flex: 1; border-top: 1px solid var(--el-border-color-light); }
.directory-other { display: flex; flex-direction: column; min-height: 0; flex: 0 1 auto; }
.directory-other h4 { flex-shrink: 0; }
.directory-other:not(:first-child) { margin-top: var(--space-sm); }
.topic-link { position: relative; box-sizing: border-box; width: 100%; min-width: 0; border: 1px solid transparent; border-radius: var(--radius-sm); padding: var(--space-xs) var(--space-sm); background: transparent; color: var(--el-text-color-regular); font: inherit; font-size: var(--type-ui-label); line-height: var(--leading-ui); text-align: left; overflow-wrap: anywhere; cursor: pointer; }
.topic-link:hover { color: var(--el-color-primary); background: var(--el-fill-color-light); }
.topic-link.active { border-color: var(--el-color-primary-light-7); background: var(--el-color-primary-light-9); color: var(--el-color-primary); font-weight: var(--weight-semibold); }
.topic-link:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: -2px; }
.directory-message { margin: var(--space-xs) 0; color: var(--el-text-color-secondary); font-size: var(--type-secondary); line-height: var(--leading-ui); }
</style>
