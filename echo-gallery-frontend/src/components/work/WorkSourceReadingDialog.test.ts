import { mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { afterEach, describe, expect, it, vi } from 'vitest'
import WorkSourceReadingDialog from './WorkSourceReadingDialog.vue'
import type { WorkSource } from '../../types/work'

const api = vi.hoisted(() => ({ getCard: vi.fn() }))
vi.mock('../../utils/api/cardApi', () => ({ cardApi: api }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))
const source: WorkSource = { id: 1, cardId: null, recordId: 2, title: '2026-10-22', text: '只有發現\n\n第二段', tryText: null, discovery: '只有發現\n\n第二段', experimentId: 3, experimentTitle: '寫作', archived: false }
const clients: QueryClient[] = [], wrappers: ReturnType<typeof mount>[] = []
function render(value: WorkSource) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } }); clients.push(client)
  const wrapper = mount(WorkSourceReadingDialog, { props: { modelValue: true, source: value }, global: {
    plugins: [[VueQueryPlugin, { queryClient: client }]], stubs: {
      AppDialog: { template: '<section><slot /><slot name="footer" :request-close="() => {}" /></section>' },
      'el-button': { props: ['disabled'], template: '<button :disabled="disabled"><slot /></button>' },
      'el-tag': true,
    },
  } }); wrappers.push(wrapper); return wrapper
}
afterEach(() => { wrappers.splice(0).forEach(w => w.unmount()); clients.splice(0).forEach(c => c.clear()); vi.clearAllMocks() })

describe('共用素材原文閱讀', () => {
  it('只有發現時保留原欄位，並可直接複製，不等待卡片查詢', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    const original = Object.getOwnPropertyDescriptor(navigator, 'clipboard')
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText } })
    try {
      const wrapper = render(source)
      expect(wrapper.get('section[aria-label="試法"]').text()).toContain('沒有填寫試法')
      expect(wrapper.get('section[aria-label="發現"]').text()).toContain(source.discovery)
      const copy = wrapper.findAll('button').find(b => b.text() === '複製內容')!
      expect(copy.attributes('disabled')).toBeUndefined()
      await copy.trigger('click')
      expect(writeText).toHaveBeenCalledWith(source.text)
      expect(api.getCard).not.toHaveBeenCalled()
    } finally {
      if (original) Object.defineProperty(navigator, 'clipboard', original)
      else Reflect.deleteProperty(navigator, 'clipboard')
    }
  })
  it('試法中的空行不會移到發現欄', () => {
    const wrapper = render({ ...source, tryText: '試法第一段\n\n第二段', discovery: '真正發現' })
    expect(wrapper.get('section[aria-label="試法"]').text()).toContain('試法第一段\n\n第二段')
    expect(wrapper.get('section[aria-label="發現"]').text()).toContain('真正發現')
    expect(wrapper.get('section[aria-label="發現"]').text()).not.toContain('第二段')
  })
  it('卡片尚未載入時仍停用複製', () => {
    api.getCard.mockReturnValueOnce(new Promise(() => {}))
    const wrapper = render({ ...source, cardId: 7, recordId: null })
    expect(wrapper.findAll('button').find(b => b.text() === '複製內容')!.attributes('disabled')).toBeDefined()
  })
})
