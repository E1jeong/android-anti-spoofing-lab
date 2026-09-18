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
- Product-session state and probability averaging: `internal/session/`.
- Assets: `vision/src/main/assets/ubio-vision/model_manifest.json`, matching `.tflite` and sidecar JSON in the same folder.

## Boundary & Architecture Constraints

1. Hosts depend only on `com.unionbiometrics.vision.VisionSdk` and public types in `com.unionbiometrics.vision.api`. Packages under `com.unionbiometrics.vision.internal` are unsupported implementation details; Java cross-package bridge types are marked AndroidX library-only so consumer lint rejects their use.
2. `AntiSpoofingFrame` borrows both bitmaps for the duration of `process()`; the SDK never recycles host-owned frames. Keep both RGB and IR inputs even when the active slot uses IR only.
3. The default session contract requests IR illumination, waits 400 ms, and averages three probability vectors. `reset()`/`close()` clear session state and request IR off.
4. Sidecar `normalization` / `quantization` is the runtime recipe for incoming 0–255 pixels. Resize to the tensor HxW, apply mean/std, then INT8 quantize. Do not treat a quantized `.tflite` as already-preprocessed camera input.
5. Model file and sidecar are one set in this module under `assets/ubio-vision/`. Do not load a host tflite with this module's sidecar, or the reverse. Do not place `model_manifest.json` at the host assets root; recognition uses a different fallback when that file is absent.
6. Output shape and class order must match `ClassLabels.values()` (currently `[1,12]`). Reject legacy ten-class assets.
7. A manifest slot that fails NNAPI setup or warmup is rejected. No silent CPU fallback.
8. Never enable NNAPI compilation caching (`setCacheDir`/`setModelToken`).
9. Library `namespace` is `com.unionbiometrics.vision`. Do not use `com.virditech.ac7000` or add `sharedUserId`/camera permissions to this manifest.

## Change Gates

- Hosts must pass an `Application` context so merged assets resolve.
- `tensorflow-lite` stays `implementation`, not `api`.
- Lab Auth Mode's separate five-frame threshold accumulator and motion gating stay in the app module; they are not the SDK's default three-frame product session.

## Verify

```powershell
./gradlew.bat :vision:compileDebugJavaWithJavac
./gradlew.bat :vision:testDebugUnitTest
```
