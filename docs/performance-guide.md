# Performance and Recognition Measurement

Version-bound execution checklist for the checked-out Android evaluator. Resolve the project wiki through root `AGENTS.md`; `technical/code-structure-performance-diagnosis` and `technical/mobilefacenet-recognition-experiment` owns the contracts, rationale, and dated results. Record the tested commit, APK/model hashes, device, scenario, and outcome. A documentation review does not rerun these checks.

## Measurement Rules

- Do not claim a speedup from static analysis or a successful compile. Re-measure on target hardware.
- `MainActivity:I` logs a rolling 120-sample P50/P95 summary for model preprocess, TFLite invoke, inference queue wait, and tracking-queue-to-result time every 30 inferences.
- Capture-save P50/P95 is logged every 10 attempted saves.
- Keep detailed timing out of the on-screen diagnostics. Collect it with:

```bash
adb logcat -s MainActivity:I
```

- `Motion gate` diagnostics are throttled to 250ms and report RGB face speed, edge/movement state, stable-frame count, inference allowance, and RGB/IR pair delta. Use them to tune only from target-device evidence.

## Required Device Baseline

- Collect preprocess, invoke, inference queue, tracking-to-result, capture-save P50/P95, processing FPS, Java/native heap, and GC.
- Include fixed IR standalone model loading, current twelve-class output, RGB/IR preview and crop, overlay/UI, camera-open termination, at least 20 pause/resume cycles, warmup termination, and a 100-sample capture with pause/resume/cancel.
- Verify live HIGH/MEDIUM acceptance, non-live bypass, BMP output, metadata, portrait-pool pressure, and stale-directory prevention.
- The fixed IR latency, five-cycle teardown regression, 100-sample file audit, capture-save timing, quality-gate application, refactored UI/IR regression, MediaPipe-to-inference path, and 67-minute memory comparison have been verified at the dated 2026-07-20/28 baseline. Do not infer an optimization speedup without a comparable pre-optimization APK, or full 20-cycle lifecycle coverage from the accepted five-cycle teardown scope.

## Fixed-Input Face-Recognition Diagnostic

- Put one or more already-aligned 112x112 PNG, JPG, or BMP files in `/sdcard/Pictures/recognition-fixed-input/`. Keep biometric images outside Git and the APK.
- Wait for normal engine loading to finish, open the hidden test menu, and select `FIXED-INPUT RECOG TEST`.
- The separate diagnostic Activity pauses the main camera pipeline, then runs the active recognition model five times per input on requested CPU and requested NNAPI paths.
- The result JSON in the same directory records model/input hashes, device/app identity, requested versus active delegate, tensor contract, per-run latency, P50/P95, embedding norms, repeat cosine values, and CPU-versus-NNAPI cosine values. When NNAPI falls back to CPU, cross-delegate comparison is marked not applicable.
