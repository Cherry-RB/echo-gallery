<script setup lang="ts">

import { computed, defineAsyncComponent, ref } from 'vue';
import { Link, Star, StarFilled, MoreFilled } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import { useCardStatus } from '../utils/useCardStatus';
import type { CardDto } from '../types/card';
import { getBoardCapabilities, type BoardType } from '../types/board';
import RecurrenceIntervalPicker from './RecurrenceIntervalPicker.vue';

const AddCardToIssueDialog = defineAsyncComponent(() => import('./work/AddCardToIssueDialog.vue'));

const props = defineProps<{
  data: CardDto;
  boardType: BoardType;
}>();

const capabilities = computed(() => getBoardCapabilities(props.boardType));
const addIssueDialogVisible = ref(false);
const recurrencePopoverVisible = ref(false);

const growthTag = computed(() => {
  if (props.data.growthStatus === 'SEED') {
    return { icon: '🌱', label: '種子' };
  }
  if (props.data.growthStatus === 'GROWING') {
    return { icon: '🌿', label: '生長' };
  }
  if (props.data.growthStatus === 'MATURE') {
    return { icon: '🌳', label: '成熟' };
  }
  return null;
});

const emit = defineEmits<{
  (e: "open-detail", card: any): void;
}>();

const {
  handleToggleStar,
  handleToggleArchive,
  handleSnoozeCard,
  handlePauseCard,
  handleResumeCard,
  handleUpdateRecurrence,
  handleUpdateGrowthStatus,
  handleDeleteCard,
  isGrowthStatusPending,
  isPausePending,
  isResumePending,
  isRecurrencePending,
  isSnoozePending,
  isArchivePending,
} = useCardStatus();

const isRecurrencePaused = computed(() =>
  props.data.intervalDays == null && props.data.nextShowAt == null,
);

const sourceLabel = computed(() => {
  return props.data.url ? getUrlDomain(props.data.url) : '';
});

const boardContextLabel = computed(() => {
  if (isRecurrencePaused.value) {
    return props.data.isArchived ? '封存前已暫停' : '已暫停回流';
  }

  const interval = props.data.intervalDays ? `每 ${props.data.intervalDays} 天` : '已設定';
  if (props.data.isArchived) return `封存前${interval}`;
  return interval;
});

const snoozeContextLabel = computed(() =>
  props.boardType === 'snoozed' ? `已延後 ${props.data.snoozeCount ?? 0} 次` : null,
);

// =====================================================
// 💡 關鍵：判定卡片是否處於「灰掉狀態 (Muted)」
// =====================================================
const isMuted= computed(() => {
  if(props.boardType == 'archived'){
    return false;
  }
  return props.data.isArchived;
});

