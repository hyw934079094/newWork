# 素材筛选作用域独立排序 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 工作台在人物/系列/篇章/无关联等固定筛选下可拖拽缩略图并持久化独立顺序；关键字搜索仅临时可拖；「全部」仍用分类 `asset.sort_order`。

**Architecture:** Flyway V10 为三张关联表加 `sort_order`，新建 `asset_unlinked_order`。列表在现有 `search` 过滤后由 `AssetService` 按作用域重排。新增 `PUT /api/assets/reorder-by-scope` 只写对应 scope 顺序。工作台按筛选状态分支调用 `reorder` / `reorder-by-scope` / 不调接口。

**Tech Stack:** Java 17+、Spring Boot 3.3、JPA、Flyway、Vue 3、Element Plus、vuedraggable

**Spec:** `docs/superpowers/specs/2026-08-15-asset-scope-sort-design.md`

## Global Constraints

- 在 `master` 上直接提交并 push
- Scope 改序**禁止**修改 `asset.sort_order`
- `orderedIds` 必须等于「当前分类 + 该 scope」下 NORMAL 可见全集
- 关键字 `q` 非空：前端可拖、不调持久化接口
- JDK：`JAVA_HOME=D:\jdk\jdk-24.0.1`；Maven：`D:\tool\apache-maven-3.9.10`；Git：`D:\tool\Git\bin\git.exe` + `-F` UTF-8 无 BOM
- 改 `.java`/`.sql` 后确认无 `%TSD-Header-###%`；必要时 `.txt` + `cmd ren`
- 测试沿用现有 `@SpringBootTest` + H2 `create-drop` + `flyway.enabled=false` 模式（实体字段即可）

---

## File Map

| Path | Responsibility |
|------|----------------|
| `db/migration/V10__asset_scope_sort.sql` | rel 加列、建 unlinked 表、回填 |
| `domain/AssetCharacterRel.java` 等 | `sortOrder` 字段 |
| `domain/AssetUnlinkedOrder.java` + Id + Repository | 无关联顺序 |
| `AssetService.java` | 列表重排、`reorderByScope`、建关联时赋序、清理 |
| `AssetController.java` + `AssetReorderByScopeRequest.java` | 新 API |
| `AssetCharacterRelRepository.java` 等 | max 序、按人物取 id 带序 |
| `AssetScopeReorderTest.java` | 作用域改序与列表序单测 |
| `api/asset.ts` | `reorderAssetsByScope` |
| `AssetWorkbench.vue` | 拖拽分支与文案 |
| `docs/superpowers/acceptance/2026-08-15-asset-scope-sort.md` | 验收 |
| `README.md` | 一行能力说明（可选） |

---

### Task 1: Flyway V10 + 实体

**Files:**
- Create: `story-admin-server/src/main/resources/db/migration/V10__asset_scope_sort.sql`
- Modify: `domain/AssetCharacterRel.java`, `AssetSeriesRel.java`, `AssetArcRel.java`
- Create: `domain/AssetUnlinkedOrder.java`, `AssetUnlinkedOrderId.java`
- Create: `repository/AssetUnlinkedOrderRepository.java`
- Modify: `repository/AssetCharacterRelRepository.java`, `AssetSeriesRelRepository.java`, `AssetArcRelRepository.java`（max 查询 + 人物 assetIds 带序）

**Interfaces:**
- Produces: `AssetCharacterRel.getSortOrder()/setSortOrder(int)`（series/arc 同理）
- Produces: `AssetUnlinkedOrder(categoryId, assetId, sortOrder)`
- Produces: `findMaxSortOrderByCharacterIdAndCategoryId(characterId, categoryId)` → `Optional<Integer>`（-1 表示无）
- Produces: `findAssetIdsByCharacterId` **改为** `order by r.sortOrder asc, r.assetId asc`

- [ ] **Step 1: 写迁移 SQL**

