# 素材组合编排 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付管理端「组合编排」：多素材成员编号、播放序列、默认间隔、步进停留、循环预览，以及素材硬删时的组合引用拦截。

**Architecture:** Flyway 新增 `asset_combo` / `member` / `step_hold`；Spring 提供 `/api/combos` CRUD；前端在素材管理下增加列表+编辑页，预览用定时器按步间隔切换。硬删素材时扩展现有 `ConflictException` 引用检查。

**Tech Stack:** Java 17、Spring Boot 3.3、JPA、Flyway、Vue 3、Element Plus、Vite

**Spec:** `docs/superpowers/specs/2026-08-13-asset-combo-design.md`

## Global Constraints

- 菜单：素材管理 → 组合编排 `/assets/combos`
- 成员编号组合内 1..n 唯一；排序定成员与编号
- 播放序列逗号分隔，如 `1,3,5,4,9`；可重复、可跳号
- 个性化停留按**播放序列步序号**（从 1 起），非成员编号
- 默认间隔默认 1 秒，最小 0.1；循环默认 true
- 硬删素材若被组合引用 → 409 + 组合名
- 删组合不删素材文件；不导出 GIF/视频；不接使用端
- 提交：Windows 用 `D:\tool\Git\bin\git.exe` + `-F` 消息文件（避免 `--trailer`）
- JDK：`JAVA_HOME=D:\jdk\jdk-24.0.1`（或本机 17+）

---

## File Map

### Backend

| Path | Responsibility |
|------|----------------|
| `db/migration/V3__asset_combo.sql` | 三表 + 索引/唯一约束 |
| `domain/AssetCombo.java` 等 | 实体 |
| `repository/AssetCombo*Repository.java` | 仓库；`findComboNamesByAssetId` |
| `dto/Combo*Request.java` / `ComboDetailResponse.java` | 入出参 |
| `service/ComboService.java` | 校验、CRUD、解析序列 |
| `controller/ComboController.java` | `/api/combos` |
| `service/AssetService.java` | `hardDelete` 增加组合引用检查 |

### Frontend

| Path | Responsibility |
|------|----------------|
| `api/combo.ts` | API 客户端 |
| `views/combos/ComboList.vue` | 列表 |
| `views/combos/ComboEditor.vue` | 编辑 + 预览播放器 |
| `App.vue` / `router/index.ts` | 子菜单与路由 |

---

### Task 1: Flyway V3 + 实体/仓库

**Files:**
- Create: `story-admin-server/src/main/resources/db/migration/V3__asset_combo.sql`
- Create: `domain/AssetCombo.java`, `AssetComboMember.java`, `AssetComboStepHold.java`
- Create: `repository/AssetComboRepository.java`, `AssetComboMemberRepository.java`, `AssetComboStepHoldRepository.java`
- Test: 启动应用或 `mvn test` 中带 Flyway 的集成冒烟（可选 `ComboSchemaSmoke` 仅查表存在）

**Interfaces:**
- Produces: 表结构可用；`AssetComboMemberRepository.findComboNamesByAssetId(Long assetId): List<String>`

- [ ] **Step 1: 写 V3 SQL**

三表字段对齐设计 §3；`uk_combo_member_no (combo_id, member_no)`；`uk_combo_asset (combo_id, asset_id)`；`uk_combo_step (combo_id, step_index)`；FK 到 `asset_combo` / `asset`。

- [ ] **Step 2: 实体与仓库**

`AssetCombo`：`name`, `playSequence`, `defaultIntervalSec` (BigDecimal/double), `loopEnabled`, `remark`, timestamps。  
Member / StepHold 双向或单向 `@ManyToOne` + `comboId`。

- [ ] **Step 3: 启动验证 Flyway 成功**

```bash
# JAVA_HOME=JDK17+ ; application-local.yml 已配置
mvn -q -DskipTests spring-boot:run
# 确认 flyway_schema_history 含 V3
```

- [ ] **Step 4: Commit**（用户要求或 SDD 流程时）

```bash
git commit -m "chore: add Flyway V3 asset_combo tables"
```

---

### Task 2: ComboService CRUD + 校验 + API

**Files:**
- Create: `dto/ComboUpsertRequest.java`, `ComboMemberRequest.java`, `ComboStepHoldRequest.java`, `ComboDetailResponse.java`
- Create: `service/ComboService.java`, `controller/ComboController.java`
- Create: `src/test/java/.../ComboServiceTest.java`

