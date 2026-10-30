import { mount, flushPromises } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { describe, expect, it, vi } from 'vitest'
import WorkResume from './WorkResume.vue'
import WorkResumeEntry from './WorkResumeEntry.vue'
import type { WorkSummary } from '../../types/work'

const list = vi.hoisted(() => vi.fn())
vi.mock('../../utils/api/workApi', () => ({ workApi: { list } }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }), useRoute: () => ({ fullPath: '/experiments/7' }) }))

const work: WorkSummary = { id: 12, title: '虛構作品', description: '作品庫的完整說明', experimentId: 7, experimentTitle: '寫作', editorMode: 'INTERNAL', stage: 'WORKING', nextStep: '', published: false, archived: false, treasureId: null, characterCount: 0, editedAt: '2026-10-22T00:00:00Z' }

describe('作品接續摘要', () => {
  it('只有選擇隱藏空作品時才收起區塊，載入與失敗仍可見', async () => {
    let rejectRequest!: (reason: Error) => void
    list.mockImplementationOnce(() => new Promise((_, reject) => { rejectRequest = reject }))
    const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
    const wrapper = mount(WorkResume, { props: { compact: true, hideEmpty: true }, global: {
      plugins: [[VueQueryPlugin, { queryClient: client }]], stubs: {
        WorkFormDialog: true, RouterLink: { template: '<a><slot /></a>' },
        'el-button': { template: '<button><slot /></button>' },
        'el-alert': { props: ['title'], template: '<div role="alert">{{ title }}<slot /></div>' },
      },
    } })
    try {
      expect(wrapper.text()).toContain('正在載入作品')
      rejectRequest(new Error('無法連線'))
      await flushPromises()
      expect(wrapper.get('[role="alert"]').text()).toContain('無法載入作品')
      list.mockResolvedValue({ content: [], totalElements: 0 })
      await wrapper.get('[role="alert"] button').trigger('click')
      await flushPromises()
      expect(wrapper.find('.resume-work').exists()).toBe(false)
      await wrapper.setProps({ hideEmpty: false, experimentId: 7 })
      await flushPromises()
      expect(wrapper.find('.resume-work').exists()).toBe(true)
      expect(wrapper.text()).toContain('此實驗目前沒有未封存的作品')
    } finally { wrapper.unmount(); client.clear() }
  })

  it('總覽摘要顯示後端字元數，外部文件不冒充零字元，並保留原本的非緊湊呈現', async () => {
    const global = { stubs: { RouterLink: { template: '<a><slot /></a>' } } }
    const wrapper = mount(WorkResumeEntry, { props: { work: { ...work, nextStep: '補上結尾', characterCount: 1280 }, compact: true }, global })
    try {
      expect(wrapper.text()).toContain('1,280 字元')
      expect(wrapper.text()).toContain('補上結尾')
      expect(wrapper.find('.resume-entry-label').exists()).toBe(false)
      await wrapper.setProps({ work: { ...work, editorMode: 'EXTERNAL', characterCount: null } })
      expect(wrapper.text()).toContain('外部文件')
      expect(wrapper.text()).not.toContain('0 字元')
      await wrapper.setProps({ compact: false, work: { ...work, nextStep: '保留原標籤' } })
      expect(wrapper.get('.resume-entry-label').text()).toBe('下次接著做')
      expect(wrapper.classes()).not.toContain('resume-entry--compact')
    } finally { wrapper.unmount() }
  })

  it('總覽沿用同一份作品列表資料，不為字元數逐件取得正文', async () => {
    list.mockResolvedValue({ content: [{ ...work, characterCount: 6 }], totalElements: 12 })
    const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
    const wrapper = mount(WorkResume, { props: { compact: true }, global: { plugins: [[VueQueryPlugin, { queryClient: client }]], stubs: {
      WorkFormDialog: true, RouterLink: { template: '<a><slot /></a>' }, 'el-button': { template: '<button><slot /></button>' },
    } } })
    try {
      await flushPromises()
      expect(wrapper.text()).toContain('6 字元')
      expect(wrapper.get('.app-section-title').text()).toBe('接著創作')
      expect(wrapper.get('[aria-label="接著創作數量：12"]').text()).toBe('12')
      expect(wrapper.findAll('.resume-entry')).toHaveLength(1)
      expect(wrapper.text()).toContain('查看全部')
      expect(wrapper.text()).not.toContain('查看全部作品')
      expect(wrapper.classes()).toContain('resume-work--compact')
    } finally { wrapper.unmount(); client.clear() }
  })

  it.each([
    { experimentId: undefined, size: 3, compact: false },
    { experimentId: 7, size: 3, compact: false },
    { experimentId: 7, size: 3, compact: true },
  ])('保留接續數量與返回脈絡：實驗 $experimentId、緊湊 $compact', async ({ experimentId, size, compact }) => {
    list.mockResolvedValue({ content: [work], totalElements: 4 })
    const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
    const wrapper = mount(WorkResume, { props: { experimentId, compact }, global: { plugins: [[VueQueryPlugin, { queryClient: client }]], stubs: {
      WorkFormDialog: true,
      RouterLink: { name: 'RouterLink', props: ['to'], template: '<a><slot /></a>' },
      'el-button': { template: '<button><slot /></button>' },
    } } })
    try {
      await vi.waitFor(() => expect(wrapper.find('.resume-entry').exists()).toBe(true))
      expect(list).toHaveBeenCalledWith({ experimentId, size })
      expect(wrapper.text()).not.toContain('作品庫的完整說明')
      expect(wrapper.find('.resume-entry-next').exists()).toBe(false)
      if (compact) {
        expect(wrapper.get('.app-section-title').text()).toBe('正在製作')
        expect(wrapper.get('[aria-label="正在製作數量：4"]').text()).toBe('4')
        expect(wrapper.text()).toContain('0 字元')
        expect(wrapper.get('.resume-entry').classes()).toContain('resume-entry--compact')
      }
      const links = wrapper.findAllComponents({ name: 'RouterLink' })
      expect(links.find(link => link.text() === (compact ? '查看全部' : '查看全部作品'))!.props('to')).toEqual({ path: '/works', query: experimentId ? { experimentId } : {} })
      expect(links.find(link => link.text() === '虛構作品')!.props('to')).toEqual({ path: '/works/12', query: { from: '/experiments/7' } })
      expect(wrapper.text()).not.toContain('繼續製作 →')
      await wrapper.get('button').trigger('click')
      expect(wrapper.findComponent({ name: 'WorkFormDialog' }).props('experimentId')).toBe(experimentId)
    } finally { wrapper.unmount(); client.clear() }
  })
})
