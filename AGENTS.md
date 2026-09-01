# Anti-Spoofing Viewer AI Guide

## Start Here

- This is a navigation aid, not a history archive: help the AI locate the runtime flow, source entry points, and authoritative knowledge before working.
- The Obsidian wiki `Dev/Project/Company/android-anti-spoofing-lab` is the source of truth for runtime contracts, hardware coupling, benchmark baselines, and experiment continuity. Resolve the machine-specific vault through `_meta/routing-tables.md`; do not search arbitrary filesystem copies or use hardcoded `file:///` links.
- Read its operating documents in order: `README.md` → `handoff.md` → applicable schemas (parent to nearest) → `index.md`. Read `roadmap.md` only when the task touches backlog, uncertainty, or an unresolved claim.
- Before multi-step or resumed implementation, ground the wiki context against the live code, propose `step → verify` checkpoints, and confirm them before editing.
- Read [`app/AGENTS.md`](app/AGENTS.md) for package-level code entry points, threading rules, and test boundaries when changing the app module.
- This company-PC checkout is the Android test-app repository; anti-spoofing training/export belongs to the paired `access-liveness-model` repository, while the shared signaling server and operator web belong to `ubio-webrtc`.
- Use English for code comments, commit messages, maintained Project-wiki pages, and AI operating guides. Report work in the user's language unless they request otherwise.

## Product and Runtime/Pipeline Map

Minimal Android testbed evaluating RGB/IR anti-spoofing TFLite models, MobileFaceNet recognition, and WebRTC on physical UBio-N Face Pro hardware (`com.virditech.ac7000`).

Anti-spoofing and face recognition are unordered, independent evaluation tracks. Anti-spoofing training/export belongs to the paired `access-liveness-model` project; this Android app deploys and evaluates its artifacts. The current face-recognition track does not train a model: it acquires a suitable pretrained artifact, converts it, and measures standalone Android inference and identity-score behavior before any product integration.

```text
Dual Cameras (RGB + IR)
           │
           ▼
[ CameraStream + YuvConverter ] ── (NV21 / Bitmap conversion on worker executor)
           │
           ├─► [ FaceDetector / MediaPipeFaceDetector ] ── (Largest RGB face + 5 landmarks)
           │          │
           │          └─► [ FaceRecognitionManager ] ── (independent CPU/NNAPI validation; 112x112 aligned 1:N search)
           │
           ├─► [ FramePair (<= 150ms delta) ] ── (Calibration RGB-to-IR affine mapping)
           │          │
           │          ▼
           │   [ FaceMotionGate ] ── (Blocks motion >0.8 face-width/s or edge touch)
           │          │
           │          ▼
           ├─► [ AntiSpoofingClassifier / ModelSlotClassifier ] ── (1/2/5-input TFLite NPU/CPU)
           │
           ├─► [ OverlayView + MainScreenView ] ── (Mirrored canvas overlay & diagnostics)
           │
           ├─► [ CaptureStorage + BmpWriter ] ── (/sdcard/Pictures/raw 100-sample dataset)
           │
           └─► [ WebRtcCallActivity + VideoPeerConnection ] ── (Isolated 768x432 15fps video/audio call)
```

## Module/Domain Map and First Reads

| Domain / Package | Ownership | First Source Entry Point | Wiki & Spec Documents |
| --- | --- | --- | --- |
| **Model & Inference** (`model/`) | TFLite loader, manifest slots, tensor mapping, NNAPI, motion gate | `ModelSlotClassifier`, `AntiSpoofingClassifier`, `FaceMotionGate` | `features/model-contract-branches`, [`docs/model-contract.md`](docs/model-contract.md) |
| **Face Detection** (`face/`) | FaceMe SDK 7.8.2, MediaPipe BlazeFace, NPU warmup, quality gate | `FaceDetector`, `MediaPipeFaceDetector` | `features/camera-and-calibration`, [`docs/device-runtime.md`](docs/device-runtime.md) |
| **Camera & Teardown** (`camera/`, `calibration/`) | Dual Camera2, 150ms pairing, `CalibConfig.dat`, `onClosed` teardown | `DualCameraController`, `CameraStream`, `Calibration` | `features/camera-and-calibration`, [`docs/device-runtime.md`](docs/device-runtime.md) |
| **Dataset Capture** (`capture/`) | 100-sample raw dataset, ATTACK false-live, BMP writing, `meta.json` | `CaptureStorage`, `BmpWriter`, `CaptureSchedule` | `features/camera-and-calibration`, [`docs/capture-contract.md`](docs/capture-contract.md) |
| **Face Recognition** (`recognition/`) | MobileFaceNet 1:N, 5-point alignment, independent correctness and delegate validation | `FaceRecognitionManager`, `FaceAligner`, `FaceEmbeddingModel` | `technical/mobilefacenet-recognition-experiment`, [`docs/performance-guide.md`](docs/performance-guide.md) |
| **WebRTC PoC** (`call/`, `api/call/`) | Signaling WebSocket, Camera2 handoff, PeerConnection, audio routing | `SignalingClient`, `WebRtcCallActivity`, `VideoPeerConnection` | `features/webrtc-test`, [`docs/webrtc-test.md`](docs/webrtc-test.md) |
| **Device & Sysfs** (`device/`) | PI6008K IR AE Full/Center, IR LED/LCD sysfs, lighting detector, CSV logger, daemon watchdog | `IrCameraExposureController`, `HardwareControls`, `DualLightingDetector`, `LightingExperimentLogger`, `UbimDaemonClient` | `features/camera-and-calibration`, `technical/build-deployment-requirements` |
| **UI & Overlay** (`ui/`) | Java-based layout, mirrored Canvas bounding boxes, Auth Mode cards | `MainScreenView`, `OverlayView` | `technical/code-structure-refactoring`, `features/model-contract-branches` |
| **Performance** (`performance/`) | P50/P95 rolling latency windows, memory & GC diagnostics | `LatencyWindow`, `MainActivity` logcat pipelines | `technical/code-structure-performance-diagnosis`, [`docs/performance-guide.md`](docs/performance-guide.md) |

