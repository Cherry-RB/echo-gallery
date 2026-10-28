import { mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { afterEach, describe, expect, it, vi } from 'vitest'
import UseForWork from './UseForWork.vue'
import { workApi } from '../../utils/api/workApi'
import type { WorkUsage } from '../../types/work'

vi.mock('../../utils/api/workApi', () => ({ workApi: { usages: vi.fn() } }))
const wrappers: ReturnType<typeof mount>[] = [], clients: QueryClient[] = []
function render(usages?: WorkUsage[]) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: 3, retryDelay: 0, gcTime: 0 } } })
  clients.push(client)
  const wrapper = mount(UseForWork, { props: { cardId: 11, showAction: false, usages }, global: {
    plugins: [[VueQueryPlugin, { queryClient: client }]],
    stubs: {
      RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' },
      'el-button': { template: '<button><slot /></button>' },
    },
  } })
  wrappers.push(wrapper)
  return wrapper
}
afterEach(() => { wrappers.splice(0).forEach(w => w.unmount()); clients.splice(0).forEach(c => c.clear()); vi.resetAllMocks() })
describe('卡片作品引用回饋', () => {
  it('請求期間顯示載入，成功後才顯示沒有引用', async () => {
    let resolve!: (value: WorkUsage[]) => void
    vi.mocked(workApi.usages).mockImplementation(() => new Promise(done => { resolve = done }))
    const wrapper = render()
    expect(wrapper.get('[role="status"]').text()).toContain('正在載入作品引用')
    expect(wrapper.text()).not.toContain('尚未被作品引用')
    resolve([])
    await vi.waitFor(() => expect(wrapper.text()).toContain('尚未被作品引用'))
  })
  it('失敗不當成空集合或自動連續重試，手動重試成功後顯示引用', async () => {
    vi.mocked(workApi.usages).mockRejectedValueOnce(new Error('伺服器錯誤'))
    const wrapper = render()
    await vi.waitFor(() => expect(wrapper.get('[role="alert"]').text()).toContain('無法載入作品引用'))
    expect(wrapper.text()).not.toContain('尚未被作品引用')
    expect(workApi.usages).toHaveBeenCalledTimes(1)
    vi.mocked(workApi.usages).mockResolvedValueOnce([{ cardId: 11, recordId: null, workId: 4, title: '引用這張卡片的作品', archived: false }])
    await wrapper.get('button').trigger('click')
    await vi.waitFor(() => expect(wrapper.get('a').attributes('href')).toBe('/works/4'))
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(workApi.usages).toHaveBeenCalledTimes(2)
  })
  it('父層提供引用時沿用資料，不另外發送查詢', async () => {
    const wrapper = render([{ cardId: 11, recordId: null, workId: 5, title: '已封存作品', archived: true }])
    await wrapper.vm.$nextTick()
    expect(wrapper.get('a').text()).toBe('已封存作品（已封存）')
    expect(workApi.usages).not.toHaveBeenCalled()
    expect(wrapper.find('[role="status"]').exists()).toBe(false)
  })
})
