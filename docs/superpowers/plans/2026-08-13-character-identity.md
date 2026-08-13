# 人物本体与多形态 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付管理端「人物本体」：独立人物可挂到同一人下；本体共用素材；从独立人物「添加形态」时自动创建本体并关联原人物与新形态。

**Architecture:** Flyway V4 增加 `character_identity` / `identity_asset_rel`，人物表增加可空 `identity_id`、`form_label`。Spring 提供 `/api/character-identities` 与 `POST /api/characters/{id}/forms`；前端人物管理下增加本体列表/编辑，人物页增加添加形态弹窗。素材硬删扩展本体引用检查。

**Tech Stack:** Java 17+、Spring Boot 3.3、JPA、Flyway、Vue 3、Element Plus、Vite

**Spec:** `docs/superpowers/specs/2026-08-13-character-identity-design.md`

## Global Constraints

- 在 `master` 上直接提交（不再开 feature 分支，除非用户另行要求）
- 普通人：`identity_id` 为空，行为与现网一致
- 一个人物最多一个本体；本体本身不出场
- 添加形态：无本体则自动建本体并挂原人物+新人物；已有本体则只新建并挂接
- 添加形态不自动迁移原人物素材到本体
- 删本体时若仍有形态 → 409；硬删素材若被本体引用 → 409 + 本体名
- 本体 `code` 前缀 `ID-`（与人物 code 区分）；`form_label` 首期自由文本
- Git：`D:\tool\Git\bin\git.exe` + `-F` 消息文件（UTF-8 无 BOM）；避免 `--trailer`
- JDK：`JAVA_HOME=D:\jdk\jdk-24.0.1`；前端 Node `D:\tool\nvm\v22.17.0`
- SQL 文件写入后须验证非 `%TSD-Header-###%`（可用 node 读文件头）；优先 Write / .NET WriteAllText，必要时 `cmd ren` from `.txt`

---

## File Map

### Backend

| Path | Responsibility |
|------|----------------|
| `db/migration/V4__character_identity.sql` | 本体表、关联表、人物增量列 |
| `domain/CharacterIdentity.java` | 本体实体 |
| `domain/IdentityAssetRel.java` (+ Id) | 本体-素材关联 |
| `domain/CharacterProfile.java` | `identityId`, `formLabel` |
| `repository/CharacterIdentityRepository.java` 等 | 仓库；`findIdentityNamesByAssetId` |
| `service/CharacterIdentityService.java` | CRUD、members、assets、删检 |
| `service/CharacterService.java` | DTO 字段；`addForm` |
| `controller/CharacterIdentityController.java` | `/api/character-identities` |
| `controller/CharacterController.java` | `POST /{id}/forms`；列表筛 identityId |
| `service/AssetService.java` | hardDelete 增加本体引用 |

### Frontend

| Path | Responsibility |
|------|----------------|
| `api/characterIdentity.ts` | 本体 API |
| `api/character.ts` | identity 字段 + `addCharacterForm` |
| `views/identities/IdentityList.vue` | 本体列表 |
| `views/identities/IdentityEditor.vue` | 本体编辑 |
| `views/characters/CharacterList.vue` | 所属本体列；添加形态弹窗 |
| `App.vue` / `router/index.ts` | 子菜单与路由 |

---

### Task 1: Flyway V4 + 实体/仓库

**Files:**
- Create: `story-admin-server/src/main/resources/db/migration/V4__character_identity.sql`
- Create: `domain/CharacterIdentity.java`, `IdentityAssetRel.java`, `IdentityAssetRelId.java`
- Modify: `domain/CharacterProfile.java` — `identityId`, `formLabel`
- Create: `repository/CharacterIdentityRepository.java`, `IdentityAssetRelRepository.java`
- Modify: `repository/CharacterProfileRepository.java` — `findByIdentityIdOrderByIdAsc`, `countByIdentityId`
- Test: 可选本地 Flyway 冒烟或依赖 Task 2 集成测试

