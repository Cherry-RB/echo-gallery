<script setup lang="ts">
defineOptions({ name: 'CardDetail' })

import { ArrowLeft, Calendar, Clock, CollectionTag, Edit, Link, MoreFilled, Plus, Star, StarFilled } from '@element-plus/icons-vue'
import router from '../router';
import type { CardDto, CardGrowthStatus, UpdateCardRequest } from '../types/card';
import { computed, onBeforeUnmount, onMounted, ref, toRaw, watch } from 'vue';
import { formatDate } from '../utils/formatDate';
import { getDefaultCardData } from '../mock-data/card-default-new';
import { cardApi } from '../utils/api/cardApi';
import { experimentApi } from '../utils/api/experimentApi';
import { useQuery } from '@tanstack/vue-query';
import { useCardStatus } from '../utils/useCardStatus';
import { onBeforeRouteLeave, useRoute } from 'vue-router';
import type { FormInstance } from 'element-plus';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useTags } from '../utils/composables/useTags';
import CardWorkManager from '../components/work/CardWorkManager.vue';
import { createCardFormRules, toCardContentRequest } from '../utils/cardForm';
import { cardTextFieldCopy } from '../utils/cardTextFieldCopy';
import AppDialog from '../components/AppDialog.vue';
import { cardDetailQueryKey } from '../utils/cardDetailQuery';
import RecurrenceIntervalPicker from '../components/RecurrenceIntervalPicker.vue';
import { getTextLength, trimToTextLength } from '../utils/textLength';

const props = defineProps<{ id: string }>();
const route = useRoute();

const goBack = () => {
  // 有瀏覽紀錄（正常從看板點進來）→ 走原本的 back，體驗最自然（會保留捲動位置等）
  if (window.history.state?.back) {
    router.back();
    return;
  }
  // 從議題素材直接開啟或重新整理時，優先回到來源議題。
  const fromWork = route.query.fromWork;
  if (typeof fromWork === 'string' && fromWork) {
    router.push({ name: 'WorkDetail', params: { id: fromWork } });
    return;
  }
  // 沒有紀錄（重新整理 / 外部連結進入）→ 退回到 query 記錄的來源看板，沒有就給預設值
  const fallbackBoard = (route.query.from as string) || 'all';
  router.push(`/board/${fallbackBoard}`);
};

// --- 💡 編輯模式核心邏輯 ---

// 💡 1. 判定當前是否為「創建模式」
const isCreateMode = computed(() => props.id === 'new' || !props.id)
// 💡 2. 如果是創建模式，預設就必須是編輯狀態
const isEditMode = ref(isCreateMode.value)
const isClosingAfterSave = ref(false)
type EditSection = 'all' | 'basic' | 'reason' | 'summary' | 'content' | 'source'
const editSection = ref<EditSection>('all')

// 模擬資料取得
const cardData = ref<CardDto>(getDefaultCardData());

const growthStatusOptions: Array<{
  value: CardGrowthStatus;
  label: string;
  tagType: 'info' | 'success' | 'warning' | 'primary';
}> = [
  { value: 'UNMARKED', label: '未標記', tagType: 'info' },
  { value: 'SEED', label: '🌱 種子', tagType: 'success' },
  { value: 'GROWING', label: '🌿 生長', tagType: 'warning' },
  { value: 'MATURE', label: '🌳 成熟', tagType: 'primary' }
];

const currentGrowthStatus = computed(() =>
  growthStatusOptions.find(option => option.value === cardData.value.growthStatus)
    ?? growthStatusOptions[0]
);

// 監聽路由的 id 變動，當從詳情頁切換到 'new' (新增模式) 時，強制重置表單與狀態
watch(() => props.id, (newId) => {
  const createMode = newId === 'new' || !newId;
  isEditMode.value = createMode;
  if (createMode) {
    cardData.value = getDefaultCardData(); // 重置為空白的新卡片預設值
    cardFormRef.value?.clearValidate();
  }
});

// =====================================================
// 🔄 【核心重構】改成用 useQuery 監聽同一個快取 Key
// =====================================================
const { data: fetchedCard, isLoading, isError, refetch } = useQuery({
  queryKey: computed(() => cardDetailQueryKey(props.id)),
  queryFn: () => cardApi.getCard(props.id),
  // 💡 只有在「非創建模式」且有 id 時才發送請求
  enabled: computed(() => !isCreateMode.value && !!props.id),
  refetchOnWindowFocus: false,
});
const { data: cardExperimentContext } = useQuery({
  queryKey: computed(() => ['card-experiment-context', props.id]),
  queryFn: () => experimentApi.getCardExperimentContext(props.id),
  enabled: computed(() => !isCreateMode.value && !!props.id),
  refetchOnWindowFocus: false,
});
const cardExperiments = computed(() => cardExperimentContext.value?.experiments ?? []);
const cardRelations = computed(() => cardExperimentContext.value?.relations ?? []);

const updateProcessingStatus = async (needsProcessing: boolean) => {
  const updated = await cardApi.updateProcessingStatus(props.id, needsProcessing);
  cardData.value.needsProcessing = updated.needsProcessing;
  ElMessage.success(needsProcessing ? '已標記為待整理' : '已完成待整理標記');
};
// =====================================================

const {
  handleToggleStar,
  handleToggleArchive,
  handlePauseCard,
  handleResumeCard,
  handleUpdateRecurrence,
  handleUpdateGrowthStatus,
  handleCreateCard,
  handleUpdateCard,
  handleDeleteCard,
  isArchivePending,
  isStarPending,
  isPausePending,
  isResumePending,
  isRecurrencePending,
  isCreatePending,
  isUpdatePending,
  isDeletePending,
  isGrowthStatusPending
} = useCardStatus();

const isRecurrencePaused = computed(() =>
  cardData.value.intervalDays === null && cardData.value.nextShowAt === null
);

const selectDraftRecurrence = (intervalDays: number) => {
  cardData.value.intervalDays = intervalDays;
};

const updateRecurrence = (intervalDays: number) => {
  if (isRecurrencePaused.value || isRecurrencePending.value) return;
  handleUpdateRecurrence({ id: props.id, intervalDays });
};

// 如果你喜歡用監聽的方式同步：
watch(fetchedCard, (newCard) => {
  if (newCard){
    cardData.value = structuredClone(toRaw(newCard)) // 避免唯獨模式 cardData.value = newCard
  }
}, {immediate: true})

let backupData = '' // 用於存放編輯前的資料快照

const editDialogTitle = computed(() => ({
  all: '編輯卡片',
  basic: '編輯標題與標籤',
  reason: `編輯${cardTextFieldCopy.reason.label}`,
  summary: `編輯${cardTextFieldCopy.summary.label}`,
  content: `編輯${cardTextFieldCopy.content.label}`,
  source: '編輯來源資訊',
})[editSection.value]);

