# 篇章与故事页面（首期）设计说明

> 状态：已确认（待实现）  
> 日期：2026-08-14  
> 分支策略：在 `master` 上实现  
> 依赖：`docs/superpowers/specs/2026-08-14-story-page-beat-layout-design.md`（画面组与间距）  
> 前置：故事系列首期已落地  

## 1. 背景与目标

在系列之下提供可编辑的「篇章 → 故事页面」链路，并在页面内用时间线编排独立块与画面组，管理端预览验证图文间距。

首期目标：

1. 系列下篇章 CRUD（轻量字段）。  
2. 篇章下故事页面 CRUD；页面内容为 `content_json`（遵守画面组规格）。  
3. `page_asset_ref` 同步 BEAT 封面引用；硬删素材统一拦截。  
4. 管理端页面编辑器（时间线 + 预览）；**不做**使用端阅读器、章节/场景实体。

## 2. 已确认决策

| 项 | 决策 |
|----|------|
| 树形范围 | 系列 → 篇章 → 页面（跳过章节/场景） |
| 篇章字段 | 标题、简介、状态、可选封面、排序 |
| 篇章状态 | `DRAFT` / `WRITING` / `FINALIZED` |
| 内容存储 | `content_json` + `page_asset_ref` 同步 |
| 落地路径 | 独立表 + 系列下篇章列表 + 页面编辑器 |
| 删篇章 | 级联删除其下页面与 ref（确认框提示） |
| 删系列 | 若仍有篇章 → 409 |
| 使用端 | 首期不做 |

## 3. 数据模型

### 3.1 `story_arc`（Flyway 下一版本，如 V7）

| 列 | 说明 |
|----|------|
| id | PK |
| series_id | NOT NULL，属系列 |
| code | UNIQUE，如 `A000001` |
| title | NOT NULL |
| summary | TEXT NULL |
| status | NOT NULL，默认 `DRAFT` |
| cover_asset_id | NULL |
| sort_order | INT DEFAULT 0 |
| created_at / updated_at | |

### 3.2 `story_page`

| 列 | 说明 |
|----|------|
| id | PK |
| arc_id | NOT NULL |
| title | NOT NULL |
| sort_order | INT DEFAULT 0 |
| content_json | JSON/LONGTEXT NOT NULL，默认 `[]` |
| created_at / updated_at | |

`content_json` 顶层项结构见画面组规格（`TITLE`/`BODY`/`DIVIDER`/`BEAT`；BEAT 含 `coverAssetId` + `children`）。

### 3.3 `page_asset_ref`

| 列 | 说明 |
|----|------|
| page_id | |
| asset_id | |
| ref_kind | 首期仅 `BEAT_COVER` |
| PK | (page_id, asset_id, ref_kind) 或含自增 id |

保存页面时：解析 JSON 中全部 BEAT 封面 → 删除旧 ref → 插入新 ref。

### 3.4 引用与删除

- 硬删素材：系列封面 ∪ 篇章封面 ∪ `page_asset_ref` → 任一命中则 409。  
- 删篇章：级联删 `story_page` 与对应 `page_asset_ref`。  
- 删系列：`count(arcs by series_id)>0` → 409。  
- 封面素材须存在且 NORMAL（创建/更新时校验）。

## 4. API（示意）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET/POST | `/api/series/{seriesId}/arcs` | 列表/创建 |
| GET/PUT/DELETE | `/api/arcs/{id}` | 详情/更新/删除（级联页） |
| GET/POST | `/api/arcs/{arcId}/pages` | 页面列表/创建 |
| GET/PUT/DELETE | `/api/pages/{id}` | 详情（含 content）/保存 content/删页 |
| PUT | `/api/arcs/{arcId}/pages/reorder` | 可选：页面排序 |

## 5. 管理端 UI

- 系列列表 → 进入篇章列表：`/series/:seriesId/arcs`  
- 篇章列表 CRUD；进入页面列表：`/arcs/:arcId/pages`  
- 页面编辑：`/pages/:pageId/edit`  
  - 左：时间线（独立块 / 画面组，拖拽排序）  
  - 右：预览（共用 `--gap-beat` / `--gap-figure-text` / `--gap-inline`）  
  - 画面组：单选封面 + BODY/DIALOGUE 子块  
- 保存触发 JSON + ref 重建  

## 6. 验收标准

1. 可在某系列下创建/编辑/删除篇章；有页面时删篇章须确认后级联清理。  
2. 可创建页面并保存含 BEAT 的 content；预览上图下文且组外疏、图文中、组内紧。  
3. 硬删被 BEAT/篇章/系列用作封面的素材 → 409。  
4. 有篇章时删系列 → 409。  
5. 无章节/场景实体；无使用端阅读路由。

## 7. 非目标

- 章节、场景实体  
- 使用端阅读器 / 发布流程  
- 左右分栏、组内多图  
- 将人物 `storyName` 强制改为篇章外键  

## 8. 实现备注

- Git：`master`；JDK/Node 同项目约定；TSD 注意明文  
- 实现计划须引用画面组规格；Flyway 版本号以仓库当前最大 +1 为准（系列为 V6 则本模块从 V7 起）
