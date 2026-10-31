import { defineComponent, h } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin, type InfiniteData } from '@tanstack/vue-query'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ElMessage } from 'element-plus'
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
  likeCount: 0, isArchived: false, createdAt: '', updatedAt: '',
})

function boardCache(cards: CardDto[]) {
  return {
    pages: [{ content: cards, nextPageParam: { pageNumber: 2 } }],
    pageParams: [{ pageNumber: 1 }],
  }
}

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
    const infinite = boardCache([original])
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

  it('調整週期時同步共存的分頁格式，保留游標及其他卡片', async () => {
    const { queryClient, status } = setup()
    const original = card('1')
    const other = card('2')
    const updated = { ...original, intervalDays: 30, nextShowAt: '2026-09-23T00:00:00+08:00' }
    const pickerPage: PageResponse<CardDto> = {
      content: [original, other], page: 0, size: 20, totalElements: 2, totalPages: 1,
    }
    const pickerKey = ['cards', 'shared-picker', 'ALL', '']
    const pickerCache: InfiniteData<PageResponse<CardDto>> = { pages: [pickerPage], pageParams: [0] }
    const legacy: InfiniteData<CardDto[]> = { pages: [[original, other]], pageParams: [1] }
    const board = boardCache([other])
    board.pages.push({ content: [original], nextPageParam: { pageNumber: 3 } })
    board.pageParams.push({ pageNumber: 2 })
    queryClient.setQueryData(['cards', 'all', {}], board)
    queryClient.setQueryData(['cards', 'random', {}], boardCache([]))
    queryClient.setQueryData(pickerKey, pickerCache)
    queryClient.setQueryData(['cards', 'search', {}], pickerPage)
    queryClient.setQueryData(['cards', 'filtered'], [original, other])
    queryClient.setQueryData(['cards', 'legacy'], legacy)
    queryClient.setQueryData(['card', '1'], original)
    vi.mocked(cardApi.updateRecurrence).mockResolvedValue(updated)

    status.handleUpdateRecurrence({ id: '1', intervalDays: 30 })
    await flushPromises()

    const updatedPage = { ...pickerPage, content: [updated, other] }
    expect(cardApi.updateRecurrence).toHaveBeenCalledWith('1', 30, false)
    expect(queryClient.getQueryData(['cards', 'all', {}])).toEqual({
      ...board,
      pages: [board.pages[0], { ...board.pages[1], content: [updated] }],
    })
    expect(queryClient.getQueryData(['cards', 'random', {}])).toEqual(boardCache([]))
    expect(queryClient.getQueryData(pickerKey)).toEqual({ ...pickerCache, pages: [updatedPage] })
    expect(queryClient.getQueryData(['cards', 'search', {}])).toEqual(updatedPage)
    expect(queryClient.getQueryData(['cards', 'filtered'])).toEqual([updated, other])
    expect(queryClient.getQueryData(['cards', 'legacy'])).toEqual({ ...legacy, pages: [[updated, other]] })
    expect(queryClient.getQueryData(['card', '1'])).toEqual(updated)
    expect(ElMessage.success).toHaveBeenCalledWith('已調整為每 30 天回流')
    expect(ElMessage.error).not.toHaveBeenCalled()
  })

  it.each(['pause', 'resume', 'recurrence'] as const)('%s API 失敗時還原物件分頁、詳情及今日批次', async (action) => {
    const { queryClient, status } = setup()
    const original = card('1')
    const board = boardCache([original])
    const batch: TodayBatchResponse = { cards: [original], batchOfferedAt: null }
    const error = { response: { data: { message: '儲存失敗' } } }
    queryClient.setQueryData(['cards', 'all'], board)
    queryClient.setQueryData(['card', '1'], original)
    queryClient.setQueryData(todayBatchQueryKey, batch)
    if (action === 'pause') {
      vi.mocked(cardApi.pauseCard).mockRejectedValue(error)
      status.handlePauseCard({ id: '1' })
    } else if (action === 'resume') {
      vi.mocked(cardApi.resumeCard).mockRejectedValue(error)
      status.handleResumeCard({ id: '1', intervalDays: 30 })
    } else {
      vi.mocked(cardApi.updateRecurrence).mockRejectedValue(error)
      status.handleUpdateRecurrence({ id: '1', intervalDays: 30, deferCurrentOccurrence: true })
    }
    await flushPromises()

    expect(queryClient.getQueryData(['cards', 'all'])).toEqual(board)
    expect(queryClient.getQueryData(['card', '1'])).toEqual(original)
    expect(queryClient.getQueryData(todayBatchQueryKey)).toEqual(batch)
    expect(ElMessage.error).toHaveBeenCalledWith('儲存失敗')
    expect(ElMessage.success).not.toHaveBeenCalled()
  })

  it('暫停時清除回流設定並從 Today 移除', async () => {
    const { queryClient, status } = setup()
    const original = { ...card('1'), nextShowAt: '2026-09-03T00:00:00+08:00' }
    const paused = { ...original, intervalDays: null, nextShowAt: null }
    const infinite = boardCache([original])

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
    expect(queryClient.getQueryData(['cards', 'all']))
      .toEqual(boardCache([paused]))
    expect(ElMessage.error).not.toHaveBeenCalled()
    expect(queryClient.getQueryState(['cards', 'all'])?.isInvalidated).toBe(true)
  })

  it('恢復時以後端排程更新詳情與列表快取', async () => {
    const { queryClient, status } = setup()
    const paused = { ...card('1'), intervalDays: null, nextShowAt: null }
    const resumed = { ...paused, intervalDays: 14, nextShowAt: '2026-09-07T00:00:00+08:00' }
    const infinite = boardCache([paused])

    queryClient.setQueryData(['cards', 'all'], infinite)
    queryClient.setQueryData(['card', '1'], paused)
    vi.mocked(cardApi.resumeCard).mockResolvedValue(resumed)

    status.handleResumeCard({ id: '1', intervalDays: 14 })
    await flushPromises()

    expect(cardApi.resumeCard).toHaveBeenCalledWith('1', 14)
    expect(queryClient.getQueryData<CardDto>(['card', '1']))
      .toMatchObject({ intervalDays: 14, nextShowAt: '2026-09-07T00:00:00+08:00' })
    expect(queryClient.getQueryData(['cards', 'all']))
      .toEqual(boardCache([resumed]))
    expect(ElMessage.error).not.toHaveBeenCalled()
    expect(queryClient.getQueryState(['cards', 'all'])?.isInvalidated).toBe(true)
  })

  it('調整週期時以後端結果同步列表與詳情快取', async () => {
    const { queryClient, status } = setup()
    const original = { ...card('1'), intervalDays: 10, nextShowAt: '2026-09-03T00:00:00+08:00' }
    const updated = { ...original, intervalDays: 40, nextShowAt: '2026-10-03T00:00:00+08:00' }
    const infinite = boardCache([original])

    queryClient.setQueryData(['cards', 'all'], infinite)
    queryClient.setQueryData(['card', '1'], original)
    vi.mocked(cardApi.updateRecurrence).mockResolvedValue(updated)

    status.handleUpdateRecurrence({ id: '1', intervalDays: 40 })
    await flushPromises()

    expect(cardApi.updateRecurrence).toHaveBeenCalledWith('1', 40, false)
    expect(queryClient.getQueryData<CardDto>(['card', '1']))
      .toMatchObject({ intervalDays: 40, nextShowAt: '2026-10-03T00:00:00+08:00' })
    expect(queryClient.getQueryData(['cards', 'all']))
      .toEqual(boardCache([updated]))
    expect(ElMessage.success).toHaveBeenCalledWith('已調整為每 40 天回流')
    expect(ElMessage.error).not.toHaveBeenCalled()
    expect(queryClient.getQueryState(['cards', 'all'])?.isInvalidated).toBe(true)
  })

  it('從 Today 調整週期時視為稍後再看並立即移除', async () => {
    const { queryClient, status } = setup()
    const original = { ...card('1'), intervalDays: 10, nextShowAt: '2026-09-03T00:00:00+08:00' }
    const updated = {
      ...original,
      intervalDays: 40,
      nextShowAt: '2026-10-03T00:00:00+08:00',
      snoozeCount: 11,
    }
    queryClient.setQueryData<TodayBatchResponse>(todayBatchQueryKey, {
      cards: [original], batchOfferedAt: '2026-08-24T12:00:00+08:00',
    })
    queryClient.setQueryData(['card', '1'], original)
    vi.mocked(cardApi.updateRecurrence).mockResolvedValue(updated)

    queryClient.setQueryData(['cards', 'all'], boardCache([original]))
    status.handleUpdateRecurrence({ id: '1', intervalDays: 40, deferCurrentOccurrence: true })
    await flushPromises()

    expect(cardApi.updateRecurrence).toHaveBeenCalledWith('1', 40, true)
    expect(queryClient.getQueryData<TodayBatchResponse>(todayBatchQueryKey)?.cards).toEqual([])
    expect(queryClient.getQueryData<CardDto>(['card', '1']))
      .toMatchObject({ intervalDays: 40, snoozeCount: 11 })
    expect(queryClient.getQueryData(['cards', 'all'])).toEqual(boardCache([updated]))
    expect(ElMessage.success).toHaveBeenCalledWith('已稍後再看，將於 40 天後回流')
    expect(ElMessage.error).not.toHaveBeenCalled()
  })

  it('從 Today 調整週期失敗時還原批次', async () => {
    const { queryClient, status } = setup()
    const original = card('1')
    const batch: TodayBatchResponse = {
      cards: [original], batchOfferedAt: '2026-08-24T12:00:00+08:00',
    }
    queryClient.setQueryData(todayBatchQueryKey, batch)
    vi.mocked(cardApi.updateRecurrence).mockRejectedValue(new Error('failed'))

    status.handleUpdateRecurrence({ id: '1', intervalDays: 40, deferCurrentOccurrence: true })
    await flushPromises()

    expect(queryClient.getQueryData(todayBatchQueryKey)).toEqual(batch)
  })
})
