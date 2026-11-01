<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useQueryClient } from '@tanstack/vue-query'
import { ArrowUp, ArrowDown } from '@element-plus/icons-vue'
import AppDialog from '../AppDialog.vue'
import { goalApi } from '../../utils/api/goalApi'
import type { GoalView } from '../../types/goal'
const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [boolean] }>()
const visible = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) })
const client = useQueryClient(), goals = ref<GoalView[]>([]), busy = ref(false), error = ref('')
let generation = 0
async function read() {
  const token = ++generation; const result: GoalView[] = []; let page = 0
  do {
    const batch = await goalApi.list({ pinned: true, archiveScope: 'UNARCHIVED', sort: 'pinOrder', page, size: 100 })
    if (token !== generation) return
    result.push(...batch.content); page++
    if (page >= batch.totalPages) break
  } while (true)
  goals.value = result
}
async function load() {
  busy.value = true; error.value = ''
  try { await read() } catch { error.value = '固定目標讀取失敗，請重試。' } finally { busy.value = false }
}
async function move(index: number, offset: number) {
  const goal = goals.value[index], adjacent = goals.value[index + offset]
  if (!goal || !adjacent || busy.value) return
  busy.value = true; error.value = ''
  try {
    await goalApi.pinOrder(goal, adjacent, offset < 0 ? 'UP' : 'DOWN')
    await client.invalidateQueries({ queryKey: ['goals'] }); await read()
  } catch {
    error.value = '順序可能已更新，已嘗試重新讀取。請確認畫面後再操作。'
    try { await read() } catch { error.value = '無法確認最新順序，請重新讀取後再操作。'; goals.value = [] }
  } finally { busy.value = false }
}
watch(() => props.modelValue, value => { if (value) void load(); else generation++ })
</script>
<template>
  <AppDialog v-model="visible" title="排列固定目標" width="min(560px, calc(100vw - 32px))" scroll-body :busy="busy">
    <p class="app-dialog-intro">上移或下移會立即儲存；總覽依此順序預覽前三個目標。</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false"><el-button :disabled="busy" @click="load">重新讀取</el-button></el-alert>
    <p v-if="busy" role="status">更新中…</p>
    <p v-if="!busy && !error && !goals.length">目前沒有固定於總覽的目標。</p>
    <ol class="pinned-list"><li v-for="(goal, index) in goals" :key="goal.id"><span>{{ index + 1 }}. {{ goal.definition.name }}</span><div><el-button :icon="ArrowUp" :disabled="busy || index === 0" :aria-label="`上移${goal.definition.name}`" @click="move(index, -1)" /><el-button :icon="ArrowDown" :disabled="busy || index === goals.length - 1" :aria-label="`下移${goal.definition.name}`" @click="move(index, 1)" /></div></li></ol>
    <template #footer="{ requestClose }"><el-button @click="requestClose">關閉</el-button></template>
  </AppDialog>
</template>
<style scoped>
.pinned-list { list-style: none; padding: 0; margin: 0; }
.pinned-list li { display: flex; justify-content: space-between; align-items: center; gap: var(--space-sm); padding-block: var(--space-sm); border-bottom: 1px solid var(--el-border-color-lighter); }
.pinned-list li > span { min-width: 0; overflow-wrap: anywhere; }
.pinned-list li > div { display: flex; gap: var(--space-xs); flex-shrink: 0; }
.pinned-list .el-button { margin-left: 0; }
</style>
