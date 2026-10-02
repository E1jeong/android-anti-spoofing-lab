# Vision SDK Contract

Product hosts use `AntiSpoofingEngine`, `AntiSpoofingResult`, and
`AntiSpoofingCallback`. Load one zero-based manifest slot on a background thread:

```java
AntiSpoofingEngine engine = AntiSpoofingEngine.create(applicationContext, 0, 10, 3);
engine.infer(null, null, irBitmap, irFaceBox, result -> {
    // Handle the completed decision or error on the Android main looper.
});
// Call engine.reset() before another session and engine.close() at host teardown.
```

For a two-input slot, pass `rgbBitmap` and `rgbFaceBox` in the first two
arguments. The host pairs RGB and IR frames, passes unexpanded face boxes, and
controls IR illumination. `infer` copies expanded crops before returning; the
host can then release its bitmaps. Only the latest waiting frame is kept.
Superseded, pending, or reset frames receive no callback. A completed session
receives one `LIVE` or `SPOOF` callback; an inference failure sends `ERROR`.
Call `create`, `process`, `infer`, `reset`, and `close` off the main thread;
`infer` invokes its callback on the main looper.

The session discards the configured number of incoming frames, then averages
the configured number of probability vectors. Results contain `LIVE`, `SPOOF`,
or `ERROR` status and the corresponding probabilities,
score, attack, counts, and error. Call `reset()` after authentication or
cancellation and `close()` at host teardown.

A manifest slot that fails NNAPI setup or warmup is rejected without CPU
fallback. The in-repository Lab app uses `AntiSpoofingEngine.process(...)`
for return-valued raw frame evaluation and reads slot metadata from the engine.
It accesses the Vision module only through `AntiSpoofingEngine` and
`AntiSpoofingResult`; the `internal` package is not a host API.
