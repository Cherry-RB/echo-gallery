import { flushPromises, mount } from '@vue/test-utils'
import type { GlobalMountOptions } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import IssueUpdates from './IssueUpdates.vue'
import { issueApi } from '../../utils/api/issueApi'

vi.mock('../../utils/api/issueApi', () => ({
  issueApi: {
    getIssueUpdates: vi.fn(),
    createIssueUpdate: vi.fn(),
    updateIssueUpdate: vi.fn(),
    deleteIssueUpdate: vi.fn(),
  },
}))

vi.mock('element-plus', () => ({
  ElMessage: {
    success: vi.fn(),
    error: vi.fn(),
  },
  ElMessageBox: {
    confirm: vi.fn(),
  },
}))

const globalOptions = (queryClient: QueryClient): GlobalMountOptions => ({
  plugins: [[VueQueryPlugin, { queryClient }]],
  stubs: {
    'el-button': { template: '<button @click="$emit(\'click\')"><slot /></button>' },
    'el-dialog': {
      props: ['modelValue', 'title'],
      template: '<section v-if="modelValue"><h2>{{ title }}</h2><slot /><slot name="footer" /></section>',
    },
    'el-input': {
      props: ['modelValue'],
      template: '<textarea :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
    },
    'el-skeleton': true,
    'el-icon': true,
    'el-dropdown': true,
    'el-dropdown-menu': true,
    'el-dropdown-item': true,
  },
})

