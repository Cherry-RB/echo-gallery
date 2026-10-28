import { mount } from '@vue/test-utils'
import { EditorView } from '@codemirror/view'
import { afterEach, describe, expect, it, vi } from 'vitest'
import WorkMarkdownEditor from './WorkMarkdownSource.vue'

vi.mock('vue-router', () => ({ useRouter: () => ({ beforeEach: () => vi.fn() }) }))
const wrappers: ReturnType<typeof mount>[] = []
function render() {
  const wrapper = mount(WorkMarkdownEditor, { props: { modelValue: '正文', mode: 'write' }, global: { stubs: {
    AppDialog: { props: ['modelValue'], template: '<section v-if="modelValue"><slot /><slot name="footer" :request-close="() => {}" /></section>' },
    'el-button': { props: ['disabled'], template: '<button :disabled="disabled"><slot /></button>' },
    'el-tooltip': { template: '<span><slot /></span>' },
    'el-input': { props: ['modelValue'], emits: ['update:modelValue'], template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />' },
    'el-form': { template: '<form><slot /></form>' }, 'el-form-item': { props: ['error'], template: '<label><slot />{{ error }}</label>' },
  } } })
  wrappers.push(wrapper); return wrapper
}
afterEach(() => wrappers.splice(0).forEach(w => w.unmount()))
describe('Markdown 編輯核心', () => {
  it('選取文字套用格式，預覽切換保留編輯實例、選取與復原紀錄', async () => {
    const wrapper = render()
    const view = EditorView.findFromDOM(wrapper.get('.cm-editor').element as HTMLElement)!
    view.dispatch({ selection: { anchor: 0, head: 2 } })
    await wrapper.get('button[aria-label="粗體"]').trigger('click')
    expect(view.state.doc.toString()).toBe('**正文**')
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['**正文**'])
    await wrapper.setProps({ modelValue: '**正文**', mode: 'read' })
    expect(wrapper.get('.markdown-reading strong').text()).toBe('正文')
    await wrapper.setProps({ mode: 'split' })
    expect(EditorView.findFromDOM(wrapper.get('.cm-editor').element as HTMLElement)).toBe(view)
    expect(view.state.selection.main.from).toBe(2)
    await wrapper.get('button[aria-label="復原正文編輯"]').trigger('click')
    expect(view.state.doc.toString()).toBe('正文')
  })
  it('網址驗證通過才插入，閱讀或取消不會留下半段 Markdown', async () => {
    const wrapper = render()
    const view = EditorView.findFromDOM(wrapper.get('.cm-editor').element as HTMLElement)!
    await wrapper.get('button[aria-label="插入連結"]').trigger('click')
    await wrapper.findAll('input')[1]!.setValue('javascript:alert(1)')
    await wrapper.findAll('button').find(b => b.text() === '插入')!.trigger('click')
    expect(wrapper.text()).toContain('請輸入有效的 HTTP(S) 網址')
    expect(view.state.doc.toString()).toBe('正文')
    await wrapper.findAll('input')[0]!.setValue('網站')
    await wrapper.findAll('input')[1]!.setValue('https://example.com')
    await wrapper.findAll('button').find(b => b.text() === '插入')!.trigger('click')
    expect(view.state.doc.toString()).toBe('[網站](<https://example.com>)正文')
  })
})
