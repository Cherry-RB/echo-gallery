import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import RecurrenceIntervalPicker from './RecurrenceIntervalPicker.vue'

describe('RecurrenceIntervalPicker', () => {
  const mountPicker = (modelValue = 30) => mount(RecurrenceIntervalPicker, {
    props: { modelValue, showPause: true },
    global: {
      stubs: {
        'el-input-number': {
          props: ['modelValue'],
          template: '<input :value="modelValue" />',
        },
        'el-button': { template: '<button><slot /></button>' },
      },
    },
  })

  it('以三欄選項提供固定週期與週期增量', async () => {
    const wrapper = mountPicker()
    expect(wrapper.text()).toContain('固定週期')
    expect(wrapper.text()).toContain('180 天')
    expect(wrapper.text()).toContain('+100 天')

    const addThirty = wrapper.findAll('button').find(button => button.text() === '+30 天')
    await addThirty?.trigger('click')
    expect(wrapper.emitted('select')).toContainEqual([60])
  })

  it('超過 365 天時停用對應增量', () => {
    const wrapper = mountPicker(300)
    const addHundred = wrapper.findAll('button').find(button => button.text() === '+100 天')
    expect(addHundred?.attributes('disabled')).toBeDefined()
  })
})
