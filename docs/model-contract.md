# Model Contract Guidance

Read this document before changing models, manifests, tensor mapping, preprocessing, delegates, output handling, model switching, or model-result UI.

## Manifest and Slot Runtime

- Model slots and their model/spec assets are declared in the local ignored `app/src/main/assets/model_manifest.json`.
- Supported slot types are `single_1_input`, `paired_1_input`, `dual_2_input`, and `five_input`. A 1-input spec must declare `inputKind` as `rgb` or `ir`; 5-input specs map `cropRgb`, `cropIr`, `fullRgb`, `fullIr`, and `heatmap` by configured tensor-name substring.
- On startup, every manifest entry and the FaceMe quality detector are initialized during the loading phase. FaceDetector performs a dummy-frame warmup in its constructor to cover FaceMe NPU cold-start; the spinner remains visible until model loading and FaceMe NPU warmup finish and the quality detector reports available.
- Only the active preloaded slot runs per frame on the inference single-thread executor; the model-switch button selects it. The current local evaluation manifest registers two `single_1_input` IR slots and one `dual_2_input` RGB+IR slot.
- `ModelSpec` supports both legacy spec JSON and generated sidecar manifests; do not assume the two schemas expose inputs in the same JSON shape.
- A generated sidecar is consumed only through `inputs[].input_kind`, `inputs[].index`, `inputs[].shape`, `normalization.mean`/`std`, `outputs[].output_is_logits`, `crop_margin_ratio`, and `delegate`. `file_name` and `normalization.range` are descriptive and ignored, so never gate behavior on them; derive `dual_2_input` RGB/IR tensor indices from `input_kind`, not from the legacy top-level `rgbInputIndex`/`irInputIndex` keys that generated sidecars do not carry.

## Tensor and Output Contract

- The parser accepts `FLOAT32`, `UINT8`, and `INT8` inputs, but current deployment verification covers float and full INT8 only. UINT8 normalization/quantization semantics are not yet verified against an exported model.
- The model must have one `FLOAT32` or `INT8` output with shape `[1,12]`.
- Output indices are fixed in this order: `LIVE`, `PRINT`, `PICTURE`, `MASK`, `DISPLAY`, `PMASK`, `CURVED_PRINT`, `CURVED_MASK`, `CURVED_PICTURE`, `CURVED_PMASK`, `DENTAL_WHITE`, `DENTAL_BLACK`. They must match `ClassificationResult.LABELS`; internal class identifiers and capture paths use lowercase names.
- Spec JSONs control channel order (RGB/BGR), normalization values, delegate backend (`cpu`/`nnapi`), whether output contains logits, and crop margin ratio.
- The current IR spec uses `[0.5]` mean/std normalization and `delegate: nnapi`.
- Do not change preprocessing, output ordering, tensor assumptions, or normalization without updating the contract and verifying against the exact exported model assigned in the manifest.

## Delegate Policy

- Current deployment supports float and full INT8 models. A CPU spec uses CPU/XNNPACK.
- An NNAPI setup or model warmup failure rejects the manifest slot instead of falling back to CPU. This no-fallback policy is intentional for the NPU evaluation tool.
- `Backend NNAPI` shows the requested interpreter path after successful allocation and warmup; it does not prove every operation executed on the NPU.
- Do not enable NNAPI compilation caching with `NnApiDelegate.Options.setCacheDir` or `setModelToken`. The VSI driver fails compilation with `File ... couldn't be opened for reading` and `ANEURALNETWORKS_OP_FAILED` when caching is enabled.

## Branch and Deployment State

- `master` is the current manifest-based evaluator supporting single 1-input, paired 1-input, dual 2-input, and 5-input slots. Model files, sidecar JSON, and `model_manifest.json` are ignored local assets; a fresh checkout has no selected slot until they are provisioned.
- `codex/keras-5-input-tflite` is an earlier standard/NPU hot-swap and 5-input experiment. Do not copy its slot assumptions into `master` documentation.
- The previous RGB fold3/IR fold4 INT8 pairing was observed on target hardware with both NNAPI backend labels and six-class output.
- The local fixed IR 1-input asset remains a 10-class NPU-friendly INT8 export until the 12-class artifact and sidecar are copied into `assets/`; it will be rejected by the current `[1,12]` output contract. The replacement model must use IR input `[1,224,224,1]`, INT8 output logits `[1,12]`, and the fixed class order. Target-device NNAPI warmup, 12 probabilities, latency/FPS, and overlay verification remain required.

## Validation

- Verify changed model files load and their input/output tensors match this contract.
- For NNAPI/NPU changes, verify warmup, on-device backend label, logcat, and latency. Do not report acceleration from the backend label alone.
- Hardware verification must include all ten probabilities, RGB/IR individual inference timing, pair FPS, and the latest overlay/UI.
- During affected NNAPI compilation, the VSI NPU may be monopolized for roughly 165 seconds. FaceMe detection uses `PREFER_NXP_DETECTION` on the same NPU, so tracking may appear frozen until warmup finishes. Check:

```bash
adb logcat -s AntiSpoofingClassifier:I MainActivity:I
```
