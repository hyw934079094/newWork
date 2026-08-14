# 篇章与故事页面（首期） Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 系列下 CRUD 篇章与故事页面；页面以 content_json（含 BEAT）编辑并预览；硬删封面/BEAT 素材统一 409；有篇章时不可删系列。

**Architecture:** Flyway V7 建 `story_arc` / `story_page` / `page_asset_ref`；`ArcService` + `PageService`；保存页面时重建 BEAT 封面引用；`AssetService.hardDelete` 并入篇章封面与 page refs；`SeriesService.delete` 有篇章则 409；Vue：篇章列表 → 页面列表 → 左时间线右预览编辑器。

**Tech Stack:** Java 17+、Spring Boot 3.3、JPA/Flyway、Vue 3、Element Plus、SortableJS（若项目已有则复用，否则原生拖拽或 vuedraggable）

**Spec:** `docs/superpowers/specs/2026-08-14-story-arc-page-design.md`  
**Beat layout:** `docs/superpowers/specs/2026-08-14-story-page-beat-layout-design.md`

## Global Constraints

- 在 `master` 上直接提交（用户已确认）
- 篇章状态仅：`DRAFT` / `WRITING` / `FINALIZED`；默认 `DRAFT`
- 篇章 code：`A` + 6 位数字（仿系列 `S000001`）
- content_json 顶层：`TITLE` / `BODY` / `DIVIDER` / `BEAT`；BEAT 含必填 `coverAssetId` + `children`（仅 `BODY`/`DIALOGUE`）
- 间距 CSS：`--gap-beat` 40–48、`--gap-figure-text` 14–16、`--gap-inline` 8–10；预览必须用这些变量
- 硬删素材：系列封面 ∪ 篇章封面 ∪ page_asset_ref → 任一命中 409
- 删篇章：级联删其下页面与 page_asset_ref（前端确认框）
- 删系列：若仍有篇章 → 409
- 不做章节/场景实体、不做使用端阅读路由
- Git：`D:\tool\Git\bin\git.exe` + `-F` UTF-8 无 BOM；`--trailer` 时用 `cmd /c`
- JDK：`JAVA_HOME=D:\jdk\jdk-24.0.1`；Node：`D:\tool\nvm\v22.17.0`
- TSD：改受保护扩展后验明文；必要时 `.txt` + `cmd ren`
- 单测：常 `spring.flyway.enabled=false` + H2 `ddl-auto=create-drop`（看现有 `*ServiceTest`）

---

## File Map

| Path | Responsibility |
|------|----------------|
| `db/migration/V7__story_arc_page.sql` | 三表 |
| `domain/StoryArc.java`、`ArcStatus.java` | 篇章 |
| `domain/StoryPage.java` | 页面 |
| `domain/PageAssetRef.java`、`PageAssetRefId.java` | 引用（pageId+assetId+refKind） |
| `repository/StoryArcRepository.java` | 篇章查询；`countBySeriesId`；`findByCoverAssetId` |
| `repository/StoryPageRepository.java` | 按 arcId 列表；删页 |
| `repository/PageAssetRefRepository.java` | 按 page/asset 删查 |
| `dto/Arc*.java`、`Page*.java` | 请求/查询 |
| `service/ArcService.java` | 篇章 CRUD；级联删页 |
| `service/PageService.java` | 页面 CRUD；校验 JSON；重建 ref |
| `controller/ArcController.java`、`PageController.java` | REST |
| `SeriesService.delete` | 有篇章 409 |
| `AssetService.hardDelete` | 篇章封面 + page refs |
| `api/arc.ts`、`api/page.ts` | 前端客户端 |
| `views/arcs/ArcList.vue` | `/series/:seriesId/arcs` |
| `views/pages/PageList.vue` | `/arcs/:arcId/pages` |
| `views/pages/PageEditor.vue`（+ 可选子组件 Preview） | `/pages/:pageId/edit` |
| `styles` 或组件内 CSS vars | beat 间距 |
| `router`、`App.vue`（侧栏可不新增顶层，从系列进入） | 路由 |
| Spec 状态 / README / 验收 | Task 7 |

---

### Task 1: Flyway V7 + 篇章 CRUD + 删系列拦截

**Files:**
- Create: `story-admin-server/src/main/resources/db/migration/V7__story_arc_page.sql`（本任务可先只建 `story_arc`，或一次建三表以免后续迁移冲突——**推荐一次建三表**，本任务仅使用 arc 相关代码）
- Create: `domain/StoryArc.java`、`ArcStatus.java`、`StoryArcRepository.java`
- Create: DTOs `ArcCreateRequest`、`ArcUpdateRequest`、`ArcQuery`（可选 q）
- Create: `ArcService.java`、`ArcController.java`
- Modify: `SeriesService.delete` — `countBySeriesId > 0` → 409
- Create: `src/test/java/.../ArcServiceTest.java`；扩 `SeriesServiceTest` 或新测删系列

