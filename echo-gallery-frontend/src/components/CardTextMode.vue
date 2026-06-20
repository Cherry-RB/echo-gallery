<script setup lang="ts">

import router from '../router/index';
import { formatDate } from '../utils/formatDate'
import { computed } from 'vue';
import { Link, Star, StarFilled, RefreshRight, MoreFilled } from '@element-plus/icons-vue'
import { useCardStatus } from '../utils/useCardStatus';

const props = defineProps({
  data: {
    type: Object,
    required: true
  }
})

const { handleToggleStar, handleToggleArchive, handleSnoozeCard, handleReadCard } = useCardStatus();

// =====================================================
// 💡 關鍵：判定卡片是否處於「灰掉狀態 (Muted)」
// =====================================================
const isMuted= computed(() => {
  return props.data.isArchived;
});

const getUrlDomain = (url:string)=>{
  try {
    const domain = new URL(url).hostname;
    return domain.replace('www.', '')
  } catch(e) {
    return url
  }
}

const getCardShowInfo = computed(()=>{
  const cardData = props.data;
  if(cardData.reason){
    return cardData.reason;
  }
  if(cardData.summary){
    return cardData.summary;
  }
  // if( cardData.isShowContentPreview && cardData.content){
  //   return cardData.content;
  // }
  return null;
})

const goToDetail = () => {
  router.push( { name: 'CardDetail', params: { id: props.data.id }})

  // const routeData = router.resolve({
  //   name: 'CardDetail', 
  //   params: { id: props.data.id }
  // });
  // window.open(routeData.href, "_blank");
  handleReadCard({ 
    id: props.data.id, 
    intervalDays: props.data.intervalDays  // 狀態反轉
  });
}

const toggleStar = () => {
  const canLike = getLikeAvailableStatus(props.data.likeAvailableAt);

  // 觸發 mutation，將當前卡片 id 與是否為「點讚」狀態傳入
  handleToggleStar({ 
    id: props.data.id, 
    starStatus: canLike // 如果當前可以點讚，代表這次操作是加星星(true)；反之為取消(false)
  });
}

// 2. 點擊封存 / 取消封存
const toggleArchive = () => {
  handleToggleArchive({ 
    id: props.data.id, 
    archivedStatus: !props.data.isArchived  // 狀態反轉
  });
};

// 3. 點擊稍後再看
const triggerSnooze = () => {
  // 如果卡片本身有設定頻率就用它的，不然預設為 10 天
  // 後續根據使用體驗，考慮再加上直接可以客製化，讓使用者透過按鈕，決定本次回流增加天數，如：5天後再看 / 1 周後再看 / 1 個月後再看
  const days = props.data.intervalDays || 10; 
  handleSnoozeCard({ id: props.data.id, nextIntervalDays: days });
};

const getLikeAvailableStatus = (likeAvailableAt: string) => {
  if (!likeAvailableAt) return true;
  try {
    return new Date() >= new Date(likeAvailableAt);
  } catch (e) {
    return true;
  }
}

const openSourceUrl = (sourceUrl:string) => {
  window.open(sourceUrl, '_blank');

  // handleReadCard({ 
  //   id: props.data.id, 
  //   intervalDays: props.data.intervalDays  // 狀態反轉
  // });
}

</script>

