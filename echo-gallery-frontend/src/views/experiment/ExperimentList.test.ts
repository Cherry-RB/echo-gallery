import { mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ExperimentList from './ExperimentList.vue'

const state = vi.hoisted(() => ({ list: vi.fn(), archive: vi.fn(), push: vi.fn(), exploration: vi.fn() }))
vi.mock('../../utils/api/experimentApi', () => ({ experimentApi: { getExperiments: state.list, setExperimentArchived: state.archive, getExploration: state.exploration } }))
vi.mock('../../utils/api/workApi', () => ({ workApi: { usages: vi.fn().mockResolvedValue([]) } }))
vi.mock('vue-router', async importOriginal => ({ ...await importOriginal<typeof import('vue-router')>(), useRouter: () => ({ push: state.push }) }))
vi.mock('element-plus', async importOriginal => ({
  ...await importOriginal<typeof import('element-plus')>(), ElMessage: { success: vi.fn(), error: vi.fn() },
}))
const experiment = { id: 1, title: '探索主題', currentTry: '先試一次', explorationRecordCount: 1, themeColor: 'LEAF', updatedAt: '2026-10-16T00:00:00Z', isArchived: false }
const clients: QueryClient[] = []
const wrappers: ReturnType<typeof mount>[] = []
function render() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
  clients.push(client)
  const wrapper = mount(ExperimentList, { global: {
    plugins: [[VueQueryPlugin, { queryClient: client }]],
    stubs: {
      AppDialog: true, ExperimentExplorationEditor: true, ExperimentExplorationHistoryDialog: true,
      'el-card': { template: '<article><slot /></article>' },
      'el-dropdown': { name: 'ElDropdown', emits: ['command'], template: '<div><slot /></div>' },
      'el-button': { props: ['disabled'], emits: ['click'], template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>' },
      'el-tooltip': { template: '<span><slot /></span>' },
      'el-radio-group': { name: 'ElRadioGroup', props: ['modelValue'], emits: ['update:modelValue'], template: '<div><slot /></div>' },
      'el-radio-button': true,
      'el-pagination': { name: 'ElPagination', props: ['currentPage', 'pageSize', 'layout'], emits: ['current-change'], template: '<nav />' },
      'el-skeleton': true, 'el-result': true,
      'el-dropdown-item': true, 'el-dropdown-menu': true, 'el-input': true, 'el-form-item': true, 'el-form': true,
    },
  } })
  wrappers.push(wrapper)
  return wrapper
}

describe('實驗列表操作與分頁', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    state.list.mockResolvedValue({ content: [experiment], totalElements: 21 })
    state.exploration.mockResolvedValue({ currentTry: '', favoriteTries: [], records: [] })
  })
  afterEach(() => { wrappers.splice(0).forEach(w => w.unmount()); clients.splice(0).forEach(c => c.clear()) })

  it('子按鈕的 Enter／Space 不觸發卡片導頁，卡片本身仍可由鍵盤開啟', async () => {
    const wrapper = render()
    await vi.waitFor(() => expect(wrapper.find('.quick-record-trigger').exists()).toBe(true))
    const record = wrapper.get('.quick-record-trigger')
    await record.trigger('keydown', { key: 'Enter' })
    await record.trigger('keydown', { key: ' ' })
    await record.trigger('click')
    expect(state.push).not.toHaveBeenCalled()
    const card = wrapper.get('article[role="link"]')
    await card.trigger('keydown', { key: 'Enter' })
    await card.trigger('keydown', { key: ' ' })
    expect(state.push.mock.calls).toEqual([['/experiments/1'], ['/experiments/1']])
  })

  it('點擊共用歷程計數只開啟該實驗紀錄，不觸發外層卡片導頁', async () => {
    const wrapper = render()
    await vi.waitFor(() => expect(wrapper.find('.record-history-trigger').exists()).toBe(true))
    const history = wrapper.get('.record-history-trigger')
    expect(history.attributes('aria-label')).toBe('查看 1 筆探索紀錄')
    await history.trigger('keydown', { key: 'Enter' })
    await history.trigger('keydown', { key: ' ' })
    await history.trigger('click')
    expect(state.push).not.toHaveBeenCalled()
    expect(wrapper.findComponent({ name: 'ExperimentExplorationHistoryDialog' }).props('experimentId')).toBe(1)
  })

  it('封存末頁最後一筆後回到前頁，分頁保持每頁二十筆', async () => {
    let removed = false
    state.list.mockImplementation((_archived, page) => Promise.resolve({ content: removed && page === 1 ? [] : [experiment], totalElements: removed ? 20 : 21 }))
    state.archive.mockImplementation(async () => { removed = true; return { ...experiment, isArchived: true } })
    const wrapper = render()
    await vi.waitFor(() => expect(wrapper.find('article[role="link"]').exists()).toBe(true))
    const pagination = wrapper.findComponent({ name: 'ElPagination' })
    expect(pagination.props('pageSize')).toBe(20)
    expect(pagination.props('layout')).toBe('total, prev, pager, next')
    pagination.vm.$emit('current-change', 2)
    await vi.waitFor(() => expect(state.list).toHaveBeenCalledWith(false, 1, 20))
    wrapper.findAllComponents({ name: 'ElDropdown' })[1]!.vm.$emit('command', 'archive')
    await vi.waitFor(() => expect(state.archive).toHaveBeenCalledWith(1, true))
    await vi.waitFor(() => expect(state.list.mock.calls.at(-1)).toEqual([false, 0, 20]))
    expect(wrapper.text()).toContain('探索主題')
  })

  it('封存列表仍可記錄，保留既有行為', async () => {
    state.list.mockResolvedValue({ content: [{ ...experiment, isArchived: true }], totalElements: 1 })
    const wrapper = render()
    await vi.waitFor(() => expect(wrapper.find('.quick-record-trigger').exists()).toBe(true))
    wrapper.findComponent({ name: 'ElRadioGroup' }).vm.$emit('update:modelValue', 'ARCHIVED')
    await vi.waitFor(() => expect(state.list).toHaveBeenCalledWith(true, 0, 20))
    expect(wrapper.find('.quick-record-trigger').exists()).toBe(true)
  })

  it('全部範圍立即載入觀察中與已封存實驗', async () => {
    const wrapper = render()
    await vi.waitFor(() => expect(wrapper.find('.quick-record-trigger').exists()).toBe(true))
    wrapper.findComponent({ name: 'ElRadioGroup' }).vm.$emit('update:modelValue', 'ALL')
    await vi.waitFor(() => expect(state.list).toHaveBeenCalledWith('all', 0, 20))
  })
})
