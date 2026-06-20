import type { CardDto } from "../types/card"

// card-mock-data.ts 範例
export const getCardByIdFromMock = (id: string): CardDto | null => {
  // 這裡假設你本地或 window 上有一個大清單，或者根據 id 臨時生成一個
  // 最直覺的做法：直接拿你 generateCards 產出的假資料來 filter
  // 這裡示範直接現產一個，確保畫面能完美渲染
  return {
    id: id,
    type: Math.random() > 0.5 ? 'note' : 'link',
    title: `模擬卡片標題 #${id}`,
    url: 'https://github.com/vuejs/core',
    summary: '這是這張卡片的短摘要，會出現在瀑布流中...',
    content: '這是很長很長很長的詳細卡片內容...\n支援換行格式。Vue 3 的開發體驗非常棒。',
    reason: '這是我收藏這張卡片的原因，主要是因為工作上用得到。',
    showContentPreview: true,
    coverImageUrl: 'https://picsum.photos/800/400', // 隨機封面圖
    tags: ['Vue3', 'TypeScript', 'ElementPlus'],
    intervalDays: 7,
    nextShowAt: new Date().toISOString(),
    openCount: 12,
    likeCount: 5,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString()
  }
}