<template>
  <el-card @click="goToDetail" 
  :class="['card-clickable', { 'card-muted-style': isMuted }]">

    <div v-if="isMuted" class="muted-banner">
        <span>📥 已封存卡片</span>
    </div>

    <!-- header -->
    <template #header v-if="!isMuted">
      <div class="card-header">
        <!-- 類型標籤 -->
        <el-tag :type="data.type==='note'?'success':'primary'">{{ data.type==='note'?'筆記':'連結' }}</el-tag>
        <!-- 標題 -->
        <span class="title-text">{{ data.title }}</span>
      </div>
    </template>

    <!-- body -->
    <div class="card-body" v-if="!isMuted">
      <!-- 內文 -->
      <p class="card-body-content" v-if="getCardShowInfo">{{ getCardShowInfo }}</p>
      <!-- 標籤 -->
      <div class="tag-container">
        <el-tag v-for="tag in data.tags" type="info" size="small" effect="plain" :key="tag">#{{ tag }}</el-tag>
      </div>

      <div class="card-footer">

        <div class="card-footer-side">

        <!-- 星數 -->
        <div class="status star-clickable" @click.stop="toggleStar()">
          <el-icon 
          :size="16" 
          style="color: #f7ba2a;" 
          :class="{ 'active-star': !getLikeAvailableStatus(data.likeAvailableAt) }">
          <Star v-if="getLikeAvailableStatus(data.likeAvailableAt)" />
          <StarFilled v-else />
        </el-icon>
        <span class="like-count">{{ data.likeCount }}</span>
      </div>

        <!-- 連結 -->
        <el-button v-if="data.url" @click.stop="openSourceUrl(data.url)" size="small" link class="link-btn" :title="getUrlDomain(data.url)">
          <el-icon :size="16" style="margin-right: 3px;">
            <Link />
          </el-icon>
          <!-- {{ getUrlDomain(data.url) }} -->
            連結
            <!-- (GO?GO DETAIL?ORIGINAL?) -->
        </el-button>

        </div>

        <div class="card-footer-side">
          <!-- 下次回流 -->
          <div class="next-review" @click.stop>
          <!-- <div class="card-body-next-review-text"> -->
            <el-icon :size="16"><RefreshRight /></el-icon>
            <span>{{ formatDate(data.nextShowAt, "YYYY-MM-DD") }}</span>
          </div>
          <!-- </div> -->
          <!-- 更多功能按鈕 -->
          
  <el-dropdown>
    <el-icon :size="16" class="rotate-icon" @click.stop><MoreFilled /></el-icon>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item @click="goToDetail()">編輯</el-dropdown-item>
        <el-dropdown-item v-if="!isMuted" @click.stop="triggerSnooze">
          稍後再看
        </el-dropdown-item>
        <el-dropdown-item @click.stop="toggleArchive">
          {{ data.isArchived ? '還原卡片' : '封存卡片' }}
        </el-dropdown-item>
        <el-dropdown-item divided>
          <span style="color: red;">刪除</span>
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>

        </div>


    </div>
    
    

    </div>
    
    <!-- 底部：來源、按讚數 -->
    <!-- <template #footer>
      
    </template> -->
  </el-card>
</template>

<style scoped>
/* 讓底部文字一左一右 */
.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: #999;
  font-size: 12px;
  gap: 20px; /* 確保左右區塊中間至少有間距 */
  margin-top: 15px;
}
.card-footer-side{
  display: flex;
  align-items: center;
  gap: 15px
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
.link-btn{
  color: #999;
  /* transition: transform 0.5s; */
}
.link-btn:hover{
  color: lightblue;  
}
.rotate-icon{
  transform: rotate(90deg);
  color: #999; /* 調整為適合的灰色，可依需求自行更改 */
  outline: none; /* 關鍵：移除點擊或聚焦時的黑色外框 */
}
.card-body-next-review-text {
  color: #b5b5b5;
  font-size: 12px;
  line-height: 1;
}
.next-review{
  display: flex;
  align-items: center;
  gap: 5px;
}
.title-text{
  font-weight: 600;
  text-align: left;
  color: #333;
  /* 強制不換行 */
  /* white-space: nowrap;       */
  /* 隱藏超出部分 */
  /* overflow: hidden;          */
  /* 超出變 ... */
  /* text-overflow: ellipsis;   */

  flex: 1;

  /* 🌟 限制 3 行並顯示省略號的關鍵 CSS */
  display: -webkit-box;
  -webkit-line-clamp: 3;    /* 這裡寫幾，就是限制最多幾行 */
  -webkit-box-orient: vertical;
  overflow: hidden;

  /* 💡 必加：防止遇到長英文單字或連續驚嘆號（例如 !!!!!）時， */
  /* 瀏覽器不知道怎麼斷行，導致整行直接隱形或破版 */
  overflow-wrap: break-word; 
  word-break: break-word;
}
.like-count{
  font-weight: 900;
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
/* 🎯 新增的灰掉卡片核心 CSS 樣式 */
.card-muted-style {
  opacity: 0.45;              /* 半透明度 */
  filter: grayscale(85%);     /* 灰階化 */
  position: relative;
}
</style>
