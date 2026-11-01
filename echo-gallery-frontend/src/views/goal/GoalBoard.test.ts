import { shallowMount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import GoalBoard from './GoalBoard.vue'
import { goalApi } from '../../utils/api/goalApi'
import type { GoalBoardPage, GoalPreference } from '../../types/goal'
vi.mock('vue-router', async importOriginal => ({ ...await importOriginal<typeof import('vue-router')>(), useRoute: () => ({ query: {} }), useRouter: () => ({ push: vi.fn() }) }))
vi.mock('../../components/CardItem.vue', () => ({ default: { template: '<div />' } }))
vi.mock('../../utils/api/goalApi', () => ({ goalApi: { preference: vi.fn(), savePreference: vi.fn(), board: vi.fn() } }))
const preference: GoalPreference = { allActive: true, goalIds: [], includePaused: true, includeArchived: false, version: 0 }
const page = (total: number, count: number): GoalBoardPage => ({ content: [], total, selectedGoalCount: count, hasMore: false, nextCursor: '', fingerprint: 'test' })
describe('目標看板套用範圍', () => {
  beforeEach(() => { vi.resetAllMocks(); sessionStorage.clear(); localStorage.setItem('id', 'board-test'); vi.mocked(goalApi.preference).mockResolvedValue(preference) })
  it('保存成功但讀取失敗時保留原結果，重試成功才改摘要', async () => {
    vi.mocked(goalApi.board).mockResolvedValueOnce(page(18, 2)).mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce(page(9, 1))
    vi.mocked(goalApi.savePreference).mockResolvedValue({ ...preference, allActive: false, goalIds: [7], version: 1 })
    const wrapper = shallowMount(GoalBoard, { global: { plugins: [ElementPlus], renderStubDefaultSlot: true, stubs: {
      MasonryWall: { template: '<div />' },
      ElAlert: { props: ['title'], template: '<div>{{ title }}<slot /></div>' },
      ElButton: { emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' },
    } } })
    await flushPromises()
    expect(wrapper.get('.goal-results').text()).toBe('已選 2 個目標 · 相關卡片 18 張')
    expect(wrapper.text()).not.toContain('本批')
    wrapper.findComponent({ name: 'GoalScopeDialog' }).vm.$emit('confirm', { ...preference, allActive: false, goalIds: [7] }); await flushPromises()
    expect(wrapper.text()).toContain('新設定已保存，但材料尚未載入')
    expect(wrapper.get('.goal-results').text()).toContain('18 張')
    await wrapper.findAll('button').find(button => button.text() === '重試')!.trigger('click'); await flushPromises()
    expect(wrapper.get('.goal-results').text()).toBe('已選 1 個目標 · 相關卡片 9 張')
    wrapper.unmount(); sessionStorage.clear(); localStorage.removeItem('id')
  })
})
