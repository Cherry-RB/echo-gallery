<script setup lang="ts">
import { ArrowLeft, Link, Star, StarFilled, Calendar, CollectionTag, Clock, Plus } from '@element-plus/icons-vue'
import router from '../router';
import type { CardDto } from '../types/card';
import { computed, ref, watch } from 'vue';
import { formatDate } from '../utils/formatDate';
import { getDefaultCardData } from '../mock-data/card-default-new';
import { cardApi } from '../utils/api/cardApi';
import { useQuery } from '@tanstack/vue-query';
import { useCardStatus } from '../utils/useCardStatus';

const props = defineProps<{ id: string }>();

const goBack = () => {
  if (window.history.length > 1) {
    router.back();
  } else {
    window.close();
  }
}

// --- 💡 編輯模式核心邏輯 ---

// 💡 1. 判定當前是否為「創建模式」
const isCreateMode = computed(() => props.id === 'new' || !props.id)
// 💡 2. 如果是創建模式，預設就必須是編輯狀態
const isEditMode = ref(isCreateMode.value)

// 模擬資料取得
const cardData = ref<CardDto>(getDefaultCardData());

// =====================================================
// 🔄 【核心重構】改成用 useQuery 監聽同一個快取 Key
// =====================================================
const { data: fetchedCard, isLoading: loading } = useQuery({
  queryKey: ['card', props.id],
  queryFn: () => cardApi.getCard(props.id),
  // 💡 只有在「非創建模式」且有 id 時才發送請求
  enabled: computed(() => !isCreateMode.value && !!props.id)
});
// =====================================================

// 引入你寫好的超強基礎建設工具
const {
  handleToggleStar,
  handleToggleArchive,
  handleCreateCard,
  handleUpdateCard,
} = useCardStatus();

// 如果你喜歡用監聽的方式同步：
watch(fetchedCard, (newCard) => {
  if (newCard) cardData.value = newCard
})

// onMounted(()=>{
//   // 💡 4. 只有在「非創建模式」下，才去撈取舊資料
//   if(!isCreateMode.value){
//     fetchCard(props.id) // 只要執行，上面的 watch 就會自動幫你更新 cardData
//   }
// })

let backupData = '' // 用於存放編輯前的資料快照

// 處理動態標籤動態新增所需變數
const inputVisible = ref(false)
const newTagInputValue = ref('')

// 監聽編輯模式切換
watch(isEditMode, (newVal) => {
  if (newVal) {
    // 進入編輯模式：備份當前資料
    backupData = JSON.stringify(cardData.value)
  }
})

// 💡 5. 儲存與取消的路由導向
const handleSave = () => {

  if(isCreateMode.value){
    console.log('送出 POST API 建立新卡片', cardData.value);
    // 調用對外接口建立卡片
    handleCreateCard(cardData.value, {
      // 💡 當 useCardStatus 內部的後端成功且快取刷完後，才會觸發這個 UI 回呼
      onSuccess: () => {
        router.push('/'); // 建立成功後優雅退回首頁瀑布流
      }
    });
  } else{
    console.log('送出 PUT API 更新卡片');
    // 這裡未來可以對接後端 API 儲存修改
    // 調用對外接口更新卡片（傳入封裝好的參數物件）
    handleUpdateCard({ id: props.id, data: cardData.value }, {
      // 💡 後端儲存成功且快取重整後，才執行 UI 狀態切換
      onSuccess: () => {
        isEditMode.value = false;
      }
    });
  }
}

// 取消編輯（還原資料）
const handleCancel = () => {

  if (isCreateMode.value) {
    router.back() // 創建點取消，直接退回上一頁
  } else {
    if (backupData) {
      cardData.value = JSON.parse(backupData)
    }
    isEditMode.value = false
}
}

// 刪除標籤
const handleCloseTag = (tag: string) => {
  cardData.value.tags = cardData.value.tags?.filter(t => t !== tag) || []
}

