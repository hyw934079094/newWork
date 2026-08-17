# Page Drag + COVER Child Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Page timeline and beat children are drag-reorderable; beat cover is a `COVER` child so text can sit above or below the image; preview and reading-stream follow array order.

**Architecture:** Persist order in `content_json`. On save, `PageService` normalizes legacy BEATs (inject `COVER` from `coverAssetId`) then validates exactly one `COVER` and syncs `coverAssetId`. `ArcService` reading-stream walks children in order. Frontend uses `vuedraggable` at page and beat levels.

**Tech Stack:** Spring Boot 3 / Jackson; Vue 3 + vuedraggable; existing PageEditor / PagePreview.

**Spec:** `docs/superpowers/specs/2026-08-17-page-drag-cover-order-design.md`

## Global Constraints

- No DB migration; JSON-only evolution.
- Keep `coverAssetId` on BEAT for `page_asset_ref` compatibility.
- Nav / AI reference out of scope.
- Work on `master`; commit + push when done.

## File map

| File | Role |
|------|------|
| `PageService.java` | Normalize + validate COVER |
| `ArcService.java` | Ordered beat segments |
| `PageServiceTest.java` / `ArcReadingStreamTest.java` | Backend tests |
| `PageEditor.vue` | Drag + COVER UI |
| `PagePreview.vue` | Ordered render |

---

### Task 1: Backend normalize/validate + reading stream

**Files:**
- Modify: `story-admin-server/src/main/java/com/story/admin/service/PageService.java`
- Modify: `story-admin-server/src/main/java/com/story/admin/service/ArcService.java`
- Modify: `story-admin-server/src/test/java/com/story/admin/service/PageServiceTest.java`
- Modify: `story-admin-server/src/test/java/com/story/admin/service/ArcReadingStreamTest.java`

- [ ] Normalize BEAT on update: inject COVER if missing; sync `coverAssetId` from COVER; allow child types `COVER|BODY|DIALOGUE`; reject ≠1 COVER
- [ ] Reading stream: if any COVER child, emit IMAGE/BODY/DIALOGUE in children order; else legacy image-then-text
- [ ] Tests: text-before-cover stream order; legacy JSON still saves refs; reject two COVERs
- [ ] Commit

### Task 2: Frontend editor + preview

**Files:**
- Modify: `story-admin-web/src/views/pages/PageEditor.vue`
- Modify: `story-admin-web/src/views/pages/PagePreview.vue`

- [ ] Child type includes COVER; parse/serialize/normalize
- [ ] vuedraggable page timeline + beat children; keep up/down
- [ ] COVER row: thumb + pick/clear; no delete type change
- [ ] Preview renders children order (COVER → figure)
- [ ] Commit + push

---

## Spec coverage

| Spec § | Task |
|--------|------|
| 2.2 COVER model + legacy | Task 1–2 |
| 2.3 backend + stream | Task 1 |
| 2.4 frontend drag/preview | Task 2 |
| 2.6 acceptance | Manual after push |
| §1 nav / §3 AI | Out of scope |
