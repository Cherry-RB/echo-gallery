import { computed, onScopeDispose, ref } from 'vue'
import type { GoalReview, GoalReviewContent } from '../types/goal'
import { apiErrorMessage } from './api/errorMessage'

// 將防抖、自動儲存及提交前的 flush 串成單一寫入佇列，保留請求期間的新輸入。
export function useReviewDraft(write: (content: GoalReviewContent, version: number) => Promise<GoalReview>) {
  const content = ref<GoalReviewContent>({ observation: '', decision: '', direction: '', informationHelpfulness: null, actionEffectiveness: null })
  const version = ref(0), saving = ref(false), error = ref(''), baseline = ref('')
  let timer: ReturnType<typeof setTimeout> | undefined
  let pending: Promise<boolean> | undefined
  const normalized = () => ({ ...content.value, informationHelpfulness: content.value.informationHelpfulness || null, actionEffectiveness: content.value.actionEffectiveness || null })
  const dirty = computed(() => JSON.stringify(normalized()) !== baseline.value)
  function load(review: GoalReview) {
    clearTimeout(timer)
    content.value = { ...review.content, direction: review.content.direction || '' }
    version.value = review.version; baseline.value = JSON.stringify(normalized()); error.value = ''
  }
  async function flush(): Promise<boolean> {
    clearTimeout(timer)
    while (pending) { if (!await pending) return false }
    if (!dirty.value) return true
    const value = normalized(), signature = JSON.stringify(value)
    saving.value = true; error.value = ''
    pending = (async () => {
      try {
        const saved = await write(value, version.value)
        version.value = saved.version; baseline.value = signature
        return true
      } catch (e) {
        error.value = apiErrorMessage(e, '儲存失敗，內容仍保留。請重試，或先複製內容。')
        return false
      } finally { saving.value = false; pending = undefined }
    })()
    if (!await pending) return false
    return dirty.value ? flush() : true
  }
  function queue() { clearTimeout(timer); timer = setTimeout(() => { void flush() }, 700) }
  function cancel() { clearTimeout(timer) }
  onScopeDispose(cancel)
  return { content, version, saving, error, dirty, load, flush, queue, cancel }
}
