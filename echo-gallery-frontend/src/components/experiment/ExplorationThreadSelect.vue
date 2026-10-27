<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { explorationActivityApi as api } from '../../utils/api/explorationActivityApi'
const props = withDefaults(defineProps<{ experimentId: number; modelValue: number | null; label?: string; disabled?: boolean; clearable?: boolean }>(), { clearable: true })
const emit = defineEmits<{ 'update:modelValue': [value: number | null]; 'label-change': [label: string] }>()
const keyword = ref(''), page = ref(1)
const query = useQuery({
  queryKey: computed(() => ['experiment-thread-options', props.experimentId, keyword.value, page.value]),
  queryFn: () => api.threadOptions(props.experimentId, { keyword: keyword.value, page: page.value - 1 }),
})
watch(keyword, () => { page.value = 1 })
watch(() => props.experimentId, () => { page.value = 1; keyword.value = '' })
const options = computed(() => query.data.value?.content ?? [])
watch([() => props.modelValue, options], () => {
  const selected = options.value.find(option => option.id === props.modelValue)
  if (selected) emit('label-change', selected.name)
})
const field = ref<{ focus: () => void }>()
defineExpose({ focus: () => field.value?.focus() })
</script>
<template>
  <div class="thread-select">
    <el-select ref="field" :model-value="modelValue" :disabled="disabled" :clearable="clearable" filterable remote :remote-method="(value: string) => keyword = value" :loading="query.isFetching.value" placeholder="未設定" aria-label="選擇探索線" @update:model-value="emit('update:modelValue', $event ?? null)">
      <el-option v-if="modelValue != null && !options.some(item => item.id === modelValue)" :value="modelValue" :label="label || '探索線 #' + modelValue" />
      <el-option v-for="thread in options" :key="thread.id" :value="thread.id" :label="thread.name" />
      <template #footer><el-pagination v-if="(query.data.value?.totalPages ?? 0) > 1" v-model:current-page="page" :page-size="20" :total="query.data.value?.totalElements ?? 0" layout="prev, pager, next" /><el-button v-if="query.isError.value" type="primary" link @click="query.refetch()">載入失敗，重試</el-button></template>
    </el-select>
  </div>
</template>
<style scoped>.thread-select, .thread-select :deep(.el-select) { width: 100%; }</style>