// 顯示標籤輸入框
const showTagInput = () => {
  inputVisible.value = true
}

// 確認新增標籤
const handleTagInputConfirm = () => {
  if (newTagInputValue.value) {
    if (!cardData.value.tags) cardData.value.tags = []
    if (!cardData.value.tags.includes(newTagInputValue.value)) {
      cardData.value.tags.push(newTagInputValue.value)
    }
  }
  inputVisible.value = false
  newTagInputValue.value = ''
}
// ----------------------------

// 取得網域的輔助函式
const getUrlDomain = (url: string) => {
  try {
    const domain = new URL(url).hostname;
    return domain.replace('www.', '')
  } catch (e) {
    return url
  }
}

// 點擊星標互動
// =====================================================
// 🎯 完善互動功能 1：點擊星標（直接享用樂觀更新）
// =====================================================
const toggleStar = async () => {
  if (isEditMode.value) return // 編輯模式下停用星標點擊

  // 1. 根據當前冷卻狀態，判定這次點擊是要「點亮(true)」還是「熄滅(false)」
  // getLikeAvailableStatus 為 true 代表目前沒點過或冷卻結束(空心星星) -> 這次要點亮
  const canLike = getLikeAvailableStatus(cardData.value.likeAvailableAt);

  // 呼叫封裝好的 Mutation 丟入參數，try-catch 與 ElMessage 你的工具都幫你做好了！
  handleToggleStar({ id: props.id, starStatus: canLike });
}

// =====================================================
// 🎯 完善互動功能 2：快速切換封存狀態
// =====================================================
const toggleArchive = () => {
  if (isEditMode.value) return; // 編輯模式下交給下拉選單，非編輯模式點擊按鈕直接觸發

  // 反轉當前狀態發送
  handleToggleArchive({
    id: props.id,
    archivedStatus: !cardData.value.isArchived
  });
}

const getLikeAvailableStatus = (likeAvailableAt: string | undefined | null) => {
  if (!likeAvailableAt) return true;
  try {
    return new Date() >= new Date(likeAvailableAt);
  } catch (e) {
    return true;
  }
}

const openSourceUrl = () => {
  if (!cardData.value.url) return;
  window.open(cardData.value.url, '_blank');
}
</script>

