# Model Deployment Verification

Version-bound execution checklist for the checked-out Android evaluator. Resolve the project wiki through root `AGENTS.md`; `features/model-contract-branches` owns the contracts, rationale, and dated results. Record the tested commit, APK/model hashes, device, scenario, and outcome. A documentation review does not rerun these checks.

## Validation

- Verify changed model files load and their input/output tensors match this contract.
- For NNAPI/NPU changes, verify warmup, on-device backend label, logcat, and latency. Do not report acceleration from the backend label alone.
- Hardware verification must include all twelve probabilities, RGB/IR individual inference timing, pair FPS, and the latest overlay/UI.
- During affected NNAPI compilation, the VSI NPU may be monopolized for roughly 165 seconds. FaceMe detection uses `PREFER_NXP_DETECTION` on the same NPU, so tracking may appear frozen until warmup finishes. Check:

```bash
adb logcat -s AntiSpoofingClassifier:I MainActivity:I
```
