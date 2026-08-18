# Asset Recycle Associations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Recycle force-detaches asset associations into snapshots; restore reattaches when targets still exist; hardDelete cascades without 409.

**Architecture:** Flyway `asset_association_snapshot` + `AssetAssociationLifecycle` (detach/restore/purge) called from `AssetService.recycle` / `restore` / `hardDelete`. `PageService` allows empty COVER and zero-member COMBO. Frontend already shows empty cover placeholder; relax client save guard.

**Tech Stack:** Spring Boot 3.3, JPA, Flyway V12, H2 tests (`ddl-auto=create-drop`, Flyway off), Vue 3 PageEditor

**Spec:** `docs/superpowers/specs/2026-08-18-asset-recycle-associations-design.md`

## Global Constraints

- Work on `master`; after accepted work commit + push.
- PowerShell: no bash HEREDOC; use `git commit -m "..."` or `-F` message file.
- Quote Maven `-Dtest=...` in PowerShell.
- Do not commit `storage/assets/**` churn unless user asks.
- Tags / unlinked order: do **not** detach on recycle.
- Do **not** renumber combo `memberNo`.
- Empty COVER keeps COVER node; empty COMBO keeps COMBO node + `page_combo_ref`.

## File map

| File | Responsibility |
|------|----------------|
| `story-admin-server/src/main/resources/db/migration/V12__asset_association_snapshot.sql` | Snapshot table |
| `.../domain/AssetAssociationSnapshot.java` | Entity |
| `.../repository/AssetAssociationSnapshotRepository.java` | CRUD + deleteByAssetId |
| `.../service/AssetAssociationLifecycle.java` | detach → snapshots; restore; purgeAssociations |
| `.../service/AssetService.java` | Wire recycle/restore/hardDelete |
| `.../repository/PageAssetRefRepository.java` | `findByAssetId`, `deleteByAssetId` |
| `.../repository/AssetComboMemberRepository.java` | `findByAssetId` |
| `.../repository/AiReferenceItemRepository.java` | `deleteByAssetId` |
| `.../service/PageService.java` | Allow empty COVER / 0-member COMBO |
| `.../service/ArcService.java` | Confirm skip empty visual (likely already OK) |
| `story-admin-web/src/views/pages/PageEditor.vue` | Allow save with empty COVER |
| Tests listed per task | |

---

### Task 1: Snapshot table + entity + repository

**Files:**
- Create: `story-admin-server/src/main/resources/db/migration/V12__asset_association_snapshot.sql`
- Create: `story-admin-server/src/main/java/com/story/admin/domain/AssetAssociationSnapshot.java`
- Create: `story-admin-server/src/main/java/com/story/admin/repository/AssetAssociationSnapshotRepository.java`
- Modify: `PageAssetRefRepository.java`, `AssetComboMemberRepository.java`, `AiReferenceItemRepository.java`

**Interfaces:**
- Produces: `AssetAssociationSnapshot` fields `assetId`, `kind`, `payloadJson`, `createdAt`; repo `deleteByAssetId`, `findByAssetIdOrderByIdAsc`; `PageAssetRefRepository.findByAssetId` / `deleteByAssetId`; `AssetComboMemberRepository.findByAssetId`; `AiReferenceItemRepository.deleteByAssetId`

- [ ] **Step 1: Add Flyway migration**

```sql
CREATE TABLE asset_association_snapshot (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  asset_id BIGINT NOT NULL,
  kind VARCHAR(64) NOT NULL,
  payload_json TEXT NOT NULL,
  created_at DATETIME(6) NOT NULL,
  KEY idx_asset_association_snapshot_asset (asset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

- [ ] **Step 2: Add entity + repository**

Entity package `com.story.admin.domain`, table `asset_association_snapshot`, `@GeneratedValue IDENTITY`, columns matching migration (`payload_json` as `@Column(columnDefinition = "TEXT")`).

```java
public interface AssetAssociationSnapshotRepository
    extends JpaRepository<AssetAssociationSnapshot, Long> {
  void deleteByAssetId(Long assetId);
  List<AssetAssociationSnapshot> findByAssetIdOrderByIdAsc(Long assetId);
}
```

- [ ] **Step 3: Extend lookup/delete helpers**

```java
// PageAssetRefRepository
List<PageAssetRef> findByAssetId(Long assetId);
void deleteByAssetId(Long assetId);

