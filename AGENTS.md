# Project: ubio-anti-spoofing

## Purpose

- Keep this isolated Android test app minimal: it evaluates new RGB/IR anti-spoofing TensorFlow Lite models on UBio-N Face Pro hardware; it is not the production terminal app.
- Retain only the source project's device, camera, calibration, face-detection, and lifecycle behavior required for model evaluation.
- When copied device behavior is unclear, consult the UBio-N Face Pro source project before changing it. Do not import unrelated production features or modules.

## Task-Specific Guidance

- Before changing an area below, read and follow its document. Read every applicable document for cross-area work.
  - Models, manifests, tensors, preprocessing, delegates, or model-result UI: [`docs/agent/model-contract.md`](docs/agent/model-contract.md)
  - Cameras, calibration, FaceMe tracking, lifecycle, hardware controls, or device storage: [`docs/agent/device-runtime.md`](docs/agent/device-runtime.md)
  - Capture collection, quality gating, sample paths, metadata, BMP output, pause/resume, or cancel: [`docs/agent/capture-contract.md`](docs/agent/capture-contract.md)
  - Performance diagnostics, benchmarks, allocation/concurrency optimization, or logcat troubleshooting: [`docs/agent/performance-guide.md`](docs/agent/performance-guide.md)

## Build

- Android `minSdk 30`, `targetSdk 34`, Java 17.
- Configure proprietary FaceMe access with the `UBIO_MAVEN_URL` and `FACEME_LICENSE_KEY` Gradle properties.
- Configure optional platform signing with `UBIO_KEYSTORE_PATH`, `UBIO_KEY_ALIAS`, `UBIO_KEY_PASSWORD`, and `UBIO_STORE_PASSWORD`.

## Change Rules

- Make the smallest change that satisfies the requested model test or diagnostic goal.
- Do not broadly refactor, add production features, or copy more UBio-N Face Pro modules unless explicitly requested.
- Keep model-specific behavior in the model layer and device-specific behavior in the existing camera, calibration, face, and device packages.
- Match the existing Java style and remove only imports or code made unused by the current change.
- Inspect the existing worktree before editing and do not overwrite unrelated user changes.
- Never commit FaceMe licenses, keystores, passwords, credentials, customer data, or secrets.
- Do not add machine-local configuration or paths to tracked files.

## Validation

- After code or build changes, run `./gradlew.bat :app:compileDebugJavaWithJavac`; use a narrower check only when it fully covers the changed behavior.
- Apply validation from every applicable task-specific document above.
- Hardware-dependent changes require manual verification on the target device. If hardware validation cannot be performed, state which checks remain unverified and the resulting risk.
