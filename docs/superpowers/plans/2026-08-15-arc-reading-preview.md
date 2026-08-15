# 篇章整篇预览与 AI 阅读流 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 管理端篇章整篇竖滚预览（页标题分隔）+ 登录可调的 `GET /api/arcs/{id}/reading-stream` 结构化阅读流，供外部 AI 按序读文看图。

**Architecture:** `ArcService.readingStream(arcId)` 加载篇章与按序页面，只读展开 `contentJson` 为 segment 列表；管理端新路由 `/arcs/:arcId/preview` 用 `getArc`+`listPages` 拼装并复用 `PagePreview`；弹窗展示/复制阅读流 URL。

**Tech Stack:** Java 17+、Spring Boot 3.3、Jackson、Vue 3、Element Plus

**Spec:** `docs/superpowers/specs/2026-08-15-arc-reading-preview-design.md`

## Global Constraints

- 在 `master` 上提交并 push
- 阅读流需登录 Session；未登录 401；篇章不存在 404
- 不做 TTS/LLM、免登、发布过滤、签名图 URL
- 图片 `contentPath` 形如 `/api/assets/{id}/content`
- JDK：`JAVA_HOME=D:\jdk\jdk-24.0.1`；Maven：`D:\tool\apache-maven-3.9.10`；Git：`D:\tool\Git\bin\git.exe` + UTF-8 无 BOM `-F`
- 改 `.java` 后确认无 `%TSD-Header-###%`
- 测试：现有 `@SpringBootTest` + H2 `create-drop` + `flyway.enabled=false`（参考 `ArcServiceTest`）

---

## File Map

| Path | Responsibility |
|------|----------------|
| `dto/ArcReadingStreamResponse.java` | 响应 DTO + segment（`Map`/`record` + type 字段） |
| `service/ArcService.java` | `readingStream(Long arcId)` |
| `controller/ArcController.java` | `GET /api/arcs/{id}/reading-stream` |
| `ArcReadingStreamTest.java` | 顺序/空页/404 单测 |
| `api/arc.ts` | `getArcReadingStream`（可选，弹窗可只拼 URL） |
| `views/arcs/ArcPreview.vue` | 整篇预览 + AI 弹窗 |
| `router/index.ts` | 注册路由 |
| `views/arcs/ArcList.vue` / `views/pages/PageList.vue` | 入口按钮 |
| `docs/superpowers/acceptance/2026-08-15-arc-reading-preview.md` | 验收 |
| `README.md` | 一行能力说明 |

---

### Task 1: 后端 reading-stream API

**Files:**
- Create: `story-admin-server/src/main/java/com/story/admin/dto/ArcReadingStreamResponse.java`
- Modify: `story-admin-server/src/main/java/com/story/admin/service/ArcService.java`
- Modify: `story-admin-server/src/main/java/com/story/admin/controller/ArcController.java`
- Create: `story-admin-server/src/test/java/com/story/admin/service/ArcReadingStreamTest.java`

**Interfaces:**
- Produces: `ArcReadingStreamResponse readingStream(Long arcId)`
- Produces: HTTP `GET /api/arcs/{id}/reading-stream`
- Segment JSON 每项至少含 `"type"`；可选字段按 type 出现（Jackson 序列化 `Map<String,Object>` 或带 `@JsonInclude(NON_NULL)` 的 record）

- [ ] **Step 1: DTO**

```java
public record ArcReadingStreamResponse(
    Long arcId,
    String arcTitle,
    String arcSummary,
    Long coverAssetId,
    String coverContentPath,
    int pageCount,
    List<Map<String, Object>> segments) {}
```

Helper:

```java
private static String contentPath(Long assetId) {
  return assetId == null ? null : "/api/assets/" + assetId + "/content";
}
```

- [ ] **Step 2: 失败测试 — BEAT 顺序**

在 `ArcReadingStreamTest`（同 `ArcServiceTest` 的 H2 配置）中：

