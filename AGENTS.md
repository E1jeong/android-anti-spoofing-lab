# Project: ubio-anti-spoofing

## Purpose

This repository is a minimal Android test application for evaluating newly developed RGB/IR anti-spoofing TensorFlow Lite models on UBio-N Face Pro hardware. It is an isolated model-development and verification tool, not the production terminal application.

Only the device, camera, calibration, face-detection, and lifecycle behavior required for model evaluation was extracted from the UBio-N Face Pro project. Keep this repository minimal. When copied device behavior is unclear, consult the source project before changing it; do not import unrelated production features or modules.

## Runtime Pipeline

The application performs the following pipeline:

1. Capture RGB and IR frames from the device cameras. YUV-to-Bitmap conversion is offloaded to dedicated single-threaded executors to avoid blocking camera callbacks, dropping incoming frames when busy.
2. Detect the largest face from the latest RGB frame with FaceMe and update the overlay independently of model inference.
3. Match the latest IR frame within the timestamp tolerance of 150ms (`MAX_PAIR_DELTA_NS`) for anti-spoofing inference.
4. Map the RGB face region to IR coordinates using the device calibration file, then expand crops according to the model specification.
5. Run the matched crops (RGB crop, IR crop, Full RGB, Full IR, Heatmap) through the TensorFlow Lite anti-spoofing model.
6. Update the diagnostic crop preview (top-right) independently of pairing success using a copied frame buffer, throttled to a minimum interval of 66ms (~15 FPS).
7. Display the five-class probabilities, top result, conversion time, detection time, inference time, and processing FPS over the RGB or IR preview.
8. **Pre-warming & Hot Swapping**: On startup, both standard and NPU models are loaded and warmed up concurrently in the background. Pressing the "Switch Model" button instantly (0ms) swaps the model reference without reloading or memory leaks, ensuring thread-safe operation.

## Model Contract

- The model asset is `app/src/main/assets/anti_spoofing.tflite` (default). Optional NPU model can be placed at `app/src/main/assets/anti_spoofing_npu.tflite` (fallback is standard if not present).
- Preprocessing settings are defined by two spec files:
  - `model_spec.json` (Standard Model: `rgbNormalization: imagenet`, `delegate: nnapi`)
  - `model_spec_npu.json` (NPU Model: `rgbNormalization: minus_one_to_one`, `delegate: nnapi`)
- The model must have exactly **five NHWC inputs** matched by name (case-insensitive):
  - `cropRgb` (RGB Crop, 3 channels)
  - `cropIr` (IR Crop, 1 channel)
  - `fullRgb` (Full RGB image, 3 channels)
  - `fullIr` (Full IR image, 1 channel)
  - `heatmap` (Face bounding box heatmap, 1 channel)
- Supported input types are `FLOAT32`, `UINT8`, and `INT8`.
- The model must have one `FLOAT32` or `INT8` output with shape `[1,5]`.
- Output indices are fixed in this order: `LIVE`, `PRINT`, `PICTURE`, `MASK`, `DISPLAY` (must match `ClassificationResult.LABELS`).
- Spec JSONs control channel order (RGB/BGR), normalization values, delegate backend (`cpu`/`nnapi`), whether the output contains logits, and the crop margin ratio.
- Do not change preprocessing, output ordering, or tensor assumptions without updating the model contract and verifying them against the exported model.
- **Current deployment supports float and full INT8 models.** The app tries Android NNAPI first for NPU evaluation and falls back to CPU/XNNPACK if NNAPI model preparation fails. Current target-board result: even the NPU-friendly Keras INT8 export still falls back to CPU with `ANEURALNETWORKS_BAD_DATA ... while adding operation`; do not report NPU acceleration as working until the on-device UI shows `Backend NNAPI` and latency is measured. Full history/decision: `\\wsl.localhost\Ubuntu-24.04\home\union\access-liveness-model\docs\project_status.md` section 3.
- Standard model expects ImageNet RGB normalization. NPU model expects `[-1, 1]` style normalization (`mean=[0.5]`, `std=[0.5]`).

## Device and Build Requirements

- This is an Android application with `minSdk 30`, `targetSdk 34`, and Java 17 source compatibility.
- The proprietary FaceMe dependency repository must be supplied through the `UBIO_MAVEN_URL` Gradle property.
- Face detection requires the configured FaceMe dependency and a valid `FACEME_LICENSE_KEY` Gradle property.
- Platform signing uses `UBIO_KEYSTORE_PATH`, `UBIO_KEY_ALIAS`, `UBIO_KEY_PASSWORD`, and `UBIO_STORE_PASSWORD` when provided.
- RGB-to-IR mapping requires `/sdcard/devlocal/CalibConfig.dat` on the device.
  - **Storage Permissions**: Since the app runs as `android.uid.system`, the Android FUSE filesystem/MediaProvider blocks direct file access to `/sdcard/` by default on API 30+. To bypass this, the app uses `UbimDaemonClient` at startup to programmatically grant itself `MANAGE_EXTERNAL_STORAGE` (`appops set com.virditech.ac7000 MANAGE_EXTERNAL_STORAGE allow`).
  - **Fallback Location**: If reading/writing to `/sdcard/devlocal/CalibConfig.dat` still fails, it transparently falls back to `appFile` at `getFilesDir()/CalibConfig.dat` (internal app storage).
