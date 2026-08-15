# 篇章整篇预览与 AI 阅读流 验收（2026-08-15）

> 规格：[设计说明](../specs/2026-08-15-arc-reading-preview-design.md) §7

## 实现提交

| Task | Commit | 说明 |
|------|--------|------|
| 1 | `87256c5` | `GET /api/arcs/{id}/reading-stream` + `ArcReadingStreamTest` |
| 2 | `b303a88` | `ArcPreview.vue` 整篇竖滚预览 + 列表入口 |
| 3 | `a56d2d1` | 「AI 阅读流」弹窗 + 复制链接 |

## 验收表（Spec §7）

| # | 标准 | 结果 | 证据 |
|---|------|------|------|
| 1 | 多页篇章「预览」连续竖滚可读，页标题分隔，BEAT 上图下文顺序正确 | **PARTIAL** | `ArcPreview.vue` 复用 `PagePreview`；`ArcList`/`PageList` 入口；浏览器竖滚与 BEAT 顺序 **NOT TESTED** |
| 2 | 无页面 / 空内容有明确空态 | **PARTIAL** | `ArcPreview.vue`：「本篇章暂无页面」+ `PagePreview`「暂无内容」；浏览器点验 **NOT TESTED** |
| 3 | 登录可调 `reading-stream`，segment 顺序与预览语义一致 | **PASS** | `ArcReadingStreamTest#readingStreamOrdersPagesAndBeatImageBeforeText` — ARC_TITLE → PAGE_TITLE → TITLE → IMAGE(BEAT_COVER) → BODY → DIALOGUE → PAGE_TITLE |
| 4 | 未登录 401；篇章不存在 404 | **PARTIAL** | `#readingStreamNotFound` → 404 PASS；401 依赖现有 Session Security（本任务未 MockMvc 复测） |
| 5 | 「复制链接」可用 | **PARTIAL** | `ArcPreview.vue`：`navigator.clipboard.writeText` + `ElMessage`；浏览器点击 **NOT TESTED** |

## 验证命令

| 命令 | 结果 |
|------|------|
| `JAVA_HOME=D:\jdk\jdk-24.0.1 mvn -Dtest=ArcReadingStreamTest test`（story-admin-server） | **PASS** — Tests run: 2, Failures: 0, Errors: 0 |
| `npm run build`（story-admin-web） | **PASS** — exit 0，built ~7.7s |

**Tasks 1–4 steps:** 全部完成。
