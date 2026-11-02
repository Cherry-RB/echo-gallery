import 'fake-indexeddb/auto'
import { mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { afterEach, describe, expect, it, vi } from 'vitest'
import WorkWorkspace from './WorkWorkspace.vue'
import type { WorkDetail } from '../../types/work'
import { workApi } from '../../utils/api/workApi'
import { experimentApi } from '../../utils/api/experimentApi'
import ExperimentThreads from '../experiment/ExperimentThreads.vue'
import ExpandableText from '../ExpandableText.vue'
import WorkSourceReadingDialog from './WorkSourceReadingDialog.vue'
import { ElMessageBox } from 'element-plus'
import { workDrafts } from '../../utils/workDrafts'
const route = vi.hoisted(() => ({ query: {} as Record<string, string> }))
const navigation = vi.hoisted(() => ({ push: vi.fn() }))
vi.mock('vue-router', () => ({ useRoute: () => route, useRouter: () => ({ push: navigation.push, beforeEach: () => vi.fn() }), onBeforeRouteLeave: vi.fn(), onBeforeRouteUpdate: vi.fn() }))
vi.mock('../../utils/api/workApi', () => ({ workApi: { sources: vi.fn().mockResolvedValue([]), body: vi.fn(), section: vi.fn(), update: vi.fn() } }))
vi.mock('../../utils/api/request', () => ({ default: vi.fn() }))
vi.mock('../../utils/api/treasureApi', () => ({ treasureApi: { list: vi.fn().mockResolvedValue({ content: [] }) } }))
const clients: QueryClient[] = [], wrappers: ReturnType<typeof mount>[] = []
const base: WorkDetail = { work: { id: 987, title: '工作區測試', description: '', experimentId: null, experimentTitle: null, editorMode: 'INTERNAL', stage: 'WORKING', nextStep: '', published: false, archived: false, treasureId: null, characterCount: 0, editedAt: '2026-10-20T10:00:00Z' }, body: '**正文**', bodyVersion: 0, ideas: '', ideasVersion: 0, manuscript: '', manuscriptVersion: 0, externalUrl: null, firstCompletedOn: null, stageChangedAt: '2026-10-20T10:00:00Z', shareUrl: null, sharedOn: null, shareNote: null }
function render(initial: WorkDetail) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } }); clients.push(client)
  const wrapper = mount(WorkWorkspace, { props: { initial }, global: { plugins: [[VueQueryPlugin, { queryClient: client }]], stubs: {
    AppDialog: true, WorkFormDialog: true, WorkSourcePicker: true, WorkSourceReadingDialog: true, TreasureFormDialog: true, ExperimentThreads: true,
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
afterEach(() => { wrappers.splice(0).forEach(w => w.unmount()); clients.splice(0).forEach(c => c.clear()); route.query = {}; navigation.push.mockClear(); vi.restoreAllMocks() })
describe('作品工作區模式與封存', () => {
  it('復原確認期間切換區域，草稿仍只套用到原本的區域', async () => {
    vi.spyOn(workDrafts, 'list').mockResolvedValueOnce([{ key: 'old', owner: '', workId: 987, instance: 'old', body: '復原的主專注', version: 0, sequence: 1, savedAt: Date.now(), section: 'FOCUS' }])
    let confirm!: () => void
    vi.spyOn(ElMessageBox, 'confirm').mockImplementationOnce(() => new Promise(resolve => { confirm = () => resolve('confirm' as never) }))
    const d = structuredClone(base); d.ideas = '原本的構思'
    const wrapper = render(d)
    await vi.waitFor(() => expect(wrapper.text()).toContain('復原草稿'))
    await wrapper.get('textarea').setValue('尚未儲存的主專注')
    await wrapper.findAll('button').find(button => button.text() === '復原草稿')!.trigger('click')
    await wrapper.findAll('.work-section-list button')[1]!.trigger('click')
    confirm()
    await vi.waitFor(() => expect(wrapper.get('textarea').element.value).toBe('原本的構思'))
    await wrapper.findAll('.work-section-list button')[0]!.trigger('click')
    await vi.waitFor(() => expect(wrapper.get('textarea').element.value).toBe('復原的主專注'))
  })
  it('切換三區保留各自內容並原樣複製', async () => {
    const d = structuredClone(base); d.ideas = '構思\n第二行'; d.manuscript = '已寫稿件'
    const wrapper = render(d)
    expect(wrapper.find('.body-panel > textarea').exists()).toBe(true)
    await wrapper.get('textarea').setValue('主專注的新內容')
    const choices = wrapper.findAll('.work-section-list button')
    await choices[1]!.trigger('click')
    expect(wrapper.get('textarea').element.value).toBe('構思\n第二行')
    await wrapper.get('textarea').setValue('構思的新內容')
    await choices[2]!.trigger('click')
    expect(wrapper.get('textarea').element.value).toBe('已寫稿件')
    await choices[0]!.trigger('click')
    expect(wrapper.get('textarea').element.value).toBe('主專注的新內容')
    await choices[1]!.trigger('click')
    expect(wrapper.get('textarea').element.value).toBe('構思的新內容')
  })
  it('進出專注編輯保留正文及同一編輯器，離開時恢復頁面捲動', async () => {
    const previousOverflow = document.body.style.overflow
    const wrapper = render(structuredClone(base))
    const input = wrapper.get('textarea').element
    await wrapper.get('textarea').setValue('專注中的草稿')
    await wrapper.findAll('button').find(button => button.text() === '專注編輯')!.trigger('click')
    expect(wrapper.get('.body-panel').attributes('role')).toBe('dialog')
    expect(document.body.style.overflow).toBe('hidden')
    expect(wrapper.get('textarea').element).toBe(input)
    await wrapper.get('.body-panel').trigger('keydown', { key: 'Escape' })
    expect(wrapper.find('.is-focus').exists()).toBe(false)
    expect(document.body.style.overflow).toBe(previousOverflow)
    expect(wrapper.get('textarea').element.value).toBe('專注中的草稿')
  })
  it('頁首下拉選單變更階段時保留完成日期，且不顯示舊的快捷按鈕', async () => {
    const d = structuredClone(base); d.firstCompletedOn = '2026-10-01'
    const saved = structuredClone(d); saved.work.stage = 'REVISING'
    vi.mocked(workApi.update).mockResolvedValueOnce(saved)
    const wrapper = render(d)
    expect(wrapper.text()).not.toContain('標記初版完成')
    expect(wrapper.find('.detail-heading-metadata').exists()).toBe(false)
    wrapper.findAllComponents({ name: 'Dropdown' }).find(item => item.classes().includes('work-stage-menu'))!.vm.$emit('command', 'REVISING')
    await vi.waitFor(() => expect(workApi.update).toHaveBeenCalledWith(base.work.id, 'stage', { stage: 'REVISING', firstCompletedOn: '2026-10-01' }))
    await vi.waitFor(() => expect(wrapper.get('.work-stage-menu').text()).toContain('編修中'))
  })

  it('頁首切換發表狀態保留分享網址、日期及備註', async () => {
    const d = structuredClone(base); d.shareUrl = 'https://example.com/story'; d.sharedOn = '2026-10-01'; d.shareNote = '原有分享備註'
    const saved = structuredClone(d); saved.work.published = true
    vi.mocked(workApi.update).mockResolvedValueOnce(saved)
    const wrapper = render(d)
    wrapper.findAllComponents({ name: 'Dropdown' }).find(item => item.classes().includes('work-publication-menu'))!.vm.$emit('command', 'PUBLISHED')
    await vi.waitFor(() => expect(workApi.update).toHaveBeenCalledWith(base.work.id, 'share', { published: true, shareUrl: d.shareUrl, sharedOn: d.sharedOn, shareNote: d.shareNote }))
    expect(wrapper.get('textarea').element.value).toBe(base.body)
  })

  it('參考素材整塊可閱讀，長篇下一步使用共用展開元件', async () => {
    const source = { id: 6, cardId: 7, recordId: null, title: '來源卡片', text: '', experimentId: null, experimentTitle: null, archived: false }
    vi.mocked(workApi.sources).mockResolvedValueOnce([source])
    const d = structuredClone(base); d.work.nextStep = '下一步'.repeat(200)
    const wrapper = render(d)
    await vi.waitFor(() => expect(wrapper.find('.work-source').exists()).toBe(true))
    expect(wrapper.get('.work-source').text()).not.toContain('閱讀全文')
    await wrapper.get('.work-source').trigger('keydown.enter')
    expect(wrapper.findComponent(WorkSourceReadingDialog).props('source')).toEqual(source)
    expect(wrapper.findComponent(ExpandableText).props()).toMatchObject({ content: d.work.nextStep, lines: 3 })
    expect(wrapper.findAll('.work-disclosure > summary button').map(button => button.text())).toEqual(['加入來源', '編輯分享資訊'])
  })
  it('關聯探索線在作品原頁開窗，關閉後保留正文', async () => {
    vi.spyOn(experimentApi, 'getWorkThreads').mockResolvedValue([{ threadId: 3, threadName: '散步探索', experimentId: 8, experimentTitle: '生活實驗', status: 'IN_PROGRESS' }])
    const wrapper = render(structuredClone(base))
    await vi.waitFor(() => expect(wrapper.find('.work-related-item').exists()).toBe(true))
    await wrapper.get('.work-related-item').trigger('click')
    const dialog = wrapper.findComponent(ExperimentThreads)
    expect(dialog.props()).toMatchObject({ experimentId: 8, initialThreadId: 3, dialogOnly: true })
    expect(navigation.push).not.toHaveBeenCalled()
    dialog.vm.$emit('closed')
    await wrapper.vm.$nextTick()
    expect(wrapper.findComponent(ExperimentThreads).exists()).toBe(false)
    expect(wrapper.get('textarea').element.value).toBe(base.body)
  })
  it('正文儲存成功後同步詳情字元數，並讓總覽與列表重新取得摘要', async () => {
    const saved = { body: '新稿😀', bodyVersion: 1, editedAt: '2026-10-22T10:00:00Z' }
    vi.mocked(workApi.section).mockResolvedValueOnce(saved)
    const wrapper = render(structuredClone(base))
    const client = clients.at(-1)!
    client.setQueryDefaults(['works', 'detail'], { gcTime: Infinity })
    client.setQueryData(['works', 'detail', base.work.id], structuredClone(base))
    client.setQueryDefaults(['works', 'resume'], { gcTime: Infinity })
    client.setQueryDefaults(['works', 'list'], { gcTime: Infinity })
    client.setQueryData(['works', 'resume'], { content: [base.work] })
    client.setQueryData(['works', 'list'], { content: [base.work] })
    await wrapper.get('textarea').setValue(saved.body)
    await vi.waitFor(() => expect(client.getQueryData<WorkDetail>(['works', 'detail', base.work.id])).toMatchObject({
      body: saved.body, bodyVersion: 1, work: { editedAt: saved.editedAt, characterCount: 3 },
    }), { timeout: 3000 })
    expect(wrapper.text()).toContain('已儲存')
    expect(client.getQueryState(['works', 'resume'])?.isInvalidated).toBe(true)
    expect(client.getQueryState(['works', 'list'])?.isInvalidated).toBe(true)
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
    expect(wrapper.get('textarea').attributes('readonly')).toBeDefined()
    expect(wrapper.text()).toContain('還原作品'); expect(wrapper.text()).toContain('複製全文')
    expect(wrapper.text()).not.toContain('加入來源'); expect(wrapper.text()).not.toContain('更改階段')
    expect(wrapper.get('textarea').element.value).toBe('**正文**')
  })
  it('外部作品保有文件入口與站內純文字', () => {
    const d = structuredClone(base); d.work.editorMode = 'EXTERNAL'; d.externalUrl = 'https://example.com/original'
    const wrapper = render(d)
    expect(wrapper.get('textarea').element.value).toBe(base.body)
    expect(wrapper.get('a[target="_blank"]').attributes('href')).toBe(d.externalUrl)
    expect(wrapper.text()).not.toContain('複製 Markdown'); expect(wrapper.text()).not.toContain('匯出 Markdown')
  })
  it('展品閱讀入口顯示已寫稿件', () => {
    route.query = { mode: 'read', section: 'MANUSCRIPT' }
    const d = structuredClone(base); d.manuscript = '**已修改的最新版**'
    const wrapper = render(d)
    expect(wrapper.get('textarea').attributes('readonly')).toBeDefined()
    expect(wrapper.get('textarea').element.value).toBe('**已修改的最新版**')
  })
  it('複製選單仍可取出正文，且不帶入私人下一步與分享備註', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    const original = Object.getOwnPropertyDescriptor(navigator, 'clipboard')
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText } })
    try {
      const d = structuredClone(base); d.work.nextStep = '私人下一步'; d.shareNote = '私人分享備註'
      const wrapper = render(d)
      await wrapper.findAll('button').find(button => button.text() === '複製全文')!.trigger('click')
      await vi.waitFor(() => expect(writeText).toHaveBeenCalledWith('**正文**'))
      expect(writeText.mock.calls.flat().join('')).not.toContain('私人')
    } finally {
      if (original) Object.defineProperty(navigator, 'clipboard', original)
      else Reflect.deleteProperty(navigator, 'clipboard')
    }
  })
})
