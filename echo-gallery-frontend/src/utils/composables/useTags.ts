import { ref, computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { tagApi } from '../api/tagApi'

export function useTags(cardDataRef: any) {
  const tagPopoverVisible = ref(false)
  const tagSearchQuery = ref('')

  // 1. 取得後端標籤清單
  const { data: existingTags, isLoading: isTagsLoading } = useQuery({
    queryKey: ['tags'],
    queryFn: () => tagApi.getTags(),
    staleTime: 1000 * 60 * 5, // 5 分鐘快取
  })

  // 2. 即時過濾標籤
  const filteredExistingTags = computed(() => {
    const tags = existingTags.value ?? []
    if (!tagSearchQuery.value.trim()) return tags

    return tags.filter((t: any) =>
      t.name.toLowerCase().includes(tagSearchQuery.value.toLowerCase())
    )
  })

  // 3. 切換選取/取消選取標籤
  const handleToggleSelectTag = (tagName: string) => {
    if (!cardDataRef.value.tags) cardDataRef.value.tags = []
    if (cardDataRef.value.tags.includes(tagName)) {
      cardDataRef.value.tags = cardDataRef.value.tags.filter((t: string) => t !== tagName)
    } else {
      cardDataRef.value.tags.push(tagName)
    }
  }

  // 4. 刪除已選標籤
  const handleCloseTag = (tag: string) => {
    cardDataRef.value.tags = cardDataRef.value.tags?.filter((t: string) => t !== tag) || []
  }

  // 5. 新增/選取標籤（支援按 Enter 或點擊觸控按鈕觸發）
  const handleConfirmAddTag = () => {
    const trimmed = tagSearchQuery.value.trim()
    if (!trimmed) return

    if (!cardDataRef.value.tags) cardDataRef.value.tags = []
    if (!cardDataRef.value.tags.includes(trimmed)) {
      cardDataRef.value.tags.push(trimmed)
    }
    tagSearchQuery.value = ''
  }

  return {
    tagPopoverVisible,
    tagSearchQuery,
    existingTags,
    isTagsLoading,
    filteredExistingTags,
    handleToggleSelectTag,
    handleCloseTag,
    handleConfirmAddTag
  }
}
