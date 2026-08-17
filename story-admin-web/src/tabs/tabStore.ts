import { computed, reactive } from 'vue';

export type TabItem = {
  tabId: string;
  /** Stable sidebar bucket for dedupe (e.g. series, assets-workbench). */
  entryKey: string;
  fullPath: string;
  title: string;
  refreshNonce: number;
};

const STORAGE_KEY = 'story-admin-tabs-v1';

const state = reactive({
  tabs: [] as TabItem[],
  activeTabId: null as string | null,
});

export const tabsRef = computed(() => state.tabs);
export const activeTabIdRef = computed(() => state.activeTabId);
export const activeTabRef = computed(
  () => state.tabs.find((t) => t.tabId === state.activeTabId) ?? null,
);

function persist() {
  try {
    sessionStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({
        tabs: state.tabs,
        activeTabId: state.activeTabId,
      }),
    );
  } catch {
    // ignore quota / private mode
  }
}

function newTabId(): string {
  return `tab-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}

export function getTabs(): TabItem[] {
  return state.tabs;
}

export function getActiveTabId(): string | null {
  return state.activeTabId;
}

export function getActiveTab(): TabItem | null {
  return state.tabs.find((t) => t.tabId === state.activeTabId) ?? null;
}

export function clearTabsStorage() {
  state.tabs = [];
  state.activeTabId = null;
  try {
    sessionStorage.removeItem(STORAGE_KEY);
  } catch {
    // ignore
  }
}

export function restoreTabsFromStorage(): void {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) return;
    const parsed = JSON.parse(raw) as {
      tabs?: TabItem[];
      activeTabId?: string | null;
    };
    if (!Array.isArray(parsed.tabs) || parsed.tabs.length === 0) return;
    state.tabs = parsed.tabs
      .filter(
        (t) =>
          t &&
          typeof t.tabId === 'string' &&
          typeof t.entryKey === 'string' &&
          typeof t.fullPath === 'string' &&
          typeof t.title === 'string',
      )
      .map((t) => ({
        ...t,
        refreshNonce: typeof t.refreshNonce === 'number' ? t.refreshNonce : 0,
      }));
    if (
      parsed.activeTabId &&
      state.tabs.some((t) => t.tabId === parsed.activeTabId)
    ) {
      state.activeTabId = parsed.activeTabId;
    } else {
      state.activeTabId = state.tabs[0]?.tabId ?? null;
    }
  } catch {
    // ignore
  }
}

export function findTabByEntryKey(entryKey: string): TabItem | undefined {
  return state.tabs.find((t) => t.entryKey === entryKey);
}

export function findTabByFullPath(fullPath: string): TabItem | undefined {
  return state.tabs.find((t) => t.fullPath === fullPath);
}

export function findTabById(tabId: string): TabItem | undefined {
  return state.tabs.find((t) => t.tabId === tabId);
}

export function openOrActivateEntry(
  entryKey: string,
  fullPath: string,
  title: string,
): TabItem {
  const existing = findTabByEntryKey(entryKey);
  if (existing) {
    state.activeTabId = existing.tabId;
    persist();
    return existing;
  }
  const tab: TabItem = {
    tabId: newTabId(),
    entryKey,
    fullPath,
    title,
    refreshNonce: 0,
  };
  state.tabs.push(tab);
  state.activeTabId = tab.tabId;
  persist();
  return tab;
}

export function syncRouteToTabs(
  entryKey: string,
  fullPath: string,
  title: string,
): TabItem {
  const byPath = findTabByFullPath(fullPath);
  if (byPath) {
    byPath.title = title;
    state.activeTabId = byPath.tabId;
    persist();
    return byPath;
  }

  const active = getActiveTab();
  if (active && active.entryKey === entryKey) {
    active.fullPath = fullPath;
    active.title = title;
    persist();
    return active;
  }

  const byEntry = findTabByEntryKey(entryKey);
  if (byEntry) {
    byEntry.fullPath = fullPath;
    byEntry.title = title;
    state.activeTabId = byEntry.tabId;
    persist();
    return byEntry;
  }

  return openOrActivateEntry(entryKey, fullPath, title);
}

export function activateTab(tabId: string): TabItem | null {
  const tab = findTabById(tabId);
  if (!tab) return null;
  state.activeTabId = tabId;
  persist();
  return tab;
}

export function bumpRefresh(tabId: string): TabItem | null {
  const tab = findTabById(tabId);
  if (!tab) return null;
  tab.refreshNonce += 1;
  persist();
  return tab;
}

function ensureOverviewFallback(overview: {
  entryKey: string;
  fullPath: string;
  title: string;
}): void {
  if (state.tabs.length === 0) {
    openOrActivateEntry(overview.entryKey, overview.fullPath, overview.title);
  }
}

export function closeTab(
  tabId: string,
  overview: { entryKey: string; fullPath: string; title: string },
): { nextFullPath: string | null } {
  const index = state.tabs.findIndex((t) => t.tabId === tabId);
  if (index < 0) return { nextFullPath: null };
  const wasActive = state.activeTabId === tabId;
  state.tabs.splice(index, 1);
  if (state.tabs.length === 0) {
    ensureOverviewFallback(overview);
    persist();
    return { nextFullPath: overview.fullPath };
  }
  if (wasActive) {
    const next = state.tabs[index] ?? state.tabs[index - 1] ?? state.tabs[0]!;
    state.activeTabId = next.tabId;
    persist();
    return { nextFullPath: next.fullPath };
  }
  persist();
  return { nextFullPath: null };
}

export function closeOtherTabs(tabId: string): void {
  const keep = findTabById(tabId);
  if (!keep) return;
  state.tabs = [keep];
  state.activeTabId = keep.tabId;
  persist();
}

export function closeAllTabs(overview: {
  entryKey: string;
  fullPath: string;
  title: string;
}): string {
  state.tabs = [];
  state.activeTabId = null;
  openOrActivateEntry(overview.entryKey, overview.fullPath, overview.title);
  return overview.fullPath;
}

export function closeTabsToTheLeft(tabId: string): void {
  const index = state.tabs.findIndex((t) => t.tabId === tabId);
  if (index <= 0) return;
  const removedActive = state.tabs
    .slice(0, index)
    .some((t) => t.tabId === state.activeTabId);
  state.tabs = state.tabs.slice(index);
  if (removedActive) {
    state.activeTabId = tabId;
  }
  persist();
}

export function closeTabsToTheRight(tabId: string): void {
  const index = state.tabs.findIndex((t) => t.tabId === tabId);
  if (index < 0 || index >= state.tabs.length - 1) return;
  const removedActive = state.tabs
    .slice(index + 1)
    .some((t) => t.tabId === state.activeTabId);
  state.tabs = state.tabs.slice(0, index + 1);
  if (removedActive) {
    state.activeTabId = tabId;
  }
  persist();
}
