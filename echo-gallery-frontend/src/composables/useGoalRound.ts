import { getCurrentScope, onScopeDispose, ref } from 'vue'
import type { GoalBoardPage, GoalPreference } from '../types/goal'
import { goalApi } from '../utils/api/goalApi'
interface Round { seed: string; selection: string; cursors: string[]; index: number; pages: Array<{ ids: string[]; nextCursor: string; hasMore: boolean }>; seen: string[]; expires: number }
export function useGoalRound(owner: string) {
  const key = `goal-round:${owner}`, round = ref<Round | null>(null), current = ref<GoalBoardPage | null>(null), busy = ref(false), error = ref('')
  let disposed = false
  if (getCurrentScope()) onScopeDispose(() => { disposed = true })
  function restore(selection: GoalPreference) {
    try { const stored = JSON.parse(sessionStorage.getItem(key) || 'null') as Round | null; if (stored && Array.isArray(stored.pages) && stored.expires > Date.now() && stored.selection === JSON.stringify(selection)) round.value = stored } catch { sessionStorage.removeItem(key) }
  }
  async function load(selection: GoalPreference, direction: 'start' | 'next' | 'previous' | 'resume' = 'start') {
    if (busy.value) return
    busy.value = true; error.value = ''
    const authAtStart = localStorage.getItem('id')
    if (round.value && round.value.expires <= Date.now()) direction = 'start'
    const previous = round.value ? JSON.parse(JSON.stringify(round.value)) as Round : null
    if (direction === 'start' || !round.value) round.value = { seed: crypto.randomUUID(), selection: JSON.stringify(selection), cursors: [''], index: 0, pages: [], seen: [], expires: Date.now() + 86400000 }
    const state = round.value!
    if (direction === 'next' && current.value?.hasMore) { state.index++; if (!state.cursors[state.index]) state.cursors[state.index] = current.value.nextCursor }
    if (direction === 'previous') state.index = Math.max(0, state.index - 1)
    try {
      const previousPage = state.pages[state.index]
      const result = await goalApi.board(selection, state.seed, state.cursors[state.index], previousPage?.ids.map(Number))
      if (disposed || localStorage.getItem('id') !== authAtStart) return
      if (previousPage) {
        const byId = new Map(result.content.map(card => [String(card.id), card]))
        result.content = previousPage.ids.flatMap(id => byId.has(id) ? [byId.get(id)!] : [])
        result.hasMore = previousPage.hasMore; result.nextCursor = previousPage.nextCursor
      } else {
        if (direction === 'next') result.content = result.content.filter(card => !state.seen.includes(String(card.id)))
        state.pages[state.index] = { ids: result.content.map(card => String(card.id)), nextCursor: result.nextCursor, hasMore: result.hasMore }
      }
      state.seen = [...new Set([...state.seen, ...result.content.map(c => String(c.id))])]
      current.value = result; sessionStorage.setItem(key, JSON.stringify(state))
    } catch { round.value = previous; error.value = '讀取失敗或目標範圍已變更。可重試，或重新洗牌開始新一輪。' }
    finally { busy.value = false }
  }
  return { round, current, busy, error, restore, load }
}
