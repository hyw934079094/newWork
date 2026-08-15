# 素材缩略图拖拽边缘自动滚动

> 状态：已确认  
> 日期：2026-08-15

## 目标

工作台底部横向缩略图拖排序时，指针靠近左/右边缘自动滚动列表，便于一次拖到远处位置。

## 决策

- 左右边缘均自动滚动  
- 感应区约 48px；仅拖拽中启用  
- 优先启用 SortableJS `scroll` + `forceAutoScrollFallback`（HTML5 DnD 下原生自动滚常失效）  
- 仅前端 `AssetWorkbench.vue`，无 API 变更
