<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import PickerOption from '../ui/PickerOption.vue'
import PickerWorkspace from '../ui/PickerWorkspace.vue'
import SelectionSummary from '../ui/SelectionSummary.vue'
import RecordCount from '../ui/RecordCount.vue'
import { goalApi } from '../../utils/api/goalApi'
import { goalKinds } from '../../types/goal'
import type { GoalKind, GoalOption, GoalSource, GoalSourceView } from '../../types/goal'

const props = defineProps<{ sources: GoalSource[]; allTags: boolean; metadata: Record<string, GoalOption | GoalSourceView> }>()
const emit = defineEmits<{ confirm: [value: { sources: GoalSource[]; allTags: boolean; metadata: Record<string, GoalOption | GoalSourceView> }]; cancel: [] }>()
const sources = ref<GoalSource[]>(JSON.parse(JSON.stringify(props.sources)))
const allTags = ref(props.allTags)
const names = ref({ ...props.metadata })
const kind = ref<GoalKind>('TAG'), keyword = ref(''), page = ref(0)
const options = ref<GoalOption[]>([]), busy = ref(false), error = ref(''), preview = ref<number | null>(null), previewBusy = ref(false)
const excluded = ref(false), previewError = ref(''), previewCalculated = ref(false)
let generation = 0, previewGeneration = 0
const initial = JSON.stringify({ sources: props.sources, allTags: props.allTags })
const hasChanges = computed(() => initial !== JSON.stringify({ sources: sources.value, allTags: allTags.value }))
const included = computed(() => sources.value.filter(source => !source.excluded))
const exceptions = computed(() => sources.value.filter(source => source.excluded))
const groups = computed(() => Object.entries(goalKinds).map(([value, label]) => ({ kind: value as GoalKind, label, entries: included.value.filter(source => source.kind === value) })).filter(group => group.entries.length))
const key = (source: Pick<GoalSource, 'kind' | 'id'>) => `${source.kind}:${source.id}`
const title = (source: GoalSource) => names.value[key(source)]?.title || `${goalKinds[source.kind]} #${source.id}`

