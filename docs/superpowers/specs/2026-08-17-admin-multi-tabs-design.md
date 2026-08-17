# 设计：管理台多 Tab 并行页

> 状态：已确认待实现  
> 日期：2026-08-17  
> 范围：`story-admin-web`（`App.vue` / router / 新 Tab 状态模块）  
> 挂起：篇章挂组合见 `2026-08-17-page-beat-combo-design.md`（本规格之后再实现）

---

## 1. 目标

侧栏可并行打开多个业务页：主区上方横向 Tab，点击切换回先前页面；右键提供关闭类操作与刷新。页内下钻不另开 Tab。

## 2. 已确认决策

| 项 | 选择 |
|----|------|
| 页内下钻（系列→篇章→编辑等） | **当前 Tab 内前进**，标题/路径随路由更新 |
| 侧栏点已开入口 | **激活已有 Tab**，不重复开 |
| 右键菜单 | 关闭、关闭其它、关闭全部、关闭左侧、关闭右侧、刷新 |
| 实现路线 | Tab 栏 + `keep-alive` 缓存页状态 |
| 登录页 | blank layout，无 Tab |

## 3. 信息架构

```
┌──────────┬─────────────────────────────────────┐
│  侧栏    │  [Tab1] [Tab2*] [Tab3] …             │
│          ├─────────────────────────────────────┤
│          │  keep-alive + RouterView（当前 Tab）  │
└──────────┴─────────────────────────────────────┘
```

- 仅 shell 布局显示 Tab 条；`meta.layout === 'blank'` / 登录页不显示。
- 浏览器地址栏始终对应当前激活 Tab 的 `fullPath`。

## 4. Tab 数据模型

```ts
type TabItem = {
  key: string;       // 去重键，默认 = fullPath
  fullPath: string;
  title: string;     // 展示标题
  name?: string | symbol | null; // route.name，供 keep-alive / 刷新
};
```

- **Store**：模块级 reactive / 小 composable（或 pinia，若项目已有则复用；当前无 pinia 则用 `src/tabs/tabStore.ts`）。
- **列表**：有序数组 + `activeKey`。
- **持久化（本期做）**：`sessionStorage` 存 `{ tabs, activeKey }`；登录成功后的 shell 挂载时恢复；退出登录清空。

## 5. 行为规则

### 5.1 路由 → Tab

| 场景 | 行为 |
|------|------|
| 侧栏 `RouterLink` / 编程导航到某 path | 若存在同 `key` → 激活；否则 `push` 新 Tab 并激活 |
| 页内链接 / `router.push`（非侧栏） | 更新**当前** Tab 的 `fullPath` 与 `title`（key 随 fullPath 变：从列表旧 key 迁到新 key，保持顺序位置） |
| 浏览器前进/后退 | 同步激活或更新对应 Tab（与 vue-router 历史一致） |
| 直接打开 URL / 刷新 | 恢复 session 列表；若当前 path 不在列表则补一个 Tab |

**标题**：优先 `route.meta.title`；无则用静态映射表（概览、系列列表、篇章、页面编辑…）；动态页可用「页面编辑」「组合编辑」等 + 必要时短 id。

### 5.2 关闭

| 动作 | 行为 |
|------|------|
| 关闭 | 移除该 Tab；若关的是当前 → 激活右侧邻近，否则左侧；若已空 → 打开/激活「概览」`/` |
| 关闭其它 | 只留该 Tab |
| 关闭全部 | 清空后落到「概览」 |
| 关闭左侧 / 右侧 | 按数组下标删除该侧全部 |
| 刷新 | 将该 Tab 对应组件从 `keep-alive` 剔除（`include` 名单去掉或 bump `refreshKey`），再 `router.replace` 同 path |

最后一个「概览」Tab：**允许关闭**（关后重建概览）；或禁止关闭概览——**本期允许关闭后自动重建概览**，保证至少有一个落地页。

### 5.3 交互

- 左键点 Tab：`router.push(tab.fullPath)`。
- 中键点 Tab（可选）：关闭该 Tab——**本期做**（常见习惯）。
- 右键：自定义上下文菜单（阻止浏览器默认菜单），展示上述 6 项；无左侧时「关闭左侧」禁用，无右侧同理；仅一个 Tab 时「关闭其它」禁用。
- Tab 过多：横向滚动，不换行。

