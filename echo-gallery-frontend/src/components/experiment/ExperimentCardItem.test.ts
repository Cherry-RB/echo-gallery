import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { ExperimentCardDto } from '../../types/experiment'
import ExperimentCardItem from './ExperimentCardItem.vue'

const card: ExperimentCardDto = {
  cardId: 7,
  cardType: 'note',
  cardTitle: '畫畫的練習方式',
  cardReason: '希望找到容易開始的方法',
  cardSummary: '每天先畫十分鐘',
  cardTags: ['創作'],
  cardArchived: false,
  intervalDays: 10,
  nextShowAt: null,
  needsProcessing: false,
  stage: 'SEED',
  note: '可能適合拿來試驗',
  addedAt: '2026-09-26T00:00:00Z',
}

describe('ExperimentCardItem', () => {
  it('在實驗場直接顯示材料內容，並能選取比較', async () => {
    const wrapper = mount(ExperimentCardItem, {
      props: { card, stages: [{ value: 'SEED', title: '種子土壤' }], comparing: false },
      global: {
        stubs: {
          'el-tag': { template: '<span><slot /></span>' },
          'el-dropdown': { template: '<div><slot /></div>' },
          'el-dropdown-menu': true,
          'el-dropdown-item': true,
          'el-button': true,
        },
      },
    })

    expect(wrapper.text()).toContain('每天先畫十分鐘')
    expect(wrapper.text()).toContain('希望找到容易開始的方法')

    await wrapper.find('.compare-toggle').trigger('click')
    expect(wrapper.emitted('toggleCompare')).toEqual([[card]])
  })
})
