<script setup lang="ts">
import { ref } from 'vue'
import { ArrowLeft, Plus } from '@element-plus/icons-vue'
import router from '../router'

const goBack = () => router.back()

// 初始化乾淨的創建表單
const form = ref({
  title: '',
  type: 'note', // 預設為筆記
  url: '',
  coverImageUrl: '',
  tags: [] as string[],
  reason: '',
  summary: '',
  content: '',
  intervalDays: 1 // 預設 1 天回流一次
})

// 標籤相關邏輯...（可沿用詳情頁的 tag 邏輯）

const handleCreate = () => {
  // 呼叫建立 API: POST /api/cards
  console.log('提交表單', form.value)
  router.push('/board/all') // 成功後回總卡片列表，可看見最新新增的卡片
}
</script>

<template>
  <div class="create-container">
    <div class="page-header-wrapper">
      <el-page-header :icon="ArrowLeft" @back="goBack">
        <template #content><span class="header-title">創建新卡片</span></template>
        <template #extra>
          <el-button @click="goBack">取消</el-button>
          <el-button type="primary" @click="handleCreate">確認建立</el-button>
        </template>
      </el-page-header>
    </div>

    <el-card class="create-card">
      <el-form :model="form" label-position="top">

        <el-form-item label="卡片類型">
          <el-radio-group v-model="form.type">
            <el-radio-button label="note">知識筆記</el-radio-button>
            <el-radio-button label="link">外部連結</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item v-if="form.type === 'link'" label="連結網址 (URL)" required>
          <el-input v-model="form.url" placeholder="https://..." />
        </el-form-item>

        <el-form-item label="卡片標題" required>
          <el-input v-model="form.title" placeholder="請輸入標題" />
        </el-form-item>

        <el-form-item label="記憶回流頻率 (天)">
          <el-input-number v-model="form.intervalDays" :min="1" :max="365" />
        </el-form-item>

        <el-form-item label="詳細內容">
          <el-input v-model="form.content" type="textarea" :rows="5" placeholder="請輸入內容..." />
        </el-form-item>

      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.create-container { padding: 20px; max-width: 800px; margin: 0 auto; }
.create-card { padding: 20px; }
/* 你的其他優雅樣式... */
</style>