```sql
ALTER TABLE asset_character_rel ADD COLUMN sort_order INT NOT NULL DEFAULT 0;
ALTER TABLE asset_series_rel ADD COLUMN sort_order INT NOT NULL DEFAULT 0;
ALTER TABLE asset_arc_rel ADD COLUMN sort_order INT NOT NULL DEFAULT 0;

UPDATE asset_character_rel r
INNER JOIN asset a ON a.id = r.asset_id
SET r.sort_order = a.sort_order;

UPDATE asset_series_rel r
INNER JOIN asset a ON a.id = r.asset_id
SET r.sort_order = a.sort_order;

UPDATE asset_arc_rel r
INNER JOIN asset a ON a.id = r.asset_id
SET r.sort_order = a.sort_order;

CREATE TABLE asset_unlinked_order (
  category_id BIGINT NOT NULL,
  asset_id BIGINT NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  PRIMARY KEY (category_id, asset_id),
  CONSTRAINT fk_asset_unlinked_order_category FOREIGN KEY (category_id) REFERENCES asset_category (id),
  CONSTRAINT fk_asset_unlinked_order_asset FOREIGN KEY (asset_id) REFERENCES asset (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO asset_unlinked_order (category_id, asset_id, sort_order)
SELECT a.category_id, a.id, a.sort_order
FROM asset a
WHERE a.status = 'NORMAL'
  AND NOT EXISTS (SELECT 1 FROM asset_character_rel r WHERE r.asset_id = a.id);
```

- [ ] **Step 2: 实体加 `sortOrder`（三张 rel）**

```java
@Column(name = "sort_order", nullable = false)
private int sortOrder;

public AssetCharacterRel(Long assetId, Long characterId, int sortOrder) {
  this.assetId = assetId;
  this.characterId = characterId;
  this.sortOrder = sortOrder;
}
// 保留旧双参构造，内部 this(assetId, characterId, 0);
```

- [ ] **Step 3: `AssetUnlinkedOrder` + Repository**

```java
@Entity
@Table(name = "asset_unlinked_order")
@IdClass(AssetUnlinkedOrderId.class)
public class AssetUnlinkedOrder {
  @Id @Column(name = "category_id") private Long categoryId;
  @Id @Column(name = "asset_id") private Long assetId;
  @Column(name = "sort_order", nullable = false) private int sortOrder;
  // getters/setters + ctor
}

public interface AssetUnlinkedOrderRepository extends JpaRepository<AssetUnlinkedOrder, AssetUnlinkedOrderId> {
  void deleteByAssetId(Long assetId);
  void deleteByCategoryIdAndAssetId(Long categoryId, Long assetId);
  Optional<AssetUnlinkedOrder> findByCategoryIdAndAssetId(Long categoryId, Long assetId);
  List<AssetUnlinkedOrder> findByCategoryIdOrderBySortOrderAscAssetIdAsc(Long categoryId);

  @Query("select coalesce(max(u.sortOrder), -1) from AssetUnlinkedOrder u where u.categoryId = :categoryId")
  Optional<Integer> findMaxSortOrderByCategoryId(@Param("categoryId") Long categoryId);
}
```

- [ ] **Step 4: Rel Repository 增加 max 查询；人物 ids 带序**

```java
@Query("""
  select coalesce(max(r.sortOrder), -1) from AssetCharacterRel r, Asset a
  where r.assetId = a.id and r.characterId = :characterId and a.categoryId = :categoryId
  """)
Optional<Integer> findMaxSortOrderByCharacterIdAndCategoryId(
    @Param("characterId") Long characterId, @Param("categoryId") Long categoryId);

@Query("""
  select r.assetId from AssetCharacterRel r
  where r.characterId = :characterId
  order by r.sortOrder asc, r.assetId asc
  """)
List<Long> findAssetIdsByCharacterId(@Param("characterId") Long characterId);
```

Series/Arc 同理：`findMaxSortOrderBySeriesIdAndCategoryId` / `findMaxSortOrderByArcIdAndCategoryId`。

- [ ] **Step 5: Commit**

```bash
git add story-admin-server/src/main/resources/db/migration/V10__asset_scope_sort.sql \
  story-admin-server/src/main/java/com/story/admin/domain/ \
  story-admin-server/src/main/java/com/story/admin/repository/
git commit -m "feat: add scope sort_order columns and unlinked order table"
```

---

### Task 2: 列表按 scope 重排 + 建关联赋序

**Files:**
- Modify: `AssetService.java` — `list(...)` 末尾重排；`replaceCharacters` / `writeSeriesLinks` / `writeArcLinks` / `clearCharacterLinks` 赋序与清理 unlinked
- Modify: inject `AssetUnlinkedOrderRepository`
- Test: Create `AssetScopeReorderTest.java`（本 Task 先写「list 按人物序」用例，Task 3 补改序）

