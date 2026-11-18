# NovaRun Lite 1.0 — Zero-Pending Checklist

Status values: NOT_STARTED, IN_PROGRESS, IMPLEMENTED, VERIFIED, BLOCKED (with reason and approved alternative).

## Product requirements
- [ ] Home dashboard, project count, server status, recent projects, ZIP import and server quick action.
- [ ] Project create/open/rename/delete/search/info, ZIP import/extract, backup/restore.
- [ ] File/folder explorer; create, rename, delete, copy, move, search, file-size information.
- [ ] HTML, CSS, JavaScript, JSON and Python editor; syntax highlighting, line numbers, find/replace, manual/automatic save and unsaved-change indicator.
- [ ] HTML/CSS/JS live preview, refresh, fullscreen, JavaScript toggle and error display.
- [ ] Localhost hosting; start/stop/restart; port selection; URLs, status and logs.
- [ ] LAN, Wi-Fi and hotspot access with access control and authentication.
- [ ] Optional separately provisioned Node.js, Python and SQLite runtime; install/uninstall, start/stop, status.
- [ ] Foreground background service, state monitoring, cleanup and crash recovery.
- [ ] Project directory isolation, ZIP traversal defense, symlink defense, safe file operations, secure credential storage and runtime permission control.
- [ ] ZIP backup/restore, storage usage, cache and temporary-file cleanup.
- [ ] Dark/light/system themes; default port, autosave, project directory, LAN access, runtime management and app information.
- [ ] R8, resource shrinking, lazy loading, minimal animation/dependencies, system fonts, memory/CPU/cache/startup optimization.

## Milestones and exit evidence
- [x] M0 source foundation: Android Gradle project, Compose shell/theme/navigation, CI workflow and smoke-test source added. Build/test verification is still in progress.
- [x] M1 initial implementation: project create/open/rename/delete/search, ZIP import/extract, backup export, metadata and ZIP path safety tests added. Runtime verification and remaining edge cases are still in progress.
- [ ] M2: File manager and project-root-confined file operations; path/symlink tests.
- [ ] M3: Editor, save/autosave and search; persistence and editor-state tests.
- [ ] M4: HTML/CSS/JS preview; refresh, fullscreen, JS toggle and error tests.
- [ ] M5: Localhost server lifecycle, port handling, HTTP and logs tests.
- [ ] M6: LAN/Wi-Fi/hotspot binding and authentication; network integration tests on device.
- [ ] M7: Optional Node runtime feasibility, install lifecycle and execution tests.
- [ ] M8: Optional Python/SQLite feasibility and execution/database tests.
- [ ] M9: Foreground service, recovery, permissions and security tests.
- [ ] M10: APK/RAM/CPU/startup/storage profiling and compatibility tests.
- [ ] M11: Clean release build, integration/regression/security/performance audit and release artifact verification.

## Explicitly out of 1.0 scope
Premium/subscription, advertisements, Git history, DevTools, network inspector, MySQL/PostgreSQL, PHP, React/Vite build system, npm dependency installer, heavy analytics, multiple runtime supervisor.

## Performance targets (targets, not guarantees)
- APK download size <= 15 MB.
- Idle RAM <= 150 MB.
- Cold startup <= 3 seconds.
All values require measurement on a stated device/build configuration; no target is claimed achieved without evidence.

## Evidence log
| Milestone | Commit | CI run | Tests | Status |
|---|---|---|---|---|
| M0 | a0139e99fef4883e6712a9851f274532506f425d | CI runs 44320981426 / 44321172131 (older runs failed before Gradle) | Smoke test added; latest build pending | IN_PROGRESS |
| M1 | 9cdc633f07b0afd7ba28adedf257772b4ef6c168 | CI run 44321190952 queued | ZIP path tests added; not yet executed | IN_PROGRESS |
