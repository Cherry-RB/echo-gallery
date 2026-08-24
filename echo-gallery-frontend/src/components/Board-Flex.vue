<script setup lang="ts">
defineOptions({
  name: 'BoardFlex'
})
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import CardItem from './CardItem.vue'
import type { CardDto } from '../types/card.ts';
import type { ViewMode } from '../types/card.ts';
import { cardApi } from '../utils/api/cardApi.ts';
import { useInfiniteQuery } from '@tanstack/vue-query'
import { shouldMarkReviewedOnOpenDetail, type BoardType } from '../types/board.ts';
import { useRouter } from 'vue-router';
import { useCardStatus } from '../utils/useCardStatus.ts';
import { MasonryWall } from '@yeger/vue-masonry-wall'

const props = defineProps<{
  boardType: BoardType;
  title?: string;
  description?: string;
  filters?: Record<string, unknown>;
}>();

const router = useRouter();
const { handleReadCard } = useCardStatus();
const normalizedFilters = computed(() => props.filters ?? {});

// =====================================================
// 🚀 TanStack Query 無限滾動設定
// =====================================================
const {
    data,               // 後端取得的分頁資料
    fetchNextPage,      // 自動觸發抓取下頁
    hasNextPage,        // 判斷後面還有沒有資料
    isFetchingNextPage, // 正在載入下一頁時狀態為 true
    isLoading,          // 初次載入資料時為 true
    error
} = useInfiniteQuery({
    queryKey: computed(() => ["cards", props.boardType, normalizedFilters.value]), // 給這個快取一個名字
    initialPageParam: 1, // 起始頁碼

    // 自動帶入當前頁碼進行 API 請求
    queryFn: ({ pageParam }) => cardApi.getCards({ pageNumber: pageParam, pageSize: 15, boardType: props.boardType, ...normalizedFilters.value }),

    /**
     * 判斷有無下一頁
     * @param lastPage 最後一次（最新那一頁）撈到的資料（陣列）
     * @param allPages 目前已經撈出來的所有頁面清單
     */
    getNextPageParam: (lastPage, allPages) => {
        // 如果最新這一頁撈出來剛好是 15 筆，代表「應該還有下一頁」
        if (lastPage.length === 15) {
            return allPages.length + 1
        }
        // 如果撈出來少於 15 筆（例如 5 筆、或空陣列），代表到底了，回傳 undefined 告訴套件停止
        return undefined
    }
});

// 把二維陣列壓扁，無縫接軌瀑布流
const cardList = computed<CardDto[]>(() => {
    return data.value?.pages.flatMap(page => page) || [];
})

function handleOpenDetail(card: CardDto) {
  router.push({
    name: "CardDetail",
    params: { id: card.id },
    query: { from: props.boardType },
  });

  if (shouldMarkReviewedOnOpenDetail(props.boardType)) {
    handleReadCard({
      id: card.id,
      sourceBoard: props.boardType,
    });
  }
}

// 無限推播觸發載入新內容
const loadMore = () => {
    // 如果正在抓資料，或者已經確定沒有下一頁了，就直接結束，不重複觸發
    if (isFetchingNextPage.value || !hasNextPage.value) return

    // 呼叫 TanStack Query 內建函式去抓下一頁
    fetchNextPage()
}

const viewMode = ref<ViewMode>("text");

// 新增這兩行，用來綁定底部觸發點與觀測器
const triggerRef = ref<HTMLElement | null>(null)
let observer: IntersectionObserver | null = null

onMounted(() => {

    // 新增：建立交叉觀測器，當滾動到底部 triggerRef 時觸發 loadMore
    observer = new IntersectionObserver((entries) => {
        if (entries[0].isIntersecting) {
            loadMore()
        }
    }, { rootMargin: '200px' }) // 提早 200px 載入，體驗更流暢
})

// 不管 triggerRef 何時出現／消失，都能正確跟上
watch(triggerRef, (newEl, oldEl) => {
    if (oldEl && observer) observer.unobserve(oldEl)
    if (newEl && observer) observer.observe(newEl)
})

onUnmounted(() => {
    observer?.disconnect()
})

</script>

<template>
    <!-- <el-scrollbar class="feed-scrollbar"> -->
    <!-- <div> -->
        <!-- <h1>Board</h1> -->
        <!-- <el-switch v-model="isTextMode" active-text="Text" inactive-text="Gallery" /> -->
    <!-- </div> -->

  <section>
    <header class="board-header">
      <h1 style="text-align: center;margin: 10px;">{{ title }}</h1>
      <p v-if="description" class="board-description">{{ description }}</p>
    </header>

    <div v-if="isLoading && cardList.length === 0">
      載入中...
    </div>

    <div v-else-if="error">
      載入失敗，請稍後再試
    </div>

    <div v-else-if="cardList.length === 0">
      目前沒有卡片
    </div>


    <div class="feed-container" v-else>
      <masonry-wall
      :items="cardList"
      :column-width="270"
      :gap="20">
        <template #default="{item}">
          <CardItem
          :data="item"
          :viewMode="viewMode"
          :board-type="boardType"
          @open-detail="handleOpenDetail"
          ></CardItem>
        </template>
      </masonry-wall>
    </div>
    <!-- 載入中提示 -->
    <div ref="triggerRef" class="loading-trigger">
        <p v-if="isLoading" class="loading-text">正在初始化卡片...</p>
        <p v-else-if="isFetchingNextPage" class="loading-text">更多卡片載入中...</p>
        <p v-else-if="!hasNextPage && cardList.length > 0" class="loading-text">🎉 已經看完全部卡片囉！</p>
    </div>

    <!-- 置頂按鈕 -->
    <el-backtop :right="50" :bottom="50" />

  </section>
</template>

<style scoped>
.feed-container{
    padding: 20px;
    background-color: var(--el-bg-color-page);
    min-height: 100vh;
}

/* 💡 5. 瀑布流單欄：直向排列卡片 */
.masonry-col {
    flex: 1;           /* 讓每一欄平均分配寬度 */
    display: flex;
    flex-direction: column;
    gap: 20px;         /* 卡片與卡片之間的上下間距 */
    min-width: 0;      /* 防止內容意外撐開 Flex 項目 */
}
.loading-text {
  text-align: center;
  padding: 20px;
  color: var(--el-text-color-secondary);
}
.board-description {
  margin: 15px;
  color: var(--el-text-color-secondary);
  text-align: center;
}

/* 🌟 新增：底部觀測點置中樣式 */
.loading-trigger {
    width: 100%;
    display: flex;
    justify-content: center;
}
</style>
