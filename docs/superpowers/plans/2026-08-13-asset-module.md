# 管理端素材模块 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付管理端标准首期素材能力（分类/上传预览/拖拽排序与跨分类/标签/人物关联/回收站/AI 参考区）及系统配置骨架。

**Architecture:** Spring Boot 管理端连本机 MySQL `story_admin`，文件落在仓库旁 `storage/`；业务配置经 `sys_config` 覆盖 yml 默认值。Vue3 管理台按原型做素材工作台，经 Vite 代理调用 `/api`。

**Tech Stack:** Java 17、Spring Boot 3.3、Spring Data JPA、MySQL 8、Vue 3、Vite、TypeScript、Element Plus、vuedraggable/SortableJS

**Spec:** `docs/superpowers/specs/2026-08-13-asset-module-design.md`

## Global Constraints

- 素材 `series_id` 可空；允许不归属系列
- 上传：`jpg/jpeg/png/webp/gif`，单文件 ≤20MB，支持多选
- 删除：`NORMAL`→回收站(`DELETED`)可恢复；回收站硬删；有引用则 409
- 本期不做：批量移动/打标/关联、真 AI、登录权限、篇章实体
- 密钥：`application-local.yml` 或环境变量，勿提交密码
- `storage/` 必须 gitignore
- 人物编号：全局流水（如 `C` + 零填充数字）
- AI 参考：单例当前会话（表保留 multi-session）
- UI：Element Plus

---

## File Map

### Backend (`story-admin-server`)

| Path | Responsibility |
|------|----------------|
| `pom.xml` | 增加 `spring-boot-starter-data-jpa`、`mysql-connector-j`、`lombok`（可选） |
| `src/main/resources/application.yml` | 非密钥默认配置 |
| `src/main/resources/application-local.yml` | 本地 DB 密码（gitignore） |
| `src/main/resources/schema.sql` 或 Flyway `db/migration/V1__init.sql` | 建表 + 预置分类 |
| `.../config/StorageProperties.java` | `story.storage.root` 默认值 |
| `.../config/WebConfig.java` | CORS / 静态资源映射（可选） |
| `.../domain/*` | JPA 实体 |
| `.../repository/*` | Spring Data 仓库 |
| `.../service/ConfigService.java` | get(key, default) + CRUD |
| `.../service/StorageService.java` | 存删文件、解析绝对路径 |
| `.../service/CategoryService.java` | 分类 |
| `.../service/CharacterService.java` | 人物 |
| `.../service/AssetService.java` | 素材全流程 |
| `.../service/AiReferenceService.java` | 参考会话 |
| `.../controller/*Controller.java` | REST |
| `.../web/ApiExceptionHandler.java` | 400/404/409 |
| `src/test/java/...` | 服务与 API 测试 |

### Frontend (`story-admin-web`)

| Path | Responsibility |
|------|----------------|
| `package.json` | element-plus、axios、vuedraggable |
| `src/main.ts` | 注册 Element Plus |
| `src/api/*.ts` | API 客户端 |
| `src/router.ts` | 路由 |
| `src/App.vue` | 侧栏壳 |
| `src/views/assets/AssetWorkbench.vue` | 素材主界面 |
| `src/views/characters/CharacterList.vue` | 人物 |
| `src/views/recycle/RecycleBin.vue` | 回收站 |
| `src/views/ai/AiReference.vue` | AI 参考区 |
| `src/views/config/SysConfig.vue` | 系统配置 |

### Repo root

| Path | Responsibility |
|------|----------------|
| `.gitignore` | `storage/`、`application-local.yml`、`node_modules`、`target` |
| `storage/.gitkeep` | 可选占位（或仅忽略内容） |

---

### Task 1: 基础设施（库、依赖、本地配置、gitignore）

**Files:**
- Create: `story-admin-server/src/main/resources/db/migration/V1__init.sql`
- Create: `story-admin-server/src/main/resources/application-local.yml`
- Modify: `story-admin-server/pom.xml`
- Modify: `story-admin-server/src/main/resources/application.yml`
- Modify: `.gitignore`
- Test: 手工 `mysql` 建库 + 应用能启动到 health

