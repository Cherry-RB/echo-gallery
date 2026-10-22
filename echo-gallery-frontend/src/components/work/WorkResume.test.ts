import { mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { describe, expect, it, vi } from 'vitest'
import WorkResume from './WorkResume.vue'
import type { WorkSummary } from '../../types/work'

const list = vi.hoisted(() => vi.fn())
vi.mock('../../utils/api/workApi', () => ({ workApi: { list } }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }), useRoute: () => ({ fullPath: '/experiments/7' }) }))

const work: WorkSummary = { id: 12, title: '虛構作品', description: '作品庫的完整說明', experimentId: 7, experimentTitle: '寫作', editorMode: 'INTERNAL', stage: 'WORKING', nextStep: '', published: false, archived: false, treasureId: null, editedAt: '2026-10-22T00:00:00Z' }

describe('作品接續摘要', () => {
  it.each([{ experimentId: undefined, size: 3 }, { experimentId: 7, size: 3 }])('保留接續數量與返回脈絡：$size 件', async ({ experimentId, size }) => {
    list.mockResolvedValue({ content: [work], totalElements: 4 })
    const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
    const wrapper = mount(WorkResume, { props: { experimentId }, global: { plugins: [[VueQueryPlugin, { queryClient: client }]], stubs: {
      WorkFormDialog: true,
      RouterLink: { name: 'RouterLink', props: ['to'], template: '<a><slot /></a>' },
      'el-button': { template: '<button><slot /></button>' },
    } } })
    try {
      await vi.waitFor(() => expect(wrapper.find('.resume-entry').exists()).toBe(true))
      expect(list).toHaveBeenCalledWith({ experimentId, size })
      expect(wrapper.text()).not.toContain('作品庫的完整說明')
      expect(wrapper.find('.resume-entry-next').exists()).toBe(false)
      const links = wrapper.findAllComponents({ name: 'RouterLink' })
      expect(links.find(link => link.text() === '查看全部作品')!.props('to')).toEqual({ path: '/works', query: experimentId ? { experimentId } : {} })
      expect(links.find(link => link.text() === '虛構作品')!.props('to')).toEqual({ path: '/works/12', query: { from: '/experiments/7' } })
      expect(wrapper.text()).not.toContain('繼續製作 →')
    } finally { wrapper.unmount(); client.clear() }
  })
})
