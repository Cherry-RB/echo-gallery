<script setup lang="ts">
import { ArrowDown, ArrowUp, Delete, Plus, Sort } from '@element-plus/icons-vue'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppDialog from '../AppDialog.vue'
import { experimentApi } from '../../utils/api/experimentApi'
import type { ExperimentTopicDto } from '../../types/experiment'

const props = defineProps<{ modelValue: boolean; experimentId: number }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
const client = useQueryClient()
const name = ref('')
const editingTopicId = ref<number | null>(null)
const editingName = ref('')
const ordered = ref<ExperimentTopicDto[]>([])
const draggingIndex = ref<number | null>(null)
const dropTargetIndex = ref<number | null>(null)
const dropAfter = ref(false)
let activePointerId: number | null = null
const topics = useQuery({ queryKey: computed(() => ['experiment-topics', props.experimentId]), queryFn: () => experimentApi.getTopics(props.experimentId) })
const refresh = async () => { await client.invalidateQueries({ queryKey: ['experiment-topics', props.experimentId] }) }
watch(() => topics.data.value, value => { ordered.value = value ? [...value] : [] }, { immediate: true })
const nextSlot = computed(() => [1, 2, 3].find(slot => !ordered.value.some(topic => topic.pinnedSlot === slot)))
const hasOrderChange = computed(() => ordered.value.map(topic => topic.id).join(',') !== (topics.data.value ?? []).map(topic => topic.id).join(','))
const add = useMutation({ mutationFn: () => experimentApi.createTopic(props.experimentId, name.value.trim()), onSuccess: async () => { name.value = ''; await refresh() }, onError: () => ElMessage.error('無法新增主題。') })
const rename = useMutation({
  mutationFn: ({ id, name }: { id: number; name: string }) => experimentApi.renameTopic(props.experimentId, id, name),
  onSuccess: async () => { editingTopicId.value = null; editingName.value = ''; await refresh() },
  onError: () => ElMessage.error('無法更新主題名稱。'),
})
const pin = useMutation({ mutationFn: ({ id, slot }: { id: number; slot: number }) => experimentApi.pinTopic(props.experimentId, id, slot), onSuccess: refresh, onError: () => ElMessage.error('無法更新釘選主題。') })
const reorder = useMutation({ mutationFn: () => experimentApi.reorderTopics(props.experimentId, ordered.value.map(topic => topic.id)), onSuccess: async () => { await refresh(); ElMessage.success('已更新主題排序。') }, onError: () => ElMessage.error('無法更新主題排序。') })
const remove = async (topic: ExperimentTopicDto) => {
  try { await ElMessageBox.confirm(`刪除「${topic.name}」不會刪除探索紀錄，只會移除主題歸屬。`, '刪除主題', { type: 'warning', confirmButtonText: '刪除主題', cancelButtonText: '取消' }) } catch { return }
  try { await experimentApi.deleteTopic(props.experimentId, topic.id); await refresh() } catch { ElMessage.error('無法刪除主題。') }
}
const move = (index: number, offset: number) => {
  const target = index + offset
  if (target < 0 || target >= ordered.value.length) return
  const next = [...ordered.value]
  ;[next[index], next[target]] = [next[target]!, next[index]!]
  ordered.value = next
}
const startRename = (topic: ExperimentTopicDto) => { editingTopicId.value = topic.id; editingName.value = topic.name }
const cancelRename = () => { editingTopicId.value = null; editingName.value = '' }
const saveRename = (topic: ExperimentTopicDto) => {
  const value = editingName.value.trim()
  if (!value || value === topic.name || rename.isPending.value) { cancelRename(); return }
  rename.mutate({ id: topic.id, name: value })
}
const clearDrag = () => {
  draggingIndex.value = null
  dropTargetIndex.value = null
  dropAfter.value = false
  activePointerId = null
  window.removeEventListener('pointermove', moveDrag)
  window.removeEventListener('pointerup', finishDrag)
  window.removeEventListener('pointercancel', cancelDrag)
}
const updateDropTarget = (event: PointerEvent) => {
  const target = document.elementFromPoint(event.clientX, event.clientY)?.closest<HTMLElement>('[data-topic-index]')
  if (!target) return
  const targetIndex = Number(target.dataset.topicIndex)
  if (!Number.isInteger(targetIndex) || targetIndex === draggingIndex.value) return
  const rect = target.getBoundingClientRect()
  dropTargetIndex.value = targetIndex
  dropAfter.value = event.clientY > rect.top + rect.height / 2
}
const applyDrop = (targetIndex: number, after: boolean) => {
  const sourceIndex = draggingIndex.value
  if (sourceIndex == null || sourceIndex === targetIndex) return
  const next = [...ordered.value]
  const [topic] = next.splice(sourceIndex, 1)
  if (!topic) return
  let destination = targetIndex + (after ? 1 : 0)
  if (sourceIndex < destination) destination--
  next.splice(destination, 0, topic)
  ordered.value = next
}
const startDrag = (event: PointerEvent, index: number) => {
  if (reorder.isPending.value || (event.pointerType === 'mouse' && event.button !== 0)) return
  event.preventDefault()
  activePointerId = event.pointerId
  draggingIndex.value = index
  ;(event.currentTarget as HTMLElement).setPointerCapture?.(event.pointerId)
  window.addEventListener('pointermove', moveDrag)
  window.addEventListener('pointerup', finishDrag)
  window.addEventListener('pointercancel', cancelDrag)
}
const moveDrag = (event: PointerEvent) => {
  if (event.pointerId !== activePointerId) return
  updateDropTarget(event)
}
const finishDrag = (event: PointerEvent) => {
  if (event.pointerId !== activePointerId) return
  const targetIndex = dropTargetIndex.value
  const after = dropAfter.value
  if (targetIndex != null) applyDrop(targetIndex, after)
  clearDrag()
}
const cancelDrag = (event: PointerEvent) => {
  if (event.pointerId === activePointerId) clearDrag()
}
onBeforeUnmount(clearDrag)
</script>

