import { flushPromises, mount } from '@vue/test-utils'
import type { GlobalMountOptions } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import WorkProgressUpdates from './WorkProgressUpdates.vue'
import { workApi } from '../../utils/api/workApi'

vi.mock('../../utils/api/workApi', () => ({
  workApi: {
    getWorkUpdates: vi.fn(),
    createWorkUpdate: vi.fn(),
    updateWorkUpdate: vi.fn(),
    deleteWorkUpdate: vi.fn(),
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

describe('WorkProgressUpdates', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(workApi.getWorkUpdates).mockResolvedValue({
      items: [],
      page: 0,
      size: 5,
      hasNext: false,
    })
    vi.mocked(workApi.createWorkUpdate).mockResolvedValue({
      id: 1,
      workId: 7,
      workTitle: '測試議題',
      changeSummary: '取得新回饋',
      assessment: null,
      nextStep: null,
      createdAt: '2026-09-06T00:00:00Z',
      updatedAt: '2026-09-06T00:00:00Z',
    })
  })

  it('拒絕發布三個欄位皆空白的更新', async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const wrapper = mount(WorkProgressUpdates, {
      props: { workId: 7 },
      global: globalOptions(queryClient),
    })
    await flushPromises()

    await wrapper.findAll('button').find((button) => button.text().includes('提出近況'))?.trigger('click')
    const submitButtons = wrapper.findAll('button').filter((button) => button.text().includes('提出近況'))
    await submitButtons.at(-1)?.trigger('click')

    expect(wrapper.text()).toContain('至少寫下一項')
    expect(workApi.createWorkUpdate).not.toHaveBeenCalled()
  })

  it('任一欄位有內容即可提出近況', async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const wrapper = mount(WorkProgressUpdates, {
      props: { workId: 7 },
      global: globalOptions(queryClient),
    })
    await flushPromises()

    await wrapper.findAll('button').find((button) => button.text().includes('提出近況'))?.trigger('click')
    await wrapper.get('textarea').setValue(' 取得新回饋 ')
    const submitButtons = wrapper.findAll('button').filter((button) => button.text().includes('提出近況'))
    await submitButtons.at(-1)?.trigger('click')
    await flushPromises()

    expect(workApi.createWorkUpdate).toHaveBeenCalledWith(7, {
      changeSummary: '取得新回饋',
      assessment: null,
      nextStep: null,
    })
  })

  it('最新近況完整顯示改變、研判與下一步', async () => {
    vi.mocked(workApi.getWorkUpdates).mockResolvedValue({
      items: [{
        id: 2,
        workId: 7,
        workTitle: '測試議題',
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
    const wrapper = mount(WorkProgressUpdates, {
      props: { workId: 7 },
      global: globalOptions(queryClient),
    })

    await flushPromises()

    expect(wrapper.text()).toContain('最近有什麼改變？')
    expect(wrapper.text()).toContain('收到新的市場回饋')
    expect(wrapper.text()).toContain('現在怎麼看？')
    expect(wrapper.text()).toContain('原本的方向仍值得推進')
    expect(wrapper.text()).toContain('所以接下來呢？')
    expect(wrapper.text()).toContain('安排下一次訪談')
  })

  it('初始只查詢五筆，後續更新只呈現日期與改變內容', async () => {
    vi.mocked(workApi.getWorkUpdates).mockResolvedValue({
      items: Array.from({ length: 5 }, (_, index) => ({
        id: 10 - index,
        workId: 7,
        workTitle: '測試議題',
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
    const wrapper = mount(WorkProgressUpdates, {
      props: { workId: 7 },
      global: globalOptions(queryClient),
    })

    await flushPromises()

    expect(workApi.getWorkUpdates).toHaveBeenCalledWith(7, 0, 5)
    expect(wrapper.text()).toContain('研判 1')
    expect(wrapper.text()).not.toContain('研判 2')
    expect(wrapper.text()).not.toContain('下一步 5')
    expect(wrapper.text()).toContain('變化 5')
  })

  it('開啟歷次更新後才查詢十筆歷程', async () => {
    vi.mocked(workApi.getWorkUpdates).mockResolvedValue({
      items: [{
        id: 2,
        workId: 7,
        workTitle: '測試議題',
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
    const wrapper = mount(WorkProgressUpdates, {
      props: { workId: 7 },
      global: globalOptions(queryClient),
    })
    await flushPromises()

    expect(workApi.getWorkUpdates).toHaveBeenCalledTimes(1)
    await wrapper.findAll('button').find((button) => button.text().includes('查看歷次更新'))?.trigger('click')
    await flushPromises()

    expect(workApi.getWorkUpdates).toHaveBeenCalledWith(7, 0, 10)
  })
})
