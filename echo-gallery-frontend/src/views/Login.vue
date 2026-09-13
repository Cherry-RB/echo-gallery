<script setup lang="ts">
import { reactive, computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAsync } from '../utils/api/useAsync'
import { Lock, Message } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/authStore'
import ThemeSwitcher from '../components/ThemeSwitcher.vue'
import { authApi } from '../utils/api/authApi'

const router = useRouter()
const form = reactive({ email: '', password: '' })

const errors = reactive({ email: '', password: '' });
// 統一驗證函式
const validateForm = () => {
  let isValid = true;

  // 1. Email 驗證
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  if (!form.email) {
    errors.email = 'Email 必填';
    isValid = false;
  } else if (!emailRegex.test(form.email)) {
    errors.email = 'Email 格式錯誤';
    isValid = false;
  } else {
    errors.email = '';
  }

  // 2. Password 驗證
  if (form.password.trim().length < 6) {
    errors.password = '密碼至少需要 6 個字元';
    isValid = false;
  } else {
    errors.password = '';
  }

  return isValid;
};

const authStore = useAuthStore();
const { loading, execute: handleLogin } = useAsync(authStore.login)
const { loading: demoLoading, execute: startDemo } = useAsync(authStore.startDemo)
const demoEnabled = ref(false)
const selectedDemoLibrary = ref('')
const demoOptions = [
  {
    value: 'tech',
    label: '技術與學習收藏',
    description: '以官方技術文件與原創學習註記為主，看看收藏理由與回流間隔如何把知識帶回自己的學習節奏。'
  },
  {
    value: 'visual',
    label: '視覺靈感與創作素材',
    description: '從大型文化機構公開頁面與原創觀察出發，看看保留過的素材如何再次成為正在進行的創作起點。'
  },
  {
    value: 'writing',
    label: '文字片段與生活觀察',
    description: '以原創筆記 Card 為主，重新遇見曾經想留下的句子，並自行決定要稍後再看、暫停或封存。'
  }
]
const selectedDemoOption = computed(() =>
  demoOptions.find((option) => option.value === selectedDemoLibrary.value)
)

onMounted(async () => {
  try {
    demoEnabled.value = (await authApi.demoFeatureStatus()).enabled
  } catch {
    demoEnabled.value = false
  }
})

const onSubmit = async () => {
  if (!validateForm()){
    ElMessage.error('請填寫完整欄位');
    return
  }
  try {
    const res = await handleLogin(form)
    if (res?.token) {
      ElMessage.success("登入成功");
      router.push('/board/today') // 導向今日看板
    }
  } catch (err: any) {
    console.error('API 呼叫失敗:', err);
  }
}

const onStartDemo = async (library: string) => {
  try {
    const res = await startDemo(library)
    if (res?.token) {
      ElMessage.success('已建立獨立 Demo 體驗')
      router.push('/board/today')
    }
  } catch (err) {
    console.error('Demo 工作階段建立失敗:', err)
  }
}

// 計算屬性直接依賴 errors 物件
const isFormValid = computed(() => {
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  return form.email.trim() !== '' &&
         emailRegex.test(form.email) &&
         form.password.trim().length >= 6 &&
         errors.email === '' &&
         errors.password === '';
});
</script>

<template>
  <div class="auth-page">
    <ThemeSwitcher compact class="auth-theme-switcher" />
    <el-card class="auth-card">
      <template #header>
        <h2 class="auth-title">EchoGallery 登入</h2>
      </template>

      <el-form label-position="top" :model="form" @submit.prevent="onSubmit">
        <el-form-item label="Email" :error="errors.email">
          <el-input v-model="form.email" :prefix-icon="Message" placeholder="請輸入 Email" @blur="validateForm"/>
        </el-form-item>
        <el-form-item label="密碼" :error="errors.password">
          <el-input v-model="form.password" :prefix-icon="Lock" type="password" placeholder="請輸入密碼" show-password @blur="validateForm"/>
        </el-form-item>

        <el-button type="primary" :loading="loading" class="auth-btn"  :disabled="!isFormValid" native-type="submit">
          確認登入
        </el-button>

        <div class="auth-link">
          <router-link to="/register">還沒有帳號？立即註冊</router-link>
        </div>
      </el-form>

      <template v-if="demoEnabled">
        <el-divider class="demo-divider">或快速體驗</el-divider>
        <section class="demo-entry" aria-label="Demo 快速體驗">
          <p class="demo-entry-prompt">想先看看不同內容如何回流？選擇展示內容庫，立即體驗可操作資料。</p>
          <el-select v-model="selectedDemoLibrary" placeholder="選擇展示內容庫" class="demo-library-select">
            <el-option
              v-for="option in demoOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
          <div v-if="selectedDemoOption" class="demo-selection">
            <p class="demo-selection-title">已選擇：{{ selectedDemoOption.label }}</p>
            <p>{{ selectedDemoOption.description }}</p>
            <el-button type="primary" plain :loading="demoLoading" @click="onStartDemo(selectedDemoOption.value)">
              以此內容庫開始體驗
            </el-button>
          </div>
        </section>
      </template>
    </el-card>
  </div>
</template>

<style scoped>
@import '../assets/auth.css'; /* 引入共用樣式 */

.demo-entry {
  display: grid;
  gap: 12px;
}

.demo-entry-prompt {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: var(--type-ui);
  line-height: var(--leading-ui);
}

.demo-library-select {
  width: 100%;
}

.demo-selection {
  display: grid;
  gap: 8px;
  padding: 12px 14px;
  border-left: 2px solid var(--el-color-primary-light-5);
  background: var(--el-fill-color-lighter);
}

.demo-selection-title {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: var(--type-ui);
  font-weight: 600;
  line-height: var(--leading-ui);
}

.demo-selection > p:not(.demo-selection-title) {
  margin: 0;
  color: var(--el-text-color-regular);
  font-size: var(--type-caption);
  line-height: var(--leading-ui);
}

.demo-selection .el-button {
  justify-self: start;
  margin-top: 2px;
}

:deep(.demo-divider .el-divider__text) {
  color: var(--el-text-color-secondary);
  font-size: var(--type-caption);
  font-weight: 500;
}
</style>
