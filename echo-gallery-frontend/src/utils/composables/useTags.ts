import { ref, computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { tagApi } from '../api/tagApi'
import { ElMessage } from 'element-plus'
import type { Ref } from 'vue'
import type { CardDto } from '../../types/card'

const MAX_TAGS = 10
const MAX_TAG_LENGTH = 50

export function useTags(cardDataRef: Ref<CardDto>) {
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
      if (cardDataRef.value.tags.length >= MAX_TAGS) {
        ElMessage.warning(`每張卡片最多只能有 ${MAX_TAGS} 個標籤`)
        return
      }
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
    if (!trimmed) {
      ElMessage.warning('標籤不可為空')
      return
    }
    if (trimmed.length > MAX_TAG_LENGTH) {
      ElMessage.warning(`單一標籤不可超過 ${MAX_TAG_LENGTH} 個字元`)
      return
    }

    if (!cardDataRef.value.tags) cardDataRef.value.tags = []
    if (cardDataRef.value.tags.includes(trimmed)) {
      ElMessage.warning('標籤不可重複')
      return
    }
    if (cardDataRef.value.tags.length >= MAX_TAGS) {
      ElMessage.warning(`每張卡片最多只能有 ${MAX_TAGS} 個標籤`)
      return
    }
    cardDataRef.value.tags.push(trimmed)
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