**Interfaces:**
- Produces:
  - `GET /api/combos`
  - `POST /api/combos`
  - `GET /api/combos/{id}`
  - `PUT /api/combos/{id}`
  - `DELETE /api/combos/{id}`
- `ComboService.upsert` 校验：间隔≥0.1；序列项∈成员编号；stepIndex∈[1,序列长度]；保存时按 members 顺序重写 member_no=1..n（若客户端已带 memberNo 则校验连续 1..n）

- [ ] **Step 1: 失败测试 — 非法序列项拒绝**

```java
@Test
void rejectsPlaySequenceWithUnknownMemberNo() {
  // 成员仅 1,2；序列 "1,3" → BAD_REQUEST
}
```

- [ ] **Step 2: 失败测试 — 步进停留越界**

```java
@Test
void rejectsStepHoldOutOfRange() {
  // 序列长度 3；stepIndex=4 → BAD_REQUEST
}
```

- [ ] **Step 3: 实现 ComboService + Controller，跑通 create/get/list/update/delete**

详情响应含水合：`members[{memberNo, assetId, displayName, content可用}]`。

- [ ] **Step 4: `mvn -Dtest=ComboServiceTest test` → PASS**

- [ ] **Step 5: Commit**

```bash
git commit -m "feat: add asset combo CRUD API with sequence validation"
```

---

### Task 3: 素材硬删增加组合引用检查

**Files:**
- Modify: `AssetService.java`（`hardDelete` / `buildReferenceSummary`）
- Modify: `AssetDeleteTest.java` 或新建用例

**Interfaces:**
- Consumes: `AssetComboMemberRepository` 查引用组合名
- Produces: 硬删冲突消息同时包含人物 / AI / **组合**

- [ ] **Step 1: 失败测试 — 组合引用时硬删 409**

```java
@Test
void hardDeleteBlockedWhenUsedInCombo() {
  // 建素材 + 组合含该素材 → hardDelete → ConflictException，消息含组合名
}
```

- [ ] **Step 2: 实现检查并入 `buildReferenceSummary`**

- [ ] **Step 3: 测试 PASS + Commit**

```bash
git commit -m "fix: block asset hard-delete when referenced by combo"
```

---

### Task 4: 前端路由、列表、API 客户端

**Files:**
- Create: `story-admin-web/src/api/combo.ts`
- Create: `story-admin-web/src/views/combos/ComboList.vue`
- Modify: `App.vue`, `router/index.ts`

**Interfaces:**
- Produces: 侧栏「组合编排」；列表页 CRUD 入口（编辑跳 `/assets/combos/:id` 或 query）

- [ ] **Step 1: `combo.ts` 封装 list/get/create/update/remove**

- [ ] **Step 2: ComboList 表格 + 新建/删除/进编辑**

- [ ] **Step 3: App 子菜单 + 路由**

```ts
{ path: '/assets/combos', name: 'asset-combos', component: ComboList },
{ path: '/assets/combos/:id', name: 'asset-combo-edit', component: ComboEditor },
// 新建可用 id=new
```

- [ ] **Step 4: 浏览器打开列表可加载（可先空表）+ Commit**

```bash
git commit -m "feat: add combo list page and nav under assets"
```

---

### Task 5: 组合编辑页（成员 / 序列 / 间隔 / holds）

**Files:**
- Create: `views/combos/ComboEditor.vue`
- Reuse: `api/asset.ts` `listAssets` 选材

**Interfaces:**
- Consumes: Combo get/upsert APIs
- Produces: 可保存完整组合配置

- [ ] **Step 1: 基本字段表单**（name、defaultIntervalSec、loopEnabled、remark）

- [ ] **Step 2: 成员选择**（多选 NORMAL 素材，支持分类筛选）；拖拽排序；展示编号角标 1..n

- [ ] **Step 3: 播放序列输入 + 前端校验提示**

- [ ] **Step 4: 个性化停留动态表格**（stepIndex、holdSeconds）

- [ ] **Step 5: 保存调用 POST/PUT；失败展示后端 message**

- [ ] **Step 6: Commit**

```bash
git commit -m "feat: combo editor for members, sequence, and step holds"
```

---

### Task 6: 预览播放器

**Files:**
- Modify: `ComboEditor.vue`（或抽 `ComboPreviewPlayer.vue`）

