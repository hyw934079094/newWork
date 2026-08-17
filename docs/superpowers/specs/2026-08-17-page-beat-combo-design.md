# 设计：篇章画面组接入组合（一框切帧）

> 状态：已确认待实现  
> 日期：2026-08-17  
> 范围：`story-admin-web` / `story-admin-server`  
> 相关：`2026-08-13-asset-combo-design.md`、`2026-08-17-page-drag-cover-order-design.md`

---

## 1. 目标

页面画面组（BEAT）的主画面位除静图封面外，可引用**已配置组合**；管理端预览在**同一画面框**内按组合配置自动切帧；AI 阅读流按播放序列展开为多张连续 `IMAGE`。

## 2. 已确认决策

| 项 | 选择 |
|----|------|
| 主画面位 | 静图 `COVER` **或** 组合 `COMBO`，**二选一** |
| 预览播放 | 自动按组合间隔/步进播放，可暂停/重播（复用 `ComboPreviewPlayer`） |
| AI 阅读流 | 按 `playSequence` **展开为多条** `IMAGE`（方案 B） |
| 组合来源 | 只选已有组合，不在篇章内新建组合 |

## 3. 数据模型

### 3.1 BEAT 子节点

在现有 `COVER | BODY | DIALOGUE` 上增加 `COMBO`：

```json
{
  "type": "BEAT",
  "coverAssetId": 101,
  "children": [
    { "type": "BODY", "text": "先文" },
    { "type": "COMBO", "comboId": 7 },
    { "type": "DIALOGUE", "text": "对白" }
  ]
}
```

或静图：

```json
{
  "type": "BEAT",
  "coverAssetId": 101,
  "children": [
    { "type": "COVER", "assetId": 101 }
  ]
}
```

### 3.2 校验规则（保存前 normalize + validate）

| 规则 | 说明 |
|------|------|
| 视觉节点 XOR | 每个 BEAT 的 `children` 中，`COVER` 与 `COMBO` **合计恰好 1 个**（不能 0、不能 2、不能并存） |
| `COMBO.comboId` | 必填；组合存在；成员 ≥1；成员素材均为 `NORMAL` |
| `COVER.assetId` | 同现规（NORMAL） |
| `coverAssetId` 同步 | **COVER**：写 COVER.assetId；**COMBO**：写组合 `playSequence` 解析后的**首帧**对应 `assetId`（无序列则成员 1） |
| 正文子节点 | `BODY` / `DIALOGUE` 不变；可相对视觉节点上下拖 |

### 3.3 `page_asset_ref`

| ref_kind | 用途 |
|----------|------|
| `BEAT_COVER` | 继续写入 `coverAssetId`（静图封面或组合首帧），兼容旧逻辑 |
| `BEAT_COMBO_MEMBER`（新增） | 当视觉位为 COMBO 时，为组合成员各写一条，避免硬删仍被篇章引用的素材 |

硬删素材 / 删组合前：检查上述引用；删组合若仍被页引用则拒绝或提示（与现 combo 删除策略对齐：优先 **拒绝删除被引用组合**）。

## 4. 后端

### 4.1 `PageService`

- `BEAT_CHILD_TYPES` 含 `COMBO`。
- normalize：若仅有旧 COVER、无 COMBO，行为不变；若已有 COMBO 则不得再注入 COVER。
- validate：XOR 视觉节点；解析并校验 `comboId`；同步 `coverAssetId`。
- `rebuildRefs`：写 `BEAT_COVER` +（若 COMBO）成员的 `BEAT_COMBO_MEMBER`。

### 4.2 `ArcService` 阅读流

遇到 `COMBO` 子节点时：

1. 加载组合详情（members + playSequence + 默认间隔仅作元数据，流里可不发 hold）。
2. 按 `playSequence` 顺序，对每一步解析出 `assetId`，依次 `appendBeatImage(..., role: "BEAT_COMBO_FRAME")`（或沿用 `BEAT_COVER` 并加 `comboId`/`stepIndex` 字段二选一；**推荐** `role: "BEAT_COMBO_FRAME"` + 可选 `comboId`、`stepIndex`）。
3. 不在同一位置再输出静图 COVER。

`BODY` / `DIALOGUE` 仍按 children 数组顺序与 COMBO 展开块穿插：即 COMBO 展开的多张 IMAGE 作为**连续块**插在该子节点位置。

### 4.3 组合删除

`ComboService.delete`：若存在 `page_asset_ref` 指向该组合成员且来自含该 `comboId` 的页，或增加 `page_combo_ref` 表——**本期采用**：扫描 `story_page.content_json` 成本高，优先用 **`page_asset_ref.ref_kind=BEAT_COMBO_MEMBER` 不够区分 comboId**。

更干净做法（本期落地）：

- 新增表或扩展 ref：`page_asset_ref` 增加可选 `combo_id` 列，**或** 新表 `page_combo_ref(page_id, combo_id)`。
- **推荐**：新表 `page_combo_ref(page_id, combo_id)` UNIQUE，保存页时按 content 重建；删组合前 `existsByComboId` → 400。

Flyway 新迁移：`V*_page_combo_ref.sql`。

## 5. 前端

### 5.1 `PageEditor`

- 画面组视觉行：状态为 COVER 或 COMBO。
- 操作：「选择封面」「选择组合」互斥；选组合打开组合列表 picker（`listCombos`）；清除后须再选其一才能保存。
- 拖拽：COMBO 节点与 BODY/DIALOGUE 同一列表拖（同 COVER）。
- 不可删除视觉节点本身，只能切换类型或清除后重选。

### 5.2 `PagePreview` / `ArcPreview`

- COVER → 静图（现逻辑）。
- COMBO → 同一 `.figure` 框内挂载 `views/combos/ComboPreviewPlayer.vue`（或抽共享播放器），**默认自动播放**；需加载 `GET /combos/{id}` 详情。
- 解析 children 时支持 `COMBO`；legacy 无 COVER/COMBO 仍用 `coverAssetId` 静图。

### 5.3 非目标

- 篇章内新建/编辑组合内容。
- 阅读流发送 hold 秒数（可后续加）。
- 左右分栏、多视觉槽并存。

## 6. 验收

1. 画面组可选已有组合；预览单框自动切帧，可暂停。
2. 切回静图封面后预览为单图，保存后 JSON 无 COMBO。
3. 阅读流：COMBO 位置展开为多张 IMAGE，顺序与 playSequence 一致；前后正文顺序正确。
4. 被页引用的组合不可删（或明确报错）。
5. 旧仅 COVER 页打开/保存行为不变。

## 7. 风险

| 风险 | 缓解 |
|------|------|
| 组合成员变更后页未重开 | 预览/流按 comboId 实时取最新组合 |
| coverAssetId 仅首帧 | 硬删用 BEAT_COMBO_MEMBER + page_combo_ref |
| 播放器依赖登录拉图 | 与现 ComboEditor 一致 |