## Task Router

| Request Concerns | Read First | Primary Entry Point | Trace Path |
| --- | --- | --- | --- |
| **Add / update anti-spoofing model** | `features/model-contract-branches`, [`docs/model-contract.md`](docs/model-contract.md) | `assets/model_manifest.json`, `ModelSpec.java` | `ModelSlotClassifier` → `AntiSpoofingClassifier` → `MainActivity.loadModelSlots` |
| **Camera crash / pause-resume / SIGSEGV** | `technical/code-structure-performance-diagnosis`, [`docs/device-runtime.md`](docs/device-runtime.md) | `CameraStream.java`, `DualCameraController.java` | `CameraDevice.StateCallback.onClosed` → `ImageReader.close` → `YuvConverter` |
| **IR AE profile / register tweak** | `features/camera-and-calibration` | `IrCameraExposureController.java` | Sysfs register write → `MainActivity.startCameras` → Hidden test menu |
| **Lighting / backlight detector** | `features/camera-and-calibration` | `DualLightingDetector.java` | `MainActivity.processTracking` → `DualLightingDetector.evaluate` → `MainScreenView.showCleanModeLighting` |
| **Capture flow / metadata / BMP format** | `features/camera-and-calibration`, [`docs/capture-contract.md`](docs/capture-contract.md) | `CaptureStorage.java`, `BmpWriter.java` | `MainActivity.processTracking` → `CaptureSchedule` → `SampleMetadata` |
| **MobileFaceNet recognition experiment** | `technical/mobilefacenet-recognition-experiment`, [`docs/performance-guide.md`](docs/performance-guide.md) | `FaceRecognitionManager.java`, `FaceAligner.java` | FaceMe 5-point landmarks → aligned 112x112 RGB → embedding → `FaceTemplate` search |
| **WebRTC video / audio / handoff** | `features/webrtc-test`, [`docs/webrtc-test.md`](docs/webrtc-test.md) | `SignalingClient.java`, `WebRtcCallActivity.java` | `MainActivity.onPause` camera stop → `VideoPeerConnection` → `CallAudioManager` |
| **Auth Mode (5-frame moving avg)** | `features/model-contract-branches` | `MainActivity.java` (`authScoreBuffer`) | `OverlayView.drawAuthVerdict` → `ToneGenerator` pass/fail feedback |

## Immutable Boundaries and Change Gates

1. **Anti-Spoofing NNAPI No-Fallback Rule**: Never implement silent CPU fallback for a manifest model slot. An NNAPI error during setup/warmup must reject the slot so NPU defects are immediately detected. The separate MobileFaceNet experiment defaults explicitly to CPU because its PReLU graph is slower on this board.
2. **VSI NPU Cache Restriction**: Never enable NNAPI compilation caching (`setCacheDir`/`setModelToken`); the board driver will fail compilation.
3. **Camera Teardown Sequencing**: Never close `ImageReader` or preview `Surface` before `CameraDevice.StateCallback.onClosed()` has fired. Violating this triggers native `SIGSEGV` in `YuvConverter`.
4. **Capture Atomicity**: A capture sample advances the count only when all 5 files (`RGB.bmp`, `cropRGB.bmp`, `IR.bmp`, `cropIR.bmp`, `meta.json`) succeed.
5. **Output Dimension Contract**: Anti-spoofing models must strictly output `[1,10]` matching the fixed class order.
6. **Secrets & Credentials**: Never commit `FACEME_LICENSE_KEY`, private Maven URLs, keystores, or signaling server credentials.

## Build and Verification

```powershell
# 1. Compile check
./gradlew.bat :app:compileDebugJavaWithJavac

# 2. JVM Unit Tests
./gradlew.bat :app:testDebugUnitTest

# 3. Target Device Logcat Filters (when device connected)
adb logcat -s AntiSpoofingClassifier:I MainActivity:I
adb logcat -s MainActivity:E CameraStream:E *:S
```
