<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import CardItem from './CardItem.vue'
import type { CardDto } from '../types/card.ts';
import type { ViewMode } from '../types/card.ts';
import { cardApi } from '../utils/api/cardApi.ts';
import { useAsync } from '../utils/api/useAsync.ts';

// 模擬從後端取得的資料，符合 DTO 結構
const pageNumber = ref(1);
// const cardList = ref<CardDto[]>(generateCards(pageNumber.value, 15));
// const loading = ref(false);
const cardList = ref<CardDto[]>([]);

// =====================================================
// // 傳入 API 函式，直接解構出需要的狀態與執行函式
const { loading, data: fetchedCards, execute: fetchCards } = useAsync(cardApi.getCards)
// =====================================================

// 如果你喜歡用監聽的方式同步：
watch(fetchedCards, (newCards) => {
  if (newCards && Array.isArray(newCards)){
    cardList.value.push(...newCards);
  }
})

// 無限推播觸發載入新內容
const loadMore = () => {
    if (loading.value) return

    // // 模擬網路延遲 0.5 秒，讓載入感更真實
    // setTimeout(()=>{
    //     pageNumber.value++
    //     const newCards = generateCards(pageNumber.value, 15);
    //     cardList.value.push(...newCards);
    //     loading.value = false
    // }, 500)

    pageNumber.value++
    fetchCards({ pageNumber: pageNumber.value, pageSize: 15});
}

const viewMode = ref<ViewMode>("text");
// const isTextMode = computed({
//     get: () => viewMode.value === "text",
//     set: (val: boolean) => {
//         viewMode.value = val ? "text" : "gallery"
//     }
// })

// 💡 1. 響應式欄數控制 (替代原本 CSS 的 auto-fill)
const columnCount = ref(4)

const updateColumnCount = () => {
    const width = window.innerWidth
    if (width < 600) {
        columnCount.value = 1 // 手機 1 欄
    } else if (width < 900) {
        columnCount.value = 2 // 平板 2 欄
    } else if (width < 1200) {
        columnCount.value = 3 // 小螢幕 3 欄
    } else {
        columnCount.value = 3 // 桌機 4 欄
    }
}

onMounted(() => {
    updateColumnCount() // 一進網頁先執行一次，決定初始欄數
    // 叫瀏覽器監聽 resize（縮放視窗）事件，只要使用者拉動視窗，就立刻執行 updateColumnCount
    window.addEventListener('resize', updateColumnCount);

    // 主動載入第一頁資料，這會讓 useAsync 的 loading 變成 true
    fetchCards({ pageNumber: pageNumber.value, pageSize: 15 })
})

onUnmounted(() => {
    // 把剛剛的監聽器拔掉，這就像離開房間要關燈，避免佔用瀏覽器記憶體
    window.removeEventListener('resize', updateColumnCount)
})

// 💡 2. 核心魔法：將扁平的陣列，依照索引依序分發到各個欄位中
const columnsData = computed(() => {
    // 建立指定欄數的空陣列，例如: [[], [], [], []]
    const cols = Array.from({ length: columnCount.value }, () => [] as CardDto[])

    // 輪流發牌給各個欄位
    cardList.value.forEach((item, index) => {
        cols[index % columnCount.value].push(item)
    })
    return cols
})

</script>

<template>
    <div>
        <h1>Board</h1>
        <!-- <el-switch v-model="isTextMode" active-text="Text" inactive-text="Gallery" /> -->
    </div>
    <div class="feed-container">
        <div
        class="masonry-wrapper"
            v-infinite-scroll="loadMore"
            :infinite-scroll-disabled="loading"
            :infinite-scroll-distance="20">
            <div v-for="(col, colIndex) in columnsData" :key="colIndex" class="masonry-col">
                <CardItem
                v-for="item in col"
                :key="item.id"
                :data="item"
                :viewMode="viewMode"
                ></CardItem>
            </div>
        </div>
        <!-- 載入中提示 -->
        <p v-if="loading" class="loading-text">載入中...</p>
    </div>

    <!-- 置頂按鈕 -->
    <el-backtop :right="50" :bottom="50" />

</template>

<style scoped>
.feed-container{
    padding: 20px;
    background-color: #f4f4f4; /* 淡淡的底色 */
    min-height: 100vh;
}
/* .card-grid{
    display: grid;
    align-items: start; */
    /* 自動填滿：每張卡片最小 280px，最大平均分配 */
    /* grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
    gap: 20px;
    max-width: 1600px;
    margin: 0 auto;
} */

/* 💡 4. 瀑布流外層：改用 Flex 橫向並排 */
.masonry-wrapper {
    display: flex;
    gap: 20px;         /* 欄與欄之間的左右間距 */
    max-width: 1600px;
    margin: 0 auto;
    align-items: flex-start; /* 關鍵：防止子元素欄位被拉到等高 */
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
  color: #999;
}
</style>
