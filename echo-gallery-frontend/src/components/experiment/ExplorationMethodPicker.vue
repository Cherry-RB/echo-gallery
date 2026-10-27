<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import PickerOption from '../ui/PickerOption.vue'
import type { MethodOption, ActivityState } from '../../types/explorationActivity'
import { explorationActivityApi as api } from '../../utils/api/explorationActivityApi'
import { apiErrorMessage } from '../../utils/api/errorMessage'
const props = withDefaults(defineProps<{ experimentId: number; modelValue: number | null; threadId?: number | null; disabled?: boolean; showShortcuts?: boolean }>(), { showShortcuts: true })
const emit = defineEmits<{ select: [method: MethodOption]; 'settings-changed': [state: ActivityState] }>()
const client = useQueryClient()
const keyword = ref(''), page = ref(1), saving = ref(false)
const state = useQuery({ queryKey: computed(() => ['experiment-exploration', props.experimentId]), queryFn: () => api.state(props.experimentId) })
const methods = useQuery({
  queryKey: computed(() => ['experiment-methods', props.experimentId, props.threadId, keyword.value, page.value]),
  queryFn: () => api.methods(props.experimentId, { threadId: props.threadId, keyword: keyword.value, page: page.value - 1 }),
})
watch([keyword, () => props.threadId], () => { page.value = 1 })
const recent = computed(() => (state.data.value?.recentMethods ?? []).filter(m => props.threadId == null || m.threadId === props.threadId))
const favorites = computed(() => (state.data.value?.favoriteMethods ?? []).filter(m => props.threadId == null || m.threadId === props.threadId))
const isFavorite = (id: number) => state.data.value?.favoriteMethods.some(m => m.id === id) ?? false
const toggleFavorite = async (method: MethodOption) => {
  if (saving.value || !state.data.value) return
  const ids = state.data.value.favoriteMethods.map(m => m.id)
  if (!ids.includes(method.id) && ids.length >= 3) { ElMessage.info('最多 3 筆常用行動，請先移除一筆。'); return }
  saving.value = true
  try {
    const result = await api.favorites(props.experimentId, ids.includes(method.id) ? ids.filter(id => id !== method.id) : [...ids, method.id], state.data.value.version)
    client.setQueryData(['experiment-exploration', props.experimentId], result)
    emit('settings-changed', result)
    ElMessage.success('常用行動已儲存')
  } catch (e) { ElMessage.error(apiErrorMessage(e, '儲存常用行動失敗')); void state.refetch() }
  finally { saving.value = false }
}
</script>
<template>
  <div class="method-picker">
    <template v-if="showShortcuts && !keyword">
      <section v-if="recent.length"><h4>最近用過</h4><PickerOption v-for="method in recent" :key="method.id" :title="method.content" :selected="modelValue === method.id" :disabled="disabled" @select="emit('select', method)">{{ method.threadName }}</PickerOption></section>
      <section v-if="favorites.length"><h4>常用行動</h4><PickerOption v-for="method in favorites" :key="method.id" :title="method.content" :selected="modelValue === method.id" :disabled="disabled" @select="emit('select', method)">{{ method.threadName }}</PickerOption></section>
    </template>
    <section><h4>行動備選</h4>
      <el-input v-model="keyword" :disabled="disabled" clearable placeholder="搜尋行動備選" aria-label="搜尋行動備選" />
      <el-skeleton v-if="methods.isPending.value" :rows="2" animated />
      <p v-else-if="methods.isError.value">無法載入行動。<el-button type="primary" link @click="methods.refetch()">重試</el-button></p>
      <div v-else class="options"><PickerOption v-for="method in methods.data.value?.content ?? []" :key="method.id" :title="method.content" :selected="modelValue === method.id" :disabled="disabled" @select="emit('select', method)">
        {{ method.threadName }}<template #actions><el-button type="primary" link size="small" :loading="saving" :disabled="disabled || state.isError.value" @click="toggleFavorite(method)">{{ isFavorite(method.id) ? '移除常用' : '設為常用' }}</el-button></template>
      </PickerOption><p v-if="!methods.data.value?.content.length" class="metadata">尚無符合的行動，可自行建立。</p></div>
      <el-pagination v-if="(methods.data.value?.totalPages ?? 0) > 1" v-model:current-page="page" :page-size="20" :total="methods.data.value?.totalElements ?? 0" layout="prev, pager, next" />
    </section>
  </div>
</template>
<style scoped>
.method-picker, .method-picker section, .options { display: grid; gap: var(--space-xs); min-width: 0; }
.method-picker { gap: var(--space-md); } h4 { margin: 0; font-size: var(--type-caption); color: var(--el-text-color-secondary); }
.metadata { margin: 0; color: var(--el-text-color-secondary); }
</style>
