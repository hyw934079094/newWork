# Asset List Pagination Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Paginate `GET /api/assets` with DTO + batch hydrate; update all web callers; workbench show-all for drag sort.

**Architecture:** Filter+scope-order full list then slice (spec §3.3); `hydrateAll` via `IN` queries; frontend `AssetPageResponse`.

**Tech Stack:** Spring Boot, Vue 3, Element Plus pagination

**Spec:** `docs/superpowers/specs/2026-08-18-asset-list-pagination-design.md`

## Global Constraints

- Default `page=0`, `size=48`; `size` in `1..5000`; `page < 0` → 400
- Response `{ items, page, size, total }` — breaking change from bare array
- Workbench: paginated → no drag;「展示全部」if total>200 confirm; filter change → back to paged
- Recycle/AI: page bar; pickers: load more
- JDK: `D:\jdk\jdk-24.0.1` for Maven
- Commit with `git.exe commit --no-verify`; no `storage/assets/**`
- Work on `master`; push when done

---

### Task 1: Backend page DTO + listPage + batch hydrate + tests

**Files:**
- Create: `dto/AssetPageResponse.java`
- Modify: `AssetController.java`, `AssetService.java`
- Modify: `AssetTagRelRepository`, `AssetCharacterRelRepository`, `AssetSeriesRelRepository`, `AssetArcRelRepository` — add batch queries returning `(assetId, …)` rows or maps
- Create/Modify: `AssetListPaginationTest.java`; fix tests calling `list()` that assumed `List<Asset>` from controller (service internal list helpers may remain for reorder)

- [ ] Add `AssetPageResponse(List<Asset> items, int page, int size, long total)`
- [ ] `AssetService.listPage(... filters, int page, int size)` validates page/size; search+applyScopeOrder; slice; `hydrateAll`; return DTO
- [ ] Keep package-private or private full ordered list path for reorder tests; public `list(...)` either remove or delegate to listPage with size=5000 for internal — **prefer**: change public API to `listPage` only; update service tests to use `listPage(...).items()`
- [ ] `hydrateAll`: batch load tags/chars/series/arcs by asset id IN
- [ ] Controller passes `page`/`size` defaults
- [ ] Tests: default page, page 1, empty page, bad size/page, hydrate fields present
- [ ] Commit: `feat(asset): paginate asset list API with batch hydrate`

### Task 2: Web API + Workbench

**Files:** `api/asset.ts`, `AssetWorkbench.vue`

- [ ] `listAssets` → `AssetPageResponse`; params `page?`, `size?`
- [ ] Workbench state: `page`, `pageSize=48`, `total`, `showAll`
- [ ] Paged mode: no drag; el-pagination
- [ ] Show all / back to paged per spec
- [ ] Commit: `feat(web): asset workbench pagination and show-all sort`

### Task 3: Recycle + AI + pickers

**Files:** `RecycleBin.vue`, `AiReference.vue`, `PageEditor.vue`, `SeriesList.vue`, `ArcList.vue`, `CharacterList.vue`, `ComboEditor.vue`, `IdentityEditor.vue`

- [ ] Recycle/AI: pagination bar; bulk recycle uses one large size fetch
- [ ] Pickers: load more append
- [ ] Commit: `feat(web): paginate recycle AI and asset pickers`

### Task 4: Spec status + push + smoke

- [ ] Spec → 已实现; push; restart backend if needed
