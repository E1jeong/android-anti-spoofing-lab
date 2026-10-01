# Vision SDK Contract

Product hosts use `com.unionbiometrics.vision.AntiSpoofingEngine`,
`AntiSpoofingResult`, and `AntiSpoofingCallback`. Load and warm one zero-based manifest
slot on a background thread:

```java
AntiSpoofingEngine engine = AntiSpoofingEngine.create(applicationContext);
// To choose another slot: create(applicationContext, Options.defaults().withSlotIndex(index)).
AntiSpoofingEngine.Frame frame = AntiSpoofingEngine.Frame.ir(irBitmap, irFaceBox);
AntiSpoofingResult result = engine.process(frame);
```

Use `Frame.dual(rgbBitmap, rgbFaceBox, irBitmap, irFaceBox, rgbTimestampNs,
irTimestampNs)` for a two-input slot. Pass unexpanded face boxes. The host pairs
RGB and IR frames and controls IR illumination across every terminal, reset,
failure, and close path. Check `requiresRgb()` for the selected slot.

The default session discards ten incoming frames, then averages three probability
vectors before returning `LIVE` or `SPOOF`. Earlier calls return `PENDING`;
inference failures return `ERROR`. `inferenceMs()` is null when no inference ran.
The result also exposes `probabilities()`, `topIndex()`, `score()`, `attack()`,
`displayLabel()`, `settleRemaining()`, and `acceptedSamples()`. Call `reset()`
after authentication or cancellation and `close()` at host teardown.

`process()` borrows frame bitmaps for its synchronous call; keep them alive until
it returns. For asynchronous inference, create the engine with
`Options.defaults().live(callbackExecutor)` and call `submit(frame, callback)`.
`submit()` copies the expanded crops before returning, so the host can release
its bitmaps afterward. Only the newest waiting frame is kept; a superseded or
reset frame gets no callback. A null executor sends callbacks to the Android main looper.
Run loading and synchronous inference off the main thread.

A manifest slot that fails NNAPI setup or warmup is rejected without CPU
fallback. Model label, backend, and crop margin are available from `label()`,
`backend()`, and `cropMarginRatio()`. Packages under
`com.unionbiometrics.vision.internal` are unsupported implementation details;
the in-repository Lab app uses `internal.DemoInferenceEngine` for raw
multi-slot evaluation.
