# 篇章与故事页面（首期）验收记录

> 日期：2026-08-14  
> 规格：[篇章与故事页面设计](../specs/2026-08-14-story-arc-page-design.md) §6  
> 依赖：[画面组与间距](../specs/2026-08-14-story-page-beat-layout-design.md) §7（随本模块落地）

## 实现提交

| Task | Commit | 说明 |
|------|--------|------|
| 1 | `6a06ac2` | 篇章 CRUD；有篇章时删系列 409 |
| 2 | `9e72dfa` | 页面 CRUD；content_json；page_asset_ref 同步 |
| 3 | `34d0110` | 硬删篇章封面 / BEAT 封面 409 |
| 4 | `097a3ff` | 系列下篇章列表 UI |
| 5 | `178654d` | 篇章下页面列表 UI |
| 6 | `b74a582` | 时间线编辑器 + BEAT 预览间距 |

## 验收表（Spec §6）

| # | Criterion | Result | Evidence |
|---|-----------|--------|----------|
| 1 | 系列下篇章 CRUD；删篇章须确认后级联清理页面 | **PASS** (UI browser **PARTIAL**) | ArcServiceTest；PageServiceTest#deleteArcCascadesPagesAndRefs；ArcList.vue 确认框 |
| 2 | 页面保存含 BEAT 的 content；预览上图下文，组外疏/图文中/组内紧 | **PASS** (UI browser **PARTIAL**) | PageServiceTest BEAT ref 同步；PagePreview.vue `--gap-beat` / `--gap-figure-text` / `--gap-inline`；PageEdit Task 6 |
| 3 | 硬删被 BEAT / 篇章 / 系列用作封面的素材 → 409 | **PASS** | AssetHardDeleteArcCoverTest；AssetHardDeletePageBeatTest；系列封面既有测试 |
| 4 | 有篇章时删系列 → 409 | **PASS** | ArcServiceTest#deleteSeriesBlockedWhenArcsExist |
| 5 | 无章节 / 场景实体；无使用端阅读路由 | **PASS** | 仓库无 story_chapter / scene 实体；story-user-web 无阅读路由 |

| Extra | Result | Evidence |
|-------|--------|----------|
| Backend tests | **PASS** | ArcServiceTest, PageServiceTest, AssetHardDeleteArcCoverTest, AssetHardDeletePageBeatTest |
| Frontend build | **PASS** | Task 6 `npm run build` |
| UI browser | **PARTIAL OK** | 按 brief 可接受 |

**Tasks 1–7 steps:** 全部完成。
