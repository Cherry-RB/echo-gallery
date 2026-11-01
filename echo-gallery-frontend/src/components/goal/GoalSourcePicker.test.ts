import { shallowMount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import GoalSourcePicker from './GoalSourcePicker.vue'
import { goalApi } from '../../utils/api/goalApi'
vi.mock('../../utils/api/goalApi', () => ({ goalApi: { options: vi.fn(), preview: vi.fn() } }))
const render = () => shallowMount(GoalSourcePicker, { props: { sources: [], allTags: false, metadata: {} }, global: { plugins: [ElementPlus], renderStubDefaultSlot: true, stubs: {
  PickerOption: false, PickerWorkspace: false, SelectionSummary: false, ElButton: { emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' },
} } })
describe('來源草稿與預覽', () => {
  beforeEach(() => { vi.resetAllMocks(); vi.mocked(goalApi.options).mockResolvedValue([{ kind: 'TAG', id: 1, title: '寫作' }]) })
  it('未填目標名稱仍可預覽來源，晚到的舊預覽不能覆蓋新選取', async () => {
    let resolve!: (value: { cardCount: number }) => void
    vi.mocked(goalApi.preview).mockImplementation(() => new Promise(done => { resolve = done }))
    const wrapper = render(); await flushPromises()
    await wrapper.findAll('button').find(button => button.text() === '計算卡片數')!.trigger('click')
    expect(goalApi.preview).toHaveBeenCalledWith({ sources: [], allTags: false })
    await wrapper.get('.picker-option').trigger('click')
    resolve({ cardCount: 99 }); await flushPromises()
    expect(wrapper.text()).not.toContain('99 張')
    wrapper.vm.confirm()
    expect(wrapper.emitted('confirm')?.[0]?.[0]).toMatchObject({ sources: [{ kind: 'TAG', id: 1, excluded: false }] })
    wrapper.unmount()
  })
  it('計算包含零張的結果，修改來源後舊數字失效，重新計算使用新條件', async () => {
    vi.mocked(goalApi.preview).mockResolvedValueOnce({ cardCount: 0 }).mockResolvedValueOnce({ cardCount: 27 })
    const wrapper = render(); await flushPromises()
    await wrapper.findAll('button').find(button => button.text() === '計算卡片數')!.trigger('click'); await flushPromises()
    expect(wrapper.get('.source-preview').text()).toContain('符合目前設定：0 張')
    await wrapper.get('.picker-option').trigger('click')
    expect(wrapper.get('.source-preview').text()).toContain('待重新計算')
    expect(wrapper.get('.source-preview').text()).not.toContain('符合目前設定：0 張')
    await wrapper.findAll('button').find(button => button.text() === '重新計算')!.trigger('click'); await flushPromises()
    expect(goalApi.preview).toHaveBeenLastCalledWith({ sources: [{ kind: 'TAG', id: 1, excluded: false }], allTags: false })
    expect(wrapper.get('.source-preview').text()).toContain('符合目前設定：27 張')
    wrapper.unmount()
  })
  it('實驗範圍以納入勾選呈現，取消只建立排除且保留獨立來源', async () => {
    vi.mocked(goalApi.options).mockImplementation(async kind => kind === 'THREAD' ? [
      { kind: 'THREAD', id: 31, title: '獨立追蹤', parentId: 3, parentTitle: '寫作實驗' },
      { kind: 'THREAD', id: 32, title: '原本排除', parentId: 3, parentTitle: '寫作實驗' },
      { kind: 'THREAD', id: 33, title: '原本納入', parentId: 3, parentTitle: '寫作實驗' },
    ] : [])
    const wrapper = shallowMount(GoalSourcePicker, { props: { sources: [
      { kind: 'EXPERIMENT', id: 3, excluded: false }, { kind: 'THREAD', id: 31, excluded: false }, { kind: 'THREAD', id: 32, excluded: true },
    ], allTags: false, metadata: {
      'EXPERIMENT:3': { kind: 'EXPERIMENT', id: 3, title: '寫作實驗' },
      'THREAD:32': { kind: 'THREAD', id: 32, title: '原本排除', parentId: 3 },
    } }, global: { plugins: [ElementPlus], renderStubDefaultSlot: true, stubs: {
      PickerOption: false, PickerWorkspace: false, SelectionSummary: false,
      ElButton: { emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' },
    } } })
    await flushPromises()
    await wrapper.findAll('button').find(button => button.text() === '調整追蹤範圍')!.trigger('click'); await flushPromises()
    expect(goalApi.options).toHaveBeenLastCalledWith('THREAD', '', 0, 3)
    const choices = wrapper.findAll('.picker-option')
    expect(choices[0]!.attributes('disabled')).toBeDefined()
    expect(choices[1]!.attributes('aria-pressed')).toBe('false')
    expect(choices[2]!.attributes('aria-pressed')).toBe('true')
    await choices[1]!.trigger('click'); await choices[2]!.trigger('click')
    wrapper.vm.confirm()
    expect(wrapper.emitted('confirm')?.[0]?.[0]).toMatchObject({ sources: [
      { kind: 'EXPERIMENT', id: 3, excluded: false }, { kind: 'THREAD', id: 31, excluded: false }, { kind: 'THREAD', id: 33, excluded: true },
    ] })
    wrapper.unmount()
  })
})