**Interfaces:**
- Consumes: Task 1 实体与 max 查询
- Produces: `list` 在 `characterId != null` / unlinked / 具体 seriesId / 具体 arcId 时按 scope 序返回
- Produces: 新建 rel 时 `sortOrder = max+1`（按分类）

- [ ] **Step 1: 失败测试 — 人物列表序跟 rel.sortOrder 而非 asset.sortOrder**

```java
@Test
void listByCharacterUsesRelSortOrderNotAssetSortOrder() {
  // 同分类两素材：asset.sortOrder A=0,B=1；均挂同一人物；
  // rel 上故意设 A.sortOrder=10, B.sortOrder=1
  // list(..., characterId=...) 期望顺序 B, A
  // 且 list(..., characterFilter=all) 或无人物筛仍按 asset.sortOrder → A, B
}
```

Run:  
`mvn -pl story-admin-server -Dtest=AssetScopeReorderTest#listByCharacterUsesRelSortOrderNotAssetSortOrder test`  
（或在 `story-admin-server` 目录直接 `mvn -Dtest=...`）  
Expected: FAIL（尚无重排逻辑）

- [ ] **Step 2: 在 `list` 返回前按条件重排**

伪代码：

```java
List<Asset> assets = assetRepository.search(...);
return applyScopeOrder(assets, categoryId, characterFilter, characterId, linkType, seriesId, arcId);

private List<Asset> applyScopeOrder(...) {
  if (characterId != null) {
    return sortByMap(assets, loadCharacterOrders(characterId, assets));
  }
  if ("unlinked".equals(characterFilter) && (linkType == null || linkType.isBlank() || "CHARACTER".equals(linkType) || "NONE".equals(...))) {
    // 工作台默认 unlinked：当无具体 series/arc 且 characterFilter=unlinked
    return sortByMap(assets, loadUnlinkedOrders(categoryId, assets));
  }
  if (seriesId != null && "SERIES".equalsIgnoreCase(linkType)) {
    return sortByMap(assets, loadSeriesOrders(seriesId, assets));
  }
  if (arcId != null && "ARC".equalsIgnoreCase(linkType)) {
    return sortByMap(assets, loadArcOrders(arcId, assets));
  }
  return assets; // 已按 asset.sortOrder
}

private List<Asset> sortByMap(List<Asset> assets, Map<Long, Integer> scopeOrder) {
  return assets.stream()
      .sorted(Comparator
          .comparingInt((Asset a) -> scopeOrder.getOrDefault(a.getId(), a.getSortOrder()))
          .thenComparingLong(Asset::getId))
      .toList();
}
```

`loadCharacterOrders`：对 `assets` 的 id，查 `findByCharacterId`，取 `sortOrder` 填 Map（缺则不放，回退 asset.sortOrder）。

- [ ] **Step 3: 建关联时赋序；挂人物时删 unlinked 行；清人物链且无其它人物关联时确保 unlinked 行**

在 `replaceCharacters` / `writeSeriesLinks` / `writeArcLinks`：

```java
int next = characterRelRepository
    .findMaxSortOrderByCharacterIdAndCategoryId(characterId, asset.getCategoryId())
    .orElse(-1) + 1;
characterRelRepository.save(new AssetCharacterRel(assetId, characterId, next));
unlinkedOrderRepository.deleteByCategoryIdAndAssetId(asset.getCategoryId(), assetId);
```

`clearCharacterLinks`：删 rel 后，若该 asset 已无任何人物 rel，则插入/更新 `AssetUnlinkedOrder`（`max+1` 或沿用 `asset.sortOrder`）。

硬删素材路径已有 `characterRelRepository.deleteByAssetId`：追加 `unlinkedOrderRepository.deleteByAssetId`。

- [ ] **Step 4: 跑测试 PASS；Commit**

```bash
git commit -m "feat: order asset list by character/series/arc/unlinked scope"
```

---

### Task 3: `reorderByScope` API

**Files:**
- Create: `dto/AssetReorderByScopeRequest.java`
- Modify: `AssetService.java` — `reorderByScope(...)`
- Modify: `AssetController.java` — `PUT /reorder-by-scope`
- Modify: `AssetScopeReorderTest.java` — 改序用例

**Interfaces:**
- Produces: `void reorderByScope(Long categoryId, String scope, Long scopeId, List<Long> orderedIds)`
- Produces: HTTP `PUT /api/assets/reorder-by-scope`