// AssetComboMemberRepository
List<AssetComboMember> findByAssetId(Long assetId);

// AiReferenceItemRepository
void deleteByAssetId(Long assetId);
```

- [ ] **Step 4: Commit**

```bash
git add story-admin-server/src/main/resources/db/migration/V12__asset_association_snapshot.sql story-admin-server/src/main/java/com/story/admin/domain/AssetAssociationSnapshot.java story-admin-server/src/main/java/com/story/admin/repository/AssetAssociationSnapshotRepository.java story-admin-server/src/main/java/com/story/admin/repository/PageAssetRefRepository.java story-admin-server/src/main/java/com/story/admin/repository/AssetComboMemberRepository.java story-admin-server/src/main/java/com/story/admin/repository/AiReferenceItemRepository.java
git commit -m "feat(db): add asset association snapshot table"
```

---

### Task 2: PageService empty COVER / empty COMBO

**Files:**
- Modify: `story-admin-server/src/main/java/com/story/admin/service/PageService.java`
- Modify: `story-admin-server/src/test/java/com/story/admin/service/PageServiceTest.java`

**Interfaces:**
- Consumes: existing `normalizeBeat` / `validateAndCollectRefs` / `rebuildRefs`
- Produces: empty COVER (`assetId` null, `coverAssetId` null) and COMBO with 0 members persistable

- [ ] **Step 1: Write failing tests in `PageServiceTest`**

```java
@Test
void saveAllowsEmptyBeatCover() {
  // create page with BEAT children: COVER without assetId, coverAssetId null
  // pageService.save(...) must succeed
  // content still contains "COVER"; page_asset_ref for that page has no BEAT_COVER
}

@Test
void saveAllowsComboWithZeroMembers() {
  // create empty-member combo (or detach all members in test setup)
  // BEAT with COMBO child pointing at that comboId, coverAssetId null
  // save succeeds; page_combo_ref still present; no BEAT_COMBO_MEMBER refs
}
```

Use same `@SpringBootTest` / H2 profile pattern as existing `PageServiceTest`.

- [ ] **Step 2: Run tests — expect FAIL**

```powershell
cd d:\study\mine\newWork\story-admin-server
mvn -q "-Dtest=PageServiceTest#saveAllowsEmptyBeatCover,PageServiceTest#saveAllowsComboWithZeroMembers" test
```

Expected: FAIL (COVER assetId required / combo has no members).

- [ ] **Step 3: Implement PageService changes**

In `normalizeBeat` (COVER branch): if COVER child exists with null `assetId` and no usable legacy `coverAssetId`, keep empty COVER and set `coverAssetId` to null (Jackson: `beat.putNull("coverAssetId")` or remove field — prefer explicit null).

In `validateAndCollectRefs`:
- If COVER child and `assetId` missing/null: require `coverAssetId` also null/absent; do **not** call `validateNormalAsset`; do not add to coverIds.
- If COVER has assetId: keep existing match + NORMAL checks.
- If COMBO: `comboId` required; combo must exist; **members may be empty**; if empty, `coverAssetId` must be null; if non-empty, keep first-frame / NORMAL rules.
- XOR: still exactly one of COVER or COMBO.

In `rebuildRefs`: only write refs for non-null cover / non-empty members.

- [ ] **Step 4: Re-run tests — expect PASS**

- [ ] **Step 5: Commit**

```bash
git commit -m "fix(page): allow empty beat cover and zero-member combo"
```

---

### Task 3: `AssetAssociationLifecycle` — detach + restore for simple rels

**Files:**
- Create: `story-admin-server/src/main/java/com/story/admin/service/AssetAssociationLifecycle.java`
- Modify: `story-admin-server/src/main/java/com/story/admin/service/AssetService.java`
- Create: `story-admin-server/src/test/java/com/story/admin/service/AssetRecycleAssociationTest.java`

**Kinds this task:** `CHARACTER_REL`, `SERIES_REL`, `ARC_REL`, `IDENTITY_REL`, `AI_REF`, `SERIES_COVER`, `ARC_COVER`  
(Leave combo/page stubs as empty methods or `TODO` in same class — implement fully in Task 4; do not leave vague comments — either implement no-op private methods named `detachComboAndPages` called later or skip calling them until Task 4.)

**Recommended:** Implement **all** detach/restore kinds in Task 3–4 split as:
- Task 3: simple rels + covers + wire recycle/restore
- Task 4: combo + page

**Interfaces:**
- Produces:
  - `void detachAll(Long assetId)` — replace snapshots for asset, force-detach
  - `void restoreAll(Long assetId)` — apply snapshots; skip missing targets
  - `void purgeAll(Long assetId)` — delete snapshots + associations (no snapshot write); used by hardDelete

Kind string constants exactly as spec.

- [ ] **Step 1: Failing tests**

```java
@Test
void recycleDetachesCharacterAndRestoreReattaches() { ... }

