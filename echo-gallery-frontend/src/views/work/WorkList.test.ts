import { mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { createMemoryHistory, createRouter } from 'vue-router'
import { ElInput, ElOption, ElRadioButton, ElRadioGroup, ElSelect } from 'element-plus'
import { describe, expect, it, vi } from 'vitest'
import WorkList from './WorkList.vue'
import { workApi } from '../../utils/api/workApi'

vi.mock('../../utils/api/workApi', () => ({ workApi: {
  list: vi.fn().mockResolvedValue({ content: [{ id: 1 }], totalElements: 21, totalPages: 2 }),
  experiments: vi.fn().mockResolvedValue([{ id: 7, title: '????', archived: false }]),
} }))

describe('????????', () => {
  it('?????????????????????', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/works', component: { template: '<div />' } }] })
    await router.push('/works?page=2'); await router.isReady()
    const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
    const wrapper = mount(WorkList, { global: { plugins: [router, [VueQueryPlugin, { queryClient: client }]], components: { ElInput, ElOption, ElRadioButton, ElRadioGroup, ElSelect }, stubs: {
      WorkPreview: true, WorkFormDialog: true, 'el-button': { template: '<button><slot /></button>' }, 'el-pagination': true, 'el-skeleton': true, 'el-result': true,
    } } })
    try {
      await vi.waitFor(() => expect(wrapper.find('.work-results-heading').exists()).toBe(true))
      expect(wrapper.findAllComponents(ElSelect)).toHaveLength(3)
      wrapper.findAllComponents(ElSelect)[0]!.vm.$emit('update:modelValue', ['7', 'none'])
      wrapper.findAllComponents(ElSelect)[1]!.vm.$emit('update:modelValue', ['WORKING'])
      await wrapper.vm.$nextTick()
      expect(workApi.list).toHaveBeenLastCalledWith(expect.objectContaining({ experimentIds: undefined, stages: undefined }))
      await wrapper.get('form').trigger('submit')
      await vi.waitFor(() => expect(router.currentRoute.value.query.experimentIds).toBe('7'))
      expect(router.currentRoute.value.query.unassigned).toBe('true')
      expect(router.currentRoute.value.query.stages).toBe('WORKING')
      expect(router.currentRoute.value.query.page).toBeUndefined()
      await vi.waitFor(() => expect(workApi.list).toHaveBeenLastCalledWith(expect.objectContaining({ experimentIds: '7', unassigned: 'true', stages: 'WORKING', page: 0 })))
    } finally { wrapper.unmount(); client.clear() }
  })
})
