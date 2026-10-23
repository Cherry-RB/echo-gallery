<script setup lang="ts">
import { computed, nextTick, onUnmounted, ref, watch } from 'vue'
import { useInfiniteQuery, useQuery } from '@tanstack/vue-query'
import { Search } from '@element-plus/icons-vue'
import type { CardDto, CardSearchParams } from '../types/card'
import { cardApi } from '../utils/api/cardApi'
import { normalizeIssueCardSearch } from '../utils/cardSearch'
import AppDialog from './AppDialog.vue'
import PickerWorkspace from './ui/PickerWorkspace.vue'
import PickerOption from './ui/PickerOption.vue'
import CardPickerSelectionSummary from './ui/CardPickerSelectionSummary.vue'
import SourceReadingDialog, { type SourceReadingItem } from './SourceReadingDialog.vue'
import { useWorkDialogGuard } from '../composables/useWorkDialogGuard'

const props = withDefaults(defineProps<{
  modelValue: boolean
  title: string
  description: string
  excludedCardIds?: Array<string | number>
  archiveStatus?: 'ACTIVE' | 'ARCHIVED' | 'ALL'
  confirmLabel?: string
  settingsDraft?: unknown
  submitting?: boolean
  emptyDescription?: string
  settingsStyle?: 'surface' | 'plain'
  initialCardId?: string | number | null
  confirmationHint?: string
  multiple?: boolean
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
  'confirm-many': [cards: CardDto[]]
}>()

defineSlots<{
  settings?: (props: { selectedCard: CardDto | null }) => unknown
}>()

const searchInput = ref('')
const searchKeyword = ref('')
const selectedCard = ref<CardDto | null>(null)
const selectedCards = ref(new Map<string, CardDto>())
const draftBaseline = ref<string>()
const shell = ref<InstanceType<typeof AppDialog>>()
const serializeDraft = () => JSON.stringify([props.settingsDraft, props.multiple ? [...selectedCards.value.keys()] : selectedCard.value?.id])
watch(() => props.modelValue, async visible => {
  if (!visible) return
  await nextTick()
  draftBaseline.value = serializeDraft()
}, { immediate: true })
const readingCard = ref<CardDto | null>(null)
useWorkDialogGuard(shell, () => props.modelValue, () => {
  if (!readingCard.value) return false
  readingCard.value = null
  return true
})
const selectionTouched = ref(false)
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
    ...normalizeIssueCardSearch(searchKeyword.value),
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
const initialCardQuery = useQuery({
  queryKey: computed(() => ['cards', 'picker-initial', props.initialCardId]),
  enabled: computed(() => props.modelValue && props.initialCardId != null),
  queryFn: () => cardApi.getCard(props.initialCardId!),
})
watch([() => props.modelValue, () => initialCardQuery.data.value], ([visible, card]) => {
  if (visible && card && !excludedIds.value.has(String(card.id))) {
    if (!selectionTouched.value) selectedCard.value = card
    draftBaseline.value = JSON.stringify([props.settingsDraft, props.multiple ? [] : card.id])
  }
}, { immediate: true })
watch([() => props.modelValue, cards], ([visible]) => {
  if (visible && !selectionTouched.value && props.initialCardId != null) {
    selectedCard.value = cards.value.find(card => String(card.id) === String(props.initialCardId)) ?? selectedCard.value
  }
}, { immediate: true })

const reset = () => {
  if (searchTimer) clearTimeout(searchTimer)
  searchInput.value = ''
  searchKeyword.value = ''
  selectedCard.value = null
  selectedCards.value.clear()
  readingCard.value = null
  selectionTouched.value = false
}

const confirm = () => {
  if (props.multiple) {
    if (!props.submitting && selectedCards.value.size) emit('confirm-many', [...selectedCards.value.values()])
    return
  }
  if (selectedCard.value && !props.submitting) emit('confirm', selectedCard.value)
}
const select = (card: CardDto) => {
  if (props.submitting) return
  selectionTouched.value = true
  if (!props.multiple) { selectedCard.value = card; return }
  const key = String(card.id)
  if (selectedCards.value.has(key)) selectedCards.value.delete(key)
  else if (selectedCards.value.size < 100) selectedCards.value.set(key, card)
}
let scrollTop = 0
const read = (card: CardDto) => {
  scrollTop = document.querySelector('.card-picker-list')?.scrollTop ?? 0
  readingCard.value = card
}
const restoreScroll = () => {
  const list = document.querySelector('.card-picker-list')
  if (list) list.scrollTop = scrollTop
}

const toSourceReadingItem = (card: CardDto): SourceReadingItem => ({
  id: null,
  cardId: Number(card.id),
  recordId: null,
  title: card.title,
  text: '',
  experimentId: null,
  experimentTitle: null,
  archived: card.isArchived,
})
</script>

<template>
  <AppDialog ref="shell" :busy="submitting" :draft="[settingsDraft, multiple ? [...selectedCards.keys()] : selectedCard?.id]" :draft-baseline="draftBaseline"
    :model-value="dialogVisible"
    :close-on-click-modal="!readingCard" :close-on-press-escape="!readingCard"
    @update:model-value="dialogVisible = $event"
    :draft-ready="initialCardId == null || initialCardQuery.isFetched.value"
    :title="title"
    width="min(980px, calc(100vw - 32px))"
    mode="workspace"
    scroll-body
    @closed="!modelValue && reset()"
    @opened="restoreScroll"
  >
    <PickerWorkspace :settings-style="settingsStyle">
        <p class="picker-description">{{ description }}</p>
        <el-input
          v-model="searchInput"
          :prefix-icon="Search"
          maxlength="255"
          clearable
          :disabled="submitting"
          placeholder="輸入 卡片 ID（例如 #7）或標題"
          aria-label="搜尋卡片"
        />

        <div v-if="cardsQuery.isLoading.value" class="picker-state"><el-skeleton :rows="6" animated /></div>
        <el-result v-else-if="cardsQuery.isError.value" icon="warning" title="無法載入卡片" />
        <div v-else class="card-picker-list picker-results">
          <PickerOption v-for="card in cards" :key="card.id" class="card-picker-item" :title="card.title" :multiple="multiple" :selected="multiple ? selectedCards.has(String(card.id)) : selectedCard?.id === card.id" :disabled="submitting || (multiple && selectedCards.size >= 100 && !selectedCards.has(String(card.id)))" @select="select(card)">
            <span>#{{ card.id }}</span>
            <span>{{ card.type === 'link' ? '連結' : '筆記' }}</span>
            <span v-if="card.isArchived">已封存</span>
            <span v-if="card.intervalDays == null">暫停回流</span>
            <span v-if="card.needsProcessing">待整理</span>
            <template #actions><el-button link type="primary" :disabled="submitting" :aria-label="`閱讀全文：${card.title}`" @click.stop="read(card)">閱讀全文</el-button></template>
          </PickerOption>
          <el-empty v-if="cards.length === 0" :image-size="72" :description="emptyDescription" />
          <div v-if="cardsQuery.hasNextPage.value" class="load-more-row">
            <el-button :loading="cardsQuery.isFetchingNextPage.value" @click="cardsQuery.fetchNextPage()">載入更多</el-button>
          </div>
        </div>
      <template #selection>
        <slot name="settings" :selected-card="selectedCard">
          <h3>已選卡片<template v-if="multiple">（{{ selectedCards.size }}／100）</template></h3>
          <template v-if="multiple">
            <CardPickerSelectionSummary v-if="!selectedCards.size" :card="null" />
            <div class="selected-card-list"><div v-for="card in selectedCards.values()" :key="card.id"><CardPickerSelectionSummary :card="card" /><el-button text size="small" :disabled="submitting" @click="select(card)">移除</el-button></div></div>
          </template>
          <CardPickerSelectionSummary v-else :card="selectedCard" />
          <p v-if="confirmationHint" class="settings-placeholder">{{ confirmationHint }}</p>
        </slot>
      </template>
    </PickerWorkspace>

    <template #footer="{ requestClose }">
      <el-button :disabled="submitting" @click="requestClose">取消</el-button>
      <el-button type="primary" :loading="submitting" :disabled="multiple ? !selectedCards.size : !selectedCard" @click="confirm">{{ confirmLabel }}<template v-if="multiple">（{{ selectedCards.size }}）</template></el-button>
    </template>
  </AppDialog>
  <SourceReadingDialog
    v-if="readingCard"
    :model-value="true"
    :source="toSourceReadingItem(readingCard)"
    close-label="返回選擇"
    @update:model-value="readingCard = null"
  />
</template>

<style scoped>
.picker-description { margin: 0 0 var(--space-sm); color: var(--el-text-color-secondary); font-size: var(--type-caption); line-height: var(--leading-ui); }
.picker-state { margin-top: var(--space-sm); }
.load-more-row { display: flex; justify-content: center; padding: var(--space-md) 0; }
.settings-placeholder { margin: var(--space-sm) 0 0; color: var(--el-text-color-secondary); font-size: var(--type-meta); line-height: var(--leading-ui); }
.selected-card-list { display: grid; gap: var(--space-sm); }
</style>
