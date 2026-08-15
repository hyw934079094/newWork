# 素材工作台筛选作用域独立排序 验收（2026-08-15）

> 规格：[设计说明](../specs/2026-08-15-asset-scope-sort-design.md) §8

## 实现提交

| Task | Commit | 说明 |
|------|--------|------|
| 1 | `a95761c` | Flyway：rel sort_order + asset_unlinked_order |
| 2 | `37c2b49` | 列表 ORDER BY 按 scope |
| 3 | `3a0a901` | `PUT /api/assets/reorder-by-scope` + 单测 |
| 4 | `628e32d` | 工作台拖拽分支与文案 |

## 验收表（Spec §8）

| # | 标准 | 结果 | 证据 |
|---|------|------|------|
| 1 | 全部+无关键字改序刷新正确 | **PARTIAL** | `AssetReorderTest#reorderUpdatesSortOrder` PASS；浏览器刷新点验 **NOT TESTED** |
| 2 | 人物 scope 改序不影响 asset.sortOrder | **PASS** | `AssetScopeReorderTest#reorderByCharacterDoesNotChangeAssetSortOrder`、`#listByCharacterUsesRelSortOrderNotAssetSortOrder` |
| 3 | 无关联/系列/篇章同理 | **PASS** | `AssetScopeReorderTest#reorderBySeriesDoesNotChangeAssetSortOrder`、`#reorderByArcDoesNotChangeAssetSortOrder`、`#reorderByUnlinkedDoesNotChangeAssetSortOrder` |
| 4 | 关键字临时拖，刷新恢复 | **PARTIAL** | `AssetWorkbench.vue`：`hasKeyword` 时 `onThumbsChange` 不调 API；浏览器拖/刷新 **NOT TESTED** |
| 5 | 跨分类仍可用 | **PASS** | `AssetReorderTest#moveUpdatesCategoryAndSortOrderWithoutCopyingFile`；UI `moveAsset` 路径未改 |
| 6 | ids 不匹配 400 + UI 回滚 | **PARTIAL** | `AssetScopeReorderTest#reorderByScopeRejectsMismatchedIds` → 400 PASS；UI `catch` 调 `restoreDragSnapshot()`，浏览器点验 **NOT TESTED** |

## 验证命令

| 命令 | 结果 |
|------|------|
| `JAVA_HOME=D:\jdk\jdk-24.0.1 mvn "-Dtest=AssetScopeReorderTest,AssetReorderTest" test`（story-admin-server） | **PASS** — Tests run: 8, Failures: 0, Errors: 0 |
| `npm run build`（story-admin-web） | **PASS** — exit 0，built ~11s |
