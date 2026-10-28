import { defineComponent, h, ref } from 'vue'
import { mount } from '@vue/test-utils'
import { onlineManager, QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { createMemoryHistory, createRouter } from 'vue-router'
import { describe, expect, it, vi } from 'vitest'
import WorkDetail from './WorkDetail.vue'
import type { WorkDetail as Detail } from '../../types/work'

const api = vi.hoisted(() => ({ detail: vi.fn() }))
vi.mock('../../utils/api/workApi', () => ({ workApi: api }))

const detail: Detail = {
  work: { id: 1, title: '作品', description: '', experimentId: null, experimentTitle: null, editorMode: 'INTERNAL', stage: 'WORKING', nextStep: '', published: false, archived: false, treasureId: null, characterCount: 0, editedAt: '' },
  body: '舊稿', bodyVersion: 0, externalUrl: null, firstCompletedOn: null, stageChangedAt: '', shareUrl: null, sharedOn: null, shareNote: null,
}
const Workspace = defineComponent({
  props: ['initial'],
  setup(props) {
    const body = ref(props.initial.body)
    return () => h('input', { class: 'workspace-body', value: body.value, onInput: (e: Event) => { body.value = (e.target as HTMLInputElement).value } })
  },
})
async function render(client: QueryClient) {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/works/:id', component: { template: '<div />' } }] })
  await router.push('/works/1')
  return mount(WorkDetail, { global: {
    plugins: [router, [VueQueryPlugin, { queryClient: client }]],
    stubs: { WorkWorkspace: Workspace, 'el-button': true, 'el-skeleton': true, 'el-result': { template: '<div class="load-error" />' } },
  } })
}

describe('作品詳情重新進入', () => {
  it('等待伺服器新版再建立工作區，後續背景更新不覆蓋正在輸入的正文', async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: Infinity } } })
    client.setQueryData(['works', 'detail', 1], detail)
    let resolve!: (value: Detail) => void
    api.detail.mockReturnValueOnce(new Promise<Detail>(done => { resolve = done }))
    const wrapper = await render(client)
    try {
      expect(wrapper.find('.workspace-body').exists()).toBe(false)
      resolve({ ...detail, body: '伺服器新版', bodyVersion: 1 })
      await vi.waitFor(() => expect(wrapper.find<HTMLInputElement>('.workspace-body').element.value).toBe('伺服器新版'))
      await wrapper.get('.workspace-body').setValue('尚未儲存的續寫')
      api.detail.mockResolvedValueOnce({ ...detail, body: '另一份背景更新', bodyVersion: 2 })
      await client.invalidateQueries({ queryKey: ['works', 'detail', 1] })
      expect(wrapper.find<HTMLInputElement>('.workspace-body').element.value).toBe('尚未儲存的續寫')
    } finally { wrapper.unmount(); client.clear() }
  })
  it('取得最新版失敗時，不開啟快取的舊稿供編輯', async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: Infinity } } })
    client.setQueryData(['works', 'detail', 1], detail)
    api.detail.mockRejectedValueOnce(new Error('離線'))
    const wrapper = await render(client)
    try {
      await vi.waitFor(() => expect(wrapper.find('.load-error').exists()).toBe(true))
      expect(wrapper.find('.workspace-body').exists()).toBe(false)
    } finally { wrapper.unmount(); client.clear() }
  })
  it('離線而暫停取得最新版時，也不先開啟快取舊稿', async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: Infinity } } })
    client.setQueryData(['works', 'detail', 1], detail)
    onlineManager.setOnline(false)
    const wrapper = await render(client)
    try {
      expect(client.getQueryState(['works', 'detail', 1])?.fetchStatus).toBe('paused')
      expect(wrapper.find('.workspace-body').exists()).toBe(false)
    } finally { wrapper.unmount(); client.clear(); onlineManager.setOnline(true) }
  })
})
