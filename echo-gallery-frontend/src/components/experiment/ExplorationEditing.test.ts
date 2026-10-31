import { defineComponent, nextTick } from 'vue'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ElMessage, ElMessageBox } from 'element-plus'
import ExplorationHistoryEditor from './ExplorationHistoryEditor.vue'
import ExplorationMethodAssignmentDialog from './ExplorationMethodAssignmentDialog.vue'
import { explorationActivityApi as api } from '../../utils/api/explorationActivityApi'

vi.mock('../../utils/api/explorationActivityApi', () => ({ explorationActivityApi: {
  history: vi.fn(), updateHistory: vi.fn(), method: vi.fn(), state: vi.fn(), assignMethod: vi.fn(),
} }))
const { invalidate, confirm } = vi.hoisted(() => ({ invalidate: vi.fn(), confirm: vi.fn() }))
vi.mock('../../composables/useExplorationInvalidation', () => ({ useExplorationInvalidation: () => invalidate }))
vi.mock('element-plus', () => ({ ElMessage: { success: vi.fn(), warning: vi.fn() }, ElMessageBox: { confirm } }))
const method = { id: 8, threadId: 3, threadName: 'A', content: '原行動', version: 4, discoveryCount: 2, hasHistory: true, lastOrganizedAt: null }
const wrappers: VueWrapper[] = []
const Input = defineComponent({
  name: 'Input', props: ['modelValue', 'disabled'], emits: ['update:modelValue'],
  methods: { focus() {} },
  template: '<textarea :value="modelValue" :disabled="disabled" @input="$emit(\'update:modelValue\', $event.target.value)" />',
})
async function render(kind: 'history' | 'assignment' = 'history') {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: { template: '<div />' } }, { path: '/other', component: { template: '<div />' } }] })
  await router.push('/'); await router.isReady()
  const common = { modelValue: true, experimentId: 1, threadId: 3 }
  const props = kind === 'history' ? { ...common, methodId: 8, label: 'A／原行動' } : { ...common, methodId: 8 }
  const wrapper = mount(kind === 'history' ? ExplorationHistoryEditor : ExplorationMethodAssignmentDialog, {
    props, global: { plugins: [router], stubs: {
      'el-dialog': { props: ['modelValue', 'title'], template: '<section v-if="modelValue"><h2>{{ title }}</h2><slot /><footer><slot name="footer" /></footer></section>' },
      'el-input': Input,
      'el-button': { props: ['disabled'], template: '<button :disabled="disabled"><slot /></button>' },
      'el-alert': { props: ['title'], template: '<p role="alert">{{ title }}</p>' },
      'el-skeleton': true, 'el-form': { template: '<div><slot /></div>' }, 'el-form-item': { template: '<div><slot /></div>' },
      ExplorationThreadSelect: { name: 'ThreadSelect', props: ['modelValue', 'disabled'], emits: ['update:modelValue'], methods: { focus() {} }, template: '<div />' },
    } },
  })
  wrappers.push(wrapper)
  await flushPromises()
  return { wrapper, router }
}
async function click(wrapper: VueWrapper, text: string) {
  await nextTick()
  await wrapper.findAll('button').find(button => button.text() === text)!.trigger('click')
  await flushPromises()
}
beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(api.history).mockResolvedValue({ text: '原歷史\n\n---\n\n心得', version: 4, lastOrganizedAt: null })
  vi.mocked(api.method).mockResolvedValue(method)
  vi.mocked(api.state).mockResolvedValue({ currentMethod: method } as Awaited<ReturnType<typeof api.state>>)
  vi.mocked(api.updateHistory).mockResolvedValue({ text: '修改後', version: 5, lastOrganizedAt: null })
  vi.mocked(api.assignMethod).mockResolvedValue({ ...method, threadId: 9 })
  confirm.mockResolvedValue(undefined)
  invalidate.mockResolvedValue(undefined)
})
afterEach(() => wrappers.splice(0).forEach(wrapper => wrapper.unmount()))

