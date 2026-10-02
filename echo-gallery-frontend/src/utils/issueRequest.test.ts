import { describe, expect, it } from 'vitest'
import type { IssueDetail } from '../types/issue'
import { toIssueUpdateRequest } from './issueRequest'

const issue: IssueDetail = {
  id: 7,
  title: '轉職系統',
  objective: '取得市場回饋',
  description: '背景',
  currentAssessment: '既有補充研判',
  keyStates: '市場資訊增加',
  dominantLoops: '接觸 → 回饋 → 學習',
  primaryConstraint: '樣本不足',
  leveragePoint: '增加市場接觸',
  watchSignals: '回覆與面談',
  nonInterventionNote: '單次拒絕不改定位',
  outcomeCriteria: '樣本穩定後收斂',
  status: 'ACTIVE',
  externalUrl: 'https://example.com/execution',
  completedAt: null,
  createdAt: '2026-10-01T00:00:00Z',
  updatedAt: '2026-10-02T00:00:00Z',
}

describe('toIssueUpdateRequest', () => {
  it('只切換目前模式時仍帶回所有系統欄位與舊版補充研判', () => {
    expect(toIssueUpdateRequest(issue, 'DONE')).toEqual({
      title: '轉職系統',
      objective: '取得市場回饋',
      description: '背景',
      currentAssessment: '既有補充研判',
      keyStates: '市場資訊增加',
      dominantLoops: '接觸 → 回饋 → 學習',
      primaryConstraint: '樣本不足',
      leveragePoint: '增加市場接觸',
      watchSignals: '回覆與面談',
      nonInterventionNote: '單次拒絕不改定位',
      outcomeCriteria: '樣本穩定後收斂',
      externalUrl: 'https://example.com/execution',
      status: 'DONE',
    })
  })
})
