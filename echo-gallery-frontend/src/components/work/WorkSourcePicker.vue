<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import AppDialog from '../AppDialog.vue'
import PickerOption from '../ui/PickerOption.vue'
import PickerWorkspace from '../ui/PickerWorkspace.vue'
import SelectionSummary from '../ui/SelectionSummary.vue'
import WorkSourceReadingDialog from './WorkSourceReadingDialog.vue'
import { useWorkDialogGuard } from '../../composables/useWorkDialogGuard'
import { workApi } from '../../utils/api/workApi'
import type { WorkSource } from '../../types/work'
const props = defineProps<{ modelValue: boolean; workId: number }>()
const emit = defineEmits<{ 'update:modelValue': [boolean]; saved: [] }>()
const kind = ref('CARD'), keyword = ref(''), experimentId = ref<number>(), page = ref(1)
const selected = reactive(new Map<string, WorkSource>()), reading = ref<WorkSource>()
const busy = ref(false), error = ref('')
const shell = ref<InstanceType<typeof AppDialog>>()
const draftBaseline = ref<string>()
const serializeDraft = () => JSON.stringify([...selected.keys()])
useWorkDialogGuard(shell, () => props.modelValue, () => {
  if (!reading.value) return false
  reading.value = undefined
  return true
})
const keyOf = (s: WorkSource) => s.cardId ? `c${s.cardId}` : `r${s.recordId}`
const metadata = (s: WorkSource) => [s.cardId ? '卡片 #' + s.cardId : '探索紀錄 #' + s.recordId, s.experimentTitle, s.archived ? '已封存' : ''].filter(Boolean).join(' · ')
function toggle(s: WorkSource) {
  if (busy.value) return
  if (selected.has(keyOf(s))) selected.delete(keyOf(s))
  else if (selected.size < 100) selected.set(keyOf(s), s)
}
const params = computed(() => ({ kind: kind.value, keyword: keyword.value, experimentId: experimentId.value, page: page.value - 1 }))
const query = useQuery({ queryKey: ['work-candidates', params], queryFn: () => workApi.candidates(params.value) })
const experiments = useQuery({ queryKey: ['work-experiments'], queryFn: workApi.experiments })
watch([kind, keyword, experimentId], () => { page.value = 1 })
watch(() => props.modelValue, async visible => {
  if (!visible) return
  await nextTick()
  if (props.modelValue) draftBaseline.value = serializeDraft()
}, { immediate: true })
async function save() {
  if (busy.value || !selected.size) return
  busy.value = true; error.value = ''
  try { await workApi.addSources(props.workId, [...selected.values()]); emit('saved'); emit('update:modelValue', false) }
  catch { error.value = '無法加入素材，選取內容已保留。' } finally { busy.value = false }
}
</script>
<template>
  <AppDialog v-if="!reading" ref="shell" :model-value="modelValue" title="加入參考素材" pattern="picker" width="min(980px, calc(100vw - 32px))" :draft="[...selected.keys()]" :draft-baseline="draftBaseline" :busy="busy" scroll-body @update:model-value="emit('update:modelValue', $event)">
    <div>
      <PickerWorkspace>
      <el-tabs v-model="kind" aria-label="素材種類"><el-tab-pane label="卡片" name="CARD" :disabled="busy" /><el-tab-pane label="探索紀錄" name="RECORD" :disabled="busy" /></el-tabs>
      <div class="source-search"><el-input v-model="keyword" :disabled="busy" :placeholder="kind === 'CARD' ? '搜尋卡片標題或 ID' : '搜尋試法或發現'" clearable aria-label="搜尋參考素材" /><el-select v-if="kind === 'RECORD'" :disabled="busy" v-model="experimentId" clearable placeholder="所有實驗" aria-label="篩選實驗"><el-option v-for="e in experiments.data.value" :key="e.id" :value="e.id" :label="e.title + (e.archived ? '（已封存）' : '')" /></el-select></div>
      <p class="source-rule">已選 {{ selected.size }} 筆。可跨搜尋與分頁選取，最多 100 筆；原始內容不會貼入正文。</p>
      <div class="picker-results">
      <p v-if="query.isPending.value">正在搜尋…</p>
      <el-alert v-else-if="query.isError.value" title="無法載入素材" type="error"><el-button @click="query.refetch()">重新載入</el-button></el-alert>
      <p v-else-if="!query.data.value?.content.length">找不到符合條件的素材。</p>
      <PickerOption v-for="s in query.data.value?.content" :key="keyOf(s)" :title="s.title" :selected="selected.has(keyOf(s))" multiple :disabled="busy || (selected.size >= 100 && !selected.has(keyOf(s)))" @select="toggle(s)">
        <span>{{ metadata(s) }}</span>
        <template #actions><el-button link type="primary" :disabled="busy" :aria-label="`閱讀全文：${s.title}`" @click="reading = s">閱讀全文</el-button></template>
      </PickerOption>
      </div>
      <el-pagination v-if="(query.data.value?.totalElements ?? 0) > 20" v-model:current-page="page" :disabled="busy" :page-size="20" :total="query.data.value?.totalElements ?? 0" layout="prev, pager, next" />
      <template #selection>
        <h3>已選素材（{{ selected.size }}）</h3>
        <div class="selected-sources">
          <SelectionSummary v-if="!selected.size" empty-text="從候選清單勾選素材，可一次加入多筆。" />
          <SelectionSummary v-for="s in selected.values()" :key="keyOf(s)" :title="s.title" :metadata="metadata(s)" hide-label>
            <template #actions><el-button link :disabled="busy" :aria-label="`移除已選素材 ${s.title}`" @click="selected.delete(keyOf(s))">移除</el-button></template>
          </SelectionSummary>
        </div>
      </template>
      </PickerWorkspace>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <template #footer="{ requestClose }"><el-button :disabled="busy" @click="requestClose">取消</el-button><el-button type="primary" :disabled="!selected.size" :loading="busy" @click="save">加入 {{ selected.size }} 筆素材</el-button></template>
  </AppDialog>
  <WorkSourceReadingDialog v-else :model-value="true" :source="reading" close-label="返回選擇" @update:model-value="reading = undefined" />
</template>
<style scoped>
.source-search { display: flex; gap: var(--space-sm); margin-block: var(--space-md); flex-wrap: wrap; }
.source-search > * { flex: 1; min-width: min(100%, 180px); }
.source-rule { color: var(--el-text-color-secondary); font-size: var(--type-secondary); line-height: var(--leading-ui); }
.selected-sources { display: grid; gap: var(--space-sm); }
.el-pagination { margin-top: var(--space-md); flex-shrink: 0; }
</style>
