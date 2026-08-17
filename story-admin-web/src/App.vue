<template>
  <div v-if="isLogin" class="blank-layout">
    <RouterView />
  </div>
  <div v-else class="shell">
    <aside>
      <h1>故事管理台</h1>
      <nav>
        <a
          href="/"
          class="nav-link"
          :class="{ 'router-link-active': isEntryActive('dashboard') }"
          @click="onSidebarNav($event, '/', 'dashboard')"
        >概览</a>

        <div class="nav-group" :class="{ open: isGroupOpen('story') }">
          <button
            type="button"
            class="nav-group-title"
            :aria-expanded="isGroupOpen('story')"
            @click="toggleGroup('story')"
          >
            <span>故事管理</span>
            <span class="nav-caret" aria-hidden="true">▾</span>
          </button>
          <div v-show="isGroupOpen('story')" class="nav-children">
            <a
              href="/series"
              class="nav-link"
              :class="{ 'router-link-active': isEntryActive('story') }"
              @click="onSidebarNav($event, '/series', 'story')"
            >系列列表</a>
          </div>
        </div>

        <div class="nav-group" :class="{ open: isGroupOpen('assets') }">
          <button
            type="button"
            class="nav-group-title"
            :aria-expanded="isGroupOpen('assets')"
            @click="toggleGroup('assets')"
          >
            <span>素材管理</span>
            <span class="nav-caret" aria-hidden="true">▾</span>
          </button>
          <div v-show="isGroupOpen('assets')" class="nav-children">
            <a
              href="/assets"
              class="nav-link"
              :class="{ 'router-link-active': isEntryActive('assets-workbench') }"
              @click="onSidebarNav($event, '/assets', 'assets-workbench')"
            >工作台</a>
            <a
              href="/assets/categories"
              class="nav-link"
              :class="{ 'router-link-active': isEntryActive('assets-categories') }"
              @click="onSidebarNav($event, '/assets/categories', 'assets-categories')"
            >管理配置</a>
            <a
              href="/assets/combos"
              class="nav-link"
              :class="{ 'router-link-active': isEntryActive('asset-combos') }"
              @click="onSidebarNav($event, '/assets/combos', 'asset-combos')"
            >组合编排</a>
          </div>
        </div>

        <a
          href="/ai-reference"
          class="nav-link"
          :class="{ 'router-link-active': isEntryActive('ai-reference') }"
          @click="onSidebarNav($event, '/ai-reference', 'ai-reference')"
        >AI 参考区</a>

        <div class="nav-group" :class="{ open: isGroupOpen('characters') }">
          <button
            type="button"
            class="nav-group-title"
            :aria-expanded="isGroupOpen('characters')"
            @click="toggleGroup('characters')"
          >
            <span>人物管理</span>
            <span class="nav-caret" aria-hidden="true">▾</span>
          </button>
          <div v-show="isGroupOpen('characters')" class="nav-children">
            <a
              href="/characters"
              class="nav-link"
              :class="{ 'router-link-active': isEntryActive('characters') }"
              @click="onSidebarNav($event, '/characters', 'characters')"
            >人物</a>
            <a
              href="/character-identities"
              class="nav-link"
              :class="{ 'router-link-active': isEntryActive('character-identities') }"
              @click="onSidebarNav($event, '/character-identities', 'character-identities')"
            >人物本体</a>
          </div>
        </div>

        <a
          href="/recycle"
          class="nav-link"
          :class="{ 'router-link-active': isEntryActive('recycle') }"
          @click="onSidebarNav($event, '/recycle', 'recycle')"
        >回收站</a>
        <a
          href="/config"
          class="nav-link"
          :class="{ 'router-link-active': isEntryActive('sys-config') }"
          @click="onSidebarNav($event, '/config', 'sys-config')"
        >系统配置</a>
      </nav>
      <div class="aside-footer">
        <div class="user-line">{{ displayName }}</div>
        <div class="user-actions">
          <button type="button" class="linkish" @click="pwdVisible = true">改密</button>
          <button type="button" class="linkish" @click="onLogout">退出</button>
        </div>
      </div>
    </aside>
    <div class="shell-main">
      <TabBar />
      <main>
        <RouterView v-slot="{ Component }">
          <KeepAlive :max="20">
            <component
              :is="Component"
              :key="viewKey"
            />
          </KeepAlive>
        </RouterView>
      </main>
    </div>

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
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { changePassword, logout, useAuthState } from './auth/session';
import TabBar from './tabs/TabBar.vue';
import {
  activeTabRef,
  clearTabsStorage,
  findTabByEntryKey,
  openOrActivateEntry,
  restoreTabsFromStorage,
  syncRouteToTabs,
} from './tabs/tabStore';
import { OVERVIEW_TAB, resolveEntryKey, resolveTabTitle } from './tabs/tabTitles';

