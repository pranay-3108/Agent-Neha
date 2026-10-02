from pathlib import Path

root = Path(__file__).resolve().parents[1]
app = root / "app/src/main"

forbidden = [
    "setPermissionGrantState(",
    "DeviceAdminReceiver",
]

forbidden_hits = []
for p in app.rglob("*.kt"):
    text = p.read_text(errors="ignore")
    for token in forbidden:
        if token in text:
            forbidden_hits.append((str(p), token))
assert not forbidden_hits, forbidden_hits

manifest = (app / "AndroidManifest.xml").read_text()
assert "SentinelApplication" in manifest
assert "DeviceAdminReceiver" not in manifest

build = (root / "app/build.gradle.kts").read_text()
assert 'versionName = "0.6.0"' in build
assert 'SNIPER_ENABLED' in build
assert 'create("sniper")' in build

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

intervention = app / "java/com/sentineldroid/intervention"
assert (intervention / "InterventionPlanner.kt").exists()
assert (intervention / "InterventionExecutor.kt").exists()
assert (intervention / "InterventionVerifier.kt").exists()
assert (intervention / "InterventionRegistry.kt").exists()

print("NEHA 0.6 fixed static/security verification: PASS")
