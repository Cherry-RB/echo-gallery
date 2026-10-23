import { computed, ref } from 'vue'
import { shallowMount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { CardDto } from '../types/card'
import CardDetail from './CardDetail.vue'
import ExperimentThreads from '../components/experiment/ExperimentThreads.vue'
import router from '../router'

const queryState = vi.hoisted(() => ({
  card: undefined as CardDto | undefined,
  loading: false,
  error: false,
  threads: [] as Array<{ threadId: number; threadName: string; experimentId: number; experimentTitle: string; role: string }>,
}))

vi.mock('@tanstack/vue-query', async () => {
  const { ref: vueRef } = await import('vue')
  return {
    useQuery: (options: { queryKey: { value: string[] } }) => ({
      data: vueRef(options.queryKey.value[0] === 'card-exploration-threads' ? queryState.threads : options.queryKey.value[0] === 'card-relations' ? [] : queryState.card),
      isLoading: vueRef(queryState.loading),
      isError: vueRef(queryState.error),
      refetch: vi.fn(),
    }),
  }
})

vi.mock('../router', () => ({
  default: { back: vi.fn(), push: vi.fn() },
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {} }),
  onBeforeRouteLeave: vi.fn(),
}))

vi.mock('../utils/useCardStatus', () => ({
  useCardStatus: () => ({
    handleToggleStar: vi.fn(),
    handleToggleArchive: vi.fn(),
    handlePauseCard: vi.fn(),
    handleResumeCard: vi.fn(),
    handleUpdateRecurrence: vi.fn(),
    handleCreateCard: vi.fn(),
    handleUpdateCard: vi.fn(),
    handleDeleteCard: vi.fn(),
    isArchivePending: ref(false),
    isStarPending: ref(false),
    isPausePending: ref(false),
    isResumePending: ref(false),
    isRecurrencePending: ref(false),
    isCreatePending: ref(false),
    isUpdatePending: ref(false),
    isDeletePending: ref(false),
  }),
}))

vi.mock('../utils/composables/useTags', () => ({
  useTags: () => ({
    tagPopoverVisible: ref(false),
    tagSearchQuery: ref(''),
    filteredExistingTags: computed(() => []),
    handleToggleSelectTag: vi.fn(),
    handleCloseTag: vi.fn(),
    handleConfirmAddTag: vi.fn(),
  }),
}))

const card: CardDto = {
  id: '7',
  type: 'note',
  title: '值得再次看見的內容',
  cardNote: '這會改變我處理問題的方式。\n\n先理解問題，再選擇工具。',
  content: '完整筆記內容',
  tags: ['思考'],
  showContentPreview: true,
  intervalDays: 10,
  nextShowAt: '2026-09-29T00:00:00+08:00',
  openCount: 3,
  likeCount: 1,
  isArchived: false,
  createdAt: '2026-09-01T00:00:00+08:00',
  updatedAt: '2026-09-18T00:00:00+08:00',
}

const mountDetail = () => shallowMount(CardDetail, {
  props: { id: '7' },
  global: {
    stubs: {
      SidePanel: false,
      'el-button': { template: '<button><slot /></button>' },
      'el-tag': { template: '<span><slot /></span>' },
      'el-result': { props: ['title'], template: '<section class="result-stub">{{ title }}<slot name="extra" /></section>' },
      'el-skeleton': true,
      'el-dropdown': true,
      'el-dialog': true,
      'el-icon': true,
    },
  },
})

describe('CardDetail', () => {
  beforeEach(() => {
    queryState.card = undefined
    queryState.loading = false
    queryState.error = false
    queryState.threads = []
    vi.mocked(router.push).mockClear()
  })

  it('點擊關聯探索線在卡片原頁開啟共用彈窗', async () => {
    queryState.card = card
    queryState.threads = [{ threadId: 3, threadName: '散步探索', experimentId: 8, experimentTitle: '生活實驗', role: 'MATERIAL' }]
    const wrapper = mountDetail()
    await wrapper.get('.experiment-context-item').trigger('click')
    const dialog = wrapper.findComponent(ExperimentThreads)
    expect(dialog.props()).toMatchObject({ experimentId: 8, initialThreadId: 3, dialogOnly: true })
    expect(router.push).not.toHaveBeenCalled()
    dialog.vm.$emit('closed')
    await wrapper.vm.$nextTick()
    expect(wrapper.findComponent(ExperimentThreads).exists()).toBe(false)
    wrapper.unmount()
  })

  it('載入期間不會同時顯示預設卡片內容', () => {
    queryState.loading = true
    const wrapper = mountDetail()

    expect(wrapper.find('.detail-state').exists()).toBe(true)
    expect(wrapper.find('.reading-layout').exists()).toBe(false)
  })

  it('成功載入後以閱讀區段呈現核心內容', () => {
    queryState.card = card
    const wrapper = mountDetail()

    expect(wrapper.find('.reading-layout').exists()).toBe(true)
    expect(wrapper.text()).toContain('值得再次看見的內容')
    expect(wrapper.text()).toContain('這會改變我處理問題的方式。')
    expect(wrapper.text()).toContain('先理解問題，再選擇工具。')
    expect(wrapper.text()).toContain('完整筆記內容')
  })
})