**Interfaces:**
- Produces: 库 `story_admin`；JPA 可连库；`spring.jpa.hibernate.ddl-auto=validate`（以 SQL 迁移为准）

- [ ] **Step 1: 建库**

```bash
mysql -uroot -pEmp@2026 -e "CREATE DATABASE IF NOT EXISTS story_admin DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

Expected: 无报错

- [ ] **Step 2: 写 V1 迁移 SQL**

`story-admin-server/src/main/resources/db/migration/V1__init.sql` 包含表：

`asset_category`, `asset`, `asset_tag`, `asset_tag_rel`, `character_profile`（表名避免 MySQL 保留字 `character`）, `asset_character_rel`, `ai_reference_session`, `ai_reference_item`, `sys_config`

并 INSERT 五条预置分类：

```sql
INSERT INTO asset_category (code, name, sort_order, system_preset, created_at, updated_at) VALUES
('expression', '人物表情', 1, 1, NOW(), NOW()),
('portrait', '人物立绘', 2, 1, NOW(), NOW()),
('costume', '人物服装', 3, 1, NOW(), NOW()),
('mixed', '综合素材', 4, 1, NOW(), NOW()),
('complete', '完整图片', 5, 1, NOW(), NOW());
```

说明：实体类可用 `@Table(name = "character_profile")`，对外 API 仍称「人物」。

- [ ] **Step 3: 更新 pom 与配置**

`pom.xml` 增加：

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
  <groupId>com.mysql</groupId>
  <artifactId>mysql-connector-j</artifactId>
  <scope>runtime</scope>
</dependency>
<dependency>
  <groupId>org.flywaydb</groupId>
  <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
  <groupId>org.flywaydb</groupId>
  <artifactId>flyway-mysql</artifactId>
</dependency>
```

`application.yml`:

```yaml
spring:
  application:
    name: story-admin-server
  profiles:
    active: local
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
    properties:
      hibernate:
        format_sql: true
  flyway:
    enabled: true
    locations: classpath:db/migration
story:
  storage:
    root: ../storage
  upload:
    max-file-size-mb: 20
    allowed-extensions: jpg,jpeg,png,webp,gif
server:
  port: 8081
  servlet:
    multipart:
      max-file-size: 20MB
      max-request-size: 100MB
```

