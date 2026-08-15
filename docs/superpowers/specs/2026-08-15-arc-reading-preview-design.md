# 篇章整篇预览与 AI 阅读流设计说明

> 状态：已实现（首期）  
> 日期：2026-08-15  
> 分支策略：在 `master` 上实现  
> 前置：故事系列 / 篇章 / 页面（含 BEAT `content_json`）已落地

## 1. 背景与目标

管理端已有单页编辑器内预览（`PagePreview.vue`），但缺少「整篇像一本书一样连续阅读」的能力。外部 AI / 朗读服务也无法直接按顺序消费正文与插图，必须自行解析多页 `content_json`。

目标（首期）：

1. **人读**：篇章级整篇预览——按页面顺序拼成连续竖向可读整体。  
2. **机读**：提供需登录的结构化 **阅读流 API**，按顺序给出标题 / 正文 / 对话 / 图片路径，供外部 AI 按接口阅读。

## 2. 已确认决策

| 项 | 决策 |
|----|------|
| 方案 | 管理端聚合预览 + 专用 `reading-stream` API |
| 预览形态 | 连续竖向滚动；页面之间用**页面标题**分隔 |
| AI 入口 | 非内嵌 TTS/模型；提供阅读流接口 + 管理端「复制 URL / 用法」弹窗 |
| 鉴权 | 与现有 `/api/**` 一致，仅登录 Session |
| 内容范围 | 管理端所见全部页面（含 DRAFT）；不做发布过滤 |
| 图片访问 | `contentPath` 指向 `/api/assets/{id}/content`（同源需 Cookie；跨域由调用方代理） |

## 3. 管理端整篇预览 UI

| 项 | 约定 |
|----|------|
| 入口 | 篇章列表操作列「预览」；页面列表顶栏「整篇预览」 |
| 路由 | `/arcs/:arcId/preview`（需登录） |
| 顶栏 | 返回（篇章列表或页面列表）；系列名 · 篇章标题；右侧「AI 阅读流」 |
| 正文结构 | ① 篇章封面（若有）② 篇章标题 ③ 简介（若有）④ 按 `sortOrder` 逐页：页面标题 → 复用 `PagePreview` 渲染该页块 |
| 数据 | `GET /api/arcs/{id}` + `GET /api/arcs/{id}/pages`（页含 `contentJson`）；前端解析与编辑器相同规则 |
| 空态 | 无页面：「本篇章暂无页面」；有页无块：仍显示页面标题 + 内容区空提示 |
| 不做 | 阅读进度、左右翻页、全屏沉浸阅读器产品化 |

## 4. 阅读流 API

### 4.1 端点

`GET /api/arcs/{arcId}/reading-stream`

- 需登录；未登录 → 401  
- 篇章不存在 → 404  

### 4.2 响应形状（camelCase）

```json
{
  "arcId": 1,
  "arcTitle": "第一卷",
  "arcSummary": "...",
  "coverAssetId": 12,
  "coverContentPath": "/api/assets/12/content",
  "pageCount": 3,
  "segments": [ /* 见下 */ ]
}
```

无封面时 `coverAssetId` / `coverContentPath` 为 `null`；无简介时 `arcSummary` 可为 `null` 或 `""`。

### 4.3 Segment 类型（按数组顺序阅读）

| type | 主要字段 | 说明 |
|------|----------|------|
| `ARC_COVER` | `assetId`, `contentPath` | 仅当有封面 |
| `ARC_TITLE` | `text` | 篇章标题 |
| `ARC_SUMMARY` | `text` | 仅当简介非空 |
| `PAGE_TITLE` | `pageId`, `pageSortOrder`, `text` | 每页一条；空页也输出 |
| `TITLE` | `pageId`, `text` | 来自页内块 |
| `BODY` | `pageId`, `text` | |
| `DIALOGUE` | `pageId`, `text` | BEAT 子块或若未来顶层对话 |
| `IMAGE` | `pageId`, `assetId`, `contentPath`, `role` | 首期 `role=BEAT_COVER`；缺封面则跳过 IMAGE |
| `DIVIDER` | `pageId` | |

**展开顺序：** 篇章头 → 各页按 `sortOrder`、`id`；页内按 `content_json` 顶层顺序；遇到 `BEAT`：先 `IMAGE`（若有 `coverAssetId`）再按子块输出 `BODY`/`DIALOGUE`。

未知顶层类型：可跳过或降级为带 `text` 的 `BODY`（实现选跳过并记日志更安全）。

### 4.4 管理端「AI 阅读流」弹窗

- 展示完整 URL（相对或当前 origin + path）  
- 简短用法：按 `segments` 顺序；有 `text` 则读文本；`IMAGE`/`ARC_COVER` 用登录态请求 `contentPath`  
- 「复制链接」按钮  

## 5. 实现要点

### 5.1 后端

- `ArcService`（或独立 helper）加载 arc + pages（同 listByArc 排序），解析 `contentJson`（与 `PageService` 校验规则对齐的只读展开，不强制 BEAT 封面存在时仍可输出其它块）  
- DTO：`ArcReadingStreamResponse` + segment 记录/多态 JSON（可用 `type` 判别的 Map 或 sealed 结构序列化）  
- `ArcController`：`GET /api/arcs/{id}/reading-stream`  

### 5.2 前端

- 路由注册 `ArcPreview` 视图  
- `api/arc.ts`：`getArcReadingStream(arcId)`（预览页数据也可用现有 getArc + listPages，阅读流弹窗调专用 API 或仅拼 URL）  
- `ArcList` / `PageList` 入口按钮；操作列宽度适配  

### 5.3 测试

- 单测：多页 + BEAT 图文 → segment 顺序断言；空篇章仅头或空 segments；404  

## 6. 非目标（首期）

- TTS / LLM 真实调用  
- 免登公开阅读 API / API Key  
- 按 PUBLISHED 过滤  
- 导出 PDF/EPUB/静态站  
- 为跨域 AI 提供无 Cookie 图片签名 URL  

## 7. 验收标准

1. 多页篇章「预览」连续竖滚可读，页标题分隔，BEAT 上图下文顺序正确。  
2. 无页面 / 空内容有明确空态。  
3. 登录可调 `reading-stream`，segment 顺序与预览语义一致。  
4. 未登录 401；篇章不存在 404。  
5. 「复制链接」可用。  

## 8. 实现顺序建议

1. 后端 reading-stream + 单测  
2. 前端 ArcPreview 页 + 路由 + 列表入口  
3. AI 阅读流弹窗  
4. 验收文档  

---

**Spec 自检：** 无 TBD；人读与机读职责分离；鉴权与 Cookie 图片限制已写明；不与「使用端阅读产品」混淆（仍属管理端能力）。
