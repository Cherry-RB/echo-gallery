import { defineComponent, h } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin, type InfiniteData } from '@tanstack/vue-query'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { CardDto, PageResponse, TodayBatchResponse } from '../types/card'
import { cardApi } from './api/cardApi'
import { todayBatchQueryKey } from './todayBatchCache'
import { useCardStatus } from './useCardStatus'

vi.mock('./api/cardApi', () => ({
  cardApi: {
    pauseCard: vi.fn(),
    resumeCard: vi.fn(),
    updateRecurrence: vi.fn(),
    snoozeCard: vi.fn(),
  },
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

describe('useCardStatus recurrence cache', () => {
  beforeEach(() => vi.clearAllMocks())

  it('暫停時清除回流設定並從 Today 移除', async () => {
    const { queryClient, status } = setup()
    const original = { ...card('1'), nextShowAt: '2026-09-03T00:00:00+08:00' }
    const paused = { ...original, intervalDays: null, nextShowAt: null }
    const infinite: InfiniteData<CardDto[]> = { pages: [[original]], pageParams: [1] }

    queryClient.setQueryData<TodayBatchResponse>(todayBatchQueryKey, {
      cards: [original], batchOfferedAt: '2026-08-24T12:00:00+08:00',
    })
    queryClient.setQueryData(['cards', 'all'], infinite)
    queryClient.setQueryData(['card', '1'], original)
    vi.mocked(cardApi.pauseCard).mockResolvedValue(paused)

    status.handlePauseCard({ id: '1' })
    await flushPromises()

    expect(cardApi.pauseCard).toHaveBeenCalledWith('1')
    expect(queryClient.getQueryData<TodayBatchResponse>(todayBatchQueryKey)?.cards).toEqual([])
    expect(queryClient.getQueryData<CardDto>(['card', '1']))
      .toMatchObject({ intervalDays: null, nextShowAt: null })
    expect(queryClient.getQueryState(['cards', 'all'])?.isInvalidated).toBe(true)
  })

  it('恢復時以後端排程更新詳情與列表快取', async () => {
    const { queryClient, status } = setup()
    const paused = { ...card('1'), intervalDays: null, nextShowAt: null }
    const resumed = { ...paused, intervalDays: 14, nextShowAt: '2026-09-07T00:00:00+08:00' }
    const infinite: InfiniteData<CardDto[]> = { pages: [[paused]], pageParams: [1] }

    queryClient.setQueryData(['cards', 'all'], infinite)
    queryClient.setQueryData(['card', '1'], paused)
    vi.mocked(cardApi.resumeCard).mockResolvedValue(resumed)

    status.handleResumeCard({ id: '1', intervalDays: 14 })
    await flushPromises()

    expect(cardApi.resumeCard).toHaveBeenCalledWith('1', 14)
    expect(queryClient.getQueryData<CardDto>(['card', '1']))
      .toMatchObject({ intervalDays: 14, nextShowAt: '2026-09-07T00:00:00+08:00' })
    expect(queryClient.getQueryState(['cards', 'all'])?.isInvalidated).toBe(true)
  })

  it('調整週期時以後端結果同步列表與詳情快取', async () => {
    const { queryClient, status } = setup()
    const original = { ...card('1'), intervalDays: 10, nextShowAt: '2026-09-03T00:00:00+08:00' }
    const updated = { ...original, intervalDays: 40, nextShowAt: '2026-10-03T00:00:00+08:00' }
    const infinite: InfiniteData<CardDto[]> = { pages: [[original]], pageParams: [1] }

    queryClient.setQueryData(['cards', 'all'], infinite)
    queryClient.setQueryData(['card', '1'], original)
    vi.mocked(cardApi.updateRecurrence).mockResolvedValue(updated)

    status.handleUpdateRecurrence({ id: '1', intervalDays: 40 })
    await flushPromises()

    expect(cardApi.updateRecurrence).toHaveBeenCalledWith('1', 40)
    expect(queryClient.getQueryData<CardDto>(['card', '1']))
      .toMatchObject({ intervalDays: 40, nextShowAt: '2026-10-03T00:00:00+08:00' })
    expect(queryClient.getQueryState(['cards', 'all'])?.isInvalidated).toBe(true)
  })
})
