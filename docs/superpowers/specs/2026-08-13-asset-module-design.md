# 管理端素材模块（标准首期）设计说明

> 日期：2026-08-13  
> 状态：已实现（首期）；实现计划见 `docs/superpowers/plans/2026-08-13-asset-module.md`  
> 关联：`docs/PROJECT_REQUIREMENTS.md`（需求基线 v0.2）  
> 实现路线：A（前后端一体推进）

## 1. 背景与目标

建设「插图 + 文字故事」管理端的**素材主体能力**，并附带最小人物库、AI 参考区（不接真模型）、回收站与系统配置骨架。

管理端定位：带设定约束能力的故事创作与资产管理平台，而非简单发文后台。

## 2. 已确认决策

| 项 | 决策 |
|----|------|
| 系列归属 | 素材可不挂系列；后续再挂；允许一直不归属 |
| 范围 | 标准首期（按需求文档）：上传/预览/排序/跨分类拖拽/标签/关联/AI 参考区；不接真 AI |
| 人物 | 最小人物库 CRUD；篇章关联仅占位 |
| 人物字段 | 编号、姓名、别名、性别、年龄/阶段、种族、身份/职业、公开简介、内部说明（设定项下一期） |
| 分类 | 预置 5 类，允许新增；预置类不可删，可改显示名与排序 |
| 删除 | 进回收站可恢复；回收站再删才彻底删除；有引用则禁止硬删并提示 |
| 上传 | jpg/jpeg/png/webp/gif，单文件 ≤20MB，支持多选批量上传 |
| 批量操作 | 本期不做（批量移动/打标/关联） |
| 数据库 | MySQL 本机，库名 `story_admin`，账号 `root`（密码仅本地配置） |
| 文件存储 | 项目路径下 `storage/`，库内存相对路径 |
| 配置模块 | 首期先做能力（`sys_config` + CRUD + 默认回退）；配置项后续再补 |
| UI 参考 | `docs/prototypes/asset-management.html` |

## 3. 总体结构

```text
newWork/
  storage/                      # gitignore
    assets/{yyyy}/{MM}/{uuid}.ext
  story-admin-server/           # Spring Boot 3.3 + JPA + MySQL
  story-admin-web/              # Vue3 + Vite + TS（建议 Element Plus）
  docs/
```

- 启动默认：`application.yml`（含库连接、默认 `storage.root` 等）
- 运行时覆盖：`sys_config` 有值则用配置，否则用默认
- 库连接本身以 yml 引导启动；配置表不承担「无库启动」职责

## 4. 数据模型

### 4.1 `asset_category`

| 字段 | 说明 |
|------|------|
| id | PK |
| code | 稳定编码（预置：expression / portrait / costume / mixed / complete） |
| name | 显示名 |
| sort_order | 分类排序 |
| system_preset | 是否系统预置（预置不可删） |
| created_at / updated_at | |

预置：人物表情、人物立绘、人物服装、综合素材、完整图片。

### 4.2 `asset`

| 字段 | 说明 |
|------|------|
| id | PK |
| display_name | 显示名 |
| category_id | 当前分类 |
| series_id | 可空（本期不强制） |
| sort_order | 同分类内排序 |
| status | `NORMAL` / `DELETED` |
| description | 说明 |
| original_filename | 原始文件名 |
| storage_path | 相对路径 |
| content_type / width / height / size_bytes / checksum | 文件元数据 |
| chapter_ref_placeholder | 篇章占位（可空文本，无实体） |
| created_at / updated_at / deleted_at | |

### 4.3 标签

- `asset_tag`：id, name（唯一）
- `asset_tag_rel`：asset_id, tag_id

### 4.4 `character`

| 字段 | 说明 |
|------|------|
| id | PK |
| code | 人物编号（系列可空时全局唯一或全局流水） |
| name / alias | 姓名、别名 |
| gender / age_stage / race / occupation | |
| public_intro / internal_note | 公开简介、内部说明 |
| created_at / updated_at | |

### 4.5 `asset_character_rel`

asset_id + character_id（多对多）

### 4.6 AI 参考区

