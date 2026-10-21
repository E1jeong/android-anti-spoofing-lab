# `face-detection` Module Guide

## Scope

- Standalone RGB YuNet TFLite library with box-only v1 and optional five-point v2 output. No dependency on `app` or `vision`.
- Does not own cameras, IR detection, Calibration, face quality, recognition, or PAD decisions.

## Orient First

- Public API: `src/main/java/com/unionbiometrics/facedetection/YuNetFaceDetector.java` and `FaceDetection.java`.
- Contract and decoding: `ModelContract.java`, `Letterbox.java`, `YuNetPostprocessor.java` in the same package.
- Models and export manifest: `src/main/assets/ubio-face-detection/` (Git-ignored; supplied locally as one export set).

## Boundary & Architecture Constraints

- `create(applicationContext, mode)`, `detect(rgbBitmap)`, and `close()` run off the main thread. Detection and close are serialized; input bitmaps remain host-owned.
- Default `INT8_NNAPI` and comparison `FLOAT_CPU` are explicit modes. Never retry an NNAPI failure on CPU or report NPU coverage without device evidence.
- The export manifest owns RGB pixel range, centered letterbox, tensor quantization, stride/channel ordering, score and NMS thresholds. Validate artifact hashes and live tensors against it.
- Keep logits `[1,N,2]`, boxes `[1,N,4]` and v2 landmarks `[1,N,10]` separate; each INT8 output has independent quantization. Tensor graph indices in the manifest are not Java output slots.
- Return source-coordinate boxes/confidence and available immutable named points, without display mirroring or PAD crop margins. V1 points are empty; v2 uses the manifest point order and shared box/NMS indices, with no visibility/quality estimate. The app owns RGB-to-IR mapping and rejects YuNet for RGB/IR calibration measurement.

## Verify

Use the root guide's JDK 21 selection, then:

```powershell
./gradlew.bat :face-detection:compileDebugJavaWithJavac
./gradlew.bat :face-detection:testDebugUnitTest
```
