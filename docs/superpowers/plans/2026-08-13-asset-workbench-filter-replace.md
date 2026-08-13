# 素材工作台筛选与替换 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 工作台按人物（默认无关联）+ 关键字筛选素材，并支持单张「替换图片」覆盖原文件且保持素材 id。

**Architecture:** 扩展 `AssetRepository.search` / `AssetService.list` 增加人物过滤；新增 `POST /api/assets/{id}/content` 覆盖写入 `storage_path`；`AssetWorkbench.vue` 增加人物下拉与替换按钮，预览带 cache-bust。

**Tech Stack:** Java 17+、Spring Boot 3.3、JPA、Vue 3、Element Plus

**Spec:** `docs/superpowers/specs/2026-08-13-asset-workbench-filter-replace-design.md`

## Global Constraints

- 在 `master` 上直接提交
- 人物筛：`characterId` 有值 → 该人物；否则 `characterFilter=unlinked|all`；工作台默认 `unlinked`
- 与 `categoryId`、`status`、`q` AND
- 排序：仅当 `characterFilter=all`（或未筛人物）且 `q` 为空时允许；否则禁用
- 替换：仅 NORMAL；覆盖文件；不改 displayName / 分类 / 关联
- 预览建议 `?t=timestamp` 破缓存
- Git：`D:\tool\Git\bin\git.exe` + `-F` UTF-8 无 BOM；`storage/` 变更由调用方后续提交（替换测可用临时文件）
- JDK：`JAVA_HOME=D:\jdk\jdk-24.0.1`；Node：`D:\tool\nvm\v22.17.0`
- TSD：改 `.java`/`.sql` 后验明文；必要时 `.txt` + `cmd ren`

---

## File Map

| Path | Responsibility |
|------|----------------|
| `AssetRepository.java` | search 增加人物条件 |
| `AssetService.java` | `list(...)` 签名扩展；`replaceContent(id, file)` |
| `AssetController.java` | list 新参数；`POST /{id}/content` |
| `StorageService.java` | 可选 `overwrite(relativePath, bytes)` 或复用写路径 |
| `AssetListFilterTest.java` / `AssetReplaceContentTest.java` | 测试 |
| `api/asset.ts` | list 参数；`replaceAssetContent`；`assetContentUrl(id, bust?)` |
| `AssetWorkbench.vue` | 人物下拉、默认 unlinked、替换 UI |

---

### Task 1: 列表人物筛选（后端）

**Files:**
- Modify: `AssetRepository.java` — 扩展 `search` 或新增重载
- Modify: `AssetService.list`、`AssetController.list`
- Create: `src/test/java/.../AssetListFilterTest.java`

**Interfaces:**
- Produces: `list(categoryId, status, q, characterFilter, characterId)`
- 规则：`characterId != null` → 关联该人物；else `unlinked` → 无 rel；`all` 或不识别时按 all（Controller 工作台会显式传）

- [ ] **Step 1: 失败测试 — unlinked 不含已关联素材**

```java
@Test
void listUnlinkedExcludesCharacterLinkedAssets() {
  // 两素材同分类：A 无人物，B 挂人物 → filter unlinked 仅 A
}
```

- [ ] **Step 2: 测试 — characterId 仅返回关联**

```java
@Test
void listByCharacterIdReturnsOnlyLinked() { ... }
```

- [ ] **Step 3: 实现 JPQL/服务层 + Controller 参数；测试 PASS**

示例 JPQL 条件（示意）：

```text
unlinked: not exists (select 1 from AssetCharacterRel r where r.assetId = a.id)
by character: exists (... r.characterId = :characterId)
```

- [ ] **Step 4: Commit**

```bash
git commit -m "feat: filter assets by character unlinked/all/id"
```

---

### Task 2: 替换图片 API（后端）

**Files:**
- Modify: `StorageService.java` — `overwrite(String relativePath, byte[] bytes)` 或等价
- Modify: `AssetService.java` — `replaceContent(Long id, MultipartFile file)`
- Modify: `AssetController.java` — `POST /{id}/content` multipart（注意与 `GET /{id}/content` 同路径不同方法）
- Create: `AssetReplaceContentTest.java`

**Interfaces:**
- Produces: `Asset replaceContent(Long id, MultipartFile file)`
- 校验 NORMAL；类型/大小同 upload；更新元数据；**不改** displayName

- [ ] **Step 1: 失败测试 — 回收站素材不可替换**

```java
@Test
void replaceContentRejectsNonNormal() { ... }
```

- [ ] **Step 2: 成功测试 — 同 path 内容与 checksum 变、displayName 不变**

```java
@Test
void replaceContentOverwritesFileKeepsDisplayName() { ... }
```

- [ ] **Step 3: 实现 + 测试 PASS**

- [ ] **Step 4: Commit**

```bash
git commit -m "feat: replace asset image content in place"
```

---

### Task 3: 工作台筛选 UI

**Files:**
- Modify: `story-admin-web/src/api/asset.ts`
- Modify: `story-admin-web/src/views/assets/AssetWorkbench.vue`

**Interfaces:**
- Consumes: list 新参数
- Produces: 人物下拉默认「无关联」；关键字联动；排序禁用规则

- [ ] **Step 1: `listAssets` 增加 `characterFilter?`、`characterId?`**

- [ ] **Step 2: UI 下拉 + `loadAssets` 传参；默认 unlinked**

- [ ] **Step 3: `isSearchActive` / 排序禁用：`q` 非空或人物不是「全部」时禁用**

- [ ] **Step 4: `npm run build` PASS + Commit**

```bash
git commit -m "feat: workbench character filter defaulting to unlinked"
```

---

### Task 4: 工作台替换 UI

**Files:**
- Modify: `api/asset.ts` — `replaceAssetContent`；`assetContentUrl(id, bust?)`
- Modify: `AssetWorkbench.vue` — 替换按钮、file input、成功刷新预览

**Interfaces:**
- Consumes: `POST /assets/{id}/content`
- Produces: 详情区「替换图片」；预览 `?t=` bust

- [ ] **Step 1: API 客户端**

- [ ] **Step 2: 按钮 + 上传流程 + ElMessage**

- [ ] **Step 3: build PASS + Commit**

```bash
git commit -m "feat: replace asset image from workbench detail"
```

---

### Task 5: 验收与文档

**Files:**
- Modify: design 状态 → 已实现（首期）
- Modify: `README.md` 一句说明筛选默认与替换
- Append: 本计划验收表

- [ ] **Step 1: API/单测验收设计 §5；UI 尽量点验或标 PARTIAL**

- [ ] **Step 2: 文档回写 + Commit**

```bash
git commit -m "docs: record workbench filter and replace acceptance"
```

---

## Spec Coverage

| Spec | Task |
|------|------|
| unlinked/all/characterId + q | Task 1, 3 |
| 排序禁用规则 | Task 3 |
| replace content | Task 2, 4 |
| 验收文档 | Task 5 |

## Notes

- Controller 上 `POST /{id}/content` 与 `GET /{id}/content` 共存合法  
- 替换测勿依赖已 git 跟踪的生产图；用测试临时目录或 H2+mock storage  
- 若改 `search` 签名，更新所有调用点（含回收站若共用）  