**Interfaces:**
- Produces: `listBySeries(seriesId)`、`get(id)`、`create(seriesId, req)`、`update`、`delete`（本任务 delete 仅删空篇章；有页面的级联在 Task 2 补全——若 Task1 已建 page 表，delete 可先只删 arc，Task2 再挂级联）
- Code：`A` + 6 位
- Cover：非空须 NORMAL 素材（同 SeriesService.validateCoverAsset）

- [ ] **Step 1: 失败测试 — 标题必填；默认 DRAFT；有篇章不可删系列**

```java
@Test
void createRequiresTitle() {
  assertThatThrownBy(() -> arcService.create(seriesId, new ArcCreateRequest("  ", null, null, null)))
      .isInstanceOf(ResponseStatusException.class);
}

@Test
void createDefaultsDraft() {
  StoryArc a = arcService.create(seriesId, new ArcCreateRequest("开篇", null, null, null));
  assertThat(a.getCode()).startsWith("A");
  assertThat(a.getStatus()).isEqualTo(ArcStatus.DRAFT);
}

@Test
void deleteSeriesBlockedWhenArcsExist() {
  arcService.create(seriesId, new ArcCreateRequest("开篇", null, null, null));
  assertThatThrownBy(() -> seriesService.delete(seriesId))
      .isInstanceOf(ResponseStatusException.class)
      .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(409));
}
```

- [ ] **Step 2: Flyway SQL（三表一次建齐）**

```sql
CREATE TABLE story_arc (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  series_id BIGINT NOT NULL,
  code VARCHAR(50) NOT NULL,
  title VARCHAR(200) NOT NULL,
  summary TEXT NULL,
  status VARCHAR(20) NOT NULL,
  cover_asset_id BIGINT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  UNIQUE KEY uk_story_arc_code (code),
  KEY idx_story_arc_series (series_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE story_page (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  arc_id BIGINT NOT NULL,
  title VARCHAR(200) NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  content_json LONGTEXT NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  KEY idx_story_page_arc (arc_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE page_asset_ref (
  page_id BIGINT NOT NULL,
  asset_id BIGINT NOT NULL,
  ref_kind VARCHAR(32) NOT NULL,
  PRIMARY KEY (page_id, asset_id, ref_kind),
  KEY idx_page_asset_ref_asset (asset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- [ ] **Step 3: 实现实体/仓库/Service/Controller；改 SeriesService.delete；测试 PASS**

API：
- `GET/POST /api/series/{seriesId}/arcs`
- `GET/PUT/DELETE /api/arcs/{id}`

- [ ] **Step 4: Commit**

```bash
git commit --trailer "Co-authored-by: Cursor <cursoragent@cursor.com>" -m "feat: add story arc CRUD and block series delete when arcs exist"
```

---

### Task 2: 故事页面 CRUD + content_json + page_asset_ref

**Files:**
- Create: `StoryPage`、`PageAssetRef`、`PageAssetRefId`、repositories
- Create: `PageCreateRequest`、`PageUpdateRequest`（含 title、contentJson 字符串或 JsonNode、sortOrder）
- Create: `PageService`、`PageController`
- Modify: `ArcService.delete` — 先删该 arc 下所有 page 的 refs + pages，再删 arc
- Create: `PageServiceTest.java`

**Interfaces:**
- Produces: `listByArc(arcId)`、`get(id)`、`create(arcId, title)`、`update(id, title, contentJson)`、`delete(id)`、可选 `reorder`
- 保存时：`rebuildRefs(page)` — 解析 content_json 数组中 type=BEAT 的 coverAssetId（去重）→ deleteByPageId → insert BEAT_COVER
- 校验：顶层 type 合法；BEAT 必须有 coverAssetId（Long）且素材 NORMAL；children 仅 BODY/DIALOGUE；非法 → 400
- content 默认 `[]`

- [ ] **Step 1: 测试 — 创建空页；保存 BEAT 建 ref；非法 type 400；删篇章级联**

```java
@Test
void saveBeatRebuildsPageAssetRef() {
  StoryPage p = pageService.create(arcId, new PageCreateRequest("P1"));
  String json = "[{\"type\":\"BEAT\",\"coverAssetId\":" + assetId + ",\"children\":[{\"type\":\"BODY\",\"text\":\"hi\"}]}]";
  pageService.update(p.getId(), new PageUpdateRequest("P1", json));
  assertThat(pageAssetRefRepository.findByPageId(p.getId())).hasSize(1);
}

