import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useGoalRound } from './useGoalRound'
import { goalApi } from '../utils/api/goalApi'
import type { GoalBoardPage, GoalPreference } from '../types/goal'
vi.mock('../utils/api/goalApi', () => ({ goalApi: { board: vi.fn() } }))
const selection: GoalPreference = { allActive: true, goalIds: [], includePaused: true, includeArchived: false, version: 0 }
const page = (ids: string[], more: boolean, cursor: string) => ({ content: ids.map(id => ({ id })), hasMore: more, nextCursor: cursor, total: 7, fingerprint: 'test' }) as GoalBoardPage
describe('目標看板裝置內輪次', () => {
  beforeEach(() => { sessionStorage.clear(); vi.resetAllMocks() })
  it('換批去重，但上一批後再前進仍能回看原批次', async () => {
    vi.mocked(goalApi.board).mockResolvedValueOnce(page(['1', '2'], true, 'next')).mockResolvedValueOnce(page(['2', '3'], false, 'end')).mockResolvedValueOnce(page(['1', '2'], true, 'next')).mockResolvedValueOnce(page(['2', '3'], false, 'end'))
    const state = useGoalRound('one')
    await state.load(selection); await state.load(selection, 'next'); expect(state.current.value?.content.map(c => c.id)).toEqual(['3'])
    await state.load(selection, 'previous'); expect(state.current.value?.content.map(c => c.id)).toEqual(['1', '2'])
    await state.load(selection, 'next'); expect(state.current.value?.content.map(c => c.id)).toEqual(['3'])
  })
  it('刷新可接續，但另一個 owner 不取得原輪次', async () => {
    vi.mocked(goalApi.board).mockResolvedValue(page(['1'], false, 'end'))
    const state = useGoalRound('one'); await state.load(selection)
    const resumed = useGoalRound('one'); resumed.restore(selection); expect(resumed.round.value?.seed).toBe(state.round.value?.seed)
    const other = useGoalRound('two'); other.restore(selection); expect(other.round.value).toBeNull()
  })
  it('換範圍與過期不沿用舊輪次', async () => {
    vi.mocked(goalApi.board).mockResolvedValue(page([], false, 'end'))
    const state = useGoalRound('one'); await state.load(selection)
    const changed = useGoalRound('one'); changed.restore({ ...selection, includeArchived: true }); expect(changed.round.value).toBeNull()
    const data = JSON.parse(sessionStorage.getItem('goal-round:one')!); data.expires = Date.now() - 1; sessionStorage.setItem('goal-round:one', JSON.stringify(data))
    changed.restore(selection); expect(changed.round.value).toBeNull()
  })
  it('換批失敗保留原卡片與位置', async () => {
    vi.mocked(goalApi.board).mockResolvedValueOnce(page(['1'], true, 'next')).mockRejectedValueOnce(new Error('offline'))
    const state = useGoalRound('one'); await state.load(selection); await state.load(selection, 'next')
    expect(state.round.value?.index).toBe(0); expect(state.current.value?.content[0]?.id).toBe('1'); expect(state.error.value).not.toBe('')
  })
})