const hasUnsavedEdit = computed(() => Boolean(backupData) && backupData !== JSON.stringify(cardData.value));

const openEditDialog = (section: EditSection = 'all') => {
  editSection.value = section;
  isClosingAfterSave.value = false;
  backupData = JSON.stringify(cardData.value);
  isEditMode.value = true;
};

// 監聽編輯模式切換
watch(isEditMode, (newVal) => {
  if (newVal) {
    // 進入編輯模式：備份當前資料
    backupData = JSON.stringify(cardData.value)
  }
})

// 💡 5. 儲存與取消的路由導向
const handleSave = async () => {
  if (!cardFormRef.value) return;

  if (!isCreateMode.value && cardData.value.intervalDays === null && cardData.value.nextShowAt !== null) {
    ElMessage.warning('若要停止排程，請先取消編輯並使用「暫停回流」');
    return;
  }

  // 執行前端欄位校驗
  await cardFormRef.value.validate(async (valid) => {
    if (!valid) {
      return; // 校驗失敗會自動顯示紅字提示，直接中斷不送出
    }

    if(isCreateMode.value){
      console.log('送出 POST API 建立新卡片', cardData.value);
      // 調用對外接口建立卡片
      handleCreateCard(toCardContentRequest(cardData.value), {
        // 💡 當 useCardStatus 內部的後端成功且快取刷完後，才會觸發這個 UI 回呼
        onSuccess: () => {
          router.push('/board/all') // 成功後回總卡片列表，可看見最新新增的卡片
        }
      });
    } else{
      console.log('送出 PUT API 更新卡片');
      // 調用對外接口更新卡片（傳入封裝好的參數物件）
      const request: UpdateCardRequest = {
        ...toCardContentRequest(cardData.value),
        isArchived: cardData.value.isArchived,
        growthStatus: cardData.value.growthStatus
      };
      handleUpdateCard({ id: props.id, data: request }, {
        // 💡 後端儲存成功且快取重整後，才執行 UI 狀態切換
        onSuccess: () => {
          backupData = JSON.stringify(cardData.value);
          isClosingAfterSave.value = true;
          isEditMode.value = false;
        }
      });
    }
  });
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

const requestCancelEdit = async () => {
  if (isUpdatePending.value) return;
  if (!hasUnsavedEdit.value) {
    handleCancel();
    return;
  }

  try {
    await ElMessageBox.confirm(
      '尚有未儲存的卡片內容，確定要放棄嗎？',
      '放棄編輯',
      { confirmButtonText: '放棄內容', cancelButtonText: '繼續編輯', type: 'warning' },
    );
    handleCancel();
  } catch {
    // 使用者選擇繼續編輯時維持對話框開啟。
  }
};

const handleEditDialogVisibilityChange = (visible: boolean) => {
  if (visible) {
    isEditMode.value = true;
    return;
  }

  if (isClosingAfterSave.value) {
    isClosingAfterSave.value = false;
    return;
  }

  requestCancelEdit();
};

const limitTextLength = (field: 'title' | 'reason' | 'summary', maximum: number) => {
  const value = cardData.value[field] ?? '';
  cardData.value[field] = trimToTextLength(value, maximum);
};

onBeforeRouteLeave(async () => {
  if (isCreateMode.value || !isEditMode.value || !hasUnsavedEdit.value) return true;
  try {
    await ElMessageBox.confirm(
      '尚有未儲存的卡片內容，確定要離開嗎？',
      '離開編輯',
      { confirmButtonText: '放棄並離開', cancelButtonText: '繼續編輯', type: 'warning' },
    );
    return true;
  } catch {
    return false;
  }
});

const warnBeforeUnload = (event: BeforeUnloadEvent) => {
  if (isCreateMode.value || !isEditMode.value || !hasUnsavedEdit.value) return;
  event.preventDefault();
  event.returnValue = '';
};

onMounted(() => window.addEventListener('beforeunload', warnBeforeUnload));
onBeforeUnmount(() => window.removeEventListener('beforeunload', warnBeforeUnload));

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
const toggleStar = async () => {
  if (isEditMode.value) return // 編輯模式下停用星標點擊

  // 1. 根據當前冷卻狀態，判定這次點擊是要「點亮(true)」還是「熄滅(false)」
  // getLikeAvailableStatus 為 true 代表目前沒點過或冷卻結束(空心星星) -> 這次要點亮
  const canLike = getLikeAvailableStatus(cardData.value.likeAvailableAt);
  handleToggleStar({ id: props.id, starStatus: canLike });
}

// 快速切換封存狀態
const toggleArchive = () => {
  if (isEditMode.value || isArchivePending.value) return;
  // 反轉當前狀態發送
  handleToggleArchive({
    id: props.id,
    archivedStatus: !cardData.value.isArchived
  });
}

const changeGrowthStatus = (growthStatus: CardGrowthStatus) => {
  if (isGrowthStatusPending.value || growthStatus === cardData.value.growthStatus) return;
  handleUpdateGrowthStatus({ id: props.id, growthStatus });
};

const handleMoreCommand = (command: 'archive' | 'delete') => {
  if (command === 'archive') {
    toggleArchive();
    return;
  }
  deleteCard();
};

const pauseRecurrence = async () => {
  if (isEditMode.value || isPausePending.value) return;

  try {
    await ElMessageBox.confirm(
      '暫停後，這張卡片仍可搜尋、查看及加入議題，但不會再出現在 Today。',
      '暫停回流',
      {
        confirmButtonText: '確認暫停',
        cancelButtonText: '取消',
        type: 'warning'
      }
    );
    handlePauseCard({ id: props.id });
  } catch {
    // 使用者取消操作時不需顯示錯誤。
  }
}

const resumeRecurrence = async () => {
  if (isEditMode.value || isResumePending.value) return;

  try {
    const { value } = await ElMessageBox.prompt(
      '請設定新的回流間隔；排程會從今天起重新計算。',
      '恢復回流',
      {
        confirmButtonText: '確認恢復',
        cancelButtonText: '取消',
        inputValue: '10',
        inputPlaceholder: '1–365 天',
        inputValidator: input => {
          const days = Number(input);
          return Number.isInteger(days) && days >= 1 && days <= 365
            ? true
            : '回流間隔必須介於 1 到 365 天';
        }
      }
    );
    handleResumeCard({ id: props.id, intervalDays: Number(value) });
  } catch {
    // 使用者取消操作時不需顯示錯誤。
  }
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

// 建立表單參照
const cardFormRef = ref<FormInstance>();

const rules = createCardFormRules(cardData);

const deleteCard = async () => {
  if (isDeletePending.value) return;
  try {
    await ElMessageBox.confirm(
      `確定要刪除「${cardData.value.title}」嗎？刪除後無法復原。`,
      '刪除卡片',
      { confirmButtonText: '刪除卡片', cancelButtonText: '取消', type: 'warning' },
    );
    await handleDeleteCard({ id: props.id });
    goBack();
  } catch {
    // 使用者取消刪除或請求失敗時留在目前頁面。
  }
}

const {
  tagPopoverVisible,
    tagSearchQuery,
    filteredExistingTags,
    handleToggleSelectTag,
    handleCloseTag,
    handleConfirmAddTag
} = useTags(cardData)

</script>

<template>
  <div class="detail-container">
    <template v-if="!isCreateMode">
      <section class="card-detail-experience">
        <header class="detail-navigation">
          <el-button :icon="ArrowLeft" text @click="goBack">返回</el-button>

          <div v-if="fetchedCard" class="detail-navigation-actions">
            <el-tag :type="cardData.isArchived ? 'info' : 'success'" effect="light">
              {{ cardData.isArchived ? '已封存' : '使用中' }}
            </el-tag>
            <el-button type="primary" plain :icon="Edit" @click="openEditDialog('all')">
              編輯卡片
            </el-button>
            <el-dropdown trigger="click" @command="handleMoreCommand">
              <el-button :icon="MoreFilled" aria-label="更多卡片操作" />
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="archive" :disabled="isArchivePending">
                    {{ cardData.isArchived ? '還原使用' : '封存卡片' }}
                  </el-dropdown-item>
                  <el-dropdown-item command="delete" divided class="danger-menu-item">
                    刪除卡片
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </header>

        <div v-if="isLoading" class="detail-state" aria-label="卡片詳情載入中">
          <el-skeleton :rows="8" animated />
        </div>

        <el-result
          v-else-if="isError"
          class="detail-state"
          icon="error"
          title="無法載入卡片"
          sub-title="卡片可能不存在，或目前無法連線"
        >
          <template #extra>
            <el-button @click="goBack">返回</el-button>
            <el-button type="primary" @click="refetch()">重新載入</el-button>
          </template>
        </el-result>

        <div v-else-if="fetchedCard" class="reading-layout">
          <main class="card-reading-panel" aria-label="卡片內容">
            <article>
              <header class="card-reading-heading">
                <div class="card-identity-line">
                  <el-tag :type="cardData.type === 'note' ? 'success' : 'primary'" effect="light">
                    {{ cardData.type === 'note' ? '筆記' : '連結' }}
                  </el-tag>
                  <span class="detail-card-id">#{{ cardData.id }}</span>
                </div>
                <div class="title-with-action">
                  <h1>{{ cardData.title }}</h1>
                  <button type="button" class="inline-edit-button" @click="openEditDialog('basic')">編輯</button>
                </div>
                <div v-if="cardData.tags.length" class="reading-tags" aria-label="卡片標籤">
                  <el-tag v-for="tag in cardData.tags" :key="tag" size="small" type="info" effect="plain">
                    #{{ tag }}
                  </el-tag>
                </div>
              </header>

              <img
                v-if="cardData.coverImageUrl"
                :src="cardData.coverImageUrl"
                class="reading-cover"
                alt="卡片封面"
              />

              <section class="reading-section prominent-section" :aria-labelledby="`reason-${cardData.id}`">
                <header class="section-heading">
                  <div>
                    <h2 :id="`reason-${cardData.id}`">{{ cardTextFieldCopy.reason.label }}</h2>
                    <p>重新看見這張卡片時，先回到當初留下它的原因。</p>
                  </div>
                  <button type="button" class="inline-edit-button" @click="openEditDialog('reason')">編輯</button>
                </header>
                <p v-if="cardData.reason" class="reading-copy prominent-copy">{{ cardData.reason }}</p>
                <div v-else class="quiet-empty-state">
                  <p>還沒有留下收藏理由。</p>
                  <button type="button" class="inline-edit-button" @click="openEditDialog('reason')">補上理由</button>
                </div>
              </section>

              <section class="reading-section" :aria-labelledby="`summary-${cardData.id}`">
                <header class="section-heading">
                  <div>
                    <h2 :id="`summary-${cardData.id}`">{{ cardTextFieldCopy.summary.label }}</h2>
                    <p>用較短的篇幅保留這張卡片最值得記住的部分。</p>
                  </div>
                  <button type="button" class="inline-edit-button" @click="openEditDialog('summary')">編輯</button>
                </header>
                <p v-if="cardData.summary" class="reading-copy">{{ cardData.summary }}</p>
                <div v-else class="quiet-empty-state">
                  <p>還沒有摘要；需要快速回顧時再補充即可。</p>
                </div>
              </section>

              <section class="reading-section" :aria-labelledby="`content-${cardData.id}`">
                <header class="section-heading">
                  <div>
                    <h2 :id="`content-${cardData.id}`">{{ cardTextFieldCopy.content.label }}</h2>
                    <p>完整保存自己的筆記、摘錄或延伸想法。</p>
                  </div>
                  <button type="button" class="inline-edit-button" @click="openEditDialog('content')">編輯</button>
                </header>
                <p v-if="cardData.content" class="reading-copy main-copy">{{ cardData.content }}</p>
                <div v-else class="quiet-empty-state">
                  <p>尚未補充詳細內容。</p>
                  <button type="button" class="inline-edit-button" @click="openEditDialog('content')">開始補充</button>
                </div>
              </section>
            </article>
          </main>

          <aside class="card-properties-panel" aria-label="卡片管理資訊">
            <section v-if="cardData.type === 'link'" class="property-card source-property-card">
              <header class="property-heading">
                <div>
                  <span class="property-eyebrow">來源</span>
                  <h2>{{ cardData.url ? getUrlDomain(cardData.url) : '尚未設定來源' }}</h2>
                </div>
                <button type="button" class="inline-edit-button" @click="openEditDialog('source')">編輯</button>
              </header>
              <el-button type="primary" class="full-width-action" :disabled="!cardData.url" @click="openSourceUrl">
                開啟來源連結
              </el-button>
            </section>

            <section class="property-card">
              <header class="property-heading">
                <div>
                  <span class="property-eyebrow">回流安排</span>
                  <h2>{{ isRecurrencePaused ? '目前已暫停' : `每 ${cardData.intervalDays} 天回流` }}</h2>
                </div>
                <el-tag :type="isRecurrencePaused ? 'info' : 'success'" size="small" effect="light">
                  {{ isRecurrencePaused ? '已暫停' : '回流中' }}
                </el-tag>
              </header>
              <p class="property-description">
                {{ isRecurrencePaused
                  ? '暫停期間不會出現在 Today。'
                  : `下次看見：${formatDate(cardData.nextShowAt || '') || '尚未排定'}` }}
              </p>
              <div class="property-actions">
                <el-button
                  v-if="isRecurrencePaused"
                  type="primary"
                  plain
                  :loading="isResumePending"
                  @click="resumeRecurrence"
                >恢復回流</el-button>
                <template v-else>
                  <el-popover placement="bottom-end" :width="330" trigger="click">
                    <template #reference>
                      <el-button :disabled="cardData.isArchived" :loading="isRecurrencePending">調整週期</el-button>
                    </template>
                    <RecurrenceIntervalPicker
                      :model-value="cardData.intervalDays"
                      :loading="isRecurrencePending"
                      @select="updateRecurrence"
                    />
                  </el-popover>
                  <el-button text :loading="isPausePending" @click="pauseRecurrence">暫停回流</el-button>
                </template>
              </div>
              <p v-if="cardData.isArchived && !isRecurrencePaused" class="property-hint">還原卡片後才能調整回流週期。</p>
            </section>

            <section class="property-card">
              <header class="property-heading">
                <div>
                  <span class="property-eyebrow">卡片狀態</span>
                  <h2>整理與使用</h2>
                </div>
              </header>
              <dl class="property-list">
                <div>
                  <dt>成長狀態</dt>
                  <dd>
                    <el-select
                      :model-value="cardData.growthStatus"
                      class="growth-status-select"
                      size="small"
                      :loading="isGrowthStatusPending"
                      @change="changeGrowthStatus"
                    >
                      <el-option
                        v-for="option in growthStatusOptions"
                        :key="option.value"
                        :label="option.label"
                        :value="option.value"
                      />
                    </el-select>
                  </dd>
                </div>
                <div>
                  <dt>使用狀態</dt>
                  <dd>{{ cardData.isArchived ? '已封存' : '使用中' }}</dd>
                </div>
                <div>
                  <dt>整理狀態</dt>
                  <dd>
                    <el-switch
                      :model-value="cardData.needsProcessing"
                      active-text="待整理"
                      inactive-text="已完成"
                      @change="updateProcessingStatus(Boolean($event))"
                    />
                  </dd>
                </div>
                <div>
                  <dt>喜愛程度</dt>
                  <dd>
                    <button
                      type="button"
                      class="star-button"
                      :disabled="isStarPending"
                      @click="toggleStar"
                    >
                      <el-icon class="star-icon">
                        <Star v-if="getLikeAvailableStatus(cardData.likeAvailableAt)" />
                        <StarFilled v-else />
                      </el-icon>
                      {{ cardData.likeCount }}
                    </button>
                  </dd>
                </div>
              </dl>
            </section>

            <CardWorkManager :card-id="props.id" />

            <section class="property-card experiment-context-card">
              <header class="property-heading">
                <div>
                  <span class="property-eyebrow">實驗場</span>
                  <h2>實驗主題與思想脈絡</h2>
                </div>
                <el-button text size="small" @click="router.push('/experiments')">前往實驗場</el-button>
              </header>
              <div class="experiment-context-section">
                <h3>所在實驗主題</h3>
                <p v-if="!cardExperiments.length" class="property-hint">尚未加入任何實驗主題。</p>
                <div v-else class="experiment-context-list">
                  <button v-for="experiment in cardExperiments" :key="experiment.experimentId" type="button" class="experiment-context-item" @click="router.push(`/experiments/${experiment.experimentId}`)">
                    <span>{{ experiment.stage === 'SEED' ? '🌱' : experiment.stage === 'GROWING' ? '🌿' : '🌳' }} {{ experiment.experimentTitle }}</span>
                    <small v-if="experiment.experimentHypothesis">{{ experiment.experimentHypothesis }}</small>
                    <small v-if="experiment.note">備註：{{ experiment.note }}</small>
                    <el-tag v-if="experiment.experimentArchived" type="info" size="small">已封存</el-tag>
                  </button>
                </div>
              </div>
              <div class="experiment-context-section lineage-section">
                <h3>由此長出／長自</h3>
                <p v-if="!cardRelations.length" class="property-hint">尚未留下衍生關係。</p>
                <div v-else class="experiment-context-list">
                  <button v-for="relation in cardRelations" :key="relation.id" type="button" class="experiment-context-item" @click="router.push(`/card/${relation.sourceCard.cardId === Number(props.id) ? relation.derivedCard.cardId : relation.sourceCard.cardId}`)">
                    <span>{{ relation.sourceCard.cardId === Number(props.id) ? '由此長出：' : '長自：' }} {{ relation.sourceCard.cardId === Number(props.id) ? relation.derivedCard.cardTitle : relation.sourceCard.cardTitle }}</span>
                    <small>{{ relation.experimentTitle }}</small>
                  </button>
                </div>
              </div>
            </section>

            <section class="system-metadata" aria-label="卡片系統資訊">
              <span>累積點閱 {{ cardData.openCount }} 次</span>
              <span>建立於 {{ formatDate(cardData.createdAt || '') }}</span>
              <span>更新於 {{ formatDate(cardData.updatedAt || '') }}</span>
            </section>
          </aside>
        </div>
      </section>

      <AppDialog
        :model-value="isEditMode"
        :title="editDialogTitle"
        width="min(820px, calc(100vw - 32px))"
        class="card-edit-dialog"
        append-to-body
        destroy-on-close
        :close-on-click-modal="false"
        :close-on-press-escape="false"
        @update:model-value="handleEditDialogVisibilityChange"
      >
        <el-form
          ref="cardFormRef"
          :model="cardData"
          :rules="rules"
          label-position="top"
          @submit.prevent="handleSave"
        >
          <template v-if="editSection === 'all' || editSection === 'basic'">
            <div class="readonly-type-row">
              <span>卡片類型</span>
              <el-tag :type="cardData.type === 'note' ? 'success' : 'primary'">
                {{ cardData.type === 'note' ? '筆記' : '連結' }}
              </el-tag>
            </div>
            <el-form-item label="標題" prop="title">
              <el-input v-model="cardData.title" @update:model-value="limitTextLength('title', 255)" />
              <div class="word-count-hint" :class="{ 'near-limit': getTextLength(cardData.title) >= 230 }">字數：{{ getTextLength(cardData.title) }} / 255</div>
            </el-form-item>
            <el-form-item label="標籤" prop="tags" class="tags-field">
              <div class="tag-editor">
                <el-tag
                  v-for="tag in cardData.tags"
                  :key="tag"
                  type="info"
                  size="small"
                  effect="plain"
                  closable
                  @close="handleCloseTag(tag)"
                >#{{ tag }}</el-tag>
                <el-popover v-model:visible="tagPopoverVisible" placement="bottom-start" :width="280" trigger="click">
                  <template #reference>
                    <el-button size="small" class="button-new-tag"><el-icon><Plus /></el-icon> 新增標籤</el-button>
                  </template>
                  <div class="tag-popover-content">
                    <div class="tag-input-group">
                      <el-input v-model="tagSearchQuery" placeholder="加上標籤或搜尋..." size="small" clearable @keyup.enter="handleConfirmAddTag" />
                      <el-button type="primary" size="small" @click="handleConfirmAddTag">新增</el-button>
                    </div>
                    <div class="existing-tags-section">
                      <div class="popover-subtitle">既有標籤（點選切換）</div>
                      <div class="popover-tags-list">
                        <el-tag
                          v-for="tag in filteredExistingTags"
                          :key="tag.id"
                          size="small"
                          :effect="cardData.tags.includes(tag.name) ? 'dark' : 'plain'"
                          class="clickable-popover-tag"
                          @click="handleToggleSelectTag(tag.name)"
                        >{{ tag.name }}</el-tag>
                        <span v-if="filteredExistingTags.length === 0" class="no-tag-tip">尚無符合的既有標籤</span>
                      </div>
                    </div>
                  </div>
                </el-popover>
              </div>
            </el-form-item>
          </template>

          <el-form-item
            v-if="editSection === 'all' || editSection === 'reason'"
            :label="cardTextFieldCopy.reason.label"
            prop="reason"
          >
            <el-input v-model="cardData.reason" type="textarea" :rows="3" :placeholder="cardTextFieldCopy.reason.placeholder" @update:model-value="limitTextLength('reason', 300)" />
            <div class="word-count-hint" :class="{ 'near-limit': getTextLength(cardData.reason) >= 270 }">字數：{{ getTextLength(cardData.reason) }} / 300</div>
          </el-form-item>

          <el-form-item
            v-if="editSection === 'all' || editSection === 'summary'"
            :label="cardTextFieldCopy.summary.label"
            prop="summary"
          >
            <el-input v-model="cardData.summary" type="textarea" :rows="5" :placeholder="cardTextFieldCopy.summary.placeholder" @update:model-value="limitTextLength('summary', 600)" />
            <div class="word-count-hint" :class="{ 'near-limit': getTextLength(cardData.summary) >= 540 }">字數：{{ getTextLength(cardData.summary) }} / 600</div>
          </el-form-item>

          <el-form-item
            v-if="editSection === 'all' || editSection === 'content'"
            :label="cardTextFieldCopy.content.label"
            prop="content"
          >
            <el-input v-model="cardData.content" type="textarea" :rows="12" :placeholder="cardTextFieldCopy.content.placeholder" />
            <div class="word-count-hint">總字數：{{ cardData.content?.length || 0 }} 字</div>
          </el-form-item>

          <template v-if="editSection === 'all' || editSection === 'source'">
            <el-form-item v-if="cardData.type === 'link'" label="來源連結" prop="url">
              <el-input v-model="cardData.url" placeholder="https://..." clearable />
            </el-form-item>
            <el-form-item label="封面圖片來源連結" prop="coverImageUrl">
              <el-input v-model="cardData.coverImageUrl" placeholder="https://..." clearable />
            </el-form-item>
          </template>
        </el-form>

        <template #footer>
          <el-button :disabled="isUpdatePending" @click="requestCancelEdit">取消</el-button>
          <el-button type="primary" :loading="isUpdatePending" @click="handleSave">儲存變更</el-button>
        </template>
      </AppDialog>
    </template>

<template v-else>

    <div v-if="isLoading">
      載入中...
    </div>

    <div v-else-if="isError">
      載入失敗
    </div>

    <div class="page-header-wrapper">
      <!-- 新增模式 -->
      <div v-if="isCreateMode" class="create-header">
        <span class="header-title">新增卡片</span>
        <div class="page-header-inner">
          <el-button type="primary" :loading="isCreatePending" @click="handleSave">確認建立</el-button>
        </div>
      </div>

      <!-- 詳情與編輯模式 -->
      <el-page-header v-else :icon="ArrowLeft" @back="goBack">
        <template #content>
          <span class="header-title">卡片詳情</span>
        </template>

        <template #extra>
          <div class="page-header-inner">
            <!-- 儲存/取消更新按鈕 -->
            <template v-if="isEditMode">
              <el-button @click="handleCancel">取消</el-button>
              <el-button type="primary" @click="handleSave">儲存變更</el-button>
            </template>

            <!-- 編輯模式切換按鈕 -->
            <template v-if="!isEditMode">
              <el-button @click="deleteCard" type="danger" plain>刪除</el-button>
              <el-button @click="isEditMode = true" type="primary" plain>
                <el-icon style="margin-right: 5px;"><Edit /></el-icon>
                編輯
              </el-button>
            </template>
          </div>
        </template>
      </el-page-header>
    </div>

    <!-- 外層包上 el-form 並綁定 ref、model、rules -->
    <el-form :model="cardData" :rules="rules" ref="cardFormRef" class="detail-main-layout" @submit.prevent>

      <div class="left-content">
        <el-card class="detail-card">
          <template #header>
            <div class="card-header-zone">
                <!-- 卡片類型 -->
                <!-- 顯示/編輯狀態 -->
                <div class="card-identity-row">
                  <el-form-item prop="type" style="margin-bottom: 0;">
                    <el-tag v-if="!isCreateMode" :type="cardData.type==='note'?'success':'primary'">
                      {{ cardData.type==='note'?'筆記':'連結' }}
                    </el-tag>
                    <!-- 創建卡片狀態 -->
                    <el-radio-group v-else v-model="cardData.type" size="small">
                      <el-radio-button label="note">筆記</el-radio-button>
                      <el-radio-button label="link">連結</el-radio-button>
                    </el-radio-group>
                  </el-form-item>
                  <span v-if="!isCreateMode" class="detail-card-id">#{{ cardData.id }}</span>
                </div>

                <el-form-item prop="title" style="flex: 1; margin-bottom: 0;">
                  <h1 v-if="!isEditMode" class="main-title">{{ cardData.title }}</h1>
                  <el-input
                    v-else
                    v-model="cardData.title"
                    placeholder="請輸入卡片標題"
                    size="large"
                  />
                  <div v-if="isEditMode" class="word-count-hint" :class="{ 'over-limit': (cardData.title?.length || 0) > 255 }">總字數：
                      {{ cardData.title?.length || 0 }} / 255
                    </div>
                </el-form-item>

                <el-form-item prop="tags" class="tags-row" v-if="(cardData.tags && cardData.tags.length) || isEditMode">
                <!-- 已選取的標籤呈現 -->
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

                <!-- 標籤 Popover 選單 (僅在編輯模式顯示) -->
                <el-popover
                  v-if="isEditMode"
                  v-model:visible="tagPopoverVisible"
                  placement="bottom-start"
                  :width="280"
                  trigger="click"
                >
                  <template #reference>
                    <el-button size="small" class="button-new-tag">
                      <el-icon><Plus /></el-icon> 新增標籤
                    </el-button>
                  </template>

                <!-- Popover 內部內容 -->
                <div class="tag-popover-content">
                  <!-- 手機與桌面共用的搜尋與輸入框 -->
                  <div class="tag-input-group" style="display: flex; gap: 6px;">
                    <el-input
                      v-model="tagSearchQuery"
                      placeholder="加上標籤或搜尋..."
                      size="small"
                      clearable
                      @keyup.enter="handleConfirmAddTag"
                    />
                    <!-- 💡 新增的明顯觸控確認按鈕，解決手機沒有 Enter/Esc 的痛點 -->
                    <el-button type="primary" size="small" @click="handleConfirmAddTag">
                      新增
                    </el-button>
                  </div>

                  <div class="existing-tags-section">
                    <div class="popover-subtitle">現有標籤 (點擊選取)：</div>
                    <div class="popover-tags-list">
                      <el-tag
                        v-for="t in filteredExistingTags"
                        :key="t.id"
                        size="small"
                        :effect="cardData.tags?.includes(t.name) ? 'dark' : 'plain'"
                        class="clickable-popover-tag"
                        @click="handleToggleSelectTag(t.name)"
                      >
                        {{ t.name }}
                      </el-tag>
                      <div v-if="filteredExistingTags.length === 0" class="no-tag-tip">
                        沒有找到相符標籤
                      </div>
                    </div>
                  </div>
                </div>
                </el-popover>
              </el-form-item>
            </div>
          </template>

          <div class="cover-wrapper" v-if="cardData.coverImageUrl || isEditMode">
            <template v-if="!isEditMode">
              <img :src="cardData.coverImageUrl" class="cover-image" alt="Cover" />
            </template>
            <template v-else>
              <div class="cover-edit-area">
                <el-form-item prop="coverImageUrl" style="margin-bottom: 0;">
                <el-input
                  v-model="cardData.coverImageUrl"
                  placeholder="請輸入封面圖片 URL (留空則不顯示封面)"
                  clearable
                />
                </el-form-item>
                <div v-if="cardData.coverImageUrl" class="cover-preview-mini">
                  <span>預覽：</span>
                  <img :src="cardData.coverImageUrl" style="max-height: 80px; border-radius: 4px;" />
                </div>
              </div>
            </template>
          </div>

          <div class="content-section">
            <div class="info-paragraph" v-if="cardData.reason || isEditMode">
              <h3 class="paragraph-title reason"><span class="title-marker reason"></span>{{ cardTextFieldCopy.reason.label }}</h3>
              <p v-if="!isEditMode" class="paragraph-text">{{ cardData.reason }}</p>
              <el-form-item v-else prop="reason">
                <el-input
                  v-model="cardData.reason"
                  type="textarea"
                  :rows="3"
                  :placeholder="cardTextFieldCopy.reason.placeholder"
                />
                <div class="word-count-hint" :class="{ 'over-limit': (cardData.reason?.length || 0) > 300 }">總字數：
                  {{ cardData.reason?.length || 0 }} / 300
                </div>
              </el-form-item>
            </div>

            <div class="info-paragraph" v-if="cardData.summary || isEditMode">
              <h3 class="paragraph-title summary"><span class="title-marker summary"></span>{{ cardTextFieldCopy.summary.label }}</h3>
              <p v-if="!isEditMode" class="paragraph-text">{{ cardData.summary }}</p>
              <el-form-item v-else prop="summary">
                <el-input
                  v-model="cardData.summary"
                  type="textarea"
                  :rows="5"
                  :placeholder="cardTextFieldCopy.summary.placeholder"
                />
                <div class="word-count-hint" :class="{ 'over-limit': (cardData.summary?.length || 0) > 600 }">總字數：
                  {{ cardData.summary?.length || 0 }} / 600
                </div>
              </el-form-item>
            </div>

            <div class="info-paragraph" v-if="cardData.content || isEditMode">
              <h3 class="paragraph-title content"><span class="title-marker content"></span>{{ cardTextFieldCopy.content.label }}</h3>
              <p v-if="!isEditMode" class="paragraph-text main-content">{{ cardData.content }}</p>
              <el-form-item v-else prop="content">
                <el-input
                  v-model="cardData.content"
                  type="textarea"
                  :rows="10"
                  :placeholder="cardTextFieldCopy.content.placeholder"
                />
                <div class="word-count-hint">
                  總字數：{{ cardData.content?.length || 0 }} 字
                </div>
              </el-form-item>
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
            <el-form-item prop="url" style="margin-bottom: 0;">
              <el-input v-model="cardData.url" placeholder="https://..." size="small" />
            </el-form-item>
          </template>
        </el-card>

        <el-card class="sidebar-card">
          <div class="sidebar-card-header">
            <el-icon><Clock /></el-icon>
            <span>記憶回流設定</span>
          </div>
          <div v-if="!isCreateMode && !isEditMode" class="recurrence-status-row">
            <el-tag :type="isRecurrencePaused ? 'info' : 'success'" size="small" effect="light">
              {{ isRecurrencePaused ? '已暫停' : '回流中' }}
            </el-tag>
            <el-button
              type="primary"
              link
              size="small"
              :loading="isPausePending || isResumePending"
              @click="isRecurrencePaused ? resumeRecurrence() : pauseRecurrence()"
            >
              {{ isRecurrencePaused ? '恢復回流' : '暫停回流' }}
            </el-button>
          </div>
          <div v-if="isEditMode && !isRecurrencePaused" class="detail-recurrence-editor">
            <RecurrenceIntervalPicker
              :model-value="cardData.intervalDays"
              @select="selectDraftRecurrence"
            />
            <span class="recurrence-save-hint">儲存卡片後才會套用新的回流週期</span>
          </div>
          <el-descriptions v-else :column="1" border size="small" class="clean-desc">
            <el-descriptions-item label="回流頻率 (天)">

              <span v-if="!isEditMode">
                {{ isRecurrencePaused ? '已暫停' : cardData.intervalDays ? `${cardData.intervalDays} 天一次` : '未設定' }}
              </span>
              <span v-else class="paused-edit-hint">已暫停，請先恢復回流</span>
            </el-descriptions-item>
            <el-descriptions-item label="下次看見" v-if="!isEditMode">
              <el-icon class="icon-align"><Calendar /></el-icon>
              {{ isRecurrencePaused ? '暫停期間不排程' : formatDate(cardData.nextShowAt || '') || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="最近出現在 Today" v-if="!isEditMode">
              {{ formatDate(cardData.lastOfferedAt || '') || '尚未出現' }}
            </el-descriptions-item>
            <el-descriptions-item label="最近完成回顧" v-if="!isEditMode">
              {{ formatDate(cardData.lastOpenAt || '') || '尚無回顧' }}
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
                  class="star-icon"
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
              <span class="info-label">成長狀態</span>
              <el-tag
                v-if="!isEditMode"
                :type="currentGrowthStatus.tagType"
                size="small"
                effect="light"
              >
                {{ currentGrowthStatus.label }}
              </el-tag>
              <el-select
                v-else
                v-model="cardData.growthStatus"
                size="small"
                style="width: 112px;"
              >
                <el-option
                  v-for="option in growthStatusOptions"
                  :key="option.value"
                  :value="option.value"
                  :label="option.label"
                />
              </el-select>
            </div>
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

        <CardWorkManager
          v-if="fetchedCard && !isEditMode"
          :card-id="props.id"
        />
      </div>

    </el-form>

    </template>

  </div>
</template>

<style scoped>
.detail-container {
  width: 100%;
  min-width: 0;
  box-sizing: border-box;
  padding: 20px;
  max-width: 1300px;
}

.recurrence-status-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.paused-edit-hint {
  color: var(--el-text-color-secondary);
  font-size: var(--type-caption);
}
.detail-recurrence-editor {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.recurrence-save-hint {
  color: var(--el-text-color-placeholder);
  font-size: var(--type-meta);
}
.page-header-wrapper { margin-bottom: 20px; }
.page-header-inner {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap; /* 💡 允許按鈕在空間不足時自動換行 */
  justify-content: flex-end;
}
.header-title { font-size: var(--type-section-title); font-weight: 600; color: var(--el-text-color-primary); }
.detail-main-layout { display: flex; gap: 20px; align-items: flex-start; }
.left-content { flex: 3; min-width: 0; }
.right-sidebar { flex: 1; min-width: 280px; position: sticky; top: 20px; }
.detail-card, .sidebar-card { box-shadow: var(--el-box-shadow-lighter); }
.card-header-zone { display: flex; flex-direction: column; gap: 12px; }
.card-identity-row { display: flex; align-items: center; gap: 8px; }
.detail-card-id { color: var(--el-text-color-placeholder); font-size: var(--type-meta); font-variant-numeric: tabular-nums; }
.main-title { font-size: var(--type-detail-title); font-weight: 600; color: var(--el-text-color-primary); margin: 0; line-height: var(--leading-title); }
.tags-row { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }
.cover-wrapper { margin: -20px -20px 20px -20px; overflow: hidden; max-height: 450px; display: flex; align-items: center; justify-content: center; background-color: var(--el-fill-color-light); }
.cover-image { width: 100%; height: 100%; object-fit: cover; }
.content-section { display: flex; flex-direction: column; text-align: left; }
.info-paragraph { display: flex; flex-direction: column; gap: 10px; }
.info-paragraph + .info-paragraph { margin-top: 26px; padding-top: 26px; border-top: 1px solid var(--el-border-color-lighter); }
.paragraph-title { font-size: var(--type-section-title); font-weight: 600; color: var(--el-text-color-primary); margin: 0; line-height: var(--leading-section); }
.title-marker { display: none; }
.paragraph-title.reason, .paragraph-title.summary, .paragraph-title.content { color: var(--el-text-color-primary); }
.paragraph-text { font-size: var(--type-body); color: var(--el-text-color-regular); line-height: var(--leading-body); margin: 0; padding: 0; background: transparent; white-space: pre-wrap; overflow-wrap: anywhere; }
.paragraph-text.main-content { color: var(--el-text-color-primary); }
.icon-align { vertical-align: middle; margin-right: 4px; color: var(--el-text-color-secondary); }
.star-interaction { display: inline-flex; align-items: center; gap: 6px; user-select: none; }
.star-icon { color: var(--el-color-warning); }
.active-star { animation: pop 0.3s ease; }
@keyframes pop { 0% { transform: scale(1); } 50% { transform: scale(1.3); } 100% { transform: scale(1); } }

/* 💡 手機版響應式調整 (<= 768px) */
@media (max-width: 768px) {
  .detail-container {
    padding: 0;
    max-width: none;
  }

  .detail-main-layout { flex-direction: column; }
  .left-content,
  .right-sidebar { width: 100%; min-width: 0; position: static; }

  .detail-card :deep(.el-card__header),
  .detail-card :deep(.el-card__body),
  .sidebar-card :deep(.el-card__body) {
    padding: 16px;
  }

  .cover-wrapper {
    margin: -16px -16px 20px;
  }

  /* 讓 Element Plus 的頁首改為上下排列，釋放標題空間 */
  :deep(.el-page-header__header) {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  /* 讓右側按鈕區塊佔滿整行並靠右對齊 */
  :deep(.el-page-header__right) {
    width: 100%;
    display: flex;
    justify-content: flex-end;
  }
}

.sidebar-card { margin-bottom: 16px; }
.sidebar-card-header { display: flex; align-items: center; gap: 8px; font-size: var(--type-ui); font-weight: 600; color: var(--el-text-color-primary); margin-bottom: 10px; }
.domain-text { font-size: var(--type-ui); color: var(--el-text-color-secondary); }
.goto-btn { width: 100%; }
.status-grid { display: flex; justify-content: space-around; text-align: center; padding: 8px 0; }
.grid-item { display: flex; flex-direction: column; gap: 8px; align-items: center; }
.grid-label { font-size: var(--type-meta); color: var(--el-text-color-secondary); }
.grid-value { font-size: var(--type-section-title); font-weight: 600; color: var(--el-text-color-primary); }
.compact-divider { margin: 12px 0; }
.info-list { display: flex; flex-direction: column; gap: 5px; font-size: var(--type-caption); padding: 0 4px; }
.info-item { display: flex; justify-content: space-between; align-items: center; }
.info-label { color: var(--el-text-color-regular); }
.info-val { color: var(--el-text-color-primary); font-weight: 500; }
.time-stamp-section { font-size: var(--type-meta); color: var(--el-text-color-placeholder); text-align: center; line-height: var(--leading-ui); }

.cover-edit-area {
  width: 100%;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  background-color: var(--el-fill-color-light);
}
.cover-preview-mini {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: var(--type-caption);
  color: var(--el-text-color-secondary);
}
.button-new-tag {
  height: 24px;
  padding-top: 0;
  padding-bottom: 0;
}
.word-count-hint {
  width: 100%;
  text-align: left;
  font-size: var(--type-meta);
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}
.word-count-hint.over-limit {
  color: var(--el-color-danger);
}
.word-count-hint.near-limit {
  color: var(--el-color-warning);
}
.create-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
}
/* --- 💡 仿 HackMD 標籤彈出框樣式 --- */
.tag-popover-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 4px;
}
.existing-tags-section {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.popover-subtitle {
  font-size: var(--type-meta);
  color: var(--el-text-color-secondary);
}
.popover-tags-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  max-height: 150px;
  overflow-y: auto;
}
.clickable-popover-tag {
  cursor: pointer;
  transition: all 0.2s;
}
.clickable-popover-tag:hover {
  opacity: 0.8;
}
.no-tag-tip {
  font-size: var(--type-meta);
  color: var(--el-text-color-disabled);
  padding: 4px 0;
}
.popover-footer-hint {
  font-size: var(--type-meta);
  color: var(--el-text-color-placeholder);
  border-top: 1px solid var(--el-border-color-lighter);
  padding-top: 8px;
  text-align: center;
}

/* 閱讀優先的卡片詳情體驗 */
.card-detail-experience {
  overflow: hidden;
  border: 1px solid var(--el-border-color-light);
  border-radius: 10px;
  background: var(--el-bg-color);
  box-shadow: var(--el-box-shadow-lighter);
}

.detail-navigation {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 52px;
  padding: 8px 16px;
  border-bottom: 1px solid var(--el-border-color-light);
}

.detail-navigation-actions,
.property-actions,
.card-identity-line,
.reading-tags,
.tag-editor {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.detail-state {
  min-height: 420px;
  padding: 32px;
}

.reading-layout {
  display: grid;
  min-height: calc(100dvh - 134px);
  grid-template-columns: minmax(0, 1fr) 340px;
}

.card-reading-panel {
  min-width: 0;
  padding: 32px clamp(24px, 4vw, 56px) 48px;
}

.card-reading-panel article {
  width: 100%;
  max-width: 760px;
  margin: 0 auto;
}

.card-reading-heading {
  padding-bottom: 28px;
}

.card-reading-heading h1 {
  min-width: 0;
  margin-top: 10px;
  overflow-wrap: anywhere;
  font-size: var(--type-detail-title);
  line-height: var(--leading-title);
}

.title-with-action,
.section-heading,
.property-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
}

.reading-tags {
  margin-top: 14px;
}

.reading-cover {
  display: block;
  width: 100%;
  max-height: 440px;
  margin-bottom: 30px;
  border-radius: 10px;
  object-fit: cover;
}

.reading-section {
  padding: 26px 0;
  border-top: 1px solid var(--el-border-color-lighter);
}

.section-heading h2,
.property-heading h2 {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: var(--type-section-title);
  line-height: var(--leading-section);
}

.section-heading p {
  margin: 4px 0 0;
  color: var(--el-text-color-secondary);
  font-size: var(--type-caption);
  line-height: var(--leading-ui);
}

.reading-copy {
  margin: 16px 0 0;
  color: var(--el-text-color-regular);
  font-size: var(--type-body);
  line-height: var(--leading-body);
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.prominent-copy {
  color: var(--el-text-color-primary);
  font-size: var(--type-prominent);
  font-weight: 500;
}

.main-copy {
  color: var(--el-text-color-primary);
}

.quiet-empty-state {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 8px 12px;
  margin-top: 14px;
  color: var(--el-text-color-placeholder);
  font-size: var(--type-ui);
}

.inline-edit-button {
  flex: 0 0 auto;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--el-color-primary);
  font: inherit;
  font-size: var(--type-caption);
  cursor: pointer;
}

.inline-edit-button:hover,
.inline-edit-button:focus-visible {
  color: var(--el-color-primary-light-3);
  text-decoration: underline;
}

.card-properties-panel {
  min-width: 0;
  padding: 20px;
  border-left: 1px solid var(--el-border-color-lighter);
  background: var(--el-bg-color-page);
}

.property-card {
  padding: 18px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 10px;
  background: var(--el-bg-color);
  box-shadow: var(--el-box-shadow-lighter);
}

.property-card + .property-card,
.property-card + :deep(.el-card),
.card-properties-panel :deep(.el-card) + .system-metadata {
  margin-top: 14px;
}

.property-eyebrow {
  display: block;
  margin-bottom: 4px;
  color: var(--el-text-color-placeholder);
  font-size: var(--type-meta);
  letter-spacing: 0.08em;
}

.property-description,
.property-hint {
  margin: 12px 0 0;
  color: var(--el-text-color-secondary);
  font-size: var(--type-caption);
  line-height: var(--leading-ui);
}

.property-hint {
  color: var(--el-text-color-placeholder);
}

.property-actions {
  margin-top: 14px;
}

.full-width-action {
  width: 100%;
  margin-top: 14px;
}

.property-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin: 16px 0 0;
}

.property-list > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.property-list dt {
  color: var(--el-text-color-secondary);
  font-size: var(--type-caption);
}

.property-list dd {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: var(--type-ui);
}

.growth-status-select {
  width: 132px;
  flex: 0 0 132px;
}

.star-button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 0;
  border: 0;
  background: transparent;
  color: var(--el-text-color-primary);
  font: inherit;
  cursor: pointer;
}

.star-button:disabled {
  cursor: wait;
  opacity: 0.6;
}

.system-metadata {
  display: flex;
  flex-direction: column;
  gap: 3px;
  margin-top: 16px;
  padding: 0 4px;
  color: var(--el-text-color-placeholder);
  font-size: var(--type-meta);
}

.readonly-type-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 18px;
  color: var(--el-text-color-secondary);
  font-size: var(--type-ui);
}

.tags-field :deep(.el-form-item__content) {
  display: block;
}

.tag-input-group {
  display: grid;
  width: 100%;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 6px;
}

:global(.card-edit-dialog.el-dialog) {
  display: flex;
  max-height: calc(100dvh - 32px);
  flex-direction: column;
}

:global(.card-edit-dialog .el-dialog__body) {
  min-height: 0;
  overflow-y: auto;
}

:global(.card-edit-dialog .el-dialog__footer) {
  flex: 0 0 auto;
  padding-top: 14px;
  border-top: 1px solid var(--el-border-color-lighter);
}

:global(.danger-menu-item) {
  color: var(--el-color-danger);
}

@media (max-width: 980px) {
  .reading-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .card-properties-panel {
    border-top: 1px solid var(--el-border-color-lighter);
    border-left: 0;
  }
}

@media (max-width: 600px) {
  .card-detail-experience {
    border-right: 0;
    border-left: 0;
    border-radius: 0;
    box-shadow: none;
  }

  .detail-navigation {
    align-items: flex-start;
    flex-wrap: wrap;
    padding: 12px 16px;
  }

  .detail-navigation-actions {
    width: 100%;
    justify-content: flex-end;
  }

  .card-reading-panel {
    padding: 24px 18px 32px;
  }

  .card-properties-panel {
    padding: 18px;
  }

  .section-heading {
    gap: 12px;
  }

  :global(.card-edit-dialog.el-dialog) {
    width: calc(100vw - 24px) !important;
    max-height: calc(100dvh - 24px);
  }
}

.experiment-context-card {
  margin-top: 16px;
}

.experiment-context-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.experiment-context-section > h3 {
  margin: 0 0 8px;
  color: var(--el-text-color-regular);
  font-size: var(--type-caption);
}

.lineage-section {
  padding-top: 14px;
  margin-top: 14px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.experiment-context-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 3px 8px;
  padding: 9px 10px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-fill-color-lighter);
  color: var(--el-text-color-primary);
  cursor: pointer;
  text-align: left;
}

.experiment-context-item:hover {
  border-color: var(--el-color-success-light-5);
}

.experiment-context-item small {
  grid-column: 1 / -1;
  color: var(--el-text-color-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
