# Anti-Spoofing Viewer AI Guide

## Context

- Governs code navigation, implementation boundaries, and safety for `android-anti-spoofing-lab` (`ubio-anti-spoofing`).
- **Project wiki (company-maintained)**: The `ubio-vision-sdk` folder of the company GitLab repository `ai/ubio-vision/wiki` is the single source of truth for runtime contracts, hardware coupling, benchmark baselines, and experiment continuity. Use a pulled checkout of that repository (it may be embedded in a host Vault as a submodule), never a hardcoded file URL; follow its root `AGENTS.md` and update owning pages through reviewed commits or merge requests.
- **Without the wiki**: Code and these `AGENTS.md` guides are authoritative; an unpulled wiki snapshot is read-only background that may be stale and yields to code. Report device results with APK or code revision, device, scenario, and observed result in commit messages or merge requests for wiki intake. Wiki page names cited in guides (e.g. `technical/build-deployment-requirements`) are relative to the project folder or snapshot root.
- **Paired Project**: Anti-spoofing model training and INT8 export belong upstream in `access-liveness-model`; this repository deploys, benchmarks, and validates those artifacts on physical hardware. WebRTC signaling server and operator web belong to `ubio-webrtc`.
- Read [`app/AGENTS.md`](app/AGENTS.md) for package-level code entry points, threading rules, and test boundaries when changing the app module.
- Read [`vision/AGENTS.md`](vision/AGENTS.md) when changing the anti-spoofing engine library.

## Code Map

| Module | Responsibility | First entry point | Module guide |
| --- | --- | --- | --- |
| `vision/` | Anti-spoofing SDK: host contract, crop, preprocess, NNAPI inference, `[1,12]` output | `vision/src/main/java/com/unionbiometrics/vision/AntiSpoofingEngine.java` | `vision/AGENTS.md` |
| `app/` | Android evaluation runtime, capture, recognition, and WebRTC terminal PoC | `app/src/main/java/com/virditech/ac7000/MainActivity.java` | `app/AGENTS.md` |

## Change Gates

- Preserve camera selection, resolution, timestamp synchronization, calibration, IR LED/LCD control, watchdog behavior, lifecycle cleanup, package identity, and signing unless the request targets them. Consult UBio-N Face Pro when copied device behavior is unclear; do not import unrelated production features or modules.

- **Anti-Spoofing NNAPI No-Fallback Rule**: Never implement silent CPU fallback for a manifest model slot. An NNAPI error during setup/warmup must reject the slot so NPU defects are immediately detected. Recognition has a separate delegate policy in `app/AGENTS.md`; the older PReLU CPU baseline does not define the current recognition model's default.
- **VSI NPU Cache Restriction**: Never enable NNAPI compilation caching (`setCacheDir`/`setModelToken`); the board driver will fail compilation.
- **Camera Teardown Sequencing**: Never close `ImageReader` or preview `Surface` before `CameraDevice.StateCallback.onClosed()` has fired (avoids native `SIGSEGV` in `YuvConverter`).
- **Capture Atomicity**: A capture sample advances the count only when all 5 files (`RGB.bmp`, `cropRGB.bmp`, `IR.bmp`, `cropIR.bmp`, `meta.json`) succeed.
- **Model Input Contract**: Manifest slots support only IR `single_1_input` and RGB+IR `dual_2_input`. Reject RGB-only, paired one-input, five-input, and any additional-input form in both build-time asset validation and runtime loading.
- **Output Dimension Contract**: Anti-spoofing output must match `VisionConstants.CLASS_LABELS` in shape and order; the current evaluator requires `[1,12]` and rejects legacy ten-class assets.
- **Secrets & Credentials**: Never commit `FACEME_LICENSE_KEY`, private Maven URLs, keystores, or signaling server credentials.

## Verify

- Use JDK 21 for these commands (verified: Zulu 21.0.11 via process-local `JAVA_HOME`, or a Gradle-provisioned JDK 21 under `%USERPROFILE%\.gradle\jdks\`). The default Android Studio JBR 25 fails Gradle unit-test report setup (`Type T not present`).
- Compile check: `./gradlew.bat :vision:compileDebugJavaWithJavac` then `./gradlew.bat :app:compileDebugJavaWithJavac`
- JVM Unit Tests: `./gradlew.bat :vision:testDebugUnitTest` then `./gradlew.bat :app:testDebugUnitTest`
- When the wiki or snapshot is available, use its execution checklists (model deployment, camera/capture, performance/recognition, WebRTC) for affected target behavior.
- Target Device Logcat Filters (when device connected via adb):
  - `adb logcat -s AntiSpoofingClassifier:I MainActivity:I`
  - `adb logcat -s MainActivity:E CameraStream:E *:S`
- Report exact commands and results.
