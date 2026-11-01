<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import PickerOption from '../ui/PickerOption.vue'
import PickerWorkspace from '../ui/PickerWorkspace.vue'
import SelectionSummary from '../ui/SelectionSummary.vue'
import RecordCount from '../ui/RecordCount.vue'
import { goalApi } from '../../utils/api/goalApi'
import { goalKinds } from '../../types/goal'
import type { GoalKind, GoalOption, GoalSource, GoalSourceView } from '../../types/goal'
const props = defineProps<{ sources: GoalSource[]; allTags: boolean; metadata: Record<string, GoalOption | GoalSourceView> }>()
const emit = defineEmits<{ confirm: [value: { sources: GoalSource[]; allTags: boolean; metadata: Record<string, GoalOption | GoalSourceView> }]; cancel: [] }>()
const sources = ref<GoalSource[]>(JSON.parse(JSON.stringify(props.sources))), allTags = ref(props.allTags)
const names = ref({ ...props.metadata }), kind = ref<GoalKind>('TAG'), keyword = ref(''), page = ref(0)
const options = ref<GoalOption[]>([]), busy = ref(false), error = ref(''), preview = ref<number | null>(null), previewBusy = ref(false)
const excluded = ref(false), experiment = ref<number | undefined>()
const previewError = ref(''), previewCalculated = ref(false)
let generation = 0, previewGeneration = 0
const initial = JSON.stringify({ sources: props.sources, allTags: props.allTags })
const hasChanges = computed(() => initial !== JSON.stringify({ sources: sources.value, allTags: allTags.value }))
const included = computed(() => sources.value.filter(s => !s.excluded))
const exceptions = computed(() => sources.value.filter(s => s.excluded))
const experiments = computed(() => included.value.filter(s => s.kind === 'EXPERIMENT'))
const groups = computed(() => Object.entries(goalKinds).map(([value, label]) => ({ kind: value as GoalKind, label, entries: included.value.filter(s => s.kind === value) })).filter(group => group.entries.length))
const rangeMode = computed(() => excluded.value && kind.value === 'THREAD' && experiment.value != null)
const rangeTitle = computed(() => title({ kind: 'EXPERIMENT', id: experiment.value ?? 0, excluded: false }))
const cardExceptions = computed(() => exceptions.value.filter(s => s.kind === 'CARD'))
const threadExceptions = computed(() => exceptions.value.filter(s => s.kind === 'THREAD'))
const key = (s: Pick<GoalSource, 'kind' | 'id'>) => `${s.kind}:${s.id}`
const title = (s: GoalSource) => names.value[key(s)]?.title || `${goalKinds[s.kind]} #${s.id}`
watch([sources, allTags], () => { preview.value = null; previewGeneration++; previewBusy.value = false; previewError.value = '' }, { deep: true })
async function load(reset = false) {
  if (reset) page.value = 0
  const token = ++generation; busy.value = true; error.value = ''; options.value = []
  try { const result = await goalApi.options(kind.value, keyword.value, page.value, experiment.value); if (token === generation) options.value = result }
  catch { if (token === generation) error.value = '來源載入失敗，請重試。' }
  finally { if (token === generation) busy.value = false }
}
function category(value: GoalKind, omit = false, parent?: number) {
  kind.value = value; excluded.value = omit; experiment.value = parent; keyword.value = ''; void load(true)
}
function directThread(option: GoalOption) { return rangeMode.value && included.value.some(s => key(s) === key(option)) }
function selected(option: GoalOption) {
  if (rangeMode.value) return !sources.value.some(s => key(s) === key(option) && s.excluded)
  return sources.value.some(s => key(s) === key(option) && s.excluded === excluded.value)
}
function choose(option: GoalOption) {
  if (directThread(option)) return
  if (rangeMode.value) {
    const index = sources.value.findIndex(s => key(s) === key(option) && s.excluded)
    if (index >= 0) sources.value.splice(index, 1)
    else { sources.value.push({ kind: option.kind, id: option.id, excluded: true }); names.value[key(option)] = option }
    return
  }
  const index = sources.value.findIndex(s => key(s) === key(option))
  if (index >= 0 && sources.value[index]!.excluded === excluded.value) sources.value.splice(index, 1)
  else {
    if (index >= 0) sources.value.splice(index, 1)
    sources.value.push({ kind: option.kind, id: option.id, excluded: excluded.value })
    names.value[key(option)] = option
  }
}
async function remove(source: GoalSource) {
  const children = source.kind === 'EXPERIMENT' ? exceptions.value.filter(s => s.kind === 'THREAD' && names.value[key(s)]?.parentId === source.id) : []
  if (children.length) {
    try { await ElMessageBox.confirm('移除此實驗時，將一併移除它的探索線範圍設定。原資料與歷史會保留。', '移除追蹤來源', { confirmButtonText: '移除實驗與設定', cancelButtonText: '取消', type: 'warning' }) } catch { return }
  }
  const removals = new Set([key(source), ...children.map(key)])
  sources.value = sources.value.filter(s => !removals.has(key(s)))
  if (source.kind === 'EXPERIMENT' && experiment.value === source.id) category('EXPERIMENT')
}
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
        <div class="source-mode-heading"><el-button text @click="category(rangeMode ? 'EXPERIMENT' : 'CARD')">← 返回來源選擇</el-button><h3>{{ rangeMode ? rangeTitle + '・追蹤範圍' : '不納入此目標的卡片' }}</h3></div>
        <p class="source-rule">{{ rangeMode ? '勾選要透過此實驗納入的探索線；未來新增的探索線預設納入。' : '勾選後，這些卡片不會出現在此目標的相關卡片中。' }}</p>
        <p v-if="rangeMode" class="source-rule">取消勾選只影響這個實驗帶入的範圍，卡片仍可能經其他來源納入。已獨立加入的探索線，請先從右側移除再調整。</p>
      </template>
      <el-tabs v-else :model-value="kind" aria-label="來源種類" @update:model-value="category($event as GoalKind)"><el-tab-pane v-for="(label, value) in goalKinds" :key="value" :name="value" :label="label" /></el-tabs>
      <el-input v-model="keyword" aria-label="搜尋來源" :placeholder="kind === 'THREAD' ? '搜尋探索線或所屬實驗' : '搜尋名稱'" clearable @keyup.enter="load(true)"><template #append><el-button @click="load(true)">查詢</el-button></template></el-input>
      <div class="source-options picker-results" :aria-busy="busy">
        <el-skeleton v-if="busy" :rows="3" animated />
        <el-alert v-else-if="error" :title="error" type="error" :closable="false"><el-button @click="load()">重試</el-button></el-alert>
        <template v-else>
          <p v-if="!options.length" class="source-help">沒有符合的來源。</p>
          <PickerOption v-for="option in options" :key="key(option)" :title="option.title" multiple :disabled="directThread(option)" :selected="selected(option)" @select="choose(option)"><span>{{ goalKinds[option.kind] }}<template v-if="option.parentTitle"> · {{ option.parentTitle }}</template><template v-if="directThread(option)"> · 已獨立加入</template></span></PickerOption>
        </template>
      </div>
      <div v-if="page > 0 || options.length >= 20" class="source-pagination"><span>第 {{ page + 1 }} 頁</span><el-button :disabled="!page || busy" @click="page--; load()">上一頁</el-button><el-button :disabled="options.length < 20 || busy" @click="page++; load()">下一頁</el-button></div>
      <template #selection>
        <section class="selected-summary" aria-label="已選來源">
          <h3 class="selection-heading">已選來源 <RecordCount :count="included.length" /></h3>
          <SelectionSummary v-if="!included.length" empty-text="從候選清單選擇相關資料，也可以留白。" />
          <section v-for="group in groups" :key="group.kind" class="source-group">
            <h4>{{ group.label }} · {{ group.entries.length }}</h4>
            <div v-for="source in group.entries" :key="key(source)" class="selected-source">
            <SelectionSummary :title="title(source)" :metadata="names[key(source)]?.parentTitle || undefined" hide-label>
              <template #actions><el-button type="primary" link size="small" :aria-label="'移除來源：' + title(source)" @click="remove(source)">移除</el-button></template>
            </SelectionSummary>
            <el-button v-if="source.kind === 'EXPERIMENT'" class="range-action" type="primary" link size="small" @click="category('THREAD', true, source.id)">調整追蹤範圍</el-button>
            </div>
          </section>
          <p v-if="included.some(source => 'available' in (names[key(source)] || {}) && !(names[key(source)] as GoalSourceView)?.available)" class="source-warning">部分來源已不可用，請移除或重新選擇。</p>
          <div v-if="included.filter(s => s.kind === 'TAG').length > 1" class="tag-rule"><h4>標籤比對</h4><el-radio-group v-model="allTags"><el-radio :value="false">符合任一標籤</el-radio><el-radio :value="true">符合全部標籤</el-radio></el-radio-group><p class="source-help">其他來源仍會一併納入。</p></div>
          <div class="card-exclusions"><h4>不納入的卡片 <span v-if="cardExceptions.length">· {{ cardExceptions.length }}</span></h4><el-button type="primary" link size="small" @click="category('CARD', true)">選擇卡片</el-button><div v-for="source in cardExceptions" :key="key(source)" class="excluded-source"><span>{{ title(source) }}</span><el-button type="primary" link size="small" @click="remove(source)">取消排除</el-button></div></div>
          <details v-if="threadExceptions.length" class="range-summary"><summary>未透過實驗納入的探索線 · {{ threadExceptions.length }}</summary><div v-for="source in threadExceptions" :key="key(source)" class="excluded-source"><div><strong>{{ title(source) }}</strong><p class="source-help">{{ names[key(source)]?.parentTitle }}<template v-if="!experiments.some(s => s.id === names[key(source)]?.parentId)"> · 原實驗未選取，目前未作用</template></p></div><el-button type="primary" link size="small" @click="remove(source)">恢復納入</el-button></div></details>
          <section class="source-preview" aria-label="符合條件的卡片">
            <h3>符合條件的卡片</h3>
            <p v-if="preview !== null" role="status">符合目前設定：{{ preview }} 張</p>
            <p v-else class="source-help" role="status">{{ previewCalculated ? '待重新計算' : '尚未計算' }}</p>
            <el-button :loading="previewBusy" @click="checkPreview">{{ previewCalculated ? '重新計算' : '計算卡片數' }}</el-button>
            <el-alert v-if="previewError" :title="previewError" type="error" :closable="false" />
            <p class="source-help">包含暫停與封存卡片；目標看板會依範圍設定再篩選。</p>
          </section>
        </section>
      </template>
    </PickerWorkspace>
  </div>
