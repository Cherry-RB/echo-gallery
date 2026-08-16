import type { Ref } from 'vue'
import type { FormRules } from 'element-plus'
import type { CardContentRequest, CardDto } from '../types/card'

const isHttpUrl = (value?: string) => {
  if (!value) return true

  try {
    const url = new URL(value)
    return (url.protocol === 'http:' || url.protocol === 'https:') && Boolean(url.hostname)
  } catch {
    return false
  }
}

export const toCardContentRequest = (card: CardDto): CardContentRequest => ({
  type: card.type,
  title: card.title.trim(),
  url: card.url?.trim() || undefined,
  summary: card.summary?.trim() || undefined,
  content: card.content?.trim() || undefined,
  reason: card.reason?.trim() || undefined,
  coverImageUrl: card.coverImageUrl?.trim() || undefined,
  tags: card.tags.map(tag => tag.trim()),
  intervalDays: card.intervalDays,
})

export const createCardFormRules = (cardData: Ref<CardDto>): FormRules<CardDto> => ({
  type: [
    { required: true, message: '卡片類型不能為空', trigger: 'change' },
  ],
  title: [
    { required: true, message: '標題不能為空', trigger: 'blur' },
    { max: 255, message: '標題長度不能超過 255 個字元', trigger: 'blur' },
  ],
  coverImageUrl: [
    { max: 2048, message: '封面圖片網址不可超過 2048 個字元', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        callback(isHttpUrl(value) ? undefined : new Error('封面圖片網址必須是有效的 HTTP 或 HTTPS 網址'))
      },
      trigger: 'blur',
    },
  ],
  url: [
    { max: 2048, message: '來源網址不可超過 2048 個字元', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (cardData.value.type === 'link' && !value?.trim()) {
          callback(new Error('連結類卡片必須提供來源網址'))
          return
        }
        callback(isHttpUrl(value) ? undefined : new Error('來源網址必須是有效的 HTTP 或 HTTPS 網址'))
      },
      trigger: 'blur',
    },
  ],
  summary: [
    { max: 600, message: '摘要不能超過 600 個字元', trigger: 'blur' },
  ],
  reason: [
    { max: 300, message: '原因不能超過 300 個字元', trigger: 'blur' },
  ],
  tags: [
    {
      validator: (_rule, value: string[], callback) => {
        if ((value?.length ?? 0) > 10) {
          callback(new Error('每張卡片最多只能有 10 個標籤'))
          return
        }

        const normalizedTags = (value ?? []).map(tag => tag.trim())
        if (normalizedTags.some(tag => !tag || tag.length > 50)) {
          callback(new Error('標籤不可為空，且單一標籤不可超過 50 個字元'))
          return
        }

        callback(new Set(normalizedTags).size === normalizedTags.length
          ? undefined
          : new Error('標籤不可重複'))
      },
      trigger: 'change',
    },
  ],
  intervalDays: [
    { type: 'number', min: 1, max: 365, message: '回流間隔必須介於 1 到 365 天', trigger: 'change' },
  ],
})
