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

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'dashboard', component: Dashboard },
    { path: '/assets', name: 'assets-workbench', component: AssetWorkbench },
    { path: '/assets/categories', name: 'assets-categories', component: CategoryManage },
    { path: '/assets/combos', name: 'asset-combos', component: ComboList },
    { path: '/assets/combos/:id', name: 'asset-combo-edit', component: ComboEditor },
    { path: '/ai-reference', name: 'ai-reference', component: AiReference },
    { path: '/series', name: 'series', component: SeriesList },
    { path: '/characters', name: 'characters', component: CharacterList },
    { path: '/character-identities', name: 'character-identities', component: IdentityList },
    {
      path: '/character-identities/:id',
      name: 'character-identity-edit',
      component: IdentityEditor,
    },
    { path: '/recycle', name: 'recycle', component: RecycleBin },
    { path: '/config', name: 'sys-config', component: SysConfig },
  ],
});

export default router;
