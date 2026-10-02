# Neha / SentinelDroid 0.6.0 Fixed Patch

This package is an incremental repair of the previously packaged Neha 0.6.0 project.

## Repairs

1. All capability reconciliation call sites now enter `CapabilityMonitor.observeAndReconcile(...)`.
2. `ACTION_PACKAGE_REPLACED` and replacing `ACTION_PACKAGE_ADDED` explicitly use `PACKAGE_REPLACE`.
3. A normal package installation explicitly uses `PACKAGE_ADD`.
4. The old public `reconcile`, `recordInstalledPackage`, and `initializeBaseline` bypass methods were removed so future callers cannot accidentally bypass the mandatory reconciliation gate.
5. Baseline cache writes still occur only after the signed vault record is sealed.
6. `observeAndReconcile` is synchronized to reduce same-process race conditions between background/package events.

## Verification status

Static source/security verification is included under `verification/`. Android SDK/Gradle compilation was not run in the packaging environment.