</template>
<style scoped>
.goal-source-picker { min-width: 0; }
.source-mode-heading { display: grid; justify-items: start; gap: var(--space-xs); margin-bottom: var(--space-xs); }
.source-mode-heading h3 { font-size: var(--type-card-title); overflow-wrap: anywhere; }
.source-rule, .source-help { color: var(--el-text-color-secondary); font-size: var(--type-metadata); line-height: var(--leading-ui); overflow-wrap: anywhere; }
.source-rule { margin-bottom: var(--space-sm); }
.source-options { flex: 0 1 auto; }
.source-pagination { display: flex; justify-content: flex-end; align-items: center; gap: var(--space-xs); flex-wrap: wrap; margin-top: var(--space-sm); }
.source-pagination > span { margin-right: auto; font-size: var(--type-metadata); color: var(--el-text-color-secondary); }
.source-pagination .el-button { margin-left: 0; }
.selected-summary { display: grid; gap: var(--space-md); }
.selected-summary > h3.selection-heading { display: flex; align-items: center; gap: var(--space-xs); margin-bottom: 0; }
.source-group { display: grid; gap: var(--space-xs); }
.selected-source { display: grid; justify-items: stretch; gap: var(--space-2xs); }
.range-action { justify-self: end; margin: 0; }
h4 { font-size: var(--type-secondary); font-weight: var(--weight-medium); line-height: var(--leading-ui); }
.excluded-source { display: flex; align-items: center; justify-content: space-between; gap: var(--space-xs); min-width: 0; }
.excluded-source > span, .excluded-source > div { min-width: 0; overflow-wrap: anywhere; font-size: var(--type-secondary); }
.excluded-source .el-button { flex-shrink: 0; margin-left: 0; }
.card-exclusions, .tag-rule, .source-preview, .range-summary { display: grid; justify-items: start; gap: var(--space-xs); border-top: 1px solid var(--el-border-color-lighter); padding-top: var(--space-sm); }
.range-summary { display: block; }
.range-summary summary { cursor: pointer; font-size: var(--type-secondary); line-height: var(--leading-ui); }
.range-summary .excluded-source { margin-top: var(--space-xs); }
.source-warning { color: var(--el-color-warning); font-size: var(--type-secondary); }
</style>
