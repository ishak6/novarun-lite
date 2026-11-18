# Performance Budget and Measurement

## Targets
- Minified release APK <= 15 MiB.
- Idle RAM <= 150 MiB.
- Cold startup <= 3 seconds.
- Low idle CPU and no unnecessary background polling.
- Optional Node/Python runtimes remain outside the base APK.

## Implemented size controls
- Kotlin + Jetpack Compose; no large image or analytics SDKs.
- R8 minification and resource shrinking enabled for release.
- Node/Python runtime binaries are not bundled.
- Minimal dependencies and system fonts.

## Measurement rules
- Record the exact commit, APK variant, device/API level and test procedure.
- Debug APK size is not compared with the release APK target.
- APK size is measured by CI from the unsigned minified release APK; the QA-signed copy may be slightly larger.
- RAM, CPU and startup targets remain unverified until measured on physical devices and an emulator.
- A CI build success does not prove device performance or LAN behavior.
