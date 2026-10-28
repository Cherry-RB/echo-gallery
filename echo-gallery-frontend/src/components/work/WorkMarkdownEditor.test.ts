import { mount } from '@vue/test-utils'
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest'
import { TextSelection } from '@milkdown/kit/prose/state'
import type { EditorView } from '@milkdown/kit/prose/view'
import WorkMarkdownEditor from './WorkMarkdownEditor.vue'
import * as navigation from '../../utils/workEditorNavigation'

vi.mock('vue-router', () => ({ useRouter: () => ({ beforeEach: () => vi.fn() }) }))
const wrappers: ReturnType<typeof mount>[] = []
beforeAll(() => {
  // jsdom 不提供排版座標；實機鍵盤與游標可見性另行驗收。
  if (!Range.prototype.getClientRects) Range.prototype.getClientRects = () => [] as unknown as DOMRectList
  if (!Range.prototype.getBoundingClientRect) Range.prototype.getBoundingClientRect = () => new DOMRect()
})
async function render(value = '正文') {
  const wrapper = mount(WorkMarkdownEditor, { attachTo: document.body, props: { modelValue: value, mode: 'write' }, global: { stubs: {
    AppDialog: { props: ['modelValue'], template: '<section v-if="modelValue"><slot /><slot name="footer" :request-close="() => {}" /></section>' },
    'el-button': { props: ['disabled'], template: '<button :disabled="disabled"><slot /></button>' },
    'el-tooltip': { template: '<span><slot /></span>' },
    'el-input': { props: ['modelValue'], emits: ['update:modelValue'], template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />' },
    'el-form': { template: '<form><slot /></form>' }, 'el-form-item': { props: ['error'], template: '<label><slot />{{ error }}</label>' },
  } } })
  wrappers.push(wrapper)
  await vi.waitFor(() => expect((wrapper.vm as unknown as { getEditor(): EditorView }).getEditor()).toBeDefined())
  await wrapper.vm.$nextTick()
  const view = (wrapper.vm as unknown as { getEditor(): EditorView }).getEditor()
  return { wrapper, view }
}
afterEach(() => { wrappers.splice(0).forEach(w => { w.unmount(); w.element.remove() }); vi.restoreAllMocks() })

describe('作品可視化編輯', () => {
  it('載入既有排版不寫回正文，選取後套用粗體；切換閱讀仍保留選取與復原紀錄', async () => {
    const { wrapper, view } = await render()
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    view.dispatch(view.state.tr.setSelection(TextSelection.create(view.state.doc, 1, 3)))
    await wrapper.get('button[aria-label="粗體"]').trigger('click')
    expect(wrapper.get('.ProseMirror strong').text()).toBe('正文')
    const updated = wrapper.emitted('update:modelValue')!.at(-1)![0] as string
    expect(updated.trim()).toBe('**正文**')
    await wrapper.setProps({ modelValue: updated, mode: 'read' })
    expect(wrapper.get('.markdown-reading strong').text()).toBe('正文')
    await wrapper.setProps({ mode: 'write' })
    expect((wrapper.vm as unknown as { getEditor(): EditorView }).getEditor()).toBe(view)
    expect(view.state.selection.from).toBe(1)
    await wrapper.get('button[aria-label="復原正文編輯"]').trigger('click')
    expect(wrapper.emitted('update:modelValue')!.at(-1)).toEqual(['正文'])
  })

  it('大小標題、斜體與刪除線可視化呈現，輸出後重新載入仍保留', async () => {
    const { wrapper, view } = await render('要寫的段落')
    view.dispatch(view.state.tr.setSelection(TextSelection.create(view.state.doc, 1, 6)))
    await wrapper.get('select[aria-label="段落樣式"]').setValue('2')
    await wrapper.get('button[aria-label="斜體"]').trigger('click')
    await wrapper.get('button[aria-label="刪除線"]').trigger('click')
    expect(wrapper.get('.ProseMirror h2 em').text()).toBe('要寫的段落')
    expect(wrapper.get('.ProseMirror h2 del').text()).toBe('要寫的段落')
    const value = wrapper.emitted('update:modelValue')!.at(-1)![0] as string
    const reloaded = await render(value)
    expect(reloaded.wrapper.get('.ProseMirror h2').text()).toContain('要寫的段落')
    expect(reloaded.wrapper.find('.ProseMirror del').exists()).toBe(true)
    expect(reloaded.wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('勾選清單可切換完成狀態，表格與連結重開不遺失', async () => {
    const content = '- [ ] 看完這本書\n\n| 項目 | 狀態 |\n| --- | --- |\n| 閱讀 | 進行中 |\n\n[參考](https://example.com)'
    const { wrapper } = await render(content)
    expect(wrapper.find('.is-source').exists()).toBe(false)
    expect(wrapper.find('.ProseMirror table').exists()).toBe(true)
    await wrapper.get('.work-task-check').trigger('click')
    const value = wrapper.emitted('update:modelValue')!.at(-1)![0] as string
    expect(value).toContain('[x]')
    expect(value).toContain('https://example.com')
    const reloaded = await render(value)
    expect(reloaded.wrapper.get('.work-task-check').attributes('aria-checked')).toBe('true')
    expect(reloaded.wrapper.find('.ProseMirror table').exists()).toBe(true)
  })

  it('建立勾選清單不必輸入 Markdown 符號', async () => {
    const { wrapper } = await render('待辦')
    await wrapper.findAll('button').find(button => button.text() === '勾選清單')!.trigger('click')
    expect(wrapper.find('.work-task-check').exists()).toBe(true)
    expect(wrapper.emitted('update:modelValue')!.at(-1)![0]).toContain('[ ]')
  })

  it('標題收合只改顯示；大綱跳轉會展開目標，不另存正文', async () => {
    const { wrapper } = await render('# 第一節\n\n第一節內容\n\n## 子節\n\n子節內容\n\n# 第二節\n\n第二節內容')
    await wrapper.get('.work-heading-toggle').trigger('click')
    expect(wrapper.findAll('.work-folded-block').length).toBeGreaterThan(0)
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    await wrapper.findAll('button').find(button => button.text() === '大綱')!.trigger('click')
    await wrapper.findAll('.editor-outline button').find(button => button.text() === '子節')!.trigger('click')
    expect(wrapper.get('.ProseMirror h2').classes()).not.toContain('work-folded-block')
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('搜尋跨粗體邊界，跳轉不修改正文', async () => {
    const { wrapper, view } = await render('這是**一段**文字')
    await wrapper.findAll('button').find(button => button.text() === '搜尋')!.trigger('click')
    await wrapper.get('input[aria-label="搜尋正文"]').setValue('是一段文')
    expect(view.state.doc.textBetween(view.state.selection.from, view.state.selection.to)).toBe('是一段文')
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('工具選單保留文字選取，啟用搜尋後收合並繼續套用格式', async () => {
    const { wrapper, view } = await render('選取文字')
    view.dispatch(view.state.tr.setSelection(TextSelection.create(view.state.doc, 1, 5)))
    await wrapper.get('.utility-menu summary').trigger('click')
    expect(wrapper.get('.utility-menu').attributes('open')).toBeDefined()
    await wrapper.findAll('.utility-menu button').find(button => button.text() === '搜尋')!.trigger('click')
    expect(wrapper.get('.utility-menu').attributes('open')).toBeUndefined()
    expect(wrapper.find('input[aria-label="搜尋正文"]').exists()).toBe(true)
    expect(view.state.selection.from).toBe(1)
    expect(view.state.selection.to).toBe(5)
    await wrapper.get('button[aria-label="粗體"]').trigger('click')
    expect(wrapper.get('.ProseMirror strong').text()).toBe('選取文字')
  })

  it('中文組字期間不寫出半成品，完成組字才同步正文', async () => {
    const { wrapper, view } = await render()
    const composing = vi.spyOn(view, 'composing', 'get').mockReturnValue(true)
    view.dispatch(view.state.tr.insertText('中文', 3))
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    composing.mockReturnValue(false)
    await wrapper.get('.ProseMirror').trigger('compositionend')
    await vi.waitFor(() => expect(wrapper.emitted('update:modelValue')!.at(-1)![0]).toContain('正文中文'))
  })

  it('不相容格式退回原始碼，載入與離開都不覆寫原文', async () => {
    vi.spyOn(navigation, 'compatibleWorkMarkdown').mockReturnValue(false)
    const content = '原始排版\n\n保留內容'
    const { wrapper } = await render(content)
    expect(wrapper.text()).toContain('需要保留原樣的格式')
    await vi.waitFor(() => expect(wrapper.find('.cm-editor').exists()).toBe(true))
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    wrapper.unmount()
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    wrappers.splice(wrappers.indexOf(wrapper), 1)
    wrapper.element.remove()
  })

  it('按需載入原始碼編輯器，編輯後返回排版保留最新正文', async () => {
    const { wrapper, view } = await render()
    await wrapper.findAll('button').find(button => button.text() === '原始碼')!.trigger('click')
    await vi.waitFor(() => expect(wrapper.find('.cm-editor').exists()).toBe(true))
    const { EditorView: SourceView } = await import('@codemirror/view')
    const sourceView = SourceView.findFromDOM(wrapper.get('.cm-editor').element as HTMLElement)!
    sourceView.dispatch({ changes: { from: sourceView.state.doc.length, insert: '的新內容' } })
    const value = wrapper.emitted('update:modelValue')!.at(-1)![0] as string
    expect(value).toBe('正文的新內容')
    await wrapper.setProps({ modelValue: value })
    const updates = wrapper.emitted('update:modelValue')!.length
    await wrapper.findAll('button').find(button => button.text() === '返回排版編輯')!.trigger('click')
    expect(wrapper.find('.cm-editor').exists()).toBe(false)
    expect(view.state.doc.textContent).toBe(value)
    expect(wrapper.emitted('update:modelValue')).toHaveLength(updates)
    view.dispatch(view.state.tr.insertText('！', view.state.doc.content.size - 1))
    expect(wrapper.emitted('update:modelValue')!.at(-1)![0]).toContain('正文的新內容！')
  })

  it('外部復原載入新正文，不會再發布舊稿；唯讀不能套用格式', async () => {
    const { wrapper, view } = await render('舊稿')
    await wrapper.setProps({ modelValue: '復原的新稿', readonly: true })
    expect(view.state.doc.textContent).toBe('復原的新稿')
    expect(view.editable).toBe(false)
    expect(wrapper.get('button[aria-label="粗體"]').attributes('disabled')).toBeDefined()
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('網址驗證失敗不改正文，插入有效連結回到原選取位置', async () => {
    const { wrapper, view } = await render()
    view.dispatch(view.state.tr.setSelection(TextSelection.create(view.state.doc, 1, 3)))
    await wrapper.get('button[aria-label="插入連結"]').trigger('click')
    await wrapper.findAll('input')[1]!.setValue('javascript:alert(1)')
    await wrapper.findAll('button').find(button => button.text() === '插入')!.trigger('click')
    expect(wrapper.text()).toContain('請輸入有效的 HTTP(S) 網址')
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    await wrapper.findAll('input')[1]!.setValue('https://example.com')
    await wrapper.findAll('button').find(button => button.text() === '插入')!.trigger('click')
    expect(wrapper.get('.ProseMirror a').text()).toBe('正文')
    expect(wrapper.get('.ProseMirror a').attributes('href')).toBe(new URL('https://example.com').href)
  })

  it('表格列欄操作只在游標位於儲存格時啟用，新增後確實擴充表格', async () => {
    const { wrapper, view } = await render('前言\n\n| 欄位 |\n| --- |\n| 內容 |\n\n結語')
    const addRow = () => wrapper.findAll('button').find(button => button.text() === '表格新增列')!
    const addColumn = () => wrapper.findAll('button').find(button => button.text() === '表格新增欄')!
    expect(addRow().attributes('disabled')).toBeDefined()
    expect(addColumn().attributes('disabled')).toBeDefined()
    let cellText = 0
    view.state.doc.descendants((node, pos) => { if (node.isText && node.text === '內容') cellText = pos })
    view.dispatch(view.state.tr.setSelection(TextSelection.create(view.state.doc, cellText)))
    await wrapper.vm.$nextTick()
    expect(addRow().attributes('disabled')).toBeUndefined()
    expect(addColumn().attributes('disabled')).toBeUndefined()
    await addRow().trigger('click')
    expect(wrapper.findAll('.ProseMirror tr')).toHaveLength(3)
    await addColumn().trigger('click')
    expect(wrapper.findAll('.ProseMirror tr')[0]!.findAll('th')).toHaveLength(2)
    await wrapper.setProps({ readonly: true })
    expect(addRow().attributes('disabled')).toBeDefined()
    expect(addColumn().attributes('disabled')).toBeDefined()
    await wrapper.setProps({ readonly: false })
    view.dispatch(view.state.tr.setSelection(TextSelection.create(view.state.doc, 1)))
    await wrapper.vm.$nextTick()
    expect(addRow().attributes('disabled')).toBeDefined()
    expect(addColumn().attributes('disabled')).toBeDefined()
  })
})
