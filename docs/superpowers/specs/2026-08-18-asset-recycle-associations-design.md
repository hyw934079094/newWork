# 设计：素材回收断链 / 恢复挂回 / 硬删级联

> 状态：已确认待实现  
> 日期：2026-08-18  
> 范围：`story-admin-server`（主）；`story-admin-web` 仅空封面/空组合占位展示  
> 相关：`2026-08-13-asset-module-design.md`、`2026-08-13-asset-combo-design.md`、`2026-08-17-page-beat-combo-design.md`、`2026-08-18-recycle-bulk-actions-design.md`

---

## 1. 目标

素材进入回收站后，业务侧**立即看不到占用**；从回收站恢复后，在目标仍存在的前提下**尽量还原关联**；彻底删除时**级联清关联与快照**，不再因引用返回 409。

## 2. 已确认决策

| 项 | 选择 |
|----|------|
| 占用策略 | **强制断链**（不拦截、不半挂起） |
| 恢复 | 靠**快照**挂回；目标实体已删则跳过该条 |
| 页面 BEAT 静图封面 | **清空 `assetId` / `coverAssetId`，保留 COVER 节点** |
| 硬删 | 级联删除关联 + 快照 + 文件；**取消「仍有引用 → 409」** |
| 标签 / 未归类排序 | **不断开**（仍挂在素材上，随素材进回收站） |

## 3. 关联种类

回收时须拆除并快照的种类：

| kind | 来源 | 回收动作 |
|------|------|----------|
| `CHARACTER_REL` | `character_asset_rel` | 删行 |
| `SERIES_REL` | `asset_series_rel` | 删行 |
| `ARC_REL` | `asset_arc_rel` | 删行 |
| `IDENTITY_REL` | `identity_asset_rel` | 删行 |
| `AI_REF` | `ai_reference_item`（按 `asset_id`） | 删行（payload 含重建所需字段） |
| `SERIES_COVER` | `story_series.cover_asset_id` | 置 `NULL` |
| `ARC_COVER` | `story_arc.cover_asset_id` | 置 `NULL` |
| `COMBO_MEMBER` | `asset_combo_member` + 该组合 `play_sequence` | 移出成员；重写 `play_sequence`（去掉该 `memberNo`，**不重编号**其余成员） |
| `PAGE_BEAT_COVER` | `story_page.content_json` 中 COVER + `coverAssetId`；`page_asset_ref` | 清空该 BEAT 的 COVER/`coverAssetId`；删对应 `page_asset_ref` |
| `PAGE_COMBO_MEMBER_REF` | `page_asset_ref` 中 `BEAT_COMBO_MEMBER` | 删该素材的 ref；若某页 `coverAssetId` 等于本素材（组合首帧），改为组合新首帧，若组合已无成员则置 `null` |

说明：

- 页面视觉位为 `COMBO` 时，**不删 COMBO 节点、不删 `page_combo_ref`**；只因组合成员变化间接失效帧。
- `page_combo_ref` 本身不因「成员素材回收」删除。
- 同一素材若既是某页 COVER 又是组合成员，两类快照都写。

## 4. 数据模型

### 4.1 表 `asset_association_snapshot`

Flyway：`V12__asset_association_snapshot.sql`。

| 列 | 类型 | 说明 |
|----|------|------|
| `id` | BIGINT PK AI | |
| `asset_id` | BIGINT NOT NULL | 被回收素材 |
| `kind` | VARCHAR(64) NOT NULL | 上表 kind |
| `payload_json` | JSON/TEXT NOT NULL | 挂回所需最小字段 |
| `created_at` | DATETIME NOT NULL | |

索引：`(asset_id)`。

**同一素材再次回收**：先 `DELETE` 该 `asset_id` 全部快照，再写入本轮快照（以最新一次为准）。

### 4.2 `payload_json` 约定

| kind | payload 示例 |
|------|----------------|
| `CHARACTER_REL` | `{"characterId":1}` |
| `SERIES_REL` | `{"seriesId":1}` |
| `ARC_REL` | `{"arcId":1}` |
| `IDENTITY_REL` | `{"identityId":1}` |
| `AI_REF` | `{"sessionId":1,"sortOrder":0,"purpose":"外貌","note":null,"strength":null}`；session 仍在则新建 item，否则跳过 |
| `SERIES_COVER` | `{"seriesId":1}` |
| `ARC_COVER` | `{"arcId":1}` |
| `COMBO_MEMBER` | `{"comboId":7,"memberNo":2,"playSequenceBefore":"1,2,3"}`（`playSequenceBefore` 为拆除前整串，恢复时写回该串并插入成员） |
| `PAGE_BEAT_COVER` | `{"pageId":9,"beatIndex":0,"childIndex":1}`（索引为 content 数组中目标 BEAT / COVER 子节点下标；回收前校验仍指向本 `assetId`） |
| `PAGE_COMBO_MEMBER_REF` | `{"pageId":9,"comboId":7,"refKind":"BEAT_COMBO_MEMBER","coverAssetIdBefore":101}`（`comboId` 供恢复定位页内 COMBO；`coverAssetIdBefore` 可选，仅当回收时改写了页 `coverAssetId`） |

## 5. 算法

### 5.1 `AssetService.recycle(id)`

