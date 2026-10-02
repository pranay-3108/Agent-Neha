# SentinelDroid 0.4.1 build fixes

This build addresses the Kotlin errors visible in the Android Studio Build Output from the 0.4.0 package.

## Fixed

- CapabilityMonitor: explicit null checks before calling `lowercase()` on nullable capability-state values.
- MainActivity: corrected `onNewIntent` override to use Android's non-null `Intent` parameter.
- MainActivity: added a String-action overload for opening Android Settings actions.
- MainActivity: removed the now-unnecessary null check for `onNewIntent`.
- PackageCollector: Android `PackageInfo.receivers` contains `ActivityInfo[]`; the invalid `ReceiverInfo` type was removed and receiver formatting now uses `ActivityInfo`.
- SentinelNotificationListener: corrected `onNotificationPosted` to use Android's non-null `StatusBarNotification` parameter.
- gradle.properties: removed deprecated `android.useAndroidX=false`.

## Build on Windows

Use Android Studio with SDK 36 and JDK 17. From the project root:

```powershell
.\build.bat
```

The build helper downloads Gradle 9.6.0 when it is not already present and builds both the app and lab target.

## Verification boundary

The host environment used to prepare this archive does not include the Android SDK/Gradle Android toolchain, so final Android compilation must still be run on the Windows development machine.
