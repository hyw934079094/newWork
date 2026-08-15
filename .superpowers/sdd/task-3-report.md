# Task 3 Report: AI reading-stream URL dialog on ArcPreview

**Status:** DONE  
**Commit:** `a56d2d1` — `feat: add AI reading-stream URL dialog on arc preview`  
**Pushed:** `origin/master`

## Deliverables

| Item | Result |
|------|--------|
| `story-admin-web/src/api/arc.ts` | Added `arcReadingStreamUrl(arcId)` → `/api/arcs/{id}/reading-stream` |
| `story-admin-web/src/views/arcs/ArcPreview.vue` | Header「AI 阅读流」+ `el-dialog` with full URL, usage bullets,「复制链接」 |
| `npm run build` | PASS (`vue-tsc -b && vite build`, exit 0, ~7.3s) |

## Behavior

- Full URL: `window.location.origin + arcReadingStreamUrl(arcId)`.
- Dialog usage: login Session (401 if missing); read `segments` in order; `text` for prose, `contentPath` with Session for `IMAGE` / `ARC_COVER`.
- Copy: `navigator.clipboard.writeText` + `ElMessage.success('链接已复制')`; clipboard failure shows error toast.
- Button disabled when `arcId` invalid.

## Verify

```
cd story-admin-web
npm run build
→ EXIT=0
```

Browser click-through of dialog not exercised (build-only verify).

## Concerns

- Clipboard API may require secure context / permission in some browsers; fallback message only.
- Unrelated `storage/assets/*` and other SDD reports left unstaged.
