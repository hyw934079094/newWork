import { createRouter, createWebHistory } from 'vue-router';
import Dashboard from '../views/Dashboard.vue';
import SysConfig from '../views/config/SysConfig.vue';
import CharacterList from '../views/characters/CharacterList.vue';
import IdentityList from '../views/identities/IdentityList.vue';
import IdentityEditor from '../views/identities/IdentityEditor.vue';
import AssetWorkbench from '../views/assets/AssetWorkbench.vue';
import CategoryManage from '../views/assets/CategoryManage.vue';
import ComboList from '../views/combos/ComboList.vue';
import ComboEditor from '../views/combos/ComboEditor.vue';
import AiReference from '../views/ai/AiReference.vue';
import RecycleBin from '../views/recycle/RecycleBin.vue';
import SeriesList from '../views/series/SeriesList.vue';
import ArcList from '../views/arcs/ArcList.vue';
import ArcPreview from '../views/arcs/ArcPreview.vue';
import PageList from '../views/pages/PageList.vue';
import PageEditor from '../views/pages/PageEditor.vue';
import Login from '../views/login/Login.vue';
import { ensureAuth } from '../auth/session';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: Login,
      meta: { public: true, layout: 'blank' },
    },
    {
      path: '/',
      name: 'dashboard',
      component: Dashboard,
      meta: { title: '概览', entryKey: 'dashboard' },
    },
    {
      path: '/assets',
      name: 'assets-workbench',
      component: AssetWorkbench,
      meta: { title: '素材工作台', entryKey: 'assets-workbench' },
    },
    {
      path: '/assets/categories',
      name: 'assets-categories',
      component: CategoryManage,
      meta: { title: '素材配置', entryKey: 'assets-categories' },
    },
    {
      path: '/assets/combos',
      name: 'asset-combos',
      component: ComboList,
      meta: { title: '组合编排', entryKey: 'asset-combos' },
    },
    {
      path: '/assets/combos/:id',
      name: 'asset-combo-edit',
      component: ComboEditor,
      meta: { title: '组合编辑', entryKey: 'asset-combos' },
    },
    {
      path: '/ai-reference',
      name: 'ai-reference',
      component: AiReference,
      meta: { title: 'AI 参考区', entryKey: 'ai-reference' },
    },
    {
      path: '/series',
      name: 'series',
      component: SeriesList,
      meta: { title: '系列列表', entryKey: 'story' },
    },
    {
      path: '/series/:seriesId/arcs',
      name: 'series-arcs',
      component: ArcList,
      meta: { title: '篇章', entryKey: 'story' },
    },
    {
      path: '/arcs/:arcId/pages',
      name: 'arc-pages',
      component: PageList,
      meta: { title: '页面列表', entryKey: 'story' },
    },
    {
      path: '/arcs/:arcId/preview',
      name: 'arc-preview',
      component: ArcPreview,
      meta: { title: '篇章预览', entryKey: 'story' },
    },
    {
      path: '/pages/:pageId/edit',
      name: 'page-edit',
      component: PageEditor,
      meta: { title: '页面编辑', entryKey: 'story' },
    },
    {
      path: '/characters',
      name: 'characters',
      component: CharacterList,
      meta: { title: '人物', entryKey: 'characters' },
    },
    {
      path: '/character-identities',
      name: 'character-identities',
      component: IdentityList,
      meta: { title: '人物本体', entryKey: 'character-identities' },
    },
    {
      path: '/character-identities/:id',
      name: 'character-identity-edit',
      component: IdentityEditor,
      meta: { title: '本体编辑', entryKey: 'character-identities' },
    },
    {
      path: '/recycle',
      name: 'recycle',
      component: RecycleBin,
      meta: { title: '回收站', entryKey: 'recycle' },
    },
    {
      path: '/config',
      name: 'sys-config',
      component: SysConfig,
      meta: { title: '系统配置', entryKey: 'sys-config' },
    },
  ],
});

router.beforeEach(async (to) => {
  if (to.meta.public) {
    if (to.name === 'login') {
      const user = await ensureAuth();
      if (user) {
        return { path: '/' };
      }
    }
    return true;
  }
  const user = await ensureAuth();
  if (!user) {
    return {
      name: 'login',
      query: { redirect: to.fullPath },
    };
  }
  return true;
});

export default router;
