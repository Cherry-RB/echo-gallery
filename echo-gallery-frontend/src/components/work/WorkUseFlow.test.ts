import { mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { afterEach, describe, expect, it, vi } from 'vitest'
import WorkUseFlow from './WorkUseFlow.vue'
import { workUseTarget } from '../../utils/workFlow'
import AppDialog from '../AppDialog.vue'

const api = vi.hoisted(() => ({
  candidates: vi.fn(),
  experiments: vi.fn().mockResolvedValue([]),
  list: vi.fn().mockResolvedValue({ content: [] }),
  addSources: vi.fn().mockResolvedValue([]),
}))
const navigation = vi.hoisted(() => ({ push: vi.fn() }))

vi.mock('../../utils/api/workApi', () => ({ workApi: api }))
vi.mock('../../utils/api/cardApi', () => ({ cardApi: { getCard: vi.fn() } }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: navigation.push, beforeEach: () => vi.fn() }) }))

describe('從卡片用於作品', () => {
  const clients: QueryClient[] = []
  const wrappers: ReturnType<typeof mount>[] = []

  afterEach(() => {
    workUseTarget.value = undefined
    wrappers.splice(0).forEach(wrapper => wrapper.unmount())
    clients.splice(0).forEach(client => client.clear())
  })

  it('卡片直接選擇作品，暫選不提交，確認後維持原有作品導向', async () => {
    api.list.mockResolvedValue({ content: [{ id: 22, title: '已有作品', stage: 'WORKING', experimentTitle: '實驗' }], totalElements: 1 })
    workUseTarget.value = { cardIds: [7], cardTitle: '場景靈感' }
    const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
    clients.push(client)
    const wrapper = mount(WorkUseFlow, {
      global: {
        plugins: [[VueQueryPlugin, { queryClient: client }]],
        stubs: {
          AppDialog: { props: ['modelValue', 'width'], template: '<section v-if="modelValue"><slot /><slot name="footer" :request-close="() => {}" /></section>' },
          SelectionSummary: true,
          WorkInfoFields: true,
          'el-input': true,
          'el-select': true,
          'el-option': true,
          'el-button': { template: '<button><slot /></button>' },
          'el-alert': true,
          'el-pagination': true,
          'el-radio-group': { name: 'RadioGroup', props: ['modelValue'], emits: ['update:modelValue'], template: '<div><slot /></div>' },
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
    expect(wrapper.findComponent(AppDialog).props('width')).toContain('620px')
    wrapper.findComponent({ name: 'RadioGroup' }).vm.$emit('update:modelValue', 'existing')
    await vi.waitFor(() => expect(wrapper.find('.work-options .picker-option').exists()).toBe(true))
    expect(wrapper.findComponent(AppDialog).props('width')).toContain('560px')
    await wrapper.get('.work-options .picker-option').trigger('click')
    expect(wrapper.get('.work-options .picker-option').attributes('aria-pressed')).toBe('true')
    expect(api.addSources).not.toHaveBeenCalled()
    await wrapper.findAll('button').find(button => button.text() === '加入作品')!.trigger('click')
    await vi.waitFor(() => expect(api.addSources).toHaveBeenCalledWith(22, [{ cardId: 7 }]))
    await vi.waitFor(() => expect(navigation.push).toHaveBeenCalledWith('/works/22'))
  })
})
