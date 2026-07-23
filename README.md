# Anti-Spoofing Viewer

Minimal Android test application for evaluating RGB/IR anti-spoofing TFLite models on UBio-N Face Pro hardware.

## Current model setup

Model slots are declared in `app/src/main/assets/model_manifest.json`. The loader supports:

- `single_1_input`: one RGB or IR crop model, selected by the spec's `inputKind`.
- `paired_1_input`: separate one-input RGB and IR models.
- `dual_2_input`: one model receiving RGB and IR crops.
- `five_input`: one model receiving RGB crop, IR crop, full RGB, full IR, and heatmap.

The current manifest contains one `single_1_input` IR slot:

- IR: `best_crop_ir_fixed_npu_int8.tflite` with `best_crop_ir_fixed_npu_int8_manifest.json`.

The model uses an NNAPI-first INT8 spec and runs on the single inference executor. The UI displays its six-class probabilities, inference latency, and processing FPS. The current fixed IR model still requires target-device model-load, backend-label, probability-output, and latency/FPS verification.

The runtime parser accepts one-, two-, or five-input NHWC models with `FLOAT32`, `UINT8`, or `INT8` inputs. Current deployment verification covers float and full INT8; UINT8 normalization/quantization semantics have not yet been verified against an exported model. Every model must have one `FLOAT32` or `INT8` output with shape `[1,6]`. Preprocessing, delegate selection, logits handling, input kind/name mapping, and crop margin are controlled by the spec assigned to each manifest entry.

NNAPI compilation caching must remain disabled because the target board's VSI NPU driver fails models with caching enabled. A `cpu` spec uses CPU/XNNPACK, while NNAPI setup or model warmup failure rejects that manifest slot instead of falling back. This no-fallback behavior is intentional for NPU evaluation. Verify model warmup, the on-screen backend label, logcat, and latency together; `Backend NNAPI` alone does not prove that every operation ran on the NPU.

## Face detector comparison branch

`feat/mediapipe-face-detector-toggle` keeps FaceMe and MediaPipe Face Detector available at startup and adds a detector-toggle button for hardware comparison. The selected detector supplies largest-face tracking and RGB/IR calibration validation; `live` collection remains FaceMe-only because its HIGH/MEDIUM quality contract has not been replaced. MediaPipe uses the CPU delegate and the public `blaze_face_short_range.tflite` asset. The repository ignores `.tflite` files, so provision that model locally before building this branch; do not commit it with the anti-spoofing model artifacts.

## Required local configuration

Supply proprietary dependencies, the FaceMe license, and optional platform-signing values through user-level Gradle properties or the ignored root `local.properties`. Do not commit their values.

```properties
UBIO_MAVEN_URL=...
FACEME_LICENSE_KEY=...
UBIO_KEYSTORE_PATH=...
UBIO_KEY_ALIAS=...
UBIO_KEY_PASSWORD=...
UBIO_STORE_PASSWORD=...
```

`UBIO_MAVEN_URL` must contain the proprietary FaceMe dependency. A public checkout without the repository, license, signing inputs, calibration data, and target hardware cannot perform a complete device validation.

## Data capture

Capture sessions save 100 valid samples. Files are written directly under `/sdcard/Pictures/raw` as `RGB.bmp`, `cropRGB.bmp`, `IR.bmp`, `cropIR.bmp`, and `meta.json`.

- `live` uses `/live/high/live_<subject>/...` or `/live/medium/live_<subject>/...` and applies the selected FaceMe quality threshold.
- `display`, `picture`, `print`, `mask`, and `pmask` use `/<class>/<class>_<subject>/...` and bypass quality checks.
- Capture can be paused and resumed. Cancel invalidates queued work and deletes the current subject directory.

## Camera calibration

Tap the invisible upper-left hotspot five times within two seconds to open calibration. Place exactly one face inside the guide and press `CONFIRM`. The app detects the face in synchronized RGB and IR frames and writes the device-specific 64-byte `CalibConfig.dat`. `CANCEL` exits without changing the saved calibration.

The preferred path is `/sdcard/devlocal/CalibConfig.dat`; internal app storage is used as a fallback when the external path cannot be accessed.

## Build

```powershell
./gradlew.bat :app:compileDebugJavaWithJavac
```

Hardware-dependent behavior still requires manual verification on the target device. The latest overlay draw-order and background change has compiled, but its final visual and touch/UI regression check remains pending.
