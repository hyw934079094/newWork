# Preview Lightbox + Batch Link Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Arc full-preview images open a lightbox; asset workbench can multi-select and batch-overwrite links via a dialog.

**Architecture:** Reuse `AssetService.applyLinks` behind `PUT /api/assets/batch-link`. Frontend adds checked-id set + dialog. Preview lightboxes mirror SeriesList Teleport overlay in `PagePreview` and `ArcPreview`.

**Tech Stack:** Spring Boot 3.3, Vue 3 + Element Plus, H2 tests

## Global Constraints

- Branch: `master` only; commit + push after acceptance
- Link mutual exclusion: SERIES | ARC | CHARACTER | NONE (same as single update)
- Batch overwrites existing links; empty assetIds → 400; recycled → 400
- Lightbox: click open, blank close; no wheel zoom this slice

---

### Task 1: batch-link API

**Files:**
- Create: `story-admin-server/src/main/java/com/story/admin/dto/AssetBatchLinkRequest.java`
- Modify: `AssetService.java`, `AssetController.java`
- Test: `AssetLinkServiceTest.java` (add cases)

**Interfaces:**
- Produces: `List<Asset> batchLink(List<Long> assetIds, AssetLinkType linkType, List<Long> seriesIds, List<Long> arcIds, List<Long> characterIds)`
- Consumes: existing `applyLinks`, `getRaw`, `hydrate`

- [ ] **Step 1: Failing test** — batch two assets to SERIES; overwrite CHARACTER→ARC; NONE clears; empty ids 400; recycled 400
- [ ] **Step 2: DTO + service + controller**
- [ ] **Step 3: Tests pass**
- [ ] **Step 4: Commit** `feat(assets): batch-link API overwrites associations`

### Task 2: Workbench multi-select + dialog

**Files:**
- Modify: `story-admin-web/src/api/asset.ts`, `AssetWorkbench.vue`

- [ ] **Step 1:** `batchLinkAssets` client
- [ ] **Step 2:** checkbox on thumbs; toolbar buttons; dialog form (linkType + ids); submit/clear
- [ ] **Step 3:** Manual smoke (or note for acceptance)
- [ ] **Step 4: Commit** `feat(assets): batch link selected workbench assets`

### Task 3: Preview lightbox

**Files:**
- Modify: `PagePreview.vue`, `ArcPreview.vue`

- [ ] **Step 1:** PagePreview beat img → lightbox
- [ ] **Step 2:** ArcPreview cover → lightbox
- [ ] **Step 3: Commit** `feat(arcs): lightbox for preview cover and beat images`

### Task 4: Docs + push

- [ ] Acceptance note under `docs/superpowers/acceptance/`
- [ ] Push all commits
