<template>
  <div class="shell">
    <aside>
      <h1>故事管理台</h1>
      <nav>
        <RouterLink to="/">概览</RouterLink>
        <span>故事管理</span>
        <span>素材管理</span>
        <RouterLink to="/characters">人物管理</RouterLink>
        <span>分类与标签</span>
        <RouterLink to="/config">系统配置</RouterLink>
      </nav>
    </aside>
    <main>
      <RouterView />
    </main>
  </div>
</template>
