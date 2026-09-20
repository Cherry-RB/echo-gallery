<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import type { FormInstance } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { getDefaultCardData } from '../mock-data/card-default-new'
import { createCardFormRules, toCardContentRequest } from '../utils/cardForm'
import { cardTextFieldCopy } from '../utils/cardTextFieldCopy'
import { useTags } from '../utils/composables/useTags'
import { useCardStatus } from '../utils/useCardStatus'
import { getTextLength, trimToTextLength } from '../utils/textLength'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
type OptionalField = 'reason' | 'summary' | 'coverImageUrl'

const cardFormRef = ref<FormInstance>()
const cardData = ref(getDefaultCardData())
const visibleOptionalFields = ref<OptionalField[]>([])
const titleInputRef = ref<{ focus: () => void }>()
const createdCardId = ref<string | null>(null)
const router = useRouter()
const dialogVisible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value),
})
const rules = createCardFormRules(cardData)
const {
  tagPopoverVisible,
  tagSearchQuery,
  filteredExistingTags,
  handleToggleSelectTag,
  handleCloseTag,
  handleConfirmAddTag,
} = useTags(cardData)
const { handleCreateCard, isCreatePending } = useCardStatus()

const optionalFields: Array<{ key: OptionalField; label: string }> = [
  { key: 'reason', label: cardTextFieldCopy.reason.label },
  { key: 'summary', label: cardTextFieldCopy.summary.label },
  { key: 'coverImageUrl', label: '封面圖片' },
]
const quickRecurrenceOptions = [7, 10, 30]

const showOptionalField = (field: OptionalField) => {
  if (!visibleOptionalFields.value.includes(field)) visibleOptionalFields.value.push(field)
}

const resetForm = () => {
  cardData.value = getDefaultCardData()
  visibleOptionalFields.value = []
  tagPopoverVisible.value = false
  tagSearchQuery.value = ''
  createdCardId.value = null
  cardFormRef.value?.clearValidate()
}

const handleOpened = () => nextTick(() => titleInputRef.value?.focus())

const isHttpUrl = (value: string) => {
  try {
    const url = new URL(value)
    return url.protocol === 'http:' || url.protocol === 'https:'
  } catch {
    return false
  }
}

const handleTitlePaste = (event: ClipboardEvent) => {
  const pastedText = event.clipboardData?.getData('text/plain').trim() ?? ''
  if (!isHttpUrl(pastedText)) return
  event.preventDefault()
  cardData.value.type = 'link'
  cardData.value.url = pastedText
  ElMessage.success('已辨識為連結，請補上卡片標題')
}

const selectRecurrence = (intervalDays: number) => {
  cardData.value.intervalDays = intervalDays
}

const selectCustomRecurrence = (intervalDays: number | undefined) => {
  if (intervalDays == null) return
  selectRecurrence(intervalDays)
}

const pauseRecurrence = () => {
  cardData.value.intervalDays = null
}

const hasUnsavedChanges = computed(() => {
  const defaultCard = getDefaultCardData()
  return cardData.value.type !== defaultCard.type
    || cardData.value.title.trim() !== ''
    || cardData.value.url?.trim() !== ''
    || cardData.value.reason?.trim() !== ''
    || cardData.value.summary?.trim() !== ''
    || cardData.value.content?.trim() !== ''
    || cardData.value.coverImageUrl?.trim() !== ''
    || cardData.value.intervalDays !== defaultCard.intervalDays
    || cardData.value.tags.length > 0
})

const confirmDiscard = () => ElMessageBox.confirm(
  '尚有未建立的卡片內容，確定要放棄嗎？',
  '放棄新增',
  { confirmButtonText: '放棄內容', cancelButtonText: '繼續編輯', type: 'warning' },
)

const requestClose = async () => {
  if (isCreatePending.value || createdCardId.value || !hasUnsavedChanges.value) {
    dialogVisible.value = false
    return
  }
  try {
    await confirmDiscard()
    dialogVisible.value = false
  } catch {
    // 使用者選擇繼續編輯時維持彈窗開啟。
  }
}

const handleBeforeClose = (done: () => void) => {
  if (isCreatePending.value || createdCardId.value || !hasUnsavedChanges.value) {
    done()
    return
  }
  confirmDiscard().then(() => done()).catch(() => undefined)
}

const createAnother = () => {
  resetForm()
  nextTick(() => titleInputRef.value?.focus())
}

const viewCreatedCard = () => {
  if (!createdCardId.value) return
  const cardId = createdCardId.value
  dialogVisible.value = false
  router.push({ name: 'CardDetail', params: { id: cardId } })
}

const submit = async () => {
  if (!cardFormRef.value) return
  const valid = await cardFormRef.value.validate().catch(() => false)
  if (!valid) return
  handleCreateCard(toCardContentRequest(cardData.value), {
    onSuccess: card => { createdCardId.value = card.id },
  })
}

