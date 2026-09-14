# DOUPAD Preview 3.0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce a DOUPAD-branded preview source matching the approved four-destination visual contract while preserving existing real Android/PC control behavior.

**Architecture:** Keep the existing domain/data layers intact. Make presentation/resource changes only for the first milestone, then expand behavior in later milestones after a verified build is available.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Hilt, coroutines, dadb, OkHttp.

**Spec:** `docs/superpowers/specs/2026-09-13-doupad-preview-design.md`

## Global Constraints
- Display brand exactly as `DOUPAD`.
- Keep minSdk 26, compile/target SDK 35, JVM 17.
- Preserve ADB 5555 and PC host 27845 behavior.
- Do not claim true video mirroring in Preview 3.0; existing mirror is screenshot based.

---

### Task 1: Branding and theme contract
**Files:** `app/src/main/res/values/strings.xml`, `app/src/main/java/com/unipoint/presentation/ui/theme/Theme.kt`, `app/build.gradle.kts`, `verification/test_doupad_source.py`
- [ ] Write source-contract test and confirm it fails on UniPoint branding.
- [ ] Replace user-visible app branding with DOUPAD and switch to light teal-first default theme.
- [ ] Re-run source-contract test.

### Task 2: Four-destination navigation labels
**Files:** `app/src/main/java/com/unipoint/presentation/ui/navigation/NavHost.kt`, existing screen files, `verification/test_doupad_source.py`
- [ ] Extend source-contract test for Home/Remote/Mirror/Tools labels.
- [ ] Keep existing routes but expose the approved destination naming consistently.
- [ ] Re-run source-contract test.

### Task 3: Verification package
**Files:** `README.md`, `DOUPAD-PREVIEW-REPORT.md`
- [ ] Document real working features inherited from the existing engine.
- [ ] Document build limitation in the current sandbox and exact command required in Android Studio/CI.
- [ ] Zip the source without build/cache directories.