describe('歷次整理編輯', () => {
  it('載入完整文字，未修改不能儲存，儲存保留換行並帶入版本', async () => {
    const { wrapper } = await render()
    expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('原歷史\n\n---\n\n心得')
    expect(wrapper.findAll('button').find(b => b.text() === '儲存')!.attributes('disabled')).toBeDefined()
    await wrapper.get('textarea').setValue('  修改後\n\n心得 😀\n')
    await click(wrapper, '儲存')
    expect(api.updateHistory).toHaveBeenCalledWith(1, { threadId: 3, methodId: 8, text: '  修改後\n\n心得 😀\n', expectedVersion: 4 })
    expect(wrapper.emitted('saved')).toHaveLength(1)
    expect(invalidate).toHaveBeenCalledWith(1)
  })
  it('取消與路由離開都保護草稿，拒絕放棄時不寫入', async () => {
    const { wrapper, router } = await render()
    await wrapper.get('textarea').setValue('未保存草稿')
    vi.mocked(ElMessageBox.confirm).mockRejectedValue('cancel')
    await click(wrapper, '取消')
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    await router.push('/other')
    expect(router.currentRoute.value.path).toBe('/')
    expect(api.updateHistory).not.toHaveBeenCalled()
  })
  it('清空需要確認，取消清空仍保留草稿', async () => {
    const { wrapper } = await render()
    await wrapper.get('textarea').setValue('')
    vi.mocked(ElMessageBox.confirm).mockRejectedValueOnce('cancel')
    await click(wrapper, '儲存')
    expect(api.updateHistory).not.toHaveBeenCalled()
    await click(wrapper, '儲存')
    expect(ElMessageBox.confirm).toHaveBeenCalledWith(expect.any(String), '清空歷次整理', expect.any(Object))
    expect(api.updateHistory).toHaveBeenCalledWith(1, expect.objectContaining({ text: '' }))
  })
  it('空白歷史可貼上內容，版本衝突保留草稿且不能直接覆寫', async () => {
    vi.mocked(api.history).mockResolvedValue({ text: '', version: 0, lastOrganizedAt: null })
    const { wrapper } = await render()
    await wrapper.setProps({ methodId: null })
    await flushPromises()
    await wrapper.get('textarea').setValue('從 A 複製的文字')
    vi.mocked(api.updateHistory).mockRejectedValue({ response: { status: 409 } })
    await click(wrapper, '儲存')
    expect(api.updateHistory).toHaveBeenCalledWith(1, expect.objectContaining({ methodId: null, expectedVersion: 0 }))
    expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('從 A 複製的文字')
    expect(wrapper.findAll('button').find(b => b.text() === '儲存')!.attributes('disabled')).toBeDefined()
    vi.mocked(ElMessageBox.confirm).mockRejectedValue('cancel')
    await click(wrapper, '重新載入')
    expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('從 A 複製的文字')
  })
  it('儲存逾時先查證，不重送寫入，查證成功才關閉', async () => {
    const { wrapper } = await render()
    await wrapper.get('textarea').setValue('修改後')
    vi.mocked(api.updateHistory).mockRejectedValue(new Error('timeout'))
    await click(wrapper, '儲存')
    expect(wrapper.emitted('saved')).toBeUndefined()
    expect(wrapper.get('textarea').attributes('disabled')).toBeDefined()
    vi.mocked(api.history).mockResolvedValue({ text: '修改後', version: 5, lastOrganizedAt: null })
    await click(wrapper, '確認儲存結果')
    expect(api.updateHistory).toHaveBeenCalledTimes(1)
    expect(wrapper.emitted('saved')).toHaveLength(1)
  })
  it('API 成功後快取更新失敗仍回報已保存', async () => {
    const { wrapper } = await render()
    await wrapper.get('textarea').setValue('修改後')
    invalidate.mockRejectedValue(new Error('refresh failed'))
    await click(wrapper, '儲存')
    expect(wrapper.emitted('saved')).toHaveLength(1)
    expect(ElMessage.warning).toHaveBeenCalled()
    expect(ElMessage.success).toHaveBeenCalledWith('歷次整理已儲存')
  })
  it('儲存中阻擋重複提交與路由離開', async () => {
    const { wrapper, router } = await render()
    let complete!: () => void
    vi.mocked(api.updateHistory).mockImplementation(() => new Promise(resolve => {
      complete = () => resolve({ text: '修改後', version: 5, lastOrganizedAt: null })
    }))
    await wrapper.get('textarea').setValue('修改後')
    await click(wrapper, '儲存')
    await click(wrapper, '儲存')
    await router.push('/other')
    expect(router.currentRoute.value.path).toBe('/')
    expect(api.updateHistory).toHaveBeenCalledTimes(1)
    complete(); await flushPromises()
    expect(wrapper.emitted('saved')).toHaveLength(1)
  })
})