const limitTextLength = (field: 'title' | 'reason' | 'summary', maximum: number) => {
  const value = cardData.value[field] ?? ''
  cardData.value[field] = trimToTextLength(value, maximum)
}
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    title="快速新增卡片"
    width="min(620px, calc(100vw - 32px))"
    class="quick-create-dialog"
    destroy-on-close
    append-to-body
    :before-close="handleBeforeClose"
    @opened="handleOpened"
    @closed="resetForm"
  >
    <template v-if="createdCardId">
      <div class="create-success">
        <h3>卡片已建立</h3>
        <p>你可以立即查看卡片，或繼續記下下一則內容。</p>
      </div>
    </template>

    <template v-else>
      <p class="dialog-description">先留下最重要的內容，其餘資訊可以現在補充，也可以之後再慢慢完善。</p>
      <el-form
        ref="cardFormRef"
        :model="cardData"
        :rules="rules"
        label-position="top"
        @submit.prevent
        @keydown.ctrl.enter.prevent="submit"
        @keydown.meta.enter.prevent="submit"
      >
        <div class="quick-settings-grid">
          <el-form-item label="卡片類型" prop="type" class="quick-setting-field">
            <el-radio-group v-model="cardData.type">
              <el-radio-button label="note">筆記</el-radio-button>
              <el-radio-button label="link">連結</el-radio-button>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="回流天數" prop="intervalDays" class="quick-setting-field">
            <div class="quick-recurrence-control">
              <button
                v-for="days in quickRecurrenceOptions"
                :key="days"
                type="button"
                class="quick-interval-button"
                :class="{ active: cardData.intervalDays === days }"
                :aria-pressed="cardData.intervalDays === days"
                @click="selectRecurrence(days)"
              >{{ days }} 天</button>
              <div class="custom-interval-input">
                <el-input-number
                  :model-value="cardData.intervalDays ?? undefined"
                  :min="1"
                  :max="365"
                  :controls="false"
                  aria-label="自訂回流天數"
                  placeholder="自訂"
                  @change="selectCustomRecurrence"
                />
                <span>天</span>
              </div>
              <button
                type="button"
                class="pause-recurrence-button"
                :class="{ active: cardData.intervalDays === null }"
                :aria-pressed="cardData.intervalDays === null"
                @click="pauseRecurrence"
              >
                {{ cardData.intervalDays === null ? '已暫停' : '暫停' }}
              </button>
            </div>
          </el-form-item>
        </div>

        <el-form-item label="標題" prop="title">
          <el-input
            ref="titleInputRef"
            v-model="cardData.title"
            placeholder="先記下這張卡片的核心想法"
            @paste="handleTitlePaste"
            @update:model-value="limitTextLength('title', 255)"
          />
          <div class="word-count-hint" :class="{ 'near-limit': getTextLength(cardData.title) >= 230 }">字數：{{ getTextLength(cardData.title) }} / 255</div>
        </el-form-item>

        <el-form-item v-if="cardData.type === 'link'" label="來源連結" prop="url">
          <el-input v-model="cardData.url" placeholder="https://..." clearable />
        </el-form-item>

        <el-form-item :label="cardTextFieldCopy.content.label" prop="content">
          <el-input v-model="cardData.content" type="textarea" :rows="4" :placeholder="cardTextFieldCopy.content.placeholder" />
          <div class="word-count-hint">總字數：{{ cardData.content?.length || 0 }} 字</div>
        </el-form-item>

        <el-form-item label="標籤" prop="tags" class="tags-field">
          <div class="tag-editor">
            <el-tag v-for="tag in cardData.tags" :key="tag" type="info" size="small" effect="plain" closable @close="handleCloseTag(tag)">
              #{{ tag }}
            </el-tag>
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
                    <div v-if="filteredExistingTags.length === 0" class="no-tag-tip">尚無符合的既有標籤</div>
                  </div>
                </div>
              </div>
            </el-popover>
          </div>
        </el-form-item>

        <div class="optional-field-actions">
          <span class="optional-label">按需補充</span>
          <el-button
            v-for="field in optionalFields"
            v-show="!visibleOptionalFields.includes(field.key)"
            :key="field.key"
            text type="primary" :icon="Plus" @click="showOptionalField(field.key)"
          >{{ field.label }}</el-button>
        </div>

        <el-form-item v-if="visibleOptionalFields.includes('reason')" :label="cardTextFieldCopy.reason.label" prop="reason">
          <el-input v-model="cardData.reason" type="textarea" :rows="2" :placeholder="cardTextFieldCopy.reason.placeholder" @update:model-value="limitTextLength('reason', 300)" />
          <div class="word-count-hint" :class="{ 'near-limit': getTextLength(cardData.reason) >= 270 }">字數：{{ getTextLength(cardData.reason) }} / 300</div>
        </el-form-item>
        <el-form-item v-if="visibleOptionalFields.includes('summary')" :label="cardTextFieldCopy.summary.label" prop="summary">
          <el-input v-model="cardData.summary" type="textarea" :rows="3" :placeholder="cardTextFieldCopy.summary.placeholder" @update:model-value="limitTextLength('summary', 600)" />
          <div class="word-count-hint" :class="{ 'near-limit': getTextLength(cardData.summary) >= 540 }">字數：{{ getTextLength(cardData.summary) }} / 600</div>
        </el-form-item>
        <el-form-item v-if="visibleOptionalFields.includes('coverImageUrl')" label="封面圖片來源連結" prop="coverImageUrl">
          <el-input v-model="cardData.coverImageUrl" placeholder="https://..." clearable />
        </el-form-item>
      </el-form>
    </template>

    <template #footer>
      <template v-if="createdCardId">
        <el-button @click="createAnother">繼續新增</el-button>
        <el-button type="primary" @click="viewCreatedCard">查看卡片</el-button>
      </template>
      <template v-else>
        <el-button @click="requestClose">取消</el-button>
        <el-button type="primary" :loading="isCreatePending" @click="submit">建立卡片</el-button>
      </template>
    </template>
  </el-dialog>
