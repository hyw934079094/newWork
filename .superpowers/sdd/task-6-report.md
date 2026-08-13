# Task 6 Report — 联调验收与文档（人物本体）

**Branch:** `master`  
**Date:** 2026-08-13  
**BASE:** `113359d`  
**Env:** JDK `D:\jdk\jdk-24.0.1` (24.0.1), MySQL `story_admin`, admin API `http://localhost:8081`, Vite `http://localhost:5174`

## Unit tests

```text
mvn "-Dtest=CharacterIdentityServiceTest,CharacterServiceTest" test  (JAVA_HOME=D:\jdk\jdk-24.0.1)
Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS

mvn test
Tests run: 30, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
（含 CharacterIdentityServiceTest 2、CharacterServiceTest 6、AssetHardDeleteIdentityTest 1）
```

| Test class | Result |
|------------|--------|
| `CharacterIdentityServiceTest` (2) | PASS — delete 409 when has forms；CRUD/members/assets round-trip |
| `CharacterServiceTest` (6) | PASS — 含 `addFormCreatesIdentityForStandaloneCharacter` / `addFormReusesExistingIdentity` |
| `AssetHardDeleteIdentityTest` (1) | PASS — 本体引用硬删拦截 |

## API smoke（重启 8081；Flyway V4 已应用到 `story_admin`）

| Check | Result | Evidence |
|-------|--------|----------|
| `GET /api/health` | PASS | `{"status":"ok","service":"story-admin-server"}` |
| §7.1 无本体人物 | PASS | create/get `identityId=null` |
| §7.2 本体 + ≥2 形态 formLabel + 刷新 | PASS | `ID-*`；labels=`daily,thief`；GET members=2 |
| §7.3 本体共用素材 + 形态素材 | PASS | identity `assetIds` + `PUT /characters/{id}/assets` |
| §7.4 删本体 / 硬删引用素材 | PASS | 均 HTTP **409**，message 含形态名 / 本体名 |
| §7.5 列表 identity 字段 | PASS | `identityId` + `formLabel`；`?identityId=` 筛选 |
| §7.6 `POST /characters/{id}/forms` | PASS | 自动建本体；原+新形态均挂接；刷新可见 |

原始摘要：`.superpowers/sdd/task-6-smoke.json`（脚本 `.superpowers/sdd/task-6-smoke.ps1`）

## 设计第 7 节验收清单

| # | 项 | 结果 | 说明 |
|---|----|------|------|
| 1 | 无本体时人物用法与现在一致 | PASS | API + 单测 |
| 2 | 可建本体并挂 ≥2 形态（含 formLabel），刷新后配置仍在 | PASS | API + `CharacterIdentityServiceTest` |
| 3 | 本体可挂共用素材；形态可各自挂素材 | PASS | API smoke |
| 4 | 有形态时删本体 → 409；硬删被本体引用的素材 → 409 | PASS | API + 单测 |
| 5 | 人物列表能看出所属本体并跳转 | PARTIAL | API 字段/筛选 PASS；`CharacterList` 列与 `/character-identities/:id` 链接（代码）；Vite `/characters` 200；**浏览器未点跳转** |
| 6 | 独立人物点「添加形态」自动生成本体并挂接 | PASS | API `forms` + 单测；UI 弹窗未点（标 UI PARTIAL） |

## UI / 浏览器

- Vite 在 **5174** 监听；`/`、`/characters`、`/character-identities` HTTP **200**（SPA shell）。
- **未做**真实浏览器点击：本体编辑保存、所属本体跳转、「添加形态」弹窗。
- 判定同前序验收：可自动化项用 API/单测证据；UI 交互标 **PARTIAL**。

## 文档回写

- [x] `README.md` — 人物本体入口 + 当前状态 + 基础能力
- [x] `docs/superpowers/specs/2026-08-13-character-identity-design.md` — 状态 → **已实现（首期）**
- [x] `docs/superpowers/plans/2026-08-13-character-identity.md` — Task 6 checklist [x]；文末 §7 验收表
- [x] `.superpowers/sdd/task-6-smoke.json` — API smoke 摘要

## Commit

| Message |
|---------|
| `docs: record character identity acceptance` (master `HEAD`) |

## Concerns / Notes

1. 验收前本机 `V2__character_story_name.sql` COMMENT 字节损坏（非 UTF-8），Flyway 无法算 checksum，已改为 ASCII COMMENT 并更新 `flyway_schema_history` version=2 checksum；**V4 已成功 migrate**（`character_identity` / `identity_id` 列存在）。
2. 409 响应体在 Windows PowerShell 控制台中文可能乱码，HTTP 状态与 JSON 字段仍可读。
3. 人物列表 DTO 不含水合 `identityName`；前端用本体列表 Map 拼「本体名 · formLabel」——API §7.5 以 `identityId`/`formLabel` 为准。
4. 浏览器点验（§7 #5 跳转、#6 弹窗）需人工补全。
