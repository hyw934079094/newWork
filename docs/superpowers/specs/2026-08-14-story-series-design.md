# 故事系列管理（首期）设计说明

> 状态：已实现（首期）  
> 日期：2026-08-14  
> 分支策略：在 `master` 上实现  
> 路线图顺序：1 故事系列 → 3 篇章/内容块 → 6 其它页美化（本期仅第 1 项）

## 1. 背景与目标

管理端素材/人物基础已落地，但尚无正式「故事系列」实体；素材仅有可空 `series_id`，人物用文本 `story_name`。按 MVP，下一步需要可维护的系列壳子，供后续篇章挂靠。

本期目标：

1. 新增 `story_series` 与 `/api/series` CRUD。  
2. 管理端「故事系列」列表/编辑，含状态、简介、标签、封面（素材库单选）。  
3. 硬删素材时若被用作系列封面则 409。  
4. **不**强制人物/素材挂系列；**不**做世界观大段字段与使用端发布。

## 2. 已确认决策

| 项 | 决策 |
|----|------|
| 首期档位 | A：CRUD + 状态 + 封面/简介/题材标签 |
| 封面 | 素材库单选，存 `cover_asset_id` |
| 状态 | `DRAFT` / `SERIALIZING` / `COMPLETED` / `PUBLISHED` |
| 标签 | 单文本字段，逗号分隔 |
| 实现路径 | 独立表 + `/series` 页 + 单选封面弹窗 |
| 硬删素材作封面 | 409 Conflict |
| 删系列 | 本期无篇章则可删；只清封面引用，不删素材文件 |
| 人物/素材挂载 | 本期不做 |
| 后续 | 篇章模块、其它页美化另开 |

## 3. 数据模型

### 3.1 表 `story_series`

Flyway：`V6__story_series.sql`

| 列 | 类型 | 说明 |
|----|------|------|
| id | BIGINT PK AI | |
| code | VARCHAR(50) UNIQUE NOT NULL | 如 `S000001`，服务端生成 |
| name | VARCHAR(200) NOT NULL | |
| status | VARCHAR(20) NOT NULL | 枚举字符串，默认 `DRAFT` |
| cover_asset_id | BIGINT NULL | 逻辑引用 `asset.id` |
| summary | TEXT NULL | 公开简介 |
| tags | VARCHAR(500) NULL | 逗号分隔标签原文 |
| sort_order | INT NOT NULL DEFAULT 0 | |
| created_at / updated_at | DATETIME NOT NULL | |

索引：`status`；可选 `(sort_order, id)`。

### 3.2 约束与引用

- 创建/更新：`name` 必填非空；`status` 必须为四态之一；`cover_asset_id` 若有值须存在且 `status=NORMAL`。  
- `AssetService.hardDelete`：若存在系列以该素材为封面 → 409，文案标明系列名/code。  
- 删除系列：删除行即可；不级联删素材。

## 4. API

前缀：`/api/series`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/` | 列表；可选 `q`（匹配 name/code/tags）、`status` |
| GET | `/{id}` | 详情；可附带封面预览用 `coverAssetId` |
| POST | `/` | 创建 |
| PUT | `/{id}` | 更新 |
| DELETE | `/{id}` | 删除 |

请求/响应字段（camelCase）：`id`、`code`、`name`、`status`、`coverAssetId`、`summary`、`tags`、`sortOrder`、时间戳。列表按 `sort_order`、`id` 排序（首期可不提供拖拽改序 UI，创建时 `sort_order=0` 或按 id）。

## 5. 管理端 UI

- 侧栏：「故事系列」→ 路由 `/series`  
- 列表：封面缩略图（`assetContentUrl(coverAssetId)`）、编号、名称、状态、标签、编辑/删除；筛选关键字 + 状态；新增  
- 编辑弹窗（建议宽约 720–840px）：名称、状态下拉、简介、标签（placeholder 提示逗号分隔）、封面预览 +「选择封面」「清除」  
- 封面挑选：二级弹窗，**单选**；分类 + 关键字 + 缩略图网格（交互对齐人物素材挑选，改为单选）  
- 排版：避免一字换行；筛选与操作区对齐（参考人物页首期美化）

## 6. 非目标

- 人物 `storyName` / 素材 `seriesId` 强制或迁移到系列  
- 世界观、文风、AI 约束等大段设定  
- 篇章/章节、使用端展示、发布流程  
- 系列标签独立实体表  
- 整站其它页面美化（路线图第 6 项另做）

## 7. 验收标准

1. 可创建/编辑/删除系列；状态四态可选；标签与简介可空可存。  
2. 可从素材库单选封面并预览；可清除封面；硬删该封面素材 → 409。  
3. 列表可按关键字/状态筛选；侧栏可进入 `/series`。  
4. 不改动现有人物/素材归属行为。

## 8. 实现备注

- Git：`D:\tool\Git\bin\git.exe` + `-F` UTF-8 无 BOM；`master` 直接提交  
- JDK / Node：`JAVA_HOME=D:\jdk\jdk-24.0.1`；Node `D:\tool\nvm\v22.17.0`  
- TSD：改 `.java`/`.sql`/`.vue`/`.md` 后验明文；必要时 `.txt` + `cmd ren`