type NavGroupId = 'story' | 'assets' | 'characters';

const NAV_OPEN_KEY = 'story-admin-nav-open';

const SIDEBAR_TITLES: Record<string, string> = {
  dashboard: '概览',
  story: '系列列表',
  'assets-workbench': '素材工作台',
  'assets-categories': '素材配置',
  'asset-combos': '组合编排',
  'ai-reference': 'AI 参考区',
  characters: '人物',
  'character-identities': '人物本体',
  recycle: '回收站',
  'sys-config': '系统配置',
};

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

const groupOpen = reactive<Record<NavGroupId, boolean>>({
  story: true,
  assets: true,
  characters: true,
});

const viewKey = computed(() => {
  const tab = activeTabRef.value;
  if (!tab) return route.fullPath;
  return `${tab.tabId}:${tab.refreshNonce}:${route.fullPath}`;
});

function loadNavOpenState() {
  try {
    const raw = sessionStorage.getItem(NAV_OPEN_KEY);
    if (!raw) return;
    const parsed = JSON.parse(raw) as Partial<Record<NavGroupId, boolean>>;
    for (const key of Object.keys(groupOpen) as NavGroupId[]) {
      if (typeof parsed[key] === 'boolean') {
        groupOpen[key] = parsed[key]!;
      }
    }
  } catch {
    // ignore bad storage
  }
}

function persistNavOpenState() {
  sessionStorage.setItem(NAV_OPEN_KEY, JSON.stringify({ ...groupOpen }));
}

function isGroupOpen(id: NavGroupId): boolean {
  return groupOpen[id];
}

function toggleGroup(id: NavGroupId) {
  groupOpen[id] = !groupOpen[id];
  persistNavOpenState();
}

function groupForPath(path: string): NavGroupId | null {
  if (
    path.startsWith('/series') ||
    path.startsWith('/arcs') ||
    path.startsWith('/pages')
  ) {
    return 'story';
  }
  if (path.startsWith('/assets')) {
    return 'assets';
  }
  if (path.startsWith('/characters') || path.startsWith('/character-identities')) {
    return 'characters';
  }
  return null;
}

function ensureActiveGroupOpen() {
  const id = groupForPath(route.path);
  if (id && !groupOpen[id]) {
    groupOpen[id] = true;
    persistNavOpenState();
  }
}

function isEntryActive(entryKey: string): boolean {
  return resolveEntryKey(route) === entryKey;
}

async function onSidebarNav(event: MouseEvent, to: string, entryKey: string) {
  event.preventDefault();
  const existing = findTabByEntryKey(entryKey);
  if (existing) {
    openOrActivateEntry(entryKey, existing.fullPath, existing.title);
    if (router.currentRoute.value.fullPath !== existing.fullPath) {
      await router.push(existing.fullPath);
    }
    return;
  }
  const title = SIDEBAR_TITLES[entryKey] ?? OVERVIEW_TAB.title;
  openOrActivateEntry(entryKey, to, title);
  await router.push(to);
}

function syncTabsWithRoute() {
  if (isLogin.value) return;
  const entryKey = resolveEntryKey(route);
  const title = resolveTabTitle(route);
  syncRouteToTabs(entryKey, route.fullPath, title);
}

loadNavOpenState();
ensureActiveGroupOpen();

onMounted(() => {
  if (!isLogin.value) {
    restoreTabsFromStorage();
    syncTabsWithRoute();
  }
});

watch(
  () => route.fullPath,
  () => {
    ensureActiveGroupOpen();
    syncTabsWithRoute();
  },
);

watch(isLogin, (login) => {
  if (login) {
    clearTabsStorage();
  } else {
    restoreTabsFromStorage();
    syncTabsWithRoute();
  }
});

async function onLogout() {
  clearTabsStorage();
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
.shell-main {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 100vh;
}
.shell-main > main {
  flex: 1;
  min-height: 0;
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
