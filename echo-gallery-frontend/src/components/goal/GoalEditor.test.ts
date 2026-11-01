import { shallowMount, flushPromises } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import ElementPlus from 'element-plus'
import { ElMessageBox } from 'element-plus'
import { onBeforeRouteLeave } from 'vue-router'
import GoalEditor from './GoalEditor.vue'
import GoalSourcePicker from './GoalSourcePicker.vue'
import { goalApi } from '../../utils/api/goalApi'
import type { GoalView } from '../../types/goal'
vi.mock('vue-router', () => ({ onBeforeRouteLeave: vi.fn() }))
vi.mock('../../utils/api/goalApi', () => ({ goalApi: { save: vi.fn(), options: vi.fn(), sources: vi.fn() } }))
describe('目標編輯衝突保護', () => {
  it('只開啟來源但沒有變更時，路由離開不詢問放棄', async () => {
    vi.mocked(goalApi.options).mockResolvedValue([])
    const confirm = vi.spyOn(ElMessageBox, 'confirm').mockRejectedValue(new Error('沒有變更時不應詢問放棄'))
    const wrapper = shallowMount(GoalEditor, { props: { modelValue: false }, global: { plugins: [ElementPlus], renderStubDefaultSlot: true, stubs: { AppDialog: { template: '<div><slot /></div>' }, GoalSourcePicker: false } } })
    await wrapper.setProps({ modelValue: true }); await wrapper.get('.goal-source-button').trigger('click'); await flushPromises()
    const guard = vi.mocked(onBeforeRouteLeave).mock.calls.at(-1)![0] as () => Promise<boolean>
    expect(await guard()).toBe(true); expect(confirm).not.toHaveBeenCalled()
    wrapper.unmount(); confirm.mockRestore()
  })

  it('透過共用選擇器顯示候選與已選來源，選取只改草稿', async () => {
    vi.mocked(goalApi.options).mockResolvedValue([{ kind: 'TAG', id: 7, title: '人物塑造' }])
    vi.mocked(goalApi.save).mockClear()
    const wrapper = shallowMount(GoalEditor, {
      props: { modelValue: false },
      global: {
        plugins: [ElementPlus], renderStubDefaultSlot: true,
        stubs: {
          AppDialog: { template: '<div><slot /><slot name="footer" /></div>' },
          GoalSourcePicker: false, PickerOption: false, PickerWorkspace: false, SelectionSummary: false,
        },
      },
    })
    await wrapper.setProps({ modelValue: true }); await flushPromises()
    await wrapper.get('.goal-source-button').trigger('click'); await flushPromises()
    expect(wrapper.get('.picker-option').text()).toContain('人物塑造')
    await wrapper.get('.picker-option').trigger('click')
    expect(wrapper.get('.selected-summary').text()).toContain('人物塑造')
    expect(wrapper.get('.picker-option').attributes('aria-pressed')).toBe('true')
    expect(goalApi.save).not.toHaveBeenCalled()
    wrapper.findComponent(GoalSourcePicker).vm.confirm(); await flushPromises()
    expect(wrapper.get('.goal-help').text()).toContain('標籤 1 項')
    wrapper.unmount()
  })

  it('取消來源子流程不改父草稿，確認來源也不提前保存', async () => {
    vi.mocked(goalApi.options).mockResolvedValue([{ kind: 'TAG', id: 7, title: '人物塑造' }])
    vi.mocked(goalApi.save).mockClear()
    const wrapper = shallowMount(GoalEditor, { props: { modelValue: false, initialSection: 'sources' }, global: { plugins: [ElementPlus], renderStubDefaultSlot: true, stubs: {
      AppDialog: { template: '<div><slot /><slot name="footer" /></div>' }, GoalSourcePicker: false, PickerOption: false, PickerWorkspace: false, SelectionSummary: false,
      ElButton: { emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' },
    } } })
    await wrapper.setProps({ modelValue: true }); await flushPromises()
    expect(wrapper.findComponent(GoalSourcePicker).exists()).toBe(true)
    await wrapper.get('.picker-option').trigger('click')
    await wrapper.findAll('button').find(button => button.text() === '放棄並返回')!.trigger('click')
    expect(wrapper.get('.goal-help').text()).toContain('未設定站內追蹤')
    await wrapper.get('.goal-source-button').trigger('click'); await flushPromises()
    expect(wrapper.get('.picker-option').attributes('aria-pressed')).toBe('false')
    await wrapper.get('.picker-option').trigger('click')
    await wrapper.findAll('button').find(button => button.text() === '套用並返回')!.trigger('click'); await flushPromises()
    expect(wrapper.get('.goal-help').text()).toContain('標籤 1 項')
    expect(goalApi.save).not.toHaveBeenCalled(); wrapper.unmount()
  })

  it('背景取得新版時，仍以開啟表單時的版本保存，避免覆蓋另一裝置', async () => {
    vi.mocked(goalApi.options).mockResolvedValue([]); vi.mocked(goalApi.sources).mockResolvedValue([])
    const original: GoalView = { id: 1, version: 1, cardCount: 0, effectiveStatus: 'ACTIVE', trackingStartedAt: '', closedAt: null, definition: { name: '原期待', startsOn: '2026-11-01', endsOn: '2026-12-01', expectation: '', motivation: '', imageUrl: '', status: 'ACTIVE', archived: false, pinned: true, pinOrder: 0, reviewDays: 7, remindersEnabled: true, allTags: false, sources: [], metrics: [] } }
    vi.mocked(goalApi.save).mockResolvedValue({ ...original, version: 2 })
    const wrapper = shallowMount(GoalEditor, { props: { modelValue: false, goal: original }, global: { plugins: [ElementPlus], renderStubDefaultSlot: true, stubs: { AppDialog: { template: '<div><slot /></div>' }, ElForm: { emits: ['submit'], template: '<form @submit.prevent="$emit(\'submit\', $event)"><slot /></form>' } } } })
    await wrapper.setProps({ modelValue: true }); await flushPromises()
    await wrapper.setProps({ goal: { ...original, version: 2, definition: { ...original.definition, name: '另一裝置的新期待' } } })
    await wrapper.get('form').trigger('submit'); await flushPromises()
    expect(goalApi.save).toHaveBeenCalledWith(1, expect.objectContaining({ name: '原期待' }), 1, '', expect.any(String))
    wrapper.unmount()
  })
})
