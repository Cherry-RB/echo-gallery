import { effectScope } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { useReviewDraft } from './useReviewDraft'
import type { GoalReview } from '../types/goal'

const initial: GoalReview = { id: 1, startsOn: '2026-11-01', endsOn: '2026-11-02', status: 'DRAFT', content: { observation: '', decision: '', direction: 'ALIGNED', informationHelpfulness: null, actionEffectiveness: null }, version: 1, updatedAt: '', submittedAt: null, snapshot: null, goalVersion: 1 }
const scopes: ReturnType<typeof effectScope>[] = []
function setup(write: Parameters<typeof useReviewDraft>[0]) {
  const scope = effectScope(); scopes.push(scope)
  const draft = scope.run(() => useReviewDraft(write))!
  draft.load(initial); return draft
}
afterEach(() => { scopes.splice(0).forEach(scope => scope.stop()) })

describe('復盤草稿保存', () => {
  it('等待寫入中的請求，保留其間的新輸入並依序存妥後才完成 flush', async () => {
    let resolve!: (value: GoalReview) => void
    const write = vi.fn<Parameters<typeof useReviewDraft>[0]>()
      .mockImplementationOnce(() => new Promise(done => { resolve = done }))
      .mockImplementation(async (content, version) => ({ ...initial, content, version: version + 1 }))
    const draft = setup(write)
    draft.content.value.observation = '第一次'
    const first = draft.flush()
    draft.content.value.observation = '請求期間的最新輸入'
    const second = draft.flush()
    resolve({ ...initial, version: 2 })
    expect(await first).toBe(true); expect(await second).toBe(true)
    expect(write).toHaveBeenCalledTimes(2)
    expect(write.mock.calls[1]).toEqual([expect.objectContaining({ observation: '請求期間的最新輸入' }), 2])
    expect(draft.dirty.value).toBe(false)
    expect(draft.content.value.observation).toBe('請求期間的最新輸入')
  })
  it('儲存失敗回傳 false，內容與版本仍保留，重試後才清除未存狀態', async () => {
    const write = vi.fn<Parameters<typeof useReviewDraft>[0]>()
      .mockRejectedValueOnce({ response: { data: { message: '復盤已有更新，請重新讀取' } } })
      .mockImplementation(async (content, version) => ({ ...initial, content, version: version + 1 }))
    const draft = setup(write); draft.content.value.decision = '不要遺失'
    expect(await draft.flush()).toBe(false)
    expect(draft.content.value.decision).toBe('不要遺失')
    expect(draft.version.value).toBe(1); expect(draft.dirty.value).toBe(true)
    expect(draft.error.value).toContain('已有更新')
    expect(await draft.flush()).toBe(true)
    expect(draft.dirty.value).toBe(false)
  })
})