</template>

<style scoped>
.dialog-description { display: block; margin: 0 0 18px; padding-top: 2px; color: var(--el-text-color-secondary); font-size: var(--type-ui); line-height: 1.65; }
.quick-settings-grid { display: grid; grid-template-columns: auto minmax(0, 1fr); gap: 18px; padding: 14px; margin-bottom: 18px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; background: var(--el-fill-color-lighter); }
.quick-setting-field { margin-bottom: 0; }
.quick-setting-field :deep(.el-form-item__content) { display: block; }
.quick-recurrence-control { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; }
.quick-interval-button { min-height: 36px; padding: 5px 10px; border: 1px solid var(--el-border-color); border-radius: 6px; background: var(--el-bg-color); color: var(--el-text-color-regular); font: inherit; font-size: var(--type-ui); cursor: pointer; }
.quick-interval-button:hover, .quick-interval-button:focus-visible { border-color: var(--el-color-primary-light-5); color: var(--el-color-primary); }
.quick-interval-button.active { border-color: var(--el-color-primary-light-5); background: var(--el-color-primary-light-9); color: var(--el-color-primary); }
.custom-interval-input { display: flex; align-items: center; gap: 5px; color: var(--el-text-color-secondary); font-size: var(--type-caption); }
.custom-interval-input :deep(.el-input-number) { width: 76px; }
.pause-recurrence-button { min-height: 36px; padding: 5px 8px; border: 1px solid transparent; border-radius: 6px; background: transparent; color: var(--el-text-color-secondary); font: inherit; font-size: var(--type-caption); cursor: pointer; }
.pause-recurrence-button:hover, .pause-recurrence-button:focus-visible { color: var(--el-color-danger); }
.pause-recurrence-button.active { border-color: var(--el-color-info-light-5); background: var(--el-color-info-light-9); color: var(--el-text-color-regular); }
.tags-field :deep(.el-form-item__content) { display: block; }
.tag-editor { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; min-height: 28px; }
.button-new-tag { height: 24px; padding-top: 0; padding-bottom: 0; }
.tag-input-group { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 6px; width: 100%; }
.tag-input-group :deep(.el-input) { min-width: 0; }
.tag-input-group :deep(.el-button) { white-space: nowrap; }
.existing-tags-section { margin-top: 12px; }
.popover-subtitle, .no-tag-tip { color: var(--el-text-color-secondary); font-size: var(--type-caption); }
.popover-tags-list { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 6px; }
.clickable-popover-tag { cursor: pointer; }
.optional-field-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 2px 4px; padding: 10px 12px; margin-bottom: 18px; border-radius: 8px; background: var(--el-fill-color-light); }
.optional-label { margin-right: 6px; font-size: var(--type-caption); color: var(--el-text-color-secondary); }
.word-count-hint { width: 100%; margin-top: 5px; color: var(--el-text-color-secondary); font-size: var(--type-meta); line-height: 1.2; }
.word-count-hint.near-limit { color: var(--el-color-warning); }
.create-success { padding: 12px 0 4px; }
.create-success h3 { margin: 0 0 8px; color: var(--el-text-color-primary); font-size: var(--type-title-sm); }
.create-success p { margin: 0; color: var(--el-text-color-secondary); font-size: var(--type-ui); }
:global(.quick-create-dialog.el-dialog) { display: flex; flex-direction: column; max-height: calc(100dvh - 32px); }
:global(.quick-create-dialog .el-dialog__body) { min-height: 0; overflow-y: auto; }
:global(.quick-create-dialog .el-dialog__footer) { flex: 0 0 auto; padding-top: 14px; border-top: 1px solid var(--el-border-color-lighter); }
@media (max-width: 560px) {
  :global(.quick-create-dialog.el-dialog) { width: calc(100vw - 24px) !important; max-height: calc(100dvh - 24px); }
  .quick-settings-grid { grid-template-columns: minmax(0, 1fr); gap: 12px; }
  .quick-interval-button, .pause-recurrence-button { min-height: 40px; }
  .optional-field-actions { align-items: flex-start; flex-direction: column; }
}
</style>
