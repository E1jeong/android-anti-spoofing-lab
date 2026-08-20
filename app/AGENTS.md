# `app` Module Guide

## Scope

- Own the complete Android application implementation for `ubio-anti-spoofing`.
- Houses the dual Camera2 capture pipeline, TFLite NPU/CPU inference runtime, FaceMe/MediaPipe detection wrappers, MobileFaceNet embedding manager, WebRTC call activity, hardware sysfs controllers, and 100-sample dataset capture engine.

## Orient First

- Read `technical/code-structure-performance-diagnosis` and `overview` before modifying core runtime loops or camera pipelines.
- Source entry points:
  - Application orchestration: `MainActivity.java` (lifecycle, executors, hotspot gesture dispatcher), `IntroActivity.java`
  - Deep technical specifications:
    - Model contracts & manifests: [`../docs/agent/model-contract.md`](../docs/agent/model-contract.md)
    - Device runtime & teardown: [`../docs/agent/device-runtime.md`](../docs/agent/device-runtime.md)
    - Dataset capture & BMP stream: [`../docs/agent/capture-contract.md`](../docs/agent/capture-contract.md)
    - Performance diagnostics & P50/P95: [`../docs/agent/performance-guide.md`](../docs/agent/performance-guide.md)
    - WebRTC signaling & handoff: [`../docs/agent/webrtc-test.md`](../docs/agent/webrtc-test.md)

## Boundary & Package Architecture Rules

1. **`camera` & `calibration`**:
   - `CameraStream` owns Camera2 session lifecycle. Teardown must always be serialized on the camera handler thread.
   - `FramePair` matches RGB and IR frames within 150 ms (`MAX_PAIR_DELTA_NS`).
   - `Calibration.rgbToIr()` maps RGB face bounding boxes to IR coordinates via the 64-byte `CalibConfig.dat` (stored at `/sdcard/devlocal/CalibConfig.dat` or internal fallback).
   - Screen displays mirrored preview (`setScaleX(-1f)`); `OverlayView` must pass `mirror=true` to `map()` to align canvas boxes with visible faces.

2. **`model`**:
   - `AntiSpoofingClassifier` supports NHWC Float32/Int8 models with 1, 2, or 5 inputs and fixed `[1, 10]` output.
   - `ModelSlotClassifier` loads entries from `assets/model_manifest.json`. Only the active slot runs per frame on `inferenceExecutor`.
   - `FaceMotionGate` halts inference when RGB face center speed exceeds 0.8 face widths/s or box touches image edge; clears results and resumes on 1st stable frame.

3. **`recognition`**:
   - Isolated experimental package for MobileFaceNet (`w600k_mbf`).
   - `FaceAligner.align5PointsTo112` performs 2D affine similarity transform from FaceMe 5 landmarks to canonical 112x112 ArcFace coordinates.
   - Model loading and delegate reloads must run asynchronously on `modelInitExecutor` (never block the Android UI Main Thread).

4. **`capture`**:
   - Writes directly to `/sdcard/Pictures/raw/<class>/<class>_<subject>/<index>/`.
   - `live` applies FaceMe HIGH (`> 0.9`) or MEDIUM (`> 0.6`) quality gate; non-live and curved classes bypass quality checks.
   - ATTACK mode captures false-live misclassifications (`LIVE >= 0.80`) to `/sdcard/Pictures/raw/attack_live/`.
   - `BmpWriter` streams 24-bit uncompressed BGR rows using reusable 16-row stripe buffers without full-frame allocations.

5. **`call` & `api.call`**:
   - `WebRtcCallActivity` executes isolated video/audio call PoC.
   - Entering `WebRtcCallActivity` pauses `MainActivity`, triggering `MainActivity.onPause()` to completely stop RGB/IR cameras and IR LED before WebRTC Camera2 capturer starts.
   - SDP descriptions must be preserved verbatim (do not trim CRLF).

6. **`device`**:
   - `IrCameraExposureController` applies PI6008K IR ISP register sequences for Full AE vs Center AE profile switching.
   - `UbimDaemonClient` acquires `MANAGE_EXTERNAL_STORAGE` via UBio daemon socket.

7. **`ui`**:
   - `MainScreenView` constructs pure Java View hierarchy (no XML layouts).
   - `OverlayView` renders face boxes, landmark points, and Auth Mode cards.

## Change Gates

- **No Main Thread Blocking**: Model loading, NNAPI compilation, face detection, and BMP I/O must run on dedicated background executors.
- **Teardown Sequencing**: Never close `ImageReader` or preview `Surface` before `CameraDevice.StateCallback.onClosed()` has fired (avoids native `SIGSEGV` in `YuvConverter`).
- **No XML Inflation**: All UI views are constructed programmatically in Java via `MainScreenView`.

## Verify

```powershell
# Compile check
./gradlew.bat :app:compileDebugJavaWithJavac

# JVM Unit tests
./gradlew.bat :app:testDebugUnitTest
```
