<script setup lang="ts">
import { computed, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { MasonryWall } from '@yeger/vue-masonry-wall'
import { useRouter } from 'vue-router'
import CardItem from '../../components/CardItem.vue'
import type { CardDto, TodayBatchResponse } from '../../types/card'
import { cardApi } from '../../utils/api/cardApi'
import { useCardStatus } from '../../utils/useCardStatus'
import { resolveNextBatch, todayBatchQueryKey } from '../../utils/todayBatchCache'

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
  <section class="today-board">
    <header class="today-page-header">
      <h1 class="today-page-title">今日回流</h1>
      <p class="today-page-description">看看今天與哪些卡片再次相遇。</p>
    </header>

    <div class="board-surface">
      <div v-if="isLoading" class="state-message" v-loading="true">正在準備今天的卡片</div>

      <el-result
        v-else-if="isError"
        icon="error"
        title="今天的卡片載入失敗"
        sub-title="請稍後再試"
      >
        <template #extra><el-button type="primary" @click="refetch()">重新載入</el-button></template>
      </el-result>

      <template v-else>
        <masonry-wall v-if="cards.length" :items="cards" :column-width="270" :gap="12">
          <template #default="{ item }">
            <CardItem
              :data="item"
              view-mode="text"
              board-type="today"
              @open-detail="openDetail"
            />
          </template>
        </masonry-wall>

        <div v-else class="state-message">
          {{ hasBatch ? '目前這批已完成' : '今天目前沒有需要回流的卡片' }}
        </div>

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
  max-width: 1240px;
  margin: 0 auto;
}
.today-page-header {
  margin-bottom: 24px;
}
.today-page-title {
  margin: 0;
  font-size: var(--type-page-title);
  line-height: 1.35;
}
.today-page-description {
  margin: 8px 0 0;
  color: var(--el-text-color-secondary);
  font-size: var(--type-ui);
  line-height: var(--leading-ui);
}
.board-surface {
  min-height: calc(100dvh - 160px);
  padding: 12px;
  background: var(--el-bg-color-page);
}
.state-message {
  display: flex;
  min-height: 180px;
  align-items: center;
  justify-content: center;
  color: var(--el-text-color-secondary);
  text-align: center;
}
.batch-actions {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding-top: 24px;
}
.batch-action-hint {
  margin: 8px 0 0;
  color: var(--el-text-color-secondary);
  font-size: var(--type-caption);
  text-align: center;
}
.notice {
  margin: 20px 0 0;
  color: var(--el-text-color-secondary);
  text-align: center;
}
@media (max-width: 768px) {
  .today-board {
    max-width: none;
  }

  .today-page-header {
    margin-bottom: 16px;
  }
  .board-surface {
    min-height: calc(100dvh - 140px);
    margin-inline: -16px;
    padding: 12px 16px;
  }
}
</style>