1. 加载素材；若已非 `NORMAL` 则按现规处理（保持现有错误语义）。
2. **采集并拆除** §3 全部关联；边拆除边写入内存快照列表。
3. 清空该 `asset_id` 旧快照行，批量插入本轮快照。
4. `status=DELETED`，`deleted_at=now`，保存。
5. 全程同一事务。

组合细节：

- 拆除成员后，`play_sequence` = 原序列中仍存在的 `memberNo`，逗号拼接；若无剩余步骤则置空串。
- 若剩余成员数为 0：允许组合暂时无成员（见 §6）。
- 对每个引用了该组合的页：删本素材的 `BEAT_COMBO_MEMBER` ref；必要时更新 JSON 内 `coverAssetId`（不经完整 Page 保存校验，直接 patch + 保存实体）。

页面 COVER 细节：

- 扫描 `page_asset_ref`（及必要时 content）定位含本素材的 BEAT COVER。
- COVER 节点保留；`assetId` 移除或 `null`；BEAT `coverAssetId` 同步清空。
- 删除该页对该素材的 `BEAT_COVER`（及若有）相关 ref。

### 5.2 `AssetService.restore(id)`

1. 素材 `status=NORMAL`，`deleted_at=null`。
2. 读取该资产全部快照，按 kind 挂回：
   - 目标实体不存在 → **跳过**该条。
   - `COMBO_MEMBER`：若组合仍在，插入成员；将 `playSequence` 写回 `playSequenceBefore`（若序列中其它 memberNo 已不存在，则过滤后再写，避免非法序列）。
   - `PAGE_BEAT_COVER`：若页仍在且索引处仍为 COVER（可为空），写回 `assetId` / `coverAssetId`，并补 `page_asset_ref`。
   - `PAGE_COMBO_MEMBER_REF`：若页仍引用相关 combo，按当前组合成员重算并补 `BEAT_COMBO_MEMBER` refs；若本素材为当前首帧且 payload 含 `coverAssetIdBefore`，将页 `coverAssetId` 写回本素材 id。
3. 快照**保留至再次回收（覆盖）或硬删**；恢复成功不删快照。
4. 同一事务。

### 5.3 `AssetService.hardDelete(id)`

1. 若 `NORMAL`：先执行与 recycle 相同的断链（或直接级联删关联），无需保留快照。
2. 若已在回收站：删除该资产快照；清任何残留关联（防御性）。
3. 删 tag_rel / unlinked_order 等现有附属行。
4. 删资产行 + 存储文件。
5. **禁止**再抛「仍被引用」类 `ConflictException`。

## 6. 页面校验与展示（空视觉位）

回收后会出现「COVER 无 assetId」或「COMBO 成员为空」。

### 6.1 `PageService`

允许并持久化：

| 状态 | 规则 |
|------|------|
| 空 COVER | 存在 COVER 子节点；`assetId` 可空；`coverAssetId` 可空；仍满足 COVER/COMBO XOR（恰好一个视觉子节点） |
| 空组合 | `COMBO.comboId` 仍必填且组合存在；**允许成员数 = 0**；此时 `coverAssetId` 可空 |
| 非空 | 保持现规：素材须 `NORMAL` |

`rebuildRefs`：无有效 cover/member 则不写对应 ref。

### 6.2 阅读流 `ArcService`

- 空 COVER：该 BEAT **不输出**画面 IMAGE（正文/对白仍按序输出）。
- COMBO 无成员或序列为空：不输出组合帧。

### 6.3 前端

- 页面编辑器：空 COVER / 空组合显示占位（不报死错）；用户可重选素材或组合后保存。
- 回收站 UI、批量恢复/删除：**无接口变更**（仍逐条现有 API）。

## 7. 错误与并发

- 回收/恢复/硬删均 `@Transactional`。
- 恢复时跳过失效目标，不因单条失败回滚整笔（实现时可收集 skip 计数打日志；API 仍返回已恢复素材即可）。
- 不引入新的占用拦截错误文案。

## 8. 测试要点

| 用例 | 期望 |
|------|------|
| 回收仅角色关联 | rel 消失；快照有 `CHARACTER_REL`；恢复后 rel 回来 |
| 回收系列/篇章封面 | cover 变 null；恢复写回 |
| 回收组合成员（仍剩 ≥1） | 成员移除；playSequence 过滤；恢复后成员与原序列恢复 |
| 回收组合成员（最后一人） | 组合 0 成员；引用页 cover 可空；页仍可打开 |
| 回收页 BEAT COVER | COVER 节点在、assetId 空；ref 无；恢复填回 |
| 硬删回收站素材 | 无 409；快照与残留关联清除；文件删除 |
| 硬删仍 NORMAL 且有引用 | 先断链再删，无 409 |
| 恢复时角色已删 | 跳过该 CHARACTER_REL，其它关联仍恢复 |

## 9. 非目标

- 不改回收站批量 UI / 不新增批量断链 API。
- 不做「部分断链」或「占用则禁止回收」。
- 不重编号组合成员 `memberNo`（避免扩大 diff；靠原号 + 原 playSequence 恢复）。
- 不在本期做快照 UI 展示。

## 10. 实现落点（预览）

- Flyway `V12__asset_association_snapshot.sql`
- 实体/仓库 `AssetAssociationSnapshot*`
- `AssetService.recycle` / `restore` / `hardDelete` 重写关联逻辑
- `PageService` 空 COVER / 空组合校验
- `ArcService` 阅读流跳过空视觉
- 前端 PageEditor 空占位（最小改动）
- 服务测试覆盖 §8