const getUrlDomain = (url:string)=>{
  try {
    const domain = new URL(url).hostname;
    return domain.replace('www.', '')
  } catch(e) {
    return '來源連結'
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
  if(!isMuted.value){
    emit("open-detail", props.data);
  }
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

const pauseRecurrence = async () => {
  try {
    await ElMessageBox.confirm(
      '暫停後，這張卡片仍可搜尋、查看及加入議題，但不會再出現在 Today。',
      '暫停回流',
      {
        confirmButtonText: '暫停回流',
        cancelButtonText: '取消',
        type: 'warning',
      },
    );
    handlePauseCard({ id: props.data.id });
  } catch {
    // 使用者取消時不需要額外提示。
  }
};

const resumeRecurrence = async () => {
  try {
    const { value } = await ElMessageBox.prompt(
      '請設定恢復後的回流間隔（1～365 天）。',
      '恢復回流',
      {
        confirmButtonText: '恢復回流',
        cancelButtonText: '取消',
        inputValue: '10',
        inputPattern: /^(?:[1-9]|[1-9]\d|[12]\d{2}|3[0-5]\d|36[0-5])$/,
        inputErrorMessage: '請輸入 1～365 的整數',
      },
    );
    handleResumeCard({ id: props.data.id, intervalDays: Number(value) });
  } catch {
    // 使用者取消時不需要額外提示。
  }
};

const selectRecurrence = (intervalDays: number) => {
  recurrencePopoverVisible.value = false;
  handleUpdateRecurrence({ id: props.data.id, intervalDays });
};

const pauseFromPicker = () => {
  recurrencePopoverVisible.value = false;
  pauseRecurrence();
};

const markAsSeed = () => {
  handleUpdateGrowthStatus({ id: props.data.id, growthStatus: 'SEED' });
};

const getLikeAvailableStatus = (likeAvailableAt: string | undefined) => {
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

const deleteCard = async () => {
  try {
    await ElMessageBox.confirm(
      `確定要永久刪除「${props.data.title}」嗎？此操作無法復原。`,
      '刪除卡片',
      {
        confirmButtonText: '確定刪除',
        cancelButtonText: '取消',
        type: 'error',
      },
    );
    await handleDeleteCard({ id: props.data.id });
  } catch {
    // 使用者取消或刪除失敗時，由既有 mutation 統一處理提示。
  }
}

</script>

<template>
  <el-card @click="goToDetail"
  :class="[
    'card-clickable',
    { 'card-muted-style': isMuted },
    { disabled: isMuted }
  ]">

    <div v-if="isMuted" class="muted-overlay">
        <span>📥 已封存卡片</span>
    </div>

    <!-- header -->
     <div>
      <div class="card-header">
        <!-- 標題 -->
        <h4 class="title-text">{{ data.title }}</h4>
      </div>
      <div v-if="data.url" class="card-source-row">
        <el-button
          link
          class="source-link"
          :title="data.url"
          @click.stop="openSourceUrl(data.url)"
        >
          <el-icon><Link /></el-icon>
          <span>{{ sourceLabel }}</span>
        </el-button>
      </div>
    </div>

    <!-- body -->
    <div class="card-body">
      <!-- 內文 -->
      <div v-if="getCardShowInfo" class="card-preview">
        <p class="card-body-content">{{ getCardShowInfo }}</p>
      </div>
      <!-- 標籤 -->
      <div class="tag-container">
        <el-tooltip v-if="growthTag" :content="growthTag.label" placement="top">
          <el-tag
            type="info"
            size="small"
            effect="plain"
            class="growth-status-tag"
            role="img"
            :aria-label="growthTag.label"
          >
            {{ growthTag.icon }}
          </el-tag>
        </el-tooltip>
        <el-tag v-for="tag in data.tags" type="info" size="small" effect="plain" :key="tag">#{{ tag }}</el-tag>
      </div>

      <div class="card-footer">

        <div class="card-footer-side footer-actions">

        <!-- 星數 -->
        <div class="status star-clickable" v-if="capabilities.canStar" @click.stop="toggleStar">
          <el-icon
          :size="16"
          class="star-icon"
          :class="{ 'active-star': !getLikeAvailableStatus(data.likeAvailableAt) }">
          <Star v-if="getLikeAvailableStatus(data.likeAvailableAt)" />
          <StarFilled v-else />
        </el-icon>
        <span class="like-count">{{ data.likeCount }}</span>
      </div>

        </div>

        <div class="card-footer-side footer-actions">
          <el-popover
            v-if="!data.isArchived && !isRecurrencePaused"
            v-model:visible="recurrencePopoverVisible"
            trigger="click"
            placement="bottom-start"
            :width="288"
          >
            <template #reference>
              <button class="recurrence-trigger" type="button" :disabled="isRecurrencePending" @click.stop>
                ↻ {{ boardContextLabel }}
              </button>
            </template>
            <RecurrenceIntervalPicker
              :model-value="data.intervalDays"
              :loading="isRecurrencePending"
              show-pause
              @select="selectRecurrence"
              @pause="pauseFromPicker"
            />
          </el-popover>
          <button
            v-else-if="!data.isArchived"
            type="button"
            class="recurrence-trigger paused-trigger"
            :disabled="isResumePending"
            @click.stop="resumeRecurrence"
          >
            ↻ {{ boardContextLabel }}
          </button>
          <span v-else class="recurrence-static">↻ {{ boardContextLabel }}</span>
          <span v-if="snoozeContextLabel" class="recurrence-exception">{{ snoozeContextLabel }}</span>
          <el-button
            v-if="boardType === 'today'"
            size="small"
            type="primary"
            plain
            :loading="isSnoozePending"
            @click.stop="triggerSnooze"
          >
            稍後再看
          </el-button>
          <el-button
            v-if="boardType === 'archived'"
            size="small"
            type="primary"
            plain
            :loading="isArchivePending"
            @click.stop="toggleArchive"
          >
            還原
          </el-button>
          <!-- 下次回流 -->
          <span class="card-id" @click.stop>#{{ data.id }}</span>
          <!-- </div> -->
          <!-- 更多功能按鈕 -->
  <el-dropdown @click.stop>
    <el-icon :size="16" class="rotate-icon" @click.stop><MoreFilled /></el-icon>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item v-if="!data.isArchived" @click.stop="addIssueDialogVisible = true">
          加入議題
        </el-dropdown-item>
        <!-- <el-dropdown-item @click="goToDetail()">編輯</el-dropdown-item> -->
        <el-dropdown-item
          v-if="!isMuted && data.growthStatus !== 'SEED'"
          :disabled="isGrowthStatusPending"
          @click.stop="markAsSeed"
        >
          標記種子
        </el-dropdown-item>
        <el-dropdown-item v-if="!isMuted && capabilities.canSnooze && boardType !== 'today' && boardType !== 'snoozed' && !isRecurrencePaused" @click.stop="triggerSnooze">
          稍後再看
        </el-dropdown-item>
        <el-dropdown-item
          v-if="!data.isArchived"
          :disabled="isPausePending || isResumePending"
          @click.stop="isRecurrencePaused ? resumeRecurrence() : pauseRecurrence()"
        >
          {{ isRecurrencePaused ? '恢復回流' : '暫停回流' }}
        </el-dropdown-item>
        <el-dropdown-item v-if="boardType !== 'archived'" @click.stop="toggleArchive">
          {{ data.isArchived ? '還原卡片' : '封存卡片' }}
        </el-dropdown-item>
        <el-dropdown-item divided>
          <span style="color: red;" @click.stop="deleteCard">刪除</span>
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
  <AddCardToIssueDialog
    v-if="addIssueDialogVisible"
    v-model="addIssueDialogVisible"
    :card-id="data.id"
  />
</template>

<style scoped>
/* 讓底部文字一左一右 */
.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  gap: 20px; /* 確保左右區塊中間至少有間距 */
  /* margin-top: 15px; */
}
.card-footer-side{
  display: flex;
  align-items: center;
  gap: 10px
}
.card-source-row {
  display: flex;
  min-width: 0;
  margin: -5px 0 8px;
  color: var(--el-text-color-placeholder);
  font-size: 11px;
}
.card-preview {
  min-width: 0;
}
.recurrence-trigger {
  display: inline-flex;
  min-width: 0;
  align-items: center;
  gap: 3px;
  padding: 0;
  border: 0;
  background: transparent;
  color: inherit;
  font-size: 11px;
  cursor: pointer;
}
.recurrence-trigger:hover {
  color: var(--el-color-primary);
}
.recurrence-trigger:disabled {
  cursor: wait;
  opacity: 0.65;
}
.paused-trigger {
  color: var(--el-text-color-secondary);
}
.recurrence-static {
  color: var(--el-text-color-secondary);
  font-size: 11px;
  white-space: nowrap;
}
.recurrence-exception {
  color: var(--el-color-warning-dark-2);
  font-size: 11px;
  white-space: nowrap;
}
.tag-container{
  display: flex;
  max-height: 52px;
  gap: 4px;
  overflow: hidden;
  flex: 1;
  flex-wrap: wrap;
}
.growth-status-tag {
  flex: 0 0 auto;
  width: 24px;
  padding: 0;
  margin-right: 5px;
  justify-content: center;
  cursor: default;
}
/* 讓標題跟標籤有點距離 */
.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  overflow: hidden; /* 防止標題把標籤擠掉 */
}
.card-id {
  margin-left: auto;
  color: var(--el-text-color-placeholder);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  line-height: 16px;
  white-space: nowrap;
}
.card-body{
  text-align: left;
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.card-body-content {
  color: var(--el-text-color-regular);
  font-size: 14px;
  /* line-height: 1.5; */
  /* 即使不顯示圖片，也可以限制文字行數，讓卡片整齊 */
  /* display: -webkit-box; */
  /* -webkit-line-clamp: 3;  */
  /* -webkit-box-orient: vertical; */
  /* overflow: hidden; */
   /* 處理長單字 */
  overflow-wrap: break-word;
  /* 強制所有字符在邊界斷開 */
  /* word-break: break-all;  */
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 5;
  white-space: pre-wrap;
}
.source-link {
  min-width: 0;
  max-width: 100%;
  padding: 0;
  color: var(--el-text-color-placeholder);
  font-size: 13px;
}
.source-link :deep(span) {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.source-link:hover{
  color: var(--el-color-primary-light-3);
}
.rotate-icon{
  transform: rotate(90deg);
  color: var(--el-text-color-secondary);
  outline: none; /* 關鍵：移除點擊或聚焦時的黑色外框 */
}
.title-text{
  font-weight: 600;
  text-align: left;
  color: var(--el-text-color-primary);
  /* 強制不換行 */
  /* white-space: nowrap;       */
  /* 隱藏超出部分 */
  /* overflow: hidden;          */
  /* 超出變 ... */
  /* text-overflow: ellipsis;   */

  flex: 1;

  /* 🌟 限制 3 行並顯示省略號的關鍵 CSS */
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;

  /* 💡 必加：防止遇到長英文單字或連續驚嘆號（例如 !!!!!）時， */
  /* 瀏覽器不知道怎麼斷行，導致整行直接隱形或破版 */
  overflow-wrap: break-word;
  word-break: break-word;
  margin: 0 0 10px;
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
  transform: translateY(-2px);
  box-shadow: var(--el-box-shadow-lighter);
}
/* --- 修改後建議 --- */
.star-clickable {
  transition: transform 0.2s ease;
  cursor: pointer;
}
.star-clickable:hover {
  transform: translateY(-2px);
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
  filter: grayscale(90%);     /* 灰階化 */
  position: relative;
}
.muted-overlay{
  position:absolute;
  inset: 0;

  display: flex;
  align-items: center;
  justify-content: center;

  background: color-mix(in srgb, var(--el-bg-color-overlay) 62%, transparent);
  backdrop-filter: blur(1px);

  font-size: 18px;
  font-weight: bold;

  z-index: 20;

  pointer-events: none;
}
.disabled{
  cursor: default;
}
.star-icon {
  color: var(--el-color-warning);
}
@media (max-width: 420px) {
  .card-footer {
    align-items: flex-start;
    flex-direction: column;
    gap: 8px;
  }
  .footer-actions {
    width: 100%;
    justify-content: space-between;
  }
}
</style>
