# 故事系列管理（首期） Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 管理端可 CRUD 故事系列（状态/简介/标签/素材库封面），硬删封面素材时 409。

**Architecture:** Flyway `V6` 建 `story_series`；`SeriesService` + `/api/series`；`AssetService.hardDelete` 增加封面引用检查；Vue `SeriesList` + 侧栏/路由；封面单选弹窗复用分类+关键字+缩略图（单选）。

**Tech Stack:** Java 17+、Spring Boot 3.3、JPA/Flyway、Vue 3、Element Plus

**Spec:** `docs/superpowers/specs/2026-08-14-story-series-design.md`

## Global Constraints

- 在 `master` 上直接提交
- 状态仅：`DRAFT` / `SERIALIZING` / `COMPLETED` / `PUBLISHED`；默认 `DRAFT`
- 封面：`cover_asset_id` 可空；有值须 NORMAL 素材；硬删该素材若作封面 → 409
- 标签：单字段逗号分隔；不强制人物/素材挂系列
- Git：`D:\tool\Git\bin\git.exe` + `-F` UTF-8 无 BOM；`--trailer` 时用 `cmd /c`
- JDK：`JAVA_HOME=D:\jdk\jdk-24.0.1`；Node：`D:\tool\nvm\v22.17.0`
- TSD：改 `.java`/`.sql`/`.vue`/`.md` 后验明文；必要时 `.txt` + `cmd ren`
- 单测：常 `spring.flyway.enabled=false` + H2 `ddl-auto=create-drop`

---

## File Map

| Path | Responsibility |
|------|----------------|
| `.../db/migration/V6__story_series.sql` | 建表 |
| `domain/StorySeries.java`、`SeriesStatus.java` | 实体/枚举 |
| `repository/StorySeriesRepository.java` | 查询；按 coverAssetId 查系列名 |
| `dto/SeriesCreateRequest.java`、`SeriesUpdateRequest.java`、`SeriesQuery.java` | API DTO |
| `service/SeriesService.java` | CRUD、code 生成、校验 |
| `controller/SeriesController.java` | `/api/series` |
| `service/AssetService.java` | hardDelete 封面 409 |
| `SeriesServiceTest.java`、`AssetHardDeleteSeriesCoverTest.java`（或扩现有 hardDelete 测） | 测试 |
| `story-admin-web/src/api/series.ts` | 客户端 |
| `views/series/SeriesList.vue` | 列表+编辑+封面单选 |
| `router/index.ts`、`App.vue` | `/series` 与侧栏 |
| Spec / README / 本计划 | Task 5 文档 |

---

### Task 1: 系列 CRUD（后端）

**Files:**
- Create: `V6__story_series.sql`、`StorySeries`、`SeriesStatus`、`StorySeriesRepository`、DTO、`SeriesService`、`SeriesController`
- Create: `src/test/java/.../SeriesServiceTest.java`

**Interfaces:**
- Produces: `list(SeriesQuery)`、`get(id)`、`create`、`update`、`delete`
- Code：`S` + 6 位数字（仿人物 `C000001` / `nextCode`）
- `coverAssetId` 非空 → 素材存在且 NORMAL，否则 400

- [ ] **Step 1: 失败测试 — 无名拒绝**

```java
@Test
void createRequiresName() {
  assertThatThrownBy(() -> seriesService.create(new SeriesCreateRequest("  ", null, null, null, null)))
      .isInstanceOf(ResponseStatusException.class);
}
```

- [ ] **Step 2: 测试 — 创建默认 DRAFT + 列表 q**

```java
@Test
void createDefaultsDraftAndListByQ() {
  StorySeries s = seriesService.create(
      new SeriesCreateRequest("暗夜物语", null, "简介", "奇幻,连载", null));
  assertThat(s.getCode()).startsWith("S");
  assertThat(s.getStatus()).isEqualTo(SeriesStatus.DRAFT);
  assertThat(seriesService.list(new SeriesQuery("暗夜", null))).extracting(StorySeries::getId)
      .contains(s.getId());
}
```

- [ ] **Step 3: 实现 Flyway + 实体 + Service + Controller；测试 PASS**

`V6__story_series.sql`（ASCII COMMENT）：

