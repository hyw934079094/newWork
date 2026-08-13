# 素材工作台筛选与替换设计说明

> 状态：已确认（待实现）  
> 日期：2026-08-13  
> 分支策略：在 `master` 上实现

## 1. 背景与目标

素材工作台当前按分类列出全部 NORMAL 素材，仅支持关键字搜索。整理素材时需要：

1. **按人物筛选**：默认只看「未关联人物」的素材；也可看某个人物或全部。  
2. **关键字筛选**：与人物条件同时生效。  
3. **替换图片**：在单条素材上重新上传一张图，覆盖当前文件，保持同一素材 id 与业务关联。

## 2. 已确认决策

| 项 | 决策 |
|----|------|
| 人物筛选项 | `无关联`（默认）/ 具体人物 / `全部` |
| 关键字与人物 | AND；仍限制在当前选中分类内 |
| 默认展示 | `characterFilter=unlinked` |
| 替换范围 | 单张；覆盖原文件内容，不新建素材记录 |
| 替换后显示名 | 默认不改（仅更新文件侧元数据） |
| 历史版本 | 本期不做（直接覆盖，不进回收站备份） |
| 回收站批量 | 本期不做（另议） |

## 3. 列表筛选

### 3.1 API

`GET /api/assets` 在现有 `categoryId`、`status`、`q` 基础上增加：

| 参数 | 说明 |
|------|------|
| `characterFilter` | `unlinked` \| `all`；缺省由调用方决定。工作台默认传 `unlinked` |
| `characterId` | 可选 Long；与 `characterFilter` 互斥使用场景：选具体人物时传 `characterId`，不传 `unlinked`/`all` 语义，或约定 `characterFilter` 省略且 `characterId` 有值即按人物筛 |

**推荐约定（实现以代码为准）：**

- `characterId` 有值 → 仅返回关联该人物的素材  
- 否则看 `characterFilter`：`unlinked` / `all`（默认工作台传 `unlinked`）  
- `q`：非空时匹配显示名等（沿用现有 search）  
- 条件之间 AND；`status` 工作台仍为 `NORMAL`

### 3.2 查询实现要点

- `unlinked`：不存在 `asset_character_rel` 行的素材  
- 具体人物：`asset_character_rel.character_id = ?`  
- `all`：不加人物条件  

### 3.3 工作台 UI

- 人物下拉：`无关联`（默认）| 人物列表项 | `全部`  
- 关键字输入框（现有或强化位置）  
- 变更分类 / 人物 / 关键字 → 重新 `listAssets`  
- 筛选激活时：拖拽排序继续禁用（与现「搜索中不可排序」一致；`unlinked`/`all`/人物任一非「分类内默认全量」时可同样禁用，或仅 `q` 非空时禁用——**建议：`q` 非空或人物筛选不是「全部」时禁用排序**，避免半集排序写回破坏全部分类顺序）

**排序禁用细化（推荐）：**

- `characterFilter=all` 且 `q` 为空：允许排序（与改前一致）  
- 否则禁用排序/跨分类拖拽写回  

## 4. 替换图片

### 4.1 API

`POST /api/assets/{id}/content`  
- `Content-Type: multipart/form-data`  
- 字段：`file`（单文件）  
- 成功：`200` + 更新后的 Asset JSON  

### 4.2 服务端行为

1. 素材必须存在且 `status=NORMAL`，否则 404/400  
2. 文件类型、大小校验与 `upload` 相同  
3. **覆盖存储**：写入当前 `storage_path` 对应文件（或写临时文件再原子替换）；更新 `contentType`、宽高、`sizeBytes`、`checksum`、`originalFilename`  
4. **不修改**：`id`、`categoryId`、`sortOrder`、`displayName`、`description`、标签、人物关联、组合引用等  
5. 预览 URL 仍为 `/api/assets/{id}/content`（可加 cache-bust 查询参数由前端处理）

### 4.3 工作台 UI

- 右侧详情在「移入回收站」旁增加 **「替换图片」**  
- 触发隐藏 `input[type=file]`，选文件后调用替换 API  
- 成功：刷新当前素材预览与元数据；提示成功  
- 失败：展示后端 message  

### 4.4 Git

`storage/` 已纳入 Git；替换后工作区文件变更，由开发者/流程提交（本期不自动 commit）。

## 5. 验收标准

1. 进入工作台（选定分类）默认仅展示无人物关联的 NORMAL 素材  
2. 选「全部」可见该分类下全部 NORMAL；选某人物仅见其关联素材  
3. 关键字与人物条件同时生效  
4. 替换图片后同一 id 预览更新，分类/人物/标签不变；旧文件内容被覆盖  
5. 非 NORMAL（如已在回收站）不可替换  

## 6. 非目标

- 按人物本体筛选  
- 多选人物、跨分类「无关联」总览  
- 替换历史版本 / 替换前自动进回收站  
- 批量替换、批量移入回收站（另议）  

## 7. 实现顺序建议

1. 后端 list 人物筛选 + 测试  
2. 后端 replace content + 测试  
3. 工作台 UI：人物下拉 + 默认 unlinked + 关键字联动  
4. 工作台 UI：替换图片按钮与预览刷新  
5. 联调与文档回写  

## 8. 开放问题（不阻塞）

- 替换后是否用新文件名更新 `displayName`（当前：**否**）  
- 前端预览是否强制 `?t=timestamp` 破缓存（建议：**是**）  
