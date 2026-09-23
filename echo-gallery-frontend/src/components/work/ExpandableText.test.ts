import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { describe, expect, it } from 'vitest'
import ExpandableText from '../ExpandableText.vue'

describe('ExpandableText', () => {
  it('只有內容超出預覽高度時才提供展開與收起', async () => {
    const wrapper = mount(ExpandableText, {
      props: {
        content: '一段超出預覽範圍的議題內容',
        lines: 2,
      },
    })
    const content = wrapper.get('.expandable-content').element
    Object.defineProperty(content, 'scrollHeight', { configurable: true, value: 120 })
    Object.defineProperty(content, 'clientHeight', { configurable: true, value: 48 })
    await nextTick()
    window.dispatchEvent(new Event('resize'))
    await nextTick()

    const button = wrapper.get<HTMLButtonElement>('.expand-button')
    expect(button.text()).toBe('展開全文')
    expect(button.attributes('aria-expanded')).toBe('false')

    await button.trigger('click')
    expect(button.text()).toBe('收起')
    expect(button.attributes('aria-expanded')).toBe('true')
    expect(wrapper.get('.expandable-content').classes()).toContain('expanded')
  })

  it('短內容不顯示展開操作', async () => {
    const wrapper = mount(ExpandableText, {
      props: { content: '短內容' },
    })
    const content = wrapper.get('.expandable-content').element
    Object.defineProperty(content, 'scrollHeight', { configurable: true, value: 40 })
    Object.defineProperty(content, 'clientHeight', { configurable: true, value: 40 })
    await nextTick()
    window.dispatchEvent(new Event('resize'))
    await nextTick()

    expect(wrapper.find('.expand-button').exists()).toBe(false)
  })
})
