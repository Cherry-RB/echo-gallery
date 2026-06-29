<script setup lang="ts">
import { reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAsync } from '../utils/api/useAsync'
import { Lock, Message, User } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/authStore'

const router = useRouter()
const form = reactive({ username: '', email: '', password: '' })
const authStore = useAuthStore();
const { loading, execute: handleRegister } = useAsync(authStore.register);

const errors = reactive({ username: '', email: '', password: '' });
// 1. 基礎格式驗證 (前端規則)
const validateForm = () => {
  let isValid = true;

  // 1. Username 驗證
  if (form.username.trim().length < 2) {
    errors.username = '名稱至少需要 2 個字元';
    isValid = false;
  } else {
    errors.username = '';
  }

  // 2. Email 驗證
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

  // 3. Password 驗證
  if (form.password.trim().length < 6) {
    errors.password = '密碼至少需要 6 個字元';
    isValid = false;
  } else {
    errors.password = '';
  }

  return isValid;
};

const onSubmit = async () => {
  if (!validateForm()){
    ElMessage.error('請填寫完整欄位');
    return
  }
  // if (!form.username || !form.email || !form.password) return alert('請填寫完整欄位')
  try {
    const res = await handleRegister(form)
    if (res?.token) {
      ElMessage.success("註冊成功");
      router.push('/login') // 註冊完畢，導向登入畫面
    }
  } catch (err: any) {
    console.error('API 呼叫失敗:', err);
  }
}


// 2. 判斷表單是否允許送出
const isFormValid = computed(() => {
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  return form.username.trim() !== '' &&
         form.email.trim() !== '' &&
         emailRegex.test(form.email) &&
         form.password.trim().length >= 6 &&
         errors.username === '' &&
         errors.email === '' &&
         errors.password === '';
});
</script>

<template>
  <div class="auth-page"> <el-card class="auth-card">
      <template #header>
        <h2 class="auth-title">註冊新帳號</h2>
      </template>

      <el-form label-position="top" @submit.prevent="onSubmit" :model="form">
        <el-form-item label="使用者名稱" :error="errors.username">
          <el-input v-model="form.username" :prefix-icon="User" placeholder="請輸入使用者名稱" @blur="validateForm"/>
        </el-form-item>
        <el-form-item label="Email" :error="errors.email">
          <el-input v-model="form.email" :prefix-icon="Message" placeholder="請輸入 Email" @blur="validateForm"/>
        </el-form-item>
        <el-form-item label="密碼" :error="errors.password">
          <el-input v-model="form.password" :prefix-icon="Lock" type="password" placeholder="請輸入密碼" show-password @blur="validateForm"/>
        </el-form-item>

        <el-button type="primary" :loading="loading" class="auth-btn"  :disabled="!isFormValid" native-type="submit">
          確認註冊
        </el-button>

        <div class="auth-link">
          <router-link to="/login">已有帳號？前往登入</router-link>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
@import '../assets/auth.css'; /* 引入共用樣式 */
</style>