**Interfaces:**
- Produces: schema；`IdentityAssetRelRepository.findIdentityNamesByAssetId(Long): List<String>`

- [ ] **Step 1: 写 V4 SQL（验证明文）**

```sql
CREATE TABLE character_identity (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    story_name VARCHAR(200) NULL,
    public_intro TEXT NULL,
    internal_note TEXT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_character_identity_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE identity_asset_rel (
    identity_id BIGINT NOT NULL,
    asset_id BIGINT NOT NULL,
    PRIMARY KEY (identity_id, asset_id),
    CONSTRAINT fk_identity_asset_rel_identity FOREIGN KEY (identity_id) REFERENCES character_identity (id),
    CONSTRAINT fk_identity_asset_rel_asset FOREIGN KEY (asset_id) REFERENCES asset (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE character_profile
  ADD COLUMN identity_id BIGINT NULL,
  ADD COLUMN form_label VARCHAR(50) NULL,
  ADD CONSTRAINT fk_character_profile_identity FOREIGN KEY (identity_id) REFERENCES character_identity (id);

CREATE INDEX idx_character_profile_identity_id ON character_profile (identity_id);
```

- [ ] **Step 2: 实体与仓库**（对齐 `AssetCategory` / `AssetCharacterRel` 模式）

- [ ] **Step 3: 验证文件头非 TSD；可选启动确认 Flyway V4**

- [ ] **Step 4: Commit**

```bash
git commit -m "chore: add Flyway V4 character_identity schema"
```

---

### Task 2: CharacterIdentityService CRUD + members/assets + 硬删引用

**Files:**
- Create: DTOs `CharacterIdentityUpsertRequest`, `IdentityMemberRequest`, `IdentityDetailResponse`, …
- Create: `service/CharacterIdentityService.java`, `controller/CharacterIdentityController.java`
- Modify: `AssetService.java` hardDelete / `buildReferenceSummary`
- Create: `CharacterIdentityServiceTest.java`, `AssetHardDeleteIdentityTest.java`（或扩现有删除测）

**Interfaces:**
- Produces:
  - `GET/POST /api/character-identities`
  - `GET/PUT/DELETE /api/character-identities/{id}`
  - `PUT /api/character-identities/{id}/members`
  - `PUT /api/character-identities/{id}/assets`
- `delete`：`countByIdentityId > 0` → ConflictException
- `setMembers`：全量；校验人物存在；写 `identityId` + `formLabel`；被移出的人物清空 identity 字段

- [ ] **Step 1: 失败测试 — 有形态时删本体 409**

```java
@Test
void deleteBlockedWhenHasForms() {
  // 建本体 + 挂 1 人物 → delete → ConflictException，消息含人物名
}
```

- [ ] **Step 2: 失败测试 — 硬删被本体引用素材 409**

```java
@Test
void hardDeleteBlockedWhenUsedByIdentity() {
  // 素材挂到本体 → hardDelete → ConflictException，含本体名
}
```

- [ ] **Step 3: 实现 Service/Controller + AssetService 扩展；跑通 CRUD/members/assets**

- [ ] **Step 4: `mvn "-Dtest=CharacterIdentityServiceTest,AssetHardDeleteIdentityTest" test` → PASS**

- [ ] **Step 5: Commit**

```bash
git commit -m "feat: add character identity CRUD, members, assets, hard-delete guard"
```

---

### Task 3: Characters 带 identity 字段 + POST forms

**Files:**
- Modify: `CharacterCreateRequest` / `CharacterUpdateRequest` / 响应或实体序列化字段
- Modify: `CharacterQuery` — 可选 `identityId`
- Modify: `CharacterService.java` — create/update/list 处理 identity；新增 `addForm`
- Modify: `CharacterController.java` — `POST /{id}/forms`
- Create/Modify: `CharacterServiceTest` — 升级流与已有本体再加形态

