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
    deleteCard: vi.fn(),
    toggleCardStar: vi.fn(),
    toggleArchive: vi.fn(),
    readCard: vi.fn(),
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

const cacheFormats = {
  board: (cards: CardDto[]) => boardCache(cards),
  picker: (cards: CardDto[]) => ({ pages: [{ content: cards, totalElements: 2, page: 0 }], pageParams: [0] }),
  legacy: (cards: CardDto[]) => ({ pages: [cards], pageParams: [1] }),
  search: (cards: CardDto[]) => ({ content: cards, totalElements: 2, page: 0 }),
  array: (cards: CardDto[]) => cards,
}

describe('卡片操作共用快取格式與詳情 ID', () => {
  beforeEach(() => vi.clearAllMocks())

  it.each(Object.entries(cacheFormats))('按讚從 %s 取得正確次數，不被第一個無關列表影響', async (_name, format) => {
    const { queryClient, status, wrapper } = setup()
    const original = { ...card('1'), likeCount: 7 }, other = card('2')
    const updated = { ...original, likeCount: 8, likeAvailableAt: '2026-11-09T00:00:00Z' }
    queryClient.setQueryData(['cards', 'unrelated'], boardCache([other]))
    queryClient.setQueryData(['cards', 'target'], format([original, other]))
    vi.mocked(cardApi.toggleCardStar).mockImplementation(async () => {
      expect(queryClient.getQueryData(['cards', 'target'])).toEqual(format([{ ...original, likeCount: 8, likeAvailableAt: expect.any(String) }, other]))
      return updated
    })
    try {
      status.handleToggleStar({ id: 1, starStatus: true })
      await flushPromises()
      expect(cardApi.toggleCardStar).toHaveBeenCalledWith(1, { starStatus: true })
      expect(queryClient.getQueryData(['cards', 'target'])).toEqual(format([updated, other]))
      expect(queryClient.getQueryState(['cards', 'target'])?.isInvalidated).toBe(true)
      expect(ElMessage.error).not.toHaveBeenCalled()
    } finally { wrapper.unmount(); queryClient.clear() }
  })

  it('只有今日批次時按讚仍從目前次數累加', async () => {
    const { queryClient, status, wrapper } = setup()
    const original = { ...card('1'), likeCount: 9 }
    queryClient.setQueryData(todayBatchQueryKey, { cards: [original], batchOfferedAt: null })
    vi.mocked(cardApi.toggleCardStar).mockImplementation(async () => {
      expect(queryClient.getQueryData<TodayBatchResponse>(todayBatchQueryKey)?.cards[0]?.likeCount).toBe(10)
      return { ...original, likeCount: 10 }
    })
    try {
      status.handleToggleStar({ id: 1, starStatus: true })
      await flushPromises()
      expect(cardApi.toggleCardStar).toHaveBeenCalled()
      expect(ElMessage.error).not.toHaveBeenCalled()
    } finally { wrapper.unmount(); queryClient.clear() }
  })

  it.each(['star', 'archive', 'read'] as const)('%s 失敗時以數字 ID 還原字串 key 的詳情、列表及今日批次', async action => {
    const { queryClient, status, wrapper } = setup()
    const original = { ...card('1'), likeCount: 7 }, other = card('2')
    const lists = boardCache([original, other])
    const batch = { cards: [original, other], batchOfferedAt: null }
    queryClient.setQueryData(['cards', 'today'], lists)
    queryClient.setQueryData(['card', '1'], original)
    queryClient.setQueryData(todayBatchQueryKey, batch)
    const error = new Error('操作失敗')
    try {
      if (action === 'star') {
        vi.mocked(cardApi.toggleCardStar).mockRejectedValue(error)
        status.handleToggleStar({ id: 1, starStatus: true })
      } else if (action === 'archive') {
        vi.mocked(cardApi.toggleArchive).mockRejectedValue(error)
        status.handleToggleArchive({ id: 1, archivedStatus: true })
      } else {
        vi.mocked(cardApi.readCard).mockRejectedValue(error)
        status.handleReadCard({ id: 1, sourceBoard: 'today' })
      }
      await flushPromises()
      expect(queryClient.getQueryData(['card', '1'])).toEqual(original)
      expect(queryClient.getQueryData(['card', 1])).toBeUndefined()
      expect(queryClient.getQueryData(['cards', 'today'])).toEqual(lists)
      expect(queryClient.getQueryData(todayBatchQueryKey)).toEqual(batch)
      expect(ElMessage.error).toHaveBeenCalled()
    } finally { wrapper.unmount(); queryClient.clear() }
  })

  it.each(Object.entries(cacheFormats))('已讀相容 %s 分頁，保留其他卡片及成功導頁回呼', async (_name, format) => {
    const { queryClient, status, wrapper } = setup()
    const original = card('1'), other = card('2'), updated = { ...original, openCount: 1 }
    queryClient.setQueryData(['cards', 'today'], format([original, other]))
    queryClient.setQueryData(['cards', 'all'], boardCache([original, other]))
    queryClient.setQueryData(todayBatchQueryKey, { cards: [original, other], batchOfferedAt: null })
    const navigate = vi.fn()
    vi.mocked(cardApi.readCard).mockImplementation(async () => {
      expect(queryClient.getQueryData(['cards', 'today'])).toEqual(format([other]))
      return updated
    })
    try {
      status.handleReadCard({ id: 1, sourceBoard: 'today' }, { onSuccess: navigate })
      await flushPromises()
      expect(cardApi.readCard).toHaveBeenCalledWith(1)
      expect(navigate).toHaveBeenCalledOnce()
      expect(queryClient.getQueryData(['card', '1'])).toEqual(updated)
      expect(queryClient.getQueryData(['cards', 'all'])).toEqual(boardCache([updated, other]))
      expect(queryClient.getQueryData<TodayBatchResponse>(todayBatchQueryKey)?.cards).toEqual([other])
      expect(ElMessage.error).not.toHaveBeenCalled()
    } finally { wrapper.unmount(); queryClient.clear() }
  })

  it.each(['archive', 'restore', 'delete'] as const)('%s 成功後讓來源與所有相關列表重新查核', async action => {
    const { queryClient, status, wrapper } = setup()
    const keys = [['cards', 'all'], ['cards', 'archived'], ['tags', 'center'], ['overview', 'current'],
      ['experiment', 1], ['experiment-thread-summaries', 1], ['experiment-thread', 1, 3],
      ['works', 'sources', 1], ['work-candidates'], ['goals', 'owner', 'sources', 1], ['treasures', 1]]
    keys.forEach(key => queryClient.setQueryData(key, { content: [] }))
    const updated = { ...card('1'), isArchived: action === 'archive' }
    try {
      if (action === 'delete') {
        vi.mocked(cardApi.deleteCard).mockResolvedValue(updated)
        await status.handleDeleteCard({ id: 1 })
      } else {
        vi.mocked(cardApi.toggleArchive).mockResolvedValue(updated)
        status.handleToggleArchive({ id: 1, archivedStatus: action === 'archive' })
        await flushPromises()
        expect(cardApi.toggleArchive).toHaveBeenCalledWith(1, { archivedStatus: action === 'archive' })
      }
      keys.forEach(key => expect(queryClient.getQueryState(key)?.isInvalidated, JSON.stringify(key)).toBe(true))
      expect(ElMessage.error).not.toHaveBeenCalled()
    } finally { wrapper.unmount(); queryClient.clear() }
  })
})