```java
@Test
void readingStreamOrdersPagesAndBeatImageBeforeText() {
  // create series + arc + two pages via services
  // page1 contentJson:
  // [{"type":"TITLE","text":"T1"},{"type":"BEAT","coverAssetId":X,"children":[{"type":"BODY","text":"B1"},{"type":"DIALOGUE","text":"D1"}]}]
  // page2 empty "[]" title "P2"
  // assert segment types in order include:
  // ARC_TITLE, PAGE_TITLE(p1), TITLE, IMAGE(role=BEAT_COVER), BODY, DIALOGUE, PAGE_TITLE(p2)
  // assert IMAGE contentPath == "/api/assets/"+X+"/content"
}

@Test
void readingStreamNotFound() {
  assertThatThrownBy(() -> arcService.readingStream(-1L))
      .isInstanceOf(ResponseStatusException.class)
      .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
      .isEqualTo(HttpStatus.NOT_FOUND);
}
```

Need a NORMAL `Asset` for coverAssetId（persist minimal Asset like other tests）。

Run: `mvn -Dtest=ArcReadingStreamTest#readingStreamOrdersPagesAndBeatImageBeforeText test`  
Expected: FAIL（method missing）

- [ ] **Step 3: 实现 `ArcService.readingStream`**

伪代码：

```java
public ArcReadingStreamResponse readingStream(Long arcId) {
  StoryArc arc = get(arcId);
  List<StoryPage> pages = pageRepository.findByArcIdOrderBySortOrderAscIdAsc(arcId);
  List<Map<String, Object>> segments = new ArrayList<>();
  if (arc.getCoverAssetId() != null) {
    segments.add(Map.of(
        "type", "ARC_COVER",
        "assetId", arc.getCoverAssetId(),
        "contentPath", contentPath(arc.getCoverAssetId())));
  }
  segments.add(Map.of("type", "ARC_TITLE", "text", arc.getTitle()));
  if (arc.getSummary() != null && !arc.getSummary().isBlank()) {
    segments.add(Map.of("type", "ARC_SUMMARY", "text", arc.getSummary()));
  }
  for (StoryPage page : pages) {
    Map<String, Object> pageTitle = new LinkedHashMap<>();
    pageTitle.put("type", "PAGE_TITLE");
    pageTitle.put("pageId", page.getId());
    pageTitle.put("pageSortOrder", page.getSortOrder());
    pageTitle.put("text", page.getTitle());
    segments.add(pageTitle);
    appendContentSegments(segments, page);
  }
  return new ArcReadingStreamResponse(
      arc.getId(),
      arc.getTitle(),
      arc.getSummary(),
      arc.getCoverAssetId(),
      contentPath(arc.getCoverAssetId()),
      pages.size(),
      segments);
}
```

`appendContentSegments`：用 `ObjectMapper.readTree(contentJson)`；非法 JSON 当 `[]`；顶层：

- `TITLE`/`BODY`/`DIVIDER` → 对应 segment（带 `pageId`）  
- `BEAT`：若 `coverAssetId` 为数字 → `IMAGE` + `role=BEAT_COVER`；再遍历 `children` 的 `BODY`/`DIALOGUE`  
- 其它 type → skip  

注入/使用已有 `ObjectMapper`（`ArcService` 当前无 ObjectMapper——**新增构造依赖** `ObjectMapper`，或把展开放到带 ObjectMapper 的小组件 `ArcReadingStreamBuilder` 由 Spring 注入；推荐 **ArcService 增加 ObjectMapper 字段**）。

- [ ] **Step 4: Controller**

```java
@GetMapping("/api/arcs/{id}/reading-stream")
public ArcReadingStreamResponse readingStream(@PathVariable Long id) {
  return arcService.readingStream(id);
}
```

- [ ] **Step 5: 测试 PASS；Commit + push**

```bash
git commit -m "feat: add arc reading-stream API for ordered text and images"
```

---

### Task 2: 前端 ArcPreview 页 + 路由 + 列表入口

**Files:**
- Create: `story-admin-web/src/views/arcs/ArcPreview.vue`
- Modify: `story-admin-web/src/router/index.ts`（及若存在的 `index.js` 同步或仅改 ts 源）
- Modify: `story-admin-web/src/views/arcs/ArcList.vue`
- Modify: `story-admin-web/src/views/pages/PageList.vue`
- Optionally extract parse helper from `PageEditor.vue` — **允许**在 `ArcPreview.vue` 内复制精简版 `parseContent`（只产出 `PagePreviewItem[]`），避免大范围重构；若易抽到 `utils/pageContent.ts` 更好

**Interfaces:**
- Consumes: `getArc`, `listPages`, `getSeries`（用于顶栏系列名）, `PagePreview`
- Route name: `arc-preview` path `/arcs/:arcId/preview`

