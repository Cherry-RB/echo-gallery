<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { useInfiniteQuery, useQuery } from '@tanstack/vue-query'
import { Search } from '@element-plus/icons-vue'
import type { CardDto, CardSearchParams } from '../types/card'
import { cardApi } from '../utils/api/cardApi'
import { normalizeIssueCardSearch } from '../utils/cardSearch'
import AppDialog from './AppDialog.vue'
import PickerWorkspace from './ui/PickerWorkspace.vue'
import PickerOption from './ui/PickerOption.vue'
import CardPickerSelectionSummary from './ui/CardPickerSelectionSummary.vue'

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
  if (visible && card && !selectionTouched.value && !excludedIds.value.has(String(card.id))) selectedCard.value = card
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
  selectionTouched.value = false
}

const confirm = () => {
  if (selectedCard.value && !props.submitting) emit('confirm', selectedCard.value)
}
</script>

<template>
  <AppDialog :busy="submitting" :draft="settingsDraft === undefined ? undefined : [settingsDraft, selectedCard?.id]"
    v-model="dialogVisible"
    :title="title"
    width="min(980px, calc(100vw - 32px))"
    mode="workspace"
    scroll-body
    @closed="reset"
  >
    <PickerWorkspace :settings-style="settingsStyle">
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
        <div v-else class="card-picker-list picker-results">
          <PickerOption v-for="card in cards" :key="card.id" class="card-picker-item" :title="card.title" :selected="selectedCard?.id === card.id" @select="selectedCard = card; selectionTouched = true">
            <span>#{{ card.id }}</span>
            <span>{{ card.type === 'link' ? '連結' : '筆記' }}</span>
            <span v-if="card.isArchived">已封存</span>
            <span v-if="card.intervalDays == null">暫停回流</span>
            <span v-if="card.needsProcessing">待整理</span>
          </PickerOption>
          <el-empty v-if="cards.length === 0" :image-size="72" :description="emptyDescription" />
          <div v-if="cardsQuery.hasNextPage.value" class="load-more-row">
            <el-button :loading="cardsQuery.isFetchingNextPage.value" @click="cardsQuery.fetchNextPage()">載入更多</el-button>
          </div>
        </div>
      <template #selection>
        <slot name="settings" :selected-card="selectedCard">
          <h3>已選卡片</h3>
          <CardPickerSelectionSummary :card="selectedCard" />
          <p v-if="confirmationHint" class="settings-placeholder">{{ confirmationHint }}</p>
        </slot>
      </template>
    </PickerWorkspace>

    <template #footer="{ requestClose }">
      <el-button :disabled="submitting" @click="requestClose">取消</el-button>
      <el-button type="primary" :loading="submitting" :disabled="!selectedCard" @click="confirm">{{ confirmLabel }}</el-button>
    </template>
  </AppDialog>
</template>

<style scoped>
.picker-description { margin: 0 0 var(--space-sm); color: var(--el-text-color-secondary); font-size: var(--type-caption); line-height: var(--leading-ui); }
.picker-state { margin-top: var(--space-sm); }
.load-more-row { display: flex; justify-content: center; padding: var(--space-md) 0; }
.settings-placeholder { margin: var(--space-sm) 0 0; color: var(--el-text-color-secondary); font-size: var(--type-meta); line-height: var(--leading-ui); }
</style>
