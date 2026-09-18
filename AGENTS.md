# Anti-Spoofing Viewer AI Guide

## Context

- Governs code navigation, implementation boundaries, and safety for `android-anti-spoofing-lab` (`ubio-anti-spoofing`).
- The Obsidian wiki at vault-relative `Dev/Project/Company/android-anti-spoofing-lab` is the single source of truth for runtime contracts, hardware coupling, benchmark baselines, and experiment continuity. Resolve the vault through `_meta/routing-tables.md` or `obsidian-wiki-sync`, never a hardcoded file URL.
- **Paired Project**: Anti-spoofing model training and INT8 export belong upstream in `access-liveness-model`; this repository deploys, benchmarks, and validates those artifacts on physical hardware. WebRTC signaling server and operator web belong to `ubio-webrtc`.
- Report to the user in Korean; keep code, identifiers, paths, and commands in English.
- Read [`app/AGENTS.md`](app/AGENTS.md) for package-level code entry points, threading rules, and test boundaries when changing the app module.
- Read [`vision/AGENTS.md`](vision/AGENTS.md) when changing the anti-spoofing engine library.

## Code Map

| Module | Responsibility | First entry point | Module guide |
| --- | --- | --- | --- |
| `vision/` | Anti-spoofing SDK: host contract, crop, preprocess, NNAPI inference, `[1,12]` output | `vision/src/main/java/com/unionbiometrics/vision/VisionSdk.java` | `vision/AGENTS.md` |
| `app/` | Android evaluation runtime, capture, recognition, and WebRTC terminal PoC | `app/src/main/java/com/virditech/ac7000/MainActivity.java` | `app/AGENTS.md` |

## Change Gates

- Preserve camera selection, resolution, timestamp synchronization, calibration, IR LED/LCD control, watchdog behavior, lifecycle cleanup, package identity, and signing unless the request targets them. Consult UBio-N Face Pro when copied device behavior is unclear; do not import unrelated production features or modules.

- **Anti-Spoofing NNAPI No-Fallback Rule**: Never implement silent CPU fallback for a manifest model slot. An NNAPI error during setup/warmup must reject the slot so NPU defects are immediately detected. Recognition has a separate delegate policy in `app/AGENTS.md`; the older PReLU CPU baseline does not define the current recognition model's default.
- **VSI NPU Cache Restriction**: Never enable NNAPI compilation caching (`setCacheDir`/`setModelToken`); the board driver will fail compilation.
- **Camera Teardown Sequencing**: Never close `ImageReader` or preview `Surface` before `CameraDevice.StateCallback.onClosed()` has fired (avoids native `SIGSEGV` in `YuvConverter`).
- **Capture Atomicity**: A capture sample advances the count only when all 5 files (`RGB.bmp`, `cropRGB.bmp`, `IR.bmp`, `cropIR.bmp`, `meta.json`) succeed.
- **Model Input Contract**: Manifest slots support only IR `single_1_input` and RGB+IR `dual_2_input`. Reject RGB-only, paired one-input, five-input, and any additional-input form in both build-time asset validation and runtime loading.
- **Output Dimension Contract**: Anti-spoofing output must match `ClassLabels.values()` in shape and order; the current evaluator requires `[1,12]` and rejects legacy ten-class assets.
- **Secrets & Credentials**: Never commit `FACEME_LICENSE_KEY`, private Maven URLs, keystores, or signaling server credentials.

## Verify

- Use JDK 21 for these commands on the company PC; the default Studio JBR 25 fails Gradle unit-test report setup. The wiki's `technical/build-deployment-requirements` records the verified local JDK selection.
- Compile check: `./gradlew.bat :vision:compileDebugJavaWithJavac` then `./gradlew.bat :app:compileDebugJavaWithJavac`
- JVM Unit Tests: `./gradlew.bat :vision:testDebugUnitTest` then `./gradlew.bat :app:testDebugUnitTest`
- For affected target behavior, use the version-bound checklists in `docs/model-contract.md`, `docs/device-runtime.md`, `docs/capture-contract.md`, `docs/performance-guide.md`, and `docs/webrtc-test.md`.
- Target Device Logcat Filters (when device connected via adb):
  - `adb logcat -s AntiSpoofingClassifier:I MainActivity:I`
  - `adb logcat -s MainActivity:E CameraStream:E *:S`
- Report exact commands and results.
