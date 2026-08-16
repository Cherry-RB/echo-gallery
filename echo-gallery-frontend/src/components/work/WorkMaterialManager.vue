<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { Check, Delete, Plus, RefreshLeft, Search } from '@element-plus/icons-vue'
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import type { CardDto, CardGrowthStatus, CardType } from '../../types/card'
import type { WorkCard, WorkCardStatus } from '../../types/work'
import { cardApi } from '../../utils/api/cardApi'
import { workApi } from '../../utils/api/workApi'
import { formatDate } from '../../utils/formatDate'

const props = defineProps<{ workId: string }>()
const router = useRouter()
const queryClient = useQueryClient()
const addCardDialogVisible = ref(false)
const searchInput = ref('')
const searchKeyword = ref('')
let searchTimer: ReturnType<typeof setTimeout> | undefined

const cardTypeMeta: Record<CardType, string> = {
  note: '筆記',
  link: '連結',
}

const growthStatusMeta: Record<CardGrowthStatus, { icon: string; label: string }> = {
  SEED: { icon: '🌱', label: '種子' },
  GROWING: { icon: '🌿', label: '生長' },
  MATURE: { icon: '🌳', label: '成熟' },
}

const {
  data: workCards,
  isLoading: areWorkCardsLoading,
  isError: areWorkCardsError,
  refetch: refetchWorkCards,
} = useQuery({
  queryKey: computed(() => ['workCards', String(props.workId)]),
  queryFn: () => workApi.getWorkCards(props.workId),
})

const candidateCards = computed(() =>
  (workCards.value ?? []).filter((item) => item.status === 'CANDIDATE'),
)
const usedCards = computed(() =>
  (workCards.value ?? []).filter((item) => item.status === 'USED'),
)
const linkedCardIds = computed(() =>
  new Set((workCards.value ?? []).map((item) => String(item.cardId))),
)

watch(searchInput, (value) => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    searchKeyword.value = value.trim()
  }, 300)
})

onUnmounted(() => {
  if (searchTimer) clearTimeout(searchTimer)
})

const {
  data: cardPages,
  isLoading: areCardsLoading,
  isError: areCardsError,
  hasNextPage,
  isFetchingNextPage,
  fetchNextPage,
} = useInfiniteQuery({
  queryKey: computed(() => ['cards', 'work-picker', searchKeyword.value]),
  queryFn: async ({ pageParam }): Promise<CardDto[]> => {
    if (searchKeyword.value) {
      return cardApi.searchCards({
        keyword: searchKeyword.value,
        pageNumber: pageParam,
        pageSize: 20,
      })
    }
    return cardApi.getCards({
        pageNumber: pageParam,
        pageSize: 20,
        boardType: 'all',
      })
  },
  initialPageParam: 1,
  getNextPageParam: (lastPage, pages) => lastPage.length === 20 ? pages.length + 1 : undefined,
  enabled: computed(() => addCardDialogVisible.value),
})

const availableCards = computed(() =>
  (cardPages.value?.pages ?? [])
    .flat()
    .filter((card) => !linkedCardIds.value.has(String(card.id))),
)

const refreshMaterialQueries = async () => {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: ['workCards', String(props.workId)] }),
    queryClient.invalidateQueries({ queryKey: ['works'] }),
  ])
}

const addCardMutation = useMutation({
  mutationFn: (card: CardDto) => workApi.addWorkCard(props.workId, { cardId: Number(card.id) }),
  onSuccess: async () => {
    await refreshMaterialQueries()
    ElMessage.success('卡片已加入候選素材')
    addCardDialogVisible.value = false
  },
})

const statusMutation = useMutation({
  mutationFn: ({ card, status }: { card: WorkCard; status: WorkCardStatus }) =>
    workApi.updateWorkCardStatus(props.workId, card.cardId, { status }),
  onSuccess: async (_updatedCard, variables) => {
    await refreshMaterialQueries()
    ElMessage.success(variables.status === 'USED' ? '已標記為採用' : '已移回候選素材')
  },
})

const removeCardMutation = useMutation({
  mutationFn: (card: WorkCard) => workApi.removeWorkCard(props.workId, card.cardId),
  onSuccess: async () => {
    await refreshMaterialQueries()
    ElMessage.success('已解除卡片與作品的關聯')
  },
})

const openCard = (cardId: number) => {
  router.push({
    name: 'CardDetail',
    params: { id: cardId },
    query: { fromWork: props.workId },
  })
}

