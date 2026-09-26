<script setup lang="ts">
import { MoreFilled } from '@element-plus/icons-vue'
import type { ExperimentCardDto, ExperimentStage } from '../../types/experiment'

defineProps<{
  card: ExperimentCardDto
  stages: Array<{ value: ExperimentStage; title: string }>
  comparing?: boolean
}>()

const emit = defineEmits<{
  open: [cardId: number]
  changeStage: [card: ExperimentCardDto, stage: ExperimentStage]
  editNote: [card: ExperimentCardDto]
  remove: [card: ExperimentCardDto]
  toggleCompare: [card: ExperimentCardDto]
}>()

const handleCommand = (card: ExperimentCardDto, command: string) => {
  if (command === 'note') emit('editNote', card)
  if (command === 'remove') emit('remove', card)
  if (command.startsWith('stage:')) {
    emit('changeStage', card, command.slice(6) as ExperimentStage)
  }
}
</script>

<template>
  <article class="experiment-card-item">
    <div class="experiment-card-main">
      <button type="button" class="experiment-card-title" @click="emit('open', card.cardId)">
        {{ card.cardTitle }}
      </button>

      <p v-if="card.cardSummary" class="experiment-card-excerpt"><span>內容重點</span>{{ card.cardSummary }}</p>
      <p v-if="card.cardReason" class="experiment-card-excerpt"><span>留下原因</span>{{ card.cardReason }}</p>

      <div class="experiment-card-metadata">
        <span class="card-id">#{{ card.cardId }}</span>
        <span>{{ card.cardType === 'link' ? '連結' : '筆記' }}</span>
        <el-tag v-if="card.cardArchived" size="small" type="info">已封存</el-tag>
        <el-tag v-if="card.intervalDays == null" size="small" type="info" effect="plain">暫停回流</el-tag>
        <el-tag v-if="card.needsProcessing" size="small" type="warning" effect="plain">待整理</el-tag>
      </div>

      <div v-if="card.cardTags.length" class="experiment-card-tags">
        <el-tag v-for="tag in card.cardTags" :key="tag" size="small" effect="plain">
          {{ tag }}
        </el-tag>
      </div>

      <p v-if="card.note" class="experiment-card-note">{{ card.note }}</p>
      <button type="button" class="compare-toggle" :aria-pressed="comparing" @click="emit('toggleCompare', card)">
        {{ comparing ? '移出比較' : '拿來比較' }}
      </button>
    </div>

    <el-dropdown trigger="click" @command="handleCommand(card, $event)">
      <el-button text circle :icon="MoreFilled" aria-label="卡片操作" @click.stop />
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item
            v-for="stage in stages"
            :key="stage.value"
            :command="`stage:${stage.value}`"
            :disabled="stage.value === card.stage"
          >
            移植至{{ stage.title }}
          </el-dropdown-item>
          <el-dropdown-item command="note" divided>編輯備註</el-dropdown-item>
          <el-dropdown-item command="remove" divided>移除</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </article>
</template>

<style scoped>
.experiment-card-item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 7px;
  background: var(--el-bg-color);
}

.experiment-card-item + .experiment-card-item {
  margin-top: 10px;
}

.experiment-card-main {
  min-width: 0;
}

.experiment-card-excerpt { display: -webkit-box; margin: 10px 0 0; overflow: hidden; color: var(--el-text-color-regular); font-size: var(--type-caption); line-height: var(--leading-ui); overflow-wrap: anywhere; white-space: pre-line; -webkit-box-orient: vertical; -webkit-line-clamp: 3; }
.experiment-card-excerpt span { flex: 0 0 auto; margin-right: 8px; color: var(--el-text-color-placeholder); }
.compare-toggle { margin-top: 12px; padding: 0; border: 0; background: none; color: var(--el-color-primary); font: inherit; font-size: var(--type-caption); cursor: pointer; }
.compare-toggle:hover, .compare-toggle:focus-visible { text-decoration: underline; }

.experiment-card-title {
  max-width: 100%;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--el-text-color-primary);
  font: inherit;
  font-weight: 600;
  line-height: 1.5;
  text-align: left;
  overflow-wrap: anywhere;
  cursor: pointer;
}

.experiment-card-title:hover,
.experiment-card-title:focus-visible {
  color: var(--el-color-primary);
}

.experiment-card-metadata {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px 10px;
  margin-top: 7px;
  color: var(--el-text-color-placeholder);
  font-size: var(--type-meta);
}

.card-id {
  font-variant-numeric: tabular-nums;
}

.experiment-card-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 9px;
}

.experiment-card-note {
  margin: 10px 0 0;
  color: var(--el-text-color-secondary);
  font-size: var(--type-caption);
  line-height: var(--leading-ui);
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}
</style>
