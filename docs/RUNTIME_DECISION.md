# Optional Runtime Feasibility — M7/M8

## Constraints
The base NovaRun Lite APK target is <= 15 MB. Node.js and Python must not be bundled into the base APK.

## Evaluated options
1. nodejs-mobile Android binaries: the upstream GitHub Releases API currently exposes Node.js 18.20.4 (published 2024-10-07), and its Android ZIP is 57,287,354 bytes for multiple ABIs. Node 18 is end-of-life, so this is not selected as the default runtime.
2. Termux-backed runtimes: Termux package metadata currently exposes nodejs-lts 24.22.2 and supports Python packages. Termux's documented RUN_COMMAND integration allows third-party apps to execute commands, but requires the user to grant com.termux.permission.RUN_COMMAND and set allow-external-apps=true in Termux. This grants NovaRun a powerful command-execution bridge and must be opt-in.
3. SQLite: Android already includes SQLite support; use the platform database API for NovaRun project data rather than shipping a second SQLite runtime.

## Proposed implementation
- Keep the APK free of Node/Python runtime binaries.
- Offer an optional Termux connector for Node.js LTS and Python.
- Require explicit user setup, explicit Android permission grant, and a visible connected/disconnected state.
- Restrict NovaRun-issued commands to an app-owned allowlist and project working directory; never accept arbitrary command strings from imported project files as control commands.
- Show the exact command and target project before installing or removing a runtime.
- Use native Android SQLite for app/project metadata.

## Approval gate
Termux integration is NOT implemented or enabled. It changes the security boundary because NovaRun can ask Termux to execute commands. User approval is required before adding the RUN_COMMAND permission and connector. Until approved, M7 Node.js and M8 Python runtime remain BLOCKED; do not claim them complete. Native SQLite work can proceed independently.
