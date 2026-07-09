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
7. Display the six-class probabilities, top result, conversion time, detection time, inference time, and processing FPS over the RGB or IR preview.
8. **Pre-warming & Hot Swapping**: On startup, the standard model, NPU model, and FaceMe quality detector are initialized in the loading phase. The loading spinner stays visible until the model warmups finish **and** the quality detector reports available, because NNAPI compilation of the NPU model monopolizes the VSI NPU driver that FaceMe detection also uses (see Troubleshooting section 4). The "Switch Model" button is enabled once the NPU model finishes loading. Switching swaps a `volatile` classifier reference without taking a lock around inference, so the toggle never waits for an in-flight inference and never reloads or leaks a model.
9. **Data Capture Collection**: The `START CAPTURE` flow writes exactly 100 valid samples per selected class. For `live` only, every candidate frame is checked with FaceMe quality before saving; low-quality frames are skipped without incrementing the saved count or sector count. Non-live classes (`display`, `picture`, `print`, `mask`, `pmask`) bypass quality checking and save the next synchronized frame. The collection can be stopped with the top-center `CANCEL CAPTURE` button; canceling stops future captures but does not delete samples already written.

## Model Contract

- The model asset is `app/src/main/assets/anti_spoofing.tflite` (default). Optional NPU model can be placed at `app/src/main/assets/anti_spoofing_npu.tflite` (fallback is standard if not present).
- Preprocessing settings are defined by two spec files:
  - `model_spec.json` (Standard Model: `rgbNormalization: imagenet`, `delegate: cpu`)
  - `model_spec_npu.json` (NPU Model: `rgbNormalization: minus_one_to_one`, `delegate: nnapi`)
- The model must have exactly **five NHWC inputs** matched by name (case-insensitive):
  - `cropRgb` (RGB Crop, 3 channels)
  - `cropIr` (IR Crop, 1 channel)
  - `fullRgb` (Full RGB image, 3 channels)
  - `fullIr` (Full IR image, 1 channel)
  - `heatmap` (Face bounding box heatmap, 1 channel)
- Supported input types are `FLOAT32`, `UINT8`, and `INT8`.
- The model must have one `FLOAT32` or `INT8` output with shape `[1,6]`.
- Output indices are fixed in this order: `LIVE`, `PRINT`, `PICTURE`, `MASK`, `DISPLAY`, `PMASK` (must match `ClassificationResult.LABELS`). Internal class identifiers and capture paths use lowercase `pmask`.
- Spec JSONs control channel order (RGB/BGR), normalization values, delegate backend (`cpu`/`nnapi`), whether the output contains logits, and the crop margin ratio.
- Do not change preprocessing, output ordering, or tensor assumptions without updating the model contract and verifying them against the exported model.
- **Current deployment supports float and full INT8 models.** The standard model runs on CPU/XNNPACK, while the NPU model tries Android NNAPI first for NPU evaluation and falls back to CPU/XNNPACK if NNAPI model preparation fails. Do not report NPU acceleration as working until the on-device UI shows `Backend NNAPI` and latency is measured. Full history/decision: `docs/project_status.md` section 3 in the model repository (`E1jeong/access-liveness-model`).
- Target-board NNAPI status (observed 2026-07-01/02): the NPU-friendly INT8 export (`anti_spoofing_npu.tflite`) **does compile on NNAPI**, but vendor compilation takes ~165 s at startup (done in the background init thread). The standard full-INT8 export is kept on CPU because it fails with `ANEURALNETWORKS_BAD_DATA ... while adding operation`.
- **Do not enable NNAPI compilation caching** (`NnApiDelegate.Options.setCacheDir`/`setModelToken`): the VSI NPU driver on this board fails compilation with `File ... couldn't be opened for reading` + `ANEURALNETWORKS_OP_FAILED` when caching is set, breaking models that otherwise compile fine.
- Standard model expects ImageNet RGB normalization. NPU model expects `[-1, 1]` style normalization (`mean=[0.5]`, `std=[0.5]`).

## Device and Build Requirements

