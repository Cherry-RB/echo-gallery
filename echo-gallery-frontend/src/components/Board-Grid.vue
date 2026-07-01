<script setup lang="ts">
import { ref } from 'vue'
import CardItem from './CardItem.vue'
import type { CardDto } from '../types/card.ts';
import { generateCards } from '../mock-data/card-mock-data.ts';
import type { ViewMode } from '../types/card.ts';

// 模擬從後端取得的資料，符合 DTO 結構
const pageNumber = ref(1);
const cardList = ref<CardDto[]>(generateCards(pageNumber.value, 15));
const loading = ref(false);

// 無限推播觸發載入新內容
const loadMore = () => {
    if (loading.value) return
    loading.value = true
    // 模擬網路延遲 0.5 秒，讓載入感更真實
    setTimeout(()=>{
        pageNumber.value++
        const newCards = generateCards(pageNumber.value, 15);
        cardList.value.push(...newCards);
        loading.value = false
    }, 500)
}

const viewMode = ref<ViewMode>("text");

// const isTextMode = computed({
//     get: () => viewMode.value === "text",
//     set: (val: boolean) => {
//         viewMode.value = val ? "text" : "gallery"
//     }
// })

</script>

<template>
    <div>
        <h1>Board</h1>
        <!-- <el-switch v-model="isTextMode" active-text="Text" inactive-text="Gallery" /> -->
    </div>

    <div class="feed-container">

        <div class="card-grid"
            v-infinite-scroll="loadMore"
            :infinite-scroll-disabled="loading"
            :infinite-scroll-distance="20">

            <CardItem
            v-for="item in cardList"
            :key="item.id"
            :data="item"
            :viewMode="viewMode"
            ></CardItem>
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

.card-grid{
    display: grid;
    align-items: start;
    /* 自動填滿：每張卡片最小 280px，最大平均分配 */
    grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
    gap: 20px;
    max-width: 1600px;
    margin: 0 auto;
}

.loading-text {
  text-align: center;
  padding: 20px;
  color: #999;

}

</style>
