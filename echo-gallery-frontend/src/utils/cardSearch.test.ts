import { describe, expect, it } from 'vitest'
import { cardSearchQueryKey, normalizeIssueCardSearch } from './cardSearch'

describe('卡片搜尋工具', () => {
  it('將議題素材的純數字與井字號輸入轉成 Card ID', () => {
    expect(normalizeIssueCardSearch(' 7 ')).toEqual({ id: 7 })
    expect(normalizeIssueCardSearch('#42')).toEqual({ id: 42 })
  })

  it('將其他輸入轉成標題並允許空條件', () => {
    expect(normalizeIssueCardSearch(' 回音 ')).toEqual({ title: '回音' })
    expect(normalizeIssueCardSearch('')).toEqual({})
  })

  it('query key 包含所有條件且正規化多選順序', () => {
    expect(cardSearchQueryKey({
      tagIds: [3, 1],
      archiveStatus: 'ACTIVE',
      recurrenceStatus: 'ACTIVE',
      minIntervalDays: 15,
      maxIntervalDays: 60,
      page: 2,
    })).toEqual([
      'cards',
      'search',
      {
        tagIds: [1, 3],
        archiveStatus: 'ACTIVE',
        recurrenceStatus: 'ACTIVE',
        minIntervalDays: 15,
        maxIntervalDays: 60,
        page: 2,
      },
    ])
  })
})
