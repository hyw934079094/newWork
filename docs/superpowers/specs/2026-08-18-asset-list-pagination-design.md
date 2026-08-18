# 设计：素材列表分页 + 批量 hydrate

> 状态：已确认待实现  
> 日期：2026-08-18  
> 范围：`story-admin-server` `GET /api/assets`；`story-admin-web` 所有 `listAssets` 调用方  
> 相关：`2026-08-13-asset-module-design.md`、`2026-08-15-asset-scope-sort-design.md`

---

## 1. 目标

素材列表改为分页请求，避免一次拉全库；同批消除列表 hydrate 的 N+1。工作台在分页模式下不拖拽排序，通过「展示全部」进入全量后再排序。

## 2. 已确认决策

| 项 | 选择 |
|----|------|
| 覆盖范围 | **所有** `listAssets` 调用方（工作台、回收站、AI 参考、各弹窗选择器） |
| API 形态 | 响应改为分页 DTO（非裸数组、非仅 Header） |
| 默认 `size` | **48** |
| 工作台分页时 | **禁止拖拽排序** |
| 工作台全量 | 「展示全部」；`total > 200` 先确认再拉全量；可切回分页 |
| 回收站 / AI 参考 | 底部分页条 |
| 弹窗选择器 | 「加载更多」 |
| hydrate | 对当前页（或全量结果）**批量**装载标签/人物/系列/篇章 |

## 3. API

### 3.1 请求

`GET /api/assets` 保留现有筛选参数：

- `categoryId`, `status`, `q`, `characterFilter`, `characterId`, `linkType`, `seriesId`, `arcId`

新增：

| 参数 | 类型 | 默认 | 说明 |
|------|------|------|------|
| `page` | int | `0` | 从 0 起；`< 0` → 400 |
| `size` | int | `48` | `1..5000`；超出 → 400。展示全部时前端用较大 `size`（通常 ≥ `total`） |

### 3.2 响应

```json
{
  "items": [],
  "page": 0,
  "size": 48,
  "total": 0
}
```

- `items`：当前页 `Asset`（已 hydrate）
- `total`：当前筛选条件下的总条数（应用 scope 排序规则后的逻辑列表长度）
- **破坏性变更**：不再返回 `Asset[]` 根数组

DTO 建议：`AssetPageResponse(List<Asset> items, int page, int size, long total)`。

### 3.3 服务端算法（首期）

1. 按现有 `search` + `applyScopeOrder` 得到**有序完整候选列表**（与今日语义一致）。
2. `total = list.size()`。
3. 按 `page`/`size` 切片：`from = page * size`，若 `from >= total` 则 `items = []`。
4. 对切片结果 **`hydrateAll(items)`**（批量），返回 DTO。

说明：首期不把分页下推到 SQL，优先行为正确、改动面可控。库很大时再开「DB 分页」迭代。

### 3.4 批量 hydrate

对一批 asset id：

- 批量查 tag 名、characterIds、seriesIds、arcIds（按现有仓库能力新增 `find…ByAssetIdIn` 或等价查询）
- 内存填回每条 `Asset`，并 `deriveLinkType`

单条 `get` / `update` 等仍可走原 `hydrate` 或共用 `hydrateAll(List.of(one))`。

## 4. 前端

### 4.1 `listAssets` API 封装

- 返回类型改为 `AssetPageResponse`
- 参数增加 `page?`, `size?`

### 4.2 素材工作台

| 模式 | 行为 |
|------|------|
| 分页（默认） | 请求 `page`/`size=48`；底部分页条；**关闭/忽略拖拽排序** |
| 展示全部 | 若上次/`total > 200`，`ElMessageBox` 确认；再以足够大的 `size`（或 `page=0&size=max(total,48)`）一次加载；**启用拖拽**；提供「分页浏览」切回 |
| 筛选变化 | 重置到 `page=0`；若处于全量模式，按产品选择：**切回分页**（推荐，避免误在大结果集排序） |

排序 API（`reorder` / `reorder-by-scope`）不变；仅全量模式触发。

### 4.3 回收站 / AI 参考

- 底部分页条；`page`/`size` 加载
- 回收站「全部恢复/删除」：仍按**当前筛选下全部**语义时，需先拿 `total` 再分页拉齐 id，或后续加批量 API——**本期**：全部操作前循环分页拉完全部 `DELETED` id 再执行（或保留一次大 `size` 仅用于 bulk 前置拉取）。推荐：bulk 前用 `size=min(total,5000)` 拉齐当前筛选结果再逐条调用。

### 4.4 弹窗选择器

涉及：`PageEditor`、`SeriesList`、`ArcList`、`CharacterList`、`ComboEditor`、`IdentityEditor` 等。

- 首次 `page=0&size=48`
- 「加载更多」：`page++`，**追加** `items`
- 筛选/打开弹窗：重置 page 与列表

## 5. 测试要点

| 用例 | 期望 |
|------|------|
| 默认无 page/size | `page=0,size=48`，返回 DTO |
| 第二页 | `items` 为切片；`total` 不变 |
| 空页（page 超出） | `items=[]`，`total` 仍正确 |
| size=0 / size>5000 / page<0 | 400 |
| hydrate | 页内 N 条不触发 N×4 次逐条 rel 查询（可用测试断言字段填齐；或集成测） |
| 筛选 + 分页 | 与旧全量列表切片一致（可用小数据集对比） |

前端：工作台切换全量/分页；选择器加载更多追加。

## 6. 非目标

- 不拆分 `AssetService` 大文件（仅分页与 hydrate）
- 不改上传、回收、硬删、batch-link 协议（batch-link 仍收 id 列表）
- 不做跨页拖拽；不做 DB 级 offset 优化
- 不改 `sys_config` / 其它模块列表分页

## 7. 实现落点（预览）

- `AssetPageResponse` + `AssetController.list` / `AssetService.listPage`
- Repository 批量 rel 查询 + `hydrateAll`
- `story-admin-web/src/api/asset.ts`
- `AssetWorkbench.vue`、`RecycleBin.vue`、`AiReference.vue`、各 picker
- `Asset*Test` 增加分页用例；更新依赖 `list()` 返回类型的测试
