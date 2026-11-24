import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus, { ElDropdown, ElSelect } from 'element-plus'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import Directory from './ExperimentTopicDirectory.vue'
import List from './ExperimentRecordList.vue'
import Reader from './ExperimentRecordReader.vue'
import Workspace from './ExperimentRecordWorkspace.vue'
import type { ExperimentRecordDto, ExperimentTopicDto } from '../../types/experiment'

const api = vi.hoisted(() => ({
  getTopics: vi.fn(), getRecordTopics: vi.fn(), getRecordReferences: vi.fn(),
  setRecordTopics: vi.fn(), createRecordReference: vi.fn(),
  getRecords: vi.fn(), getPinnedPractices: vi.fn(), getRecentPractices: vi.fn(), createRecord: vi.fn(), updateRecord: vi.fn(),
}))
vi.mock('../../utils/api/experimentApi', () => ({ experimentApi: api }))
const topics: ExperimentTopicDto[] = [
  { id: 1, name: '練習節奏', pinnedSlot: 2, displayOrder: 1, createdAt: '' },
  { id: 2, name: '開始阻力', pinnedSlot: 1, displayOrder: 2, createdAt: '' },
  { id: 3, name: '速寫素材', pinnedSlot: null, displayOrder: 3, createdAt: '' },
]
const record: ExperimentRecordDto = {
  id: 10, title: '今天的練習', content: '完整內容'.repeat(100), practiceText: '先畫五分鐘',
  recordedOn: '2026-11-21', understandingNode: true, methodCandidate: false, highlight: 'GENERAL',
  version: 0, createdAt: '2026-11-21T01:00:00Z', updatedAt: '2026-11-21T01:00:00Z', topics: [topics[0]!],
}
const wrappers: Array<{ unmount: () => void }> = []
const global = { plugins: [ElementPlus], stubs: {
  CardPickerDialog: true,
  RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' },
  AppDialog: {
    props: ['modelValue', 'title', 'busy', 'draft'], emits: ['update:modelValue'],
    template: '<section v-if="modelValue" :data-dialog="title"><slot /><footer><slot name="footer" :request-close="() => $emit(\'update:modelValue\', false)" /></footer></section>',
  },
} }
beforeEach(() => {
  localStorage.clear()
  api.getTopics.mockResolvedValue(topics)
  api.getRecordTopics.mockResolvedValue([topics[0]])
  api.getRecordReferences.mockResolvedValue([])
  api.setRecordTopics.mockResolvedValue([topics[1]])
  api.getRecords.mockResolvedValue({ content: [record], page: 0, size: 20, totalElements: 1, totalPages: 1 })
  api.getPinnedPractices.mockResolvedValue([])
  api.getRecentPractices.mockResolvedValue([])
  api.createRecord.mockResolvedValue({ ...record, id: 11 })
})
afterEach(() => { wrappers.splice(0).forEach(wrapper => wrapper.unmount()); vi.restoreAllMocks() })

