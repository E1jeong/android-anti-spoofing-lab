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
5. Run the inputs required by the active manifest slot. Supported slot types are paired RGB/IR 1-input models, a dual 2-input model, or a 5-input model using RGB crop, IR crop, Full RGB, Full IR, and Heatmap.
6. Update the diagnostic crop preview (top-right) independently of pairing success using a copied frame buffer, throttled to a minimum interval of 66ms (~15 FPS).
7. Display the six-class probabilities, top result, conversion time, detection time, inference time, and processing FPS over the RGB or IR preview.
8. **Manifest Loading & Slot Switching**: On startup, every entry in `model_manifest.json` and the FaceMe quality detector are initialized in the loading phase. The loading spinner stays visible until model loading finishes **and** the quality detector reports available. Only the active slot runs per frame; switching selects an already loaded slot without reloading models. The current manifest has one paired 1-input slot, so its RGB and IR models run sequentially on the inference single-thread executor.
9. **Data Capture Collection**: The `START CAPTURE` flow writes exactly 100 valid samples per selected class. For `live` only, every candidate frame is checked against the selected high/medium FaceMe quality threshold; low-quality frames are skipped without incrementing the saved count or sector count. Non-live classes (`display`, `picture`, `print`, `mask`, `pmask`) bypass quality checking. Capture can be paused/resumed. Canceling invalidates queued work and deletes the current subject directory, including samples already saved in that session.

## Model Contract

- Model slots and their model/spec assets are declared in `app/src/main/assets/model_manifest.json`.
- Supported slot types are `paired_1_input`, `dual_2_input`, and `five_input`. A 1-input spec must declare `inputKind` as `rgb` or `ir`; 5-input specs map `cropRgb`, `cropIr`, `fullRgb`, `fullIr`, and `heatmap` by configured tensor-name substring.
- The current manifest contains one `paired_1_input` slot using `best_crop_rgb_fixed_npu_int8.tflite`/`model_spec_rgb.json` and `best_crop_ir_fixed_npu_int8.tflite`/`model_spec_ir.json`.
- The parser accepts `FLOAT32`, `UINT8`, and `INT8` inputs, but current deployment verification covers float and full INT8 only. UINT8 normalization/quantization semantics are not yet verified against an exported model.
- The model must have one `FLOAT32` or `INT8` output with shape `[1,6]`.
- Output indices are fixed in this order: `LIVE`, `PRINT`, `PICTURE`, `MASK`, `DISPLAY`, `PMASK` (must match `ClassificationResult.LABELS`). Internal class identifiers and capture paths use lowercase `pmask`.
- Spec JSONs control channel order (RGB/BGR), normalization values, delegate backend (`cpu`/`nnapi`), whether the output contains logits, and the crop margin ratio.
- Do not change preprocessing, output ordering, or tensor assumptions without updating the model contract and verifying them against the exported model.
- **Current deployment supports float and full INT8 models.** Each spec chooses `cpu` or `nnapi`. A CPU spec uses CPU/XNNPACK; an NNAPI setup failure currently fails that manifest slot instead of falling back to CPU. The desired long-term fallback policy is still open. Do not report acceleration until model warmup, the on-device backend label, logcat, and latency are checked.
- Model warmup invocation failures are currently logged without rejecting the loaded slot. Treat this as a known diagnostic gap: `Backend NNAPI` shows the requested interpreter path, not proof that every operation executed on the NPU.
- The previous paired RGB fold3 and IR fold4 INT8 configuration was observed on the target device with `Backend RGB NNAPI / IR NNAPI` and six-class output. The current manifest selects the fixed-split RGB/IR artifacts; this exact fixed/fixed pairing still requires target-device model-load, backend-label, probability-output, and latency/FPS verification. The latest overlay readability change also needs final visual verification.
- **Do not enable NNAPI compilation caching** (`NnApiDelegate.Options.setCacheDir`/`setModelToken`): the VSI NPU driver on this board fails compilation with `File ... couldn't be opened for reading` + `ANEURALNETWORKS_OP_FAILED` when caching is set, breaking models that otherwise compile fine.
- The current RGB and IR paired specs use `[0.5]` mean/std normalization and `delegate: nnapi`. Always verify preprocessing against the exact exported model assigned in the manifest.

## Device and Build Requirements

