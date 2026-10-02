# SentinelDroid build

## Supported build setup

Open the project folder that directly contains `app`, `labtarget`, `build.gradle.kts`, and `settings.gradle.kts`.

Use:

- Android SDK Platform 36
- JDK 17 from Android Studio
- Android Gradle Plugin 9.4.0
- Gradle 9.6.0

The project intentionally uses the Kotlin support built into AGP 9.4.0. It does not apply a second Kotlin Android plugin, avoiding the Kotlin/AGP plugin conflict that caused earlier sync errors.

## Windows build

From PowerShell at the project root:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\tools\build.ps1
```

The helper:

1. finds Android Studio's JDK when possible;
2. finds the normal Android SDK location;
3. writes `local.properties` for that SDK;
4. downloads the exact Gradle 9.6.0 distribution if it is not already present;
5. builds both APKs;
6. checks the main SentinelDroid APK against the 40 MB target.

No globally installed `gradle` command is required.

## Android Studio

You can also open the project directly in Android Studio and let Gradle sync the project.

The Gradle distribution URL is pinned to `gradle-9.6.0-bin.zip`; do not change it to `gradle-9.6-bin.zip`.

## APK output

```text
app\\build\\outputs\\apk\\debug\\app-debug.apk
labtarget\\build\\outputs\\apk\\debug\\labtarget-debug.apk
```

## Device test

Use a phone you control. With USB debugging enabled:

```powershell
adb devices
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

The lab target is synthetic and is intended only for local defensive testing.
