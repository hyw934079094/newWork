# 素材关联系列 / 篇章 / 人物（互斥）设计说明

> 状态：已确认（待实现）  
> 日期：2026-08-15  
> 分支策略：在 `master` 上实现  
> 前置：故事系列、篇章、素材工作台人物关联已落地  

## 1. 背景与目标

素材目前主要通过 `asset_character_rel` 关联人物。业务上还需要：

- **系列级**：整部故事通用（场景、气氛等，通常无人物）  
- **篇章级**：主要在该篇章出现（通常无人物）  
- **人物级**：人物向素材（隐含属于人物所在故事语境）

首期目标：

1. 三类关联互斥；每类均可多选。  
2. 工作台可编辑关联，并按关联类型（及具体系列/篇章）筛选。  
3. 硬删时系列/篇章引用与人物一样拦截 409。

## 2. 已确认决策

| 项 | 决策 |
|----|------|
| 互斥 | 系列 / 篇章 / 人物三选一，不可混挂 |
| 数量 | 三类均可多选（业务上通常少选） |
| 语义 | 挂人物不必再挂系列；单独挂系列/篇章表示无人物向通图 |
| 存储 | 方案 1：关系表 `asset_series_rel` + `asset_arc_rel`，保留 `asset_character_rel` |
| 旧字段 | `asset.series_id` 不再作为关联来源（可空置；不在本需求强迁数据） |
| 筛选 | 关联类型全部/未关联/系列/篇章/人物 + 具体系列/篇章；人物筛选保留 |

## 3. 数据模型

### 3.1 `asset_series_rel`（Flyway V9）

| 列 | 说明 |
|----|------|
| asset_id | PK 部分 |
| series_id | PK 部分，指向 `story_series.id` |

### 3.2 `asset_arc_rel`

| 列 | 说明 |
|----|------|
| asset_id | PK 部分 |
| arc_id | PK 部分，指向 `story_arc.id` |

### 3.3 既有

- `asset_character_rel` 不变。  
- 硬删检查并入 series/arc rel（及既有 character / cover / beat 等）。

### 3.4 保存互斥规则

请求携带逻辑类型 `linkType`：`NONE` | `SERIES` | `ARC` | `CHARACTER`，以及：

- `seriesIds: Long[]`  
- `arcIds: Long[]`  
- `characterIds: Long[]`  

服务端行为：

| linkType | 写入 | 清空 |
|----------|------|------|
| SERIES | seriesIds（去重、校验存在） | arc + character |
| ARC | arcIds（校验篇章存在） | series + character |
| CHARACTER | characterIds（同现逻辑） | series + arc |
| NONE | — | 三类全清 |

非法：`linkType=SERIES` 但 seriesIds 空 → 400（或视为 NONE，**本规格取 400**，避免误清空意图不明）。`ARC`/`CHARACTER` 同理要求非空列表。

## 4. API

### 4.1 更新素材

扩展现有 `PUT /api/assets/{id}` body：

- `linkType`（必填或与三类 ids 兼容推断；**首期必填**，前端始终提交）  
- `seriesIds` / `arcIds` / `characterIds`（按类型使用，其它可 `[]`）

响应 Asset JSON 增加：

- `linkType`  
- `seriesIds` / `arcIds` / `characterIds`  

### 4.2 列表

扩展 `GET /api/assets`：

| 参数 | 说明 |
|------|------|
| linkType | 可选：`NONE`（未关联）/ `SERIES` / `ARC` / `CHARACTER`；不传=全部 |
| seriesId | 可选；与 SERIES 或 ARC 筛选配合（ARC 时表示该系列下篇章关联的素材） |
| arcId | 可选；精确篇章 |
| characterFilter / characterId / q / categoryId / status | 保留现有语义 |

组合约定：

- `linkType=CHARACTER` 时沿用人物筛选参数。  
- `linkType=SERIES` + `seriesId` → 关联了该系列的素材。  
- `linkType=ARC` + `seriesId`（无 arcId）→ 关联了该系列下任意篇章的素材。  
- `linkType=ARC` + `arcId` → 关联了该篇章的素材。  
- `linkType=NONE` → 三类 rel 皆空。

## 5. 管理端 UI（工作台）

### 5.1 右侧属性

- 关联类型单选：无 / 系列 / 篇章 / 人物  
- 系列：多选系列  
- 篇章：选系列（过滤）+ 多选篇章  
- 人物：现有多选  
- 切换类型清空其它已选并提示；保存提交完整 `linkType` + ids  

### 5.2 顶部筛选

- 关联类型：全部 / 未关联 / 系列 / 篇章 / 人物  
- 系列类型：可选具体系列  
- 篇章类型：可选系列，再可选篇章  
- 人物类型：保留「无关联人物 / 全部 / 某人」（与 linkType=CHARACTER 叠加时，「无关联」指无人物关联且类型为人物——**简化**：人物类型下人物下拉语义与现网一致；`linkType=NONE` 用关联类型「未关联」表达）  

说明：现网「人物筛选=无关联」与新「关联类型=未关联」可能重叠；约定：

- 关联类型「未关联」= 三类都无  
- 关联类型「人物」+ 人物「无关联」不再使用；人物下拉里「无关联」仅在关联类型=全部时保留旧行为，或 **首期：关联类型≠全部时隐藏人物「无关联」项**（推荐）

### 5.3 非目标

- 系列/篇章详情页反向批量挂素材  
- 使用端按关联展示  
- 强制人物必须属于某系列外键（人物-系列关系若未建全，不阻塞本需求）

## 6. 验收标准

1. 可将素材挂到多个系列，或改挂多个篇章，或改挂多个人物；切换类型后旧类关联被清空。  
2. 混挂（同时提交两类非空）被后端拒绝或按 linkType 只写一类（**按 linkType 为准并清空其它**）。  
3. 筛选：未关联 / 系列 / 篇章 / 人物及具体 id 结果正确。  
4. 硬删被系列或篇章关联的素材 → 409，文案含名称。  
5. 分类内排序、跨分类拖拽、替换图片行为不受破坏。

## 7. 实现备注

- Git：`master`；JDK/Node 同项目约定；TSD 注意明文  
- Flyway 版本以仓库当前最大 +1（当前 V8 则本模块 V9）  
- 测试：H2 create-drop；更新互斥 + list 筛选 + hardDelete 测例  
- 验收通过后 commit + push