- [ ] **Step 1: 失败测试**

```java
@Test
void reorderByCharacterDoesNotChangeAssetSortOrder() {
  // 三素材挂同一人物；asset.sortOrder 0,1,2
  // reorderByScope CHARACTER 逆序
  // 断言 rel 序为新顺序；asset.sortOrder 仍为 0,1,2
  // list by characterId 为新顺序；list all 仍为旧 asset 序
}

@Test
void reorderByScopeRejectsMismatchedIds() {
  // orderedIds 少一个 → BAD_REQUEST
}
```

Expected: FAIL

- [ ] **Step 2: 实现服务**

```java
public enum /* or string */ // scope: CHARACTER|SERIES|ARC|UNLINKED

@Transactional
public void reorderByScope(Long categoryId, String scope, Long scopeId, List<Long> orderedIds) {
  // validate categoryId, orderedIds unique non-null non-empty
  // resolve expectedIds = current NORMAL assets in category matching scope
  //   CHARACTER: exists rel characterId=scopeId
  //   SERIES / ARC: similarly
  //   UNLINKED: no character rel
  // if set mismatch → 400
  // write sortOrder 0..n-1 on corresponding rows (create unlinked row if missing)
  // NEVER touch asset.sortOrder
}
```

期望 id 集合可用现有 `search(categoryId, NORMAL, "", ...)` 再取 id，保证与列表一致。

- [ ] **Step 3: DTO + Controller**

```java
public record AssetReorderByScopeRequest(
    Long categoryId, String scope, Long scopeId, List<Long> orderedIds) {}

@PutMapping("/reorder-by-scope")
public void reorderByScope(@RequestBody AssetReorderByScopeRequest body) {
  if (body == null) throw new ResponseStatusException(BAD_REQUEST, "body is required");
  assetService.reorderByScope(body.categoryId(), body.scope(), body.scopeId(), body.orderedIds());
}
```

- [ ] **Step 4: 测试 PASS；Commit**

```bash
git commit -m "feat: add reorder-by-scope API for filtered asset order"
```

---

### Task 4: 工作台 UI

**Files:**
- Modify: `story-admin-web/src/api/asset.ts`
- Modify: `story-admin-web/src/views/assets/AssetWorkbench.vue`

**Interfaces:**
- Consumes: `PUT /assets/reorder-by-scope`
- Produces: `reorderAssetsByScope({ categoryId, scope, scopeId?, orderedIds })`

- [ ] **Step 1: API 客户端**

```ts
export async function reorderAssetsByScope(body: {
  categoryId: number;
  scope: 'CHARACTER' | 'SERIES' | 'ARC' | 'UNLINKED';
  scopeId?: number | null;
  orderedIds: number[];
}): Promise<void> {
  await http.put('/assets/reorder-by-scope', body);
}
```

- [ ] **Step 2: 替换 `isSearchActive` 为更细状态**

```ts
const hasKeyword = computed(() => search.value.trim().length > 0);

/** 可持久化的 scope；null = 用分类全局 reorder；'none' = 禁止持久化改序 */
const persistSortScope = computed((): null | 'none' | { scope: '...'; scopeId?: number } => {
  if (hasKeyword.value) return 'none'; // 临时拖，不落库 —— 见 onThumbsChange
  if (linkTypeFilter.value === 'SERIES') {
    if (typeof filterSeriesId.value === 'number') return { scope: 'SERIES', scopeId: filterSeriesId.value };
    return 'none';
  }
  if (linkTypeFilter.value === 'ARC') {
    if (typeof filterArcId.value === 'number') return { scope: 'ARC', scopeId: filterArcId.value };
    return 'none';
  }
  // CHARACTER 或默认人物筛
  if (typeof characterFilter.value === 'number') {
    return { scope: 'CHARACTER', scopeId: characterFilter.value };
  }
  if (characterFilter.value === 'unlinked') {
    return { scope: 'UNLINKED' };
  }
  // characterFilter === 'all' 且无 linkType 特化
  if (linkTypeFilter.value === '' || linkTypeFilter.value === 'CHARACTER') {
    if (characterFilter.value === 'all') return null; // 全局 reorder
  }
  if (linkTypeFilter.value === 'CHARACTER' && characterFilter.value === 'all') {
    // 「有人物关联」全集：首期不持久化半集（与 spec「未选具体」类似）→ none
    return 'none';
  }
  return 'none';
});

const allowThumbSort = computed(() => persistSortScope.value !== 'none' || hasKeyword.value);
// 关键字：allow sort true；persist none
// persistSortScope === 'none' && !hasKeyword → sort false
```

