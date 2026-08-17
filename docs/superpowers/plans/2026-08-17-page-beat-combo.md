# Page Beat Combo Implementation Plan

> **For agentic workers:** Spec `docs/superpowers/specs/2026-08-17-page-beat-combo-design.md`

**Goal:** BEAT visual slot COVER XOR COMBO; preview one-box autoplay; reading-stream expands combo to IMAGE frames.

**Architecture:** `COMBO` child + `page_combo_ref` + `BEAT_COMBO_MEMBER` refs; PageService normalize/validate; ArcService expand; PageEditor/Preview reuse ComboPreviewPlayer.

**Tech Stack:** Spring Boot, Flyway V11, Vue 3

### Task 1: Flyway + page_combo_ref + PageService XOR COMBO + tests
### Task 2: ArcService reading-stream expand + ComboService delete guard
### Task 3: PageEditor / PagePreview / autoplay + commit push
