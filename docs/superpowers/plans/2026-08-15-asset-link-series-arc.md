# 素材关联系列/篇章/人物 Implementation Plan

> **For agentic workers:** Use subagent-driven-development or execute inline task-by-task.

**Goal:** 素材可互斥关联系列/篇章/人物（均可多选）；工作台筛选与编辑；上传时按当前筛选自动关联；硬删 409。

**Spec:** `docs/superpowers/specs/2026-08-15-asset-link-series-arc-design.md`（含 §8 上传自动关联）

**Architecture:** V9 两张 rel 表；`AssetService.applyLinks(linkType, ids)` 互斥写入；list 扩展 linkType/seriesId/arcId；upload 可选同参；工作台 UI 联动。

**Tech Stack:** Spring Boot 3.3、JPA、Vue3、Flyway V9

## Global Constraints

- master；Git `-F` UTF-8；JDK/Node 同约定；TSD 明文
- 互斥三类；多选；上传按筛选自动挂（人物/系列/篇章具体选中时）
- 验收后 commit + push

## Tasks

### Task 1: V9 + rel 实体 + applyLinks + update/hydrate + hardDelete

- Create V9 SQL, AssetSeriesRel(+Id), AssetArcRel(+Id), repos
- Enum `AssetLinkType` NONE/SERIES/ARC/CHARACTER
- Asset transient: seriesIds, arcIds, linkType
- AssetUpdateRequest + linkType/seriesIds/arcIds
- applyLinks; update uses it; hardDelete checks + delete rels
- Tests: update mutex; hardDelete series/arc link

### Task 2: list 筛选 + upload 可选关联

- Extend AssetRepository.search / AssetService.list / Controller
- upload(categoryId, files, linkType, seriesIds, arcIds, characterIds)
- Tests: list by linkType; upload with characterId auto-link

### Task 3: 工作台 UI

- 关联类型筛选 + 系列/篇章选择；属性区编辑；upload 带当前筛选参数
- npm run build

### Task 4: 文档验收 + push

- Spec 状态已实现；acceptance；README；commit push
