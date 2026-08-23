import { describe, expect, it } from 'vitest'
import type { CardDto, TodayBatchResponse } from '../types/card'
import { removeTodayCard, resolveNextBatch, updateTodayCard } from './todayBatchCache'

const card = (id: string): CardDto => ({
  id, type: 'note', title: `card-${id}`, tags: [], showContentPreview: false,
  intervalDays: 10, nextShowAt: null, openCount: 0, likeCount: 0,
  isArchived: false, growthStatus: 'UNMARKED', createdAt: '', updatedAt: '',
})
const batch = (): TodayBatchResponse => ({
  cards: [card('1'), card('2')], batchOfferedAt: '2026-08-24T12:00:00+08:00',
})

describe('Today batch cache', () => {
  it('只移除指定卡片並保留批次時間', () => {
    expect(removeTodayCard(batch(), 1)).toEqual({
      cards: [card('2')], batchOfferedAt: '2026-08-24T12:00:00+08:00',
    })
  })

  it('更新內容時保留卡片', () => {
    const result = updateTodayCard(batch(), '1', { growthStatus: 'SEED' })
    expect(result?.cards).toHaveLength(2)
    expect(result?.cards[0].growthStatus).toBe('SEED')
  })

  it('下一批有內容時取代目前批次', () => {
    const next = { cards: [card('3')], batchOfferedAt: '2026-08-24T13:00:00+08:00' }
    expect(resolveNextBatch(batch(), next)).toEqual({ batch: next, noMoreCards: false })
  })

  it('下一批為空時保留目前批次', () => {
    const current = batch()
    const empty = { cards: [], batchOfferedAt: current.batchOfferedAt }
    expect(resolveNextBatch(current, empty)).toEqual({ batch: current, noMoreCards: true })
  })
})
