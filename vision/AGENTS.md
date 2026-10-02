# `vision` Module Guide

## Scope

- Own the UBio Vision anti-spoofing SDK (`com.unionbiometrics.vision`).
- Load TFLite slots from this module's assets, preprocess RGB/IR crops, run NNAPI inference, and return `[1,12]` probabilities.
- Do not own camera, FaceMe, calibration files, capture, recognition, WebRTC, or UI.
- Do not depend on FaceMe, private Maven, licenses, or keystores.

## Orient First

All Java paths below are relative to `vision/src/main/java/com/unionbiometrics/vision/`.

- Public loading and session façade: `AntiSpoofingEngine.java`; public callback and result: `AntiSpoofingCallback.java`, `AntiSpoofingResult.java`.
- Model loading (`ModelLoader`), inference (`Classifier`), and session implementation (`SessionController`): package-private classes beside the public facade. Shared output labels and input counts live in `VisionConstants`.
- The Lab uses `AntiSpoofingEngine.process(...)` for return-valued raw-frame evaluation and `AntiSpoofingEngine.slotCount(...)` for slot discovery.
- Assets: `vision/src/main/assets/ubio-vision/model_manifest.json`, matching `.tflite` and sidecar JSON in the same folder.

## Boundary & Architecture Constraints

1. Hosts use `AntiSpoofingEngine`, `AntiSpoofingResult`, and `AntiSpoofingCallback`. Other classes in `com.unionbiometrics.vision` are package-private implementation details; the Lab app must not use them.
2. `create(context, slotIndex, settleFrames, sampleCount)` loads one slot. `process(rgb, rgbFace, ir, irFace)` returns one raw frame for the Lab without advancing the session. `infer(rgb, rgbFace, ir, irFace, callback)` copies expanded crops before returning and delivers one terminal decision or error on the main looper; pass null RGB inputs for an IR-only slot. The SDK never recycles host-owned frames. Hosts pair RGB/IR frames.
3. The host owns IR illumination. The default session discards ten incoming frames and averages three probability vectors; `reset()`/`close()` clear SDK session state only.
4. Sidecar `normalization` / `quantization` is the runtime recipe for incoming 0–255 pixels. Resize to the tensor HxW, apply mean/std, then INT8 quantize. Do not treat a quantized `.tflite` as already-preprocessed camera input.
5. Model file and sidecar are one set in this module under `assets/ubio-vision/`. Do not load a host tflite with this module's sidecar, or the reverse. Do not place `model_manifest.json` at the host assets root; recognition uses a different fallback when that file is absent.
6. Supported slots use generated `inputs[]` sidecars: IR `single_1_input` (`ir@0`, one channel) and RGB+IR `dual_2_input` (distinct RGB/IR indices 0/1, three/one channels). Build-time asset validation and runtime parsing reject legacy sidecars, RGB-only, paired one-input, five-input, and additional-input forms.
7. Output shape and class order must match `VisionConstants.CLASS_LABELS` (currently `[1,12]`). Reject legacy ten-class assets.
8. Each `AntiSpoofingEngine` owns one model slot. `AntiSpoofingResult` carries raw frame timing or callback session status, probabilities, score, counts, and error. Do not restore separate RGB/IR result branches.
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
