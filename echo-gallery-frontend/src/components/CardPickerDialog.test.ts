import { mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import CardPickerDialog from './CardPickerDialog.vue'
import { createMemoryHistory, createRouter } from 'vue-router'

const api = vi.hoisted(() => ({ searchCards: vi.fn(), getCard: vi.fn() }))
vi.mock('../utils/api/cardApi', () => ({ cardApi: api }))
const clients: QueryClient[] = []
const wrappers: ReturnType<typeof mount>[] = []
const original = { id: '90', title: '原本關聯的卡片', type: 'note', intervalDays: 7 }
const other = { id: '2', title: '另一張卡片', type: 'note', intervalDays: 7 }
async function render(multiple = false) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } }); clients.push(client)
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: { template: '<div />' } }] })
  await router.push('/')
  await router.isReady()
  const wrapper = mount(CardPickerDialog, { props: { modelValue: true, title: '關聯卡片', description: '選擇既有卡片', initialCardId: multiple ? undefined : '90', settingsStyle: 'plain', multiple }, global: {
    plugins: [router, [VueQueryPlugin, { queryClient: client }]],
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
    const wrapper = await render()
    expect(wrapper.findComponent({ name: 'PickerWorkspace' }).exists()).toBe(true)
    await vi.waitFor(() => expect(wrapper.get('.card-picker-selection-summary').text()).toContain('原本關聯的卡片'))
    expect(wrapper.get('.card-picker-selection-summary').text()).toContain('#90')
    expect(api.getCard).toHaveBeenCalledWith('90')
    await wrapper.findAll('button').find(b => b.text() === '取消')!.trigger('click')
    expect(wrapper.emitted('confirm')).toBeUndefined()
    await vi.waitFor(() => expect(wrapper.emitted('update:modelValue')).toEqual([[false]]))
  })

  it('原卡片較晚載入也不覆蓋使用者剛選的新卡片', async () => {
    let finish!: (value: typeof original) => void
    api.getCard.mockImplementation(() => new Promise(resolve => { finish = resolve }))
    const wrapper = await render()
    await vi.waitFor(() => expect(wrapper.find('.card-picker-item').exists()).toBe(true))
    await wrapper.get('.card-picker-item').trigger('click')
    finish(original)
    await vi.waitFor(() => expect(wrapper.get('.card-picker-selection-summary').text()).toContain('另一張卡片'))
    await wrapper.findAll('footer button')[1]!.trigger('click')
    expect(wrapper.emitted('confirm')).toEqual([[other]])
  })

  it('多選閱讀原文後保留選取，確認一次送出全部卡片', async () => {
    api.searchCards.mockResolvedValue({ content: [original, other], page: 0, totalPages: 1 })
    const wrapper = await render(true)
    await vi.waitFor(() => expect(wrapper.findAll('.card-picker-item')).toHaveLength(2))
    await wrapper.findAll('.card-picker-item')[0]!.trigger('click')
    await wrapper.findAll('.card-picker-item')[1]!.trigger('click')
    await wrapper.findAll('button').find(button => button.text() === '閱讀全文')!.trigger('click')
    await vi.waitFor(() => expect(wrapper.findComponent({ name: 'SourceReadingDialog' }).exists()).toBe(true))
    expect(wrapper.findAll('.card-picker-item')).toHaveLength(2)
    const originalList = wrapper.get('.card-picker-list').element
    await wrapper.findAll('button').find(button => button.text() === '返回選擇')!.trigger('click')
    await vi.waitFor(() => expect(wrapper.findComponent({ name: 'SourceReadingDialog' }).exists()).toBe(false))
    expect(wrapper.get('.card-picker-list').element).toBe(originalList)
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    const submit = wrapper.findAll('footer button').find(button => button.text().includes('（2）'))!
    await submit.trigger('click')
    expect(wrapper.emitted('confirm-many')).toEqual([[[original, other]]])
    expect(wrapper.emitted('confirm')).toBeUndefined()
  })
})
