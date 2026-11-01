import { describe, expect, it } from 'vitest'
import { getBoardCapabilities, shouldMarkReviewedOnOpenDetail } from './board'
describe('目標閱讀與今日回流', () => {
  it('目標詳情開啟不完成今日回顧，但保留明確管理操作', () => {
    expect(shouldMarkReviewedOnOpenDetail('goal')).toBe(false)
    expect(shouldMarkReviewedOnOpenDetail('today')).toBe(true)
    expect(getBoardCapabilities('goal')).toEqual({ canStar: true, canArchive: true, canSnooze: true })
  })
})
