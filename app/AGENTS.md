# `app` Module Guide

## Scope

- Own the Android evaluation host for `ubio-anti-spoofing`.
- Houses the dual Camera2 capture pipeline, FaceMe/MediaPipe/YuNet detection wrappers, face-embedding manager, WebRTC call activity, hardware sysfs controllers, and 100-sample dataset capture engine.
- Anti-spoofing inference lives in `:vision` (`com.unionbiometrics.vision`). This module passes RGB/IR frames and face boxes and displays results.

## Orient First

All paths below are relative to `app/src/main/java/com/virditech/ac7000/`.

- Orchestration and loading: `MainActivity.java`, `IntroActivity.java`.
- Anti-spoofing SDK (other module): `vision/` — `AntiSpoofingEngine`; this Lab uses raw `inferFrame(...)` frames, while product hosts use callback `inferSession(...)` sessions. See [`../vision/AGENTS.md`](../vision/AGENTS.md).
- Camera, tracking, and calibration: `camera/DualCameraController.java`, `camera/CameraStream.java`, `camera/FrameSynchronizer.java`, `face/FaceDetector.java`, `face/MediaPipeFaceDetector.java`, `face/YuNetFaceDetectorAdapter.java`, `face/FaceDetectionEngine.java`, `calibration/Calibration.java`.
- Evaluator models and gate: `model/FaceMotionGate.java`, `model/AuthFrameAccumulator.java`.
- Capture and measurement: `capture/CaptureStorage.java`, `capture/BmpWriter.java`, `performance/LatencyWindow.java`.
- Recognition: `recognition/FaceRecognitionManager.java`, `recognition/FixedInputRecognitionRunner.java`.
- Calls and device UI: `call/WebRtcCallActivity.java`, `api/call/SignalingClient.java`, `api/call/CallSignaling.java`, `device/HardwareControls.java`, `ui/MainScreenView.java`, `ui/CaptureConfigDialog.java`.

## Boundary & Architecture Constraints

1. **`camera` & `calibration`**:
   - `CameraStream` owns Camera2 session lifecycle. Teardown must always be serialized on the camera handler thread.
   - `FrameSynchronizer` retains the latest IR frame and matches it to an RGB timestamp within the delta that `MainActivity` injects (`MAX_PAIR_DELTA_NS`, 150 ms); `MainActivity` builds the `FramePair`.
   - `Calibration.rgbToIr()` maps RGB face bounding boxes to IR coordinates via the 64-byte `CalibConfig.dat` (stored at `/sdcard/devlocal/CalibConfig.dat` or internal fallback). Preserve the NPro-compatible contract: `horizontal = RGB-IR`, subtract scaled offsets when mapping, write current values as big-endian, and treat unversioned legacy Viewer files as requiring recalibration rather than silent sign conversion.
   - `PreviewTransform` is the single source for RGB/IR raw-`TextureView`, analysis-overlay, and crop-preview display mirroring. Do not hardcode `setScaleX` or overlay mirror flags elsewhere; display transforms must not alter calibration, saved-frame, model-crop, or SDK-input coordinates.
   - `YuNetFaceDetectorAdapter` bridges `:face-detection` without landmarks or quality data. YuNet uses existing Calibration for IR mapping; RGB/IR calibration measurement requires FaceMe or MediaPipe. Preserve the FaceMe-only HIGH/MEDIUM capture-quality gate.

2. **anti-spoofing host**:
   - Product hosts load through `AntiSpoofingEngine.create(applicationContext, slotIndex, settleFrames, sampleCount)` on a background thread and pass unexpanded boxes to `inferSession(rgb, rgbFace, ir, irFace, callback)`. IR-only slots accept null or paired RGB inputs without copying them. The host owns IR LED ON/OFF across every terminal, reset, failure, and close path. This in-repository Lab app discovers models with `AntiSpoofingEngine.getModelInfo(context).modelCount`, creates one engine per manifest slot, and uses `inferFrame(...)` for raw per-frame evaluation. Do not add a host `assets/model_manifest.json` or `assets/ubio-vision/` overlay. Recognition loads its own default when no root `model_manifest.json` is present.
   - Read class labels from `ModelInfo.classLabels`; the pre-creation metadata query supplies them before model warmup. Treat `AntiSpoofingResult.getProbabilities()` as the sole Lab per-frame probability vector for both supported layouts; copy it once per display operation, and use `getScore()` for the winning score. Do not recreate separate RGB/IR result UI branches.
   - Keep engines keyed by their original manifest slot number and display `MODEL 1`, `MODEL 2`, ... even when other slots fail to load. The Lab computes preview/capture rectangles locally from `engine.getModelInfo().cropMarginRatio`; SDK inference expands its crops internally. Keep pixel rounding and image-bound clipping consistent; do not duplicate model metadata in the app.
   - `FaceMotionGate` (app `model/`) halts inference when RGB face center speed exceeds 0.8 face widths/s or box touches image edge; clears results and resumes on 1st stable frame. Lab-only helper, not part of `:vision`.

