import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus, { ElSelect } from 'element-plus'
import { afterEach, describe, expect, it } from 'vitest'
import Card from './ExperimentRecordCard.vue'
import Reader from './ExperimentRecordReader.vue'
import Editor from './ExperimentRecordEditor.vue'
import AppDialog from '../AppDialog.vue'
import type { ExperimentRecordDto } from '../../types/experiment'

const record: ExperimentRecordDto = {
  id: 1, title: '每日練習', content: '今天畫了十分鐘。', practiceText: '先畫五分鐘。',
  recordedOn: '2026-11-21', understandingNode: true, methodCandidate: false, highlight: 'GAIN',
  version: 0, createdAt: '2026-11-21T01:00:00Z', updatedAt: '2026-11-21T02:00:00Z',
  topics: [],
}
const wrappers: Array<{ unmount: () => void }> = []
afterEach(() => wrappers.splice(0).forEach(wrapper => wrapper.unmount()))

const dialogStub = {
  name: 'AppDialog', props: ['modelValue', 'title', 'draft', 'busy'], emits: ['opened', 'update:modelValue'],
  template: '<section v-if="modelValue"><h2>{{ title }}</h2><slot /><footer><slot name="footer" :request-close="() => $emit(\'update:modelValue\', false)" /></footer></section>',
}
const global = { plugins: [ElementPlus], stubs: { AppDialog: dialogStub, CardPickerDialog: true, RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' } } }

describe('探索紀錄的閱讀與輸入', () => {
  it('長摘要交由 CSS 依行數收束，開啟閱讀仍傳回完整紀錄', async () => {
    const longRecord = { ...record, content: '練習🙂'.repeat(60) }
    const wrapper = mount(Card, { props: { record: longRecord }, global })
    wrappers.push(wrapper)
    const preview = wrapper.get('.content').text()
    expect(preview).toBe(longRecord.content)
    expect(wrapper.get('.content').classes()).toContain('content')
    expect(wrapper.get('.record-footer time').text()).toBe(record.recordedOn)
    await wrapper.get('article').trigger('keydown', { key: 'Enter' })
    expect(wrapper.emitted('read')).toEqual([[longRecord]])
  })

  it('更多選單內的鍵盤與點擊不會開啟外層卡片', async () => {
    const wrapper = mount(Card, { props: { record }, global })
    wrappers.push(wrapper)
    const button = wrapper.get('button[aria-label="探索紀錄操作"]')
    await button.trigger('keydown', { key: 'Enter' })
    await button.trigger('keydown', { key: ' ' })
    await button.trigger('click')
    expect(wrapper.emitted('read')).toBeUndefined()
  })

  it('額外主題以數量標籤收束，避免擠壓日期', () => {
    const wrapper = mount(Card, {
      props: {
        record: {
          ...record,
          topics: [
            { id: 1, name: '寫作', pinnedSlot: null, displayOrder: 1, createdAt: '' },
            { id: 2, name: '素材', pinnedSlot: null, displayOrder: 2, createdAt: '' },
            { id: 3, name: '靈感', pinnedSlot: null, displayOrder: 3, createdAt: '' },
          ],
        },
      },
      global,
    })
    wrappers.push(wrapper)
    expect(wrapper.get('.record-tags').text()).toContain('寫作')
    expect(wrapper.get('.record-tags').text()).toContain('+2')
    expect(wrapper.get('.record-footer time').text()).toBe(record.recordedOn)
  })

  it.each([
    { title: '只有標題', content: null, practiceText: null },
    { title: null, content: '只有內容', practiceText: null },
    { title: null, content: null, practiceText: '只有做法' },
  ])('單一欄位紀錄可完整閱讀：$title $content $practiceText', async fields => {
    const wrapper = mount(Reader, { props: { modelValue: true, record: { ...record, ...fields } }, global })
    wrappers.push(wrapper)
    const contentSections = wrapper.findAll('.record-body, .record-practice')
    expect(contentSections).toHaveLength(Number(Boolean(fields.content)) + Number(Boolean(fields.practiceText)))
    expect(wrapper.get('.record-details').text()).toContain('2026-11-21')
    expect(wrapper.get('details').attributes('open')).toBeDefined()
    await wrapper.findAll('button').find(button => button.text() === '編輯紀錄')!.trigger('click')
    expect(wrapper.emitted('edit')).toHaveLength(1)
  })

  it.each([0, 1, 2])('第 %i 個主要欄位單獨有值即可保存，補充區切換保留草稿', async fieldIndex => {
    const wrapper = mount(Editor, { props: { modelValue: true }, global })
    wrappers.push(wrapper)
    await flushPromises()
    const save = () => wrapper.findAll('button').find(button => button.text() === '儲存紀錄')!
    expect(save().attributes('disabled')).toBeDefined()
    await wrapper.findAll('input.el-input__inner, textarea')[fieldIndex]!.setValue('新的紀錄')
    const details = wrapper.get('details')
    ;(details.element as HTMLDetailsElement).open = true
    await details.trigger('toggle')
    ;(details.element as HTMLDetailsElement).open = false
    await details.trigger('toggle')
    expect(save().attributes('disabled')).toBeUndefined()
    expect(wrapper.text()).not.toContain('選填')
    await save().trigger('click')
    const payload = wrapper.emitted('save')![0]![0] as Record<string, unknown>
    expect(payload[['title', 'content', 'practiceText'][fieldIndex]!]).toBe('新的紀錄')
  })

  it('開啟後聚焦內容，儲存中無法再次送出', async () => {
    const wrapper = mount(Editor, { props: { modelValue: true }, attachTo: document.body, global })
    wrappers.push(wrapper)
    await flushPromises()
    wrapper.getComponent(AppDialog).vm.$emit('opened')
    await flushPromises()
    expect(document.activeElement).toBe(wrapper.get('textarea').element)
    await wrapper.get('textarea').setValue('保留草稿')
    await wrapper.setProps({ saving: true })
    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('save')).toBeUndefined()
    expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('保留草稿')
  })

  it('編輯紀錄時可在同一份草稿調整主題', async () => {
    const topics = [
      { id: 1, name: '開始阻力', pinnedSlot: 1, displayOrder: 1, createdAt: '' },
      { id: 2, name: '練習節奏', pinnedSlot: null, displayOrder: 2, createdAt: '' },
    ]
    const wrapper = mount(Editor, { props: { modelValue: true, record: { ...record, topics: [topics[0]!] }, topicOptions: topics }, global })
    wrappers.push(wrapper)
    await flushPromises()
    const topicSelect = wrapper.findAllComponents(ElSelect).find(item => item.props('placeholder') === '選擇主題')!
    expect(topicSelect.props('modelValue')).toEqual([1])
    topicSelect.vm.$emit('update:modelValue', [2])
    await wrapper.findAll('button').find(button => button.text() === '儲存紀錄')!.trigger('click')
    expect((wrapper.emitted('save')![0]![0] as { topicIds: number[] }).topicIds).toEqual([2])
  })
})
