# NovaRun Lite

A lightweight native Android local development environment.

## Project principles
- Native Android UI with Jetpack Compose.
- Keep the base APK small; optional runtimes are not bundled.
- Project-scoped file access and defensive ZIP extraction.
- Localhost and explicitly controlled LAN hosting.
- No ads, subscriptions, analytics, or unnecessary background network activity.

## Milestones
See [docs/DEVELOPMENT_CHECKLIST.md](docs/DEVELOPMENT_CHECKLIST.md).

## Build
Open this repository in Android Studio or run:
`gradle :app:assembleDebug`

Android SDK platform 35 and JDK 17 are required.

## Current status
M0 foundation is being established. Features are not considered complete until implementation and tests are recorded.
