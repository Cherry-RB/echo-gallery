import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { describe, expect, it, vi } from 'vitest'
import GoalScopeDialog from './GoalScopeDialog.vue'
import { goalApi } from '../../utils/api/goalApi'
vi.mock('../../utils/api/goalApi', () => ({ goalApi: { list: vi.fn(), detail: vi.fn() } }))
describe('目標範圍草稿', () => {
  it('候選查詢與選取都不保存範圍，確認才提交獨立草稿', async () => {
    vi.mocked(goalApi.list).mockResolvedValue({ content: [], totalElements: 0, totalPages: 0 })
    const preference = { allActive: false, goalIds: [], includePaused: true, includeArchived: false, version: 2 }
    const wrapper = mount(GoalScopeDialog, { props: { modelValue: false, preference }, global: { plugins: [ElementPlus], stubs: {
      AppDialog: { template: '<div><slot /><slot name="footer" /></div>' },
    } } })
    await wrapper.setProps({ modelValue: true }); await flushPromises()
    expect(goalApi.list).toHaveBeenCalledTimes(1)
    await wrapper.get('input[aria-label="搜尋目標"]').setValue('寫作'); await flushPromises()
    expect(goalApi.list).toHaveBeenCalledTimes(1)
    const checks = wrapper.findAll('input[type="checkbox"]')
    await checks[checks.length - 1]!.setValue(true)
    expect(preference.includeArchived).toBe(false)
    expect(wrapper.emitted('confirm')).toBeUndefined()
    await wrapper.findAll('button').find(button => button.text() === '套用範圍')!.trigger('click')
    expect(wrapper.emitted('confirm')?.[0]?.[0]).toMatchObject({ includeArchived: true, version: 2 })
    wrapper.unmount()
  })
})
