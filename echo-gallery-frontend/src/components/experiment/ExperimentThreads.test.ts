import { mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ExperimentThreads from './ExperimentThreads.vue'
const api = vi.hoisted(() => ({ getThread: vi.fn(), getThreadSummaries: vi.fn(), getExploration: vi.fn(), moveThread: vi.fn() }))
vi.mock('../../utils/api/experimentApi', () => ({ experimentApi: api }))
vi.mock('../../utils/api/workApi', () => ({ workApi: { list: vi.fn() } }))
const thread = { id: 3, name: '散步探索', status: 'IN_PROGRESS', experimentId: 1, lastActivityAt: '2026-10-23T06:00:00Z', conclusion: '', cards: [], works: [], records: [], methods: [], version: 1, orderVersion: 8 }
const record = { id: 7, threadId: 3, methodId: null, tryText: null, discovery: '比較容易開始工作', createdAt: '2026-10-23T06:00:00Z', exports: [], version: 0 }
const clients: QueryClient[] = [], wrappers: ReturnType<typeof mount>[] = []
async function render(query: string, dialogOnly = false) {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/experiments/:id', component: { template: '<div />' } }] })
  await router.push('/experiments/1?' + query)
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } }); clients.push(client)
  const wrapper = mount(ExperimentThreads, { props: { experimentId: 1, explorationRecords: [record], dialogOnly, initialThreadId: dialogOnly ? 3 : undefined }, global: {
    plugins: [router, [VueQueryPlugin, { queryClient: client }]], stubs: {
      AppDialog: { props: ['modelValue', 'title'], template: '<section v-if="modelValue" class="dialog"><h2>{{ title }}</h2><slot /><footer><slot name="footer" :request-close="() => $emit(\'update:modelValue\', false)" /></footer></section>' },
      ExperimentExplorationEditor: { name: 'ExperimentExplorationEditor', props: ['modelValue', 'threadId', 'mode'], emits: ['update:modelValue'], template: '<section v-if="modelValue" class="editor"><button @click="$emit(\'update:modelValue\', false)">取消</button></section>' },
      ExplorationOrganizationDialog: { name: 'ExplorationOrganizationDialog', props: ['modelValue', 'initialRecords'], template: '<section v-if="modelValue" class="organization" />' },
      ExplorationThreadActivity: true, CardPickerDialog: true, SourceReadingDialog: true,
      'el-button': { props: ['disabled'], template: '<button :disabled="disabled"><slot /></button>' },
      'el-dropdown': { template: '<div><slot /><slot name="dropdown" /></div>' }, 'el-dropdown-menu': { template: '<div><slot /></div>' },
      'el-dropdown-item': { props: ['disabled'], template: '<button :disabled="disabled"><slot /></button>' },
      'el-tag': true, 'el-icon': true, 'el-radio-group': true, 'el-skeleton': true, 'el-result': true, 'el-pagination': true,
    },
  } }); wrappers.push(wrapper); return wrapper
}
beforeEach(() => {
  vi.clearAllMocks()
  api.getThread.mockResolvedValue(thread)
  api.getThreadSummaries.mockResolvedValue({ content: [thread], page: 0, size: 20, totalElements: 1, totalPages: 1 })
  api.getExploration.mockResolvedValue({ currentTry: '', favoriteTries: [], records: [record] })
  api.moveThread.mockResolvedValue(undefined)
  Element.prototype.scrollIntoView = vi.fn()
})
afterEach(() => { wrappers.splice(0).forEach(w => w.unmount()); clients.splice(0).forEach(c => c.clear()) })
describe('探索線的共用入口', () => {
  it('標題計數使用所有探索線總數，不使用目前頁面的預覽筆數', async () => {
    api.getThreadSummaries.mockResolvedValue({ content: [thread], page: 0, size: 20, totalElements: 23, totalPages: 2 })
    const w = await render('')
    await vi.waitFor(() => expect(w.find('[aria-label="探索線數量：23"]').exists()).toBe(true))
    expect(w.get('.app-section-title').text()).toBe('探索線')
    expect(w.get('[aria-label="探索線數量：23"]').text()).toBe('23')
  })

  it('獨立詳情彈窗保持原路由及三欄布局', async () => {
    const w = await render('', true)
    await vi.waitFor(() => expect(w.find('.thread-detail-grid').exists()).toBe(true))
    expect(w.find('.thread-workspace').exists()).toBe(false)
    expect(w.vm.$route.fullPath).toBe('/experiments/1?')
    expect(api.getThread).toHaveBeenCalledWith(1, 3)
  })
  it.each(['新增行動備選', '記下發現'])('詳情「%s」採共用編輯器，取消仍保留同一詳情', async action => {
    const w = await render('thread=3')
    await vi.waitFor(() => expect(w.find('.thread-detail-grid').exists()).toBe(true))
    const detail = w.get('.thread-detail-grid').element
    await w.findAll('button').find(b => b.text() === action)!.trigger('click')
    expect(w.find('.editor').exists()).toBe(true)
    expect(w.findComponent({ name: 'ExperimentExplorationEditor' }).props('threadId')).toBe(3)
    expect(w.get('.thread-detail-grid').element).toBe(detail)
    await w.get('.editor button').trigger('click')
    expect(w.find('.editor').exists()).toBe(false)
    expect(w.get('.thread-detail-grid').element).toBe(detail)
  })
  it('舊歷程入口改開共用整理，不重建來源副本', async () => {
    const w = await render('importRecord=7')
    await vi.waitFor(() => expect(w.find('.organization').exists()).toBe(true))
    expect(w.findComponent({ name: 'ExplorationOrganizationDialog' }).props('initialRecords')).toEqual([record])
  })
  it('排序傳入目前頁面的全域排序版本', async () => {
    api.getThreadSummaries.mockResolvedValue({ content: [thread, { ...thread, id: 4 }], page: 0, size: 20, totalElements: 2, totalPages: 1 })
    const w = await render('')
    await vi.waitFor(() => expect(w.findAll('button').some(b => b.text() === '往下移動')).toBe(true))
    await w.findAll('button').find(b => b.text() === '往下移動')!.trigger('click')
    await vi.waitFor(() => expect(api.moveThread).toHaveBeenCalledWith(1, 3, 4, true, 8))
  })
})
