# Anti-Spoofing Viewer AI Guide

## Context

- Governs code navigation, implementation boundaries, and safety for `android-anti-spoofing-lab` (`ubio-anti-spoofing`).
- The Obsidian wiki at vault-relative `Dev/Project/Company/android-anti-spoofing-lab` is the single source of truth for runtime contracts, hardware coupling, benchmark baselines, and experiment continuity. Resolve the vault through `_meta/routing-tables.md` or `obsidian-wiki-sync`, never a hardcoded file URL.
- **Paired Project**: Anti-spoofing model training and INT8 export belong upstream in `access-liveness-model`; this repository deploys, benchmarks, and validates those artifacts on physical hardware. WebRTC signaling server and operator web belong to `ubio-webrtc`.
- Before multi-step or resumed implementation, ground the wiki context against live code, propose `step → verify` checkpoints, and confirm them before editing.
- Report to the user in Korean; keep code, identifiers, paths, and commands in English.
- Read [`app/AGENTS.md`](app/AGENTS.md) for package-level code entry points, threading rules, and test boundaries when changing the app module.

## Code Map

| Domain / Package | Responsibility | First entry point | Module / Spec guide |
| --- | --- | --- | --- |
| **Model & Inference** (`model/`) | TFLite loader, manifest slots, tensor mapping, NNAPI, motion gate | `app/src/main/java/com/virditech/ac7000/model/ModelSlotClassifier.java` | [`docs/model-contract.md`](docs/model-contract.md) |
| **Face Detection** (`face/`) | FaceMe SDK 7.8.2, MediaPipe BlazeFace, NPU warmup, quality gate | `app/src/main/java/com/virditech/ac7000/face/FaceDetector.java` | [`docs/device-runtime.md`](docs/device-runtime.md) |
| **Camera & Teardown** (`camera/`, `calibration/`) | Dual Camera2, 150ms pairing, `CalibConfig.dat`, `onClosed` teardown | `app/src/main/java/com/virditech/ac7000/camera/DualCameraController.java` | [`docs/device-runtime.md`](docs/device-runtime.md) |
| **Dataset Capture** (`capture/`) | 100-sample raw dataset, ATTACK false-live, BMP writing, `meta.json` | `app/src/main/java/com/virditech/ac7000/capture/CaptureStorage.java` | [`docs/capture-contract.md`](docs/capture-contract.md) |
| **Face Recognition** (`recognition/`) | MobileFaceNet 1:N, 5-point alignment, independent correctness and delegate validation | `app/src/main/java/com/virditech/ac7000/recognition/FaceRecognitionManager.java` | [`docs/performance-guide.md`](docs/performance-guide.md) |
| **WebRTC PoC** (`call/`, `api/call/`) | Signaling WebSocket, Camera2 handoff, PeerConnection, audio routing | `app/src/main/java/com/virditech/ac7000/api/call/SignalingClient.java` | [`docs/webrtc-test.md`](docs/webrtc-test.md) |
| **Device & Sysfs** (`device/`) | PI6008K IR AE Full/Center, IR LED/LCD sysfs, lighting detector, CSV logger, daemon watchdog | `app/src/main/java/com/virditech/ac7000/device/HardwareControls.java` | `app/AGENTS.md` |
| **UI & Overlay** (`ui/`) | Java-based layout, mirrored Canvas bounding boxes, Auth Mode cards | `app/src/main/java/com/virditech/ac7000/ui/MainScreenView.java` | `app/AGENTS.md` |
| **Performance** (`performance/`) | P50/P95 rolling latency windows, memory & GC diagnostics | `app/src/main/java/com/virditech/ac7000/performance/LatencyWindow.java` | [`docs/performance-guide.md`](docs/performance-guide.md) |

## Change Gates

- **Anti-Spoofing NNAPI No-Fallback Rule**: Never implement silent CPU fallback for a manifest model slot. An NNAPI error during setup/warmup must reject the slot so NPU defects are immediately detected. (MobileFaceNet experiment defaults explicitly to CPU because its PReLU graph is slower on this board).
- **VSI NPU Cache Restriction**: Never enable NNAPI compilation caching (`setCacheDir`/`setModelToken`); the board driver will fail compilation.
- **Camera Teardown Sequencing**: Never close `ImageReader` or preview `Surface` before `CameraDevice.StateCallback.onClosed()` has fired (avoids native `SIGSEGV` in `YuvConverter`).
- **Capture Atomicity**: A capture sample advances the count only when all 5 files (`RGB.bmp`, `cropRGB.bmp`, `IR.bmp`, `cropIR.bmp`, `meta.json`) succeed.
- **Output Dimension Contract**: Anti-spoofing models must strictly output matching the fixed class order (`[1, 10]` legacy or `[1, 12]` dental-mask active contract).
- **Secrets & Credentials**: Never commit `FACEME_LICENSE_KEY`, private Maven URLs, keystores, or signaling server credentials.

## Verify

- Compile check: `./gradlew.bat :app:compileDebugJavaWithJavac`
- JVM Unit Tests: `./gradlew.bat :app:testDebugUnitTest`
- Target Device Logcat Filters (when device connected via adb):
  - `adb logcat -s AntiSpoofingClassifier:I MainActivity:I`
  - `adb logcat -s MainActivity:E CameraStream:E *:S`
- Report exact commands and results. Never commit or push: the user manages all git commits and pushes manually.
