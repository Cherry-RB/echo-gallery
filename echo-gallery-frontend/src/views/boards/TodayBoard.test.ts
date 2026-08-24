import { flushPromises, mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import TodayBoard from './TodayBoard.vue'
import { cardApi } from '../../utils/api/cardApi'

vi.mock('../../utils/api/cardApi', () => ({
  cardApi: {
    prepareToday: vi.fn(),
    nextToday: vi.fn(),
  },
}))

vi.mock('../../utils/useCardStatus', () => ({
  useCardStatus: () => ({ handleReadCard: vi.fn(), isReadPending: ref(false) }),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
}))

function mountBoard() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return mount(TodayBoard, {
    global: {
      plugins: [[VueQueryPlugin, { queryClient }]],
      directives: { loading: () => undefined },
      stubs: {
        MasonryWall: {
          props: ['items'],
          template: '<div><div v-for="item in items" :key="item.id"><slot :item="item" /></div></div>',
        },
        CardItem: { props: ['data'], template: '<article>{{ data.title }}</article>' },
        'el-result': { template: '<div><slot name="extra" /></div>' },
        'el-button': { template: '<button><slot /></button>' },
      },
    },
  })
}

describe('TodayBoard', () => {
  beforeEach(() => vi.clearAllMocks())

  it('顯示 prepare 回傳的卡片', async () => {
    vi.mocked(cardApi.prepareToday).mockResolvedValue({
      cards: [{ id: '1', title: '今日卡片' } as never],
      batchOfferedAt: '2026-08-24T12:00:00+08:00',
    })
    const wrapper = mountBoard()
    await flushPromises()
    expect(wrapper.text()).toContain('今日卡片')
  })

  it('沒有候選時顯示初始空狀態且沒有換批按鈕', async () => {
    vi.mocked(cardApi.prepareToday).mockResolvedValue({ cards: [], batchOfferedAt: null })
    const wrapper = mountBoard()
    await flushPromises()
    expect(wrapper.text()).toContain('今天目前沒有需要回流的卡片')
    expect(wrapper.text()).not.toContain('今天想多看一批')
  })

  it('批次完成後仍可要求下一批', async () => {
    vi.mocked(cardApi.prepareToday).mockResolvedValue({
      cards: [], batchOfferedAt: '2026-08-24T12:00:00+08:00',
    })
    const wrapper = mountBoard()
    await flushPromises()
    expect(wrapper.text()).toContain('目前這批已完成')
    expect(wrapper.text()).toContain('今天想多看一批')
  })

  it('prepare 失敗時顯示重新載入', async () => {
    vi.mocked(cardApi.prepareToday).mockRejectedValue(new Error('failed'))
    const wrapper = mountBoard()
    await vi.waitFor(() => expect(wrapper.text()).toContain('重新載入'))
  })

  it('409 後重新 prepare 並清除沒有更多提示', async () => {
    const current = {
      cards: [{ id: '1', title: 'card-1' } as never],
      batchOfferedAt: '2026-08-24T12:00:00+08:00',
    }
    vi.mocked(cardApi.prepareToday).mockResolvedValue(current)
    vi.mocked(cardApi.nextToday)
      .mockResolvedValueOnce({ cards: [], batchOfferedAt: current.batchOfferedAt })
      .mockRejectedValueOnce({ response: { status: 409 } })

    const wrapper = mountBoard()
    await flushPromises()
    await wrapper.find('button').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('今天沒有更多新卡片了')

    await wrapper.find('button').trigger('click')
    await flushPromises()
    expect(wrapper.text()).not.toContain('今天沒有更多新卡片了')
    expect(cardApi.prepareToday).toHaveBeenCalledTimes(2)
    expect(cardApi.nextToday).toHaveBeenCalledTimes(2)
  })
})
