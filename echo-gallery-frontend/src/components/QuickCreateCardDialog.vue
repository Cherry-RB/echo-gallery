<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import type { FormInstance } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { getDefaultCardData } from '../mock-data/card-default-new'
import { createCardFormRules, toCardContentRequest } from '../utils/cardForm'
import { useTags } from '../utils/composables/useTags'
import { useCardStatus } from '../utils/useCardStatus'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

type OptionalField = 'reason' | 'summary' | 'content' | 'coverImageUrl'

const cardFormRef = ref<FormInstance>()
const cardData = ref(getDefaultCardData())
const visibleOptionalFields = ref<OptionalField[]>([])
const titleInputRef = ref<{ focus: () => void }>()
const dialogVisible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value),
})
const rules = createCardFormRules(cardData)
const { existingTags, isTagsLoading } = useTags(cardData)
const { handleCreateCard, isCreatePending } = useCardStatus()

const optionalFields: Array<{ key: OptionalField; label: string }> = [
  { key: 'reason', label: '收藏原因' },
  { key: 'summary', label: '摘要' },
  { key: 'content', label: '完整內容' },
  { key: 'coverImageUrl', label: '封面圖片' },
]

const showOptionalField = (field: OptionalField) => {
  if (!visibleOptionalFields.value.includes(field)) {
    visibleOptionalFields.value.push(field)
  }
}

const resetForm = () => {
  cardData.value = getDefaultCardData()
  visibleOptionalFields.value = []
  cardFormRef.value?.clearValidate()
}

const handleOpened = () => {
  nextTick(() => titleInputRef.value?.focus())
}

const submit = async () => {
  if (!cardFormRef.value) return

  const valid = await cardFormRef.value.validate().catch(() => false)
  if (!valid) return

  handleCreateCard(toCardContentRequest(cardData.value), {
    onSuccess: () => {
      dialogVisible.value = false
    },
  })
}
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    title="快速新增卡片"
    width="min(680px, calc(100vw - 32px))"
    class="quick-create-dialog"
    destroy-on-close
    append-to-body
    @opened="handleOpened"
    @closed="resetForm"
  >
    <p class="dialog-description">
      先留下最重要的內容，其餘資訊可以現在補充，也可以之後再慢慢完善。
    </p>

    <el-form
      ref="cardFormRef"
      :model="cardData"
      :rules="rules"
      label-position="top"
      @submit.prevent
    >
      <div class="identity-row">
        <el-form-item label="卡片類型" prop="type" class="type-field">
          <el-radio-group v-model="cardData.type">
            <el-radio-button label="note">筆記</el-radio-button>
            <el-radio-button label="link">連結</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="回流週期" prop="intervalDays" class="interval-field">
          <el-input-number
            v-model="cardData.intervalDays"
            :min="1"
            :max="365"
            controls-position="right"
          />
          <span class="interval-unit">天</span>
        </el-form-item>
      </div>

      <el-form-item label="標題" prop="title">
        <el-input
          ref="titleInputRef"
          v-model="cardData.title"
          maxlength="255"
          show-word-limit
          placeholder="先記下這張卡片的核心想法"
        />
      </el-form-item>

      <el-form-item v-if="cardData.type === 'link'" label="來源網址" prop="url">
        <el-input v-model="cardData.url" placeholder="https://..." clearable />
      </el-form-item>

      <el-form-item label="標籤" prop="tags">
        <el-select
          v-model="cardData.tags"
          multiple
          filterable
          allow-create
          default-first-option
          :multiple-limit="10"
          :loading="isTagsLoading"
          placeholder="選擇既有標籤，或直接輸入新標籤"
          class="tag-select"
        >
          <el-option
            v-for="tag in existingTags ?? []"
            :key="tag.id"
            :label="tag.name"
            :value="tag.name"
          />
        </el-select>
      </el-form-item>

      <div class="optional-field-actions">
        <span class="optional-label">按需補充</span>
        <el-button
          v-for="field in optionalFields"
          v-show="!visibleOptionalFields.includes(field.key)"
          :key="field.key"
          text
          type="primary"
          :icon="Plus"
          @click="showOptionalField(field.key)"
        >
          {{ field.label }}
        </el-button>
      </div>

      <el-form-item
        v-if="visibleOptionalFields.includes('reason')"
        label="收藏原因"
        prop="reason"
      >
        <el-input
          v-model="cardData.reason"
          type="textarea"
          :rows="2"
          maxlength="300"
          show-word-limit
          placeholder="為什麼值得留下這項內容？"
        />
      </el-form-item>

      <el-form-item
        v-if="visibleOptionalFields.includes('summary')"
        label="摘要"
        prop="summary"
      >
        <el-input
          v-model="cardData.summary"
          type="textarea"
          :rows="3"
          maxlength="600"
          show-word-limit
          placeholder="簡短整理重點"
        />
      </el-form-item>

      <el-form-item
        v-if="visibleOptionalFields.includes('content')"
        label="完整內容"
        prop="content"
      >
        <el-input
          v-model="cardData.content"
          type="textarea"
          :rows="6"
          placeholder="記錄完整內容、行動或覆盤"
        />
      </el-form-item>

      <el-form-item
        v-if="visibleOptionalFields.includes('coverImageUrl')"
        label="封面圖片網址"
        prop="coverImageUrl"
      >
        <el-input v-model="cardData.coverImageUrl" placeholder="https://..." clearable />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="isCreatePending" @click="submit">
        建立卡片
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.dialog-description {
  margin: -8px 0 20px;
  color: var(--el-text-color-secondary);
  font-size: 14px;
  line-height: 1.6;
}

.identity-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}

.type-field {
  flex: 1;
}

.interval-field {
  width: 170px;
}

.interval-field :deep(.el-form-item__content) {
  flex-wrap: nowrap;
}

.interval-unit {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
}

.tag-select {
  width: 100%;
}

.optional-field-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 2px 4px;
  padding: 10px 12px;
  margin-bottom: 18px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
}

.optional-label {
  margin-right: 6px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

@media (max-width: 560px) {
  .identity-row {
    display: block;
  }

  .interval-field {
    width: 100%;
  }

  .optional-field-actions {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