- `ai_reference_session`：id, name/备注, created_at, updated_at
- `ai_reference_item`：session_id, asset_id, sort_order, purpose（用途）, note, strength（可空）

不调用外部模型；仅持久化「本次参考选择」。

### 4.7 `sys_config`

| 字段 | 说明 |
|------|------|
| id | PK |
| config_key | 唯一 |
| config_value | 文本 |
| remark | 备注 |
| updated_at | |

读取：`ConfigService.get(key, defaultValue)`。  
首期可预置示例 key（如 `storage.root`），也可为空表，仅提供 CRUD。

### 4.8 引用与硬删

硬删前检查：

- `asset_character_rel` 是否仍引用
- `ai_reference_item` 是否仍引用

有引用 → 拒绝并返回引用摘要；无引用 → 删库记录 + 删物理文件。

## 5. 页面

1. **素材管理**：左分类；中主预览+序号/上下份+缩略图+可拖拽网格；右属性编辑；顶上传/搜索/AI 参考/回收站  
2. **人物管理**：基础字段 CRUD  
3. **AI 参考区**：选素材、顺序、用途  
4. **回收站**：恢复 / 彻底删除  
5. **系统配置**：配置表 CRUD  

## 6. API（`/api`）

- 分类：list / create / update（含排序、改名）/ delete（禁删预置）
- 素材：upload / get / update / listByCategory / reorder / moveCategory / softDelete / restore / hardDelete / file preview
- 标签：随素材更新一并提交或独立读写
- 人物：CRUD；素材关联读写
- AI 参考：session CRUD；items 全量替换或单项调整
- 配置：list / upsert / delete

错误约定：校验失败 400；引用冲突 409；未找到 404。

## 7. 行为细则

- **排序作用域**：同一 `category_id` 内；拖拽成功立即乐观更新 UI，接口失败则回滚并提示  
- **跨分类移动**：只改 `category_id` + 目标分类 `sort_order`，不复制文件  
- **预览序号**：按当前筛选结果从 1 开始；非法序号不切换并提示  
- **筛选导致当前项离开结果集**：选中结果集第一项；空则空状态  
- **上传**：校验后缀与大小；落盘后再写库；失败尽量清理半成品文件  

## 8. 技术选型

- Backend：Spring Boot 3.3、Spring Data JPA、MySQL 8、Validation  
- Frontend：Vue 3、Vue Router、Vite、TypeScript、Element Plus、Sortable/VueDraggable  
- 本地存储根默认：`{projectRoot}/storage`，可被 `sys_config.storage.root` 覆盖  

## 9. 验收标准

1. 多图上传成功，主预览/缩略图/序号/上下切换正常  
2. 分类内拖拽排序、跨分类拖拽，刷新后一致；失败回滚提示  
3. 重命名、说明、标签、关联人物可用  
4. 删除→回收站→恢复；回收站硬删；有引用拦截提示  
5. AI 参考区顺序与用途可保存（无模型调用）  
6. 人物基础字段 CRUD  
7. 系统配置 CRUD；无配置时走 yml/代码默认  

## 10. 非目标（本期不做）

- 批量移动 / 批量打标 / 批量关联  
- 真 AI 生成与模型接入  
- 登录与权限  
- 篇章真实实体与关联  
- 对象存储 / 生产部署方案  
- 人物可扩展设定项（帽子/眼罩等多素材设定）  

## 11. 实现顺序建议

1. 建库 `story_admin`、表结构、预置分类、gitignore `storage/`  
2. ConfigService + 配置 API/页  
3. 人物 CRUD  
4. 素材上传/列表/预览/元数据  
5. 拖拽排序与跨分类  
6. 标签与人物关联  
7. 回收站  
8. AI 参考区  
9. 联调验收对照第 9 节  

## 12. 开放问题（不阻塞开工）

- 人物编号在「无系列」时的生成规则（建议：全局自增或 `C`+日期流水）  
- Element Plus 是否采用（建议采用，加速表格/上传）  
- AI 参考「会话」首期是单例工作台还是多会话列表（建议：先做单例当前会话，表结构保留 multi-session）  