**Interfaces:**
- Consumes: 当前表单/已加载详情的 members + playSequence + intervals
- Produces: 播放/暂停；步进显示；循环行为

- [ ] **Step 1: 解析序列 → 当前步素材 URL（`/api/assets/{id}/content`）**

- [ ] **Step 2: `scheduleNext`：用该步 hold 或 defaultInterval；循环开关分支**

- [ ] **Step 3: 播放/暂停按钮；显示 `k / total` 与当前秒数**

- [ ] **Step 4: 手工验收设计 §9 第 2–3 条 + Commit**

```bash
git commit -m "feat: combo preview player with interval and step holds"
```

---

### Task 7: 联调验收与文档

**Files:**
- Modify: `README.md`（简短说明组合编排入口）
- Modify: `docs/superpowers/specs/2026-08-13-asset-combo-design.md` 状态 → 已实现（首期）
- Append: 计划文末验收表

**Interfaces:** 无

- [x] **Step 1: 按设计 §9 逐条验收**（API + 浏览器）

- [x] **Step 2: 更新 README / 设计状态**

- [x] **Step 3: Commit**

```bash
git commit -m "docs: record asset combo runbook and acceptance"
```

---

## Spec Coverage

| Spec | Task |
|------|------|
| V3 表 | Task 1 |
| CRUD API + 校验 | Task 2 |
| 硬删引用 | Task 3 |
| 菜单列表 | Task 4 |
| 编辑成员/序列/holds | Task 5 |
| 预览播放 | Task 6 |
| 验收文档 | Task 7 |
| 不导出/不接使用端 | Global Constraints |

## Notes

- `member_no` 保存时服务端按提交的 members 数组顺序赋 1..n，避免前端漏号  
- 预览优先读「已保存详情」；编辑脏数据时可「用当前表单预览」按钮  
- 组合删除用确认框；不级联删素材  

---

## Task 7 验收结果（设计 §9）

**Branch:** `feature/asset-combo` · **Date:** 2026-08-13 · **Env:** JDK 24.0.1, MySQL `story_admin`, admin API `http://localhost:8081`, Vite `http://localhost:5174`

### Unit tests

```text
mvn test  (JAVA_HOME=D:\jdk\jdk-24.0.1)
Tests run: 23, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
（含 ComboServiceTest 3 + AssetHardDeleteComboTest 1）
```

### API smoke（重启服务加载 combo 端点后）

| Check | Result | Evidence |
|-------|--------|----------|
| `GET /api/health` | PASS | `{"status":"ok","service":"story-admin-server"}` |
| `POST /api/combos` 多成员 + 序列 + hold | PASS | id=1, members=2, seq=`1,2,1`, holds=1 |
| `GET /api/combos/{id}` / list | PASS | memberNos=1,2；列表含新建项 |
| `PUT` 改序列/间隔/loop/hold | PASS | seq=`2,1,2,1`, loop=false, hold step=3 |
| 再 GET 持久化 | PASS | 刷新后配置一致 |
| 硬删被引用素材 | PASS | `DELETE /api/assets/{id}` → **409**，message 含组合名 |
| `DELETE /api/combos/{id}` | PASS | 随后 GET → 404 |

原始摘要：`.superpowers/sdd/task-7-smoke.json`

### 设计第 9 节验收清单

| # | 项 | 结果 | 说明 |
|---|----|------|------|
| 1 | 可选多张素材、排序后显示 1..n，保存组合成功 | PASS | API create/get members 编号 1..n；`ComboServiceTest#createGetListUpdateDeleteRoundTrip` |
| 2 | 播放序列与默认间隔生效；个性化「第 k 步停留 x 秒」生效 | PARTIAL | API 持久化 seq/interval/holds PASS；`ComboPreviewPlayer` 按步 hold/`defaultIntervalSec` 调度（代码）；**浏览器未点播验证时序** |
| 3 | 循环开/关行为正确；预览可播可停 | PARTIAL | API `loopEnabled` 可写可读；预览组件含 play/stop/loop/finished（代码）；Vite `/assets/combos` 200；**浏览器播放/停止未点** |
| 4 | 硬删被组合引用的素材 → 409 并提示组合名 | PASS | API 409 + 组合名；`AssetHardDeleteComboTest` |
| 5 | 刷新后组合配置仍在 | PASS | update 后再 GET 字段一致 |

> 完整报告：`.superpowers/sdd/task-7-report.md`
