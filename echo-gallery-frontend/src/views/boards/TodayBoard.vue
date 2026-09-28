<script setup lang="ts">
import { computed, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { MasonryWall } from '@yeger/vue-masonry-wall'
import { useRouter } from 'vue-router'
import CardItem from '../../components/CardItem.vue'
import PageHeader from '../../components/ui/PageHeader.vue'
import AppEmptyState from '../../components/ui/AppEmptyState.vue'
import type { CardDto, TodayBatchResponse } from '../../types/card'
import { cardApi } from '../../utils/api/cardApi'
import { useCardStatus } from '../../utils/useCardStatus'
import { resolveNextBatch, todayBatchQueryKey } from '../../utils/todayBatchCache'
import { collectionLayoutTokens } from '../../utils/designTokens'

const router = useRouter()
const queryClient = useQueryClient()
const noMoreCards = ref(false)
const actionError = ref('')

const { data, isLoading, isError, refetch } = useQuery({
  queryKey: todayBatchQueryKey,
  queryFn: cardApi.prepareToday,
  retry: 2,
  retryDelay: 0,
  refetchOnWindowFocus: false,
})

const cards = computed(() => data.value?.cards ?? [])
const hasBatch = computed(() => Boolean(data.value?.batchOfferedAt))
const hasRemainingCards = computed(() => cards.value.length > 0)

const nextMutation = useMutation({
  mutationFn: (batchOfferedAt: string) => cardApi.nextToday(batchOfferedAt),
  onMutate: async () => {
    actionError.value = ''
    await queryClient.cancelQueries({ queryKey: todayBatchQueryKey })
    const previousBatch = queryClient.getQueryData<TodayBatchResponse>(todayBatchQueryKey)
    if (previousBatch) {
      queryClient.setQueryData<TodayBatchResponse>(todayBatchQueryKey, {
        ...previousBatch,
        cards: [],
      })
    }
    return { previousBatch }
  },
  onSuccess: (nextBatch, _batchOfferedAt, context) => {
    const result = resolveNextBatch(nextBatch)
    queryClient.setQueryData<TodayBatchResponse>(todayBatchQueryKey, result.batch)
    noMoreCards.value = result.noMoreCards
    context?.previousBatch?.cards.forEach(card => {
      queryClient.invalidateQueries({ queryKey: ['card', String(card.id)] })
    })
    queryClient.invalidateQueries({ queryKey: ['cards'] })
    queryClient.invalidateQueries({ queryKey: ['sidebar'] })
  },
  onError: async (error: unknown, _batchOfferedAt, context) => {
    const status = (error as { response?: { status?: number } }).response?.status
    if (status === 409) {
      noMoreCards.value = false
      actionError.value = '批次已更新，已為你恢復目前內容'
      await refetch()
      return
    }
    if (context?.previousBatch) {
      queryClient.setQueryData(todayBatchQueryKey, context.previousBatch)
    }
    actionError.value = '無法取得下一批，請稍後再試'
  },
})
const isNextPending = nextMutation.isPending

const { handleReadCard, isReadPending } = useCardStatus()

function openDetail(card: CardDto) {
  handleReadCard(
    { id: card.id, sourceBoard: 'today' },
    {
      onSuccess: () => router.push({
        name: 'CardDetail',
        params: { id: card.id },
        query: { from: 'today' },
      }),
    },
  )
}

function requestNextBatch() {
  const batchOfferedAt = data.value?.batchOfferedAt
  if (!batchOfferedAt || nextMutation.isPending.value) return
  nextMutation.mutate(batchOfferedAt)
}
</script>

<template>
  <section class="today-board app-page app-page--collection">
    <PageHeader title="今日回流" description="看看今天與哪些卡片再次相遇。" />

    <div class="board-surface app-workspace app-workspace--edge-to-edge">
      <div v-if="isLoading" class="app-empty-state today-loading-state" v-loading="true">正在準備今天的卡片</div>

      <el-result
        v-else-if="isError"
        icon="error"
        title="今天的卡片載入失敗"
        sub-title="請稍後再試"
      >
        <template #extra><el-button type="primary" @click="refetch()">重新載入</el-button></template>
      </el-result>

      <template v-else>
        <masonry-wall v-if="cards.length" :items="cards" :column-width="collectionLayoutTokens.masonryColumnWidth" :gap="collectionLayoutTokens.gridGap">
          <template #default="{ item }">
            <CardItem
              :data="item"
              view-mode="text"
              board-type="today"
              @open-detail="openDetail"
            />
          </template>
        </masonry-wall>

        <AppEmptyState
          v-else
          :description="hasBatch ? '目前這批已完成' : '今天目前沒有需要回流的卡片'"
        />

        <p v-if="noMoreCards" class="notice">今天沒有更多新卡片了</p>
        <p v-if="actionError" class="notice">{{ actionError }}</p>

        <div v-if="hasBatch" class="batch-actions">
          <el-button
            type="primary"
            plain
            :loading="isNextPending"
            :disabled="isNextPending || isReadPending"
            @click="requestNextBatch"
          >
            {{ hasRemainingCards ? '略過這批，再看一批' : '再看一批' }}
          </el-button>
          <p v-if="hasRemainingCards" class="batch-action-hint">
            尚未查看的卡片會依各自的回流天數重新安排。
          </p>
        </div>
      </template>
    </div>
  </section>
</template>

<style scoped>
.today-board {
  width: 100%;
}
.board-surface {
  min-height: 280px;
}
.batch-actions {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding-top: var(--space-lg);
}
.batch-action-hint {
  margin: var(--space-xs) 0 0;
  color: var(--el-text-color-secondary);
  font-size: var(--type-secondary);
  text-align: center;
}
.notice {
  margin: var(--space-md) 0 0;
  color: var(--el-text-color-secondary);
  text-align: center;
}
@media (max-width: 760px) {
  .board-surface {
    min-height: 240px;
  }
}
</style>
