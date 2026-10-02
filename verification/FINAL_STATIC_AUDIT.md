# Neha / SentinelDroid 0.6.0 FIXED5 — Verification Record

Date: 2026-09-22

## Static checks

- Neha 0.6 static/boundary verification: PASS
- Protected-file SHA-256 verification: PASS
- Manifest XML parse: PASS
- Manifest local-class reference check: PASS
- Build configuration check: PASS
- Build helper check: PASS
- Gradle wrapper properties check: PASS
- Source placeholder/incomplete-marker check: PASS
- Pure Kotlin seam compile check: PASS

## Changes in FIXED5

1. `tools/build.ps1`
   - Keep the PowerShell-safe Java version capture.
   - Android SDK discovery now requires the API 36 platform but does not require one exact Build Tools patch directory such as `36.0.0`.
   - Exports the discovered SDK through `ANDROID_SDK_ROOT` and `ANDROID_HOME` for the Gradle process.
   - Retains forwarding for `assembleSniper`, `assembleRelease`, `assembleDebug`, and other Gradle arguments.

2. `gradle/wrapper/gradle-wrapper.properties`
   - Added the official Gradle 9.6.0 binary distribution SHA-256 checksum.

## Protected files

All 11 protected files remain byte-for-byte unchanged.

## Full Android build status

Not executed in this packaging environment because the Android SDK and Windows PowerShell toolchain are unavailable here. Therefore no Android/Kotlin full-build PASS is claimed.
