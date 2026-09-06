import { flushPromises, mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AddCardToIssueDialog from './AddCardToIssueDialog.vue'
import { workApi } from '../../utils/api/workApi'

vi.mock('../../utils/api/workApi', () => ({
  workApi: {
    getCardWorks: vi.fn(),
    getWorks: vi.fn(),
    addWorkCard: vi.fn(),
  },
}))

vi.mock('element-plus', () => ({
  ElMessage: {
    success: vi.fn(),
    error: vi.fn(),
  },
}))

describe('AddCardToIssueDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(workApi.getCardWorks).mockResolvedValue([])
    vi.mocked(workApi.getWorks).mockResolvedValue([
      {
        id: 10,
        title: 'Echo Gallery 下一階段',
        objective: null,
        description: null,
        currentAssessment: null,
        outcomeCriteria: null,
        externalUrl: null,
        status: 'ACTIVE',
        completedAt: null,
        updatedAt: '2026-09-05T00:00:00Z',
        latestProgressAt: null,
        latestProgressChangeSummary: null,
        latestProgressAssessment: null,
        latestProgressNextStep: null,
        candidateCount: 0,
        usedCount: 0,
      },
    ])
    vi.mocked(workApi.addWorkCard).mockResolvedValue({} as never)
  })

  it('將卡片加入選取的議題並關閉對話框', async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const wrapper = mount(AddCardToIssueDialog, {
      props: { modelValue: true, cardId: '5' },
      global: {
        plugins: [[VueQueryPlugin, { queryClient }]],
        stubs: {
          'el-dialog': { props: ['title'], template: '<section><h2>{{ title }}</h2><slot /></section>' },
          'el-skeleton': true,
          'el-result': true,
          'el-empty': true,
          'el-button': { template: '<button @click="$emit(\'click\')"><slot /></button>' },
        },
      },
    })

    await flushPromises()
    expect(wrapper.text()).toContain('Echo Gallery 下一階段')
    expect(wrapper.text()).toContain('推進中')

    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect(workApi.addWorkCard).toHaveBeenCalledWith(10, { cardId: 5 })
    expect(wrapper.emitted('update:modelValue')).toContainEqual([false])
  })
})