更直白：

```ts
const canSortThumbs = computed(() => {
  if (hasKeyword.value) return true; // 临时
  return persistSortScope.value !== 'none';
});
```

`:sort="canSortThumbs"`

- [ ] **Step 3: `onThumbsChange`**

```ts
async function onThumbsChange(evt: DragChangeEvent) {
  if (!evt.moved || selectedCategoryId.value == null) return;
  if (hasKeyword.value) {
    // 仅内存顺序，不调 API
    return;
  }
  const scope = persistSortScope.value;
  if (scope === 'none') {
    restoreDragSnapshot();
    return;
  }
  try {
    const orderedIds = assets.value.map((i) => i.id);
    if (scope == null) {
      await reorderAssets({ categoryId: selectedCategoryId.value, orderedIds });
    } else {
      await reorderAssetsByScope({
        categoryId: selectedCategoryId.value,
        scope: scope.scope,
        scopeId: scope.scopeId,
        orderedIds,
      });
    }
  } catch (e) {
    restoreDragSnapshot();
    ElMessage.error(apiError(e, '排序失败'));
  }
}
```

- [ ] **Step 4: 提示文案**

替换原 `search-reorder-hint`：

| 条件 | 文案 |
|------|------|
| `hasKeyword` | 搜索中顺序仅临时，刷新后恢复；仍可拖到左侧其它分类 |
| CHARACTER scope | 当前按人物顺序排列，拖拽将保存到该人物 |
| SERIES / ARC | 当前按系列/篇章顺序排列，拖拽将保存到该筛选 |
| UNLINKED | 当前按「无关联」顺序排列，拖拽将保存到本分类无关联视图 |
| 全局 null | 可保留简短 drag-tip；去掉「暂不可用」 |
| `persistSortScope === 'none'` 且无关键字 | 当前筛选未选具体目标，本分类内排序暂不可用，仍可拖到左侧其它分类 |

更新 `drag-tip` 去掉「仅全部时可排序」的过时表述。

- [ ] **Step 5: `npm run build` PASS；Commit + push**

```bash
git commit -m "feat: workbench drag reorder by filter scope"
git push origin master
```

---

### Task 5: 验收文档

**Files:**
- Create: `docs/superpowers/acceptance/2026-08-15-asset-scope-sort.md`
- Modify: `docs/superpowers/specs/2026-08-15-asset-scope-sort-design.md` — 状态 → 已实现（首期）
- Modify: `README.md` — 能力列表加一行（若有素材能力 bullet）

- [ ] **Step 1: 验收表（对照 spec §8）**

| # | 标准 | 结果 | 证据 |
|---|------|------|------|
| 1 | 全部+无关键字改序刷新正确 | | |
| 2 | 人物 scope 改序不影响 asset.sortOrder | | 单测 |
| 3 | 无关联/系列/篇章同理 | | |
| 4 | 关键字临时拖，刷新恢复 | | |
| 5 | 跨分类仍可用 | | |
| 6 | ids 不匹配 400 + UI 回滚 | | 单测 |

跑：`mvn -Dtest=AssetScopeReorderTest,AssetReorderTest test`  
前端：`npm run build`

- [ ] **Step 2: Commit + push**

```bash
git commit -m "docs: record asset scope-sort acceptance"
git push origin master
```

---

## Spec coverage (self-review)

| Spec 项 | Task |
|---------|------|
| V10 rel sort_order + unlinked 表 + 回填 | T1 |
| 列表按 scope 序 / 回退 asset.sortOrder | T2 |
| 建关联赋 max+1；清链/硬删清理 | T2 |
| reorder 保留 | T3（不改） |
| reorder-by-scope | T3 |
| 跨分类 move 不写 scope | T3（不改 move） |
| UI 分支与文案；关键字临时 | T4 |
| 人物页 listAssets 跟 rel 序 | T1 `findAssetIdsByCharacterId` order + 现有 CharacterService |
| 验收 | T5 |

无 TBD；`CHARACTER`+`all`（仅有关联）与「未选具体系列」一律不持久化半集，与 spec §9 一致。
