import { describe, expect, it } from 'vitest'
import { getBoardCapabilities, shouldMarkReviewedOnOpenDetail } from './board'

describe('board 規則', () => {
  it('只有今日看板開啟詳情時會標記完成回顧', () => {
    expect(shouldMarkReviewedOnOpenDetail('today')).toBe(true)
    expect(shouldMarkReviewedOnOpenDetail('all')).toBe(false)
    expect(shouldMarkReviewedOnOpenDetail('tag')).toBe(false)
    expect(shouldMarkReviewedOnOpenDetail('search')).toBe(false)
  })

  it('封存看板只保留還原與清理操作', () => {
    expect(getBoardCapabilities('archived')).toEqual({
      canStar: false,
      canArchive: true,
      canSnooze: false,
    })
  })
})