- The hidden camera-calibration flow is opened by five taps on the upper-left hotspot. It measures one RGB face and one synchronized IR face, then writes the npro-compatible 64-byte calibration file.
- The app controls the UBio IR LED and LCD through device sysfs paths and communicates with the UBio daemon for watchdog behavior. These paths and protocols are hardware-specific.
- The application ID and namespace are `com.virditech.ac7000`. Because this is also used by the production application, this test app cannot coexist with UBio-N Face Pro on the same device.
- **Mirroring & Overlays**: The camera previews are mirrored on screen (RGB is mirrored, and IR preview is mirrored via `irPreviewView.setScaleX(-1f)`). Therefore, `OverlayView` must render mirrored bounding boxes (always passing `true` for the `mirror` argument to `map()` in `onDraw`) so that the green face box aligns with the displayed face.

Default compile validation:

```powershell
./gradlew.bat :app:compileDebugJavaWithJavac
```

## Change Rules

- Make the smallest change that satisfies the requested model test or diagnostic goal.
- Do not perform broad refactors, add production features, or copy additional UBio-N Face Pro modules unless explicitly requested.
- Preserve camera selection, resolution, timestamp synchronization, calibration mapping, IR LED control, watchdog behavior, lifecycle cleanup, package identity, and signing behavior unless the task specifically targets one of them.
- Keep model-specific behavior in the model layer and keep device-specific behavior isolated in the existing camera, calibration, face, and device packages.
- Match the existing Java style and remove only imports or code made unused by the current change.
- Inspect the existing worktree before editing and do not overwrite unrelated user changes.
- Never commit FaceMe licenses, keystores, passwords, credentials, customer data, or other secrets.
- Do not add machine-local configuration or paths to tracked files. The source-project path above is documentation for this repository's origin, not a location that application code should depend on.

## Validation

- Run the default compile validation after code or build changes. Use a narrower check only when it fully covers the changed behavior.
- If the model changes, verify that the model loads and that its input/output tensors match the documented contract.
- For NNAPI/NPU changes, verify the on-device backend label. `Backend CPU` means NNAPI preparation failed and measurements are CPU/XNNPACK, not NPU.
- Hardware-dependent changes require manual verification on the target device. At minimum, check RGB and IR preview startup, frame pairing, calibration alignment, IR LED state, face detection, all five output probabilities, inference timing, and cleanup/restart across pause and resume.
- Calibration changes must also verify hidden-mode entry, single-face validation for both cameras, cancel-without-save, persisted alignment after restart, and compatibility with the production RGB-to-IR mapping formula.
- If hardware validation cannot be performed, state which checks remain unverified and the resulting risk.

## Troubleshooting & Evaluation Tips

### 1. 2-Input (2채널) vs 5-Input (5채널) Performance Characteristics
- **2-Input Model**: 이미지 전처리 및 YUV 변환 개수(RGB Crop, IR Crop)가 적어 Convert 병목이 낮습니다. NPU(NNAPI) 가속 시 추론 속도 20~30ms 수준과 합쳐져 **실제 구동 속도가 7 FPS 가까이** 매끄럽게 표출됩니다.
- **5-Input Model**: NPU 가속이 작동하더라도(`Inference ~50ms`), 5개의 텐서 이미지(Full RGB, Full IR, Heatmap 등)를 전처리하고 가공하는 과정에서 심각한 CPU 병목(`Convert RGB/IR ~150ms`)이 생겨 전체 프레임 레이트가 **3~4 FPS 수준**으로 저하됩니다.

### 2. Evaluation Branch Management
- **`master` Branch**: 기존 2채널(2-input: RGB Crop, IR Crop) 연산 구조의 모델을 검증할 때 사용합니다. 
- **`codex/keras-5-input-tflite` Branch**: 최신 5채널(5-input) 연산 구조 모델 검증 및 표준/NPU 핫스왑 실시간 전환 토글 기능을 테스트할 때 사용합니다.

### 3. Tracking failed Debugging
- 화면 좌하단에 `Tracking failed` 메시지가 찍힐 경우, `processTracking` 내부에서 비동기 예외(NPE, IllegalArgumentException 등)가 발생한 것입니다.
- 상세 오류 스택트레이스는 다음 ADB logcat 명령어로 실시간 관측 가능합니다:
  ```bash
  adb logcat -s MainActivity:E *:S
  ```