describe('主題目錄與紀錄索引', () => {
  it('篩選浮層在多選主題時保持開啟，套用後才更新結果', async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const wrapper = mount(Workspace, {
      props: { experimentId: 8 },
      global: {
        plugins: [ElementPlus, [VueQueryPlugin, { queryClient }]],
        stubs: { ExperimentRecordReader: true, ExperimentRecordEditor: true, ExperimentTopicManagerDialog: true },
      },
    })
    wrappers.push(wrapper)
    await flushPromises()
    await wrapper.findAll('button').find(item => item.text() === '篩選')!.trigger('click')
    expect(wrapper.find('.filter-popover').exists()).toBe(true)
    const topicSelect = wrapper.findAllComponents(ElSelect).find(item => item.props('placeholder') === '選擇主題')!
    topicSelect.vm.$emit('update:modelValue', [1])
    await flushPromises()
    expect(wrapper.find('.filter-popover').exists()).toBe(true)
    await wrapper.findAll('button').find(item => item.text() === '套用條件')!.trigger('click')
    await flushPromises()
    expect(wrapper.find('.filter-popover').isVisible()).toBe(false)
    expect(wrapper.get('.filter-summary').text()).toContain('練習節奏')
    expect(api.getRecords).toHaveBeenLastCalledWith(8, 0, 20, expect.objectContaining({ topicIds: [1] }))
  })

  it('釘選組內沿用保存排序，搜尋主題不會提前套用紀錄篩選', async () => {
    const wrapper = mount(Directory, { props: { topics }, global }); wrappers.push(wrapper)
    expect(wrapper.findAll('.directory-group[aria-label="釘選主題"] .topic-link').map(item => item.text())).toEqual(['練習節奏', '開始阻力'])
    expect(wrapper.get('[aria-label="釘選主題"]').element.closest('.directory-scroll')).toBeNull()
    expect(wrapper.findAll('.directory-scroll .topic-link').map(item => item.text())).toEqual(['速寫素材'])
    await wrapper.get('input').setValue('素材')
    expect(wrapper.findAll('.directory-group .topic-link').map(item => item.text())).toEqual(['速寫素材'])
    expect(wrapper.emitted('select')).toBeUndefined()
    await wrapper.get('.directory-group .topic-link').trigger('click')
    expect(wrapper.emitted('select')).toEqual([[3]])
    await wrapper.get('button[aria-label="管理主題"]').trigger('click')
    expect(wrapper.emitted('manage')).toHaveLength(1)
  })

  it('多主題條件不誤標為全部，無結果搜尋仍能選取未分類', async () => {
    const wrapper = mount(Directory, { props: { topics, selectedIds: [1, 2] }, global }); wrappers.push(wrapper)
    expect(wrapper.find('.directory-shortcuts .active').exists()).toBe(false)
    await wrapper.get('input').setValue('找不到的主題')
    expect(wrapper.text()).toContain('沒有符合名稱的主題')
    await wrapper.findAll('.directory-shortcuts button')[1]!.trigger('click')
    expect(wrapper.emitted('unclassified')).toHaveLength(1)
  })

  it('索引列保留全文與操作隔離，鍵盤可開啟同一筆紀錄', async () => {
    const wrapper = mount(List, { props: { records: [record] }, global }); wrappers.push(wrapper)
    expect(wrapper.get('.index-excerpt').text()).toBe(record.content)
    expect(wrapper.get('time').text()).toBe(record.recordedOn)
    await wrapper.get('.index-actions button').trigger('click')
    expect(wrapper.emitted('read')).toBeUndefined()
    await wrapper.get('.record-index-row').trigger('keydown.space')
    expect(wrapper.emitted('read')).toEqual([[record]])
  })

  it('再記一筆以新增草稿預填做法，不帶入原紀錄的其他資料', async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const editor = {
      name: 'ExperimentRecordEditor',
      props: ['modelValue', 'record', 'initialPracticeText'],
      emits: ['save', 'update:modelValue'],
      template: '<div />',
    }
    const card = { name: 'ExperimentRecordCard', props: ['record'], emits: ['repeat'], template: '<button @click="$emit(\'repeat\', record)">再記一筆</button>' }
    const wrapper = mount(Workspace, {
      props: { experimentId: 8 },
      global: { plugins: [ElementPlus, [VueQueryPlugin, { queryClient }]], stubs: { ExperimentRecordReader: true, ExperimentRecordEditor: editor, ExperimentRecordCard: card, ExperimentTopicManagerDialog: true } },
    })
    wrappers.push(wrapper)
    await flushPromises()
    await wrapper.findAll('button').find(button => button.text() === '再記一筆')!.trigger('click')
    const editorWrapper = wrapper.getComponent({ name: 'ExperimentRecordEditor' })
    expect(editorWrapper.props('record')).toBeNull()
    expect(editorWrapper.props('initialPracticeText')).toBe('先畫五分鐘')
    editorWrapper.vm.$emit('save', { title: null, content: null, practiceText: '先畫五分鐘', recordedOn: '2026-11-24', understandingNode: false, methodCandidate: false, highlight: 'GENERAL', topicIds: [] })
    await flushPromises()
    expect(api.createRecord).toHaveBeenCalledWith(8, expect.objectContaining({ practiceText: '先畫五分鐘', requestId: expect.any(String) }))
    expect(api.updateRecord).not.toHaveBeenCalled()
  })
})

