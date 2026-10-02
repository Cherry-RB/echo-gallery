import { describe, expect, it } from 'vitest'
import { issueStatusMeta, issueStatusOptions } from './issueStatus'

describe('issueStatus', () => {
  it('將既有 enum 顯示為當前模式而非線性進度', () => {
    expect(issueStatusMeta).toEqual({
      IDEA: { label: '探索', tone: 'exploring' },
      DRAFT: { label: '建模', tone: 'modeling' },
      ACTIVE: { label: '介入／觀察', tone: 'intervening' },
      DONE: { label: '收斂', tone: 'converged' },
      ARCHIVED: { label: '封存', tone: 'archived' },
    })
    expect(issueStatusOptions.map((option) => option.value)).toEqual([
      'IDEA', 'DRAFT', 'ACTIVE', 'DONE', 'ARCHIVED',
    ])
  })
})
