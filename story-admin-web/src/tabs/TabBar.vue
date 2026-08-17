<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import {
  activateTab,
  activeTabIdRef,
  bumpRefresh,
  closeAllTabs,
  closeOtherTabs,
  closeTab,
  closeTabsToTheLeft,
  closeTabsToTheRight,
  tabsRef,
} from './tabStore';
import { OVERVIEW_TAB } from './tabTitles';

const router = useRouter();

const tabs = tabsRef;
const activeTabId = activeTabIdRef;

const menuVisible = ref(false);
const menuX = ref(0);
const menuY = ref(0);
const menuTabId = ref<string | null>(null);

const menuTabIndex = computed(() =>
  menuTabId.value == null
    ? -1
    : tabs.value.findIndex((t) => t.tabId === menuTabId.value),
);

const canCloseOthers = computed(() => tabs.value.length > 1);
const canCloseLeft = computed(() => menuTabIndex.value > 0);
const canCloseRight = computed(
  () => menuTabIndex.value >= 0 && menuTabIndex.value < tabs.value.length - 1,
);

function hideMenu() {
  menuVisible.value = false;
  menuTabId.value = null;
}

onMounted(() => {
  window.addEventListener('click', hideMenu);
  window.addEventListener('scroll', hideMenu, true);
});

onUnmounted(() => {
  window.removeEventListener('click', hideMenu);
  window.removeEventListener('scroll', hideMenu, true);
});

async function onSelect(tab: (typeof tabs.value)[number]) {
  activateTab(tab.tabId);
  if (router.currentRoute.value.fullPath !== tab.fullPath) {
    await router.push(tab.fullPath);
  }
}

async function onClose(tab: (typeof tabs.value)[number], event?: Event) {
  event?.stopPropagation();
  const { nextFullPath } = closeTab(tab.tabId, OVERVIEW_TAB);
  if (nextFullPath != null && router.currentRoute.value.fullPath !== nextFullPath) {
    await router.push(nextFullPath);
  }
}

function onMiddleClick(tab: (typeof tabs.value)[number], event: MouseEvent) {
  if (event.button !== 1) return;
  event.preventDefault();
  void onClose(tab);
}

function onContextMenu(tab: (typeof tabs.value)[number], event: MouseEvent) {
  event.preventDefault();
  event.stopPropagation();
  menuTabId.value = tab.tabId;
  menuX.value = event.clientX;
  menuY.value = event.clientY;
  menuVisible.value = true;
}

async function runMenu(action: string) {
  const tabId = menuTabId.value;
  hideMenu();
  if (!tabId) return;
  const tab = tabs.value.find((t) => t.tabId === tabId);
  if (!tab) return;

  if (action === 'close') {
    await onClose(tab);
    return;
  }
  if (action === 'close-others') {
    closeOtherTabs(tabId);
    if (router.currentRoute.value.fullPath !== tab.fullPath) {
      await router.push(tab.fullPath);
    }
    return;
  }
  if (action === 'close-all') {
    const path = closeAllTabs(OVERVIEW_TAB);
    if (router.currentRoute.value.fullPath !== path) {
      await router.push(path);
    }
    return;
  }
  if (action === 'close-left') {
    closeTabsToTheLeft(tabId);
    return;
  }
  if (action === 'close-right') {
    closeTabsToTheRight(tabId);
    if (router.currentRoute.value.fullPath !== tab.fullPath) {
      await router.push(tab.fullPath);
    }
    return;
  }
  if (action === 'refresh') {
    bumpRefresh(tabId);
    activateTab(tabId);
    if (router.currentRoute.value.fullPath !== tab.fullPath) {
      await router.push(tab.fullPath);
    } else {
      await router.replace(tab.fullPath);
    }
  }
}
</script>

<template>
  <div class="tab-bar">
    <div class="tab-scroll">
      <button
        v-for="tab in tabs"
        :key="tab.tabId"
        type="button"
        class="tab-item"
        :class="{ active: tab.tabId === activeTabId }"
        @click="onSelect(tab)"
        @mousedown="onMiddleClick(tab, $event)"
        @contextmenu="onContextMenu(tab, $event)"
      >
        <span class="tab-title" :title="tab.title">{{ tab.title }}</span>
        <span class="tab-close" title="关闭" @click="onClose(tab, $event)">×</span>
      </button>
    </div>

    <Teleport to="body">
      <ul
        v-if="menuVisible"
        class="tab-context-menu"
        :style="{ left: `${menuX}px`, top: `${menuY}px` }"
        @click.stop
      >
        <li @click="runMenu('refresh')">刷新</li>
        <li class="sep" />
        <li @click="runMenu('close')">关闭</li>
        <li :class="{ disabled: !canCloseOthers }" @click="canCloseOthers && runMenu('close-others')">
          关闭其它
        </li>
        <li @click="runMenu('close-all')">关闭全部</li>
        <li :class="{ disabled: !canCloseLeft }" @click="canCloseLeft && runMenu('close-left')">
          关闭左侧
        </li>
        <li :class="{ disabled: !canCloseRight }" @click="canCloseRight && runMenu('close-right')">
          关闭右侧
        </li>
      </ul>
    </Teleport>
  </div>
</template>

<style scoped>
.tab-bar {
  display: flex;
  align-items: stretch;
  background: #eef1f7;
  border-bottom: 1px solid #d8deea;
  min-height: 40px;
}
.tab-scroll {
  display: flex;
  align-items: stretch;
  gap: 2px;
  overflow-x: auto;
  padding: 6px 8px 0;
  width: 100%;
}
.tab-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 180px;
  padding: 6px 10px;
  border: 1px solid transparent;
  border-bottom: none;
  border-radius: 8px 8px 0 0;
  background: transparent;
  color: #5a6a88;
  font: inherit;
  font-size: 13px;
  cursor: pointer;
  flex-shrink: 0;
}
.tab-item:hover {
  background: rgba(255, 255, 255, 0.7);
  color: #172033;
}
.tab-item.active {
  background: #fff;
  color: #172033;
  font-weight: 600;
  border-color: #d8deea;
  box-shadow: 0 -1px 0 #fff;
}
.tab-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tab-close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border-radius: 4px;
  font-size: 14px;
  line-height: 1;
  color: #8a97b0;
  flex-shrink: 0;
}
.tab-close:hover {
  background: #e6ebf2;
  color: #172033;
}
</style>

<style>
.tab-context-menu {
  position: fixed;
  z-index: 4000;
  margin: 0;
  padding: 6px 0;
  list-style: none;
  min-width: 140px;
  background: #fff;
  border: 1px solid #d8deea;
  border-radius: 8px;
  box-shadow: 0 10px 30px #24325222;
  font-size: 13px;
  color: #172033;
}
.tab-context-menu li {
  padding: 8px 14px;
  cursor: pointer;
}
.tab-context-menu li:hover:not(.disabled):not(.sep) {
  background: #f4f6fa;
}
.tab-context-menu li.disabled {
  color: #b0b8c9;
  cursor: default;
}
.tab-context-menu li.sep {
  height: 1px;
  padding: 0;
  margin: 4px 0;
  background: #e6ebf2;
  cursor: default;
}
</style>
