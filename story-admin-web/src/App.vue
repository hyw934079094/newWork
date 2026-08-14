<template>
  <div v-if="isLogin" class="blank-layout">
    <RouterView />
  </div>
  <div v-else class="shell">
    <aside>
      <h1>故事管理台</h1>
      <nav>
        <RouterLink to="/">概览</RouterLink>
        <span class="nav-muted">故事管理</span>
        <div class="nav-group">
          <div class="nav-group-title">素材管理</div>
          <div class="nav-children">
            <RouterLink
              to="/assets"
              active-class=""
              exact-active-class="router-link-active"
            >工作台</RouterLink>
            <RouterLink
              to="/assets/categories"
              active-class=""
              exact-active-class="router-link-active"
            >管理配置</RouterLink>
            <RouterLink to="/assets/combos">组合编排</RouterLink>
          </div>
        </div>
        <RouterLink to="/ai-reference">AI 参考区</RouterLink>
        <RouterLink to="/series">故事系列</RouterLink>
        <div class="nav-group">
          <div class="nav-group-title">人物管理</div>
          <div class="nav-children">
            <RouterLink
              to="/characters"
              active-class=""
              exact-active-class="router-link-active"
            >人物</RouterLink>
            <RouterLink to="/character-identities">人物本体</RouterLink>
          </div>
        </div>
        <RouterLink to="/recycle">回收站</RouterLink>
        <RouterLink to="/config">系统配置</RouterLink>
      </nav>
      <div class="aside-footer">
        <div class="user-line">{{ displayName }}</div>
        <div class="user-actions">
          <button type="button" class="linkish" @click="pwdVisible = true">改密</button>
          <button type="button" class="linkish" @click="onLogout">退出</button>
        </div>
      </div>
    </aside>
    <main>
      <RouterView />
    </main>

    <el-dialog v-model="pwdVisible" title="修改密码" width="400px">
      <el-form label-width="88px" @submit.prevent>
        <el-form-item label="当前密码">
          <el-input v-model="pwdForm.current" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="pwdForm.next" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="pwdSaving" @click="onChangePassword">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { changePassword, logout, useAuthState } from './auth/session';

const route = useRoute();
const router = useRouter();
const { currentUser } = useAuthState();

const isLogin = computed(() => route.name === 'login' || route.meta.layout === 'blank');
const displayName = computed(
  () => currentUser.value?.displayName || currentUser.value?.username || '未登录',
);

const pwdVisible = ref(false);
const pwdSaving = ref(false);
const pwdForm = reactive({ current: '', next: '' });

async function onLogout() {
  await logout();
  await router.replace({ name: 'login' });
}

async function onChangePassword() {
  if (!pwdForm.current || !pwdForm.next) {
    ElMessage.warning('请填写当前密码与新密码');
    return;
  }
  pwdSaving.value = true;
  try {
    await changePassword(pwdForm.current, pwdForm.next);
    ElMessage.success('密码已更新');
    pwdVisible.value = false;
    pwdForm.current = '';
    pwdForm.next = '';
  } catch {
    ElMessage.error('修改失败，请检查当前密码');
  } finally {
    pwdSaving.value = false;
  }
}
</script>

<style scoped>
.blank-layout {
  min-height: 100vh;
}
.aside-footer {
  margin-top: auto;
  padding-top: 24px;
  border-top: 1px solid rgba(255, 255, 255, 0.12);
  color: #aeb8cf;
  font-size: 13px;
}
.user-line {
  margin-bottom: 8px;
  color: #fff;
  font-weight: 600;
}
.user-actions {
  display: flex;
  gap: 12px;
}
.linkish {
  background: transparent;
  border: 0;
  padding: 0;
  color: #9eb6ff;
  cursor: pointer;
  font-size: 13px;
}
.linkish:hover {
  color: #fff;
}
</style>
