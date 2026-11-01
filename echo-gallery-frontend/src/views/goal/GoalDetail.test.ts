import { defineComponent } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import ElementPlus, { ElMessageBox } from 'element-plus'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import GoalDetail from './GoalDetail.vue'
import { goalApi } from '../../utils/api/goalApi'
import type { GoalView } from '../../types/goal'

vi.mock('../../utils/api/goalApi', () => ({ goalApi: { detail: vi.fn(), sources: vi.fn(), revisions: vi.fn(), metrics: vi.fn(), delete: vi.fn() } }))
const goal: GoalView = { id: 1, version: 3, effectiveStatus: 'ACTIVE', cardCount: 0, trackingStartedAt: '', closedAt: null, definition: {
  name: '寫作練習', startsOn: '2026-11-01', endsOn: '2026-11-08', expectation: '', motivation: '', imageUrl: '', status: 'ACTIVE', archived: false, pinned: true, pinOrder: 0, reviewDays: 7, remindersEnabled: true, allTags: false, sources: [], metrics: [],
} }
let wrapper: ReturnType<typeof mount>, client: QueryClient
async function render() {
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/goals/:id', component: GoalDetail }, { path: '/goals', component: { template: '<p>管理目標列表</p>' } },
  ] })
  await router.push('/goals/1'); await router.isReady()
  client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
  wrapper = mount(defineComponent({ template: '<RouterView />' }), { global: {
    plugins: [router, ElementPlus, [VueQueryPlugin, { queryClient: client }]],
    stubs: { GoalEditor: true, ElDropdown: { name: 'ElDropdown', emits: ['command'], template: '<div><slot /></div>' } },
  } })
  await flushPromises()
  return router
}
describe('目標刪除後的導航與查詢', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(goalApi.detail).mockResolvedValue(goal)
    vi.mocked(goalApi.sources).mockResolvedValue([])
    vi.mocked(goalApi.revisions).mockResolvedValue([])
    vi.mocked(goalApi.metrics).mockResolvedValue([])
    vi.mocked(goalApi.delete).mockResolvedValue(undefined)
    vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue(undefined as never)
  })
  afterEach(() => { wrapper?.unmount(); client?.clear(); vi.restoreAllMocks() })
  it('調整紀錄直接顯示事件，每筆以單一控制展開當時設定', async () => {
    vi.mocked(goalApi.revisions).mockResolvedValue([
      { revision: 3, effectiveAt: '2026-11-01T11:00:00', reason: '', definition: { ...goal.definition, name: '目前設定' } },
      { revision: 2, effectiveAt: '2026-11-01T10:00:00', reason: '縮小練習範圍', definition: { ...goal.definition, name: '歷史設定' } },
      { revision: 1, effectiveAt: '2026-11-01T09:00:00', reason: '', definition: goal.definition },
    ])
    const router = await render()
    const history = wrapper.get('.goal-history')
    expect(history.element.tagName).toBe('SECTION')
    expect(history.get('.record-count').text()).toBe('3')
    expect(history.text()).toContain('更新目標：修改名稱')
    expect(history.text()).toContain('縮小練習範圍')
    expect(history.text()).toContain('建立目標')
    expect(history.text()).not.toContain('儲存目標設定')
    const settings = wrapper.findAll('.revision-settings')
    await settings[0]!.get('summary').trigger('click')
    expect((settings[0]!.element as HTMLDetailsElement).open).toBe(true)
    expect((settings[1]!.element as HTMLDetailsElement).open).toBe(false)
    expect(settings[0]!.text()).toContain('收起設定')
    expect(settings[0]!.get('dl').text()).not.toContain('納入追蹤')
    expect(settings[0]!.get('dl').text()).not.toContain('排除設定')
    expect(settings[0]!.get('dl').text()).not.toContain('顯示數據')
    await settings[1]!.get('summary').trigger('click'); await flushPromises()
    expect((settings[0]!.element as HTMLDetailsElement).open).toBe(true)
    expect((settings[1]!.element as HTMLDetailsElement).open).toBe(true)
    expect(router.currentRoute.value.path).toBe('/goals/1')
    expect(goalApi.delete).not.toHaveBeenCalled()
  })
  it('刪除成功返回列表，不重新讀取已刪除的詳情、來源與歷史', async () => {
    const router = await render()
    wrapper.findComponent({ name: 'ElDropdown' }).vm.$emit('command', 'delete')
    await vi.waitFor(() => expect(router.currentRoute.value.path).toBe('/goals'))
    expect(goalApi.delete).toHaveBeenCalledWith(1, 3)
    expect(goalApi.detail).toHaveBeenCalledTimes(1)
    expect(goalApi.sources).toHaveBeenCalledTimes(1)
    expect(goalApi.revisions).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('管理目標列表')
  })
  it('取消刪除不寫入，請求失敗則留在詳情並保留內容', async () => {
    vi.mocked(ElMessageBox.confirm).mockRejectedValueOnce('cancel')
    const router = await render()
    wrapper.findComponent({ name: 'ElDropdown' }).vm.$emit('command', 'delete'); await flushPromises()
    expect(goalApi.delete).not.toHaveBeenCalled()
    vi.mocked(goalApi.delete).mockRejectedValueOnce({ response: { status: 500 } })
    wrapper.findComponent({ name: 'ElDropdown' }).vm.$emit('command', 'delete'); await flushPromises()
    expect(router.currentRoute.value.path).toBe('/goals/1')
    expect(wrapper.text()).toContain('寫作練習')
    expect(wrapper.text()).toContain('操作失敗')
  })
  it('刪除時發現已被其他裝置刪除，也返回列表', async () => {
    vi.mocked(goalApi.delete).mockRejectedValueOnce({ response: { status: 404 } })
    const router = await render()
    wrapper.findComponent({ name: 'ElDropdown' }).vm.$emit('command', 'delete')
    await vi.waitFor(() => expect(router.currentRoute.value.path).toBe('/goals'))
    expect(goalApi.detail).toHaveBeenCalledTimes(1)
  })
  it('初次讀取 404 顯示不存在與返回入口，不循環重試', async () => {
    vi.mocked(goalApi.detail).mockRejectedValue({ response: { status: 404 } })
    await render()
    expect(wrapper.text()).toContain('這個目標已不存在')
    expect(wrapper.text()).not.toContain('重新載入')
    expect(goalApi.detail).toHaveBeenCalledTimes(1)
    expect(goalApi.sources).not.toHaveBeenCalled()
    expect(goalApi.revisions).not.toHaveBeenCalled()
  })
})
