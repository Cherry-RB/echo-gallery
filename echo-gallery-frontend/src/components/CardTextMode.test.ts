import { mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { describe, expect, it } from 'vitest'
import CardTextMode from './CardTextMode.vue'
import type { CardDto } from '../types/card'

describe('CardTextMode', () => {
  it('全部卡片顯示來源、回流狀態與內容語意', () => {
    const card: CardDto = {
      id: '1', type: 'note', title: '測試卡片', tags: [], showContentPreview: false,
      reason: '這是留下卡片的原因',
      intervalDays: 10, nextShowAt: '2099-12-31T00:00:00Z', openCount: 0,
      likeCount: 0, snoozeCount: 0, isArchived: false, growthStatus: 'UNMARKED', createdAt: '', updatedAt: '',
    }
    const wrapper = mount(CardTextMode, {
      props: { data: card, boardType: 'all' },
      global: {
        plugins: [[VueQueryPlugin, { queryClient: new QueryClient() }]],
        stubs: {
          'el-card': { template: '<div><slot /></div>' },
          'el-button': { template: '<button><slot /></button>' },
          'el-dropdown': { template: '<div><slot /><slot name="dropdown" /></div>' },
          'el-dropdown-menu': { template: '<div><slot /></div>' },
          'el-dropdown-item': { template: '<button><slot /></button>' },
          'el-tag': { template: '<span><slot /></span>' },
          'el-tooltip': { template: '<span><slot /></span>' },
          'el-icon': { template: '<span><slot /></span>' },
        },
      },
    })
    expect(wrapper.text()).toContain('筆記')
    expect(wrapper.text()).toContain('我思我長')
    expect(wrapper.text()).toContain('這是留下卡片的原因')
    expect(wrapper.text()).toContain('每 10 天回流')
    expect(wrapper.text()).toContain('2099/12/31')
    expect(wrapper.text()).toContain('加入議題')
    expect(wrapper.text()).toContain('暫停回流')
  })

  it('今日回流直接顯示稍後再看', () => {
    const card: CardDto = {
      id: '2', type: 'link', title: '今日卡片', url: 'https://example.com/post', tags: [], showContentPreview: false,
      intervalDays: 7, nextShowAt: '2026-09-05T00:00:00Z', openCount: 0,
      likeCount: 0, snoozeCount: 0, isArchived: false, growthStatus: 'UNMARKED', createdAt: '', updatedAt: '',
    }
    const wrapper = mount(CardTextMode, {
      props: { data: card, boardType: 'today' },
      global: {
        plugins: [[VueQueryPlugin, { queryClient: new QueryClient() }]],
        stubs: {
          'el-card': { template: '<div><slot /></div>' },
          'el-button': { template: '<button><slot /></button>' },
          'el-dropdown': { template: '<div><slot /><slot name="dropdown" /></div>' },
          'el-dropdown-menu': { template: '<div><slot /></div>' },
          'el-dropdown-item': { template: '<button><slot /></button>' },
          'el-tag': { template: '<span><slot /></span>' },
          'el-tooltip': { template: '<span><slot /></span>' },
          'el-icon': { template: '<span><slot /></span>' },
        },
      },
    })
    expect(wrapper.text()).toContain('example.com')
    expect(wrapper.text()).toContain('稍後再看')
    expect(wrapper.text()).toContain('本次已到期')
  })

  it('稍後再看看板顯示延後次數並提供調整入口', () => {
    const card: CardDto = {
      id: '3', type: 'note', title: '反覆延後的卡片', tags: [], showContentPreview: false,
      intervalDays: 14, nextShowAt: '2026-10-01T00:00:00Z', openCount: 0,
      likeCount: 0, snoozeCount: 12, isArchived: false, growthStatus: 'UNMARKED', createdAt: '', updatedAt: '',
    }
    const wrapper = mount(CardTextMode, {
      props: { data: card, boardType: 'snoozed' },
      global: {
        plugins: [[VueQueryPlugin, { queryClient: new QueryClient() }]],
        stubs: {
          'el-card': { template: '<div><slot /></div>' },
          'el-button': { template: '<button><slot /></button>' },
          'el-dropdown': { template: '<div><slot /><slot name="dropdown" /></div>' },
          'el-dropdown-menu': { template: '<div><slot /></div>' },
          'el-dropdown-item': { template: '<button><slot /></button>' },
          'el-tag': { template: '<span><slot /></span>' },
          'el-tooltip': { template: '<span><slot /></span>' },
          'el-icon': { template: '<span><slot /></span>' },
        },
      },
    })
    expect(wrapper.text()).toContain('已稍後再看 12 次')
    expect(wrapper.text()).toContain('調整回流')
  })
})
