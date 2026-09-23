# Vision SDK Contract

The loading façade is `com.unionbiometrics.vision.VisionSdk`; stable host contracts are
isolated under `com.unionbiometrics.vision.api`. The host owns IR LED control and passes
unexpanded RGB and IR face boxes with each frame, then uses only the
`AntiSpoofingEngine` interface during authentication.

```java
EngineLoadResult loaded = VisionSdk.loadAll(
        applicationContext, AntiSpoofingOptions.defaults());
AntiSpoofingEngine engine = loaded.engines().get(0);
EngineInfo engineInfo = engine.info();

AntiSpoofingResult result = engine.process(new AntiSpoofingFrame(
        rgbBitmap, rgbFaceBox, rgbTimestampNs,
        irBitmap, irFaceBox, irTimestampNs));
```

The host turns on IR illumination before it begins passing frames and turns it off at every
terminal, reset, failure, and close path. The first `process()` automatically starts a session;
the default session discards ten incoming frames and averages three probability vectors before
returning `LIVE` or `SPOOF`. Calls made earlier return `PENDING`; failures return `ERROR`.
`AntiSpoofingResult.inferenceMs()` returns the TFLite invocation time for each result that
performed inference, and null otherwise. Call `reset()` at authentication completion or
cancellation and `close()` at host teardown.

The SDK borrows frame bitmaps only for the synchronous `process()` call and never
recycles them. Loading and inference must run off the Android main thread. A manifest
slot that fails NNAPI setup or warmup is rejected without CPU fallback.

Probability vectors follow the defensive label array returned by `ClassLabels.values()`;
hosts do not need to import `ClassificationResult`.
Each loaded engine owns immutable model-slot label, backend, and crop-margin metadata,
exposed through `AntiSpoofingEngine.info()`.

The in-repository Lab app uses internal `FrameClassifier.infer(...)` for raw per-frame
evaluation. Product hosts use `process()` for session inference. Implementation is
grouped by responsibility under `com.unionbiometrics.vision.internal.asset`, `.engine`,
`.inference`, `.classification`, and `.session`; those packages are unsupported and are not part
of the AAR's host API. SDK-only cross-package declarations are marked library-only for
consumer lint; `FrameClassifier` is the explicit in-repository Lab exception. Product
hosts consume results returned by `VisionSdk` and `AntiSpoofingEngine` rather than
manufacturing them.
