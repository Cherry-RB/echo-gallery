import { defineComponent, h, ref } from 'vue'
import { mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { createMemoryHistory, createRouter } from 'vue-router'
import { describe, expect, it, vi } from 'vitest'
import WorkDetail from './WorkDetail.vue'
import type { WorkDetail as Detail } from '../../types/work'

const api = vi.hoisted(() => ({ detail: vi.fn() }))
vi.mock('../../utils/api/workApi', () => ({ workApi: api }))

const detail: Detail = {
  work: { id: 1, title: '作品', description: '', experimentId: null, experimentTitle: null, editorMode: 'INTERNAL', stage: 'WORKING', nextStep: '', published: false, archived: false, treasureId: null, characterCount: 0, editedAt: '' },
  body: '原始內容', bodyVersion: 0, externalUrl: null, firstCompletedOn: null, stageChangedAt: '', shareUrl: null, sharedOn: null, shareNote: null,
}
const Workspace = defineComponent({
  props: ['initial'],
  setup(props) {
    const body = ref(props.initial.body)
    return () => h('input', { class: 'workspace-body', value: body.value, onInput: (event: Event) => { body.value = (event.target as HTMLInputElement).value } })
  },
})

describe('作品詳情的背景載入失敗', () => {
  it('保留已開啟的編輯器及未儲存內容', async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: Infinity } } })
    api.detail.mockResolvedValueOnce(detail)
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/works/:id', component: { template: '<div />' } }] })
    await router.push('/works/1')
    const wrapper = mount(WorkDetail, { global: {
      plugins: [router, [VueQueryPlugin, { queryClient: client }]],
      stubs: { WorkWorkspace: Workspace, 'el-button': true, 'el-skeleton': true, 'el-result': true, 'el-alert': true },
    } })
    try {
      await vi.waitFor(() => expect(wrapper.find('.workspace-body').exists()).toBe(true))
      await wrapper.get('.workspace-body').setValue('尚未儲存的內容')
      api.detail.mockRejectedValueOnce(new Error('暫時無法連線'))
      await client.invalidateQueries({ queryKey: ['works', 'detail', 1] })
      await vi.waitFor(() => expect(wrapper.find('.work-detail-refresh-error').exists()).toBe(true))
      expect(wrapper.find<HTMLInputElement>('.workspace-body').element.value).toBe('尚未儲存的內容')
    } finally {
      wrapper.unmount()
      client.clear()
    }
  })
})
