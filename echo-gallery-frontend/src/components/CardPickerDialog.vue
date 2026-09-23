<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { useInfiniteQuery } from '@tanstack/vue-query'
import { Search } from '@element-plus/icons-vue'
import type { CardDto, CardSearchParams } from '../types/card'
import { cardApi } from '../utils/api/cardApi'
import { normalizeWorkCardSearch } from '../utils/cardSearch'
import AppDialog from './AppDialog.vue'

const props = withDefaults(defineProps<{
  modelValue: boolean
  title: string
  description: string
  excludedCardIds?: Array<string | number>
  archiveStatus?: 'ACTIVE' | 'ARCHIVED' | 'ALL'
  confirmLabel?: string
  submitting?: boolean
  emptyDescription?: string
  settingsStyle?: 'surface' | 'plain'
}>(), {
  excludedCardIds: () => [],
  archiveStatus: 'ALL',
  confirmLabel: '選擇卡片',
  submitting: false,
  emptyDescription: '沒有其他可選擇的卡片',
  settingsStyle: 'surface',
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  confirm: [card: CardDto]
}>()

defineSlots<{
  settings?: (props: { selectedCard: CardDto | null }) => unknown
}>()

const searchInput = ref('')
const searchKeyword = ref('')
const selectedCard = ref<CardDto | null>(null)
let searchTimer: ReturnType<typeof setTimeout> | undefined

const dialogVisible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value),
})
const excludedIds = computed(() => new Set(props.excludedCardIds.map(String)))

watch(searchInput, (value) => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    searchKeyword.value = value.trim()
  }, 300)
})

onUnmounted(() => {
  if (searchTimer) clearTimeout(searchTimer)
})

const cardsQuery = useInfiniteQuery({
  queryKey: computed(() => ['cards', 'shared-picker', props.archiveStatus, searchKeyword.value]),
  enabled: computed(() => props.modelValue),
  initialPageParam: 0,
  queryFn: ({ pageParam }) => cardApi.searchCards({
    ...normalizeWorkCardSearch(searchKeyword.value),
    archiveStatus: props.archiveStatus,
    sortBy: 'UPDATED_AT',
    direction: 'DESC',
    page: pageParam,
    size: 20,
  } as CardSearchParams),
  getNextPageParam: lastPage => lastPage.page + 1 < lastPage.totalPages ? lastPage.page + 1 : undefined,
})

const cards = computed(() =>
  (cardsQuery.data.value?.pages.flatMap(page => page.content) ?? [])
    .filter(card => !excludedIds.value.has(String(card.id))),
)

const reset = () => {
  if (searchTimer) clearTimeout(searchTimer)
  searchInput.value = ''
  searchKeyword.value = ''
  selectedCard.value = null
}

const confirm = () => {
  if (selectedCard.value && !props.submitting) emit('confirm', selectedCard.value)
}
</script>

