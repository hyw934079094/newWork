import type { RouteLocationNormalizedLoaded } from 'vue-router';

/** Sidebar / drill-down bucket for tab dedupe. */
export function resolveEntryKey(route: RouteLocationNormalizedLoaded): string {
  const meta = route.meta.entryKey;
  if (typeof meta === 'string' && meta) {
    return meta;
  }
  const path = route.path;
  if (path === '/' || path === '') return 'dashboard';
  if (
    path.startsWith('/series') ||
    path.startsWith('/arcs') ||
    path.startsWith('/pages')
  ) {
    return 'story';
  }
  if (path.startsWith('/assets/combos')) return 'asset-combos';
  if (path.startsWith('/assets/categories')) return 'assets-categories';
  if (path.startsWith('/assets')) return 'assets-workbench';
  if (path.startsWith('/ai-reference')) return 'ai-reference';
  if (path.startsWith('/character-identities')) return 'character-identities';
  if (path.startsWith('/characters')) return 'characters';
  if (path.startsWith('/recycle')) return 'recycle';
  if (path.startsWith('/config')) return 'sys-config';
  return path;
}

export function resolveTabTitle(route: RouteLocationNormalizedLoaded): string {
  const metaTitle = route.meta.title;
  if (typeof metaTitle === 'string' && metaTitle.trim()) {
    return metaTitle.trim();
  }
  const name = typeof route.name === 'string' ? route.name : '';
  const map: Record<string, string> = {
    dashboard: '概览',
    series: '系列列表',
    'series-arcs': '篇章',
    'arc-pages': '页面列表',
    'arc-preview': '篇章预览',
    'page-edit': '页面编辑',
    'assets-workbench': '素材工作台',
    'assets-categories': '素材配置',
    'asset-combos': '组合编排',
    'asset-combo-edit': '组合编辑',
    'ai-reference': 'AI 参考区',
    characters: '人物',
    'character-identities': '人物本体',
    'character-identity-edit': '本体编辑',
    recycle: '回收站',
    'sys-config': '系统配置',
  };
  if (name && map[name]) {
    return map[name];
  }
  return resolveEntryKey(route);
}

export const OVERVIEW_TAB = {
  entryKey: 'dashboard',
  fullPath: '/',
  title: '概览',
} as const;