- This is an Android application with `minSdk 30`, `targetSdk 34`, and Java 17 source compatibility.
- The proprietary FaceMe dependency repository must be supplied through the `UBIO_MAVEN_URL` Gradle property.
- Face detection requires the configured FaceMe dependency and a valid `FACEME_LICENSE_KEY` Gradle property.
- Platform signing uses `UBIO_KEYSTORE_PATH`, `UBIO_KEY_ALIAS`, `UBIO_KEY_PASSWORD`, and `UBIO_STORE_PASSWORD` when provided.
- RGB-to-IR mapping requires `/sdcard/devlocal/CalibConfig.dat` on the device.
  - **Storage Permissions**: Since the app runs as `android.uid.system`, the Android FUSE filesystem/MediaProvider blocks direct file access to `/sdcard/` by default on API 30+. To bypass this, the app uses `UbimDaemonClient` at startup to programmatically grant itself `MANAGE_EXTERNAL_STORAGE` (`appops set com.virditech.ac7000 MANAGE_EXTERNAL_STORAGE allow`).
  - **Fallback Location**: If reading/writing to `/sdcard/devlocal/CalibConfig.dat` still fails, it transparently falls back to `appFile` at `getFilesDir()/CalibConfig.dat` (internal app storage).
- Capture samples use direct filesystem writes under `/sdcard/Pictures/raw` and contain `RGB.bmp`, `cropRGB.bmp`, `IR.bmp`, `cropIR.bmp`, and `meta.json`. `live` uses separate `live/high/live_<subject>` and `live/medium/live_<subject>` roots; other classes use `<class>/<class>_<subject>`. A write probe must succeed before capture starts, and there is no internal-storage fallback for capture data.
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
- For NNAPI/NPU changes, verify model warmup, the on-device backend label, logcat, and latency. In the current runtime, `Backend CPU` means the spec explicitly requested CPU/XNNPACK; NNAPI preparation failure rejects the slot. `Backend NNAPI` alone does not prove that every operation was delegated to the NPU.
- Hardware-dependent changes require manual verification on the target device. At minimum, check RGB and IR preview startup, frame pairing, calibration alignment, IR LED state, face detection, all six output probabilities, inference timing, and cleanup/restart across pause and resume.
- Calibration changes must also verify hidden-mode entry, single-face validation for both cameras, cancel-without-save, persisted alignment after restart, and compatibility with the production RGB-to-IR mapping formula.
- If hardware validation cannot be performed, state which checks remain unverified and the resulting risk.

## Troubleshooting & Evaluation Tips

### 1. 2-Input vs 5-Input Performance Characteristics
- **2-Input Model**: With only two tensors to preprocess and convert (RGB Crop, IR Crop), the Convert bottleneck is low. Combined with NPU (NNAPI) inference of about 20-30 ms, the app runs smoothly at **close to 7 FPS**.
- **5-Input Model**: Even with NPU acceleration working (`Inference ~50ms`), preprocessing the five tensor images (Full RGB, Full IR, Heatmap, and both crops) creates a severe CPU bottleneck (`Convert RGB/IR ~150ms`), dropping the overall frame rate to **about 3-4 FPS**.
- The numbers above were measured on 2026-07-01. On 2026-07-02 the preprocessing lookup tables (LUT), heatmap buffer caching, NV21 buffer reuse, chroma row bulk copy, and lock-free inference optimizations were applied, so re-measure on the target device before quoting them.

### 2. Evaluation Branch Management
- **`master` Branch**: Current manifest-based evaluator supporting paired 1-input, dual 2-input, and 5-input slots. The checked-in manifest currently selects only the paired fixed-split RGB/IR slot; this exact pairing is not yet verified on the target device.
- **`codex/keras-5-input-tflite` Branch**: Earlier experimental branch for the standard/NPU hot-swap and 5-input path. Do not copy its slot assumptions into `master` documentation.

### 3. Tracking failed Debugging
- If a `Tracking failed` message appears at the bottom-left of the screen, an asynchronous exception (NPE, IllegalArgumentException, etc.) was thrown inside `processTracking`.
- Watch the detailed stack trace live with this ADB logcat command:
  ```bash
  adb logcat -s MainActivity:E *:S
  ```

### 4. Face Tracking Stalls During NPU Model Warmup
- While the NPU model's NNAPI compilation is running (~165 s per launch), the VSI NPU driver is monopolized. FaceMe detection is configured with `PREFER_NXP_DETECTION` and uses the same NPU, so `detectLargest` blocks and face tracking appears frozen until the compilation finishes.
- This is expected for affected NNAPI models: the loading spinner intentionally stays visible until manifest model loading and FaceMe quality warmup finish. Do not treat a frozen overlay during startup as a tracking bug; check the spinner and model/quality warmup log lines first:
  ```bash
  adb logcat -s AntiSpoofingClassifier:I MainActivity:I
  ```

### 5. Capture Collection Notes
- `live` capture may appear paused while the face quality level is below the configured threshold. This is intentional: the current sample number is not consumed until the frame passes quality and all four BMP files are saved.
- Non-live captures do not run FaceMe quality checks. If those classes stall, debug frame pairing, IR availability, storage writes, or asynchronous `Tracking failed` errors instead of quality level.
- Cancel invalidates the active collection session and queued saves, then recursively deletes that session's current subject directory. Verify on device that no stale directory is recreated by late asynchronous work.
- For storage failures, watch:
  ```bash
  adb logcat -s MainActivity:E MainActivity:I
  ```
