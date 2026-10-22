import { defineComponent } from 'vue'
import { mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { createMemoryHistory, createRouter } from 'vue-router'
import { describe, expect, it, vi } from 'vitest'
import WorkSourcePicker from './WorkSourcePicker.vue'
import WorkUseFlow from './WorkUseFlow.vue'
import { workUseTarget } from '../../utils/workFlow'

const record = { id: null, cardId: null, recordId: 8, title: '探索原文', text: '發現', tryText: null, discovery: '發現', experimentId: 7, experimentTitle: '寫作', archived: false }
vi.mock('../../utils/api/workApi', () => ({ workApi: {
  candidates: vi.fn(async () => ({ content: [record], totalElements: 1 })),
  experiments: vi.fn(async () => []),
} }))
vi.mock('../../utils/api/cardApi', () => ({ cardApi: { getCard: vi.fn() } }))

describe('素材閱讀子流程的路由返回', () => {
  it.each(['picker', 'use-work'] as const)('%s：瀏覽器返回恢復選取，再次離開才處理父層', async mode => {
    const router = createRouter({ history: createMemoryHistory(), routes: [
      { path: '/before', component: { template: '<div />' } },
      { path: '/works/1', component: { template: '<div />' } },
    ] })
    await router.push('/before'); await router.push('/works/1')
    const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
    const confirmNavigation = vi.fn(async () => true)
    const Shell = defineComponent({
      props: ['modelValue'],
      setup(_, { expose }) { expose({ confirmNavigation }) },
      template: '<section v-if="modelValue"><slot /><slot name="footer" :request-close="() => {}" /></section>',
    })
    if (mode === 'use-work') workUseTarget.value = { recordIds: [8], experimentId: 7, returnToHistory: true }
    const wrapper = mount(mode === 'picker' ? WorkSourcePicker : WorkUseFlow, {
      props: mode === 'picker' ? { modelValue: true, workId: 1 } : {},
      global: {
        plugins: [router, [VueQueryPlugin, { queryClient: client }]], stubs: {
          AppDialog: Shell,
          WorkSourceReadingDialog: { template: '<section class="source-reader">原文</section>' },
          WorkInfoFields: true,
          'el-button': { props: ['disabled'], template: '<button :disabled="disabled"><slot /></button>' },
          'el-input': true, 'el-select': true, 'el-option': true, 'el-tabs': { template: '<div><slot /></div>' },
          'el-tab-pane': true, 'el-pagination': true,
        },
      },
    })
    try {
      await vi.waitFor(() => expect(wrapper.find('.picker-option').exists()).toBe(true))
      if (mode === 'picker') await wrapper.get('.picker-option').trigger('click')
      await wrapper.findAll('button').find(b => b.text() === '閱讀全文')!.trigger('click')
      expect(wrapper.find('.source-reader').exists()).toBe(true)
      router.back()
      await vi.waitFor(() => expect(wrapper.find('.source-reader').exists()).toBe(false))
      expect(router.currentRoute.value.path).toBe('/works/1')
      expect(wrapper.get('.picker-option').attributes('aria-pressed')).toBe('true')
      expect(confirmNavigation).not.toHaveBeenCalled()
      await router.push('/before')
      expect(confirmNavigation).toHaveBeenCalledTimes(1)
      expect(router.currentRoute.value.path).toBe('/before')
    } finally { wrapper.unmount(); workUseTarget.value = undefined; client.clear() }
  })
})
