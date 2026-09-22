# `vision` Module Guide

## Scope

- Own the UBio Vision anti-spoofing SDK (`com.unionbiometrics.vision`).
- Load TFLite slots from this module's assets, preprocess RGB/IR crops, run NNAPI inference, and return `[1,12]` probabilities.
- Do not own camera, FaceMe, calibration files, capture, recognition, WebRTC, or UI.
- Do not depend on FaceMe, private Maven, licenses, or keystores.

## Orient First

All Java paths below are relative to `vision/src/main/java/com/unionbiometrics/vision/`.

- Public loading façade: `VisionSdk.java`.
- Stable host API/SPI and result contract: `api/`.
- Asset access: `internal/asset/ModelAssetLoader.java`.
- Crop implementation: `api/FaceCrop.java`.
- Manifest, model slots, preprocess, and interpreter: `internal/model/`.
- Engine implementation and model/session composition: `internal/engine/`.
- Shared raw-frame inference and the Lab-only entry point: `internal/inference/`.
- Product-session state and probability averaging: `internal/session/`.
- Assets: `vision/src/main/assets/ubio-vision/model_manifest.json`, matching `.tflite` and sidecar JSON in the same folder.

## Boundary & Architecture Constraints

1. Product hosts depend only on `com.unionbiometrics.vision.VisionSdk` and public types in `com.unionbiometrics.vision.api`. Packages under `com.unionbiometrics.vision.internal` are unsupported implementation details; the in-repository Lab app alone uses `internal.inference.FrameInference` for raw per-frame evaluation.
2. `AntiSpoofingFrame` borrows both bitmaps for the duration of `process()`; the SDK never recycles host-owned frames. Keep both RGB and IR inputs even when the active slot uses IR only.
3. The default session contract requests IR illumination, waits 400 ms, and averages three probability vectors. `reset()`/`close()` clear session state and request IR off.
4. Sidecar `normalization` / `quantization` is the runtime recipe for incoming 0–255 pixels. Resize to the tensor HxW, apply mean/std, then INT8 quantize. Do not treat a quantized `.tflite` as already-preprocessed camera input.
5. Model file and sidecar are one set in this module under `assets/ubio-vision/`. Do not load a host tflite with this module's sidecar, or the reverse. Do not place `model_manifest.json` at the host assets root; recognition uses a different fallback when that file is absent.
6. Supported slots are IR `single_1_input` (`ir@0`, one channel) and RGB+IR `dual_2_input` (distinct RGB/IR indices 0/1, three/one channels). Build-time asset validation and runtime parsing reject RGB-only, paired one-input, five-input, and additional-input forms.
7. Output shape and class order must match `ClassLabels.values()` (currently `[1,12]`). Reject legacy ten-class assets.
8. Each `AntiSpoofingEngine` owns its immutable `EngineInfo`; do not recreate parallel engine/metadata lists. `ProbabilityResult` contains classification data only, while internal `InferenceResult` owns Lab per-frame timing and `AntiSpoofingResult` owns the most recent accepted sample timing. Do not restore separate RGB/IR result branches or expose raw inference on `AntiSpoofingEngine`.
9. A manifest slot that fails NNAPI setup or warmup is rejected. No silent CPU fallback.
10. Never enable NNAPI compilation caching (`setCacheDir`/`setModelToken`).
11. Library `namespace` is `com.unionbiometrics.vision`. Do not use `com.virditech.ac7000` or add `sharedUserId`/camera permissions to this manifest.

## Change Gates

- Hosts must pass an `Application` context so merged assets resolve.
- `tensorflow-lite` stays `implementation`, not `api`.
- Lab Auth Mode's separate five-frame threshold accumulator and motion gating stay in the app module; they are not the SDK's default three-frame product session.

## Verify

```powershell
./gradlew.bat :vision:compileDebugJavaWithJavac
./gradlew.bat :vision:testDebugUnitTest
```