- [ ] **Step 1: 路由**

```ts
import ArcPreview from '../views/arcs/ArcPreview.vue';
// ...
{ path: '/arcs/:arcId/preview', name: 'arc-preview', component: ArcPreview },
```

- [ ] **Step 2: `ArcPreview.vue` 骨架**

```vue
<script setup lang="ts">
// load arcId from route
// parallel: getArc, listPages; then getSeries(arc.seriesId) if present
// parse each page.contentJson → items
// back: if query.from=pages → /arcs/:id/pages else /series/:seriesId/arcs
</script>
<template>
  <section class="arc-preview-page">
    <header>
      <el-button @click="goBack">返回</el-button>
      <h2>{{ seriesName }} · {{ arc?.title }}</h2>
      <!-- AI 按钮在 Task 3 -->
    </header>
    <div v-loading="loading" class="reader">
      <img v-if="arc?.coverAssetId" :src="assetContentUrl(arc.coverAssetId)" class="arc-cover" />
      <h1>{{ arc?.title }}</h1>
      <p v-if="arc?.summary" class="summary">{{ arc.summary }}</p>
      <p v-if="!pages.length" class="empty">本篇章暂无页面</p>
      <template v-for="page in pages" :key="page.id">
        <h2 class="page-title">{{ page.title }}</h2>
        <PagePreview :items="parsedByPageId[page.id!]" />
        <!-- PagePreview 空 items 时自带「暂无内容」 -->
      </template>
    </div>
  </section>
</template>
```

样式：阅读区 max-width ~720px，居中；页标题与块间距参考 `PagePreview` CSS 变量。

- [ ] **Step 3: 入口**

`ArcList.vue`：操作列加「预览」→ `router.push(\`/arcs/${row.id}/preview\`)`；列宽约 260。

`PageList.vue`：顶栏「整篇预览」→ `router.push({ path: \`/arcs/${arcId}/preview\`, query: { from: 'pages' } })`。

- [ ] **Step 4: `npm run build` PASS；Commit + push**

```bash
git commit -m "feat: add admin arc full-story preview page"
```

---

### Task 3: AI 阅读流弹窗

**Files:**
- Modify: `story-admin-web/src/views/arcs/ArcPreview.vue`
- Optional: `story-admin-web/src/api/arc.ts` 增加：

```ts
export function arcReadingStreamUrl(arcId: number): string {
  return `/api/arcs/${arcId}/reading-stream`;
}
```

- [ ] **Step 1: 顶栏按钮「AI 阅读流」打开 `el-dialog`**

展示：

- 完整 URL：`window.location.origin + arcReadingStreamUrl(arcId)`  
- 用法三点：登录 Session；按 `segments` 顺序；`text` 朗读 / `contentPath` 拉图  

按钮「复制链接」：`navigator.clipboard.writeText(url)` + `ElMessage.success`

- [ ] **Step 2: build PASS；Commit + push**

```bash
git commit -m "feat: add AI reading-stream URL dialog on arc preview"
```

---

### Task 4: 验收文档

**Files:**
- Create: `docs/superpowers/acceptance/2026-08-15-arc-reading-preview.md`
- Modify: `docs/superpowers/specs/2026-08-15-arc-reading-preview-design.md` — 状态 → 已实现（首期）
- Modify: `README.md` — 能力列表加一行

- [ ] **Step 1: 跑验证并填表**

```bash
mvn -Dtest=ArcReadingStreamTest test
npm run build
```

对照 spec §7：PASS / PARTIAL / NOT TESTED（浏览器预览可标未点）。

- [ ] **Step 2: Commit + push**

```bash
git commit -m "docs: record arc reading preview acceptance"
```

---

## Spec coverage (self-review)

| Spec | Task |
|------|------|
| 预览路由/竖滚/页标题/复用 PagePreview | T2 |
| 篇章头封面标题简介 | T2 |
| 空态 | T2 |
| reading-stream schema + 顺序 | T1 |
| 登录 Session / 404 | T1（401 由现有 Security） |
| AI 弹窗复制链接 | T3 |
| 验收文档 | T4 |
| 非目标未实现 | 遵守 |

无 TBD；segment 用 `Map` 保证 JSON 形状灵活且与 spec 字段名一致。
