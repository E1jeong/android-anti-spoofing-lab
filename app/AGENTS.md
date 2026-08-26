# `app` Module Guide

## Scope

- Own the complete Android application implementation for `ubio-anti-spoofing`.
- Houses the dual Camera2 capture pipeline, TFLite NPU/CPU inference runtime, FaceMe/MediaPipe detection wrappers, MobileFaceNet embedding manager, WebRTC call activity, hardware sysfs controllers, and 100-sample dataset capture engine.

## Orient First

- Read `technical/code-structure-performance-diagnosis` and `overview` before modifying core runtime loops or camera pipelines.
- Source entry points:
  - Application orchestration: `MainActivity.java` (lifecycle, executors, hotspot gesture dispatcher), `IntroActivity.java`
  - Deep technical specifications:
    - Model contracts & manifests: [`../docs/model-contract.md`](../docs/model-contract.md)
    - Device runtime & teardown: [`../docs/device-runtime.md`](../docs/device-runtime.md)
    - Dataset capture & BMP stream: [`../docs/capture-contract.md`](../docs/capture-contract.md)
    - Performance diagnostics & P50/P95: [`../docs/performance-guide.md`](../docs/performance-guide.md)
    - WebRTC signaling & handoff: [`../docs/webrtc-test.md`](../docs/webrtc-test.md)

## Boundary & Architecture Constraints

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
   - Default to NNAPI with `models/mobilenet_emore_npu_int8.tflite` (0.27 GFLOPs, 100% 31/31 nodes compiled to 1 NPU partition). Legacy PReLU models default to CPU/XNNPACK due to NPU graph fragmentation.
   - Keep embedding extraction off the anti-spoofing `inferenceExecutor` so recognition timing and failures can be measured independently. Transfer only an owned aligned 112x112 bitmap to the recognition executor.
   - During independent model validation, do not gate recognition or enrollment on an anti-spoofing result. Do not drop or replace requested samples through latest-wins scheduling or a fixed minimum interval; every accepted test request must produce a recorded result, explicit error, or explicit cancellation.
   - Face recognition and anti-spoofing are parallel evaluation tracks with no ordering or dependency. Anti-spoofing artifacts are trained/exported by `access-liveness-model`; the current recognition work acquires and converts a pretrained model rather than training one.
   - Stop the current recognition scope at standalone load/inference, conversion/delegate agreement, alignment inspection, embedding repeatability, score distributions, and latency. Liveness gating, rate limiting, latest-wins scheduling, template persistence, and authentication-score composition are separate future integration work.
   - `FixedInputRecognitionActivity` / `FixedInputRecognitionRunner` own camera-free CPU/NNAPI comparison on external 112x112 inputs; follow `docs/performance-guide.md` and keep biometric fixtures out of Git and the APK.
   - Treat the 0.70 identity threshold and the observed self 91% versus other 19–20% result as preliminary experiment evidence, not proof of model acceptance or a production authentication boundary.

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
- **Recognition Isolation**: MobileFaceNet alignment may copy an owned 112x112 bitmap before frame recycle, but embedding and 1:N search must run only on the recognition executor.
- **Teardown Sequencing**: Never close `ImageReader` or preview `Surface` before `CameraDevice.StateCallback.onClosed()` has fired (avoids native `SIGSEGV` in `YuvConverter`).
- **No XML Inflation**: All UI views are constructed programmatically in Java via `MainScreenView`.

## Verify

```powershell
# Compile check
./gradlew.bat :app:compileDebugJavaWithJavac

# JVM Unit tests
./gradlew.bat :app:testDebugUnitTest
```
