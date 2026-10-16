import { mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import CardPickerDialog from './CardPickerDialog.vue'

const api = vi.hoisted(() => ({ searchCards: vi.fn(), getCard: vi.fn() }))
vi.mock('../utils/api/cardApi', () => ({ cardApi: api }))
const clients: QueryClient[] = []
const wrappers: ReturnType<typeof mount>[] = []
const original = { id: '90', title: '原本關聯的卡片', type: 'note', intervalDays: 7 }
const other = { id: '2', title: '另一張卡片', type: 'note', intervalDays: 7 }
function render() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } }); clients.push(client)
  const wrapper = mount(CardPickerDialog, { props: { modelValue: true, title: '關聯卡片', description: '選擇既有卡片', initialCardId: '90', settingsStyle: 'plain' }, global: {
    plugins: [[VueQueryPlugin, { queryClient: client }]],
    stubs: {
      'el-dialog': { props: ['modelValue'], template: '<section v-if="modelValue"><slot /><footer><slot name="footer" /></footer></section>' },
      'el-input': true, 'el-result': true, 'el-skeleton': true, 'el-empty': true,
      'el-button': { props: ['disabled'], template: '<button :disabled="disabled"><slot /></button>' },
    },
  } }); wrappers.push(wrapper); return wrapper
}
describe('共用選卡的既有關聯', () => {
  beforeEach(() => { vi.clearAllMocks(); api.searchCards.mockResolvedValue({ content: [other], page: 0, totalPages: 1 }); api.getCard.mockResolvedValue(original) })
  afterEach(() => { wrappers.splice(0).forEach(w => w.unmount()); clients.splice(0).forEach(c => c.clear()) })

  it('原卡片不在目前搜尋頁時仍顯示完整共用摘要，取消不確認關聯', async () => {
    const wrapper = render()
    expect(wrapper.findComponent({ name: 'PickerWorkspace' }).exists()).toBe(true)
    await vi.waitFor(() => expect(wrapper.get('.card-picker-selection-summary').text()).toContain('原本關聯的卡片'))
    expect(wrapper.get('.card-picker-selection-summary').text()).toContain('#90')
    expect(api.getCard).toHaveBeenCalledWith('90')
    await wrapper.findAll('button').find(b => b.text() === '取消')!.trigger('click')
    expect(wrapper.emitted('confirm')).toBeUndefined()
    expect(wrapper.emitted('update:modelValue')).toEqual([[false]])
  })

  it('原卡片較晚載入也不覆蓋使用者剛選的新卡片', async () => {
    let finish!: (value: typeof original) => void
    api.getCard.mockImplementation(() => new Promise(resolve => { finish = resolve }))
    const wrapper = render()
    await vi.waitFor(() => expect(wrapper.find('.card-picker-item').exists()).toBe(true))
    await wrapper.get('.card-picker-item').trigger('click')
    finish(original)
    await vi.waitFor(() => expect(wrapper.get('.card-picker-selection-summary').text()).toContain('另一張卡片'))
    await wrapper.findAll('footer button')[1]!.trigger('click')
    expect(wrapper.emitted('confirm')).toEqual([[other]])
  })
})
