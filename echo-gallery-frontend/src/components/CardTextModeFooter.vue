<script setup lang="ts">

import router from '../router/index';
import { formatDate } from '../utils/formatDate'

const props = defineProps({
  data: {
    type: Object,
    required: true
  }
})

const getUrlDomain = (url:string)=>{
  try {
    const domain = new URL(url).hostname;
    return domain.replace('www.', '')
  } catch(e) {
    return url
  }
}

const getCardShowInfo = () => {
  const cardData = props.data;
  if(cardData.reason){
    return cardData.reason;
  }
  if(cardData.summary){
    return cardData.summary;
  }
  if( cardData.isShowContentPreview && cardData.content){
    return cardData.content;
  }
  return null;
}

const goToDetail = () => {
  // router.push( { name: 'CardDetail', params: { id: props.data.id }})

  const routeData = router.resolve({
    name: 'CardDetail', 
    params: { id: props.data.id }
  });
  window.open(routeData.href, "_blank");
}

const toggleStar = () => {

  const canLike = getLikeAvailableStatus(props.data.likeAvailableAt);

  if(canLike){
    const now = new Date();
    // props.data.lastLikedAt = now.toISOString(); 
    const sevenDaysLater = new Date(now.getTime() + 7 * 24 * 60 * 60 * 1000);
    props.data.likeAvailableAt = sevenDaysLater.toISOString()
    props.data.likeCount ++;
  }else{
    // props.data.lastLikedAt = null;
    props.data.likeAvailableAt = null;
    props.data.likeCount --;
  }
}

const getLikeAvailableStatus = (likeAvailableAt: string) => {
  if (!likeAvailableAt) return true;
  try {
    return new Date() >= new Date(likeAvailableAt);
  } catch (e) {
    return true;
  }
}

</script>

<template>
  <el-card @click="goToDetail" class="card-clickable">

    <!-- header -->
    <template #header>
      <div class="card-header">
        <!-- 類型標籤 -->
        <el-tag :type="data.type==='note'?'success':'primary'">{{ data.type==='note'?'筆記':'連結' }}</el-tag>
        <!-- 標題 -->
        <span class="title-text">{{ data.title }}</span>
      </div>
    </template>

    <!-- body -->
    <div class="card-body">
      <!-- 內文 -->
      <p class="card-body-content" v-if="getCardShowInfo()">{{ getCardShowInfo() }}</p>
      <!-- 標籤 -->
      <div class="tag-container">
        <el-tag v-for="tag in data.tags" type="info" size="small" effect="plain">#{{ tag }}</el-tag>
      </div>
      <!-- 下次回流 -->
      <div class="card-body-next-review-text">下次回流：{{ formatDate(data.nextShowAt) }}</div>

    </div>
    
    <!-- 底部：來源、按讚數 -->
    <template #footer>
      <div class="card-footer">
        <!-- 來源 -->
        <!-- 連結 -->
        <el-link 
          v-if="data.url" 
          :href="data.url"
          target="_blank" 
          type="primary"
          class="card-link"
          @click.stop>
          <el-icon :size="16"><Link /></el-icon>
          {{ getUrlDomain(data.url) }}
        </el-link>

        <!-- 星數 -->
        <div class="status star-clickable" @click.stop @click="toggleStar()">
          <el-icon 
            :size="16" 
            style="color: #f7ba2a;" 
            :class="{ 'active-star': !getLikeAvailableStatus(data.likeAvailableAt) }">
            <Star v-if="getLikeAvailableStatus(data.likeAvailableAt)" />
            <StarFilled v-else />
          </el-icon>
          <span class="like-count">{{ data.likeCount }}</span>
        </div>
      </div>
    </template>
  </el-card>
</template>

<style scoped>
/* 讓底部文字一左一右 */
.card-footer {
  display: flex;
  justify-content: space-between;
  color: #999;
  font-size: 12px;
  gap: 20px; /* 確保左右區塊中間至少有間距 */
}
.tag-container{
  display: flex;
  gap: 5px;
  overflow: hidden;    /* 隱藏超出寬度的標籤 */
  white-space: nowrap; /* 強制不換行（若想換行則改用 flex-wrap: wrap） */
  flex: 1;             /* 讓標籤區塊自動伸縮 */
  /* 選配：加上漸層遮罩，讓標籤末端看起來是淡出的，比較美觀 */
  mask-image: linear-gradient(to right, black 85%, transparent 100%);
  -webkit-mask-image: linear-gradient(to right, black 85%, transparent 100%);
}
/* 讓標題跟標籤有點距離 */
.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  overflow: hidden; /* 防止標題把標籤擠掉 */
}
.card-body{
  text-align: left;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.card-body-content {
  color: #666;
  font-size: 14px;
  line-height: 1.5;
  /* 即使不顯示圖片，也可以限制文字行數，讓卡片整齊 */
  /* display: -webkit-box; */
  /* -webkit-line-clamp: 3;  */
  /* -webkit-box-orient: vertical; */
  /* overflow: hidden; */
   /* 處理長單字 */
  overflow-wrap: break-word;
  /* 強制所有字符在邊界斷開 */
  /* word-break: break-all;  */
}
.card-body-next-review-text {
  color: #b5b5b5;
  font-size: 12px;
  line-height: 1;
}
.title-text{
  font-weight: 600;
  text-align: left;
  color: #333;
  /* 強制不換行 */
  /* white-space: nowrap;       */
  /* 隱藏超出部分 */
  overflow: hidden;         
  /* 超出變 ... */
  text-overflow: ellipsis;  
}

.status{
  display: flex;
  align-items: center;
  gap: 6px;
}
.card-link{
  /* text-decoration: none; */
  /* font-weight: 500; */
  max-width: 150px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  justify-content: left;
}
.card-clickable{
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}
.card-clickable:hover{
  transform: translateY(-5px);
  box-shadow: 0 5px 10px rgba(0, 0, 0, 0.4); 
}
/* --- 修改後建議 --- */
.star-clickable {
  transition: transform 0.2s ease;
  cursor: pointer;
}
.star-clickable:hover {
  transform: translateY(-5px);
}
.active-star {
  animation: pop 0.3s ease;
}
@keyframes pop {
  0% { transform: scale(1); }
  50% { transform: scale(1.4);}
  100% { transform: scale(1);}
}
</style>
