import { describe, expect, it } from 'vitest'
import {
  getIssueUpdateLead,
  hasIssueUpdateContent,
  normalizeIssueUpdate,
} from './issueUpdate'

describe('issueUpdate', () => {
  it('三個欄位全部空白時不視為有效更新', () => {
    expect(hasIssueUpdateContent({
      changeSummary: '  ',
      assessment: null,
      nextStep: '',
    })).toBe(false)
  })

  it('任一欄位有內容即可成立，並在送出前清理空白', () => {
    const normalized = normalizeIssueUpdate({
      changeSummary: ' 取得第一輪回饋 ',
      assessment: ' ',
      nextStep: undefined,
    })

    expect(hasIssueUpdateContent(normalized)).toBe(true)
    expect(normalized).toEqual({
      changeSummary: '取得第一輪回饋',
      assessment: null,
      nextStep: null,
    })
  })

  it('摘要缺少時依序以研判或下一步作為預覽', () => {
    expect(getIssueUpdateLead({ assessment: '目前判斷' })).toBe('目前判斷')
    expect(getIssueUpdateLead({ nextStep: '下一步' })).toBe('下一步')
  })
})
