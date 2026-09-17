# `vision` Module Guide

## Scope

- Own the UBio Vision anti-spoofing engine (`com.unionbiometrics.vision`).
- Load TFLite slots from this module's assets, preprocess RGB/IR crops, run NNAPI inference, and return `[1,12]` probabilities.
- Do not own camera, FaceMe, calibration files, capture, recognition, WebRTC, or UI.
- Do not depend on FaceMe, private Maven, licenses, or keystores.

## Orient First

All Java paths below are relative to `vision/src/main/java/com/unionbiometrics/vision/`.

- Slot loading and classify entry: `ModelSlotClassifier.java`.
- Preprocess and interpreter: `AntiSpoofingClassifier.java`, `ModelSpec.java`.
- Output contract: `ClassificationResult.java`, `SlotClassificationResult.java`.
- Crop helper used by hosts: `FaceCrop.java`.
- Assets: `vision/src/main/assets/ubio-vision/model_manifest.json`, matching `.tflite` and sidecar JSON in the same folder.

## Boundary & Architecture Constraints

1. Public classify input is `Bitmap rgb`, `Rect rgbBox`, `Bitmap ir`, `Rect irBox`. Keep the RGB arguments even when the active slot uses IR only.
2. Sidecar `normalization` / `quantization` is the runtime recipe for incoming 0–255 pixels. Resize to the tensor HxW, apply mean/std, then INT8 quantize. Do not treat a quantized `.tflite` as already-preprocessed camera input.
3. Model file and sidecar are one set in this module under `assets/ubio-vision/`. Do not load a host tflite with this module's sidecar, or the reverse. Do not place `model_manifest.json` at the host assets root; recognition uses a different fallback when that file is absent.
4. Output shape and class order must match `ClassificationResult.LABELS` (currently `[1,12]`). Reject legacy ten-class assets.
5. A manifest slot that fails NNAPI setup or warmup is rejected. No silent CPU fallback.
6. Never enable NNAPI compilation caching (`setCacheDir`/`setModelToken`).
7. Library `namespace` is `com.unionbiometrics.vision`. Do not use `com.virditech.ac7000` or add `sharedUserId`/camera permissions to this manifest.

## Change Gates

- Hosts must pass an `Application` context so merged assets resolve.
- `tensorflow-lite` stays `implementation`, not `api`.
- Lab Auth Mode accumulation and motion gating stay in the app module, not this library.

## Verify

```powershell
./gradlew.bat :vision:compileDebugJavaWithJavac
./gradlew.bat :vision:testDebugUnitTest
```
