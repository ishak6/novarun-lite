# Security Model and Known Limitations

## Implemented controls
- Project data is stored in app-private internal storage.
- ZIP extraction rejects absolute paths, traversal segments, duplicate paths, more than 5,000 entries, and more than 100 MiB of expanded data. Extraction is staged in a temporary directory and committed only after success.
- Project file operations resolve canonical paths under the project root and reject symbolic links.
- Editor reads and writes UTF-8 files up to 2 MiB; saves use a temporary file and replace operation.
- Preview uses a virtual project origin, disables WebView file/content access, blocks non-project hosts, and exposes only project files through a path-confined stream.
- Localhost hosting binds to 127.0.0.1. LAN mode binds only to a detected Wi-Fi/hotspot private IPv4 address and requires a per-session bearer token.
- LAN token comparison uses constant-time digest comparison. The token is encrypted at rest with AES-GCM using an Android Keystore key.
- Server service is non-exported, foreground-notified, and uses START_REDELIVER_INTENT for process-recreation recovery.

## Explicit limitations
- LAN transport currently uses HTTP, not TLS. Bearer tokens and project contents are not encrypted in transit. Use LAN mode only on a trusted private Wi-Fi/hotspot network. Do not use it on public Wi-Fi.
- START_REDELIVER_INTENT does not guarantee restart after the user force-stops the app, reboots the device, or the OS permanently denies service recreation.
- Android 17 (API 37) enforces ACCESS_LOCAL_NETWORK for apps targeting API 37 or higher. This app currently targets API 35; update the permission flow before raising targetSdk to 37.
- Termux runtime integration is not enabled. See RUNTIME_DECISION.md; it requires explicit approval because RUN_COMMAND grants a command-execution bridge.
- Production release signing requires a private signing key controlled by the owner. CI's ephemeral debug key is only for QA installation.

## Review checklist
- [ ] Device-level ZIP traversal and malformed archive testing.
- [ ] Device-level WebView navigation and external request blocking.
- [ ] LAN authentication and token secrecy on a trusted test network.
- [ ] TLS alternative decision and implementation.
- [ ] Foreground service stop/restart and force-stop behavior.
- [ ] Android 17 local network permission before targetSdk 37.