watch([sources, allTags], () => { preview.value = null; previewGeneration++; previewBusy.value = false; previewError.value = '' }, { deep: true })
async function load(reset = false) {
  if (reset) page.value = 0
  const token = ++generation; busy.value = true; error.value = ''; options.value = []
  try { const result = await goalApi.options(kind.value, keyword.value, page.value); if (token === generation) options.value = result }
  catch { if (token === generation) error.value = '來源載入失敗，請重試。' }
  finally { if (token === generation) busy.value = false }
}
function category(value: GoalKind, omit = false) { kind.value = value; excluded.value = omit; keyword.value = ''; void load(true) }
function selected(option: GoalOption) { return sources.value.some(source => key(source) === key(option) && source.excluded === excluded.value) }
function choose(option: GoalOption) {
  const index = sources.value.findIndex(source => key(source) === key(option))
  if (index >= 0 && sources.value[index]!.excluded === excluded.value) sources.value.splice(index, 1)
  else {
    if (index >= 0) sources.value.splice(index, 1)
    sources.value.push({ kind: option.kind, id: option.id, excluded: excluded.value })
    names.value[key(option)] = option
  }
}
function remove(source: GoalSource) { sources.value = sources.value.filter(value => key(value) !== key(source)) }
async function checkPreview() {
  const token = ++previewGeneration; previewBusy.value = true; previewError.value = ''
  try { const result = await goalApi.preview({ sources: sources.value, allTags: allTags.value }); if (token === previewGeneration) { preview.value = result.cardCount; previewCalculated.value = true } }
  catch { if (token === previewGeneration) previewError.value = '計算失敗，請檢查來源是否仍可用，再重試。' }
  finally { if (token === previewGeneration) previewBusy.value = false }
}
function confirm() { emit('confirm', { sources: JSON.parse(JSON.stringify(sources.value)), allTags: allTags.value, metadata: names.value }) }
defineExpose({ hasChanges, confirm })
onMounted(() => load(true))
onBeforeUnmount(() => { generation++; previewGeneration++ })
</script>
<template>
  <div class="goal-source-picker">
    <PickerWorkspace>
      <template v-if="excluded">
        <div class="source-mode-heading"><el-button text @click="category('CARD')">← 返回來源選擇</el-button><h3>不納入此目標的卡片</h3></div>
        <p class="source-rule">勾選後，這些卡片不會出現在此目標的相關卡片中。</p>
      </template>
      <el-tabs v-else :model-value="kind" aria-label="來源種類" @update:model-value="category($event as GoalKind)"><el-tab-pane v-for="(label, value) in goalKinds" :key="value" :name="value" :label="label" /></el-tabs>
      <el-input v-model="keyword" aria-label="搜尋來源" placeholder="搜尋名稱" clearable @keyup.enter="load(true)"><template #append><el-button @click="load(true)">查詢</el-button></template></el-input>
      <div class="source-options picker-results" :aria-busy="busy">
        <el-skeleton v-if="busy" :rows="3" animated />
        <el-alert v-else-if="error" :title="error" type="error" :closable="false"><el-button @click="load()">重試</el-button></el-alert>
        <template v-else><p v-if="!options.length" class="source-help">沒有符合的來源。</p><PickerOption v-for="option in options" :key="key(option)" :title="option.title" multiple :selected="selected(option)" @select="choose(option)"><span>{{ goalKinds[option.kind] }}</span></PickerOption></template>
      </div>
      <div v-if="page > 0 || options.length >= 20" class="source-pagination"><span>第 {{ page + 1 }} 頁</span><el-button :disabled="!page || busy" @click="page--; load()">上一頁</el-button><el-button :disabled="options.length < 20 || busy" @click="page++; load()">下一頁</el-button></div>
      <template #selection>
        <section class="selected-summary" aria-label="已選來源">
          <h3 class="selection-heading">已選來源 <RecordCount :count="included.length" /></h3>
          <SelectionSummary v-if="!included.length" empty-text="從候選清單選擇相關資料，也可以留白。" />
          <section v-for="group in groups" :key="group.kind" class="source-group"><h4>{{ group.label }} · {{ group.entries.length }}</h4><SelectionSummary v-for="source in group.entries" :key="key(source)" :title="title(source)" hide-label><template #actions><el-button type="primary" link size="small" :aria-label="'移除來源：' + title(source)" @click="remove(source)">移除</el-button></template></SelectionSummary></section>
          <div v-if="included.filter(source => source.kind === 'TAG').length > 1" class="tag-rule"><h4>標籤比對</h4><el-radio-group v-model="allTags"><el-radio :value="false">符合任一標籤</el-radio><el-radio :value="true">符合全部標籤</el-radio></el-radio-group><p class="source-help">其他來源仍會一併納入。</p></div>
          <div class="card-exclusions"><h4>不納入的卡片 <span v-if="exceptions.length">· {{ exceptions.length }}</span></h4><el-button type="primary" link size="small" @click="category('CARD', true)">選擇卡片</el-button><div v-for="source in exceptions" :key="key(source)" class="excluded-source"><span>{{ title(source) }}</span><el-button type="primary" link size="small" @click="remove(source)">取消排除</el-button></div></div>
          <section class="source-preview" aria-label="符合條件的卡片"><h3>符合條件的卡片</h3><p v-if="preview !== null" role="status">符合目前設定：{{ preview }} 張</p><p v-else class="source-help" role="status">{{ previewCalculated ? '待重新計算' : '尚未計算' }}</p><el-button :loading="previewBusy" @click="checkPreview">{{ previewCalculated ? '重新計算' : '計算卡片數' }}</el-button><el-alert v-if="previewError" :title="previewError" type="error" :closable="false" /><p class="source-help">包含暫停與封存卡片；目標看板會依範圍設定再篩選。</p></section>
        </section>
      </template>
    </PickerWorkspace>
  </div>
</template>
<style scoped>
.goal-source-picker { min-width: 0; } .source-mode-heading { display: grid; justify-items: start; gap: var(--space-xs); margin-bottom: var(--space-xs); } .source-mode-heading h3 { font-size: var(--type-card-title); overflow-wrap: anywhere; } .source-rule, .source-help { color: var(--el-text-color-secondary); font-size: var(--type-metadata); line-height: var(--leading-ui); overflow-wrap: anywhere; } .source-rule { margin-bottom: var(--space-sm); } .source-options { flex: 0 1 auto; } .source-pagination { display: flex; justify-content: flex-end; align-items: center; gap: var(--space-xs); flex-wrap: wrap; margin-top: var(--space-sm); } .source-pagination > span { margin-right: auto; font-size: var(--type-metadata); color: var(--el-text-color-secondary); } .source-pagination .el-button { margin-left: 0; } .selected-summary { display: grid; gap: var(--space-md); } .selected-summary > h3.selection-heading { display: flex; align-items: center; gap: var(--space-xs); margin-bottom: 0; } .source-group { display: grid; gap: var(--space-xs); } h4 { font-size: var(--type-secondary); font-weight: var(--weight-medium); line-height: var(--leading-ui); } .excluded-source { display: flex; align-items: center; justify-content: space-between; gap: var(--space-xs); min-width: 0; } .excluded-source > span { min-width: 0; overflow-wrap: anywhere; font-size: var(--type-secondary); } .excluded-source .el-button { flex-shrink: 0; margin-left: 0; } .card-exclusions, .tag-rule, .source-preview { display: grid; justify-items: start; gap: var(--space-xs); border-top: 1px solid var(--el-border-color-lighter); padding-top: var(--space-sm); }
</style>
