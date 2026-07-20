# Performance and Diagnostics Guidance

Read this document before changing performance instrumentation, allocations, concurrency, frame ownership, preprocessing, or benchmark reporting.

## Measurement Rules

- Do not claim a speedup from static analysis or a successful compile. Re-measure on target hardware.
- `MainActivity:I` logs a rolling 120-sample P50/P95 summary for model preprocess, TFLite invoke, inference queue wait, and tracking-queue-to-result time every 30 inferences.
- Capture-save P50/P95 is logged every 10 attempted saves.
- Keep detailed timing out of the on-screen diagnostics. Collect it with:

```bash
adb logcat -s MainActivity:I
```

## Historical Baseline

- Before the 2026-07-02 preprocessing optimizations, the 2-input model ran near 7 FPS with roughly 20-30ms NNAPI inference.
- The 5-input model ran near 3-4 FPS even with roughly 50ms inference because five-input conversion cost was around 150ms.
- These 2026-07-01 measurements predate LUT, heatmap caching, NV21 reuse, chroma-row bulk copy, lock-free inference, detailed latency logging, BMP/preview allocation reduction, single FaceMe extraction, and capture bitmap-copy removal. Do not quote them as current performance.

## Current Optimization State

- Model preprocessing reuses scaled bitmaps, pixel/scratch arrays, direct buffers, normalization/quantization LUTs, and heatmap results.
- Tracking and inference use latest-wins queues to prevent latency accumulation.
- Live save candidates share one FaceMe extraction between tracking and quality data.
- Capture I/O owns detached source frames and writes full/crop BMPs with a reusable 16-row buffer, removing two full-frame copies and two crop bitmap creations per save attempt.
- The throttled IR diagnostic preview reuses one ARGB bitmap/Canvas while dimensions remain stable, but still performs a full-frame copy.
- These changes passed 17 JVM tests and Java compilation. Target-device UI, image acquisition, fixed IR inference latency, and GC behavior were partially verified on 2026-07-20; quality acceptance, capture-save timing, a documented 100-sample file audit, and comparable before/after memory remain open.

## 2026-07-20 Target-Device Observation

- The fixed IR `single_1_input` slot reached `Ready`, rendered six-class output, and logged one NNAPI delegate partition replacing all 66 TFLite nodes. Warmup completed in 11,372 ms.
- From inference sample 3,150 to 7,320, the app completed 4,170 results in eight minutes (about 8.7/s). Recent-120-sample ranges were preprocess P50 4-5 ms/P95 12-17 ms, invoke P50 10-11 ms/P95 17-22 ms, inference queue P50 0 ms/P95 0-2 ms, and tracking-to-result P50 73-79 ms/P95 101-122 ms.
- The observation showed no sustained queue growth or time-dependent latency degradation. It is not a speedup claim because no pre-optimization APK was measured under the same conditions.
- GC pauses were short and reclaimed allocations normally. One post-restart sample showed Java Heap PSS 12,856 KiB, Native Heap PSS 185,124 KiB, and Total PSS 245,254 KiB; one point cannot establish leak behavior.
- Repeated Settings transitions failed with the camera teardown `SIGSEGV` documented in `device-runtime.md`. Fix that P0 lifecycle race before collecting more lifecycle or long-duration memory evidence from the affected APK.

## Optimization Boundaries

- FaceMe detection and model NNAPI share the same NPU. Parallel execution can be slower; compare on hardware before changing executor topology.
- On-screen `Inference` timing covers only TFLite invoke, excluding preprocessing, detection, pairing, queue wait, and UI work.
- Holding source frames during capture reduces allocation but occupies one entry from each four-bitmap portrait pool until I/O completes. Verify pool pressure and frame drops.
- Do not enable NNAPI compilation caching; see `model-contract.md`.
- Preserve latest-wins ownership and recycle behavior when modifying concurrency.

## Required Device Baseline

- Collect preprocess, invoke, inference queue, tracking-to-result, capture-save P50/P95, processing FPS, Java/native heap, and GC.
- Include fixed IR standalone model loading, six-class output, RGB/IR preview and crop, overlay/UI, camera-open termination, at least 20 pause/resume cycles, warmup termination, and a 100-sample capture with pause/resume/cancel.
- Verify live HIGH/MEDIUM acceptance, non-live bypass, BMP output, metadata, portrait-pool pressure, and stale-directory prevention.
- The 2026-07-20 fixed IR latency observation and user-confirmed image acquisition satisfy only part of this baseline. Do not infer capture-save performance, quality-gate correctness, 100-sample file integrity, memory stability, or lifecycle safety from those results.