describe('IssueUpdates', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(issueApi.getIssueUpdates).mockResolvedValue({
      items: [],
      page: 0,
      size: 5,
      hasNext: false,
    })
    vi.mocked(issueApi.createIssueUpdate).mockResolvedValue({
      id: 1,
      issueId: 7,
      issueTitle: '測試議題',
      changeSummary: '取得新回饋',
      assessment: null,
      nextStep: null,
      createdAt: '2026-09-06T00:00:00Z',
      updatedAt: '2026-09-06T00:00:00Z',
    })
  })

  it('拒絕發布三個欄位皆空白的更新', async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const wrapper = mount(IssueUpdates, {
      props: { issueId: 7 },
      global: globalOptions(queryClient),
    })
    await flushPromises()

    await wrapper.findAll('button').find((button) => button.text().includes('記錄訊號'))?.trigger('click')
    const submitButtons = wrapper.findAll('button').filter((button) => button.text().includes('記錄訊號'))
    await submitButtons.at(-1)?.trigger('click')

    expect(wrapper.text()).toContain('至少寫下一項')
    expect(issueApi.createIssueUpdate).not.toHaveBeenCalled()
  })

  it('任一欄位有內容即可記錄系統訊號', async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const wrapper = mount(IssueUpdates, {
      props: { issueId: 7 },
      global: globalOptions(queryClient),
    })
    await flushPromises()

    await wrapper.findAll('button').find((button) => button.text().includes('記錄訊號'))?.trigger('click')
    await wrapper.get('textarea').setValue(' 取得新回饋 ')
    const submitButtons = wrapper.findAll('button').filter((button) => button.text().includes('記錄訊號'))
    await submitButtons.at(-1)?.trigger('click')
    await flushPromises()

    expect(issueApi.createIssueUpdate).toHaveBeenCalledWith(7, {
      changeSummary: '取得新回饋',
      assessment: null,
      nextStep: null,
    })
  })

  it('最新近況完整顯示改變、研判與下一步', async () => {
    vi.mocked(issueApi.getIssueUpdates).mockResolvedValue({
      items: [{
        id: 2,
        issueId: 7,
        issueTitle: '測試議題',
        changeSummary: '收到新的市場回饋',
        assessment: '原本的方向仍值得推進',
        nextStep: '安排下一次訪談',
        createdAt: '2026-09-06T00:00:00Z',
        updatedAt: '2026-09-06T00:00:00Z',
      }],
      page: 0,
      size: 5,
      hasNext: false,
    })
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const wrapper = mount(IssueUpdates, {
      props: { issueId: 7 },
      global: globalOptions(queryClient),
    })

    await flushPromises()

    expect(wrapper.text()).toContain('新訊號')
    expect(wrapper.text()).toContain('收到新的市場回饋')
    expect(wrapper.text()).toContain('模型更新')
    expect(wrapper.text()).toContain('原本的方向仍值得推進')
    expect(wrapper.text()).toContain('介入／等待')
    expect(wrapper.text()).toContain('安排下一次訪談')
  })

  it('初始只查詢最新一筆，不在 dashboard 展開其他歷史', async () => {
    vi.mocked(issueApi.getIssueUpdates).mockResolvedValue({
      items: Array.from({ length: 5 }, (_, index) => ({
        id: 10 - index,
        issueId: 7,
        issueTitle: '測試議題',
        changeSummary: `變化 ${index + 1}`,
        assessment: `研判 ${index + 1}`,
        nextStep: `下一步 ${index + 1}`,
        createdAt: `2026-09-0${6 - index}T00:00:00Z`,
        updatedAt: `2026-09-0${6 - index}T00:00:00Z`,
      })),
      page: 0,
      size: 5,
      hasNext: true,
    })
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const wrapper = mount(IssueUpdates, {
      props: { issueId: 7 },
      global: globalOptions(queryClient),
    })

    await flushPromises()

    expect(issueApi.getIssueUpdates).toHaveBeenCalledWith(7, 0, 1)
    expect(wrapper.text()).toContain('研判 1')
    expect(wrapper.text()).not.toContain('研判 2')
    expect(wrapper.text()).not.toContain('下一步 5')
    expect(wrapper.text()).not.toContain('變化 5')
  })

  it('開啟歷次更新後才查詢十筆歷程', async () => {
    vi.mocked(issueApi.getIssueUpdates).mockResolvedValue({
      items: [{
        id: 2,
        issueId: 7,
        issueTitle: '測試議題',
        changeSummary: '收到新的市場回饋',
        assessment: null,
        nextStep: null,
        createdAt: '2026-09-06T00:00:00Z',
        updatedAt: '2026-09-06T00:00:00Z',
      }],
      page: 0,
      size: 5,
      hasNext: false,
    })
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const wrapper = mount(IssueUpdates, {
      props: { issueId: 7 },
      global: globalOptions(queryClient),
    })
    await flushPromises()

    expect(issueApi.getIssueUpdates).toHaveBeenCalledTimes(1)
    await wrapper.findAll('button').find((button) => button.text().includes('歷次更新'))?.trigger('click')
    await flushPromises()

    expect(issueApi.getIssueUpdates).toHaveBeenCalledWith(7, 0, 10)
  })

  it('歷次訊號完整顯示三個同層欄位與時間方向分頁', async () => {
    vi.mocked(issueApi.getIssueUpdates).mockImplementation(async (_issueId, _page, size) => ({
      items: [{
        id: 3,
        issueId: 7,
        issueTitle: '測試議題',
        changeSummary: '收到使用者訪談回饋',
        assessment: '原先假設只得到部分支持',
        nextStep: '再等待兩筆不同來源的訊號',
        createdAt: '2026-09-07T00:00:00Z',
        updatedAt: '2026-09-07T00:00:00Z',
      }],
      page: 0,
      size: size ?? 10,
      hasNext: true,
    }))
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const wrapper = mount(IssueUpdates, {
      props: { issueId: 7 },
      global: globalOptions(queryClient),
    })
    await flushPromises()

    await wrapper.findAll('button').find(button => button.text().includes('歷次更新'))?.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('歷次系統訊號')
    expect(wrapper.text()).toContain('收到使用者訪談回饋')
    expect(wrapper.text()).toContain('原先假設只得到部分支持')
    expect(wrapper.text()).toContain('再等待兩筆不同來源的訊號')
    expect(wrapper.text()).toContain('較新的訊號')
    expect(wrapper.text()).toContain('較舊的訊號')
  })
})
