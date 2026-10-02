# Neha 0.6 — Agent-First UI Patch

This patch changes the user experience without changing the security boundary.

## What changed

- Main screen is now agent-first: talk to Neha is the primary action.
- The main screen keeps only the essential actions visible: Talk, Scan, Current Status, Security Center.
- Advanced tools are grouped under **More security tools** instead of being shown as a long list.
- Agent Neha has four quick actions: current conditions, full scan, recent activity, and protection setup.
- Voice and text use the same command path.
- Saying phrases such as `scan my device` starts the full deterministic SentinelDroid collection workflow.
- Scan progress is shown in the conversation and the final result is spoken and displayed.
- Current conditions are summarized from TelemetryHealth, stored scan state, alerts, and evidence count.
- Recent activity is summarized from the EvidenceVault.

## Security boundary

Natural-language input selects safe user-facing operations only. It does not authorize intervention, grant permissions, uninstall apps, or change SentinelDroid policy.

Application-controlled evidence remains data. Neha explains collected evidence; Android and the deterministic security policy remain authoritative.

## Verification

Static/boundary verification: PASS.
Protected files are byte-identical to the FIXED5 baseline.

Android Studio/Gradle compile was not executed in this Linux environment because an Android SDK and usable Gradle wrapper JAR are not present here. The project must still be assembled on the user's Windows/Android Studio toolchain before installation.
