from pathlib import Path

root = Path(__file__).resolve().parents[1]
app = root / "app/src/main"
intervention = app / "java/com/sentineldroid/intervention"

protected = {
    app / "res/xml/sentinel_device_admin.xml",
    app / "java/com/sentineldroid/intervention/SentinelDeviceAdminReceiver.kt",
    app / "java/com/sentineldroid/intervention/KeystoneAnalyzer.kt",
    app / "java/com/sentineldroid/intervention/PrecisionIntervention.kt",
    app / "java/com/sentineldroid/intervention/ShotBudget.kt",
    app / "java/com/sentineldroid/intervention/AutonomousDecisionRecord.kt",
    app / "java/com/sentineldroid/intervention/ResponseLadder.kt",
    app / "java/com/sentineldroid/intervention/AutoRevertScheduler.kt",
    app / "java/com/sentineldroid/intervention/RevertReceiver.kt",
    app / "java/com/sentineldroid/SniperPlugIn.kt",
    app / "AndroidManifest.xml",
}

# Base-source invariant: protected sniper files are intentionally exempt.
forbidden = [
    "setPermissionGrantState(",
    "DeviceAdminReceiver",
]

forbidden_hits = []
for p in app.rglob("*.kt"):
    if p in protected:
        continue
    text = p.read_text(errors="ignore")
    for token in forbidden:
        if token in text:
            forbidden_hits.append((str(p), token))
assert not forbidden_hits, forbidden_hits

manifest = (app / "AndroidManifest.xml").read_text()
assert "SentinelApplication" in manifest
assert ".intervention.SentinelDeviceAdminReceiver" in manifest
assert ".intervention.RevertReceiver" in manifest

build = (root / "app/build.gradle.kts").read_text()
assert 'versionName = "0.6.0"' in build
assert 'buildConfigField("boolean", "SNIPER_ENABLED", "false")' in build
assert 'create("sniper")' in build

build_script = (root / "tools/build.ps1").read_text()
assert '$javaRaw = cmd.exe /c "java -version 2>&1"' in build_script
assert '$javaVersion = ($javaRaw | Select-Object -First 1).ToString().Trim()' in build_script
assert 'Find-AndroidSdk' in build_script
assert 'gradlew.bat' in build_script or ':app:assembleDebug' in build_script
assert "'assembleSniper'" in build_script
assert ':app:assembleSniper' in build_script

gradlew_bat = (root / "gradlew.bat").read_text()
assert '%*' in gradlew_bat

gradlew_sh = (root / "gradlew").read_text()
assert '"$@"' in gradlew_sh
assert 'buildConfigField("boolean", "SNIPER_ENABLED", "true")' in build

monitor = (app / "java/com/sentineldroid/CapabilityMonitor.kt").read_text()
assert 'fun observeAndReconcile(' in monitor
assert '@Synchronized' in monitor
assert 'writeBaseline(context, pkg, current, now, source)' in monitor

package_receiver = (app / "java/com/sentineldroid/PackageChangeReceiver.kt").read_text()
assert 'CapabilityMonitor.observeAndReconcile(' in package_receiver
assert '"PACKAGE_REPLACE"' in package_receiver
assert '"PACKAGE_ADD"' in package_receiver

for name in [
    "TrustScore.kt",
    "WatchdogRegistry.kt",
    "BehavioralBaseline.kt",
    "ForensicSnapshot.kt",
    "AuthorityPolicy.kt",
    "InstallSourceClassifier.kt",
    "ContainmentVerification.kt",
    "TelemetryHealth.kt",
    "PassiveNetworkObservation.kt",
    "SelfDefense.kt",
    "SentinelApplication.kt",
]:
    assert (app / "java/com/sentineldroid" / name).exists(), name

for name in [
    "InterventionPlanner.kt",
    "InterventionExecutor.kt",
    "InterventionVerifier.kt",
    "InterventionRegistry.kt",
    "SentinelDeviceAdminReceiver.kt",
    "KeystoneAnalyzer.kt",
    "PrecisionIntervention.kt",
    "ShotBudget.kt",
    "AutonomousDecisionRecord.kt",
    "ResponseLadder.kt",
    "AutoRevertScheduler.kt",
    "RevertReceiver.kt",
]:
    assert (intervention / name).exists(), name

print("NEHA 0.6 STATIC/BOUNDARY VERIFICATION: PASS")