3. **`recognition`**:
   - Isolated experimental face-recognition package; the default model is MobileNet Emore INT8, while `w600k_mbf` is a historical experiment.
   - `FaceAligner.align5PointsTo112` performs 2D affine similarity transform from FaceMe 5 landmarks to canonical 112x112 ArcFace coordinates.
   - Model loading and delegate reloads must run asynchronously on `modelInitExecutor` (never block the Android UI Main Thread).
   - Default to NNAPI with `models/mobilenet_emore_npu_int8.tflite`. Legacy PReLU models default to CPU/XNNPACK due to NPU graph fragmentation.
   - Keep embedding extraction off the anti-spoofing `inferenceExecutor` so recognition timing and failures can be measured independently. Transfer only an owned aligned 112x112 bitmap to the recognition executor.
   - During independent model validation, do not gate recognition or enrollment on an anti-spoofing result. Do not drop or replace requested samples through latest-wins scheduling or a fixed minimum interval; every accepted test request must produce a recorded result, explicit error, or explicit cancellation.
   - Face recognition and anti-spoofing are parallel evaluation tracks with no ordering or dependency. Anti-spoofing artifacts are trained/exported by `access-liveness-model`; the current recognition work acquires and converts a pretrained model rather than training one.
   - Stop the current recognition scope at standalone load/inference, conversion/delegate agreement, alignment inspection, embedding repeatability, score distributions, and latency. Liveness gating, rate limiting, latest-wins scheduling, and authentication-score composition remain separate future integration work; the existing lab-only template DB is not production authentication.
   - `FixedInputRecognitionActivity` / `FixedInputRecognitionRunner` own camera-free CPU/NNAPI comparison on external 112x112 inputs; follow the `technical/mobilefacenet-recognition-experiment` fixed-input checklist when the wiki or snapshot is available, and keep biometric fixtures out of Git and the APK.
   - Treat identity thresholds and smoke observations as experimental; they do not establish model acceptance or a production authentication boundary. Dated measurements belong in `technical/mobilefacenet-recognition-experiment` when the wiki or snapshot is available.

4. **`capture`**:
   - Writes directly to `/sdcard/Pictures/raw/live/<qualityMode>/live_<subject>/<index>/` (live) or `/sdcard/Pictures/raw/<class>/<environment>/<class>_<subject>/<index>/` (non-live).
   - `live` uses HIGH, MEDIUM, or OFF quality mode. HIGH and MEDIUM apply the FaceMe quality gate; OFF and non-live classes bypass it.
   - ATTACK mode captures false-live misclassifications (`LIVE >= 0.80`) to `/sdcard/Pictures/raw/attack_live/`.
   - `BmpWriter` streams 24-bit uncompressed BGR rows using reusable 16-row stripe buffers without full-frame allocations.

5. **`call` & `api.call`**:
   - `WebRtcCallActivity` executes isolated video/audio call PoC.
   - Entering `WebRtcCallActivity` pauses `MainActivity`, triggering `MainActivity.onPause()` to completely stop RGB/IR cameras and IR LED before WebRTC Camera2 capturer starts.
   - SDP descriptions must be preserved verbatim (do not trim CRLF).

6. **`device`**:
   - `IrCameraExposureController` applies PI6008K IR ISP register sequences for Full AE vs Center AE profile switching.
   - `UbimDaemonClient` only sends commands over the `ubimd` daemon socket. `MainActivity` uses it to grant `MANAGE_EXTERNAL_STORAGE`; `AppWatchdog` refreshes `appalive set 60 <pid> reboot` every 5 s and clears it on close.

7. **`ui`**:
   - `MainScreenView` owns the main screen hierarchy and UI state transitions.
   - Update lower-left status messages through `MainScreenView.setStatusText(...)`; empty text is `GONE`, and message updates must preserve mode-specific hiding.
   - `CaptureConfigDialog` owns capture-dialog construction, input synchronization, and show/dismiss; `MainScreenView` delegates through its public entry points.
   - `OverlayView` renders the face box (white while collecting, cyan in Auth Mode, otherwise colored by result), the top-class and recognition labels outside collection/Auth Mode, the capture sector grid and countdown, and calibration/observation guides.

## Change Gates

- **No Main Thread Blocking**: Model loading, NNAPI compilation, face detection, and BMP I/O must run on dedicated background executors.
- **Recognition Isolation**: MobileFaceNet alignment may copy an owned 112x112 bitmap before frame recycle, but embedding and 1:N search must run only on the recognition executor.
- **Teardown Sequencing**: Never close `ImageReader` or preview `Surface` before `CameraDevice.StateCallback.onClosed()` has fired (avoids native `SIGSEGV` in `YuvConverter`).

## Verify

```powershell
# Compile check
./gradlew.bat :app:compileDebugJavaWithJavac

# JVM Unit tests
./gradlew.bat :app:testDebugUnitTest
```
