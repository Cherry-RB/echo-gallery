import { reactive } from 'vue'
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import WorkInfoFields from './WorkInfoFields.vue'
import type { WorkInfo } from '../../types/work'

describe('共用作品資訊欄位', () => {
  it('切換編輯位置仍保留網址與其他草稿，並提示不會同步內容', async () => {
    const form = reactive<WorkInfo>({ title: '作品草稿', description: '尚未提交的說明', experimentId: 7, editorMode: 'INTERNAL', externalUrl: 'https://example.com/draft' })
    const wrapper = mount(WorkInfoFields, { props: { modelValue: form, originalMode: 'INTERNAL', experiments: [{ id: 7, title: '寫作', archived: false }] }, global: { stubs: {
      'el-form-item': { template: '<section><slot /></section>' },
      'el-input': { name: 'TextInput', props: ['modelValue'], emits: ['update:modelValue'], template: '<input :value="modelValue" />' },
      'el-radio-group': { name: 'ModeControl', props: ['modelValue'], emits: ['update:modelValue'], template: '<div><slot /></div>' },
      'el-radio': true, 'el-select': true, 'el-option': true,
    } } })
    try {
      const mode = wrapper.getComponent({ name: 'ModeControl' })
      mode.vm.$emit('update:modelValue', 'EXTERNAL')
      await wrapper.vm.$nextTick()
      expect(wrapper.text()).toContain('內外部內容不會同步')
      const url = wrapper.findAllComponents({ name: 'TextInput' }).find(input => input.props('modelValue') === form.externalUrl)!
      url.vm.$emit('update:modelValue', 'https://example.com/revised')
      mode.vm.$emit('update:modelValue', 'INTERNAL')
      await wrapper.vm.$nextTick()
      expect(wrapper.text()).not.toContain('內外部內容不會同步')
      mode.vm.$emit('update:modelValue', 'EXTERNAL')
      await wrapper.vm.$nextTick()
      expect(form.externalUrl).toBe('https://example.com/revised')
      expect(form.title).toBe('作品草稿')
      expect(form.description).toBe('尚未提交的說明')
      expect(form.experimentId).toBe(7)
    } finally { wrapper.unmount() }
  })
})
