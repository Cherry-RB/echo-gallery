<script setup lang="ts">
import { computed } from 'vue'
import { ArrowLeft, Link } from '@element-plus/icons-vue'
import { useQuery } from '@tanstack/vue-query'
import { useRouter } from 'vue-router'
import type { WorkStatus } from '../../types/work'
import { formatDate } from '../../utils/formatDate'
import { workApi } from '../../utils/api/workApi'

const props = defineProps<{ id: string }>()
const router = useRouter()

type WorkStatusTagType = 'primary' | 'success' | 'warning' | 'info'

const workStatusMeta: Record<WorkStatus, { label: string; type: WorkStatusTagType }> = {
  IDEA: { label: '構想', type: 'info' },
  DRAFT: { label: '草稿', type: 'warning' },
  ACTIVE: { label: '進行中', type: 'primary' },
  DONE: { label: '已完成', type: 'success' },
  ARCHIVED: { label: '已封存', type: 'info' },
}

const {
  data: work,
  isLoading,
  isError,
  refetch,
} = useQuery({
  queryKey: computed(() => ['work', String(props.id)]),
  queryFn: () => workApi.getWork(props.id),
})

const goBack = () => {
  if (window.history.state?.back) {
    router.back()
    return
  }
  router.push({ name: 'WorkList' })
}
</script>

<template>
  <section class="work-detail-page">
    <header class="detail-navigation">
      <el-button :icon="ArrowLeft" text @click="goBack">
        返回作品列表
      </el-button>
    </header>

    <div class="work-detail-surface">
      <div v-if="isLoading" aria-label="作品詳情載入中">
        <el-skeleton :rows="8" animated />
      </div>

      <el-result
        v-else-if="isError"
        icon="error"
        title="無法載入作品"
        sub-title="作品可能不存在，或目前無法連線"
      >
        <template #extra>
          <el-button @click="goBack">返回列表</el-button>
          <el-button type="primary" @click="refetch()">重新載入</el-button>
        </template>
      </el-result>

      <article v-else-if="work" class="work-detail-content">
        <div class="detail-heading">
          <h1 class="work-title">{{ work.title }}</h1>
          <el-tag :type="workStatusMeta[work.status].type" effect="plain">
            {{ workStatusMeta[work.status].label }}
          </el-tag>
        </div>

        <p v-if="work.description" class="work-description">
          {{ work.description }}
        </p>
        <p v-else class="empty-description">尚未填寫作品說明</p>

        <a
          v-if="work.externalUrl"
          :href="work.externalUrl"
          target="_blank"
          rel="noopener noreferrer"
          class="external-link"
        >
          <el-icon><Link /></el-icon>
          <span>開啟外部作品</span>
        </a>

        <dl class="time-metadata">
          <div>
            <dt>建立時間</dt>
            <dd>{{ formatDate(work.createdAt) }}</dd>
          </div>
          <div>
            <dt>最後更新</dt>
            <dd>{{ formatDate(work.updatedAt) }}</dd>
          </div>
          <div v-if="work.completedAt">
            <dt>完成時間</dt>
            <dd>{{ formatDate(work.completedAt) }}</dd>
          </div>
        </dl>
      </article>
    </div>
  </section>
</template>

<style scoped>
.work-detail-page {
  width: 100%;
  max-width: 960px;
  margin: 0 auto;
}

.detail-navigation {
  margin-bottom: 16px;
}

.work-detail-surface {
  min-height: 360px;
  padding: 28px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-bg-color);
  box-shadow: var(--el-box-shadow-lighter);
}

.detail-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}

.work-title {
  min-width: 0;
  overflow-wrap: break-word;
  word-break: break-word;
  font-size: 30px;
  line-height: 1.4;
}

.work-description {
  margin-top: 24px;
  color: var(--el-text-color-regular);
  font-size: 15px;
  line-height: 1.8;
  overflow-wrap: break-word;
  white-space: pre-wrap;
}

.empty-description {
  margin-top: 24px;
  color: var(--el-text-color-placeholder);
  font-size: 14px;
}

.external-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 24px;
  color: var(--el-color-primary);
  font-size: 14px;
  text-decoration: none;
}

.external-link:hover {
  color: var(--el-color-primary-light-3);
}

.time-metadata {
  display: flex;
  flex-wrap: wrap;
  gap: 16px 32px;
  margin: 32px 0 0;
  padding-top: 20px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.time-metadata div {
  min-width: 140px;
}

.time-metadata dt {
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}

.time-metadata dd {
  margin: 4px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

@media (max-width: 600px) {
  .work-detail-surface {
    padding: 20px 16px;
  }

  .detail-heading {
    align-items: flex-start;
    flex-direction: column;
    gap: 12px;
  }

  .work-title {
    font-size: 24px;
  }

  .time-metadata {
    flex-direction: column;
    gap: 14px;
  }
}
</style>