describe('useCardStatus delete cache', () => {
  beforeEach(() => vi.clearAllMocks())

  it.each([false, true])('各種列表格式共存時正常送出刪除，API 失敗=%s 時還原完整快取', async failure => {
    const { queryClient, status, wrapper } = setup()
    const original = card('1'), other = card('2')
    const page: PageResponse<CardDto> = { content: [original, other], page: 0, size: 20, totalElements: 2, totalPages: 1 }
    const board = boardCache([original, other])
    const picker: InfiniteData<PageResponse<CardDto>> = { pages: [page], pageParams: [0] }
    const legacy: InfiniteData<CardDto[]> = { pages: [[original, other]], pageParams: [1] }
    const batch: TodayBatchResponse = { cards: [original, other], batchOfferedAt: null }
    const entries: Array<[string[], unknown]> = [
      [['cards', 'all'], board],
      [['cards', 'shared-picker'], picker],
      [['cards', 'search'], page],
      [['cards', 'legacy'], legacy],
      [['cards', 'filtered'], [original, other]],
    ]
    entries.forEach(([key, value]) => queryClient.setQueryData(key, value))
    queryClient.setQueryData(['card', '1'], original)
    queryClient.setQueryData(todayBatchQueryKey, batch)
    vi.mocked(cardApi.deleteCard).mockImplementation(async () => {
      expect(queryClient.getQueryData(['cards', 'all'])).toEqual(boardCache([other]))
      expect(queryClient.getQueryData(['cards', 'shared-picker'])).toEqual({ ...picker, pages: [{ ...page, content: [other] }] })
      expect(queryClient.getQueryData(['cards', 'search'])).toEqual({ ...page, content: [other] })
      expect(queryClient.getQueryData(['cards', 'legacy'])).toEqual({ ...legacy, pages: [[other]] })
      expect(queryClient.getQueryData(['cards', 'filtered'])).toEqual([other])
      expect(queryClient.getQueryData(todayBatchQueryKey)).toEqual({ ...batch, cards: [other] })
      if (failure) throw new Error('刪除失敗')
      return original
    })
    try {
      if (failure) await expect(status.handleDeleteCard({ id: 1 })).rejects.toThrow('刪除失敗')
      else await status.handleDeleteCard({ id: 1 })
      expect(cardApi.deleteCard).toHaveBeenCalledWith(1)
      if (failure) {
        entries.forEach(([key, value]) => expect(queryClient.getQueryData(key)).toEqual(value))
        expect(queryClient.getQueryData(todayBatchQueryKey)).toEqual(batch)
        expect(queryClient.getQueryData(['card', '1'])).toEqual(original)
        expect(ElMessage.success).not.toHaveBeenCalled()
      } else {
        expect(queryClient.getQueryData(['card', '1'])).toBeUndefined()
        expect(queryClient.getQueryState(['cards', 'all'])?.isInvalidated).toBe(true)
        expect(ElMessage.success).toHaveBeenCalledWith('卡片刪除成功')
        expect(ElMessage.error).not.toHaveBeenCalled()
      }
    } finally {
      wrapper.unmount()
      queryClient.clear()
    }
  })
})

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
