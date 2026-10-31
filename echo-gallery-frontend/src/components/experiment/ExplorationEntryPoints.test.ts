import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ExplorationMethodGroup from './ExplorationMethodGroup.vue'
import ExplorationThreadActivity from './ExplorationThreadActivity.vue'

vi.mock('../../utils/api/explorationActivityApi', () => ({ explorationActivityApi: { history: vi.fn() } }))
const wrappers: VueWrapper[] = [], clients: QueryClient[] = []
const method = { id: 8, threadId: 3, threadName: 'A', content: '行動文字', version: 2, discoveryCount: 0, hasHistory: false, lastOrganizedAt: null }
function options() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } }); clients.push(client)
  return { plugins: [[VueQueryPlugin, { queryClient: client }] as [typeof VueQueryPlugin, { queryClient: QueryClient }]], stubs: {
    ExplorationHistoryEditor: { name: 'HistoryEditor', props: ['modelValue', 'threadId', 'methodId', 'label'], template: '<section />' },
    ExplorationMethodAssignmentDialog: { name: 'AssignmentDialog', props: ['modelValue', 'threadId', 'methodId'], template: '<section />' },
    'el-button': { props: ['disabled'], template: '<button :disabled="disabled"><slot /></button>' },
    'el-dropdown': { template: '<div><slot /><slot name="dropdown" /></div>' },
    'el-dropdown-menu': { template: '<div><slot /></div>' },
    'el-dropdown-item': { template: '<button><slot /></button>' }, 'el-pagination': true,
  } }
}
afterEach(() => { wrappers.splice(0).forEach(w => w.unmount()); clients.splice(0).forEach(c => c.clear()) })
describe('歷史與搬移入口', () => {
  it('空探索線仍可新增未指定行動的歷史', async () => {
    const wrapper = mount(ExplorationThreadActivity, { props: { experimentId: 1, thread: {
      id: 3, experimentId: 1, name: '空探索線', status: 'NOT_STARTED', conclusion: null, sortOrder: 0,
      lastActivityAt: '', createdAt: '', updatedAt: '', methods: [], records: [], cards: [], works: [],
      methodCount: 0, recordCount: 0, hasUnassignedHistory: false,
    } }, global: options() }); wrappers.push(wrapper)
    await wrapper.findAll('button').find(b => b.text() === '未指定行動的歷次整理')!.trigger('click')
    await flushPromises()
    const editor = wrapper.findComponent({ name: 'HistoryEditor' })
    expect(editor.props('methodId')).toBeNull()
    expect(editor.props('threadId')).toBe(3)
    expect(editor.props('label')).toBe('空探索線／未指定行動')
  })
  it('既有行動可編輯空歷史並開啟搬移，未指定行動不提供搬移', async () => {
    const wrapper = mount(ExplorationMethodGroup, { props: { experimentId: 1, threadId: 3, method, count: 0, hasHistory: false, initialRecords: [] }, global: options() }); wrappers.push(wrapper)
    await wrapper.findAll('button').find(b => b.text() === '調整所屬探索線')!.trigger('click')
    await flushPromises()
    expect(wrapper.emitted('assign-method')?.[0]).toEqual([method])
    await wrapper.findAll('button').find(b => b.text() === '編輯歷次整理')!.trigger('click')
    await flushPromises()
    expect(wrapper.emitted('edit-history')?.[0]).toEqual([method])
    await wrapper.setProps({ method: null })
    expect(wrapper.findAll('button').some(b => b.text() === '調整所屬探索線')).toBe(false)
  })
  it('背景更新移除行動列時，歷史編輯器與搬移彈窗保留原目標', async () => {
    const thread = {
      id: 3, experimentId: 1, name: 'A', status: 'NOT_STARTED' as const, conclusion: null, sortOrder: 0,
      lastActivityAt: '', createdAt: '', updatedAt: '', methods: [{ ...method, sortOrder: 0, sourceRecordId: null, createdAt: '', updatedAt: '' }], records: [], cards: [], works: [],
      methodCount: 1, recordCount: 0, hasUnassignedHistory: false,
    }
    const wrapper = mount(ExplorationThreadActivity, { props: { experimentId: 1, thread }, global: options() }); wrappers.push(wrapper)
    const group = wrapper.findComponent(ExplorationMethodGroup)
    group.vm.$emit('edit-history', method); group.vm.$emit('assign-method', method)
    await flushPromises()
    const editor = wrapper.findComponent({ name: 'HistoryEditor' }).vm
    await wrapper.setProps({ thread: { ...thread, methods: [], methodCount: 0 } })
    expect(wrapper.findComponent(ExplorationMethodGroup).exists()).toBe(false)
    expect(wrapper.findComponent({ name: 'HistoryEditor' }).vm).toBe(editor)
    expect(wrapper.findComponent({ name: 'HistoryEditor' }).props('methodId')).toBe(8)
    expect(wrapper.findComponent({ name: 'AssignmentDialog' }).props('methodId')).toBe(8)
  })
})
