import { defineComponent, h } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin, type InfiniteData } from '@tanstack/vue-query'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { CardDto, PageResponse, TodayBatchResponse } from '../types/card'
import { cardApi } from './api/cardApi'
import { todayBatchQueryKey } from './todayBatchCache'
import { useCardStatus } from './useCardStatus'

vi.mock('./api/cardApi', () => ({
  cardApi: { snoozeCard: vi.fn() },
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn() },
}))

const card = (id: string): CardDto => ({
  id, type: 'note', title: `card-${id}`, tags: [], showContentPreview: false,
  intervalDays: 10, nextShowAt: null, openCount: 0, snoozeCount: 10,
  likeCount: 0, isArchived: false, growthStatus: 'UNMARKED', createdAt: '', updatedAt: '',
})

function setup() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  let status!: ReturnType<typeof useCardStatus>
  const Host = defineComponent({
    setup() {
      status = useCardStatus()
      return () => h('div')
    },
  })
  const wrapper = mount(Host, {
    global: { plugins: [[VueQueryPlugin, { queryClient }]] },
  })
  return { queryClient, status, wrapper }
}

describe('useCardStatus snooze cache', () => {
  beforeEach(() => vi.clearAllMocks())

  it('只從 Today 移除，保留其他列表並更新詳情', async () => {
    const { queryClient, status } = setup()
    const original = card('1')
    const updated = { ...original, snoozeCount: 11, nextShowAt: '2026-09-03T00:00:00+08:00' }
    const infinite: InfiniteData<CardDto[]> = { pages: [[original]], pageParams: [1] }
    const search: PageResponse<CardDto> = {
      content: [original], page: 0, size: 20, totalElements: 1, totalPages: 1,
    }

    queryClient.setQueryData<TodayBatchResponse>(todayBatchQueryKey, {
      cards: [original], batchOfferedAt: '2026-08-24T12:00:00+08:00',
    })
    queryClient.setQueryData(['cards', 'all'], infinite)
    queryClient.setQueryData(['cards', 'snoozed'], infinite)
    queryClient.setQueryData(['cards', 'search', 'conditions'], search)
    queryClient.setQueryData(['card', '1'], original)
    vi.mocked(cardApi.snoozeCard).mockResolvedValue(updated)

    status.handleSnoozeCard({ id: '1', nextIntervalDays: 5 })
    await flushPromises()

    expect(queryClient.getQueryData<TodayBatchResponse>(todayBatchQueryKey)?.cards).toEqual([])
    expect(queryClient.getQueryData(['cards', 'all'])).toEqual(infinite)
    expect(queryClient.getQueryData(['cards', 'snoozed'])).toEqual(infinite)
    expect(queryClient.getQueryData(['cards', 'search', 'conditions'])).toEqual(search)
    expect(queryClient.getQueryData<CardDto>(['card', '1'])?.snoozeCount).toBe(11)
    expect(queryClient.getQueryState(['cards', 'snoozed'])?.isInvalidated).toBe(true)
  })

  it('失敗時還原 Today 批次', async () => {
    const { queryClient, status } = setup()
    const original = card('1')
    const batch: TodayBatchResponse = {
      cards: [original], batchOfferedAt: '2026-08-24T12:00:00+08:00',
    }
    queryClient.setQueryData(todayBatchQueryKey, batch)
    vi.mocked(cardApi.snoozeCard).mockRejectedValue(new Error('failed'))

    status.handleSnoozeCard({ id: '1', nextIntervalDays: 5 })
    await flushPromises()

    expect(queryClient.getQueryData(todayBatchQueryKey)).toEqual(batch)
  })
})
