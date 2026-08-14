<template>
  <div class="login-page">
    <form class="login-card" @submit.prevent="onSubmit">
      <h1>故事管理台</h1>
      <p class="hint">使用管理员账号登录</p>
      <label>
        账号
        <input v-model.trim="username" autocomplete="username" />
      </label>
      <label>
        密码
        <input v-model="password" type="password" autocomplete="current-password" />
      </label>
      <p v-if="error" class="error">{{ error }}</p>
      <button type="submit" :disabled="loading">{{ loading ? '登录中…' : '登录' }}</button>
    </form>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { login } from '../../auth/session';

const router = useRouter();
const route = useRoute();
const username = ref('admin');
const password = ref('admin');
const loading = ref(false);
const error = ref('');

async function onSubmit() {
  error.value = '';
  loading.value = true;
  try {
    await login(username.value, password.value);
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/';
    await router.replace(redirect || '/');
  } catch (e: unknown) {
    error.value = '账号或密码错误';
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  background: linear-gradient(160deg, #1a2744 0%, #2c3f66 45%, #f4f6fa 45%);
  padding: 24px;
}
.login-card {
  width: min(380px, 100%);
  background: #fff;
  border-radius: 12px;
  padding: 32px 28px;
  box-shadow: 0 18px 40px rgba(24, 35, 59, 0.18);
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.login-card h1 {
  margin: 0;
  font-size: 22px;
  color: #18233b;
}
.hint {
  margin: -6px 0 8px;
  color: #6b778c;
  font-size: 13px;
}
label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  color: #334155;
}
input {
  border: 1px solid #d0d7e2;
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 14px;
}
button {
  margin-top: 8px;
  border: 0;
  border-radius: 8px;
  padding: 11px 14px;
  background: #2f6fed;
  color: #fff;
  font-weight: 600;
  cursor: pointer;
}
button:disabled {
  opacity: 0.7;
  cursor: default;
}
.error {
  margin: 0;
  color: #c62828;
  font-size: 13px;
}
</style>
