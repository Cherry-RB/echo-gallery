<script setup lang="ts">
import { ChatLineSquare, MoreFilled } from '@element-plus/icons-vue'
import type { ExplorationThreadCardDto } from '../../types/experiment'

defineProps<{ card: ExplorationThreadCardDto; result?: boolean }>()
defineEmits<{ read: []; remove: []; note: [] }>()
</script>

<template>
  <article class="thread-card" role="button" tabindex="0" :aria-label="'閱讀原文：' + card.cardTitle" @click="$emit('read')" @keydown.enter.self.prevent="$emit('read')" @keydown.space.self.prevent="$emit('read')">
    <header class="thread-card-heading">
      <span class="thread-card-title">{{ card.cardTitle }}</span>
      <div class="thread-card-tools" @click.stop @keydown.stop>
        <el-tooltip v-if="card.note" :content="card.note" popper-class="content-tooltip" :show-after="300">
          <button type="button" class="note-trigger" :aria-label="'查看或編輯備註：' + card.note" @click="$emit('note')"><el-icon><ChatLineSquare /></el-icon></button>
        </el-tooltip>
        <span class="thread-card-meta">#{{ card.cardId }} · {{ card.cardType === 'link' ? '連結' : '筆記' }}{{ card.cardArchived ? ' · 已封存' : '' }}</span>
        <el-dropdown trigger="click">
          <el-button text circle size="small" :icon="MoreFilled" aria-label="卡片操作" />
          <template #dropdown><el-dropdown-menu>
            <el-dropdown-item @click="$emit('note')">編輯備註</el-dropdown-item>
            <el-dropdown-item divided @click="$emit('remove')">{{ result ? '解除關聯' : '移除材料' }}</el-dropdown-item>
          </el-dropdown-menu></template>
        </el-dropdown>
      </div>
    </header>
    <p v-if="card.cardNote" class="thread-card-excerpt">{{ card.cardNote }}</p>
  </article>
</template>

<style scoped>
.thread-card { min-width: 0; padding: var(--space-sm); border: 1px solid var(--el-border-color-lighter); border-radius: var(--radius-md); background: var(--el-bg-color); cursor: pointer; }
.thread-card:hover { border-color: var(--el-color-primary-light-5); }
.thread-card:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 2px; }
.thread-card-heading { display: flex; flex-wrap: wrap; align-items: flex-start; gap: var(--space-2xs) var(--space-xs); }
.thread-card-title { flex: 1 1 140px; min-width: 0; padding: 0; border: 0; background: transparent; color: var(--el-text-color-primary); font: inherit; font-size: var(--type-body); font-weight: var(--weight-semibold); line-height: var(--leading-ui); text-align: left; overflow-wrap: anywhere; cursor: pointer; }
.thread-card-title:hover { color: var(--el-color-primary); }
.thread-card-title:focus-visible, .note-trigger:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 2px; }
.thread-card-tools { display: flex; align-items: center; gap: var(--space-2xs); margin-left: auto; }
.thread-card-meta { color: var(--el-text-color-secondary); font-size: var(--type-meta); line-height: var(--leading-metadata); }
.note-trigger { display: inline-flex; padding: var(--space-2xs); border: 0; background: transparent; color: var(--el-text-color-secondary); cursor: help; }
.thread-card-excerpt { display: -webkit-box; margin: var(--space-xs) 0 0; overflow: hidden; color: var(--el-text-color-regular); font-size: var(--type-secondary); line-height: var(--leading-ui); white-space: pre-line; overflow-wrap: anywhere; -webkit-box-orient: vertical; -webkit-line-clamp: 3; }
</style>
