<template>
  <div class="shell">
    <aside>
      <h1>故事管理台</h1>
      <nav>
        <RouterLink to="/">概览</RouterLink>
        <span class="nav-muted">故事管理</span>
        <div class="nav-group">
          <div class="nav-group-title">素材管理</div>
          <div class="nav-children">
            <RouterLink to="/assets">工作台</RouterLink>
            <RouterLink to="/assets/categories">管理配置</RouterLink>
            <RouterLink to="/assets/combos">组合编排</RouterLink>
          </div>
        </div>
        <RouterLink to="/ai-reference">AI 参考区</RouterLink>
        <RouterLink to="/characters">人物管理</RouterLink>
        <RouterLink to="/recycle">回收站</RouterLink>
        <RouterLink to="/config">系统配置</RouterLink>
      </nav>
    </aside>
    <main>
      <RouterView />
    </main>
  </div>
</template>
