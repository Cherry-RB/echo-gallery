import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import SectionHeader from './SectionHeader.vue'
import RecordHistoryTrigger from './RecordHistoryTrigger.vue'

describe('標題與歷程計數的共用呈現', () => {
  it('標題將零筆、尚未取得與大數量分開呈現，保留既有插槽', async () => {
    const wrapper = mount(SectionHeader, { props: { title: '館藏' }, slots: { 'title-extra': '<span>補充資訊</span>', actions: '<button>管理</button>' } })
    try {
      expect(wrapper.find('.record-count').exists()).toBe(false)
      await wrapper.setProps({ count: 0 })
      const count = wrapper.get('.record-count')
      expect(count.text()).toBe('0')
      expect(count.element.tagName).toBe('SPAN')
      expect(count.attributes('role')).toBeUndefined()
      expect(count.attributes('tabindex')).toBeUndefined()
      expect(wrapper.get('h2').text()).toBe('館藏')
      expect(wrapper.get('.app-section-title-row').text()).toContain('補充資訊')
      expect(wrapper.get('.app-section-actions').text()).toBe('管理')
      await wrapper.setProps({ count: 10000 })
      expect(wrapper.get('.record-count').text()).toBe('10000')
      await wrapper.setProps({ count: null })
      expect(wrapper.find('.record-count').exists()).toBe(false)
    } finally { wrapper.unmount() }
  })

  it('歷程按鈕傳回原始事件，讓卡片內的 click.stop 能阻擋導頁', async () => {
    const openHistory = vi.fn(), openCard = vi.fn()
    const host = defineComponent({
      components: { RecordHistoryTrigger },
      setup: () => ({ openHistory, openCard }),
      template: '<article @click="openCard"><RecordHistoryTrigger label="探索紀錄" :count="12" count-only @click.stop="openHistory" /></article>',
    })
    const wrapper = mount(host)
    try {
      const button = wrapper.get('button')
      expect(button.attributes('type')).toBe('button')
      expect(button.attributes('aria-label')).toBe('查看 12 筆探索紀錄')
      expect(button.text()).toBe('12')
      await button.trigger('click')
      expect(openHistory).toHaveBeenCalledTimes(1)
      expect(openHistory.mock.calls[0]?.[0]).toBeInstanceOf(MouseEvent)
      expect(openCard).not.toHaveBeenCalled()
    } finally { wrapper.unmount() }
  })

  it('歷程總數未知時仍保留可辨識入口，不捏造零筆或留下空白按鈕', async () => {
    const wrapper = mount(RecordHistoryTrigger, { props: { label: '歷次更新', countOnly: true } })
    try {
      expect(wrapper.get('button').text()).toBe('歷次更新')
      expect(wrapper.get('button').attributes('aria-label')).toBe('查看歷次更新')
      expect(wrapper.find('.record-count').exists()).toBe(false)
      await wrapper.setProps({ count: 0 })
      expect(wrapper.get('.record-count').text()).toBe('0')
      expect(wrapper.get('button').attributes('aria-label')).toBe('查看 0 筆歷次更新')
    } finally { wrapper.unmount() }
  })
})
