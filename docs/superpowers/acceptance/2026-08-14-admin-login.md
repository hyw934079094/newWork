# 管理端基础登录验收（2026-08-14）

## 范围

Session Cookie 登录；种子账号 `admin` / `admin`；改密；保护 `/api/**`（白名单：health、login）。

## 结果

| # | 项 | 结果 |
|---|----|------|
| 1 | 未登录 `GET /api/series` → 401 | PASS |
| 2 | `admin/admin` 登录后业务可读 | PASS |
| 3 | 错误密码 → 401 | PASS |
| 4 | 退出后业务 → 401 | PASS |
| 5 | 种子幂等（重启不炸） | PASS（Runner） |
| 6 | 前端 build | PASS |
| 7 | 后端单测（含 Auth / Security IT） | PASS |

## 说明

- CSRF 首期关闭（管理端本机/可信环境）；会话 `server.servlet.session.timeout=8h`。
- 默认弱口令仅本地；侧栏「改密」可更换。
