<script setup lang="ts">
import type { TagDto } from '../../types/tag'
const search = defineModel<string>('search', { required: true })
defineProps<{ tags: TagDto[]; selected: string[] }>()
defineEmits<{ toggle: [name: string]; add: [] }>()
</script>

<template>
  <div class="tag-selection">
    <div class="tag-search"><el-input v-model="search" placeholder="加上標籤或搜尋..." size="small" clearable @keyup.enter="$emit('add')" /><el-button type="primary" size="small" @click="$emit('add')">新增</el-button></div>
    <p>既有標籤（點選切換）</p>
    <div class="tag-options"><button v-for="tag in tags" :key="tag.id" type="button" :class="{ selected: selected.includes(tag.name) }" :aria-pressed="selected.includes(tag.name)" @click="$emit('toggle', tag.name)">{{ tag.name }}</button><span v-if="!tags.length">尚無符合的既有標籤</span></div>
  </div>
</template>

<style scoped>
.tag-search { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: var(--space-xs); }
p { margin: var(--space-sm) 0 var(--space-xs); color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.tag-options { display: flex; flex-wrap: wrap; gap: var(--space-xs); }
.tag-options button { padding: var(--space-2xs) var(--space-xs); border: 1px solid var(--el-border-color); border-radius: var(--radius-sm); background: transparent; color: var(--el-text-color-regular); font: inherit; font-size: var(--type-meta); cursor: pointer; }
.tag-options button:hover, .tag-options button.selected { border-color: var(--el-color-primary-light-5); background: var(--el-color-primary-light-9); color: var(--el-color-primary); }
.tag-options button:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 2px; }
.tag-options > span { color: var(--el-text-color-secondary); font-size: var(--type-meta); }
</style>