const confirmRemoveCard = async (card: WorkCard) => {
  try {
    await ElMessageBox.confirm(
      `確定要從作品中移除「${card.cardTitle}」嗎？卡片本身不會被刪除。`,
      '解除素材關聯',
      {
        confirmButtonText: '解除關聯',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
    removeCardMutation.mutate(card)
  } catch {
    // 使用者取消時不需要額外提示。
  }
}

const resetCardSearch = () => {
  if (searchTimer) clearTimeout(searchTimer)
  searchInput.value = ''
  searchKeyword.value = ''
}
</script>

<template>
  <section class="material-surface" aria-labelledby="material-heading">
    <header class="material-heading-row">
      <div>
        <h2 id="material-heading">作品素材</h2>
        <p>整理準備使用與已採用的卡片</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="addCardDialogVisible = true">
        加入卡片
      </el-button>
    </header>

    <div v-if="areWorkCardsLoading" aria-label="作品素材載入中">
      <el-skeleton :rows="5" animated />
    </div>

    <el-result
      v-else-if="areWorkCardsError"
      icon="warning"
      title="無法載入作品素材"
    >
      <template #extra>
        <el-button type="primary" @click="refetchWorkCards()">重新載入</el-button>
      </template>
    </el-result>

    <div v-else class="material-columns">
      <section class="material-column" aria-labelledby="candidate-heading">
        <header class="column-heading">
          <h3 id="candidate-heading">候選素材</h3>
          <el-tag type="info" round>{{ candidateCards.length }}</el-tag>
        </header>

        <el-empty
          v-if="candidateCards.length === 0"
          :image-size="72"
          description="目前沒有候選素材"
        />

        <article v-for="card in candidateCards" :key="card.id" class="material-card">
          <div class="material-card-main">
            <button class="material-card-title" type="button" @click="openCard(card.cardId)">
              {{ card.cardTitle }}
            </button>
            <div class="card-metadata">
              <span>{{ cardTypeMeta[card.cardType] }}</span>
              <el-tooltip :content="growthStatusMeta[card.cardGrowthStatus].label">
                <span class="growth-icon" role="img" :aria-label="growthStatusMeta[card.cardGrowthStatus].label">
                  {{ growthStatusMeta[card.cardGrowthStatus].icon }}
                </span>
              </el-tooltip>
              <span>加入於 {{ formatDate(card.linkedAt) }}</span>
            </div>
            <div v-if="card.tags.length" class="card-tags">
              <el-tag v-for="tag in card.tags" :key="tag" size="small" effect="plain">
                #{{ tag }}
              </el-tag>
            </div>
            <p v-if="card.note" class="material-note">{{ card.note }}</p>
          </div>
          <div class="material-actions">
            <el-button
              type="success"
              plain
              size="small"
              :icon="Check"
              :loading="statusMutation.isPending.value"
              @click="statusMutation.mutate({ card, status: 'USED' })"
            >
              採用
            </el-button>
            <el-button
              type="danger"
              text
              size="small"
              :icon="Delete"
              :disabled="removeCardMutation.isPending.value"
              @click="confirmRemoveCard(card)"
            >
              移除
            </el-button>
          </div>
        </article>
      </section>

      <section class="material-column used-column" aria-labelledby="used-heading">
        <header class="column-heading">
          <h3 id="used-heading">已採用</h3>
          <el-tag type="success" round>{{ usedCards.length }}</el-tag>
        </header>

        <el-empty
          v-if="usedCards.length === 0"
          :image-size="72"
          description="目前沒有已採用素材"
        />

        <article v-for="card in usedCards" :key="card.id" class="material-card">
          <div class="material-card-main">
            <button class="material-card-title" type="button" @click="openCard(card.cardId)">
              {{ card.cardTitle }}
            </button>
            <div class="card-metadata">
              <span>{{ cardTypeMeta[card.cardType] }}</span>
              <el-tooltip :content="growthStatusMeta[card.cardGrowthStatus].label">
                <span class="growth-icon" role="img" :aria-label="growthStatusMeta[card.cardGrowthStatus].label">
                  {{ growthStatusMeta[card.cardGrowthStatus].icon }}
                </span>
              </el-tooltip>
              <span v-if="card.usedAt">採用於 {{ formatDate(card.usedAt) }}</span>
            </div>
            <div v-if="card.tags.length" class="card-tags">
              <el-tag v-for="tag in card.tags" :key="tag" size="small" effect="plain">
                #{{ tag }}
              </el-tag>
            </div>
            <p v-if="card.note" class="material-note">{{ card.note }}</p>
          </div>
          <div class="material-actions">
            <el-button
              plain
              size="small"
              :icon="RefreshLeft"
              :loading="statusMutation.isPending.value"
              @click="statusMutation.mutate({ card, status: 'CANDIDATE' })"
            >
              移回候選
            </el-button>
            <el-button
              type="danger"
              text
              size="small"
              :icon="Delete"
              :disabled="removeCardMutation.isPending.value"
              @click="confirmRemoveCard(card)"
            >
              移除
            </el-button>
          </div>
        </article>
      </section>
    </div>

    <el-dialog
      v-model="addCardDialogVisible"
      title="加入作品素材"
      width="min(680px, calc(100vw - 32px))"
      destroy-on-close
      @closed="resetCardSearch"
    >
      <p class="picker-description">選擇一張卡片加入候選素材；已加入此作品的卡片不會重複顯示。</p>

      <el-input
        v-model="searchInput"
        class="card-search-input"
        :prefix-icon="Search"
        maxlength="255"
        clearable
        placeholder="輸入 Card ID（例如 #7）或標題"
        aria-label="搜尋可加入的卡片"
      />

      <div v-if="areCardsLoading" aria-label="卡片列表載入中">
        <el-skeleton :rows="6" animated />
      </div>

      <el-result
        v-else-if="areCardsError"
        icon="warning"
        title="無法載入卡片"
      />

      <div v-else class="card-picker-list">
        <div v-for="card in availableCards" :key="card.id" class="card-picker-item">
          <div class="picker-card-content">
            <strong>{{ card.title }}</strong>
            <div class="card-metadata">
              <span class="card-id">#{{ card.id }}</span>
              <span>{{ cardTypeMeta[card.type] }}</span>
              <el-tooltip :content="growthStatusMeta[card.growthStatus ?? 'SEED'].label">
                <span
                  class="growth-icon"
                  role="img"
                  :aria-label="growthStatusMeta[card.growthStatus ?? 'SEED'].label"
                >
                  {{ growthStatusMeta[card.growthStatus ?? 'SEED'].icon }}
                </span>
              </el-tooltip>
            </div>
          </div>
          <el-button
            type="primary"
            plain
            size="small"
            :loading="addCardMutation.isPending.value"
            @click="addCardMutation.mutate(card)"
          >
            加入
          </el-button>
        </div>

        <el-empty
          v-if="availableCards.length === 0"
          :image-size="72"
          :description="hasNextPage ? '目前載入的卡片皆已加入作品，可繼續載入更多' : '沒有其他可加入的卡片'"
        />

        <div v-if="hasNextPage" class="load-more-row">
          <el-button :loading="isFetchingNextPage" @click="fetchNextPage()">
            載入更多
          </el-button>
        </div>
      </div>
    </el-dialog>
  </section>
</template>

<style scoped>
.material-surface {
  margin-top: 20px;
  padding: 28px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-bg-color);
  box-shadow: var(--el-box-shadow-lighter);
}

.material-heading-row,
.column-heading,
.material-card,
.card-picker-item {
  display: flex;
  align-items: center;
}

.material-heading-row {
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
}

.material-heading-row h2,
.column-heading h3 {
  margin: 0;
}

.material-heading-row h2 {
  font-size: 21px;
}

.material-heading-row p,
.picker-description {
  margin: 6px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.material-columns {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
  align-items: start;
}

.material-column {
  min-width: 0;
  padding: 18px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-extra-light);
}

.used-column {
  background: var(--el-color-success-light-9);
}

.column-heading {
  gap: 8px;
  margin-bottom: 14px;
}

.column-heading h3 {
  font-size: 16px;
}

.material-card {
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 7px;
  background: var(--el-bg-color);
}

.material-card + .material-card {
  margin-top: 10px;
}

.material-card-main,
.picker-card-content {
  min-width: 0;
}

.material-card-title {
  max-width: 100%;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--el-text-color-primary);
  font: inherit;
  font-weight: 600;
  line-height: 1.5;
  text-align: left;
  overflow-wrap: anywhere;
  cursor: pointer;
}

.material-card-title:hover,
.material-card-title:focus-visible {
  color: var(--el-color-primary);
}

.card-metadata {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px 10px;
  margin-top: 7px;
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}

.growth-icon {
  line-height: 1;
}

.card-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 9px;
}

.material-note {
  margin: 10px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.6;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.material-actions {
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  align-items: stretch;
  gap: 4px;
}

.material-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.picker-description {
  margin: -8px 0 16px;
}

.card-search-input {
  margin-bottom: 12px;
}

.card-id {
  font-variant-numeric: tabular-nums;
}

.card-picker-list {
  max-height: min(60vh, 560px);
  overflow-y: auto;
}

.card-picker-item {
  justify-content: space-between;
  gap: 16px;
  padding: 13px 4px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.picker-card-content strong {
  display: block;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.load-more-row {
  display: flex;
  justify-content: center;
  padding-top: 18px;
}

@media (max-width: 800px) {
  .material-columns {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 600px) {
  .material-surface {
    padding: 20px 16px;
  }

  .material-heading-row {
    flex-direction: column;
    gap: 12px;
  }

  .material-column {
    padding: 14px;
  }

  .material-card {
    flex-direction: column;
  }

  .material-actions {
    width: 100%;
    flex-direction: row;
  }
}
</style>
