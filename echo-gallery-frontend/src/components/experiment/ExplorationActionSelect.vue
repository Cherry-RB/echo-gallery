<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { ArrowDown } from '@element-plus/icons-vue'
import ExpandableText from '../ExpandableText.vue'
import type { MethodOption } from '../../types/explorationActivity'
import { explorationActivityApi as api } from '../../utils/api/explorationActivityApi'
import { apiErrorMessage } from '../../utils/api/errorMessage'

const props = withDefaults(defineProps<{
  experimentId: number; threadId: number | null; method: MethodOption | null; text: string;
  recent?: MethodOption[]; favorites?: MethodOption[]; disabled?: boolean; allowCreate?: boolean;
}>(), { recent: () => [], favorites: () => [] })
const emit = defineEmits<{ input: [text: string]; select: [method: MethodOption]; 'toggle-favorite': [method: MethodOption] }>()
const field = ref<{ focus: () => void; getData: (query: string) => Promise<void>; activated: boolean; highlightedIndex: number }>()
const expanded = ref(false), error = ref(''), page = ref(1), totalPages = ref(0)
const query = ref('')
let generation = 0
type Suggestion = { value: string; method?: MethodOption; create?: boolean; empty?: boolean }
const favorite = (id: number) => props.favorites.some(m => m.id === id)
const recent = (id: number) => props.recent.some(m => m.id === id)
const hasFooter = computed(() => !!error.value || totalPages.value > 1 || !!props.method && !!props.allowCreate)
const text = computed({ get: () => props.text, set: value => {
  // 新輸入立即使舊查詢失效，不等待下一次 debounce，避免舊選項覆蓋新查詢。
  generation++
  if (field.value) field.value.highlightedIndex = -1
  emit('input', value)
} })
async function suggestions(value: string, done: (items: Suggestion[]) => void) {
  const current = ++generation
  // 再次展開已選項目時仍列出備選，不把已選全文當成搜尋條件。
  const keyword = props.method && value === props.method.content ? '' : value.trim()
  if (query.value !== keyword) page.value = 1
  query.value = keyword; error.value = ''
  try {
    // 尚未建立的預設探索線不應列出其他探索線的行動。
    const threadId = props.threadId ?? (await api.threadOptions(props.experimentId, { keyword: '未設定' })).content.find(t => t.systemKey === 'UNASSIGNED')?.id
    const result = threadId == null ? { content: [], totalPages: 0 }
      : await api.methods(props.experimentId, { threadId, keyword, page: page.value - 1 })
    if (current !== generation) return
    totalPages.value = result.totalPages
    const shortcuts = !keyword && page.value === 1 ? [...props.recent, ...props.favorites].filter(m => m.threadId === threadId) : []
    const unique = new Map<number, MethodOption>()
    for (const method of [...shortcuts, ...result.content]) unique.set(method.id, method)
    const items: Suggestion[] = [...unique.values()].map(method => ({ value: method.content, method }))
    if (props.allowCreate && keyword && !items.some(item => item.value === keyword)) items.push({ value: keyword, create: true })
    done(items.length ? items : [{ value, empty: true }])
  } catch (e) {
    if (current !== generation) return
    error.value = apiErrorMessage(e, '載入行動備選失敗，請重試。')
    done([{ value, empty: true }])
  }
}
function select(item: Record<string, unknown>) {
  if (item.empty) return
  if (item.method) emit('select', item.method as MethodOption)
  else emit('input', String(item.value))
}
async function open() { field.value?.focus(); await field.value?.getData(props.text) }
async function changePage(value: number) {
  page.value = value
  await nextTick()
  await field.value?.getData(query.value)
}
watch(() => props.threadId, () => {
  generation++; page.value = 1; query.value = ''; totalPages.value = 0; error.value = ''
  if (field.value?.activated) void field.value.getData(props.text)
})
onBeforeUnmount(() => { generation++ })
defineExpose({ focus: () => field.value?.focus() })
</script>
<template>
  <div class="action-select">
    <el-autocomplete ref="field" v-model="text" class="action-input" :fetch-suggestions="suggestions" :disabled="disabled" :debounce="250" :maxlength="2000" clearable fit-input-width :popper-style="{ maxWidth: 'calc(100vw - 24px)' }" :highlight-first-item="false" :select-when-unmatched="false" placeholder="輸入行動，或選擇行動備選" :aria-label="allowCreate ? '下一步行動' : '對應行動（選填）'" @select="select">
      <template #suffix><el-button text circle size="small" :icon="ArrowDown" :disabled="disabled" aria-label="展開行動備選" @mousedown.prevent @click.stop="open" /></template>
      <template #default="{ item }">
        <div v-if="item.empty" class="empty-option">{{ error || '尚無符合的行動備選' }}</div>
        <div v-else class="action-option">
          <span class="option-content">{{ item.create ? '＋ 新增「' + item.value + '」' : item.value }}</span>
          <span v-if="item.method" class="metadata">{{ [recent(item.method.id) ? '最近' : '', favorite(item.method.id) ? '常用' : ''].filter(Boolean).join('・') }}</span>
        </div>
      </template>
      <template v-if="hasFooter" #footer>
        <div class="option-footer" @mousedown.prevent>
          <el-button v-if="error" type="primary" link size="small" @click="open">重試</el-button>
          <el-pagination v-if="totalPages > 1" :current-page="page" :page-size="20" :page-count="totalPages" layout="prev, pager, next" @current-change="changePage" />
          <el-button v-if="method && allowCreate" type="primary" link size="small" :disabled="disabled" @click="emit('toggle-favorite', method)">{{ favorite(method.id) ? '移除常用' : '設為常用' }}</el-button>
        </div>
      </template>
    </el-autocomplete>
    <div v-if="text.length > 60 || text.includes('\n')" class="long-action">
      <el-button type="primary" link size="small" :disabled="disabled" :aria-expanded="expanded" @click="expanded = !expanded">{{ expanded ? '收合內容' : allowCreate && !method ? '展開編輯' : '展開內容' }}</el-button>
      <template v-if="expanded"><el-input v-if="allowCreate && !method" v-model="text" type="textarea" :rows="4" maxlength="2000" :disabled="disabled" aria-label="完整行動內容" /><ExpandableText v-else :content="text" :lines="12" /></template>
    </div>
    <p v-if="!allowCreate && !method && text.trim()" class="metadata" role="status">請從選單選擇既有行動備選，或清空欄位。</p>
    <p v-if="allowCreate && !method && text.trim()" class="metadata" role="status">新行動備選</p>
  </div>
</template>
<style scoped>
.action-select, .action-input { width: 100%; min-width: 0; }
.action-option { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--space-xs); width: 100%; min-width: 0; box-sizing: border-box; padding-block: var(--space-xs); line-height: var(--leading-ui); }
.option-content { flex: 1; min-width: 0; overflow-wrap: anywhere; white-space: pre-wrap; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.metadata { color: var(--el-text-color-secondary); font-size: var(--type-meta); margin: var(--space-xs) 0 0; }
.action-option .metadata { white-space: nowrap; flex-shrink: 0; margin: 0; }
.empty-option { color: var(--el-text-color-secondary); white-space: normal; line-height: var(--leading-ui); padding-block: var(--space-xs); }
.option-footer { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-xs); padding: var(--space-xs) var(--space-sm); }
.long-action { display: grid; gap: var(--space-xs); justify-items: start; }
</style>
