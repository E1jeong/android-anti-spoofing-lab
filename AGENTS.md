# Project: ubio-anti-spoofing

## Purpose

This repository is a minimal Android test application for evaluating newly developed RGB/IR anti-spoofing TensorFlow Lite models on UBio-N Face Pro hardware. It is an isolated model-development and verification tool, not the production terminal application.

Only the device, camera, calibration, face-detection, and lifecycle behavior required for model evaluation was extracted from the UBio-N Face Pro project. Keep this repository minimal. When copied device behavior is unclear, consult the source project before changing it; do not import unrelated production features or modules.

## Task-Specific Guidance

Read the relevant document before changing that area. If a task crosses areas, read every applicable document.

- Models, manifests, tensors, preprocessing, delegates, or model-result UI: [`docs/agent/model-contract.md`](docs/agent/model-contract.md)
- Cameras, calibration, FaceMe tracking, lifecycle, hardware controls, or device storage access: [`docs/agent/device-runtime.md`](docs/agent/device-runtime.md)
- Capture collection, quality gating, sample paths, metadata, BMP output, pause/resume, or cancel: [`docs/agent/capture-contract.md`](docs/agent/capture-contract.md)
- Performance diagnostics, benchmarks, allocation/concurrency optimization, or logcat troubleshooting: [`docs/agent/performance-guide.md`](docs/agent/performance-guide.md)

## Core Runtime Contract

- Capture RGB and IR frames without blocking camera callbacks; match frames within `MAX_PAIR_DELTA_NS` (150ms).
- Detect the largest RGB face independently of model inference, map it to IR with device calibration, and keep displayed overlays aligned with mirrored previews.
- Load all manifest slots and the FaceMe quality detector during startup, but run only the active slot per frame.
- Display six-class output in the fixed order `LIVE`, `PRINT`, `PICTURE`, `MASK`, `DISPLAY`, `PMASK`.
- Collect exactly 100 valid samples per capture session. Only `live` uses FaceMe quality gating; cancel deletes the current subject directory.
- Preserve camera selection, resolution, timestamp synchronization, calibration mapping, IR LED control, watchdog behavior, lifecycle cleanup, package identity, and signing behavior unless the task specifically targets one of them.

## Build

- Android `minSdk 30`, `targetSdk 34`, Java 17.
- Proprietary FaceMe access and licensing require the configured `UBIO_MAVEN_URL` and `FACEME_LICENSE_KEY` Gradle properties.
- Optional platform signing uses `UBIO_KEYSTORE_PATH`, `UBIO_KEY_ALIAS`, `UBIO_KEY_PASSWORD`, and `UBIO_STORE_PASSWORD`.

Default compile validation:

```powershell
./gradlew.bat :app:compileDebugJavaWithJavac
```

## Change Rules

- Make the smallest change that satisfies the requested model test or diagnostic goal.
- Do not perform broad refactors, add production features, or copy additional UBio-N Face Pro modules unless explicitly requested.
- Keep model-specific behavior in the model layer and device-specific behavior in the existing camera, calibration, face, and device packages.
- Match the existing Java style and remove only imports or code made unused by the current change.
- Inspect the existing worktree before editing and do not overwrite unrelated user changes.
- Never commit FaceMe licenses, keystores, passwords, credentials, customer data, or other secrets.
- Do not add machine-local configuration or paths to tracked files.

## Validation

- Run the default compile validation after code or build changes. Use a narrower check only when it fully covers the changed behavior.
- Apply the area-specific validation in the routed documents above.
- Hardware-dependent changes require manual verification on the target device. If hardware validation cannot be performed, state which checks remain unverified and the resulting risk.