describe('閱讀中的主題與參考資料', () => {
  it('閱讀預設不顯示輸入表單；主題儲存後通知集合更新', async () => {
    const wrapper = mount(Reader, { props: { modelValue: true, record, experimentId: 8 }, global }); wrappers.push(wrapper)
    await flushPromises()
    expect(wrapper.find('input').exists()).toBe(false)
    expect(wrapper.get('.record-body p').text()).toBe(record.content)
    await wrapper.findAll('button').find(item => item.text() === '編輯主題')!.trigger('click')
    const picker = wrapper.get('[data-dialog="設定主題"]')
    await picker.findAll('input[type="checkbox"]')[0]!.setValue(false)
    await picker.findAll('input[type="checkbox"]')[1]!.setValue(true)
    await picker.findAll('button').find(item => item.text() === '儲存')!.trigger('click')
    await flushPromises()
    expect(api.setRecordTopics).toHaveBeenCalledWith(8, 10, [2])
    expect(wrapper.emitted('changed')).toHaveLength(1)
    expect(wrapper.get('.reader-topic-tags').text()).toBe('開始阻力')
  })

  it('加入連結失敗保留輸入，允許重試', async () => {
    api.createRecordReference.mockRejectedValueOnce(new Error('儲存失敗'))
    const wrapper = mount(Reader, { props: { modelValue: true, record, experimentId: 8 }, global }); wrappers.push(wrapper)
    await flushPromises()
    wrapper.getComponent(ElDropdown).vm.$emit('command', 'url')
    await flushPromises()
    await wrapper.get('input[aria-label="參考網址"]').setValue('https://example.com')
    const dialog = wrapper.get('[data-dialog="加入參考連結"]')
    await dialog.findAll('button').find(item => item.text() === '加入參考')!.trigger('click')
    await flushPromises()
    expect(wrapper.get('input[aria-label="參考網址"]').element).toHaveProperty('value', 'https://example.com')
    expect(wrapper.text()).toContain('無法新增參考連結')
    api.createRecordReference.mockResolvedValueOnce({ id: 1, kind: 'URL', url: 'https://example.com', label: null })
    await dialog.findAll('button').find(item => item.text() === '加入參考')!.trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-dialog="加入參考連結"]').exists()).toBe(false)
    expect(wrapper.get('.reference-list a').attributes('href')).toBe('https://example.com')
  })

  it('收藏卡片參考使用卡片詳情的單數路由', async () => {
    api.getRecordReferences.mockResolvedValueOnce([{ id: 4, kind: 'CARD', cardId: 12, title: '自動生成測試卡片標題 #1010' }])
    const wrapper = mount(Reader, { props: { modelValue: true, record, experimentId: 8 }, global }); wrappers.push(wrapper)
    await flushPromises()
    expect(wrapper.get('.reference-list a').attributes('href')).toBe('/card/12')
  })

  it('加入收藏卡片時使用目前實驗 ID，且無效 ID 不送出請求', async () => {
    const cardPicker = {
      name: 'CardPickerDialog',
      emits: ['confirm'],
      template: '<button type="button" @click="$emit(\'confirm\', { id: \'12\' })">選擇收藏卡片</button>',
    }
    api.createRecordReference.mockResolvedValue({ id: 4, kind: 'CARD', cardId: 12, title: '參考卡片' })
    const wrapper = mount(Reader, { props: { modelValue: true, record, experimentId: 8 }, global: { ...global, stubs: { ...global.stubs, CardPickerDialog: cardPicker } } })
    wrappers.push(wrapper)
    await flushPromises()
    wrapper.getComponent({ name: 'CardPickerDialog' }).vm.$emit('confirm', { id: '12' })
    await flushPromises()
    expect(api.createRecordReference).toHaveBeenCalledWith(8, 10, { kind: 'CARD', cardId: 12 })

    const invalidWrapper = mount(Reader, { props: { modelValue: true, record, experimentId: Number.NaN }, global: { ...global, stubs: { ...global.stubs, CardPickerDialog: cardPicker } } })
    wrappers.push(invalidWrapper)
    await flushPromises()
    invalidWrapper.getComponent({ name: 'CardPickerDialog' }).vm.$emit('confirm', { id: '12' })
    await flushPromises()
    expect(api.createRecordReference).toHaveBeenCalledTimes(1)
  })
})
