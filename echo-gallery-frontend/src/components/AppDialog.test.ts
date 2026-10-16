import { defineComponent, nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import AppDialog from './AppDialog.vue'

const confirm = vi.hoisted(() => vi.fn())
vi.mock('element-plus', () => ({ ElMessageBox: { confirm } }))

// 模擬 Element Plus 先詢問 before-close，再實際關閉的事件順序。
const Dialog = defineComponent({
  props: ['modelValue', 'beforeClose', 'showClose', 'closeOnClickModal', 'closeOnPressEscape'],
  emits: ['update:modelValue'],
  template: `<section v-if="modelValue"><button v-if="showClose" class="x" @click="beforeClose(() => $emit('update:modelValue', false))">×</button><button class="overlay" @click="closeOnClickModal && beforeClose(() => $emit('update:modelValue', false))" /><button class="escape" @click="closeOnPressEscape && beforeClose(() => $emit('update:modelValue', false))" /><slot /><slot name="footer" /></section>`,
})
const wrappers: ReturnType<typeof mount>[] = []
async function render(props: Record<string, unknown> = {}) {
  const wrapper = mount(AppDialog, {
    props: { modelValue: true, title: '編輯內容', ...props },
    slots: { footer: '<template #default="{ requestClose }"><button class="cancel" @click="requestClose">取消</button></template>' },
    global: { stubs: { 'el-dialog': Dialog } },
  })
  wrappers.push(wrapper)
  await nextTick()
  await nextTick()
  return wrapper
}

describe('彈窗離開保護', () => {
  beforeEach(() => { confirm.mockReset(); confirm.mockResolvedValue('confirm') })
  afterEach(() => wrappers.splice(0).forEach(wrapper => wrapper.unmount()))

  it.each(['.x', '.escape', '.cancel'])('有草稿時 %s 取消放棄會保留畫面，確認才關閉', async selector => {
    const wrapper = await render({ draft: { title: '原內容' } })
    await wrapper.setProps({ draft: { title: '修改內容' } })
    confirm.mockRejectedValueOnce('cancel')
    await wrapper.get(selector).trigger('click')
    await vi.waitFor(() => expect(confirm).toHaveBeenCalledTimes(1))
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    await wrapper.get(selector).trigger('click')
    await vi.waitFor(() => expect(wrapper.emitted('update:modelValue')).toEqual([[false]]))
  })

  it('閱讀型遮罩可關閉，表單遮罩不關閉；內容還原後不詢問', async () => {
    const reading = await render()
    await reading.get('.overlay').trigger('click')
    expect(reading.emitted('update:modelValue')).toEqual([[false]])
    const form = await render({ draft: '原內容' })
    await form.setProps({ draft: '修改' })
    await form.get('.overlay').trigger('click')
    expect(form.emitted('update:modelValue')).toBeUndefined()
    await form.setProps({ draft: '原內容' })
    await form.get('.cancel').trigger('click')
    expect(confirm).not.toHaveBeenCalled()
    expect(form.emitted('update:modelValue')).toEqual([[false]])
  })

  it('提交中所有互動入口都被鎖定，失敗解鎖後草稿仍受保護', async () => {
    const wrapper = await render({ draft: '原內容' })
    await wrapper.setProps({ draft: '修改', busy: true })
    expect(wrapper.find('.x').exists()).toBe(false)
    for (const selector of ['.overlay', '.escape', '.cancel']) await wrapper.get(selector).trigger('click')
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(confirm).not.toHaveBeenCalled()
    await wrapper.setProps({ busy: false })
    await wrapper.get('.cancel').trigger('click')
    await vi.waitFor(() => expect(confirm).toHaveBeenCalledOnce())
  })

  it('延遲載入的初始內容不視為草稿，重新開啟會建立新基準', async () => {
    const wrapper = await render({ draft: '', draftReady: false })
    await wrapper.setProps({ draft: '伺服器內容', draftReady: true })
    await nextTick()
    await wrapper.get('.cancel').trigger('click')
    expect(confirm).not.toHaveBeenCalled()
    await wrapper.setProps({ modelValue: false })
    await wrapper.setProps({ modelValue: true, draft: '下一份內容' })
    await nextTick()
    await wrapper.get('.cancel').trigger('click')
    expect(confirm).not.toHaveBeenCalled()
  })

  it('等待放棄確認時若開始提交，不得關閉；不重複開確認框', async () => {
    let resolve!: () => void
    confirm.mockImplementation(() => new Promise<void>(done => { resolve = done }))
    const wrapper = await render({ draft: '原內容' })
    await wrapper.setProps({ draft: '修改' })
    await wrapper.get('.x').trigger('click')
    await wrapper.get('.cancel').trigger('click')
    expect(confirm).toHaveBeenCalledOnce()
    await wrapper.setProps({ busy: true })
    resolve()
    await nextTick()
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('子流程先消化返回操作，不放棄父層草稿', async () => {
    const back = vi.fn()
    const wrapper = await render({ draft: '原內容', beforeClose: back })
    await wrapper.setProps({ draft: '父層草稿' })
    await wrapper.get('.cancel').trigger('click')
    expect(back).toHaveBeenCalledOnce()
    expect(confirm).not.toHaveBeenCalled()
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('上一輪未結束的放棄確認不得關閉重新開啟的彈窗', async () => {
    let resolve!: () => void
    confirm.mockImplementation(() => new Promise<void>(done => { resolve = done }))
    const wrapper = await render({ draft: '原內容' })
    await wrapper.setProps({ draft: '修改' })
    await wrapper.get('.cancel').trigger('click')
    await wrapper.setProps({ modelValue: false })
    await wrapper.setProps({ modelValue: true, draft: '新草稿' })
    resolve()
    await nextTick()
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })
})
