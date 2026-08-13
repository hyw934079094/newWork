# Task 7 Report — 联调验收与文档（组合编排）

**Branch:** `feature/asset-combo`  
**Date:** 2026-08-13  
**BASE:** `e84ce23`  
**Env:** JDK `D:\jdk\jdk-24.0.1` (24.0.1), MySQL `story_admin`, admin API `http://localhost:8081`, Vite `http://localhost:5174`

## Unit tests

```text
mvn "-Dtest=ComboServiceTest,AssetHardDeleteComboTest" test  (JAVA_HOME=D:\jdk\jdk-24.0.1)
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS

mvn test
Tests run: 23, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

| Test class | Result |
|------------|--------|
| `ComboServiceTest` (3) | PASS — 校验拒绝 + CRUD round-trip（含 loop/holds） |
| `AssetHardDeleteComboTest` (1) | PASS — 组合引用硬删拦截 |

## API smoke（重启 8081 以加载 `/api/combos`）

| Check | Result | Evidence |
|-------|--------|----------|
| `GET /api/health` | PASS | `{"status":"ok","service":"story-admin-server"}` |
| Create combo (2 members, seq, hold) | PASS | id=1, seq=`1,2,1`, holds=1, interval=0.5, loop=true |
| Get / list | PASS | memberNos=`1,2`；列表含新建项 |
| Update seq / interval / loop / hold | PASS | seq=`2,1,2,1`, loop=false, hold step=3 @ 2.0s |
| Persist after re-GET | PASS | 字段与 update 一致（§9 #5） |
| Hard-delete referenced asset | PASS | HTTP **409**, message 含组合名 `smoke-combo-*` |
| Delete combo | PASS | DELETE ok → GET 404 |

原始摘要：`.superpowers/sdd/task-7-smoke.json`

## 设计第 9 节验收清单

| # | 项 | 结果 | 说明 |
|---|----|------|------|
| 1 | 可选多张素材、排序后显示 1..n，保存组合成功 | PASS | API create/get `memberNo` 1..n；单测 round-trip |
| 2 | 播放序列与默认间隔生效；个性化「第 k 步停留 x 秒」生效 | PARTIAL | API 持久化 PASS；`ComboPreviewPlayer` 用 `holdByStepIndex` / `defaultIntervalSec` 调度（代码）；**浏览器未点播核实时序** |
| 3 | 循环开/关行为正确；预览可播可停 | PARTIAL | API `loopEnabled` PASS；预览组件 play/stop/loop/finished（代码）；Vite `GET /` 与 `/assets/combos` 200；**浏览器未点播/停** |
| 4 | 硬删被组合引用的素材 → 409 并提示组合名 | PASS | API 409 + 组合名；单测 |
| 5 | 刷新后组合配置仍在 | PASS | update 后再 GET 一致 |

## UI / 浏览器

- Vite 已在 **5174** 监听；组合路由 HTTP 200（SPA shell）。
- **未做**真实浏览器点击：编辑页成员排序、预览播放/停止、循环开关时序观察。
- 判定同素材模块 Task 9：可自动化项用 API/单测证据；UI 交互标 **PARTIAL**。

## 文档回写

- [x] `README.md` — 组合编排入口（侧栏/`/assets/combos`/`/api/combos`）+ 当前状态
- [x] `docs/superpowers/specs/2026-08-13-asset-combo-design.md` — 状态 → **已实现（首期）**
- [x] `docs/superpowers/plans/2026-08-13-asset-combo.md` — Task 7 checklist [x]；文末 §9 验收表
- [x] `.superpowers/sdd/task-7-smoke.json` — API smoke 摘要

## Commit

| SHA | Message |
|-----|---------|
| `dbbdee3` | `docs: record asset combo runbook and acceptance` |

仅暂存 README / docs（combo 设计·计划）/ `.superpowers/sdd` 验收产物；**不**包含 character WIP 及其它无关改动。

## Concerns / Notes

1. 验收前 8081 上旧进程无 `/api/combos`（404），已重启后再测。
2. 409 响应体在 Windows PowerShell 控制台中文乱码，HTTP 状态与 JSON 字段仍可读；组合名在 message 内。
3. 浏览器预览时序（§9 #2–3）需人工点验补全。