@Test
void recycleClearsSeriesAndArcCoverThenRestore() { ... }

@Test
void restoreSkipsDeletedCharacterKeepsOtherRels() { ... }
```

Pattern: same H2 `@SpringBootTest` as `AssetDeleteTest`. Assert status `DELETED` after recycle, rels empty, snapshot rows present; after restore `NORMAL` and rels/covers back.

- [ ] **Step 2: Run — FAIL** (recycle still only flips status)

- [ ] **Step 3: Implement `AssetAssociationLifecycle`**

Inject needed repositories + `ObjectMapper`. Use `LocalDateTime.now()` for `createdAt`.

`detachAll` outline:
1. `List<AssetAssociationSnapshot> snaps = new ArrayList<>();`
2. For each character rel: snapshot `{"characterId":...}`; collect ids; then `characterRelRepository.deleteByAssetId`
3. Same for series/arc/identity rels
4. AI items: snapshot fields; `deleteByAssetId` or deleteEach
5. Series/arc covers: snapshot; set cover null; save
6. Call combo/page detach (Task 4) — for now if not ready, implement those private methods in Task 4 before finishing Task 3 green for character tests only; **prefer finishing Task 4 methods before claiming Task 3 done for full recycle**, OR implement Task 3 tests that only use character/cover and implement only those branches first.

Practical order for agent: implement all detach/restore in one class across Task 3+4 without merging commits if preferred — but keep commits per task.

Payload JSON via `objectMapper.writeValueAsString(Map.of(...))`.

`restoreAll`:
- load snapshots ordered by id
- switch on kind; if target missing `continue`
- CHARACTER: `save(new AssetCharacterRel(assetId, characterId))` if character exists
- SERIES_COVER: set cover if series exists
- etc.

Wire `AssetService`:
```java
public Asset recycle(Long id) {
  Asset asset = getRaw(id);
  associationLifecycle.detachAll(id);
  asset.setStatus(AssetStatus.DELETED);
  asset.setDeletedAt(LocalDateTime.now());
  return hydrate(assetRepository.save(asset));
}

public Asset restore(Long id) {
  Asset asset = getRaw(id);
  asset.setStatus(AssetStatus.NORMAL);
  asset.setDeletedAt(null);
  asset = assetRepository.save(asset);
  associationLifecycle.restoreAll(id);
  return hydrate(asset);
}
```

- [ ] **Step 4: Tests PASS**

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(asset): snapshot and restore simple associations on recycle"
```

---

### Task 4: Combo member + page BEAT COVER / COMBO refs

