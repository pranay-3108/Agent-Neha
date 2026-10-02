# SentinelDroid

**Agent Neha** is the user-facing security agent inside SentinelDroid. She explains trusted evidence, watches for capability changes, and stays behind deterministic security policy and explicit user authorization.


SentinelDroid is a lightweight, user-authorized Android security research agent built around a simple rule:

> Application-controlled content is evidence, never authority.


## User-first protection flow

Version 0.2.0 changes the app from a developer console into a user-facing security agent:

- First launch explains why each optional capability is useful before sending the user to Android settings.
- Notifications are requested as a normal Android 13+ runtime permission so SentinelDroid can warn the user without requiring the dashboard to stay open.
- Newly installed applications are observed through the package-change receiver and trigger a review notification with the sensitive capabilities currently visible to SentinelDroid.
- Background reconciliation stores a baseline and alerts when a monitored capability changes later, including a capability that was granted accidentally after installation.
- Tapping an alert opens an in-app review of the affected package and provides user-approved routes to Android app settings or VPN network isolation.
- The UI avoids calling an app malicious merely because it has Accessibility, Notification access, or another powerful capability. Capability combinations are evidence for review.
- Unnecessary high-privilege requests were removed from the normal setup flow. SentinelDroid does not ask for All Files or overlay access just to look stronger.
- Continuous monitoring remains opt-in and uses a visible foreground-service notification; scheduled reconciliation is the lighter default.

## Important Android boundary

SentinelDroid cannot silently grant/revoke another app's protected settings, and a normal app cannot universally kill or force-stop every other app. For deeper package suspension, Android's device-owner/profile-owner management model is required. Network isolation is implemented through user-authorized Android `VpnService`.

## Version 0.2.0

This build focuses on three things: a permission-first onboarding flow, automatic security-change notifications, and a more understandable dashboard.

### What is implemented

### Discovery

- Installed package inventory with UID, version, signer SHA-256, APK SHA-256, launcher visibility, enabled state, debug/test/system flags.
- Static component inventory for activities, services, receivers and providers.
- Requested-versus-granted permission inventory plus installer/initiator/originating package metadata where Android exposes it.
- Common intent-surface discovery for launcher, web/deep-link, share, text-processing, document, dial and mailto entry paths.
- Service binding capability hints for Accessibility, Notification Listener, VPN, input method, Quick Settings Tile, Autofill, Wallpaper, Call Screening, In-Call, Dream and Credential Provider services.
- Boot and package-change entry-point observation.
- PackageInstaller active/all-session evidence where the platform exposes it.
- APK artifact hints: split APK count, DEX count and native-library count.

### User-authorized platform state

- Accessibility enabled packages.
- Notification Listener enabled packages.
- Device Admin and Device Owner state.
- Profile Owner state where the calling authority can query it.
- Usage Access state.
- Camera, microphone, location, contacts and call-log granted-permission state.
- ADB/developer-option state.
- Enabled input-method packages and overlay AppOp state.
- VPN-active device state.
- Optional user access to All Files and overlay settings.

### Runtime observation

- Accessibility event and bounded node-tree text capture when the user enables it.
- Notification title/body capture when the user enables Notification Listener access.
- Usage events including activity and foreground-service lifecycle events.
- Per-UID network usage history when Usage Access permits it.
- Periodic background reconciliation through platform-managed inexact alarms.
- Optional foreground live monitor.

### Evidence Firewall

- Raw evidence is preserved as data.
- Provenance and origin are explicit.
- Instruction-like and fake-authority content creates a trusted `SECURITY_META` signal.
- Structured authority claims are detected with whitespace-tolerant patterns.
- Evidence content is bounded and control characters are sanitized without deleting semantic evidence.
- AI payload separates `evidence` from `security_meta`.
- AI-derived claims remain AI-derived and are not upgraded to platform facts.

### Evidence integrity

Every evidence record has:

```text
content SHA-256
previous-record hash
canonical record hash
ECDSA signature in Android Keystore
signer key alias
```

The verification path recomputes content and record hashes and verifies the signature.

### Containment