<template>
  <AppDialog :model-value="modelValue" title="管理主題" pattern="compact-form" scroll-body @update:model-value="emit('update:modelValue', $event)">
    <p class="dialog-intro">調整左側主題目錄的順序，最多可釘選三個最常使用的主題。</p>
    <div class="topic-add"><el-input v-model="name" maxlength="100" placeholder="新增主題名稱" @keyup.enter="name.trim() && add.mutate()" /><el-button type="primary" :icon="Plus" :disabled="!name.trim()" :loading="add.isPending.value" @click="add.mutate()">新增</el-button></div>
    <p v-if="topics.isPending.value" class="dialog-hint">正在載入主題…</p>
    <p v-else-if="topics.isError.value" class="dialog-hint">無法載入主題。</p>
    <ol v-else class="topic-list">
      <li v-for="(topic, index) in ordered" :key="topic.id" :class="{ 'is-dragging': draggingIndex === index, 'drop-before': dropTargetIndex === index && !dropAfter, 'drop-after': dropTargetIndex === index && dropAfter }" :data-topic-index="index">
        <button type="button" class="drag-handle" :aria-label="`拖曳調整 ${topic.name} 的順序`" :disabled="reorder.isPending.value" @pointerdown="startDrag($event, index)"><el-icon><Sort /></el-icon></button>
        <el-input v-if="editingTopicId === topic.id" v-model="editingName" class="topic-name-input" maxlength="100" :disabled="rename.isPending.value" @keyup.enter="saveRename(topic)" @keyup.escape="cancelRename" />
        <span v-else class="topic-name">{{ topic.name }}</span>
        <span v-if="topic.pinnedSlot" class="topic-state">已釘選</span>
        <div class="topic-actions">
          <template v-if="editingTopicId === topic.id">
            <el-button text type="primary" :loading="rename.isPending.value" @click="saveRename(topic)">儲存</el-button>
            <el-button text :disabled="rename.isPending.value" @click="cancelRename">取消</el-button>
          </template>
          <el-button v-else text type="primary" @click="startRename(topic)">改名</el-button>
          <el-button text circle :icon="ArrowUp" :disabled="index === 0" :aria-label="`上移 ${topic.name}`" @click="move(index, -1)" />
          <el-button text circle :icon="ArrowDown" :disabled="index === ordered.length - 1" :aria-label="`下移 ${topic.name}`" @click="move(index, 1)" />
          <el-button v-if="topic.pinnedSlot" text type="primary" @click="pin.mutate({ id: topic.id, slot: 0 })">取消釘選</el-button>
          <el-button v-else text type="primary" :disabled="!nextSlot" @click="pin.mutate({ id: topic.id, slot: nextSlot! })">釘選</el-button>
          <el-button text type="danger" :icon="Delete" :aria-label="`刪除 ${topic.name}`" @click="remove(topic)" />
        </div>
      </li>
    </ol>
    <template #footer="{ requestClose }"><el-button @click="requestClose">關閉</el-button><el-button type="primary" :disabled="!hasOrderChange" :loading="reorder.isPending.value" @click="reorder.mutate()">儲存排序</el-button></template>
  </AppDialog>
</template>

<style scoped>
.dialog-intro, .dialog-hint { margin: 0 0 var(--space-md); color: var(--el-text-color-secondary); font-size: var(--type-secondary); line-height: var(--leading-ui); }
.topic-add { display: flex; gap: var(--space-sm); margin-bottom: var(--space-md); }
.topic-list { display: grid; gap: var(--space-xs); margin: 0; padding: 0; list-style: none; }
.topic-list li { position: relative; display: flex; align-items: center; gap: var(--space-sm); min-width: 0; padding: var(--space-xs); border: 1px solid var(--el-border-color-lighter); border-radius: var(--radius-sm); background: var(--el-bg-color); transition: opacity .15s ease, border-color .15s ease, background-color .15s ease; }
.topic-list li:focus-within, .topic-list li:hover { border-color: var(--el-color-primary-light-7); }
.topic-list li.is-dragging { opacity: .55; border-color: var(--el-color-primary); background: var(--el-color-primary-light-9); }
.topic-list li.drop-before::before, .topic-list li.drop-after::after { position: absolute; right: 0; left: 0; height: 2px; border-radius: 999px; background: var(--el-color-primary); content: ''; }
.topic-list li.drop-before::before { top: -2px; }
.topic-list li.drop-after::after { bottom: -2px; }
.drag-handle { display: inline-grid; flex: 0 0 auto; width: 30px; height: 30px; place-items: center; padding: 0; border: 0; border-radius: var(--radius-sm); background: transparent; color: var(--el-text-color-secondary); cursor: grab; touch-action: none; }
.drag-handle:hover, .drag-handle:focus-visible { background: var(--el-fill-color-light); color: var(--el-color-primary); }
.drag-handle:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 1px; }
.drag-handle:active { cursor: grabbing; }
.topic-name { flex: 1; min-width: 0; overflow-wrap: anywhere; color: var(--el-text-color-primary); font-size: var(--type-secondary); }
.topic-name-input { flex: 1; min-width: 0; }
.topic-state { color: var(--el-color-primary); font-size: var(--type-meta); white-space: nowrap; }
.topic-actions { display: flex; align-items: center; gap: var(--space-2xs); }
@media (max-width: 640px) { .topic-list li { flex-wrap: wrap; } .topic-actions { width: 100%; justify-content: flex-end; } }
</style>
