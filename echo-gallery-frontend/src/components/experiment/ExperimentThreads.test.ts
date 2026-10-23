import { mount, type VueWrapper } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ExperimentThreads from './ExperimentThreads.vue'

const api = vi.hoisted(() => ({ getThreads: vi.fn(), getThread: vi.fn(), getThreadSummaries: vi.fn(), getExploration: vi.fn(), importThreadRecords: vi.fn(), createThreadWithImports: vi.fn() }))
vi.mock('../../utils/api/experimentApi', () => ({ experimentApi: api }))
vi.mock('../../utils/api/workApi', () => ({ workApi: { list: vi.fn() } }))
const thread = { id: 3, name: '散步探索', status: 'IN_PROGRESS', experimentId: 1, lastActivityAt: '2026-10-23T06:00:00Z', conclusion: '', cards: [], works: [], records: [], methods: [{ id: 11, content: '每天散步', sourceRecordId: null }] }
const record = { id: 7, tryText: '每天散步', discovery: '比較容易開始工作', sourceThreadMethodId: 11, createdAt: '2026-10-23T06:00:00Z', exports: [] }
const clients: QueryClient[] = []
const wrappers: ReturnType<typeof mount>[] = []
async function render(query: string, dialogOnly = false) {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/experiments/:id', component: { template: '<div />' } }] })
  await router.push('/experiments/1?' + query)
  await router.isReady()
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
  clients.push(client)
  const wrapper = mount(ExperimentThreads, { props: { experimentId: 1, explorationRecords: [record], dialogOnly, initialThreadId: dialogOnly ? 3 : undefined }, global: {
    plugins: [router, [VueQueryPlugin, { queryClient: client }]],
    stubs: {
      AppDialog: { props: ['modelValue', 'title'], template: '<section v-if="modelValue" class="dialog"><h2>{{ title }}</h2><slot /><footer><slot name="footer" :request-close="() => $emit(\'update:modelValue\', false)" /></footer></section>' },
      ExperimentExplorationEditor: true, CardPickerDialog: true, SourceReadingDialog: true,
      'el-button': { props: ['disabled'], template: '<button :disabled="disabled"><slot /></button>' },
      'el-checkbox': { props: ['modelValue', 'disabled'], template: '<label><input type="checkbox" :checked="modelValue" :disabled="disabled" /><slot /></label>' },
      'el-dropdown': true, 'el-tag': true, 'el-icon': true, 'el-radio-group': true,
      'el-select': { props: ['modelValue'], template: '<select :value="modelValue"><slot /></select>' },
      'el-option': { props: ['value', 'label'], template: '<option :value="value">{{ label }}</option>' },
      'el-input': { props: ['modelValue'], template: '<textarea :value="modelValue" />' },
    },
  } })
  wrappers.push(wrapper)
  return wrapper
}
beforeEach(() => {
  vi.clearAllMocks()
  api.getThreads.mockResolvedValue([thread])
  api.getThread.mockResolvedValue(thread)
  api.getThreadSummaries.mockResolvedValue({ content: [thread], page: 0, size: 20, totalElements: 1, totalPages: 1 })
  api.getExploration.mockResolvedValue({ currentTry: '', favoriteTries: [], records: [record] })
  api.importThreadRecords.mockResolvedValue(thread)
  Element.prototype.scrollIntoView = vi.fn()
})
afterEach(() => { wrappers.splice(0).forEach(wrapper => wrapper.unmount()); clients.splice(0).forEach(client => client.clear()) })

describe('探索線的跨入口流程', () => {
  it('獨立彈窗模式不切換路由，也不顯示實驗頁的摘要區塊', async () => {
    const wrapper = await render('', true)
    await vi.waitFor(() => expect(wrapper.find('.thread-detail-grid').exists()).toBe(true))
    expect(wrapper.find('.thread-workspace').exists()).toBe(false)
    expect(wrapper.vm.$route.fullPath).toBe('/experiments/1?')
    expect(api.getThread).toHaveBeenCalledWith(1, 3)
    expect(api.getExploration).toHaveBeenCalledWith(1)
  })
  it('反向連結可直接開啟指定探索線', async () => {
    const wrapper = await render('thread=3')
    await vi.waitFor(() => expect(wrapper.find('.dialog').text()).toContain('散步探索'))
    expect(wrapper.find('.thread-detail-grid').exists()).toBe(true)
  })

  it.each(['新增試法', '加入紀錄'])('從詳情開啟「%s」再取消，仍保留同一個詳情內容', async action => {
    const wrapper = await render('thread=3')
    await vi.waitFor(() => expect(wrapper.find('.thread-detail-grid').exists()).toBe(true))
    const detail = wrapper.get('.thread-detail-grid').element
    await wrapper.findAll('.dialog button').find(button => button.text() === action)!.trigger('click')
    expect(wrapper.findAll('.dialog')).toHaveLength(2)
    expect(wrapper.get('.thread-detail-grid').element).toBe(detail)
    expect(wrapper.find('.dialog').attributes('close-on-press-escape')).toBe('false')
    await wrapper.findAll('.dialog').at(-1)!.findAll('footer button').find(button => button.text() === '取消')!.trigger('click')
    expect(wrapper.findAll('.dialog')).toHaveLength(1)
    expect(wrapper.get('.thread-detail-grid').element).toBe(detail)
  })

  it('從歷程匯入時保留原試法歸屬，確認不重建相同試法', async () => {
    const wrapper = await render('importRecord=7')
    await vi.waitFor(() => expect(wrapper.find('.dialog').text()).toContain('已選 1／100 筆'))
    await wrapper.findAll('footer button').find(button => button.text() === '下一步')!.trigger('click')
    const methodCheckbox = wrapper.findAll('label').find(label => label.text() === '建立新試法')!
    expect((methodCheckbox.get('input').element as HTMLInputElement).checked).toBe(false)
    expect(wrapper.find('.source-editor select').element).toHaveProperty('value', '11')
    await wrapper.findAll('footer button').find(button => button.text() === '加入探索線')!.trigger('click')
    await vi.waitFor(() => expect(api.importThreadRecords).toHaveBeenCalledWith(1, 3, [expect.objectContaining({ recordId: 7, includeMethod: false, includeDiscovery: true, targetMethodId: 11 })]))
    expect(api.createThreadWithImports).not.toHaveBeenCalled()
  })

  it('切換匯入目標仍保留已整理的文字', async () => {
    const target = { ...thread, id: 4, name: '另一條探索線', methods: [] }
    api.getThreads.mockResolvedValue([thread, target])
    const wrapper = await render('importRecord=7')
    await vi.waitFor(() => expect(wrapper.find('.dialog').text()).toContain('已選 1／100 筆'))
    await wrapper.findAll('footer button').find(button => button.text() === '下一步')!.trigger('click')
    ;(wrapper.findComponent('.source-editor textarea') as VueWrapper).vm.$emit('update:modelValue', '重新整理後的發現')
    await wrapper.vm.$nextTick()
    await wrapper.findAll('footer button').find(button => button.text() === '上一步')!.trigger('click')
    ;(wrapper.findComponent('.source-target select') as VueWrapper).vm.$emit('update:modelValue', 4)
    await wrapper.vm.$nextTick()
    await wrapper.findAll('footer button').find(button => button.text() === '下一步')!.trigger('click')
    await wrapper.findAll('footer button').find(button => button.text() === '加入探索線')!.trigger('click')
    await vi.waitFor(() => expect(api.importThreadRecords).toHaveBeenCalledWith(1, 4, [expect.objectContaining({ discovery: '重新整理後的發現' })]))
  })
})