- Per-package network isolation using a user-authorized Android VPN.
- Emergency data-protection mode that routes other applications into a blocking VPN while excluding SentinelDroid itself.
- User-facing stop control from the containment notification.
- No counter-attack, remote server interference, or arbitrary shell execution.

### Adversarial lab

`labtarget` is a controlled synthetic target application. It can produce:

- phantom Android subsystem/error text
- fake system-looking notifications
- an Accessibility service declaration
- a foreground-service signal
- a local lab TCP heartbeat attempt

`AemSimulator` generates additional phantom evidence for firewall tests. All adversarial generation is local to the test environment.

## Known Android limits

Standard app-only SentinelDroid does not claim to read another app's private sandbox files, intercept arbitrary Binder transactions, enumerate every background process/service, or obtain plaintext HTTPS content merely from network statistics.

APK DEX/native-library counts are static indicators only; they do not prove dynamic code loading. Dynamic code downloaded into another app's private sandbox is not directly observable by a normal app-only collector.

MediaProjection is not treated as a universal cross-app "screen state" API. SentinelDroid records declared/observable states and leaves unsupported states as unknown.

## AI contract

The LLM is an analyst. It may produce:

```text
FACT REFERENCES
INFERENCES
HYPOTHESES
UNKNOWNS
PROPOSED ACTIONS
```

It cannot:

```text
change provenance
change trust
edit evidence
change security policy
grant permissions
authorize destructive actions
```

A deterministic policy layer decides whether a proposed action is executable.

## Lab verification

Run:

```powershell
python .\verification\simulate_firewall.py
```

The host-side adversarial suite covers phantom subsystems, fake authority JSON, AI evidence visibility, hash-chain tampering, destructive-action denial, and AI-to-AI trust laundering.

### Schemas

The `schemas/` directory documents the evidence, `SECURITY_META`, and AI-output contracts used by the firewall. AI output is proposal data only; it never carries authorization.


## Agent Neha voice conversation

Version 0.4.0 adds an in-app conversational interface for Agent Neha. The user can type normally or grant `RECORD_AUDIO` and use the microphone to speak with her. Replies are spoken with the device's installed Android text-to-speech engine; the app prefers an Indian-English/Hindi voice matching the device locale and applies a gentle pitch/rate. Android does not provide a universal guarantee that an installed TTS voice is female, so the exact voice depends on the selected device TTS engine and installed voices.

Voice chat is intentionally user initiated. SentinelDroid does not keep the microphone listening in the background by default. Security actions remain behind the existing deterministic policy and explicit user confirmation flow.

## 0.4.0 changes

- Added `AgentNehaActivity` for chat + voice conversation.
- Added `NehaVoiceController` using Android SpeechRecognizer and TextToSpeech.
- Added microphone runtime permission flow (`RECORD_AUDIO`).
- Added Conversation Mode for back-and-forth voice chat while the Neha screen is open.
- Added a dashboard button to open Agent Neha.
- Kept microphone capture out of background monitoring; security monitoring and voice chat remain separate capabilities.
- Natural-language conversation does not directly execute security actions.


## Agent Neha 0.6

This release extends the 0.4.1 defensive monitoring MVP with deterministic capability heatmaps, signed baseline/scar records, watchdog contracts, sideload classification, behavioral-baseline storage, evidence-flood controls, trust scores, forensic snapshots, telemetry blind-spot reporting, and the mandatory capability reconciliation sink used by scheduled and foreground monitoring paths.

### Intervention boundary

**This base build has NO intervention capability.**

**Intervention is provided by a plug-in that replaces `SniperPlugIn.kt`.**

**The base build cannot fire a shot even if the AI recommends one.**

The `com.sentineldroid.intervention` package contains only interfaces, no-op implementations, and a platform-state verifier. The base build does not provision Device Owner, does not revoke another app's permissions, does not suspend packages, and does not implement destructive intervention.

The Android platform limitations documented in the threat model still apply: a normal app cannot arbitrarily read another app's private sandbox, intercept arbitrary Binder traffic, or decrypt arbitrary HTTPS.