**Files:**
- Modify: `AssetAssociationLifecycle.java`
- Modify: `AssetRecycleAssociationTest.java`
- Need: `AssetComboRepository`, `StoryPageRepository`, `ObjectMapper` for content_json patch

**Interfaces:**
- Extends `detachAll` / `restoreAll` with `COMBO_MEMBER`, `PAGE_BEAT_COVER`, `PAGE_COMBO_MEMBER_REF`

- [ ] **Step 1: Failing tests**

```java
@Test
void recycleRemovesComboMemberRewritesPlaySequenceAndRestore() { ... }

@Test
void recycleLastComboMemberLeavesEmptyCombo() { ... }

@Test
void recycleClearsPageBeatCoverKeepsNodeAndRestoreFills() { ... }
```

For page cover test: create page via `PageService` with COVER asset; recycle asset; read page content_json — COVER present, assetId null/absent, coverAssetId null; `page_asset_ref` empty for that asset; restore — assetId back + ref `BEAT_COVER`.

- [ ] **Step 2: Implement combo detach**

For each `AssetComboMember` with assetId:
- Load combo; snapshot `comboId`, `memberNo`, `sortOrder`, `playSequenceBefore`
- Delete member row
- Filter play_sequence tokens to remaining memberNos; set `playSequence` (empty string if none); save combo
- Track affected `comboId`s

For each affected combo, find pages via `pageComboRefRepository` — if no `findByComboId`, add:

```java
List<PageComboRef> findByComboId(Long comboId);
```

Or scan `page_asset_ref` for `BEAT_COMBO_MEMBER` of this asset (`findByAssetId`).

For each page ref of this asset:
- If `BEAT_COVER`: handle as PAGE_BEAT_COVER (below)
- If `BEAT_COMBO_MEMBER`: snapshot `PAGE_COMBO_MEMBER_REF`; delete ref; if page `coverAssetId` equals asset, set to new first frame or null (patch JSON + save page)

**PAGE_BEAT_COVER detach:** walk content array; for each BEAT, find COVER child whose assetId equals target; record `beatIndex`/`childIndex`; null out assetId + coverAssetId; save page; delete matching refs.

**Restore COMBO_MEMBER:** if combo exists and member slot free, insert member with original memberNo/sortOrder; set playSequence to filtered `playSequenceBefore` (only memberNos that exist after insert).

**Restore PAGE_BEAT_COVER:** if page exists and indices still COVER, write assetId/coverAssetId; save `PageAssetRef(pageId, assetId, "BEAT_COVER")`.

**Restore PAGE_COMBO_MEMBER_REF:** if page still has COMBO for that combo (optional: store comboId in payload — **add `comboId` to PAGE_COMBO_MEMBER_REF payload** for reliable restore: `{"pageId":9,"comboId":7,"refKind":"BEAT_COMBO_MEMBER","coverAssetIdBefore":101}`); re-add member refs for current members; restore coverAssetId when appropriate.

Update spec payload if adding `comboId` — implementer should add `comboId` to snapshot payload (compatible extension).

- [ ] **Step 3: Tests PASS + commit**

```bash
git commit -m "feat(asset): detach restore combo members and page beat covers"
```

---

### Task 5: hardDelete cascade + flip blocked tests

**Files:**
- Modify: `AssetService.hardDelete`
- Modify tests that expect `ConflictException`:
  - `AssetDeleteTest.java`
  - `AssetLinkServiceTest.java` (hardDeleteBlockedWhenSeriesLinked)
  - `AssetHardDeleteArcCoverTest.java`
  - `AssetHardDeletePageBeatTest.java`
  - `AssetHardDeleteSeriesCoverTest.java`
  - `AssetHardDeleteIdentityTest.java`
  - `AssetHardDeleteComboTest.java`
- Add: hardDelete cases in `AssetRecycleAssociationTest`

**Interfaces:**
- Consumes: `associationLifecycle.purgeAll(assetId)` or `detachAll` without keeping snapshots then delete snapshots

