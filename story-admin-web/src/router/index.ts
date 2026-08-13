import { createRouter, createWebHistory } from 'vue-router';
import Dashboard from '../views/Dashboard.vue';
import SysConfig from '../views/config/SysConfig.vue';
import CharacterList from '../views/characters/CharacterList.vue';
import AssetWorkbench from '../views/assets/AssetWorkbench.vue';
import AiReference from '../views/ai/AiReference.vue';
import RecycleBin from '../views/recycle/RecycleBin.vue';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'dashboard', component: Dashboard },
    { path: '/assets', name: 'assets', component: AssetWorkbench },
    { path: '/ai-reference', name: 'ai-reference', component: AiReference },
    { path: '/characters', name: 'characters', component: CharacterList },
    { path: '/recycle', name: 'recycle', component: RecycleBin },
    { path: '/config', name: 'sys-config', component: SysConfig },
  ],
});

export default router;
