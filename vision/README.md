# Vision SDK Contract

The loading façade is `com.unionbiometrics.vision.VisionSdk`; stable host contracts are
isolated under `com.unionbiometrics.vision.api`. A host supplies an `IrLedController`
and passes unexpanded RGB and IR face boxes with each frame, then uses only the
`AntiSpoofingEngine` interface during authentication.

```java
IrLedController irLedController = hardware::setIrLed;

EngineLoadResult loaded = VisionSdk.loadAll(
        applicationContext, irLedController, AntiSpoofingOptions.defaults());
AntiSpoofingEngine engine = loaded.engines().get(0);
EngineInfo engineInfo = engine.info();
engine.startSession();

AntiSpoofingResult result = engine.process(new AntiSpoofingFrame(
        rgbBitmap, rgbFaceBox, rgbTimestampNs,
        irBitmap, irFaceBox, irTimestampNs));
```

The default session requests IR illumination, waits 400 ms, and averages three
probability vectors before returning `LIVE` or `SPOOF`. Calls made earlier return
`SETTLING` or `COLLECTING`; failures return `ERROR`. Call `reset()` at authentication
completion or cancellation and `close()` at host teardown.

The SDK borrows frame bitmaps only for the synchronous `process()` call and never
recycles them. IR LED controller calls run synchronously on the engine caller's thread.
Loading and inference must run off the Android main thread. A manifest
slot that fails NNAPI setup or warmup is rejected without CPU fallback.

Probability vectors follow the defensive label array returned by `ClassLabels.values()`;
hosts do not need to import `ClassificationResult`.
Each loaded engine owns immutable model-slot label, backend, and crop-margin metadata,
exposed through `AntiSpoofingEngine.info()`.

The in-repository Lab app uses internal `FrameInference.infer(...)` for raw per-frame
evaluation. Product hosts use only `startSession()` plus `process()`. Implementation is
grouped by responsibility under `com.unionbiometrics.vision.internal.asset`, `.engine`,
`.inference`, `.model`, and `.session`; those packages are unsupported and are not part
of the AAR's host API. SDK-only cross-package declarations are marked library-only for
consumer lint; `FrameInference` is the explicit in-repository Lab exception. Product
hosts consume results returned by `VisionSdk` and `AntiSpoofingEngine` rather than
manufacturing them.
