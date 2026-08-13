# 素材组合编排设计说明

> 日期：2026-08-13  
> 状态：已实现（首期）  

> 关联：`docs/PROJECT_REQUIREMENTS.md`、素材模块设计  
> 实现路线：A（独立「组合编排」模块）

## 1. 背景与目标

在管理端提供「素材组合编排」：将多张素材编成可预览的序列动画效果（按自定义播放序列定时切换），支持默认间隔与按播放步个性化停留，并持久化为固定组合。删除素材时若被组合引用须拦截提示。

本期仅管理端编排与预览，不导出 GIF/视频，不接入使用端阅读器。

## 2. 已确认决策

| 项 | 决策 |
|----|------|
| 菜单位置 | 素材管理 → 子菜单「组合编排」`/assets/combos` |
| 成员编号 | 组合内从 1 起不重复；选择/排序时可见 |
| 排序 vs 播放 | 排序定成员与 `member_no`；另填播放序列控制播放（可跳号、可重复） |
| 播放序列格式 | 逗号分隔，如 `1,3,5,4,9` |
| 默认间隔 | 可配置，建议默认 1 秒，最小 0.1 秒 |
| 个性化停留 | `step_index` = 播放序列中的第几步（从 1 起），与成员编号无关；如第 7 步停留 2 秒 |
| 循环 | 可配置，默认开启 |
| 删除保护 | 硬删素材时检查组合引用，409 + 组合名提示 |
| 范围 | 管理端列表/编辑/预览；不导出、不接使用端 |

## 3. 数据模型

### 3.1 `asset_combo`

| 字段 | 说明 |
|------|------|
| id | PK |
| name | 组合名称 |
| play_sequence | 播放序列文本，如 `1,3,5,4,9` |
| default_interval_sec | 默认间隔（秒，小数） |
| loop_enabled | 是否循环，默认 true |
| remark | 备注，可空 |
| created_at / updated_at | |

### 3.2 `asset_combo_member`

| 字段 | 说明 |
|------|------|
| id | PK |
| combo_id | FK → asset_combo |
| asset_id | FK → asset |
| member_no | 组合内编号 1..n，同一 combo 内唯一 |
| sort_order | 编辑时排序（可与 member_no 一致或用于拖拽后重编号） |

约束：同一 `combo_id` 下 `asset_id` 不重复；`member_no` 唯一。

### 3.3 `asset_combo_step_hold`

| 字段 | 说明 |
|------|------|
| id | PK |
| combo_id | FK → asset_combo |
| step_index | 播放序列步序号，从 1 起 |
| hold_seconds | 该步停留秒数 |

约束：同一 combo 下 `step_index` 唯一。

## 4. 播放规则

1. 解析 `play_sequence` → 整数列表 `steps[]`，每项必须 ∈ 当前成员的 `member_no`。
2. 预览从步 `i=0` 开始：显示 `member_no = steps[i]` 对应素材。
3. 停留时间：若存在 `step_hold` 且 `step_index = i+1`，用 `hold_seconds`；否则用 `default_interval_sec`。
4. 步进：`i++`；若 `i >= steps.length`：循环开则 `i=0`，否则暂停并停在最后一帧。
5. 播放器支持播放/暂停；暂停清除定时器。

## 5. 页面

### 5.1 组合列表 `/assets/combos`

- 列：名称、成员数、默认间隔、是否循环、更新时间  
- 操作：新建、编辑、删除、预览  

### 5.2 组合编辑

1. 基本信息：名称、备注、默认间隔、是否循环  
2. 成员区：从素材库多选；拖拽排序；显示编号 1..n（保存时按序写 `member_no`）  
3. 播放序列：文本输入，校验逗号分隔正整数且均在成员编号内  
4. 个性化停留：表格行（步序号、秒数），可增删行；校验步序号 ∈ `[1, 序列长度]`  
5. 预览框：按规则切换；显示当前步 `k / 总步数` 与当前停留秒数  

### 5.3 侧栏

```
素材管理
  工作台
  管理配置
  组合编排
```

## 6. API（`/api`）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/combos` | 列表 |
| POST | `/combos` | 新建（含 members / sequence / holds） |
| GET | `/combos/{id}` | 详情（含水合素材摘要，便于预览） |
| PUT | `/combos/{id}` | 全量更新成员、序列、间隔、循环、holds |
| DELETE | `/combos/{id}` | 删组合（不删素材文件） |

请求体要点：

```json
{
  "name": "表情循环A",
  "playSequence": "1,3,5,4,9",
  "defaultIntervalSec": 1.0,
  "loopEnabled": true,
  "remark": null,
  "members": [
    { "assetId": 11, "memberNo": 1 },
    { "assetId": 22, "memberNo": 2 }
  ],
  "stepHolds": [
    { "stepIndex": 7, "holdSeconds": 2.0 }
  ]
}
```

**素材硬删**：在现有引用检查中增加 `asset_combo_member`；有引用则 409，消息含组合名称列表。

## 7. 校验规则

- 名称非空  
- `default_interval_sec`、`hold_seconds` ≥ 0.1  
- 成员至少 1 个；`member_no` 为 1..n 连续（保存时可由服务端按提交顺序重排生成）  
- `play_sequence` 非空；每项为正整数且属于成员编号集合  
- `step_index` ∈ `[1, steps.length]`  

## 8. 技术选型

- Flyway：`V3__asset_combo.sql`  
- 后端：Spring Data JPA + 现有 `ApiExceptionHandler`  
- 前端：Vue3 + Element Plus；预览用 `setTimeout` 链（或递归 schedule）按当前步间隔切换  

## 9. 验收标准

1. 可选多张素材、排序后显示 1..n，保存组合成功  
2. 播放序列与默认间隔生效；个性化「第 k 步停留 x 秒」生效  
3. 循环开/关行为正确；预览可播可停  
4. 硬删被组合引用的素材 → 409 并提示组合名  
5. 刷新后组合配置仍在  

## 10. 非目标

- 导出 GIF / 视频 / 音频  
- 使用端阅读器播放  
- 批量从工作台一键生成组合（可后续加）  

## 11. 实现顺序建议

1. Flyway 表 + 实体/仓库  
2. Combo CRUD API + 素材硬删引用检查  
3. 侧栏 + 列表页  
4. 编辑页（成员/序列/间隔/holds）  
5. 预览播放器  
6. 联调验收  

## 12. 开放问题（不阻塞）

- 成员从素材库筛选范围：默认全部 NORMAL；可按分类筛选（建议首期支持分类筛选）  
- 预览是否显示成员编号角标：建议显示  
