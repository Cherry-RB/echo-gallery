import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { describe, expect, it } from 'vitest'
import GoalMetricSettings from './GoalMetricSettings.vue'
describe('顯示數據建議', () => {
  it('新增來源不復勾已取消的指標，採用建議才更新且保留既有偏好', async () => {
    const wrapper = mount(GoalMetricSettings, { props: { modelValue: ['M20'], sources: [] }, global: { plugins: [ElementPlus] } })
    await wrapper.setProps({ sources: [{ kind: 'TAG', id: 1, excluded: false }] })
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    await wrapper.findAll('button').find(button => button.text() === '設定')!.trigger('click')
    await wrapper.findAll('button').find(button => button.text() === '採用建議')!.trigger('click')
    expect(wrapper.emitted('update:modelValue')?.[0]?.[0]).toEqual(['M20', 'M01'])
    expect(wrapper.findAll('input[type="checkbox"]')).toHaveLength(1)
    expect(wrapper.text()).toContain('這些設定目前尚未開放編輯，會照原設定保留')
    await wrapper.setProps({ modelValue: ['M20', 'M01'] })
    await wrapper.get('input[type="checkbox"]').setValue(false)
    expect(wrapper.emitted('update:modelValue')?.at(-1)?.[0]).toEqual(['M20'])
    wrapper.unmount()
  })
})