<template>
  <AppDialog
    v-model="dialogVisible"
    :title="title"
    width="min(980px, calc(100vw - 32px))"
    mode="workspace"
    @closed="reset"
  >
    <div class="card-picker-workspace">
      <section class="card-picker-browser">
        <p class="picker-description">{{ description }}</p>
        <el-input
          v-model="searchInput"
          :prefix-icon="Search"
          maxlength="255"
          clearable
          placeholder="輸入 Card ID（例如 #7）或標題"
          aria-label="搜尋卡片"
        />

        <div v-if="cardsQuery.isLoading.value" class="picker-state"><el-skeleton :rows="6" animated /></div>
        <el-result v-else-if="cardsQuery.isError.value" icon="warning" title="無法載入卡片" />
        <div v-else class="card-picker-list">
          <button
            v-for="card in cards"
            :key="card.id"
            type="button"
            class="card-picker-item"
            :class="{ selected: selectedCard?.id === card.id }"
            @click="selectedCard = card"
          >
            <span class="picker-card-content">
              <strong>{{ card.title }}</strong>
              <span class="card-metadata">
                <span>#{{ card.id }}</span>
                <span>{{ card.type === 'link' ? '連結' : '筆記' }}</span>
                <span v-if="card.isArchived">已封存</span>
                <span v-if="card.intervalDays == null">暫停回流</span>
                <span v-if="card.needsProcessing">待整理</span>
              </span>
            </span>
            <span class="selection-label">{{ selectedCard?.id === card.id ? '已選擇' : '選擇' }}</span>
          </button>
          <el-empty v-if="cards.length === 0" :image-size="72" :description="emptyDescription" />
          <div v-if="cardsQuery.hasNextPage.value" class="load-more-row">
            <el-button :loading="cardsQuery.isFetchingNextPage.value" @click="cardsQuery.fetchNextPage()">載入更多</el-button>
          </div>
        </div>
      </section>

      <aside :class="['card-picker-settings', `card-picker-settings--${settingsStyle}`]">
        <slot name="settings" :selected-card="selectedCard">
          <h3>已選卡片</h3>
          <p v-if="selectedCard" class="selected-card-title">{{ selectedCard.title }}</p>
          <p v-else class="settings-placeholder">請先從左側選擇一張卡片。</p>
        </slot>
      </aside>
    </div>

    <template #footer>
      <el-button :disabled="submitting" @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" :disabled="!selectedCard" @click="confirm">{{ confirmLabel }}</el-button>
    </template>
  </AppDialog>
</template>

<style scoped>
.card-picker-workspace { display: grid; height: min(68vh, 650px); min-height: 420px; grid-template-columns: minmax(0, 1.5fr) minmax(280px, 0.8fr); gap: 20px; }
.card-picker-browser { display: flex; min-width: 0; min-height: 0; flex-direction: column; }
.picker-description { margin: 0 0 14px; color: var(--el-text-color-secondary); font-size: var(--type-caption); line-height: var(--leading-ui); }
.picker-state { margin-top: 14px; }
.card-picker-list { min-height: 0; margin-top: 10px; overflow-y: auto; border-top: 1px solid var(--el-border-color-lighter); }
.card-picker-item { display: flex; width: 100%; align-items: center; justify-content: space-between; gap: 16px; padding: 13px 8px; border: 0; border-bottom: 1px solid var(--el-border-color-lighter); background: transparent; color: inherit; font: inherit; cursor: pointer; text-align: left; }
.card-picker-item:hover { background: var(--el-fill-color-light); }
.card-picker-item.selected { background: var(--el-color-primary-light-9); }
.picker-card-content { display: block; min-width: 0; }
.picker-card-content strong { display: block; line-height: 1.5; overflow-wrap: anywhere; }
.card-metadata { display: flex; align-items: center; flex-wrap: wrap; gap: 6px 10px; margin-top: 6px; color: var(--el-text-color-placeholder); font-size: var(--type-meta); }
.selection-label { flex: 0 0 auto; color: var(--el-color-primary); font-size: var(--type-caption); }
.load-more-row { display: flex; justify-content: center; padding: 16px 0; }
.card-picker-settings { min-width: 0; padding: 18px; overflow: visible; border: 1px solid var(--el-border-color-lighter); border-radius: 9px; background: var(--el-fill-color-extra-light); }
.card-picker-settings--plain { padding: 4px 0 0 20px; border: 0; border-left: 1px solid var(--el-border-color-lighter); border-radius: 0; background: transparent; }
.card-picker-settings h3 { margin: 0 0 12px; font-size: var(--type-card-title); }
.selected-card-title { margin: 0; color: var(--el-text-color-primary); font-weight: 600; line-height: 1.6; }
.settings-placeholder { margin: 0; color: var(--el-text-color-placeholder); line-height: var(--leading-ui); }
@media (max-width: 760px) {
  .card-picker-workspace { height: auto; min-height: 0; grid-template-columns: 1fr; }
  .card-picker-list { max-height: none; overflow: visible; }
  .card-picker-settings { overflow: visible; }
  .card-picker-settings--plain { padding: 20px 0 0; border-top: 1px solid var(--el-border-color-lighter); border-left: 0; }
}
</style>