`application-local.yml`（gitignore）：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/story_admin?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: Emp@2026
```

`.gitignore` 增加：

```gitignore
storage/**
!storage/.gitkeep
**/application-local.yml
**/target/
**/node_modules/
```

- [ ] **Step 4: 启动验证**

```bash
cd story-admin-server
mvn -q spring-boot:run
```

另开终端：

```bash
curl http://localhost:8081/api/health
```

Expected: `{"status":"ok","service":"story-admin-server"}`，且 Flyway 成功、五分类已入库

- [ ] **Step 5: Commit**（仅当用户要求提交时执行）

```bash
git add .gitignore story-admin-server/pom.xml story-admin-server/src/main/resources
git commit -m "chore: bootstrap MySQL, Flyway schema, and local config for admin"
```

---

### Task 2: 系统配置 ConfigService + API + 页

**Files:**
- Create: `.../domain/SysConfig.java`
- Create: `.../repository/SysConfigRepository.java`
- Create: `.../service/ConfigService.java`
- Create: `.../controller/ConfigController.java`
- Create: `.../web/ApiExceptionHandler.java`
- Create: `src/test/java/com/story/admin/service/ConfigServiceTest.java`
- Modify: 前端 `package.json`、`main.ts`、`App.vue`、`router`、`views/config/SysConfig.vue`、`api/config.ts`

**Interfaces:**
- Produces: `ConfigService.get(String key, String defaultValue): String`
- Produces: `GET/POST/PUT/DELETE /api/configs`

- [ ] **Step 1: 写失败测试**

```java
@DataJpaTest
class ConfigServiceTest {
  @Autowired SysConfigRepository repo;
  @Test
  void returnsDefaultWhenMissing() {
    ConfigService svc = new ConfigService(repo);
    assertThat(svc.get("storage.root", "../storage")).isEqualTo("../storage");
  }
}
```

- [ ] **Step 2: 实现实体、仓库、ConfigService、Controller**

```java
public String get(String key, String defaultValue) {
  return repo.findByConfigKey(key).map(SysConfig::getConfigValue).orElse(defaultValue);
}
```

API：

- `GET /api/configs` → 列表  
- `PUT /api/configs/{key}` body `{ "value": "...", "remark": "..." }` upsert  
- `DELETE /api/configs/{key}`

- [ ] **Step 3: 跑测试**

```bash
cd story-admin-server && mvn -q -Dtest=ConfigServiceTest test
```

Expected: PASS

- [ ] **Step 4: 前端配置页**

安装：`npm i element-plus axios vue-router`（router 若已有则跳过）

`SysConfig.vue`：表格展示 key/value/remark，支持新增/编辑/删除；调用 `/api/configs`。

侧栏增加「系统配置」。

- [ ] **Step 5: 手工验收**

浏览器打开配置页，新增 `storage.root` = 某路径，刷新仍在；删除后业务回退默认。

- [ ] **Step 6: Commit**（用户要求时）

```bash
git commit -m "feat: add sys_config service, API, and admin page"
```

---

### Task 3: 人物库 CRUD

**Files:**
- Create: `domain/CharacterProfile.java`, `repository/CharacterProfileRepository.java`, `service/CharacterService.java`, `controller/CharacterController.java`
- Create: `src/test/java/.../CharacterServiceTest.java`
- Create: `story-admin-web/src/views/characters/CharacterList.vue`, `api/character.ts`

**Interfaces:**
- Produces: `POST /api/characters`, `GET /api/characters`, `GET /api/characters/{id}`, `PUT /api/characters/{id}`, `DELETE /api/characters/{id}`
- Produces: 自动生成 `code`（`C` + 6 位数字）

- [ ] **Step 1: 失败测试 — 创建人物生成编号**

```java
@Test
void createAssignsCode() {
  CharacterProfile c = characterService.create(new CharacterCreateRequest(
      "女怪盗", null, "女", "青年", "人类", "怪盗", "公开简介", "内部说明"));
  assertThat(c.getCode()).startsWith("C");
  assertThat(c.getName()).isEqualTo("女怪盗");
}
```

- [ ] **Step 2: 实现并通过测试**

字段对齐设计：code, name, alias, gender, ageStage, race, occupation, publicIntro, internalNote。

- [ ] **Step 3: 前端人物列表页**（Element Plus Table + Dialog 表单）

- [ ] **Step 4: Commit**（用户要求时）

```bash
git commit -m "feat: add minimal character CRUD"
```

---

### Task 4: 分类 API + 素材上传/列表/预览/元数据更新

**Files:**
- Create: `domain/AssetCategory.java`, `domain/Asset.java`, `domain/AssetStatus.java`
- Create: `service/StorageService.java`, `service/CategoryService.java`, `service/AssetService.java`
- Create: `controller/CategoryController.java`, `controller/AssetController.java`
- Create: `AssetServiceTest.java`（可用 `@TempDir` + `@SpringBootTest`）
- Create: `views/assets/AssetWorkbench.vue`（先做上传/列表/预览/右侧编辑，拖拽下一任务）

**Interfaces:**
- `StorageService.store(MultipartFile): StoredFile(relativePath, contentType, size, checksum, width, height)`
- `StorageService.resolveAbsolute(relativePath): Path`
- `StorageService.deleteQuietly(relativePath)`
- `GET /api/categories`
- `POST /api/categories`（新增非预置）
- `PUT /api/categories/{id}`
- `DELETE /api/categories/{id}` → 预置返回 400
- `POST /api/assets/upload?categoryId=` multipart files[]
- `GET /api/assets?categoryId=&status=NORMAL&q=`
- `GET /api/assets/{id}`
- `PUT /api/assets/{id}`（displayName, description, chapterRefPlaceholder）
- `GET /api/assets/{id}/content` → 文件流

- [ ] **Step 1: StorageService 用 ConfigService 解析根目录**

```java
Path root = Path.of(configService.get("storage.root", storageProperties.getRoot())).toAbsolutePath().normalize();
```

校验扩展名与大小；路径格式 `assets/yyyy/MM/uuid.ext`。

- [ ] **Step 2: 上传测试**

```java
@Test
void uploadPersistsAssetAndFile(@TempDir Path dir) throws Exception {
  // 配置 storage.root = dir
  // 上传 1x1 png
  // assert DB status NORMAL and Files.exists(absolute)
}
```

- [ ] **Step 3: 实现 Category/Asset API 与前端工作台骨架**

布局对齐设计：左分类、中预览+缩略图、右表单、顶上传。  
预览 URL：`/api/assets/{id}/content`。  
序号切换、上一份/下一份按当前列表索引。

- [ ] **Step 4: 手工验收上传与预览**

- [ ] **Step 5: Commit**（用户要求时）

```bash
git commit -m "feat: asset upload, list, preview, and category APIs"
```

---

### Task 5: 拖拽排序与跨分类移动

**Files:**
- Modify: `AssetService.java`, `AssetController.java`, `AssetWorkbench.vue`
- Create: `AssetReorderTest.java`

**Interfaces:**
- `PUT /api/assets/reorder` body: `{ "categoryId": 1, "orderedIds": [3,1,2] }`
- `PUT /api/assets/{id}/move` body: `{ "targetCategoryId": 2, "targetIndex": 0 }`

- [ ] **Step 1: 失败测试 — reorder 更新 sort_order**

```java
@Test
void reorderUpdatesSortOrder() {
  // 同分类三素材 id 顺序重排
  assetService.reorder(categoryId, List.of(id3, id1, id2));
  assertThat(assetRepository.findAllByCategoryIdAndStatusOrderBySortOrderAsc(categoryId, NORMAL)
      .stream().map(Asset::getId)).containsExactly(id3, id1, id2);
}
```

- [ ] **Step 2: 实现 reorder / move**

跨分类：更新 `categoryId`，插入目标分类 `targetIndex`，重算两侧 sort_order；不复制文件。

- [ ] **Step 3: 前端接入 vuedraggable**

- 分类内拖拽 → 调 reorder  
- 拖到另一分类 → 调 move  
- 失败：恢复拖拽前 UI，`ElMessage.error`

- [ ] **Step 4: 手工验收刷新后顺序保持**

- [ ] **Step 5: Commit**（用户要求时）

```bash
git commit -m "feat: asset drag reorder and cross-category move"
```

---

### Task 6: 标签与人物关联

**Files:**
- Create: `domain/AssetTag.java`, rel entities, repos
- Modify: `AssetService` / `PUT /api/assets/{id}` 支持 `tagNames: string[]`, `characterIds: number[]`
- Modify: `AssetWorkbench.vue` 右侧标签输入 + 人物多选

**Interfaces:**
- Produces: 更新素材时同步 `asset_tag_rel`、`asset_character_rel`（全量替换）

- [ ] **Step 1: 测试 — 更新标签会创建缺失 tag 并替换关联**

```java
@Test
void updateTagsReplacesRelations() {
  assetService.update(id, UpdateAssetRequest.builder().tagNames(List.of("夜", "面具")).build());
  assertThat(assetService.get(id).getTagNames()).containsExactlyInAnyOrder("夜", "面具");
}
```

- [ ] **Step 2: 实现并通过测试；人物关联同理**

- [ ] **Step 3: 前端右侧绑定**

- [ ] **Step 4: Commit**（用户要求时）

```bash
git commit -m "feat: asset tags and character links"
```

---

### Task 7: 回收站（软删/恢复/硬删+引用检查）

**Files:**
- Modify: `AssetService.java`, `AssetController.java`
- Create: `views/recycle/RecycleBin.vue`
- Create: `AssetDeleteTest.java`

**Interfaces:**
- `POST /api/assets/{id}/recycle`
- `POST /api/assets/{id}/restore`
- `DELETE /api/assets/{id}` → 硬删；若 `asset_character_rel` 或 `ai_reference_item` 仍引用 → **409** + 引用摘要
- `GET /api/assets?status=DELETED`

- [ ] **Step 1: 测试硬删被引用时 409**

```java
@Test
void hardDeleteBlockedWhenLinkedToCharacter() {
  // 建立 rel 后
  assertThatThrownBy(() -> assetService.hardDelete(assetId))
      .isInstanceOf(ConflictException.class);
}
```

- [ ] **Step 2: 实现软删/恢复/硬删（删文件）**

软删：`status=DELETED`, `deletedAt=now`；恢复清空 `deletedAt`。  
硬删：检查引用 → 删 rel/tag rel → 删文件 → 删 asset 行。

- [ ] **Step 3: 回收站页面**

- [ ] **Step 4: Commit**（用户要求时）

```bash
git commit -m "feat: recycle bin with restore and guarded hard delete"
```

---

### Task 8: AI 参考区（单例会话）

**Files:**
- Create: `domain/AiReferenceSession.java`, `AiReferenceItem.java`, services, controller
- Create: `views/ai/AiReference.vue`
- Create: `AiReferenceServiceTest.java`

**Interfaces:**
- `GET /api/ai-reference/current` → 无则自动创建名为 `default` 的会话
- `PUT /api/ai-reference/current/items` body: `[{ assetId, purpose, note, strength }]` 按数组顺序写 `sort_order`

- [ ] **Step 1: 测试保存顺序与用途**

```java
@Test
void replaceItemsKeepsOrder() {
  aiReferenceService.replaceCurrentItems(List.of(
      new Item(a1, "外貌", null, null),
      new Item(a2, "服装", null, null)));
  assertThat(aiReferenceService.getCurrent().getItems())
      .extracting(AiReferenceItem::getAssetId)
      .containsExactly(a1, a2);
}
```

- [ ] **Step 2: 实现 API + 前端（从素材库勾选/排序/用途）**

- [ ] **Step 3: Commit**（用户要求时）

```bash
git commit -m "feat: AI reference session without model calls"
```

---

### Task 9: 联调验收与文档回写

**Files:**
- Modify: `README.md`（管理端启动、库、storage、本地配置说明）
- Modify: `docs/superpowers/specs/2026-08-13-asset-module-design.md` 状态改为「已实现（首期）」

**Interfaces:** 无新接口

- [ ] **Step 1: 按设计第 9 节逐条手工验收**，记录结果到计划文末或 PR 描述

验收清单：

1. 多图上传 + 预览/缩略图/序号  
2. 拖拽排序与跨分类，刷新保持；失败回滚  
3. 重命名/说明/标签/关联人物  
4. 回收站恢复与硬删；引用拦截  
5. AI 参考区持久化  
6. 人物 CRUD  
7. 配置 CRUD + 默认回退  

- [ ] **Step 2: 更新 README 启动说明**

- [ ] **Step 3: Commit**（用户要求时）

```bash
git commit -m "docs: record asset module local runbook and completion status"
```

---

## Spec Coverage Check

| Spec 项 | Task |
|---------|------|
| MySQL + storage 目录 | Task 1 |
| sys_config + 默认回退 | Task 2 |
| 人物基础字段 CRUD | Task 3 |
| 分类预置/可新增 | Task 4 |
| 上传/预览/元数据 | Task 4 |
| 拖拽排序/跨分类 | Task 5 |
| 标签 + 人物关联 | Task 6 |
| 回收站 | Task 7 |
| AI 参考区 | Task 8 |
| 验收与文档 | Task 9 |
| 不做批量/真 AI/登录 | Global Constraints（各 Task 不实现） |

## Placeholder / Consistency Notes

- 表名使用 `character_profile`，避免 SQL 保留字；API 路径仍为 `/api/characters`
- 密码仅出现在本地 `application-local.yml`
- 提交步骤默认「仅当用户要求时执行」，以符合仓库协作习惯
