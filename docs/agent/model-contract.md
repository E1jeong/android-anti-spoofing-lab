# Model Contract Guidance

Read this document before changing models, manifests, tensor mapping, preprocessing, delegates, output handling, model switching, or model-result UI.

## Manifest and Slot Runtime

- Model slots and their model/spec assets are declared in `app/src/main/assets/model_manifest.json`.
- Supported slot types are `paired_1_input`, `dual_2_input`, and `five_input`. A 1-input spec must declare `inputKind` as `rgb` or `ir`; 5-input specs map `cropRgb`, `cropIr`, `fullRgb`, `fullIr`, and `heatmap` by configured tensor-name substring.
- On startup, every manifest entry and the FaceMe quality detector are initialized during the loading phase. The spinner remains visible until model loading finishes and the quality detector reports available.
- Only the active preloaded slot runs per frame. The current manifest has one `paired_1_input` slot, so its RGB and IR models run sequentially on the inference single-thread executor.
- The current slot uses `best_crop_rgb_fixed_npu_int8.tflite` with `best_crop_rgb_fixed_npu_int8_manifest.json` and `best_crop_ir_fixed_npu_int8.tflite` with `best_crop_ir_fixed_npu_int8_manifest.json`.
- `ModelSpec` supports both legacy spec JSON and generated sidecar manifests; do not assume the two schemas expose inputs in the same JSON shape.

## Tensor and Output Contract

- The parser accepts `FLOAT32`, `UINT8`, and `INT8` inputs, but current deployment verification covers float and full INT8 only. UINT8 normalization/quantization semantics are not yet verified against an exported model.
- The model must have one `FLOAT32` or `INT8` output with shape `[1,6]`.
- Output indices are fixed in this order: `LIVE`, `PRINT`, `PICTURE`, `MASK`, `DISPLAY`, `PMASK`. They must match `ClassificationResult.LABELS`; internal class identifiers and capture paths use lowercase `pmask`.
- Spec JSONs control channel order (RGB/BGR), normalization values, delegate backend (`cpu`/`nnapi`), whether output contains logits, and crop margin ratio.
- The current RGB and IR paired specs use `[0.5]` mean/std normalization and `delegate: nnapi`.
- Do not change preprocessing, output ordering, tensor assumptions, or normalization without updating the contract and verifying against the exact exported model assigned in the manifest.

## Delegate Policy

- Current deployment supports float and full INT8 models. A CPU spec uses CPU/XNNPACK.
- An NNAPI setup or model warmup failure rejects the manifest slot instead of falling back to CPU. This no-fallback policy is intentional for the NPU evaluation tool.
- `Backend NNAPI` shows the requested interpreter path after successful allocation and warmup; it does not prove every operation executed on the NPU.
- Do not enable NNAPI compilation caching with `NnApiDelegate.Options.setCacheDir` or `setModelToken`. The VSI driver fails compilation with `File ... couldn't be opened for reading` and `ANEURALNETWORKS_OP_FAILED` when caching is enabled.

## Branch and Deployment State

- `master` is the current manifest-based evaluator supporting paired 1-input, dual 2-input, and 5-input slots. The checked-in manifest selects only the paired fixed-split RGB/IR slot.
- `codex/keras-5-input-tflite` is an earlier standard/NPU hot-swap and 5-input experiment. Do not copy its slot assumptions into `master` documentation.
- The previous RGB fold3/IR fold4 INT8 pairing was observed on target hardware with both NNAPI backend labels and six-class output.
- The current fixed/fixed pairing still requires target-device model-load, backend-label, probability-output, latency/FPS, and latest overlay readability verification.

## Validation

- Verify changed model files load and their input/output tensors match this contract.
- For NNAPI/NPU changes, verify warmup, on-device backend label, logcat, and latency. Do not report acceleration from the backend label alone.
- Hardware verification must include all six probabilities, RGB/IR individual inference timing, pair FPS, and the latest overlay/UI.
- During affected NNAPI compilation, the VSI NPU may be monopolized for roughly 165 seconds. FaceMe detection uses `PREFER_NXP_DETECTION` on the same NPU, so tracking may appear frozen until warmup finishes. Check:

```bash
adb logcat -s AntiSpoofingClassifier:I MainActivity:I
```
