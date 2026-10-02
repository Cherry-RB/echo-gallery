import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import CurrentAssessmentGuide from './CurrentAssessmentGuide.vue'

describe('CurrentAssessmentGuide', () => {
  it('由使用者主動展開並套用補充研判框架', async () => {
    const wrapper = mount(CurrentAssessmentGuide, {
      props: { modelValue: '' },
    })

    expect(wrapper.text()).not.toContain('重要例外與反例')

    await wrapper.get('.guide-trigger').trigger('click')
    expect(wrapper.text()).toContain('結構欄位')

    await wrapper.get('.apply-template-button').trigger('click')
    expect(wrapper.emitted('update:modelValue')?.[0]?.[0]).toContain('尚未放入結構欄位的補充：')
    expect(wrapper.emitted('update:modelValue')?.[0]?.[0]).toContain('例外或反例：')
  })

  it('已有內容時不覆蓋使用者文字', async () => {
    const wrapper = mount(CurrentAssessmentGuide, {
      props: { modelValue: '既有研判' },
    })

    await wrapper.get('.guide-trigger').trigger('click')

    expect(wrapper.get<HTMLButtonElement>('.apply-template-button').element.disabled).toBe(true)
    expect(wrapper.text()).toContain('避免覆蓋')
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })
})
