<script setup lang="ts">
import { reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import { authApi } from '../utils/api/auth'
import { useAsync } from '../utils/api/useAsync'
import { Lock, Message } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

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

const { loading, execute: handleLogin } = useAsync(authApi.login)

const onSubmit = async () => {
  validateForm()
  // 即使按鈕 disabled，這裡也可以加一層保護
  if (!isFormValid.value){
    ElMessage.error('請填寫完整欄位');
    return
  };
  try {
    const res = await handleLogin(form)
    if (res?.token) {
      localStorage.setItem('token', res.token)
      localStorage.setItem('username', res.username)
      router.push('/')
    }
  } catch (err: any) {
    console.error('API 呼叫失敗:', err);
  }
}

// 計算屬性直接依賴 errors 物件
const isFormValid = computed(() => {
  return form.email.trim() !== '' &&
         form.password.trim().length >= 6 &&
         errors.email === '' &&
         errors.password === '';
});
</script>

<template>
  <div class="auth-page">
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
    </el-card>
  </div>
</template>

<style scoped>
@import '../assets/auth.css'; /* 引入共用樣式 */
</style>
