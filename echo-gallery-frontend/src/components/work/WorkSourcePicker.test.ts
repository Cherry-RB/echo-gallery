import { mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { describe, expect, it, vi } from 'vitest'
import WorkSourcePicker from './WorkSourcePicker.vue'
const api = vi.hoisted(() => ({ candidates: vi.fn(), addSources: vi.fn() }))
vi.mock('../../utils/api/workApi', () => ({ workApi: { ...api, experiments: vi.fn().mockResolvedValue([]) } }))
vi.mock('../../utils/api/cardApi', () => ({ cardApi: { getCard: vi.fn() } }))
vi.mock('vue-router', () => ({ useRouter: () => ({ beforeEach: () => vi.fn() }) }))
describe('參考素材跨範圍選取', () => {
  it('跨卡片分頁保留多選，提交失敗仍保留選取且可重試', async () => {
    const card = { id: null, cardId: 1, recordId: null, title: '卡片來源', text: '完整卡片', experimentId: null, experimentTitle: null, archived: true }
    const nextCard = { ...card, cardId: 8, title: '第二頁卡片', text: '第二張全文' }
    api.candidates.mockImplementation(async ({ page }) => ({ content: [page ? nextCard : card], totalElements: 21, totalPages: 2 }))
    api.addSources.mockRejectedValueOnce(new Error('離線')).mockResolvedValueOnce([])
    const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
    const wrapper = mount(WorkSourcePicker, { props: { modelValue: true, workId: 42 }, global: { plugins: [[VueQueryPlugin, { queryClient: client }]], stubs: {
      AppDialog: { props: ['modelValue'], template: '<section v-if="modelValue"><slot /><slot name="footer" :request-close="() => {}" /></section>' },
      WorkSourceReadingDialog: { props: ['source'], emits: ['update:modelValue'], template: '<section class="source-reader">{{ source.text }}<button @click="$emit(\'update:modelValue\', false)">返回選擇</button></section>' },
      'el-button': { emits: ['click'], props: ['disabled'], template: '<button :disabled="disabled" @click="$emit(\'click\', $event)"><slot /></button>' },
      'el-tabs': { name: 'SourceTabs', props: ['modelValue'], emits: ['update:modelValue'], template: '<div><slot /></div>' },
      'el-tab-pane': true,
      'el-checkbox': { props: ['modelValue'], emits: ['change'], template: '<input type="checkbox" :checked="modelValue" @change="$emit(\'change\')" />' },
      'el-input': true, 'el-select': true, 'el-option': true, 'el-pagination': { name: 'Pagination', props: ['currentPage'], emits: ['update:currentPage'], template: '<nav />' },
      'el-alert': { props: ['title'], template: '<p>{{ title }}<slot /></p>' },
    } } })
    try {
      await vi.waitFor(() => expect(wrapper.text()).toContain('卡片來源'))
      await wrapper.get('.picker-option').trigger('click')
      await wrapper.findAll('button').find(b => b.text() === '閱讀全文')!.trigger('click')
      expect(wrapper.get('.source-reader').text()).toContain('完整卡片')
      expect(wrapper.emitted('saved')).toBeUndefined()
      await wrapper.findAll('button').find(b => b.text() === '返回選擇')!.trigger('click')
      expect(wrapper.get('.picker-option').attributes('aria-pressed')).toBe('true')
      expect(wrapper.findComponent({ name: 'SourceTabs' }).exists()).toBe(false)
      wrapper.findComponent({ name: 'Pagination' }).vm.$emit('update:currentPage', 2)
      await vi.waitFor(() => expect(wrapper.text()).toContain('第二頁卡片'))
      await wrapper.get('.picker-option').trigger('click')
      expect(wrapper.text()).toContain('已選 2 筆')
      expect(wrapper.findAll('.selected-sources .selection-summary')).toHaveLength(2)
      await wrapper.findAll('button').find(b => b.text() === '加入 2 筆素材')!.trigger('click')
      await vi.waitFor(() => expect(wrapper.text()).toContain('選取內容已保留'))
      expect(wrapper.text()).toContain('已選 2 筆'); expect(wrapper.emitted('saved')).toBeUndefined()
      await wrapper.findAll('button').find(b => b.text() === '加入 2 筆素材')!.trigger('click')
      await vi.waitFor(() => expect(wrapper.emitted('saved')).toBeTruthy())
      expect(api.addSources.mock.calls[1]![1]).toEqual([card, nextCard])
      expect(wrapper.emitted('update:modelValue')).toEqual([[false]])
    } finally { wrapper.unmount(); client.clear() }
  })
})
