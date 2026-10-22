import { mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { afterEach, describe, expect, it, vi } from 'vitest'
import WorkUseFlow from './WorkUseFlow.vue'
import { workUseTarget } from '../../utils/workFlow'

const api = vi.hoisted(() => ({
  candidates: vi.fn(),
  experiments: vi.fn().mockResolvedValue([]),
  list: vi.fn().mockResolvedValue({ content: [] }),
}))

vi.mock('../../utils/api/workApi', () => ({ workApi: api }))
vi.mock('../../utils/api/cardApi', () => ({ cardApi: { getCard: vi.fn() } }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn(), beforeEach: () => vi.fn() }) }))

describe('從卡片用於作品', () => {
  const clients: QueryClient[] = []
  const wrappers: ReturnType<typeof mount>[] = []

  afterEach(() => {
    workUseTarget.value = undefined
    wrappers.splice(0).forEach(wrapper => wrapper.unmount())
    clients.splice(0).forEach(client => client.clear())
  })

  it('直接進入建立或加入作品，不開啟探索紀錄選取', () => {
    workUseTarget.value = { cardIds: [7], cardTitle: '場景靈感' }
    const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
    clients.push(client)
    const wrapper = mount(WorkUseFlow, {
      global: {
        plugins: [[VueQueryPlugin, { queryClient: client }]],
        stubs: {
          AppDialog: { props: ['modelValue'], template: '<section v-if="modelValue"><slot /><slot name="footer" :request-close="() => {}" /></section>' },
          SelectionSummary: true,
          WorkInfoFields: true,
          'el-input': true,
          'el-select': true,
          'el-option': true,
          'el-button': { template: '<button><slot /></button>' },
          'el-alert': true,
          'el-pagination': true,
          'el-radio-group': { template: '<div><slot /></div>' },
          'el-radio-button': { props: ['value'], template: '<button><slot /></button>' },
          'el-radio': true,
          'el-form': true,
        },
      },
    })
    wrappers.push(wrapper)

    expect(wrapper.text()).toContain('場景靈感')
    expect(wrapper.text()).toContain('建立新作品')
    expect(wrapper.text()).toContain('加入既有作品')
    expect(api.candidates).not.toHaveBeenCalled()
  })
})
