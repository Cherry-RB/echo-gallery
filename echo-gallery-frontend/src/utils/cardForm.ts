import type { Ref } from 'vue'
import type { FormRules } from 'element-plus'
import type { CardContentRequest, CardDto } from '../types/card'
import { getTextLength } from './textLength'

const isHttpUrl = (value?: string) => {
  if (!value) return true

  try {
    const url = new URL(value)
    return (url.protocol === 'http:' || url.protocol === 'https:') && Boolean(url.hostname)
  } catch {
    return false
  }
}

const validateTextLength = (maximum: number, message: string) =>
  (_rule: unknown, value: string, callback: (error?: Error) => void) =>
    callback(getTextLength(value) <= maximum ? undefined : new Error(message))

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
  needsProcessing: Boolean(card.needsProcessing),
})

export const createCardFormRules = (cardData: Ref<CardDto>): FormRules<CardDto> => ({
  type: [{ required: true, message: '卡片類型為必填', trigger: 'change' }],
  title: [
    { required: true, message: '標題為必填', trigger: 'blur' },
    { validator: validateTextLength(255, '標題不可超過 255 字'), trigger: 'blur' },
  ],
  coverImageUrl: [
    { max: 2048, message: '封面圖片網址不可超過 2048 個字元', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        callback(isHttpUrl(value) ? undefined : new Error('封面圖片網址僅接受 HTTP 或 HTTPS 網址'))
      },
      trigger: 'blur',
    },
  ],
  url: [
    { max: 2048, message: '來源網址不可超過 2048 個字元', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (cardData.value.type === 'link' && !value?.trim()) {
          callback(new Error('連結卡片必須提供來源網址'))
          return
        }
        callback(isHttpUrl(value) ? undefined : new Error('來源網址僅接受 HTTP 或 HTTPS 網址'))
      },
      trigger: 'blur',
    },
  ],
  summary: [{ validator: validateTextLength(600, '內容重點不可超過 600 字'), trigger: 'blur' }],
  reason: [{ validator: validateTextLength(300, '留下原因不可超過 300 字'), trigger: 'blur' }],
  tags: [
    {
      validator: (_rule, value: string[], callback) => {
        if ((value?.length ?? 0) > 10) {
          callback(new Error('每張卡片最多只能有 10 個標籤'))
          return
        }

        const normalizedTags = (value ?? []).map(tag => tag.trim())
        if (normalizedTags.some(tag => !tag || getTextLength(tag) > 50)) {
          callback(new Error('標籤不可為空，且每個標籤不可超過 50 字'))
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
    { type: 'number', min: 1, max: 365, message: '回流週期需介於 1 至 365 天', trigger: 'change' },
  ],
})
