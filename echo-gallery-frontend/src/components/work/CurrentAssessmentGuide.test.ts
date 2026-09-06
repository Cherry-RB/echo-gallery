import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import CurrentAssessmentGuide from './CurrentAssessmentGuide.vue'

describe('CurrentAssessmentGuide', () => {
  it('由使用者主動展開並套用研判框架', async () => {
    const wrapper = mount(CurrentAssessmentGuide, {
      props: { modelValue: '' },
    })

    expect(wrapper.text()).not.toContain('目前局勢與新訊號')

    await wrapper.get('.guide-trigger').trigger('click')
    expect(wrapper.text()).toContain('不必逐項回答')

    await wrapper.get('.apply-template-button').trigger('click')
    expect(wrapper.emitted('update:modelValue')?.[0]?.[0]).toContain('目前局勢：')
    expect(wrapper.emitted('update:modelValue')?.[0]?.[0]).toContain('限制、風險與退路：')
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
