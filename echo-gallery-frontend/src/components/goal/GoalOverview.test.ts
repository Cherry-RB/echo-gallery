import { mount, flushPromises } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import ElementPlus from 'element-plus'
import { createMemoryHistory, createRouter } from 'vue-router'
import { describe, expect, it, vi } from 'vitest'
import GoalOverview from './GoalOverview.vue'
import { goalApi } from '../../utils/api/goalApi'
import type { GoalView } from '../../types/goal'
vi.mock('../../utils/api/goalApi', () => ({ goalApi: { overview: vi.fn() } }))
describe('固定目標摘要', () => {
  it('目標閱讀與翻閱卡片各自導向正確頁面，收合不導頁', async () => {
    localStorage.clear()
    const goal: GoalView = { id: 7, effectiveStatus: 'ACTIVE', cardCount: 3, version: 1, trackingStartedAt: '', closedAt: null, definition: { name: '寫作練習', startsOn: '2026-11-01', endsOn: '2026-11-08', expectation: '完成一篇短篇', motivation: '', imageUrl: '', status: 'ACTIVE', archived: false, pinned: true, pinOrder: 0, reviewDays: 7, remindersEnabled: false, allTags: false, sources: [{ kind: 'TAG', id: 1, excluded: false }], metrics: ['M01'] } }
    vi.mocked(goalApi.overview).mockResolvedValue([goal])
    const router = createRouter({ history: createMemoryHistory(), routes: [
      { path: '/overview', component: GoalOverview }, { path: '/goals/:id', component: { template: '<div />' } },
      { path: '/board/goals', component: { template: '<div />' } }, { path: '/goals', component: { template: '<div />' } },
    ] })
    await router.push('/overview'); await router.isReady()
    const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
    const wrapper = mount(GoalOverview, { global: { plugins: [ElementPlus, router, [VueQueryPlugin, { queryClient: client }]] } })
    await flushPromises()
    await wrapper.get('.goal-material-link').trigger('click'); await flushPromises()
    expect(router.currentRoute.value.fullPath).toBe('/board/goals?goal=7')
    await wrapper.get('.goal-overview-item').trigger('click'); await flushPromises()
    expect(router.currentRoute.value.path).toBe('/goals/7')
    await wrapper.findAll('button').find(button => button.text() === '收合')!.trigger('click')
    expect(router.currentRoute.value.path).toBe('/goals/7')
    expect(wrapper.get('#goal-overview-content').isVisible()).toBe(false)
    wrapper.unmount(); client.clear(); localStorage.clear()
  })
  it('站外目標不顯示假數字與閱讀入口，收合仍保留目標名稱', async () => {
    localStorage.clear()
    const goal: GoalView = { id: 1, effectiveStatus: 'ACTIVE', cardCount: 0, version: 1, trackingStartedAt: '', closedAt: null, definition: { name: '每週戶外活動', startsOn: '2026-11-01', endsOn: '2026-11-08', expectation: '', motivation: '', imageUrl: '', status: 'ACTIVE', archived: false, pinned: true, pinOrder: 0, reviewDays: 7, remindersEnabled: false, allTags: false, sources: [], metrics: [] } }
    vi.mocked(goalApi.overview).mockResolvedValue([goal])
    const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
    const wrapper = mount(GoalOverview, { global: { plugins: [ElementPlus, [VueQueryPlugin, { queryClient: client }]], stubs: { RouterLink: { props: ['to'], template: '<a><slot /></a>' } } } })
    await flushPromises()
    expect(wrapper.find('.goal-count').exists()).toBe(false); expect(wrapper.find('.goal-material-link').exists()).toBe(false)
    await wrapper.findAll('button').find(button => button.text() === '收合')!.trigger('click')
    expect(wrapper.get('.goal-collapsed-names').text()).toBe('每週戶外活動')
    expect(localStorage.getItem('goal-overview-expanded:')).toBe('false')
    wrapper.unmount(); client.clear(); localStorage.clear()
  })
})
