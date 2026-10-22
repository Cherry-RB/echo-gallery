import 'fake-indexeddb/auto'
import { mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { afterEach, describe, expect, it, vi } from 'vitest'
import WorkWorkspace from './WorkWorkspace.vue'
import type { WorkDetail } from '../../types/work'
import { workApi } from '../../utils/api/workApi'
const route = vi.hoisted(() => ({ query: {} as Record<string, string> }))
vi.mock('vue-router', () => ({ useRoute: () => route, useRouter: () => ({ push: vi.fn(), beforeEach: () => vi.fn() }), onBeforeRouteLeave: vi.fn(), onBeforeRouteUpdate: vi.fn() }))
vi.mock('../../utils/api/workApi', () => ({ workApi: { sources: vi.fn().mockResolvedValue([]), body: vi.fn() } }))
vi.mock('../../utils/api/request', () => ({ default: vi.fn() }))
vi.mock('../../utils/api/treasureApi', () => ({ treasureApi: { list: vi.fn().mockResolvedValue({ content: [] }) } }))
const clients: QueryClient[] = [], wrappers: ReturnType<typeof mount>[] = []
const base: WorkDetail = { work: { id: 987, title: '工作區測試', description: '', experimentId: null, experimentTitle: null, editorMode: 'INTERNAL', stage: 'WORKING', nextStep: '', published: false, archived: false, treasureId: null, editedAt: '2026-10-20T10:00:00Z' }, body: '**正文**', bodyVersion: 0, externalUrl: null, firstCompletedOn: null, stageChangedAt: '2026-10-20T10:00:00Z', shareUrl: null, sharedOn: null, shareNote: null }
function render(initial: WorkDetail) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } }); clients.push(client)
  const wrapper = mount(WorkWorkspace, { props: { initial }, global: { plugins: [[VueQueryPlugin, { queryClient: client }]], stubs: {
    AppDialog: true, WorkFormDialog: true, WorkSourcePicker: true, TreasureFormDialog: true,
    WorkMarkdownEditor: { props: ['modelValue', 'mode', 'readonly'], emits: ['update:modelValue'], template: '<div><textarea v-if="mode !== \'read\'" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" /><div v-else class="markdown-reading"><strong>{{ modelValue.replaceAll(\'**\', \'\') }}</strong></div></div>' },
    RouterLink: { template: '<a><slot /></a>' },
    'el-button': { props: ['disabled'], template: '<button :disabled="disabled"><slot /></button>' },
    'el-radio-group': { template: '<div><slot /></div>' }, 'el-radio-button': { template: '<span><slot /></span>' },
    'el-dropdown': { name: 'Dropdown', emits: ['command'], template: '<div><slot /><slot name="dropdown" /></div>' }, 'el-alert': { props: ['title'], template: '<div>{{ title }}<slot /></div>' },
    'el-dropdown-menu': { template: '<div><slot /></div>' }, 'el-dropdown-item': { template: '<span><slot /></span>' },
    'el-input': true, 'el-form-item': true, 'el-option': true, 'el-select': true, 'el-date-picker': true,
    'el-switch': true, 'el-radio': true, 'el-pagination': true, 'el-form': true,
  } } })
  wrappers.push(wrapper); return wrapper
}
afterEach(() => { wrappers.splice(0).forEach(w => w.unmount()); clients.splice(0).forEach(c => c.clear()); route.query = {} })
describe('作品工作區模式與封存', () => {
  it('正文儲存成功後同步詳情快取的正文與版本', async () => {
    const saved = { body: '已儲存的新稿', bodyVersion: 1, editedAt: '2026-10-22T10:00:00Z' }
    vi.mocked(workApi.body).mockResolvedValueOnce(saved)
    const wrapper = render(structuredClone(base))
    const client = clients.at(-1)!
    client.setQueryDefaults(['works', 'detail'], { gcTime: Infinity })
    client.setQueryData(['works', 'detail', base.work.id], structuredClone(base))
    await wrapper.get('textarea').setValue(saved.body)
    await vi.waitFor(() => expect(client.getQueryData<WorkDetail>(['works', 'detail', base.work.id])).toMatchObject({
      body: saved.body, bodyVersion: 1, work: { editedAt: saved.editedAt },
    }), { timeout: 3000 })
    expect(wrapper.text()).toContain('已儲存')
  })
  it('內建模式直接進入正文，編輯器變更沿用自動保存，改回原文即取消待存', async () => {
    const wrapper = render(structuredClone(base))
    expect(wrapper.get('textarea').element.value).toBe('**正文**')
    const editor = wrapper.get('textarea')
    await editor.setValue('編輯中的文字')
    expect(wrapper.text()).toContain('編輯中')
    // 立即還原並卸載，避免測試建立額外的伺服器保存。
    await editor.setValue('**正文**')
    expect(wrapper.text()).toContain('已儲存')
  })
  it('封存作品只提供閱讀與匯出，還原入口仍可見', () => {
    const d = structuredClone(base); d.work.archived = true
    const wrapper = render(d)
    expect(wrapper.find('textarea').exists()).toBe(false)
    expect(wrapper.text()).toContain('還原作品'); expect(wrapper.text()).toContain('匯出 Markdown')
    expect(wrapper.text()).not.toContain('加入來源'); expect(wrapper.text()).not.toContain('更改階段')
    expect(wrapper.find('.markdown-reading strong').text()).toBe('正文')
  })
  it('外部作品只有文件入口，沒有正文預覽或匯出', () => {
    const d = structuredClone(base); d.work.editorMode = 'EXTERNAL'; d.externalUrl = 'https://example.com/original'
    const wrapper = render(d)
    expect(wrapper.find('textarea').exists()).toBe(false)
    expect(wrapper.get('a[target="_blank"]').attributes('href')).toBe(d.externalUrl)
    expect(wrapper.text()).not.toContain('複製 Markdown'); expect(wrapper.text()).not.toContain('匯出 Markdown')
  })
  it('奇珍閱讀入口直接顯示目前正文，不增加獨立快照', () => {
    route.query = { mode: 'read' }
    const d = structuredClone(base); d.body = '**已修改的最新版**'
    const wrapper = render(d)
    expect(wrapper.find('textarea').exists()).toBe(false)
    expect(wrapper.find('.markdown-reading strong').text()).toBe('已修改的最新版')
  })
  it('複製選單仍可取出正文，且不帶入私人下一步與分享備註', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    const original = Object.getOwnPropertyDescriptor(navigator, 'clipboard')
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText } })
    try {
      const d = structuredClone(base); d.work.nextStep = '私人下一步'; d.shareNote = '私人分享備註'
      const wrapper = render(d)
      const menu = wrapper.findAllComponents({ name: 'Dropdown' }).find(item => item.classes().includes('work-export'))!
      menu.vm.$emit('command', 'markdown')
      await vi.waitFor(() => expect(writeText).toHaveBeenCalledWith('# 工作區測試\n\n**正文**'))
      menu.vm.$emit('command', 'plain')
      await vi.waitFor(() => expect(writeText).toHaveBeenLastCalledWith('工作區測試\n\n正文'))
      expect(writeText.mock.calls.flat().join('')).not.toContain('私人')
    } finally {
      if (original) Object.defineProperty(navigator, 'clipboard', original)
      else Reflect.deleteProperty(navigator, 'clipboard')
    }
  })
})
