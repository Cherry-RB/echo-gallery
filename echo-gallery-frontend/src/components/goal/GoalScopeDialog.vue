<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import AppDialog from '../AppDialog.vue'
import { goalApi } from '../../utils/api/goalApi'
import { goalStatuses } from '../../types/goal'
import type { GoalPreference, GoalStatus, GoalView } from '../../types/goal'
import { hasGoalCards } from '../../utils/goalPresentation'
const props = defineProps<{ modelValue: boolean; preference: GoalPreference; busy?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [boolean]; confirm: [GoalPreference] }>()
const visible = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) })
const draft = ref<GoalPreference>({ ...props.preference, goalIds: [...props.preference.goalIds] })
const keyword = ref(''), statuses = ref<GoalStatus[]>([]), page = ref(0), totalPages = ref(0)
const goals = ref<GoalView[]>([]), loading = ref(false), error = ref(''), names = ref<Record<number, string>>({})
let generation = 0
let opening = 0
const applied = ref({ keyword: '', statuses: [] as GoalStatus[] })
async function query(reset = false) {
  if (reset) { applied.value = { keyword: keyword.value, statuses: [...statuses.value] }; page.value = 0 }
  const token = ++generation; loading.value = true; error.value = ''
  try {
    const result = await goalApi.list({ ...applied.value, archiveScope: 'UNARCHIVED', page: page.value })
    if (token !== generation) return
    goals.value = result.content; totalPages.value = result.totalPages
    for (const goal of result.content) names.value[goal.id] = goal.definition.name
  } catch { if (token === generation) error.value = '目標清單讀取失敗，請重試。' }
  finally { if (token === generation) loading.value = false }
}
watch(() => props.modelValue, async value => {
  const token = ++opening
  if (!value) { generation++; return }
  draft.value = JSON.parse(JSON.stringify(props.preference)); keyword.value = ''; statuses.value = []; names.value = {}
  await query(true)
  const missing = draft.value.goalIds.filter(id => !names.value[id])
  // 限制同時請求數；已選但不在當頁的目標仍須能辨識。
  for (let offset = 0; offset < missing.length; offset += 10) {
    const batch = missing.slice(offset, offset + 10)
    const results = await Promise.allSettled(batch.map(id => goalApi.detail(id)))
    if (token !== opening) return
    results.forEach((result, index) => {
      const id = batch[index]!
      names.value[id] = result.status === 'fulfilled' ? `${result.value.definition.name}${result.value.definition.archived ? '（已封存，本次不納入）' : ''}` : `目標 #${id}（名稱讀取失敗）`
    })
  }
})
</script>
<template>
  <AppDialog v-model="visible" title="目標範圍設定" width="min(680px, calc(100vw - 32px))" scroll-body :draft="draft" :busy="busy">
    <el-form :disabled="busy" @submit.prevent>
    <el-radio-group v-model="draft.allActive" aria-label="目標選取方式"><el-radio :value="true">所有進行中目標</el-radio><el-radio :value="false">自行選擇目標</el-radio></el-radio-group>
    <div v-if="!draft.allActive" class="scope-custom">
      <div class="scope-search"><el-input v-model="keyword" aria-label="搜尋目標" placeholder="搜尋目標" clearable @keyup.enter="query(true)" /><el-select v-model="statuses" aria-label="候選目標狀態" multiple collapse-tags placeholder="全部狀態"><el-option v-for="(label, value) in goalStatuses" :key="value" :value="value" :label="label" /></el-select><el-button @click="query(true)">查詢</el-button></div>
      <el-alert v-if="error" :title="error" type="error" :closable="false"><el-button @click="query()">重試</el-button></el-alert>
      <p v-if="loading" class="scope-help" role="status">載入中…</p>
      <el-checkbox-group v-model="draft.goalIds" class="scope-options">
        <el-checkbox v-for="goal in goals" :key="goal.id" :value="goal.id" :disabled="loading"><span>{{ goal.definition.name }}</span><small>{{ goalStatuses[goal.effectiveStatus] }} · {{ hasGoalCards(goal.definition) ? `相關卡片 ${goal.cardCount} 張` : '尚未設定卡片來源' }}</small></el-checkbox>
      </el-checkbox-group>
      <p v-if="!loading && !error && !goals.length" class="scope-help">沒有符合條件的目標。</p>
      <div class="scope-pages"><el-button :disabled="!page || loading" @click="page--; query()">上一頁</el-button><span>第 {{ page + 1 }} 頁</span><el-button :disabled="page + 1 >= totalPages || loading" @click="page++; query()">下一頁</el-button></div>
      <div class="scope-selected"><span>已選 {{ draft.goalIds.length }} 個目標</span><el-tag v-for="id in draft.goalIds" :key="id" closable @close="draft.goalIds = draft.goalIds.filter(value => value !== id)">{{ names[id] || `目標 #${id}` }}</el-tag></div>
    </div>
    <div class="scope-flags"><el-checkbox v-model="draft.includePaused">包含暫停回流卡片</el-checkbox><el-checkbox v-model="draft.includeArchived">包含封存卡片</el-checkbox></div>
    <p class="scope-help">確認後開始新一輪；不會改動今日回流排程。自行選擇可包含未開始、暫停或已結束的未封存目標。</p>
    </el-form>
    <template #footer="{ requestClose }"><el-button @click="requestClose">取消</el-button><el-button type="primary" :loading="busy" @click="emit('confirm', JSON.parse(JSON.stringify(draft)))">套用範圍</el-button></template>
  </AppDialog>
</template>
<style scoped>
.scope-custom { display: grid; gap: var(--space-sm); margin-top: var(--space-md); }
.scope-search { display: flex; gap: var(--space-xs); }
.scope-search > .el-input { flex: 1; }
.scope-search > .el-select { width: 12rem; }
.scope-options { display: grid; gap: var(--space-xs); }
.scope-options .el-checkbox { height: auto; margin: 0; padding: var(--space-sm); border: 1px solid var(--el-border-color-light); border-radius: var(--radius-md); }
.scope-options :deep(.el-checkbox__label) { white-space: normal; overflow-wrap: anywhere; }
.scope-options small { display: block; color: var(--el-text-color-secondary); }
.scope-pages, .scope-selected, .scope-flags { display: flex; gap: var(--space-xs); flex-wrap: wrap; align-items: center; }
.scope-pages { justify-content: flex-end; }
.scope-pages .el-button { margin-left: 0; }
.scope-selected { font-size: var(--type-secondary); }
.scope-flags { margin-top: var(--space-lg); }
.scope-help { margin-top: var(--space-xs); color: var(--el-text-color-secondary); font-size: var(--type-metadata); }
@media(max-width:760px) { .scope-search { flex-wrap: wrap; } .scope-search > .el-input, .scope-search > .el-select { flex-basis: 100%; width: 100%; } }
</style>