## 6. 技术触点

| 文件 / 模块 | 职责 |
|-------------|------|
| `src/tabs/tabStore.ts` | tabs / activeKey / open / close* / refresh / persist |
| `src/tabs/TabBar.vue` | 横条 UI + 右键菜单 |
| `App.vue` | TabBar + `<router-view v-slot>` + `keep-alive`；监听 `route` 同步 store |
| `router/index.ts` | 为主要路由补 `meta.title`（可选但推荐） |
| `style.css` | Tab 条与 shell 间距 |

**keep-alive 策略**：以 Tab `key`（或 `fullPath`）作为缓存名；组件需稳定 `name` 或对动态页用包裹层。页面编辑等多实例路径用 **fullPath 作 cache key**（Vue 3 keep-alive `include` 按组件 name 时不够用 → 采用 **多个 `RouterView` 隐藏切换** 或 **`<KeepAlive :max="N">` + 自定义 key 的包装**）。

**推荐落地实现**：

```vue
<router-view v-slot="{ Component, route }">
  <keep-alive :max="20">
    <component :is="Component" :key="route.fullPath" />
  </keep-alive>
</router-view>
```

注意：`:key="fullPath"` 会使路径变化时换实例（页内下钻会丢缓存）——与「同 Tab 下钻更新路径」冲突。

**修正策略（明确）**：

- 每个 Tab 分配稳定 **`tabId`（uuid）**，不随页内下钻改变。
- `key` / keep-alive 缓存键 = `tabId`。
- 页内下钻只改该 Tab 的 `fullPath` + `title`，**不改 `tabId`**，从而同 Tab 内组件是否重建：

  - 若同组件类型下钻（少见）：可保留。
  - 系列列表 → 篇章列表是**不同组件**：路由切换必然换组件；`keep-alive` 按组件类型缓存时，**同一 tabId 下后一个路由会替换前一个在该 tab 槽位的显示**，返回侧栏其它 Tab 再回来时，应恢复该 Tab **当前** fullPath 对应页。

  实现上用「Tab 槽」模式更稳：

  ```text
  对每个未关 Tab 保留一个缓存入口（tabId → 当前 Component + fullPath）
  仅挂载 active Tab 的视图；非 active 用 v-show 或 keep-alive 按 tabId 包一层
  ```

  **本期简化（可接受）**：

  1. 激活 Tab 时 `router.push(fullPath)`，单一 `RouterView`。
  2. `keep-alive` 按 **组件 name** 缓存（同类型页共享一份缓存，例如只缓存一个 PageEditor）——弱一些。
  3. **或** 不做跨类型完美缓存：切换 Tab 时正常路由切换，仅保证 **地址与 Tab 列表正确**；表单是否保留 best-effort。

**本期明确范围（避免超做）**：

- **必须**：Tab 列表、开/切/关（含右键六项）、与侧栏去重、页内下钻改当前 Tab、地址栏同步、sessionStorage 恢复。
- **必须**：刷新 = 对当前 Tab `router.replace` + 强制重挂（可用 `:key="tabId + ':' + refreshNonce"`）。
- **尽力**：`keep-alive` 按路由组件 name、`max=20`；不保证「两个不同 pageId 的编辑器同时保活」——若需强保活，二期做 Tab 槽多实例。

## 7. 非目标

- 浏览器级真正多窗口。
- Tab 拖拽排序（可二期）。
- 跨设备持久化。
- 改造后端。

## 8. 验收

1. 侧栏开「素材工作台」再开「故事管理」→ 两条 Tab，可来回点。
2. 再点「素材工作台」→ 切回已有，不新增。
3. 系列进篇章进编辑 → 仍一条 Tab，标题更新。
4. 右键：关 / 关其它 / 全关 / 左 / 右 / 刷新 行为正确；边界禁用正确。
5. 刷新浏览器（同会话）Tab 列表可恢复；退出登录后清空。
6. 登录页无 Tab 条。

## 9. 风险

| 风险 | 缓解 |
|------|------|
| keep-alive 与页内换组件 | 本期 best-effort；验收以 Tab 导航正确为主 |
| 标题不准 | `meta.title` + 映射表 |
| 右键与浏览器菜单冲突 | `preventDefault` + 自定义菜单 |
