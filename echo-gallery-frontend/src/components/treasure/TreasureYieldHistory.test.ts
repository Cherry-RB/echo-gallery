import { mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import TreasureYieldHistory from './TreasureYieldHistory.vue'

const api = vi.hoisted(() => ({ yields: vi.fn() }))
vi.mock('../../utils/api/treasureApi', () => ({ treasureApi: api }))
const records = Array.from({ length: 4 }, (_, index) => ({ id: index + 1, title: `產出 ${index + 1}`, occurredOn: `2026-10-${15 - index}`, description: `歷程 ${index + 1}`, relatedCard: { id: index + 10, title: `筆記 ${index + 1}` } }))
const clients: QueryClient[] = []
const wrappers: ReturnType<typeof mount>[] = []
function render(archived = false) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } }); clients.push(client)
  const wrapper = mount(TreasureYieldHistory, { props: { treasureId: 1, compact: true, archived }, global: {
    plugins: [[VueQueryPlugin, { queryClient: client }]],
    stubs: {
      AppDialog: { props: ['modelValue', 'title'], template: '<aside v-if="modelValue" class="history-dialog"><h1>{{ title }}</h1><slot /><footer><slot name="footer" /></footer></aside>' },
      TreasureYieldDialog: { props: ['modelValue'], template: '<aside v-if="modelValue" class="shared-editor" />' },
      ExpandableText: { props: ['content'], template: '<p>{{ content }}</p>' },
      AppEmptyState: { props: ['description'], template: '<p>{{ description }}</p>' },
      RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' },
      'el-button': { props: ['disabled'], template: '<button :disabled="disabled"><slot /></button>' },
      'el-dropdown': { template: '<div class="record-menu"><slot /></div>' },
      'el-pagination': true, 'el-icon': true, 'el-dropdown-menu': true, 'el-dropdown-item': true, 'el-alert': true,
    },
  } }); wrappers.push(wrapper); return wrapper
}
describe('產出摘要與完整歷程', () => {
  beforeEach(() => { vi.clearAllMocks(); api.yields.mockResolvedValue({ content: records, totalElements: 4 }) })
  afterEach(() => { wrappers.splice(0).forEach(w => w.unmount()); clients.splice(0).forEach(c => c.clear()) })

  it('詳情只預覽三筆，完整歷程能閱讀第四筆及關聯卡片', async () => {
    const wrapper = render()
    await vi.waitFor(() => expect(wrapper.findAll('.record-entry')).toHaveLength(3))
    expect(wrapper.text()).not.toContain('產出 4')
    expect(wrapper.get('time').text()).toBe('發生於 2026/10/15')
    expect(wrapper.findAll('.latest-label')).toHaveLength(1)
    expect(wrapper.get('.yield-heading h2').text()).toBe('收益・最近 3 筆')
    expect(wrapper.get('.yield-actions .record-count').text()).toBe('4')
    expect(wrapper.get('.record-entry .related-card').attributes('href')).toBe('/card/10')
    expect(wrapper.get('.record-entry').text()).toContain('過程與帶來的價值')
    const description = wrapper.get('.yield-heading .app-dialog-intro').text()
    await wrapper.get('[aria-label="查看 4 筆收益紀錄"]').trigger('click')
    await vi.waitFor(() => expect(wrapper.findAll('.history-dialog .record-entry')).toHaveLength(4))
    expect(wrapper.get('.history-dialog').text()).toContain('歷程 4')
    expect(wrapper.get('.history-dialog a').attributes('href')).toBe('/card/10')
    expect(wrapper.find('.history-dialog .summary-copy').exists()).toBe(false)
    expect(wrapper.get('.history-dialog .history-toolbar .app-dialog-intro').text()).toBe(description)
    expect(wrapper.find('.history-dialog .preview-range').exists()).toBe(false)
    await wrapper.findAll('.history-dialog button').find(b => b.text() === '＋記收益')!.trigger('click')
    expect(wrapper.find('.shared-editor').exists()).toBe(true)
    expect(wrapper.find('.history-dialog').exists()).toBe(true)
  })

  it('封存後可從摘要與完整歷程新增產出，既有紀錄仍不提供編輯刪除', async () => {
    const wrapper = render(true)
    await vi.waitFor(() => expect(wrapper.findAll('.record-entry')).toHaveLength(3))
    expect(wrapper.find('.record-menu').exists()).toBe(false)
    await wrapper.findAll('.yield-actions button').find(b => b.text() === '＋記收益')!.trigger('click')
    expect(wrapper.find('.shared-editor').exists()).toBe(true)
    await wrapper.get('[aria-label="查看 4 筆收益紀錄"]').trigger('click')
    await vi.waitFor(() => expect(wrapper.findAll('.history-dialog .record-entry')).toHaveLength(4))
    expect(wrapper.find('.history-dialog .record-menu').exists()).toBe(false)
    expect(wrapper.findAll('.history-dialog button').some(b => b.text() === '＋記收益')).toBe(true)
  })

  it('零筆時保留新增入口，隱藏完整歷程入口與時間軸', async () => {
    api.yields.mockResolvedValue({ content: [], totalElements: 0 })
    const wrapper = render()
    await vi.waitFor(() => expect(wrapper.text()).toContain('它已經入藏'))
    expect(wrapper.find('.record-entry').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('查看全部')
    expect(wrapper.get('.yield-heading h2').text()).toBe('收益')
    expect(wrapper.find('.record-history-trigger').exists()).toBe(false)
    await wrapper.findAll('button').find(b => b.text() === '＋記收益')!.trigger('click')
    expect(wrapper.find('.shared-editor').exists()).toBe(true)
  })

  it.each([1, 3, 8])('共 %i 筆時，標題範圍、總數與實際預覽筆數一致', async count => {
    const content = Array.from({ length: count }, (_, index) => ({ ...records[0]!, id: index + 1, title: `產出 ${index + 1}` }))
    api.yields.mockResolvedValue({ content, totalElements: count })
    const wrapper = render()
    await vi.waitFor(() => expect(wrapper.findAll('.record-entry')).toHaveLength(Math.min(count, 3)))
    expect(wrapper.get('.yield-heading h2').text()).toBe(count > 3 ? '收益・最近 3 筆' : '收益')
    expect(wrapper.get('.record-count').text()).toBe(String(count))
    expect(wrapper.findAll('.record-entry .related-card')).toHaveLength(Math.min(count, 3))
  })

  it('總數從四筆變為三筆後移除預覽提示，沒有描述也保留關聯卡片', async () => {
    const wrapper = render()
    await vi.waitFor(() => expect(wrapper.find('.preview-range').exists()).toBe(true))
    api.yields.mockResolvedValue({ content: records.slice(0, 3).map(record => ({ ...record, description: '' })), totalElements: 3 })
    await clients.at(-1)!.invalidateQueries({ queryKey: ['treasures'] })
    await vi.waitFor(() => expect(wrapper.get('.record-count').text()).toBe('3'))
    expect(wrapper.find('.preview-range').exists()).toBe(false)
    expect(wrapper.find('.yield-description').exists()).toBe(false)
    expect(wrapper.findAll('.related-card')).toHaveLength(3)
  })
})