- This is an Android application with `minSdk 30`, `targetSdk 34`, and Java 17 source compatibility.
- The proprietary FaceMe dependency repository must be supplied through the `UBIO_MAVEN_URL` Gradle property.
- Face detection requires the configured FaceMe dependency and a valid `FACEME_LICENSE_KEY` Gradle property.
- Platform signing uses `UBIO_KEYSTORE_PATH`, `UBIO_KEY_ALIAS`, `UBIO_KEY_PASSWORD`, and `UBIO_STORE_PASSWORD` when provided.
- RGB-to-IR mapping requires `/sdcard/devlocal/CalibConfig.dat` on the device.
  - **Storage Permissions**: Since the app runs as `android.uid.system`, the Android FUSE filesystem/MediaProvider blocks direct file access to `/sdcard/` by default on API 30+. To bypass this, the app uses `UbimDaemonClient` at startup to programmatically grant itself `MANAGE_EXTERNAL_STORAGE` (`appops set com.virditech.ac7000 MANAGE_EXTERNAL_STORAGE allow`).
  - **Fallback Location**: If reading/writing to `/sdcard/devlocal/CalibConfig.dat` still fails, it transparently falls back to `appFile` at `getFilesDir()/CalibConfig.dat` (internal app storage).
- Capture images are saved under `/sdcard/Pictures/raw/<class>/<class>_<subject>/<index>/` with `RGB.bmp`, `cropRGB.bmp`, `IR.bmp`, and `cropIR.bmp`. Saving uses `MediaStore` with relative paths instead of direct `FileOutputStream` to `/sdcard`, because direct FUSE-path writes can fail on API 30+ system-UID builds.
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
- For NNAPI/NPU changes, verify the on-device backend label. With the NPU model selected, `Backend CPU` means NNAPI preparation failed and measurements are CPU/XNNPACK, not NPU.
- Hardware-dependent changes require manual verification on the target device. At minimum, check RGB and IR preview startup, frame pairing, calibration alignment, IR LED state, face detection, all six output probabilities, inference timing, and cleanup/restart across pause and resume.
- Calibration changes must also verify hidden-mode entry, single-face validation for both cameras, cancel-without-save, persisted alignment after restart, and compatibility with the production RGB-to-IR mapping formula.
- If hardware validation cannot be performed, state which checks remain unverified and the resulting risk.

## Troubleshooting & Evaluation Tips

### 1. 2-Input vs 5-Input Performance Characteristics
- **2-Input Model**: With only two tensors to preprocess and convert (RGB Crop, IR Crop), the Convert bottleneck is low. Combined with NPU (NNAPI) inference of about 20-30 ms, the app runs smoothly at **close to 7 FPS**.
- **5-Input Model**: Even with NPU acceleration working (`Inference ~50ms`), preprocessing the five tensor images (Full RGB, Full IR, Heatmap, and both crops) creates a severe CPU bottleneck (`Convert RGB/IR ~150ms`), dropping the overall frame rate to **about 3-4 FPS**.
- The numbers above were measured on 2026-07-01. On 2026-07-02 the preprocessing lookup tables (LUT), heatmap buffer caching, NV21 buffer reuse, chroma row bulk copy, and lock-free inference optimizations were applied, so re-measure on the target device before quoting them.

### 2. Evaluation Branch Management
- **`master` Branch**: Use to evaluate models with the legacy 2-input structure (RGB Crop, IR Crop).
- **`codex/keras-5-input-tflite` Branch**: Use to evaluate the latest 5-input model structure and to test the standard/NPU pre-warmed hot-swap toggle.

### 3. Tracking failed Debugging
- If a `Tracking failed` message appears at the bottom-left of the screen, an asynchronous exception (NPE, IllegalArgumentException, etc.) was thrown inside `processTracking`.
- Watch the detailed stack trace live with this ADB logcat command:
  ```bash
  adb logcat -s MainActivity:E *:S
  ```

### 4. Face Tracking Stalls During NPU Model Warmup
- While the NPU model's NNAPI compilation is running (~165 s per launch), the VSI NPU driver is monopolized. FaceMe detection is configured with `PREFER_NXP_DETECTION` and uses the same NPU, so `detectLargest` blocks and face tracking appears frozen until the compilation finishes.
- This is expected: the loading spinner intentionally stays visible until **both** models finish warming up and the FaceMe quality detector is available. Do not treat a frozen overlay during startup as a tracking bug; check the spinner and the `Model warmup completed` / `Face quality warmup completed` logcat lines first:
  ```bash
  adb logcat -s AntiSpoofingClassifier:I MainActivity:I
  ```

### 5. Capture Collection Notes
- `live` capture may appear paused while the face quality level is below the configured threshold. This is intentional: the current sample number is not consumed until the frame passes quality and all four BMP files are saved.
- Non-live captures do not run FaceMe quality checks. If those classes stall, debug frame pairing, IR availability, storage writes, or asynchronous `Tracking failed` errors instead of quality level.
- `CANCEL CAPTURE` only stops the active collection session. It does not roll back or delete a partial `<class>_<subject>` directory; any samples saved before the cancel remain on disk.
- For storage failures, watch:
  ```bash
  adb logcat -s MainActivity:E MainActivity:I
  ```
