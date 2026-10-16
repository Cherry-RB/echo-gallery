import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import TreasureSourceSummary from './TreasureSourceSummary.vue'

describe('來源摘要的共用呈現與操作', () => {
  it('卡片、實驗與多件奇珍使用相同摘要，移除操作只指向指定來源', async () => {
    const wrapper = mount(TreasureSourceSummary, { props: {
      card: { id: 1, title: '卡片名稱'.repeat(30) }, experiment: { id: 2, title: '實驗名稱' },
      treasures: [{ id: 3, title: '奇珍甲' }, { id: 4, title: '奇珍乙' }],
    }, global: { stubs: { 'el-button': { props: ['disabled'], emits: ['click'], template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>' } } } })
    expect(wrapper.findAll('.source-group')).toHaveLength(3)
    expect(wrapper.findAll('.selection-summary')).toHaveLength(4)
    expect(wrapper.findAll('.selection-summary.with-actions.without-label')).toHaveLength(4)
    expect(wrapper.get('.selection-summary strong').text()).toBe('卡片名稱'.repeat(30))
    expect(wrapper.findAll('.summary-label')).toHaveLength(0)
    await wrapper.get('[aria-label="移除來源奇珍：奇珍甲"]').trigger('click')
    expect(wrapper.emitted('remove')).toEqual([['treasure', 3]])
    expect(wrapper.text()).not.toContain('/ 20')
    expect(wrapper.get('[aria-label="來源奇珍"] .source-count').text()).toBe('2')
    expect(wrapper.get('[aria-label="來源奇珍"] .source-count').element.tagName).toBe('SPAN')
    expect(wrapper.get('[aria-label="來源奇珍"] .source-count').attributes('role')).toBeUndefined()
  })
})
