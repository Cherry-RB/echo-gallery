<script setup lang="ts">
import { MoreFilled } from '@element-plus/icons-vue'
import type { ExperimentRecordDto } from '../../types/experiment'

const props = defineProps<{ record: ExperimentRecordDto; selectedTopicId?: number }>()
const emit = defineEmits<{
  read: [record: ExperimentRecordDto]
  edit: [record: ExperimentRecordDto]
  repeat: [record: ExperimentRecordDto]
  remove: [record: ExperimentRecordDto]
}>()

const heading = () => props.record.title || '探索紀錄'
const visibleTopics = () => (props.record.topics ?? []).filter(topic => topic.id !== props.selectedTopicId).slice(0, 1)
const command = (value: string) => {
  if (value === 'edit') emit('edit', props.record)
  else if (value === 'repeat') emit('repeat', props.record)
  else emit('remove', props.record)
}
</script>

<template>
  <article
    class="record-card app-collection-card"
    tabindex="0"
    role="button"
    :aria-label="`閱讀探索紀錄：${heading()}`"
    @click="emit('read', record)"
    @keydown.enter.self="emit('read', record)"
    @keydown.space.self.prevent="emit('read', record)"
  >
    <div class="record-card-heading">
      <h3>{{ heading() }}</h3>
      <div class="record-card-actions">
        <el-dropdown trigger="click" @click.stop @keydown.stop @command="command">
          <el-button text circle :icon="MoreFilled" aria-label="探索紀錄操作" @click.stop />
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="repeat">再記一筆</el-dropdown-item>
              <el-dropdown-item command="edit">編輯</el-dropdown-item>
              <el-dropdown-item command="delete" divided>刪除</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>
    <p v-if="record.practiceText" class="practice">做法｜{{ record.practiceText }}</p>
    <p v-if="record.content" class="content">{{ record.content }}</p>
    <footer class="record-footer">
      <div class="record-tags">
        <el-tag v-for="topic in visibleTopics()" :key="topic.id" size="small" type="info">{{ topic.name }}</el-tag>
        <el-tag v-if="record.understandingNode" size="small">理解節點</el-tag>
        <el-tag v-if="record.methodCandidate" size="small">方法備選</el-tag>
        <el-tag v-if="record.highlight !== 'GENERAL'" size="small" type="success">
          {{ record.highlight === 'GAIN' ? '有所收穫' : record.highlight === 'CHALLENGE_OVERCOME' ? '克服困難' : '高峰體驗' }}
        </el-tag>
      </div>
      <time :datetime="record.recordedOn">{{ record.recordedOn }}</time>
    </footer>
  </article>
</template>

<style scoped>
.record-card { display: flex; min-width: 0; flex-direction: column; align-self: start; gap: var(--space-xs); padding: var(--space-md); border: 1px solid var(--el-border-color); border-radius: var(--radius-md); background: var(--el-bg-color); cursor: pointer; transition: transform .2s ease, box-shadow .2s ease, border-color .2s ease; }
.record-card:hover, .record-card:focus-within { transform: translateY(-2px); border-color: var(--el-color-primary-light-5); box-shadow: var(--el-box-shadow-lighter); }
.record-card:active { transform: translateY(0); box-shadow: none; }
.record-card:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 2px; }
.record-card-heading, .record-card-actions, .record-tags { display: flex; gap: var(--space-sm); }
.record-card-heading { align-items: flex-start; justify-content: space-between; }
.record-card h3 { display: -webkit-box; min-width: 0; margin: 0; overflow: hidden; color: var(--el-text-color-primary); font-size: var(--type-body); font-weight: var(--weight-semibold); line-height: var(--leading-ui); overflow-wrap: anywhere; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.record-card-actions { flex: 0 0 auto; align-items: center; }
.record-card time { color: var(--el-text-color-secondary); font-size: var(--type-meta); white-space: nowrap; }
.practice, .content { display: -webkit-box; margin: 0; overflow: hidden; overflow-wrap: anywhere; -webkit-box-orient: vertical; }
.practice { color: var(--el-text-color-secondary); font-size: var(--type-secondary); line-height: var(--leading-ui); -webkit-line-clamp: 2; }
.content { color: var(--el-text-color-regular); font-size: var(--type-secondary); line-height: var(--leading-ui); -webkit-line-clamp: 5; }
.record-footer { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-2xs) var(--space-xs); }
.record-footer time { margin-left: auto; }
.record-tags { align-items: center; flex-wrap: wrap; }
@media (prefers-reduced-motion: reduce) { .record-card { transition: none; }.record-card:hover, .record-card:focus-within { transform: none; } }
</style>