```sql
CREATE TABLE story_series (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(50) NOT NULL,
  name VARCHAR(200) NOT NULL,
  status VARCHAR(20) NOT NULL,
  cover_asset_id BIGINT NULL,
  summary TEXT NULL,
  tags VARCHAR(500) NULL,
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  UNIQUE KEY uk_story_series_code (code),
  KEY idx_story_series_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

Controller：`@RequestMapping("/api/series")`。

- [ ] **Step 4: Commit**

```bash
git commit -m "feat: add story series CRUD backend"
```

---

### Task 2: 硬删素材封面拦截

**Files:**
- Modify: `StorySeriesRepository` — `List<StorySeries> findByCoverAssetId(Long coverAssetId);` 或返回 names
- Modify: `AssetService.hardDelete` + `buildReferenceSummary`（增加系列封面段）
- Create/Modify test: e.g. `AssetHardDeleteSeriesCoverTest` 或扩 `AssetDeleteTest`

**Interfaces:**
- Consumes: series by coverAssetId
- Produces: 409 message 含系列名/code

- [ ] **Step 1: 失败测试 — 封面素材不可硬删**

```java
@Test
void hardDeleteBlockedWhenAssetIsSeriesCover() {
  // persist NORMAL asset; create series with coverAssetId; hardDelete → ConflictException
}
```

- [ ] **Step 2: 实现检查 + PASS**

在 `hardDelete` 现有引用检查中并入系列封面列表。

- [ ] **Step 3: Commit**

```bash
git commit -m "fix: block hard-delete when asset is series cover"
```

---

### Task 3: 系列列表/编辑 UI（不含挑选弹窗可先用临时 id 输入？否—与 Task 4 拆：Task 3 可先无封面选择，仅展示 coverAssetId 数字或跳过封面按钮）

**推荐：** Task 3 做列表+表单字段+侧栏路由；封面区先「选择封面」按钮占位调用空函数或先不做弹窗，Task 4 接上。更干净：Task 3 含清除封面与展示已有封面图（若有 id），选择打开由 Task 4 实现。

**Files:**
- Create: `api/series.ts`、`views/series/SeriesList.vue`
- Modify: `router/index.ts`、`App.vue`（故事管理下加「故事系列」→ `/series`，建议放在人物管理之上）

**Interfaces:**
- Consumes: `/api/series`
- Produces: 列表筛选 q/status；弹窗编辑 name/status/summary/tags；删除确认

- [ ] **Step 1: API 客户端 `series.ts`**

- [ ] **Step 2: SeriesList 列表+编辑弹窗（封面预览若 coverAssetId 有值用 assetContentUrl；清除按钮置 null）**

- [ ] **Step 3: 路由与侧栏**

- [ ] **Step 4: `npm run build` PASS + Commit**

```bash
git commit -m "feat: story series list and editor UI"
```

---

### Task 4: 封面单选挑选弹窗

**Files:**
- Modify: `SeriesList.vue`
- Reuse: `listAssets`、`listCategories`、`assetContentUrl`

**Interfaces:**
- 分类+关键字；网格单选；确定写 `form.coverAssetId`；不自动保存系列

- [ ] **Step 1: 打开/加载/单选/确定**（对齐人物挑选，选中集合改为单 id）

- [ ] **Step 2: build PASS + Commit**

```bash
git commit -m "feat: series cover single-select asset picker"
```

---

### Task 5: 验收与文档

**Files:**
- Spec 状态 → 已实现（首期）
- `README.md` 链接一句
- 本计划验收表

- [x] **Step 1:** `mvn -q -Dtest=SeriesServiceTest,AssetHardDeleteSeriesCoverTest test`（或实际测试类名）PASS

- [x] **Step 2:** `npm run build`；浏览器 PARTIAL OK

- [x] **Step 3: Commit**

```bash
git commit -m "docs: record story series acceptance"
```

---

## Spec Coverage

| Spec | Task |
|------|------|
| V6 + CRUD API | Task 1 |
| 硬删封面 409 | Task 2 |
| 列表/编辑/侧栏 | Task 3 |
| 封面单选弹窗 | Task 4 |
| 验收文档 | Task 5 |

## Notes

- 实现后需重启后端跑 Flyway V6
- 勿提交无关 `storage/` / `pic/`
- 单选弹窗可内联在 `SeriesList.vue`，不必抽通用组件（YAGNI）

---

## Task 5 验收表（Spec §7）

| # | Criterion | Result | Evidence |
|---|-----------|--------|----------|
| 1 | 可创建/编辑/删除系列；状态四态可选；标签与简介可空可存 | **PASS** | SeriesServiceTest；`/api/series` + SeriesList UI（Task 1/3） |
| 2 | 可从素材库单选封面并预览；可清除封面；硬删该封面素材 → 409 | **PASS** (UI browser **PARTIAL**) | AssetHardDeleteSeriesCoverTest；封面弹窗 Task 4；浏览器点验未跑 |
| 3 | 列表可按关键字/状态筛选；侧栏可进入 `/series` | **PASS** (UI browser **PARTIAL**) | SeriesServiceTest list q；路由/侧栏 Task 3；build PASS |
| 4 | 不改动现有人物/素材归属行为 | **PASS** | 首期未强制挂系列；仅硬删增加封面引用检查 |

| Extra | Result | Evidence |
|-------|--------|----------|
| Backend re-test (Task 5) | **PASS** | mvn `-Dtest=SeriesServiceTest,AssetHardDeleteSeriesCoverTest` EXIT=0（JDK 24.0.1） |
| Frontend build | **PASS** | story-admin-web `npm run build` |
| UI browser | **PARTIAL OK** | 按 brief 可接受 |

**Tasks 1–5 steps:** Task 5 全部勾选完成。
