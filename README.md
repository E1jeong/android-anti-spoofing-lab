# Anti-Spoofing Viewer

Minimal Android test application for evaluating RGB/IR anti-spoofing TFLite models on UBio-N Face Pro hardware.

## Current model setup

Model slots are declared in `app/src/main/assets/model_manifest.json`. The loader supports:

- `paired_1_input`: separate one-input RGB and IR models.
- `dual_2_input`: one model receiving RGB and IR crops.
- `five_input`: one model receiving RGB crop, IR crop, full RGB, full IR, and heatmap.

The current manifest contains one `paired_1_input` slot:

- RGB: `best_crop_rgb_fixed_npu_int8.tflite` with `model_spec_rgb.json`.
- IR: `best_crop_ir_fixed_npu_int8.tflite` with `model_spec_ir.json`.

Both models use NNAPI-first INT8 specs and run sequentially on the single inference executor, RGB followed by IR. The UI displays separate RGB/IR six-class probabilities and inference latency, plus paired processing FPS. The previous RGB fold3/IR fold4 pair was verified on the target device with `Backend RGB NNAPI / IR NNAPI` and six outputs. The current fixed/fixed pair still requires target-device model-load, backend-label, probability-output, and latency/FPS verification.

The runtime parser accepts one-, two-, or five-input NHWC models with `FLOAT32`, `UINT8`, or `INT8` inputs. Current deployment verification covers float and full INT8; UINT8 normalization/quantization semantics have not yet been verified against an exported model. Every model must have one `FLOAT32` or `INT8` output with shape `[1,6]`. Preprocessing, delegate selection, logits handling, input kind/name mapping, and crop margin are controlled by the spec assigned to each manifest entry.

NNAPI compilation caching must remain disabled because the target board's VSI NPU driver fails models with caching enabled. A `cpu` spec uses CPU/XNNPACK, while NNAPI setup or model warmup failure rejects that manifest slot instead of falling back. This no-fallback behavior is intentional for NPU evaluation. Verify model warmup, the on-screen backend label, logcat, and latency together; `Backend NNAPI` alone does not prove that every operation ran on the NPU.

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