**Interfaces:**
- Produces: `POST /api/characters/{id}/forms` → `IdentityDetailResponse`
- `addForm`：
  - 原人物无 `identityId`：创建本体（name=identityName 或原名，storyName 默认原人物，code=`ID-`+序号）；原人物设 identity + originalFormLabel（默认「默认」）；创建新人物并挂接
  - 原人物已有 identity：忽略 identityName；创建新人物挂同一本体；首期可不改原标签

- [ ] **Step 1: 失败/成功测试 — 独立人物添加形态自动建本体**

```java
@Test
void addFormCreatesIdentityForStandaloneCharacter() {
  // 无本体人物 → addForm → 两人同 identityId；返回详情含 2 形态
}
```

- [ ] **Step 2: 测试 — 已有本体再添加形态不新建本体**

```java
@Test
void addFormReusesExistingIdentity() {
  // 已有 identity → addForm → identity 数量仍为 1，形态 +1
}
```

- [ ] **Step 3: 实现 addForm + DTO/列表筛选；测试 PASS**

- [ ] **Step 4: Commit**

```bash
git commit -m "feat: add character form upgrade API and identity fields"
```

---

### Task 4: 前端本体列表/编辑 + 路由菜单

**Files:**
- Create: `story-admin-web/src/api/characterIdentity.ts`
- Create: `views/identities/IdentityList.vue`, `IdentityEditor.vue`
- Modify: `App.vue`, `router/index.ts`

**Interfaces:**
- Produces: 侧栏「人物管理」分组：人物 `/characters`、人物本体 `/character-identities`（及 `/:id`）
- 本体 exact-active 规则同素材子菜单（避免前缀互亮）

- [ ] **Step 1: API 客户端**

- [ ] **Step 2: IdentityList + IdentityEditor**（基本信息、members 多选/标签、assets 多选、保存）

- [ ] **Step 3: 路由与 App 子菜单**

- [ ] **Step 4: `npm run build` PASS + Commit**

```bash
git commit -m "feat: add character identity list and editor UI"
```

---

### Task 5: 人物列表本体列 + 添加形态弹窗

**Files:**
- Modify: `api/character.ts` — types + `addCharacterForm`
- Modify: `views/characters/CharacterList.vue`

**Interfaces:**
- Consumes: list with identity；`POST .../forms`
- Produces: 列「所属本体」可跳转；无本体显示「添加形态」，有本体显示「再添加形态」；成功后 `router.push` 本体编辑页

- [ ] **Step 1: 列表展示 `identityName · formLabel`（无则空）**

- [ ] **Step 2: 弹窗字段对齐 API JSON；调用 addCharacterForm**

- [ ] **Step 3: build PASS + Commit**

```bash
git commit -m "feat: character list identity column and add-form dialog"
```

---

### Task 6: 联调验收与文档

**Files:**
- Modify: `README.md`（人物本体入口）
- Modify: design 状态 → 已实现（首期）
- Append: 本计划文末 §7 验收表

**Interfaces:** 无

- [ ] **Step 1: 按设计 §7 逐条验收**（API + 尽量浏览器）

- [ ] **Step 2: 更新 README / 设计状态 / 计划验收表**

- [ ] **Step 3: Commit**

```bash
git commit -m "docs: record character identity acceptance"
```

---

## Spec Coverage

| Spec | Task |
|------|------|
| V4 表 + 人物增量列 | Task 1 |
| Identity CRUD / members / assets / 删检 | Task 2 |
| 素材硬删本体引用 | Task 2 |
| Characters identity 字段 + forms 升级 | Task 3 |
| 本体 UI | Task 4 |
| 人物列表 + 添加形态 | Task 5 |
| 验收文档 | Task 6 |
| 不迁素材 / 不接使用端 | Global Constraints |

## Notes

- 人物 `nextCode()` 已有；本体另写 `nextIdentityCode()` → `ID-0001` 风格  
- members 全量替换时注意：勿误清无关人物；仅处理本 identity 原成员 + 本次提交 id  
- TSD：改 `.sql` 后务必做文件头校验再 commit  
