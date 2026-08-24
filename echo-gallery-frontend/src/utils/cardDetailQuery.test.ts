import { computed, ref } from 'vue'
import { describe, expect, it } from 'vitest'
import { cardDetailQueryKey } from './cardDetailQuery'

describe('card detail query key', () => {
  it('路由 id 改變時會產生新的查詢 key', () => {
    const id = ref('1')
    const queryKey = computed(() => cardDetailQueryKey(id.value))

    expect(queryKey.value).toEqual(['card', '1'])
    id.value = '2'
    expect(queryKey.value).toEqual(['card', '2'])
  })
})