@Test
void deleteArcCascadesPagesAndRefs() {
  // create page with beat ref; delete arc; assert page+ref gone
}
```

- [ ] **Step 2: 实现 + PASS**

API：
- `GET/POST /api/arcs/{arcId}/pages`
- `GET/PUT/DELETE /api/pages/{id}`
- 可选：`PUT /api/arcs/{arcId}/pages/reorder` body `{ "orderedIds": [..] }`

- [ ] **Step 3: Commit**

```bash
git commit --trailer "Co-authored-by: Cursor <cursoragent@cursor.com>" -m "feat: add story page CRUD with beat cover asset refs"
```

---

### Task 3: 硬删素材拦截（篇章封面 + page_asset_ref）

**Files:**
- Modify: `StoryArcRepository.findByCoverAssetId`
- Modify: `PageAssetRefRepository` — `existsByAssetId` 或 `findPageIdsByAssetId` + 查 page 标题
- Modify: `AssetService.hardDelete` + `buildReferenceSummary`
- Create: `AssetHardDeleteArcCoverTest.java`、`AssetHardDeletePageBeatTest.java`（或合并）

- [ ] **Step 1: 测试 — 作篇章封面不可硬删；作 BEAT 封面不可硬删**

```java
@Test
void hardDeleteBlockedWhenAssetIsArcCover() { /* ... ConflictException */ }

@Test
void hardDeleteBlockedWhenAssetIsBeatCover() { /* save page with BEAT; hardDelete → Conflict */ }
```

- [ ] **Step 2: 实现并入检查 + PASS + Commit**

```bash
git commit --trailer "Co-authored-by: Cursor <cursoragent@cursor.com>" -m "fix: block hard-delete when asset used by arc cover or page beat"
```

---

### Task 4: 篇章列表 UI

**Files:**
- Create: `story-admin-web/src/api/arc.ts`
- Create: `views/arcs/ArcList.vue`
- Modify: `router/index.ts` — `/series/:seriesId/arcs`
- Modify: `views/series/SeriesList.vue` — 行操作「篇章」跳转；删系列时展示后端 409 文案
- Cover picker：复用 SeriesList 同款单选弹窗（复制精简逻辑即可，勿抽大公共库除非已有）

- [ ] **Step 1: api/arc.ts** — list/get/create/update/delete

- [ ] **Step 2: ArcList.vue** — 列表 + 表单（title/summary/status/cover）；删除确认「将级联删除其下所有页面」；行进「页面」

- [ ] **Step 3: 路由 + 系列入口；手工或 `npm run build` 无报错**

- [ ] **Step 4: Commit**

```bash
git commit --trailer "Co-authored-by: Cursor <cursoragent@cursor.com>" -m "feat: add arc list UI under series"
```

---

### Task 5: 页面列表 UI

**Files:**
- Create: `api/page.ts`
- Create: `views/pages/PageList.vue`
- Modify: router — `/arcs/:arcId/pages`
- Modify: ArcList 入口

- [ ] **Step 1–3: 列表 CRUD（title）；进入编辑；可选排序按钮调用 reorder**
- [ ] **Step 4: Commit** `feat: add story page list UI under arc`

---

### Task 6: 页面编辑器（时间线 + 预览）

**Files:**
- Create: `views/pages/PageEditor.vue`
- Optional: `components/story/PagePreview.vue`、`BeatBlock.vue`
- Modify: router — `/pages/:pageId/edit`
- CSS vars on preview root:

```css
.page-preview {
  --gap-beat: 44px;
  --gap-figure-text: 15px;
  --gap-inline: 9px;
  max-width: 720px;
}
.page-preview > * + * { margin-top: var(--gap-beat); }
.beat .figure { /* img */ }
.beat .figure + .children { margin-top: var(--gap-figure-text); }
.beat .children > * + * { margin-top: var(--gap-inline); }
```

**行为：**
- 左：顶层项列表（TITLE/BODY/DIVIDER/BEAT）；增删改；拖拽改序（可用 Element Plus 无拖拽则上下移按钮亦可，优先上下移按钮降低依赖）
- BEAT：选封面（素材单选弹窗）+ 子块 BODY/DIALOGUE 增删改序
- 右：只读预览，上图下文
- 保存：PUT page（title + contentJson）

- [ ] **Step 1: 实现编辑器 + 预览间距**
- [ ] **Step 2: `npm run build` PASS**
- [ ] **Step 3: Commit** `feat: add story page timeline editor with beat preview`

---

### Task 7: 文档与验收记录

**Files:**
- Update spec 状态 → 已实现
- Update README 链接说明
- Create: `docs/superpowers/acceptance/2026-08-14-story-arc-page.md`（对照规格 §6 勾选）
- Update this plan checkboxes if desired

- [ ] **Step 1: 文档**
- [ ] **Step 2: Commit** `docs: record story arc page acceptance`

---

## Spec coverage (self-review)

| Spec | Task |
|------|------|
| 篇章 CRUD / 状态 / 封面 | 1, 4 |
| 页面 CRUD / content_json / BEAT | 2, 5, 6 |
| page_asset_ref 同步 | 2 |
| 硬删 409 | 3 |
| 删篇章级联 | 2, 4 确认框 |
| 删系列有篇章 409 | 1, 4 |
| 间距变量预览 | 6 |
| 无章节/场景/阅读器 | 全局不建 |

---
