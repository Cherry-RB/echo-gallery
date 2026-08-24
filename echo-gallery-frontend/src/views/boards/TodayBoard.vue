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

const nextMutation = useMutation({
  mutationFn: (batchOfferedAt: string) => cardApi.nextToday(batchOfferedAt),
  onMutate: () => {
    actionError.value = ''
  },
  onSuccess: nextBatch => {
    const current = data.value
    if (!current) return
    const result = resolveNextBatch(current, nextBatch)
    queryClient.setQueryData<TodayBatchResponse>(todayBatchQueryKey, result.batch)
    noMoreCards.value = result.noMoreCards
  },
  onError: async (error: unknown) => {
    const status = (error as { response?: { status?: number } }).response?.status
    if (status === 409) {
      noMoreCards.value = false
      actionError.value = '批次已更新，已為你恢復目前內容'
      await refetch()
      return
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
    <header class="board-header">
      <h1>今日回流</h1>
      <p>看看今天與哪些卡片再次相遇。</p>
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
        <masonry-wall v-if="cards.length" :items="cards" :column-width="270" :gap="20">
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
            今天想多看一批
          </el-button>
        </div>
      </template>
    </div>
  </section>
</template>

<style scoped>
.today-board {
  width: 100%;
}
.board-header {
  padding: 10px 16px 18px;
  text-align: center;
}
.board-header h1 {
  margin: 0 0 10px;
}
.board-header p {
  margin: 0;
  color: var(--el-text-color-secondary);
}
.board-surface {
  padding: 20px;
  border-radius: 12px;
  background: var(--el-fill-color-extra-light);
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
  justify-content: center;
  padding-top: 24px;
}
.notice {
  margin: 20px 0 0;
  color: var(--el-text-color-secondary);
  text-align: center;
}
@media (max-width: 768px) {
  .board-header {
    padding-inline: 8px;
  }
  .board-surface {
    padding: 12px;
  }
}
</style>
