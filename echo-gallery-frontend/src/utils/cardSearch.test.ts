import { describe, expect, it } from 'vitest'
import { cardSearchQueryKey, normalizeWorkCardSearch } from './cardSearch'

describe('卡片搜尋工具', () => {
  it('將 Work 素材的純數字與井字號輸入轉成 Card ID', () => {
    expect(normalizeWorkCardSearch(' 7 ')).toEqual({ id: 7 })
    expect(normalizeWorkCardSearch('#42')).toEqual({ id: 42 })
  })

  it('將其他輸入轉成標題並允許空條件', () => {
    expect(normalizeWorkCardSearch(' 回音 ')).toEqual({ title: '回音' })
    expect(normalizeWorkCardSearch('')).toEqual({})
  })

  it('query key 包含所有條件且正規化多選順序', () => {
    expect(cardSearchQueryKey({
      tagIds: [3, 1],
      growthStatuses: ['SEED', 'UNMARKED'],
      archiveStatus: 'ACTIVE',
      page: 2,
    })).toEqual([
      'cards',
      'search',
      {
        tagIds: [1, 3],
        growthStatuses: ['SEED', 'UNMARKED'],
        archiveStatus: 'ACTIVE',
        page: 2,
      },
    ])
  })
})