describe('調整行動所屬探索線', () => {
  it('選擇其他探索線才可提交，帶入最新版本並說明隨行內容與下一步', async () => {
    const { wrapper } = await render('assignment')
    expect(wrapper.text()).toContain('2 筆發現與歷次整理')
    expect(wrapper.text()).toContain('下一步的探索線也會同步調整')
    wrapper.findComponent({ name: 'ThreadSelect' }).vm.$emit('update:modelValue', 3)
    await nextTick()
    expect(wrapper.findAll('button').find(b => b.text() === '確認移動')!.attributes('disabled')).toBeDefined()
    wrapper.findComponent({ name: 'ThreadSelect' }).vm.$emit('update:modelValue', 9)
    await click(wrapper, '確認移動')
    expect(api.assignMethod).toHaveBeenCalledWith(1, 8, 9, 4)
    expect(wrapper.emitted('saved')).toHaveLength(1)
  })
  it('取消沒有寫入，選擇草稿由彈窗保護', async () => {
    const { wrapper } = await render('assignment')
    wrapper.findComponent({ name: 'ThreadSelect' }).vm.$emit('update:modelValue', 9)
    await nextTick()
    vi.mocked(ElMessageBox.confirm).mockRejectedValue('cancel')
    await click(wrapper, '取消')
    expect(api.assignMethod).not.toHaveBeenCalled()
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })
  it('逾時先確認歸屬，不會重送搬移', async () => {
    const { wrapper } = await render('assignment')
    wrapper.findComponent({ name: 'ThreadSelect' }).vm.$emit('update:modelValue', 9)
    vi.mocked(api.assignMethod).mockRejectedValue(new Error('timeout'))
    await click(wrapper, '確認移動')
    expect(wrapper.emitted('saved')).toBeUndefined()
    vi.mocked(api.method).mockResolvedValue({ ...method, threadId: 9, version: 5 })
    await click(wrapper, '確認移動結果')
    expect(api.assignMethod).toHaveBeenCalledTimes(1)
    expect(wrapper.emitted('saved')).toHaveLength(1)
  })
  it('同名或版本衝突保留目標與錯誤訊息，重新載入前不覆寫', async () => {
    const { wrapper } = await render('assignment')
    wrapper.findComponent({ name: 'ThreadSelect' }).vm.$emit('update:modelValue', 9)
    vi.mocked(api.assignMethod).mockRejectedValue({ response: { status: 409, data: { message: '目標探索線已有相同行動' } } })
    await click(wrapper, '確認移動')
    expect(wrapper.findComponent({ name: 'ThreadSelect' }).props('modelValue')).toBe(9)
    expect(wrapper.text()).toContain('目標探索線已有相同行動')
    expect(wrapper.emitted('saved')).toBeUndefined()
    expect(wrapper.findAll('button').find(b => b.text() === '確認移動')!.attributes('disabled')).toBeDefined()
  })
})
