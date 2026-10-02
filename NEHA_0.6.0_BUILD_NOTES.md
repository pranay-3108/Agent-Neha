# Neha 0.6.0 FIXED3 build notes

This package contains build-plumbing fixes only in non-protected files.

## Fixed

- `tools/build.ps1` now captures `java -version` without PowerShell turning stderr into a NativeCommandError.
- Android SDK discovery now checks environment variables, `local.properties`, common Android Studio locations, `adb`, `sdkmanager`, and common filesystem-drive locations.
- SDK discovery requires API 36 (`platforms/android-36/android.jar`) before starting Gradle.
- `gradlew.bat` forwards command-line arguments.
- `gradlew` forwards command-line arguments.
- `build.bat` forwards command-line arguments.
- `assembleSniper`, `assembleRelease`, `assembleDebug`, and `assemble` are mapped to the app module when invoked from the project root.
- Static verification now understands that the protected sniper files intentionally contain Device Admin/intervention code and does not incorrectly report those files as base-build violations.

## Protected files

The 11 protected files from the external contributor were not modified.

## Build verification status

Static verification is included and passes in this packaging environment.
The Android/Kotlin Gradle build itself requires the Windows Android SDK/JDK environment and is not executed inside this Linux packaging environment.
