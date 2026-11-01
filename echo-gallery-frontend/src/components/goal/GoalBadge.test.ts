import { mount, flushPromises } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import GoalBadge from './GoalBadge.vue'
import { goalApi } from '../../utils/api/goalApi'
vi.mock('../../utils/api/goalApi', () => ({ goalApi: { counts: vi.fn(), associations: vi.fn() } }))
describe('目標徽章', () => {
  beforeEach(() => { vi.resetAllMocks(); localStorage.setItem('id', 'badge-owner') })
  const client = () => new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const stubs = { 'el-popover': { template: '<div><slot name="reference" /><slot /></div>' }, 'el-icon': { template: '<span><slot /></span>' }, RouterLink: { template: '<a><slot /></a>' } }
  it('同次渲染批次查數量，點擊不觸發外層卡片導航', async () => {
    vi.mocked(goalApi.counts).mockResolvedValue({ 'CARD:1': 2, 'CARD:2': 1 })
    const parentClick = vi.fn()
    const wrapper = mount({ components: { GoalBadge }, template: '<div @click="clicked"><GoalBadge kind="CARD" :id="1" /><GoalBadge kind="CARD" :id="2" /></div>', methods: { clicked: parentClick } }, { global: { plugins: [[VueQueryPlugin, { queryClient: client() }]], stubs } })
    await flushPromises(); await flushPromises()
    expect(goalApi.counts).toHaveBeenCalledTimes(1)
    expect(goalApi.counts).toHaveBeenCalledWith([{ kind: 'CARD', id: 1, excluded: false }, { kind: 'CARD', id: 2, excluded: false }])
    await wrapper.get('button[aria-label="查看 2 個相關目標"]').trigger('click')
    expect(parentClick).not.toHaveBeenCalled(); wrapper.unmount()
  })
  it('零關聯不顯示旗幟數量', async () => {
    vi.mocked(goalApi.counts).mockResolvedValue({ 'CARD:1': 0 })
    const wrapper = mount(GoalBadge, { props: { kind: 'CARD', id: 1 }, global: { plugins: [[VueQueryPlugin, { queryClient: client() }]], stubs } })
    await flushPromises(); expect(wrapper.find('button').exists()).toBe(false); wrapper.unmount()
  })
})
