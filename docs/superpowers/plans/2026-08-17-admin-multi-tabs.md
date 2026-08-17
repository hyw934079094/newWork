# Admin Multi-Tabs Implementation Plan

> **For agentic workers:** Implement task-by-task. Spec: `docs/superpowers/specs/2026-08-17-admin-multi-tabs-design.md`

**Goal:** Parallel tabs in admin shell with sidebar dedupe, in-tab drill-down, context menu, session restore.

**Architecture:** `tabStore` (tabId + entryKey + fullPath) + `TabBar` + App route sync; sidebar clicks activate by `entryKey`; other navigations update active tab.

**Tech Stack:** Vue 3, vue-router, sessionStorage

### Task 1: tabStore + titles + router meta
### Task 2: TabBar + App.vue wiring + styles
### Task 3: Commit push verify
