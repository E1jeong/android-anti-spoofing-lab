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

- `Motion gate` diagnostics are throttled to 250ms and report RGB face speed, edge/movement state, stable-frame count, inference allowance, and RGB/IR pair delta. Use them to tune only from target-device evidence.

## Historical Baseline

- Before the 2026-07-02 preprocessing optimizations, the 2-input model ran near 7 FPS with roughly 20-30ms NNAPI inference.
- The 5-input model ran near 3-4 FPS even with roughly 50ms inference because five-input conversion cost was around 150ms.
- These 2026-07-01 measurements predate LUT, heatmap caching, NV21 reuse, chroma-row bulk copy, lock-free inference, detailed latency logging, BMP/preview allocation reduction, single FaceMe extraction, and capture bitmap-copy removal. Do not quote them as current performance.

## Current Optimization State

- Model preprocessing reuses scaled bitmaps, pixel/scratch arrays, direct buffers, normalization/quantization LUTs, and heatmap results.
- Tracking and inference use latest-wins queues to prevent latency accumulation.
- The motion gate invalidates a pending or completed earlier inference result when movement starts; preserve that generation boundary when changing queue ownership or result delivery.
- Live save candidates share one FaceMe extraction between tracking and quality data.
- Capture I/O owns detached source frames and writes full/crop BMPs with a reusable 16-row buffer, removing two full-frame copies and two crop bitmap creations per save attempt.
- The throttled IR diagnostic preview reuses one ARGB bitmap/Canvas while dimensions remain stable, but still performs a full-frame copy.
- These changes passed 17 JVM tests and Java compilation. Target-device verification now covers the refactored RGB/IR UI, fixed IR inference, live HIGH/MEDIUM gate application, a complete 100-sample five-file audit, capture-save latency, and long-duration memory stability. This is a current-build baseline, not a comparable before/after speedup measurement.

## 2026-07-20 Target-Device Observation

- The fixed IR `single_1_input` slot reached `Ready`, rendered six-class output, and logged one NNAPI delegate partition replacing all 66 TFLite nodes. Warmup completed in 11,372 ms.
- From inference sample 3,150 to 7,320, the app completed 4,170 results in eight minutes (about 8.7/s). Recent-120-sample ranges were preprocess P50 4-5 ms/P95 12-17 ms, invoke P50 10-11 ms/P95 17-22 ms, inference queue P50 0 ms/P95 0-2 ms, and tracking-to-result P50 73-79 ms/P95 101-122 ms.
- The observation showed no sustained queue growth or time-dependent latency degradation. It is not a speedup claim because no pre-optimization APK was measured under the same conditions.
- GC pauses were short and reclaimed allocations normally. One post-restart sample showed Java Heap PSS 12,856 KiB, Native Heap PSS 185,124 KiB, and Total PSS 245,254 KiB; one point cannot establish leak behavior.
- Repeated Settings transitions on the 2026-07-20 APK failed with the camera teardown `SIGSEGV` documented in `device-runtime.md`. Commit `6a9d6ce` later moved `ImageReader`/preview release behind `CameraDevice.onClosed()`; the same five-cycle Settings test then passed without teardown warnings, crashes, restarts, or recovery failures. This closes the reproduced P0 at that five-cycle scope, not the broader 20-cycle baseline below.

## 2026-07-28 Capture and Memory Closure

- A target-device normal capture completed all 100 samples, and every sample was audited for `RGB.bmp`, `cropRGB.bmp`, `IR.bmp`, `cropIR.bmp`, and `meta.json`.
- Capture-save latency remained stable through 60 measured saves: P50 stayed at 197-210 ms and P95 settled at 282-287 ms after an initial 366 ms observation. Concurrent inference remained stable with queue P50/P95 at 0/1 ms.
- Live collection applied the configured HIGH (`> 0.9`) and MEDIUM (`> 0.6`) gates on hardware. ATTACK rejected observed 70%-range results, while the exact 79.9% reject and 80.0%/80.1% accept boundary remains covered by JVM tests because a rapidly changing on-screen probability is not a reliable hardware oracle.
- Two `dumpsys meminfo` snapshots from the same PID, 67 minutes 50 seconds apart under continued inference, changed Total PSS from 302,300 KiB to 305,594 KiB and Native Heap PSS from 223,061 KiB to 226,161 KiB. Java Heap PSS changed from 15,040 KiB to 15,416 KiB; Activity, View, ViewRoot, and Binder counts stayed constant and swap remained zero. No continuing-growth or object-accumulation signal was observed at this scope.

## Optimization Boundaries

- FaceMe detection and model NNAPI share the same NPU. Parallel execution can be slower; compare on hardware before changing executor topology.
- The current PReLU MobileFaceNet artifact measures about 300 ms on CPU versus 400–500 ms through NNAPI. Use CPU as the current baseline and NNAPI only for an explicit same-input comparison; these observations do not yet establish recognition accuracy or production suitability.
- Keep recognition on a dedicated executor so its preprocessing, embedding, and search timing can be measured without blocking or being attributed to anti-spoofing inference.
- During independent recognition validation, do not use the primary `LIVE` result, a fixed 300 ms interval, or latest-wins replacement to select test samples. Account for every accepted enrollment/query request with a result, explicit error, or explicit cancellation so alignment, embedding stability, and score distributions are auditable.
- Anti-spoofing evaluation neither precedes nor follows recognition evaluation. Its training/export loop is owned by `access-liveness-model`; recognition currently evaluates an acquired pretrained model and must finish its own standalone measurements without waiting for or consuming liveness results.
- A recognition task owns only its aligned 112x112 bitmap and must recycle it exactly once after completion, invalidation, or shutdown; never retain the source `FramePair` beyond its existing owner.
- On-screen `Inference` timing covers only TFLite invoke, excluding preprocessing, detection, pairing, queue wait, and UI work.
- Holding source frames during capture reduces allocation but occupies one entry from each four-bitmap portrait pool until I/O completes. Re-check pool pressure and frame drops when capture resolution, writer topology, or file count changes.
- Do not enable NNAPI compilation caching; see `model-contract.md`.
- Preserve each existing queue's ownership and recycle behavior when modifying concurrency; do not extend anti-spoofing queue policy to recognition validation without a separately approved integration design.

## Required Device Baseline

- Collect preprocess, invoke, inference queue, tracking-to-result, capture-save P50/P95, processing FPS, Java/native heap, and GC.
- Include fixed IR standalone model loading, ten-class output, RGB/IR preview and crop, overlay/UI, camera-open termination, at least 20 pause/resume cycles, warmup termination, and a 100-sample capture with pause/resume/cancel.
- Verify live HIGH/MEDIUM acceptance, non-live bypass, BMP output, metadata, portrait-pool pressure, and stale-directory prevention.
- The fixed IR latency, five-cycle teardown regression, 100-sample file audit, capture-save timing, quality-gate application, refactored UI/IR regression, MediaPipe-to-inference path, and 67-minute memory comparison have been verified on the current build. Do not infer an optimization speedup without a comparable pre-optimization APK, or full 20-cycle lifecycle coverage from the accepted five-cycle teardown scope.