<template>
  <div class="detail-container">

    <div class="page-header-wrapper">

      <!-- Back 按鈕 -->
      <el-page-header :icon="ArrowLeft" @back="goBack">

        <!-- 元件標題 -->
        <template #content>
          <span class="header-title">卡片詳情</span>
        </template>

        <template #extra>
          <div style="display: flex; align-items: center; gap: 12px;">

            <!-- 儲存/取消更新按鈕 -->
            <template v-if="isEditMode">
              <el-button @click="handleCancel">取消</el-button>
              <el-button type="primary" @click="handleSave">
                {{ isCreateMode ? '確認建立' : '儲存變更' }}
              </el-button>
            </template>

            <!-- 編輯模式切換按鈕 -->
            <template v-if="!isCreateMode">
              <el-button v-if="!isEditMode" @click="isEditMode = !isEditMode">
                <el-icon style="margin-right: 5px;"><Edit /></el-icon>
                編輯
              </el-button>
            </template>

          </div>
        </template>
      </el-page-header>
    </div>

    <div class="main-layout">

      <div class="left-content">
        <el-card class="detail-card">
          <template #header>
            <div class="card-header-zone">
              <div class="title-row">

                <!-- 卡片類型 -->
                <!-- 顯示/編輯狀態 -->
                <el-tag v-if="!isCreateMode" :type="cardData.type==='note'?'success':'primary'">
                  {{ cardData.type==='note'?'筆記':'連結' }}
                </el-tag>
                <!-- 創建卡片狀態 -->
                 <el-radio-group v-else v-model="cardData.type" size="small">
                  <el-radio-button label="note">筆記</el-radio-button>
                  <el-radio-button label="link">連結</el-radio-button>
                </el-radio-group>

                <h1 v-if="!isEditMode" class="main-title">{{ cardData.title }}</h1>
                <el-input
                  v-else
                  v-model="cardData.title"
                  placeholder="請輸入卡片標題"
                  size="large"
                  style="flex: 1;"
                />
              </div>

              <div class="tags-row" v-if="(cardData.tags && cardData.tags.length) || isEditMode">
                <el-tag
                  v-for="tag in cardData.tags"
                  :key="tag"
                  type="info"
                  size="small"
                  effect="plain"
                  :closable="isEditMode"
                  @close="handleCloseTag(tag)"
                >
                  #{{ tag }}
                </el-tag>

                <el-input
                  v-if="inputVisible"
                  ref="saveTagInput"
                  v-model="newTagInputValue"
                  size="small"
                  style="width: 90px;"
                  @keyup.enter="handleTagInputConfirm"
                  @blur="handleTagInputConfirm"
                />
                <el-button v-else-if="isEditMode" size="small" class="button-new-tag" @click="showTagInput">
                  <el-icon><Plus /></el-icon> 新增標籤
                </el-button>
              </div>
            </div>
          </template>

          <div class="cover-wrapper" v-if="cardData.coverImageUrl || isEditMode">
            <template v-if="!isEditMode">
              <img :src="cardData.coverImageUrl" class="cover-image" alt="Cover" />
            </template>
            <template v-else>
              <div class="cover-edit-area">
                <el-input
                  v-model="cardData.coverImageUrl"
                  placeholder="請輸入封面圖片 URL (留空則不顯示封面)"
                  clearable
                />
                <div v-if="cardData.coverImageUrl" class="cover-preview-mini">
                  <span>預覽：</span>
                  <img :src="cardData.coverImageUrl" style="max-height: 80px; border-radius: 4px;" />
                </div>
              </div>
            </template>
          </div>

          <div class="content-section">
            <div class="info-paragraph" v-if="cardData.reason || isEditMode">
              <h3 class="paragraph-title reason"><span class="title-marker reason"></span>收藏理由</h3>
              <p v-if="!isEditMode" class="paragraph-text">{{ cardData.reason }}</p>
              <el-input
                v-else
                v-model="cardData.reason"
                type="textarea"
                :rows="3"
                placeholder="請輸入這張卡片的收藏理由..."
              />
            </div>

            <div class="info-paragraph" v-if="cardData.summary || isEditMode">
              <h3 class="paragraph-title summary"><span class="title-marker summary"></span>內容摘要</h3>
              <p v-if="!isEditMode" class="paragraph-text">{{ cardData.summary }}</p>
              <el-input
                v-else
                v-model="cardData.summary"
                type="textarea"
                :rows="3"
                placeholder="請輸入內容摘要..."
              />
            </div>

            <div class="info-paragraph" v-if="cardData.type === 'note' || cardData.showContentPreview || isEditMode">
              <h3 class="paragraph-title content"><span class="title-marker content"></span>詳細內容</h3>
              <p v-if="!isEditMode" class="paragraph-text main-content">{{ cardData.content }}</p>
              <el-input
                v-else
                v-model="cardData.content"
                type="textarea"
                :rows="6"
                placeholder="請輸入詳細內容..."
              />
            </div>
          </div>
        </el-card>
      </div>

      <div class="right-sidebar">
        <el-card v-if="cardData.type === 'link'" class="sidebar-card">
          <div class="sidebar-card-header">
            <el-icon><Link /></el-icon>
            <div class="domain-text">
              <span v-if="!isEditMode">{{ cardData.url ? getUrlDomain(cardData.url) : '未設定連結' }}</span>
              <span v-else>來源連結</span>
            </div>
          </div>

          <template v-if="!isEditMode">
            <el-button class="goto-btn" :disabled="!cardData.url" @click="openSourceUrl()">
              前往來源連結
            </el-button>
          </template>
          <template v-else>
            <el-input v-model="cardData.url" placeholder="https://..." size="small" />
          </template>
        </el-card>

        <el-card class="sidebar-card">
          <div class="sidebar-card-header">
            <el-icon><Clock /></el-icon>
            <span>記憶回流設定</span>
          </div>
          <el-descriptions :column="1" border size="small" class="clean-desc">
            <el-descriptions-item label="回流頻率 (天)">

              <span v-if="!isEditMode">
                {{ cardData.intervalDays ? `${cardData.intervalDays} 天一次` : '未設定' }}
              </span>
              <el-input-number
                v-else
                v-model="cardData.intervalDays"
                :min="1"
                :max="365"
                size="small"
                controls-position="right"
                style="width: 100%;"
              />
            </el-descriptions-item>
            <el-descriptions-item label="下次看見" v-if="!isEditMode">
              <el-icon class="icon-align"><Calendar /></el-icon>
              {{ formatDate(cardData.nextShowAt || '') || '-' }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>

        <el-card class="sidebar-card status-card" v-if="!isCreateMode">
          <div class="sidebar-card-header">
            <el-icon><CollectionTag /></el-icon>
            <span>卡片狀態</span>
          </div>

          <div class="status-grid">
            <div class="grid-item">
              <span class="grid-label">累積點閱</span>
              <span class="grid-value">{{ cardData.openCount }} 次</span>
            </div>
            <div class="grid-item">
              <span class="grid-label">喜愛程度</span>
              <div class="star-interaction" :style="{ cursor: isEditMode ? 'not-allowed' : 'pointer' }" @click="toggleStar">
                <el-icon
                  :size="16"
                  style="color: #f7ba2a;"
                  :class="{ 'active-star': !getLikeAvailableStatus(cardData.likeAvailableAt) && !isEditMode }">
                  <Star v-if="getLikeAvailableStatus(cardData.likeAvailableAt)" />
                  <StarFilled v-else />
                </el-icon>
                <span class="grid-value">{{ cardData.likeCount }}</span>
              </div>
            </div>
          </div>

          <el-divider class="compact-divider" />

          <div class="info-list">
            <div class="info-item">
              <span class="info-label">使用狀態</span>
              <template v-if="!isEditMode">
                <div style="display: flex; align-items: center; gap: 8px;">
                <el-tag :type="cardData.isArchived ? 'info' : 'success'" size="small" effect="light">
                  {{ cardData.isArchived ? '已封存' : '使用中' }}
                </el-tag>
                <el-button
                  type="primary"
                  link
                  size="small"
                  @click="toggleArchive"
                >
                  {{ cardData.isArchived ? '還原使用' : '快速封存' }}
                </el-button>
                </div>
              </template>
              <template v-else>
                <el-select v-model="cardData.isArchived" size="small" style="width: 90px;">
                  <el-option :value="false" label="使用中" />
                  <el-option :value="true" label="已封存" />
                </el-select>
              </template>
            </div>
            <div class="info-item">
              <span class="info-label">最後點開</span>
              <span class="info-val">{{ formatDate(cardData.lastOpenAt || '') || '無記錄' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">最後按讚</span>
              <span class="info-val">{{ formatDate(cardData.lastLikedAt || '') || '無記錄' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">最後互動</span>
              <span class="info-val">{{ formatDate(cardData.lastInteractionAt || '') || '無記錄' }}</span>
            </div>
          </div>

          <el-divider class="compact-divider" />

          <div class="time-stamp-section">
            <div>建立於：{{ formatDate(cardData.createdAt || '') }}</div>
            <div>更新於：{{ formatDate(cardData.updatedAt || '') }}</div>
          </div>
        </el-card>
      </div>

    </div>
  </div>
</template>

<style scoped>
/* 原本的 CSS 樣式完整保留... */
.detail-container {
  padding: 20px;
  max-width: 1300px;
  /* margin: 0 auto; */
}
.page-header-wrapper { margin-bottom: 20px; }
.header-title { font-size: 18px; font-weight: 600; color: #303133; }
.main-layout { display: flex; gap: 20px; align-items: flex-start; }
.left-content { flex: 3; min-width: 0; }
.right-sidebar { flex: 1; min-width: 280px; position: sticky; top: 20px; }
.detail-card, .sidebar-card { box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05); }
.card-header-zone { display: flex; flex-direction: column; gap: 12px; }
.title-row { display: flex; align-items: center; gap: 12px; }
.main-title { font-size: 20px; font-weight: 600; color: #1d1d1f; margin: 0; line-height: 1.4; }
.tags-row { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }
.cover-wrapper { margin: -20px -20px 20px -20px; overflow: hidden; max-height: 450px; display: flex; align-items: center; justify-content: center; background-color: #f5f7fa; }
.cover-image { width: 100%; height: 100%; object-fit: cover; }
.content-section { display: flex; flex-direction: column; gap: 24px; text-align: left; }
.info-paragraph { display: flex; flex-direction: column; gap: 8px; }
.paragraph-title { font-size: 16px; font-weight: 600; color: #303133; margin: 0; display: flex; align-items: center; gap: 8px; }
.title-marker { width: 4px; height: 16px; border-radius: 2px; }
.title-marker.reason { background-color: #409eff; }
.title-marker.summary { background-color: #67c23a; }
.title-marker.content { background-color: #e6a23c; }
.paragraph-title.reason { color: #409eff; }
.paragraph-title.summary { color: #67c23a; }
.paragraph-title.content { color: #e6a23c; }
.paragraph-text { font-size: 14px; color: #606266; line-height: 1.6; margin: 0; padding: 8px 12px; background-color: #f8f9fa; border-radius: 4px; white-space: pre-wrap; }
.paragraph-text.main-content { background-color: transparent; padding: 0; color: #303133; font-size: 15px; }
.icon-align { vertical-align: middle; margin-right: 4px; color: #909399; }
.star-interaction { display: inline-flex; align-items: center; gap: 6px; user-select: none; }
.active-star { animation: pop 0.3s ease; }
@keyframes pop { 0% { transform: scale(1); } 50% { transform: scale(1.3); } 100% { transform: scale(1); } }
@media (max-width: 768px) { .main-layout { flex-direction: column; } .right-sidebar { width: 100%; position: static; } }
.sidebar-card { margin-bottom: 16px; }
.sidebar-card-header { display: flex; align-items: center; gap: 8px; font-size: 14px; font-weight: 600; color: #4a4a4a; margin-bottom: 10px; }
.domain-text { font-size: 14px; color: #909399; }
.goto-btn { width: 100%; }
.status-grid { display: flex; justify-content: space-around; text-align: center; padding: 8px 0; }
.grid-item { display: flex; flex-direction: column; gap: 8px; align-items: center; }
.grid-label { font-size: 12px; color: #909399; }
.grid-value { font-size: 18px; font-weight: bold; color: #303133; }
.compact-divider { margin: 12px 0; }
.info-list { display: flex; flex-direction: column; gap: 5px; font-size: 13px; padding: 0 4px; }
.info-item { display: flex; justify-content: space-between; align-items: center; }
.info-label { color: #606266; }
.info-val { color: #303133; font-weight: 500; }
.time-stamp-section { font-size: 11px; color: #a8abb2; text-align: center; line-height: 1.6; }
/* .header-title.active-blue { color: #409eff; } */

/* 💡 新增編輯模式微調樣式 */
.cover-edit-area {
  width: 100%;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  background-color: #f5f7fa;
}
.cover-preview-mini {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: #909399;
}
.button-new-tag {
  height: 24px;
  padding-top: 0;
  padding-bottom: 0;
}
</style>
