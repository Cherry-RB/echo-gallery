import { mount, flushPromises } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import ElementPlus from 'element-plus'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import GoalList from './GoalList.vue'
import { goalApi } from '../../utils/api/goalApi'
const { push } = vi.hoisted(() => ({ push: vi.fn() }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push }) }))
vi.mock('../../utils/api/goalApi', () => ({ goalApi: { list: vi.fn() } }))
let wrapper: ReturnType<typeof mount>, client: QueryClient
describe('管理目標手動查詢', () => {
  beforeEach(async () => {
    vi.clearAllMocks(); vi.mocked(goalApi.list).mockResolvedValue({ content: [], totalElements: 60, totalPages: 3 })
    client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
    wrapper = mount(GoalList, { global: { plugins: [ElementPlus, [VueQueryPlugin, { queryClient: client }]], stubs: {
      GoalEditor: true, GoalPinnedOrderDialog: true, AppEmptyState: true, ElPagination: { name: 'Pagination', emits: ['current-change'], template: '<div />' },
    } } }); await flushPromises()
  })
  afterEach(() => { wrapper.unmount(); client.clear() })
  it('整張目標卡片可用滑鼠與鍵盤開啟詳情，列內操作不導頁', async () => {
    vi.mocked(goalApi.list).mockResolvedValue({ content: [{
      id: 9, version: 1, effectiveStatus: 'ACTIVE', cardCount: 0, trackingStartedAt: '', closedAt: null,
      definition: { name: '閱讀計畫', startsOn: '2026-11-01', endsOn: '2026-11-08', expectation: '', motivation: '', imageUrl: '', status: 'ACTIVE', archived: false, pinned: false, pinOrder: 0, reviewDays: 7, remindersEnabled: false, allTags: false, sources: [], metrics: [] },
    }], totalElements: 1, totalPages: 1 })
    await client.invalidateQueries({ queryKey: ['goals'] }); await flushPromises()
    const card = wrapper.get('.goal-panel')
    await card.trigger('click')
    expect(push).toHaveBeenLastCalledWith('/goals/9')
    await card.trigger('keydown', { key: 'Enter' })
    await card.trigger('keydown', { key: ' ' })
    expect(push).toHaveBeenCalledTimes(3)
    push.mockClear()
    await wrapper.get('.goal-card-actions .el-button').trigger('click')
    expect(push).not.toHaveBeenCalled()
  })
  it('切換封存範圍立即查詢並回到首頁，只使用已提交的關鍵字與狀態', async () => {
    const statuses = wrapper.findComponent({ name: 'ElSelect' })
    await wrapper.get('input[aria-label="搜尋目標"]').setValue('寫作')
    statuses.vm.$emit('update:modelValue', ['ACTIVE']); await flushPromises()
    await wrapper.get('form[role="search"]').trigger('submit'); await flushPromises()
    wrapper.findComponent({ name: 'Pagination' }).vm.$emit('current-change', 2); await flushPromises()
    await wrapper.get('input[aria-label="搜尋目標"]').setValue('未提交')
    statuses.vm.$emit('update:modelValue', ['PAUSED']); await flushPromises()
    wrapper.findComponent({ name: 'ElRadioGroup' }).vm.$emit('update:modelValue', 'ALL'); await flushPromises()
    expect(goalApi.list).toHaveBeenLastCalledWith({ keyword: '寫作', statuses: ['ACTIVE'], archiveScope: 'ALL', page: 0 })
    expect(wrapper.get<HTMLInputElement>('input[aria-label="搜尋目標"]').element.value).toBe('未提交')
    expect(statuses.props('modelValue')).toEqual(['PAUSED'])
    await wrapper.get('form[role="search"]').trigger('submit'); await flushPromises()
    expect(goalApi.list).toHaveBeenLastCalledWith({ keyword: '未提交', statuses: ['PAUSED'], archiveScope: 'ALL', page: 0 })
    const count = vi.mocked(goalApi.list).mock.calls.length
    await wrapper.findAll('button').find(button => button.text() === '重設條件')!.trigger('click'); await flushPromises()
    expect(goalApi.list).toHaveBeenCalledTimes(count)
    expect(wrapper.findComponent({ name: 'ElRadioGroup' }).props('modelValue')).toBe('ALL')
  })
  it('修改與重設都不查詢，Enter 才提交；換頁不提交正在編輯的條件', async () => {
    expect(goalApi.list).toHaveBeenCalledTimes(1)
    await wrapper.get('input[aria-label="搜尋目標"]').setValue('寫作'); await flushPromises()
    expect(goalApi.list).toHaveBeenCalledTimes(1)
    await wrapper.get('form[role="search"]').trigger('submit'); await flushPromises()
    expect(goalApi.list).toHaveBeenLastCalledWith(expect.objectContaining({ keyword: '寫作', page: 0 }))
    await wrapper.get('input[aria-label="搜尋目標"]').setValue('尚未套用')
    wrapper.findComponent({ name: 'Pagination' }).vm.$emit('current-change', 2); await flushPromises()
    expect(goalApi.list).toHaveBeenLastCalledWith(expect.objectContaining({ keyword: '寫作', page: 1 }))
    const count = vi.mocked(goalApi.list).mock.calls.length
    await wrapper.findAll('button').find(button => button.text() === '重設條件')!.trigger('click'); await flushPromises()
    expect(goalApi.list).toHaveBeenCalledTimes(count)
  })
})