- [ ] **Step 1: Rewrite failing expectations**

Each former `hardDeleteBlocked*` becomes `hardDeleteCascades*` / `hardDeleteSucceeds*`:
- assert does **not** throw
- asset gone from repository
- character/series/combo/page refs no longer point at asset
- if was page cover, COVER empty

Keep one test: recycle then hardDelete clears snapshots.

- [ ] **Step 2: Implement hardDelete**

```java
@Transactional
public void hardDelete(Long id) {
  Asset asset = getRaw(id);
  associationLifecycle.purgeAll(id); // delete all business associations + snapshots; do not write new snapshots
  tagRelRepository.deleteByAssetId(id);
  // character/series/... already purged inside purgeAll; keep defensive deletes if purgeAll is comprehensive
  unlinkedOrderRepository.deleteByAssetId(id);
  String storagePath = asset.getStoragePath();
  assetRepository.delete(asset);
  storageService.deleteQuietly(storagePath);
}
```

`purgeAll` must remove the same association kinds as detach (without snapshot insert), plus `snapshotRepository.deleteByAssetId`.

Remove `ConflictException` / `buildReferenceSummary` usage from hardDelete path (method may remain unused — delete dead code if nothing else calls it).

- [ ] **Step 3: Run full related suite**

```powershell
mvn -q "-Dtest=AssetRecycleAssociationTest,AssetDeleteTest,AssetHardDelete*,AssetLinkServiceTest,PageServiceTest" test
```

Expected: PASS

- [ ] **Step 4: Commit**

```bash
git commit -m "feat(asset): cascade associations on hard delete"
```

---

### Task 6: Frontend save allows empty COVER + Arc regression

**Files:**
- Modify: `story-admin-web/src/views/pages/PageEditor.vue` (client validation ~552)
- Modify/add: `ArcReadingStreamTest` case for empty COVER (no IMAGE, BODY still present) — ArcService already skips null assetId; add regression test only

- [ ] **Step 1: Relax PageEditor validation**

Current logic rejects `visual.assetId == null` for COVER. Change to:
- COVER: **allowed** empty (user may re-pick later)
- COMBO: still require `comboId != null`
- Reject only if visual child missing entirely

Placeholder UI `暂无封面` already exists — no layout redesign.

- [ ] **Step 2: Arc regression test**

```java
@Test
void readingStreamSkipsEmptyBeatCoverKeepsBody() { ... }
```

- [ ] **Step 3: Commit**

```bash
git commit -m "fix(web): allow saving page beats with empty cover"
```

---

### Task 7: Spec status + push

- [ ] Mark spec header `状态：已实现` (or keep 已确认待实现 until verified — set **已实现** after Task 5–6 green)
- [ ] Push `master`

```powershell
git push origin HEAD
```

- [ ] Restart backend so Flyway V12 applies on real MySQL (`story_admin`)

---

## Spec coverage checklist

| Spec § | Task |
|--------|------|
| Snapshot table V12 | 1 |
| All association kinds | 3–4 |
| recycle / restore algorithms | 3–4 |
| hardDelete no 409 | 5 |
| Empty COVER / 0-member COMBO PageService | 2 |
| Reading stream skip empty | 6 (regression) |
| Frontend placeholder / save | 6 |
| Tests §8 | 3–5 |
| Non-goals (no bulk API, no member renumber) | respected |

## Notes for implementers

- Prefer extracting lifecycle out of `AssetService` to keep file size manageable.
- H2 tests use `ddl-auto=create-drop` — entity mapping must be correct; Flyway V12 still required for MySQL runtime.
- When patching `content_json`, use `ObjectMapper` readTree → mutate → writeValueAsString; do not go through `PageService.save` during recycle (avoids circular validation timing).
- COMBO_MEMBER payload should include `sortOrder` (entity field; spec example omitted it).
- PAGE_COMBO_MEMBER_REF payload should include `comboId` for restore.
